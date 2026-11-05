package fr.ecole2recte.app.sync

import fr.ecole2recte.app.data.BirthDate
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
}

private class MemoryState : NotificationStateStore {
    private val values = HashMap<String, NotifiedIds>()
    override fun load(owner: String) = values[owner]
    override fun save(owner: String, ids: NotifiedIds) {
        values[owner] = ids
    }
}

private class Recorder : NotificationSink, WidgetSink {
    val posted = mutableListOf<NotificationEvent>()
    var snapshot: WidgetSnapshot? = null
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
                val response = MockResponse().addHeader("X-Token", "tok").addHeader("2FA-Token", "tfa")
                val body = when {
                    path.endsWith("/login.awp") && "gtk=1" in query -> {
                        response.addHeader("X-GTK", "gtk")
                        "{}"
                    }
                    path.endsWith("/login.awp") -> loginBodies.removeFirst()
                    path.endsWith("/doubleauth.awp") && "verbe=get" in query -> challenge
                    path.endsWith("/doubleauth.awp") -> """{"code":200,"data":{"cn":"nouveau","cv":"nouveau"}}"""
                    path.endsWith("/emploidutemps.awp") -> """{"code":200,"data":[
                        {"id":1,"matiere":"PHILOSOPHIE","salle":"B204","start_date":"2026-11-05 10:15","end_date":"2026-11-05 12:00"},
                        {"id":2,"matiere":"MATHS","start_date":"2026-11-05 13:30","end_date":"2026-11-05 14:30","isAnnule":true}]}"""
                    path.endsWith("/cahierdetexte.awp") -> """{"code":200,"data":{"2026-11-06":[{"matiere":"MATHS","idDevoir":55,"effectue":false}]}}"""
                    path.endsWith("/notes.awp") -> """{"code":200,"data":{"periodes":[
                        {"idPeriode":"A1","periode":"T1","cloture":false,"isCurrent":true,"moyenneGenerale":"15","ensembleMatieres":{"disciplines":[]}}],
                        "notes":$notes}}"""
                    path.endsWith("/messages.awp") -> """{"code":200,"data":{"messages":{"received":[]}}}"""
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
        val outcome = service(MemoryStore(saved), MemoryPrefs(), MemoryState(), Recorder()).run()
        assertEquals(SyncOutcome.NeedsInteraction, outcome)
        assertEquals(0, paths.count { it.contains("/emploidutemps") })
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
