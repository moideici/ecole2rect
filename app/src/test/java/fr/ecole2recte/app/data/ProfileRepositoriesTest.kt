package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.data.demo.DemoSchoolRepository
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.ui.common.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Lot B : un seul dépôt par profil, partagé par tous les écrans. */
class ProfileRepositoriesTest {
    private fun profile(id: String = "jdupont", student: String? = null) =
        ActiveProfile(id, student, "Jean", "Jean Dupont", "JD")

    @Test
    fun `le meme profil donne toujours le meme depot`() {
        val registry = ProfileRepositories { DemoSchoolRepository() }
        assertSame(registry.get(profile()), registry.get(profile()))
    }

    @Test
    fun `la casse de l'identifiant ne cree pas un second depot`() {
        val registry = ProfileRepositories { DemoSchoolRepository() }
        assertSame(registry.get(profile("JDupont")), registry.get(profile("jdupont")))
    }

    @Test
    fun `chaque eleve d'un compte famille a son depot`() {
        val registry = ProfileRepositories { DemoSchoolRepository() }
        assertNotSame(registry.get(profile(student = "10")), registry.get(profile(student = "11")))
        assertNotSame(registry.get(profile("autre")), registry.get(profile()))
    }

    @Test
    fun `retirer un compte oublie ses depots et ceux de ses eleves, pas les autres`() {
        val registry = ProfileRepositories { DemoSchoolRepository() }
        val first = registry.get(profile(student = "10"))
        val second = registry.get(profile(student = "11"))
        val other = registry.get(profile("autre"))
        registry.forget("JDupont")
        assertNotSame(first, registry.get(profile(student = "10")))
        assertNotSame(second, registry.get(profile(student = "11")))
        assertSame(other, registry.get(profile("autre")))
    }

    @Test
    fun `un compte dont l'identifiant en prefixe un autre n'est pas oublie par erreur`() {
        val registry = ProfileRepositories { DemoSchoolRepository() }
        val longer = registry.get(profile("jdupont2"))
        registry.forget("jdupont")
        assertSame(longer, registry.get(profile("jdupont2")))
    }
}

/** Deux écrans qui partagent le dépôt n'envoient qu'une seule requête à ÉcoleDirecte. */
class SharedRepositoryRequestsTest {
    private lateinit var server: MockWebServer
    private val timetableRequests = AtomicInteger()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val body = when {
                    path.endsWith("/emploidutemps.awp") -> {
                        timetableRequests.incrementAndGet()
                        """{"code":200,"data":[{"id":1,"matiere":"PHILOSOPHIE","start_date":"2026-11-05 10:15","end_date":"2026-11-05 12:00"}]}"""
                    }
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

    @Test
    fun `l'accueil puis l'agenda sur le meme depot lisent l'emploi du temps une seule fois`() = runTest {
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(Account("10", "E", "Jean", "DUPONT")))
        val registry = ProfileRepositories { profile ->
            EcoleDirecteRepository(session, profile, InMemoryResponseCache(), clock = clock)
        }
        val profile = ActiveProfile("jdupont", null, "Jean", "Jean Dupont", "JD")

        registry.get(profile).home()
        registry.get(profile).schedule()

        assertEquals(1, timetableRequests.get())
    }
}

/** Retour au premier plan : on relit seulement ce qui a plus de dix minutes. */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshIfStaleTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    private class Counting(val loads: AtomicInteger) : LoadingViewModel<Int>({ loads.incrementAndGet() })

    @Test
    fun `des donnees fraiches ne sont pas relues`() {
        val loads = AtomicInteger()
        val viewModel = Counting(loads)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, loads.get())

        viewModel.refreshIfStale(now = System.currentTimeMillis() + 60_000)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, loads.get())
    }

    @Test
    fun `des donnees de plus de dix minutes sont relues une fois`() {
        val loads = AtomicInteger()
        val viewModel = Counting(loads)
        dispatcher.scheduler.advanceUntilIdle()

        val later = System.currentTimeMillis() + 11 * 60_000
        viewModel.refreshIfStale(now = later)
        viewModel.refreshIfStale(now = later) // deux retours coup sur coup : une seule requête
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, loads.get())
    }

    @Test
    fun `rien n'est relu tant que le premier chargement n'a pas eu lieu`() {
        val loads = AtomicInteger()
        val viewModel = Counting(loads)
        viewModel.refreshIfStale(now = Long.MAX_VALUE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, loads.get())
    }
}
