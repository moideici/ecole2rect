package fr.ecole2recte.app.sync

import fr.ecole2recte.app.data.Account
import fr.ecole2recte.app.data.AccountProfile
import fr.ecole2recte.app.data.Student
import fr.ecole2recte.app.data.BirthDate
import fr.ecole2recte.app.data.InMemoryTokenLog
import fr.ecole2recte.app.data.SavedSession
import fr.ecole2recte.app.data.TokenLifetimeLog
import fr.ecole2recte.app.data.USER_AGENT
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.InMemoryHomeworkStatusStore
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.TrustedAnswer
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

private class MemoryStore(vararg initial: SavedLogin) : CredentialStore {
    val logins = initial.toMutableList()

    /** Simule l'application qui modifie le compte pendant que la synchro travaille. */
    var beforeUpdate: (() -> Unit)? = null

    override fun update(identifier: String, transform: (SavedLogin) -> SavedLogin) {
        beforeUpdate?.invoke()
        super.update(identifier, transform)
    }
    override fun readAll() = logins.toList()
    override fun activeIdentifier() = logins.firstOrNull()?.identifier
    override fun save(login: SavedLogin, makeActive: Boolean) {
        val index = logins.indexOfFirst { it.identifier == login.identifier }
        if (index >= 0) logins[index] = login else logins += login
    }

    override fun setActive(identifier: String) = Unit
    override fun remove(identifier: String) {
        logins.removeAll { it.identifier == identifier }
    }

    override fun clear() = logins.clear()
}

private class MemoryPrefs(var enabled: Boolean = true) : SyncPrefs {
    var reason: String? = null
    var lastSync: Long? = null
    override val isEnabled get() = enabled
    override fun disable(reason: String) {
        enabled = false
        this.reason = reason
    }

    override fun recordSync(at: Long) {
        lastSync = at
    }

    var failures = 0
    override fun recordFailure() = ++failures
    override fun resetFailures() {
        failures = 0
    }
}

private class MemoryState : NotificationStateStore {
    private val values = HashMap<String, NotifiedIds>()
    val owners: Set<String> get() = values.keys
    override fun load(owner: String) = values[owner]
    override fun save(owner: String, ids: NotifiedIds) {
        values[owner] = ids
    }
}

private class Recorder(override val wantsSchoolLife: Boolean = false) : NotificationSink, WidgetSink {
    val posted = mutableListOf<NotificationEvent>()
    var snapshot: WidgetSnapshot? = null
    var schoolLifeOwner: String? = null
    var schoolLife: List<fr.ecole2recte.app.domain.SchoolLifeEvent>? = null
    override suspend fun publishSchoolLife(owner: String, events: List<fr.ecole2recte.app.domain.SchoolLifeEvent>) {
        schoolLifeOwner = owner
        schoolLife = events
    }
    override fun post(events: List<NotificationEvent>) {
        posted += events
    }

    override suspend fun publish(snapshot: WidgetSnapshot) {
        this.snapshot = snapshot
    }
}

