package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.domain.HomeworkWrite
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.ui.more.WriteFeature
import fr.ecole2recte.app.ui.more.needsWriteConfirmation
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Lot K : cocher un devoir chez ÉcoleDirecte. Tout se passe contre un faux serveur local : aucune
 * écriture réelle n'est faite, ni ne peut l'être, par ces tests.
 */
class HomeworkSyncTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private val bodies = mutableListOf<String>()
    private var serverDone = false
    private var putAnswer = """{"code":200,"data":{}}"""
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath
                val verb = request.requestUrl!!.queryParameter("verbe")
                if (verb == "put") bodies += URLDecoder.decode(request.body.readUtf8().removePrefix("data="), "UTF-8")
                val body = when {
                    verb == "put" -> putAnswer
                    path.endsWith("/cahierdetexte.awp") -> """{"code":200,"data":{"2026-11-06":[{"matiere":"MATHS","idDevoir":55,"effectue":$serverDone}]}}"""
                    path.endsWith("/emploidutemps.awp") -> """{"code":200,"data":[]}"""
                    path.endsWith("/notes.awp") -> """{"code":200,"data":{"periodes":[],"notes":[]}}"""
                    path.endsWith("/messages.awp") -> """{"code":200,"data":{"messages":{"received":[]}}}"""
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

    private val store = InMemoryHomeworkStatusStore()

    private fun repository(allowed: () -> Boolean, studentId: String? = null, account: Account = Account("10", "E", "Jean", "DUPONT")): EcoleDirecteRepository {
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(account))
        val writes = object : WriteAccess {
            override fun homeworkSync() = allowed()
            override fun messageSending() = false
        }
        return EcoleDirecteRepository(
            session, ActiveProfile("jdupont", studentId, "Jean", "Jean Dupont", "JD"), InMemoryResponseCache(), store, clock, writes = writes,
        )
    }

    /** Les deux listes envoyées : (cochés, décochés). L'ordre des clés du JSON n'a pas d'importance. */
    private fun lists(body: String): List<List<Int>> {
        val json = org.json.JSONObject(body)
        fun ids(name: String) = (0 until json.getJSONArray(name).length()).map { json.getJSONArray(name).getInt(it) }
        return listOf(ids("idDevoirsEffectues"), ids("idDevoirsNonEffectues"))
    }

    private val owner = CacheOwner("jdupont", null)
    private fun puts() = requests.filter { it.requestUrl!!.queryParameter("verbe") == "put" }

    @Test
    fun `par defaut cocher reste sur le telephone et rien n'est ecrit`() = runTest {
        val repository = repository(allowed = { false })
        assertEquals(HomeworkWrite.LocalOnly, repository.setHomeworkDone("55", true))
        assertEquals(emptyList<RecordedRequest>(), requests)
        assertEquals(mapOf("55" to true), store.overrides(owner))
    }

    @Test
    fun `sans autorisation explicite aucune ecriture n'existe`() = runTest {
        // Le dépôt construit sans WriteAccess (synchro de fond, vue des autres enfants) ne peut jamais écrire.
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(Account("10", "E", "J", "D")))
        val repository = EcoleDirecteRepository(session, ActiveProfile("jdupont", null, "J", "J D", "JD"), InMemoryResponseCache(), store, clock)
        assertEquals(HomeworkWrite.LocalOnly, repository.setHomeworkDone("55", true))
        assertEquals(0, puts().size)
    }

    @Test
    fun `autorisee, une seule ecriture part avec le meme appel que le site`() = runTest {
        val repository = repository(allowed = { true })
        assertEquals(HomeworkWrite.Synced, repository.setHomeworkDone("55", true))

        val put = puts().single()
        assertEquals("/v3/Eleves/10/cahierdetexte.awp", put.requestUrl!!.encodedPath)
        assertEquals("put", put.requestUrl!!.queryParameter("verbe"))
        assertEquals(listOf(listOf(55), emptyList<Int>()), bodies.map { lists(it) }.single())
    }

    @Test
    fun `decocher est l'ecriture inverse et permet de revenir en arriere`() = runTest {
        val repository = repository(allowed = { true })
        repository.setHomeworkDone("55", true)
        repository.setHomeworkDone("55", false)
        assertEquals(
            listOf(listOf(listOf(55), emptyList<Int>()), listOf(emptyList<Int>(), listOf(55))),
            bodies.map { lists(it) },
        )
    }

    @Test
    fun `apres un succes le serveur redevient la reference`() = runTest {
        val repository = repository(allowed = { true })
        assertEquals(false, repository.homework().single().done)
        serverDone = true
        repository.setHomeworkDone("55", true)
        assertEquals(emptyMap<String, Boolean>(), store.overrides(owner))
        assertEquals(true, repository.homework().single().done)
    }

    @Test
    fun `un echec garde la case sur le telephone et ne renvoie rien tout seul`() = runTest {
        putAnswer = """{"code":403,"message":"Accès refusé"}"""
        val repository = repository(allowed = { true })
        assertEquals(HomeworkWrite.SyncFailed, repository.setHomeworkDone("55", true))
        assertEquals(mapOf("55" to true), store.overrides(owner))
        assertEquals(true, repository.homework().single().done)
        // Une seule tentative, jamais rejouée par l'application.
        repository.homework()
        assertEquals(1, puts().size)
    }

    @Test
    fun `une panne reseau est un echec local, pas un plantage`() = runTest {
        val repository = repository(allowed = { true })
        server.shutdown()
        assertEquals(HomeworkWrite.SyncFailed, repository.setHomeworkDone("55", true))
        assertEquals(mapOf("55" to true), store.overrides(owner))
    }

    @Test
    fun `couper le reglage coupe l'ecriture aussitot`() = runTest {
        var allowed = true
        val repository = repository(allowed = { allowed })
        repository.setHomeworkDone("55", true)
        allowed = false
        assertEquals(HomeworkWrite.LocalOnly, repository.setHomeworkDone("55", false))
        assertEquals(1, puts().size)
    }

    @Test
    fun `lire ne fait jamais d'ecriture`() = runTest {
        val repository = repository(allowed = { true })
        repository.home()
        repository.schedule()
        repository.homework()
        repository.grades()
        repository.messagesPage(0)
        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.requestUrl!!.queryParameter("verbe") == "get" })
    }

    @Test
    fun `l'ecriture vise l'enfant choisi d'un compte famille`() = runTest {
        val family = Account("1", "1", "Marie", "DUPONT", students = listOf(Student("20", "Léa", "D"), Student("21", "Tom", "D")))
        repository(allowed = { true }, studentId = "21", account = family).setHomeworkDone("55", true)
        assertEquals("/v3/Eleves/21/cahierdetexte.awp", puts().single().requestUrl!!.encodedPath)
    }

    @Test
    fun `la confirmation n'est demandee qu'a la premiere activation`() {
        assertTrue(needsWriteConfirmation(turningOn = true, acknowledged = false))
        assertFalse(needsWriteConfirmation(turningOn = true, acknowledged = true))
        assertFalse(needsWriteConfirmation(turningOn = false, acknowledged = false))
    }

    @Test
    fun `les deux fonctions d'ecriture expliquent ce qu'elles font`() {
        WriteFeature.entries.forEach {
            assertTrue(it.confirmText.length > 50)
        }
        assertTrue("Rien d'autre n'est envoyé" in WriteFeature.HomeworkSync.confirmText)
        assertTrue("rien ne part sans ton toucher" in WriteFeature.MessageSending.confirmText)
    }
}
