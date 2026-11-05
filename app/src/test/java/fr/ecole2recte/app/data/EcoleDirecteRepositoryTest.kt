package fr.ecole2recte.app.data

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
                    path.endsWith("/cahierdetexte.awp") -> homework
                    path.endsWith("/notes.awp") -> notesAnswers.removeFirstOrNull() ?: grades
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

    private fun repository(session: ApiSession, profile: ActiveProfile = profile()) =
        EcoleDirecteRepository(session, profile, clock)

    private fun openStudent(session: ApiSession = ApiSession()): ApiSession {
        session.open("jdupont", client(), listOf(Account("10", "E", "Jean", "DUPONT")))
        return session
    }

    @Test
    fun `l'accueil compose le prochain cours, la journee, le devoir et les notes`() = runTest {
        val home = repository(openStudent()).home()
        assertEquals("Bonjour Jean", home.greeting)
        assertEquals("Jeudi 5 novembre 2026", home.dateLabel)
        assertEquals("PHILOSOPHIE", home.nextClass?.subject?.name)
        assertEquals("10:15  ·  dans 25 min", home.nextClass?.startLabel)
        assertEquals(listOf("10:15"), home.today.map { it.time })
        assertEquals("MATHS", home.reminder?.subject)
        assertEquals("15,8", home.grades.average)
    }

    @Test
    fun `l'emploi du temps garde les cours annules`() = runTest {
        val week = repository(openStudent()).schedule()
        assertEquals("Semaine du 2 novembre", week.label)
        assertEquals(3, week.entries.size)
        assertEquals("Jeudi · 10:15 — 12:00", week.entries[0].whenLabel)
        assertEquals(true, week.entries[1].canceled)
    }

    @Test
    fun `les devoirs faits ou passes sont ecartes`() = runTest {
        val tasks = repository(openStudent()).homework()
        assertEquals(listOf("MATHS"), tasks.map { it.subject })
        assertEquals("Vendredi 6 novembre", tasks.single().due)
        assertEquals(true, tasks.single().exam)
    }

    @Test
    fun `les notes absentes ne sont pas des zeros et la tendance compare les periodes`() = runTest {
        val overview = repository(openStudent()).grades()
        assertEquals("15,8", overview.average)
        assertEquals("↗ +1,6", overview.trend)
        assertEquals(listOf("MATHS"), overview.bySubject.map { it.subject.name })
        assertEquals("17,5", overview.bySubject.single().average)
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
        assertEquals("15,8", overview.average)
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
}