/** La synchronisation de fond contre un faux serveur : aucun compte réel. */
class SyncServiceTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()
    private val loginBodies = ArrayDeque<String>()
    private val timetableCodes = ArrayDeque<String>()
    private var inbox = """{"code":200,"data":{"messages":{"received":[]}}}"""
    private val dataTokens = mutableListOf<String?>()
    private var notes = """[{"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","codePeriode":"A1","date":"2026-11-04"}]"""

    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))
    private val accountBody = """{"code":200,"token":"t","data":{"accounts":[{"id":"10","typeCompte":"E","prenom":"Jean","nom":"DUPONT"}]}}"""
    private val saved = SavedLogin("jdupont", "secret", TrustedAnswer("cn", "cv"))

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val query = request.requestUrl!!.encodedQuery.orEmpty()
                paths += path
                if (!path.endsWith("/login.awp")) dataTokens += request.getHeader("X-Token")
                val response = MockResponse().addHeader("X-Token", "tok").addHeader("2FA-Token", "tfa")
                val body = when {
                    path.endsWith("/login.awp") && "gtk=1" in query -> {
                        response.addHeader("X-GTK", "gtk")
                        "{}"
                    }
                    path.endsWith("/login.awp") -> loginBodies.removeFirst()
                    path.endsWith("/doubleauth.awp") && "verbe=get" in query -> challenge
                    path.endsWith("/doubleauth.awp") -> """{"code":200,"data":{"cn":"nouveau","cv":"nouveau"}}"""
                    path.endsWith("/emploidutemps.awp") && timetableCodes.isNotEmpty() -> timetableCodes.removeFirst()
                    path.endsWith("/emploidutemps.awp") -> """{"code":200,"data":[
                        {"id":1,"matiere":"PHILOSOPHIE","salle":"B204","start_date":"2026-11-05 10:15","end_date":"2026-11-05 12:00"},
                        {"id":2,"matiere":"MATHS","start_date":"2026-11-05 13:30","end_date":"2026-11-05 14:30","isAnnule":true}]}"""
                    path.endsWith("/cahierdetexte.awp") -> """{"code":200,"data":{"2026-11-06":[{"matiere":"MATHS","idDevoir":55,"effectue":false}]}}"""
                    path.endsWith("/notes.awp") -> """{"code":200,"data":{"periodes":[
                        {"idPeriode":"A1","periode":"T1","cloture":false,"isCurrent":true,"moyenneGenerale":"15","ensembleMatieres":{"disciplines":[]}}],
                        "notes":$notes}}"""
                    path.endsWith("/messages.awp") -> inbox
                    path.endsWith("/viescolaire.awp") -> """{"code":200,"data":{"absencesRetards":[{"id":1,"typeElement":"Absence","date":"2026-11-01","duree":"1 h 30","justifie":false}]}}"""
                    else -> """{"code":404}"""
                }
                return response.setBody(body)
            }
        }
        server.start()
    }

    private var challenge = ""

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun service(
        store: CredentialStore,
        prefs: MemoryPrefs,
        state: MemoryState,
        recorder: Recorder,
        tokenLog: TokenLifetimeLog = InMemoryTokenLog(),
        journal: SyncJournal? = null,
    ) = SyncService(
        store = store,
        cache = InMemoryResponseCache(),
        homeworkStatus = InMemoryHomeworkStatusStore(),
        prefs = prefs,
        state = state,
        notifier = recorder,
        widgets = recorder,
        clientFactory = { EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')) },
        clock = clock,
        tokenLog = tokenLog,
        pauseBetweenChildrenMs = 0,
        journal = journal,
    )

    private fun keptSession(idleMs: Long, ageMs: Long = 3_600_000) = SavedSession(
        token = "stored",
        cookies = mapOf("GTK" to "g"),
        gtk = "gtk",
        userAgent = USER_AGENT,
        issuedAt = clock.millis() - ageMs,
        lastOkAt = clock.millis() - idleMs,
        accounts = listOf(Account("10", "E", "Jean", "DUPONT")),
    )

    @Test
    fun `le premier passage ne notifie rien et remplit les widgets`() = runTest {
        loginBodies += accountBody
        val recorder = Recorder()
        val prefs = MemoryPrefs()
        val outcome = service(MemoryStore(saved), prefs, MemoryState(), recorder).run()
        assertEquals(SyncOutcome.Done, outcome)
        assertEquals(emptyList<NotificationEvent>(), recorder.posted)
        assertEquals("Philosophie", recorder.snapshot?.nextClass?.subject)
        assertEquals(listOf("Maths"), recorder.snapshot?.tasks?.map { it.subject })
        assertNotNull(prefs.lastSync)
        assertFalse(paths.any { it.endsWith("/viescolaire.awp") })
        assertEquals("jdupont/", recorder.snapshot?.owner)
    }

    @Test
    fun `la vie scolaire demandee est publiee pour le profil actif`() = runTest {
        loginBodies += accountBody
        val recorder = Recorder(wantsSchoolLife = true)
        assertEquals(SyncOutcome.Done, service(MemoryStore(saved), MemoryPrefs(), MemoryState(), recorder).run())
        assertEquals("jdupont/", recorder.schoolLifeOwner)
        assertEquals(90, recorder.schoolLife?.single()?.countedMinutes)
        assertEquals(1, paths.count { it.endsWith("/viescolaire.awp") })
    }

    @Test
    fun `un module vie scolaire ferme ne declenche aucun appel ni publication`() = runTest {
        loginBodies += accountBody.replace("\"nom\":\"DUPONT\"", "\"nom\":\"DUPONT\",\"modules\":[{\"code\":\"VIE_SCOLAIRE\",\"enable\":false}]")
        val recorder = Recorder(wantsSchoolLife = true)
        val closed = saved.copy(profile = AccountProfile("Jean", "DUPONT", "E", modules = mapOf("VIE_SCOLAIRE" to false)))
        assertEquals(SyncOutcome.Done, service(MemoryStore(closed), MemoryPrefs(), MemoryState(), recorder).run())
        assertTrue(recorder.schoolLife == null)
        assertFalse(paths.any { it.endsWith("/viescolaire.awp") })
    }

    @Test
    fun `un passage suivant notifie ce qui est nouveau, une seule fois`() = runTest {
        val recorder = Recorder()
        val state = MemoryState()
        val store = MemoryStore(saved)
        val prefs = MemoryPrefs()
        loginBodies += accountBody
        service(store, prefs, state, recorder).run()

        notes = """[{"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","codePeriode":"A1","date":"2026-11-04"},
            {"id":2,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"14","noteSur":"20","codePeriode":"A1","date":"2026-11-05"}]"""
        loginBodies += accountBody
        service(store, prefs, state, recorder).run()
        // Le cours annulé et le devoir existaient déjà au premier passage : seule la note est nouvelle.
        assertEquals(listOf("Nouvelle note en Anglais"), recorder.posted.map { it.title })

        val before = recorder.posted.size
        loginBodies += accountBody
        service(store, prefs, state, recorder).run()
        assertEquals(before, recorder.posted.size)
    }

    @Test
    fun `un mot de passe refuse arrete la synchronisation sans nouvel essai`() = runTest {
        loginBodies += """{"code":505,"message":"refusé"}"""
        val prefs = MemoryPrefs()
        val outcome = service(MemoryStore(saved), prefs, MemoryState(), Recorder()).run()
        assertEquals(SyncOutcome.Stopped, outcome)
        assertFalse(prefs.enabled)
        assertTrue(prefs.reason!!.contains("mot de passe"))
        // Un seul passage : la demande de jeton GTK et une seule connexion, jamais de nouvel essai.
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
    }

    @Test
    fun `un compte bloque arrete la synchronisation`() = runTest {
        loginBodies += """{"code":516}"""
        val prefs = MemoryPrefs()
        assertEquals(SyncOutcome.Stopped, service(MemoryStore(saved), prefs, MemoryState(), Recorder()).run())
        assertFalse(prefs.enabled)
    }

    @Test
    fun `une panne reseau n'arrete rien et ne boucle pas`() = runTest {
        val service = service(MemoryStore(saved), MemoryPrefs(), MemoryState(), Recorder())
        server.shutdown()
        assertEquals(SyncOutcome.Failed, service.run())
    }

    private class MemoryJournalStorage : JournalStorage {
        var text = ""
        override fun read() = text
        override fun write(text: String) {
            this.text = text
        }
    }

    @Test
    fun `le journal garde une ligne par passage, sans donnee personnelle`() = runTest {
        val journal = SyncJournal(MemoryJournalStorage())
        val store = MemoryStore(saved)
        val prefs = MemoryPrefs()
        val state = MemoryState()
        loginBodies += accountBody
        service(store, prefs, state, Recorder(), journal = journal).run()
        notes = """[{"id":1,"libelleMatiere":"MATHS","devoir":"DS 1","valeur":"16","noteSur":"20","codePeriode":"A1","date":"2026-11-04"},
            {"id":2,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"14","noteSur":"20","codePeriode":"A1","date":"2026-11-05"}]"""
        loginBodies += accountBody
        service(store, prefs, state, Recorder(), journal = journal).run()

        val entries = journal.entries()
        assertEquals(2, entries.size)
        assertEquals(SyncResultCode.Done, entries[0].result)
        assertEquals(1, entries[0].newItems) // la nouvelle note (le plus récent d'abord)
        assertEquals(0, entries[1].newItems)
        assertEquals(clock.millis(), entries[0].startedAt)
        val stored = (journal.entries().joinToString { it.describe() })
        assertFalse(stored.contains("Anglais", ignoreCase = true))
        assertFalse(stored.contains("jdupont"))
    }

    @Test
    fun `le journal dit pourquoi un passage s'est arrete`() = runTest {
        val journal = SyncJournal(MemoryJournalStorage())
        loginBodies += """{"code":505,"message":"refusé"}"""
        service(MemoryStore(saved), MemoryPrefs(), MemoryState(), Recorder(), journal = journal).run()
        assertEquals(SyncResultCode.PasswordRefused, journal.entries().single().result)
        assertEquals(SyncResultCode.Group.Stopped, journal.entries().single().result.group)
    }

    @Test
    fun `le journal distingue le reseau, la verification et la version refusee`() = runTest {
        val journal = SyncJournal(MemoryJournalStorage())
        val offline = service(MemoryStore(saved), MemoryPrefs(), MemoryState(), Recorder(), journal = journal)
        server.shutdown()
        offline.run()
        assertEquals(SyncResultCode.NoNetwork, journal.entries().first().result)

        val stale = SyncJournal(MemoryJournalStorage())
        val second = MockWebServer()
        second.start()
        second.enqueue(MockResponse().setBody("{}").addHeader("X-GTK", "gtk"))
        second.enqueue(MockResponse().setBody("""{"code":517}"""))
        val factory = { EcoleDirecteClient(apiBase = second.url("/v3").toString().trimEnd('/')) }
        SyncService(
            store = MemoryStore(saved), cache = InMemoryResponseCache(), homeworkStatus = InMemoryHomeworkStatusStore(),
            prefs = MemoryPrefs(), state = MemoryState(), notifier = Recorder(), widgets = Recorder(),
            clientFactory = factory, clock = clock, pauseBetweenChildrenMs = 0, journal = stale,
        ).run()
        assertEquals(SyncResultCode.ApiVersionRefused, stale.entries().single().result)
        second.shutdown()
    }

    @Test
    fun `trois echecs de suite sont journalises puis l'arret`() = runTest {
        val journal = SyncJournal(MemoryJournalStorage())
        val service = service(MemoryStore(saved), MemoryPrefs(), MemoryState(), Recorder(), journal = journal)
        server.shutdown()
        repeat(3) { service.run() }
        assertEquals(
            listOf(SyncResultCode.TooManyFailures, SyncResultCode.NoNetwork, SyncResultCode.NoNetwork),
            journal.entries().map { it.result },
        )
    }

    @Test
    fun `un passage desactive ou sans compte n'ecrit rien dans le journal`() = runTest {
        val journal = SyncJournal(MemoryJournalStorage())
        service(MemoryStore(saved), MemoryPrefs(enabled = false), MemoryState(), Recorder(), journal = journal).run()
        service(MemoryStore(), MemoryPrefs(), MemoryState(), Recorder(), journal = journal).run()
        assertTrue(journal.entries().isEmpty())
    }

    @Test
    fun `desactivee elle ne fait aucune requete`() = runTest {
        val outcome = service(MemoryStore(saved), MemoryPrefs(enabled = false), MemoryState(), Recorder()).run()
        assertEquals(SyncOutcome.Disabled, outcome)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `sans compte il n'y a rien a faire`() = runTest {
        assertEquals(SyncOutcome.NoAccount, service(MemoryStore(), MemoryPrefs(), MemoryState(), Recorder()).run())
    }

    @Test
    fun `une question sans date de naissance memorisee attend l'utilisateur`() = runTest {
        loginBodies += """{"code":250}"""
        challenge = """{"code":200,"data":{"question":"${b64("Quel est ton mois de naissance ?")}","propositions":["${b64("Janvier")}","${b64("Février")}"]}}"""
        val prefs = MemoryPrefs()
        val outcome = service(MemoryStore(saved), prefs, MemoryState(), Recorder()).run()
        assertEquals(SyncOutcome.NeedsInteraction, outcome)
        assertEquals(0, paths.count { it.contains("/emploidutemps") })
        // La synchro s'arrête et explique : plus aucune connexion tant que l'utilisateur n'a pas réactivé.
        assertFalse(prefs.enabled)
        assertTrue(prefs.reason!!.contains("vérification"))
    }

    @Test
    fun `trois echecs de suite arretent la synchronisation et l'expliquent`() = runTest {
        val prefs = MemoryPrefs()
        val service = service(MemoryStore(saved), prefs, MemoryState(), Recorder())
        server.shutdown()
        assertEquals(SyncOutcome.Failed, service.run())
        assertEquals(SyncOutcome.Failed, service.run())
        assertTrue(prefs.enabled)
        assertEquals(SyncOutcome.Stopped, service.run())
        assertFalse(prefs.enabled)
        assertTrue(prefs.reason!!.contains("3 fois de suite"))
    }

    @Test
    fun `une synchronisation reussie remet le compteur d'echecs a zero`() = runTest {
        val prefs = MemoryPrefs().also { it.failures = 2 }
        loginBodies += accountBody
        assertEquals(SyncOutcome.Done, service(MemoryStore(saved), prefs, MemoryState(), Recorder()).run())
        assertEquals(0, prefs.failures)
    }

    @Test
    fun `la version d'API refusee arrete la synchronisation sans nouvel essai`() = runTest {
        loginBodies += """{"code":517}"""
        val prefs = MemoryPrefs()
        assertEquals(SyncOutcome.Stopped, service(MemoryStore(saved), prefs, MemoryState(), Recorder()).run())
        assertFalse(prefs.enabled)
        assertTrue(prefs.reason!!.contains("517"))
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
    }

    @Test
    fun `une session gardee recente est rejouee sans aucune connexion`() = runTest {
        val store = MemoryStore(saved.copy(session = keptSession(idleMs = 5 * 60_000)))
        assertEquals(SyncOutcome.Done, service(store, MemoryPrefs(), MemoryState(), Recorder()).run())
        assertEquals(0, paths.count { it.endsWith("/login.awp") })
        assertEquals("stored", dataTokens.first())
        // Le jeton renouvelé est gardé pour le passage suivant.
        assertNotNull(store.logins.single().session)
    }

    @Test
    fun `une session gardee trop ancienne n'est pas rejouee`() = runTest {
        loginBodies += accountBody
        val store = MemoryStore(saved.copy(session = keptSession(idleMs = 3 * 3_600_000)))
        assertEquals(SyncOutcome.Done, service(store, MemoryPrefs(), MemoryState(), Recorder()).run())
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
    }

    @Test
    fun `un jeton rejoue puis refuse declenche une seule reconnexion et se retient`() = runTest {
        timetableCodes += """{"code":520}"""
        loginBodies += accountBody
        val log = InMemoryTokenLog()
        val store = MemoryStore(saved.copy(session = keptSession(idleMs = 8 * 60_000)))
        assertEquals(SyncOutcome.Done, service(store, MemoryPrefs(), MemoryState(), Recorder(), log).run())
        assertEquals(2, paths.count { it.endsWith("/login.awp") }) // jeton GTK + une seule connexion
        assertEquals(8 * 60_000L, log.load().expiredAfterIdleMs)
    }

    @Test
    fun `une reconnexion refusee apres un rejeu arrete tout au lieu d'insister`() = runTest {
        timetableCodes += """{"code":520}"""
        loginBodies += """{"code":505}"""
        val prefs = MemoryPrefs()
        val store = MemoryStore(saved.copy(session = keptSession(idleMs = 5 * 60_000)))
        assertEquals(SyncOutcome.Stopped, service(store, prefs, MemoryState(), Recorder()).run())
        assertFalse(prefs.enabled)
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
    }

    @Test
    fun `la synchro ne met a jour que la reponse de confiance, jamais le reste du compte`() = runTest {
        loginBodies += """{"code":250}"""
        loginBodies += accountBody
        challenge = """{"code":200,"data":{"question":"${b64("Quel est ton mois de naissance ?")}","propositions":["${b64("Janvier")}","${b64("Février")}"]}}"""
        val store = MemoryStore(saved.copy(birthDate = BirthDate("12", "2", "2008")))
        // Pendant la synchro, l'utilisateur change d'élève et de mot de passe dans l'application.
        store.beforeUpdate = {
            store.logins[0] = store.logins[0].copy(activeStudentId = "20", password = "nouveau")
            store.beforeUpdate = null
        }
        assertEquals(SyncOutcome.Done, service(store, MemoryPrefs(), MemoryState(), Recorder()).run())
        val kept = store.logins.single()
        assertEquals(TrustedAnswer("nouveau", "nouveau"), kept.trustedAnswer)
        assertEquals("20", kept.activeStudentId)
        assertEquals("nouveau", kept.password)
    }

    private val family = saved.copy(
        profile = AccountProfile("Marie", "DUPONT", "1", listOf(Student("20", "Léa", "DUPONT"), Student("21", "Tom", "DUPONT"))),
        activeStudentId = "21",
    )
    private val familyBody = """{"code":200,"token":"t","data":{"accounts":[{"id":"1","typeCompte":"1","prenom":"Marie","nom":"DUPONT",
        "profile":{"eleves":[{"id":20,"prenom":"Léa","nom":"DUPONT"},{"id":21,"prenom":"Tom","nom":"DUPONT"}]}}]}}"""
    private fun unread(subject: String) =
        """{"code":200,"data":{"messages":{"received":[{"id":7,"from":{"nom":"VIE SCOLAIRE"},"subject":"$subject","read":false}]}}}"""

    @Test
    fun `un compte famille a un passage par enfant, la boite de reception n'est lue qu'une fois`() = runTest {
        loginBodies += familyBody
        val recorder = Recorder()
        assertEquals(SyncOutcome.Done, service(MemoryStore(family), MemoryPrefs(), MemoryState(), recorder).run())
        assertEquals(1, paths.count { it.endsWith("/emploidutemps.awp") && "/E/20/" in it })
        assertEquals(1, paths.count { it.endsWith("/emploidutemps.awp") && "/E/21/" in it })
        assertEquals(1, paths.count { it.endsWith("/messages.awp") })
        // L'enfant affiché (Tom) est lu en premier.
        assertTrue(paths.indexOfFirst { "/E/21/" in it } < paths.indexOfFirst { "/E/20/" in it })
    }

    @Test
    fun `chaque enfant a ses propres nouveautes et la famille ses messages, une seule fois`() = runTest {
        val store = MemoryStore(family)
        val state = MemoryState()
        val recorder = Recorder()
        loginBodies += familyBody
        service(store, MemoryPrefs(), state, recorder).run()
        assertEquals(setOf("jdupont|21", "jdupont|20", "jdupont|mailbox"), state.owners)

        notes = """[{"id":2,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"14","noteSur":"20","codePeriode":"A1","date":"2026-11-05"}]"""
        inbox = unread("Réunion")
        loginBodies += familyBody
        service(store, MemoryPrefs(), state, recorder).run()

        val grades = recorder.posted.filter { it.kind == EventKind.Grade }
        assertEquals(listOf("Léa", "Tom"), grades.map { it.owner }.sorted())
        val messages = recorder.posted.filter { it.kind == EventKind.Message }
        assertEquals(1, messages.size)
        assertEquals("", messages.single().owner)
        // Troisième passage sans rien de neuf : plus rien.
        val before = recorder.posted.size
        loginBodies += familyBody
        service(store, MemoryPrefs(), state, recorder).run()
        assertEquals(before, recorder.posted.size)
    }

    @Test
    fun `un compte eleve ne montre aucun sous-titre`() = runTest {
        val store = MemoryStore(saved)
        val state = MemoryState()
        val recorder = Recorder()
        loginBodies += accountBody
        service(store, MemoryPrefs(), state, recorder).run()
        notes = """[{"id":2,"libelleMatiere":"ANGLAIS","devoir":"Oral","valeur":"14","noteSur":"20","codePeriode":"A1","date":"2026-11-05"}]"""
        loginBodies += accountBody
        service(store, MemoryPrefs(), state, recorder).run()
        assertEquals(listOf(""), recorder.posted.map { it.owner })
        assertEquals(setOf("jdupont|"), state.owners)
    }

    @Test
    fun `la gigue du premier passage reste entre une et dix minutes`() {
        repeat(200) { assertTrue(SyncScheduler.jitterMinutes() in 1L..10L) }
    }

    @Test
    fun `une question qui revient recoit la reponse seule grace a la date memorisee`() = runTest {
        loginBodies += """{"code":250}"""
        loginBodies += accountBody
        challenge = """{"code":200,"data":{"question":"${b64("Quel est ton mois de naissance ?")}","propositions":["${b64("Janvier")}","${b64("Février")}"]}}"""
        val store = MemoryStore(saved.copy(birthDate = BirthDate("12", "2", "2008")))
        val outcome = service(store, MemoryPrefs(), MemoryState(), Recorder()).run()
        assertEquals(SyncOutcome.Done, outcome)
        assertEquals(TrustedAnswer("nouveau", "nouveau"), store.logins.single().trustedAnswer)
    }
}
