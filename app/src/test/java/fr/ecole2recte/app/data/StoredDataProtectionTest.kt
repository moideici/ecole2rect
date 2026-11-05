package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.FileResponseCache
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.domain.StaleCause
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.settings.shouldSecureWindow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Un « chiffrement » réversible pour les tests : refuse ce qu'il n'a pas produit. */
internal class FakeCipher(private val tag: String = "A") : ValueCipher {
    override fun encrypt(value: String) = "$tag:" + Base64.getEncoder().encodeToString(value.toByteArray())
    override fun decrypt(value: String): String {
        require(value.startsWith("$tag:")) { "pas chiffré avec cette clé" }
        return String(Base64.getDecoder().decode(value.removePrefix("$tag:")))
    }
}

internal class MapBlobStorage : BlobStorage {
    val values = HashMap<String, String>()
    var writes = 0
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) {
        writes++
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clear() = values.clear()
}

class SecureCredentialStoreTest {
    private val storage = MapBlobStorage()
    private fun store(cipher: ValueCipher = FakeCipher()) = SecureCredentialStore(storage, cipher)

    private fun login(id: String, password: String = "secret-$id", student: String? = null) =
        SavedLogin(id, password, activeStudentId = student)

    @Test
    fun `les comptes se relisent et rien n'est lisible dans le stockage`() {
        val first = store()
        first.save(login("marie", "mot-de-passe-unique"))
        first.save(login("paul"), makeActive = false)

        val reread = store()
        assertEquals(listOf("marie", "paul"), reread.readAll().map { it.identifier })
        assertEquals("marie", reread.activeIdentifier())
        assertFalse(storage.values.values.any { "mot-de-passe-unique" in it })
    }

    @Test
    fun `des mises a jour simultanees de deux comptes ne se perdent pas`() {
        val store = store()
        store.save(login("a"))
        store.save(login("b"), makeActive = false)
        val start = CountDownLatch(1)
        val workers = listOf("a", "b").map { id ->
            thread {
                start.await()
                repeat(150) { n -> store.update(id) { it.copy(activeStudentId = "$id-$n") } }
            }
        }
        start.countDown()
        workers.forEach { it.join() }
        assertEquals("a-149", store.find("a")?.activeStudentId)
        assertEquals("b-149", store.find("b")?.activeStudentId)
    }

    @Test
    fun `update ne touche que le compte demande et garde le compte actif`() {
        val store = store()
        store.save(login("a"))
        store.save(login("b"), makeActive = false)
        store.update("b") { it.copy(password = "nouveau") }
        assertEquals("nouveau", store.find("b")?.password)
        assertEquals("secret-a", store.find("a")?.password)
        assertEquals("a", store.activeIdentifier())
        val writes = storage.writes
        store.update("inconnu") { it.copy(password = "x") }
        assertEquals(writes, storage.writes)
    }

    @Test
    fun `des comptes illisibles ne sont jamais ecrases par une mise a jour de fond`() {
        store().save(login("marie"))
        val before = storage.values["accounts"]
        val broken = store(FakeCipher("autre-cle"))

        assertTrue(broken.isUnreadable())
        assertEquals(emptyList<SavedLogin>(), broken.readAll())
        broken.update("marie") { it.copy(password = "x") }
        broken.setActive("marie")
        broken.remove("marie")

        assertEquals(before, storage.values["accounts"])
        // Avec la bonne clé, tout est encore là.
        assertEquals("secret-marie", store().find("marie")?.password)
    }

    @Test
    fun `se reconnecter apres des comptes illisibles repart proprement`() {
        store().save(login("marie"))
        val broken = store(FakeCipher("autre-cle"))
        assertTrue(broken.isUnreadable())
        broken.save(login("marie", "nouveau-mot-de-passe"))
        assertFalse(broken.isUnreadable())
        assertEquals("nouveau-mot-de-passe", broken.find("marie")?.password)
    }

    @Test
    fun `l'ancien format a un seul compte est migre`() {
        val legacy = FakeCipher().encrypt("""{"identifier":"ancien","password":"pw"}""")
        storage.values["login"] = legacy
        val migrated = store()
        assertEquals("ancien", migrated.active()?.identifier)
        migrated.save(login("nouveau"))
        assertNull(storage.values["login"])
    }

    @Test
    fun `une session gardee survit au stockage`() {
        val session = SavedSession("jeton", mapOf("GTK" to "g"), "gtk", "ua", 1, 2, listOf(Account("1", "E", "A", "B")))
        store().save(login("marie").copy(session = session))
        assertEquals(session, store().find("marie")?.session)
    }
}

class EncryptedCacheTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val owner = CacheOwner("J.Dupont", null)

    @Test
    fun `un fichier du cache n'est pas lisible en clair`() {
        val cache = FileResponseCache(folder.root, FakeCipher())
        cache.put(owner, "grades", """{"note":"17,5","matiere":"MATHS"}""", 42L)
        val raw = folder.root.walkTopDown().filter { it.isFile }.joinToString { it.readText() }
        assertFalse("17,5" in raw || "MATHS" in raw)
        assertEquals("""{"note":"17,5","matiere":"MATHS"}""", cache.get(owner, "grades")?.json)
        assertEquals(42L, cache.get(owner, "grades")?.at)
    }

    @Test
    fun `un fichier d'avant le chiffrement reste lisible puis est chiffre a la mise a jour`() {
        FileResponseCache(folder.root).put(owner, "homework", "[1]", 7L)
        val encrypted = FileResponseCache(folder.root, FakeCipher())
        assertEquals("[1]", encrypted.get(owner, "homework")?.json)
        encrypted.put(owner, "homework", "[2]", 8L)
        val raw = folder.root.walkTopDown().filter { it.isFile }.joinToString { it.readText() }
        assertFalse("[2]" in raw)
        assertEquals("[2]", encrypted.get(owner, "homework")?.json)
    }

    @Test
    fun `un fichier chiffre avec une autre cle est ignore, pas une erreur`() {
        FileResponseCache(folder.root, FakeCipher("A")).put(owner, "grades", "[1]", 1L)
        assertNull(FileResponseCache(folder.root, FakeCipher("B")).get(owner, "grades"))
    }
}

class WindowSecurityTest {
    @Test
    fun `l'apercu n'est masque que si le verrou est actif et l'option cochee`() {
        assertTrue(shouldSecureWindow(lockEnabled = true, hidePreview = true))
        assertFalse(shouldSecureWindow(lockEnabled = true, hidePreview = false))
        assertFalse(shouldSecureWindow(lockEnabled = false, hidePreview = true))
        assertFalse(shouldSecureWindow(lockEnabled = false, hidePreview = false))
    }
}

/** Le cache ne remplace jamais une bonne réponse par une réponse vide, et dit pourquoi il se replie. */
class CacheProtectionTest {
    private lateinit var server: MockWebServer
    private var timetable = good
    private var failure: (() -> MockResponse)? = null
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))
    private val cache = InMemoryResponseCache()
    private val profile = ActiveProfile("jdupont", null, "Jean", "Jean Dupont", "JD")

    private companion object {
        const val good = """{"code":200,"data":[{"id":1,"matiere":"PHILOSOPHIE","start_date":"2026-11-05 10:15","end_date":"2026-11-05 12:00"}]}"""
    }

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                failure?.let { return it() }
                val body = when {
                    path.endsWith("/emploidutemps.awp") -> timetable
                    path.endsWith("/cahierdetexte.awp") -> """{"code":200,"data":{}}"""
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

    private fun repository(): EcoleDirecteRepository {
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(Account("10", "E", "Jean", "DUPONT")))
        return EcoleDirecteRepository(session, profile, cache, clock = clock, ttlMs = 0)
    }

    private fun owner() = CacheOwner("jdupont", null)

    @Test
    fun `une reponse sans contenu ne remplace pas la bonne reponse enregistree`() = runTest {
        val repository = repository()
        assertEquals(1, repository.schedule().entries.size)
        val stored = cache.get(owner(), "lessons")!!.json

        timetable = """{"code":200}""" // data absent : « null »
        assertEquals(1, repository.schedule().entries.size) // l'écran garde les cours enregistrés
        assertEquals(stored, cache.get(owner(), "lessons")!!.json)
        assertTrue(repository.freshness.value.stale)
        assertEquals(StaleCause.Unusable, repository.freshness.value.cause)
    }

    @Test
    fun `des cours tous rejetes par le lecteur ne remplacent pas non plus la bonne reponse`() = runTest {
        val repository = repository()
        repository.schedule()
        val stored = cache.get(owner(), "lessons")!!.json

        timetable = """{"code":200,"data":[{"id":1,"matiere":"X"},{"id":2,"matiere":"Y"}]}""" // aucune date
        assertEquals(1, repository.schedule().entries.size)
        assertEquals(stored, cache.get(owner(), "lessons")!!.json)
        assertEquals(StaleCause.Unusable, repository.freshness.value.cause)
    }

    @Test
    fun `une semaine vraiment vide remplace l ancienne car ce sont les vacances`() = runTest {
        val repository = repository()
        repository.schedule()
        timetable = """{"code":200,"data":[]}"""
        assertEquals(0, repository.schedule().entries.size)
        assertEquals("[]", cache.get(owner(), "lessons")!!.json)
        assertFalse(repository.freshness.value.stale)
    }

    @Test
    fun `sans rien d'enregistre une reponse vide s'affiche vide et n'est pas gardee`() = runTest {
        timetable = """{"code":200}"""
        assertEquals(0, repository().schedule().entries.size)
        assertNull(cache.get(owner(), "lessons"))
    }

    @Test
    fun `la cause du repli est gardee pour le reseau, le blocage et la version refusee`() = runTest {
        val repository = repository()
        repository.schedule()

        failure = { MockResponse().setBody("""{"code":516}""") }
        repository.schedule()
        assertEquals(StaleCause.Blocked, repository.freshness.value.cause)

        failure = { MockResponse().setBody("""{"code":517}""") }
        repository.schedule()
        assertEquals(StaleCause.StaleVersion, repository.freshness.value.cause)

        failure = { MockResponse().setResponseCode(503) }
        repository.schedule()
        assertEquals(StaleCause.Service, repository.freshness.value.cause)

        server.shutdown()
        failure = null
        repository.schedule()
        assertEquals(StaleCause.Network, repository.freshness.value.cause)
        assertNotEquals(null, repository.freshness.value.lastSyncMillis)
    }
}
