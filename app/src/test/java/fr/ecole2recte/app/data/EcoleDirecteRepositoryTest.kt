package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Teste le dépôt de bout en bout contre un faux serveur : aucun compte réel. */
class EcoleDirecteRepositoryTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()
    private var notesAnswers = ArrayDeque<String>()
    private var messageBody = """{"code":200,"data":{}}"""
    private val messageQueries = mutableListOf<String>()
    private var dayContent = """{"code":200,"data":{"matieres":[]}}"""

    // Jeudi 5 novembre 2026, 9 h 50 à Paris.
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    private val timetable = """{"code":200,"data":[
        {"id":1,"matiere":"PHILOSOPHIE","prof":"Mme LAURENT","salle":"B204","start_date":"2026-11-05 10:15","end_date":"2026-11-05 12:00","isAnnule":false},
        {"id":2,"text":"MATHS","start_date":"2026-11-05 13:30","end_date":"2026-11-05 14:30","isAnnule":true},
        {"id":3,"matiere":"ANGLAIS","start_date":"2026-11-06 08:00","end_date":"2026-11-06 09:00"}]}"""

    private val homework = """{"code":200,"data":{
        "2026-11-06":[{"matiere":"MATHS","idDevoir":55,"effectue":false,"interrogation":true},{"matiere":"ANGLAIS","idDevoir":56,"effectue":true}],
        "2026-11-02":[{"matiere":"FRANCAIS","idDevoir":1,"effectue":false}]}}"""

    private val grades = """{"code":200,"data":{"periodes":[
        {"idPeriode":"A001","periode":"Trimestre 1","cloture":true,"moyenneGenerale":"14,2","ensembleMatieres":{"disciplines":[]}},
        {"idPeriode":"A002","periode":"Trimestre 2","cloture":false,"moyenneGenerale":"15,8","ensembleMatieres":{"disciplines":[
            {"discipline":"MATHS","moyenne":"17,5","professeurs":[{"civilite":"M.","prenom":"Paul","nom":"PETIT"}]},
            {"discipline":"ANGLAIS","moyenne":""}]}}],
        "notes":[
            {"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","coef":"2","codePeriode":"A002","date":"2026-11-03"},
            {"id":2,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"Abs","noteSur":"20","codePeriode":"A002","date":"2026-11-04"}]}}"""

    private val messages = """{"code":200,"data":{"messages":{"received":[
        {"id":1,"from":{"nom":"VIE SCOLAIRE"},"subject":"Réunion","read":false,"date":"2026-11-04 10:00:00"},
        {"id":2,"from":{"civilite":"M.","prenom":"Paul","nom":"PETIT"},"subject":"Correction","read":true}]}}}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                paths += path
                val body = when {
                    path.endsWith("/emploidutemps.awp") -> timetable
                    path.contains("/cahierdetexte/") -> dayContent
                    path.endsWith("/cahierdetexte.awp") -> homework
                    path.endsWith("/notes.awp") -> notesAnswers.removeFirstOrNull() ?: grades
                    path.contains("/messages/") -> {
                        messageQueries += request.requestUrl!!.encodedQuery.orEmpty()
                        messageBody
                    }
                    path.endsWith("/messages.awp") -> messages
                    else -> """{"code":404}"""
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun client() = EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/'))

    private fun profile(studentId: String? = null) =
        ActiveProfile("jdupont", studentId, "Jean", "Jean Dupont", "JD")

    private val cache = InMemoryResponseCache()

    private fun repository(session: ApiSession, profile: ActiveProfile = profile()) =
        EcoleDirecteRepository(session, profile, cache, clock = clock)

    private fun openStudent(session: ApiSession = ApiSession()): ApiSession {
        session.open("jdupont", client(), listOf(Account("10", "E", "Jean", "DUPONT")))
        return session
    }

    @Test
    fun `l'accueil compose le prochain cours, la journee, le devoir et les notes`() = runTest {
        val home = repository(openStudent()).home()
        assertEquals("Bonjour Jean", home.greeting)
        assertEquals("Jeudi 5 novembre 2026", home.dateLabel)
        assertEquals("Philosophie", home.nextClass?.subject?.name)
        assertEquals("10:15  ·  dans 25 min", home.nextClass?.startLabel)
        assertEquals("Ensuite : Anglais vendredi à 08:00", home.nextClass?.afterLabel)
        assertEquals(listOf("10:15", "13:30"), home.today.map { it.start })
        assertEquals(listOf(false, true), home.today.map { it.canceled })
        assertEquals("Maths", home.reminder?.subject)
        assertEquals("15,80", home.grades.average)
    }

    @Test
    fun `l'accueil resume ce qu'il faut savoir`() = runTest {
        val highlights = repository(openStudent()).home().highlights
        // Du plus pressant au moins pressant : cours annulé, évaluation, message, note.
        assertEquals(
            listOf("Cours annulé : Maths", "Évaluation en Maths", "1 message non lu", "Maths"),
            highlights.map { it.title },
        )
        // La note : matière en titre, chiffre et coefficient mis en avant, détail en petit.
        val grade = highlights.last()
        assertEquals("16/20", grade.value)
        assertEquals("coef. 2", grade.valueSub)
        assertEquals("DS 1 · 3 nov.", grade.detail)
    }

    @Test
    fun `l'emploi du temps garde les cours annules`() = runTest {
        val week = repository(openStudent()).schedule()
        assertEquals("Semaine du 2 novembre", week.label)
        assertEquals(3, week.entries.size)
        assertEquals("2026-11-02", week.weekStart)
        assertEquals("2026-11-05", week.today)
        assertEquals(Triple("2026-11-05", "10:15", "12:00"), Triple(week.entries[0].date, week.entries[0].start, week.entries[0].end))
        assertEquals("Jeudi · 10:15 — 12:00", week.entries[0].whenLabel)
        assertEquals(true, week.entries[1].canceled)
    }

    @Test
    fun `les devoirs passes sont ecartes et les faits sont marques`() = runTest {
        val tasks = repository(openStudent()).homework()
        assertEquals(listOf("Maths", "Anglais"), tasks.map { it.subject })
        assertEquals("Vendredi 6 novembre", tasks.first().due)
        assertEquals(true, tasks.first().exam)
        assertEquals(listOf(false, true), tasks.map { it.done })
    }

    @Test
    fun `cocher un devoir l'emporte sur le serveur et sort des pastilles`() = runTest {
        val repository = repository(openStudent())
        repository.setHomeworkDone("55", true)
        assertEquals(listOf(true, true), repository.homework().map { it.done })
        assertEquals(0, repository.badges().urgentHomework)
        repository.setHomeworkDone("55", false)
        assertEquals(1, repository.badges().urgentHomework)
    }

    @Test
    fun `la fiche d'un devoir charge l'enonce du jour`() = runTest {
        val enonce = java.util.Base64.getEncoder().encodeToString("<p>Exercices 12 à 18</p>".toByteArray())
        val seance = java.util.Base64.getEncoder().encodeToString("Les suites".toByteArray())
        dayContent = """{"code":200,"data":{"matieres":[
            {"matiere":"MATHS","aFaire":{"idDevoir":55,"contenu":"$enonce","documents":[{"id":9,"libelle":"Fiche.pdf","type":"FICHIER"}]},
             "contenuDeSeance":{"contenu":"$seance"}}]}}"""
        val repository = repository(openStudent())
        val detail = repository.homeworkDetail(repository.homework().first())
        assertEquals("Exercices 12 à 18", detail.content)
        assertEquals(listOf("Fiche.pdf"), detail.attachments)
        assertEquals("Les suites", detail.lessonContent)
        assertTrue(paths.contains("/v3/Eleves/10/cahierdetexte/2026-11-06.awp"))
    }

    @Test
    fun `les notes absentes ne sont pas des zeros et la tendance compare les periodes`() = runTest {
        val overview = repository(openStudent()).grades()
        assertEquals("15,80", overview.average)
        assertEquals("↗ +1,60", overview.trend)
        // Une matière qui n'a qu'une absence reste visible, sans moyenne inventée.
        assertEquals(listOf("Maths", "Anglais"), overview.bySubject.map { it.subject.name })
        assertEquals("17,50", overview.bySubject.first().average)
        assertEquals("–", overview.bySubject.last().average)
        assertEquals(listOf("DS 1"), overview.bySubject.first().grades.map { it.label })
        assertEquals(listOf("DS 1"), overview.recent.map { it.label })
        assertEquals(listOf(14.2, 15.8), overview.evolution.map { it.average })
    }

    @Test
    fun `les pastilles comptent les non lus, les devoirs urgents et les notes recentes`() = runTest {
        val badges = repository(openStudent()).badges()
        assertEquals(1, badges.unreadMessages)
        assertEquals(1, badges.urgentHomework)
        assertEquals(1, badges.newGrades)
    }

    @Test
    fun `une ressource demandee plusieurs fois n'est chargee qu'une fois`() = runTest {
        val repository = repository(openStudent())
        repository.home()
        repository.homework()
        repository.badges()
        repository.schedule()
        assertEquals(1, paths.count { it.endsWith("/cahierdetexte.awp") })
        assertEquals(1, paths.count { it.endsWith("/emploidutemps.awp") })
        assertEquals(1, paths.count { it.endsWith("/notes.awp") })
    }

    @Test
    fun `un compte etudiant lit sa propre messagerie`() = runTest {
        repository(openStudent()).messages()
        assertTrue(paths.contains("/v3/eleves/10/messages.awp"))
    }

    @Test
    fun `un compte famille lit les donnees de l'eleve choisi et la messagerie de la famille`() = runTest {
        val session = ApiSession()
        val children = listOf(Student("10", "Paul", "DUPONT"), Student("11", "Zoé", "DUPONT"))
        session.open("jdupont", client(), listOf(Account("1", "1", "Marie", "DUPONT", children)))
        val repository = repository(session, profile(studentId = "11"))
        repository.homework()
        repository.messages()
        assertTrue(paths.contains("/v3/Eleves/11/cahierdetexte.awp"))
        assertTrue(paths.contains("/v3/familles/1/messages.awp"))
    }

    @Test
    fun `un jeton expire rouvre la session une seule fois puis rejoue l'appel`() = runTest {
        notesAnswers += """{"code":520}"""
        var reopened = 0
        val session = ApiSession(minReauthIntervalMs = 0)
        openStudent(session)
        session.reauthenticator = {
            reopened++
            true
        }
        val overview = repository(session).grades()
        assertEquals(1, reopened)
        assertEquals("15,80", overview.average)
        assertEquals(2, paths.count { it.endsWith("/notes.awp") })
    }

    @Test
    fun `si la reouverture echoue l'erreur remonte sans nouvel essai`() = runTest {
        notesAnswers += """{"code":520}"""
        val session = ApiSession(minReauthIntervalMs = 0)
        openStudent(session)
        session.reauthenticator = { false }
        val error = runCatching { repository(session).grades() }.exceptionOrNull()
        assertTrue(error is EcoleDirecteException)
        assertEquals(1, paths.count { it.endsWith("/notes.awp") })
    }

    @Test
    fun `un compte professeur n'est pas pris en charge`() = runTest {
        val session = ApiSession()
        session.open("jdupont", client(), listOf(Account("5", "P", "Anne", "PROF")))
        val error = runCatching { repository(session).homework() }.exceptionOrNull()
        assertTrue(error is EcoleDirecteException)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `la liste des messages porte la date et l'identifiant`() = runTest {
        val page = repository(openStudent()).messagesPage(0)
        assertEquals(listOf("1", "2"), page.items.map { it.id })
        assertEquals("4 nov.", page.items.first().dateLabel)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun `ouvrir un message lit son corps complet`() = runTest {
        val corps = java.util.Base64.getEncoder().encodeToString("<p>Bonjour</p><p>Réunion jeudi</p>".toByteArray())
        messageBody = """{"code":200,"data":{"id":1,"subject":"Réunion","from":{"nom":"VIE SCOLAIRE"},
            "to":[{"civilite":"M.","prenom":"Jean","nom":"DUPONT"}],"date":"2026-11-04 10:00:00","content":"$corps",
            "files":[{"id":4,"libelle":"Convocation.pdf","type":"FICHIER"}]}}"""
        val detail = repository(openStudent()).messageDetail("1")
        assertEquals("Réunion", detail.subject)
        assertEquals("Bonjour" + (10).toChar() + (10).toChar() + "Réunion jeudi", detail.body)
        assertEquals(listOf("M. Jean DUPONT"), detail.recipients)
        assertEquals(listOf("Convocation.pdf"), detail.attachments)
        assertTrue(paths.contains("/v3/eleves/10/messages/1.awp"))
        assertEquals(listOf("verbe=get&v=4.75.0&mode=destinataire"), messageQueries)
    }

    // --- Mode hors ligne ---------------------------------------------------------------------

    private fun offlineSession(): ApiSession =
        ApiSession().also { it.fail("jdupont", "Pas de connexion à ÉcoleDirecte.", FailureKind.Network) }

    @Test
    fun `sans reseau la derniere reponse enregistree s'affiche et est marquee perimee`() = runTest {
        val online = repository(openStudent())
        online.homework()
        assertEquals(false, online.freshness.value.stale)
        assertEquals(clock.millis(), online.freshness.value.lastSyncMillis)

        val offline = repository(offlineSession())
        val tasks = offline.homework()
        assertEquals(listOf("Maths", "Anglais"), tasks.map { it.subject })
        assertEquals(true, offline.freshness.value.stale)
        assertEquals(clock.millis(), offline.freshness.value.lastSyncMillis)
    }

    @Test
    fun `sans reseau ni enregistrement l'erreur remonte`() = runTest {
        val error = runCatching { repository(offlineSession()).homework() }.exceptionOrNull()
        assertTrue(error is EcoleDirecteException)
    }

    @Test
    fun `l'accueil hors ligne s'affiche depuis l'enregistrement`() = runTest {
        repository(openStudent()).home()
        val home = repository(offlineSession()).home()
        assertEquals("Philosophie", home.nextClass?.subject?.name)
        assertEquals("15,80", home.grades.average)
    }

    @Test
    fun `invalider force un nouveau chargement`() = runTest {
        val repository = repository(openStudent())
        repository.homework()
        repository.homework()
        assertEquals(1, paths.count { it.endsWith("/cahierdetexte.awp") })
        repository.invalidate()
        repository.homework()
        assertEquals(2, paths.count { it.endsWith("/cahierdetexte.awp") })
    }

    @Test
    fun `chaque eleve a son propre enregistrement`() = runTest {
        val session = ApiSession()
        val children = listOf(Student("10", "Paul", "DUPONT"), Student("11", "Zoé", "DUPONT"))
        session.open("jdupont", client(), listOf(Account("1", "1", "Marie", "DUPONT", children)))
        repository(session, profile(studentId = "10")).homework()
        val other = repository(offlineSession(), profile(studentId = "11"))
        assertTrue(runCatching { other.homework() }.isFailure)
    }
}
