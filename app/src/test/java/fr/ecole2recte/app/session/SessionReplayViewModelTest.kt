package fr.ecole2recte.app.session

import fr.ecole2recte.app.data.Account
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.InMemoryTokenLog
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.SavedSession
import fr.ecole2recte.app.data.TokenLifetimes
import fr.ecole2recte.app.data.USER_AGENT
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class ReplayStore(vararg initial: SavedLogin) : CredentialStore {
    val logins = initial.toMutableList()
    override fun readAll() = logins.toList()
    override fun activeIdentifier() = logins.firstOrNull()?.identifier
    override fun save(login: SavedLogin, makeActive: Boolean) {
        val index = logins.indexOfFirst { it.identifier.equals(login.identifier, ignoreCase = true) }
        if (index >= 0) logins[index] = login else logins += login
    }

    override fun setActive(identifier: String) = Unit
    override fun remove(identifier: String) {
        logins.removeAll { it.identifier.equals(identifier, ignoreCase = true) }
    }

    override fun clear() = logins.clear()
}

/** Lancer l'app avec une session gardée : pas de connexion si le jeton est encore plausible. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionReplayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()
    private val now = System.currentTimeMillis()

    @Before
    fun start() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val query = request.requestUrl!!.encodedQuery.orEmpty()
                paths += path
                val response = MockResponse().addHeader("X-Token", "renouvele")
                return when {
                    path.endsWith("/login.awp") && "gtk=1" in query -> response.addHeader("X-GTK", "gtk-neuf").setBody("{}")
                    path.endsWith("/login.awp") ->
                        response.setBody("""{"code":200,"token":"jeton-neuf","data":{"accounts":[{"id":"10","typeCompte":"E","prenom":"Jean","nom":"DUPONT"}]}}""")
                    else -> response.setBody("""{"code":200,"data":[]}""")
                }
            }
        }
        server.start()
    }

    @After
    fun stop() {
        kotlinx.coroutines.Dispatchers.resetMain()
        runCatching { server.shutdown() }
    }

    private val accounts = listOf(Account("10", "E", "Jean", "DUPONT"))

    private fun stored(idleMs: Long) = SavedSession(
        token = "jeton-garde",
        cookies = mapOf("GTK" to "cookie"),
        gtk = "gtk-garde",
        userAgent = USER_AGENT,
        issuedAt = now - 3_600_000,
        lastOkAt = now - idleMs,
        accounts = accounts,
    )

    private fun login(session: SavedSession?) = SavedLogin("jdupont", "secret", session = session)

    private fun viewModel(store: ReplayStore, api: ApiSession, log: InMemoryTokenLog = InMemoryTokenLog()) = SessionViewModel(
        store,
        clientFactory = { EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')) },
        ioDispatcher = dispatcher,
        retryDelaysMs = emptyList(),
        apiSession = api,
        tokenLog = log,
        now = { now },
    )

    @Test
    fun `une session recente est rejouee et l app est connectee sans aucune requete`() = runTest(dispatcher) {
        val api = ApiSession()
        val vm = viewModel(ReplayStore(login(stored(idleMs = 4 * 60_000))), api)
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected, vm.state.value.connection)
        assertEquals(emptyList<String>(), paths)
        val ready = api.state.value as ApiSessionState.Ready
        assertEquals(accounts, ready.accounts)
    }

    @Test
    fun `une session trop ancienne se reconnecte normalement et se garde`() = runTest(dispatcher) {
        val store = ReplayStore(login(stored(idleMs = 3 * 3_600_000)))
        val vm = viewModel(store, ApiSession())
        advanceUntilIdle()

        assertEquals(ConnectionStatus.Connected, vm.state.value.connection)
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
        assertEquals("jeton-neuf", store.logins.single().session?.token)
    }

    @Test
    fun `un jeton qui expire en quelques minutes n'est plus jamais rejoue`() = runTest(dispatcher) {
        val log = InMemoryTokenLog(TokenLifetimes(expiredAfterAgeMs = 2 * 60_000))
        viewModel(ReplayStore(login(stored(idleMs = 60_000))), ApiSession(), log)
        advanceUntilIdle()
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
    }

    @Test
    fun `reessayer a la main fait une vraie connexion, jamais un rejeu`() = runTest(dispatcher) {
        val vm = viewModel(ReplayStore(login(stored(idleMs = 60_000))), ApiSession())
        advanceUntilIdle()
        assertTrue(paths.isEmpty())

        vm.retryConnection()
        advanceUntilIdle()
        assertEquals(2, paths.count { it.endsWith("/login.awp") })
    }

    @Test
    fun `quitter l'app garde le jeton renouvele, sans changer le reste du compte`() = runTest(dispatcher) {
        val api = ApiSession()
        val store = ReplayStore(login(stored(idleMs = 4 * 60_000)).copy(activeStudentId = "20"))
        val vm = viewModel(store, api)
        advanceUntilIdle()
        (api.state.value as ApiSessionState.Ready).client.call("/E/10/emploidutemps.awp", "get")

        vm.saveSession()

        val kept = store.logins.single()
        assertEquals("renouvele", kept.session?.token)
        assertTrue(kept.session!!.lastOkAt > now - 4 * 60_000)
        assertEquals("secret", kept.password)
        assertEquals("20", kept.activeStudentId)
        assertNotNull(kept.session)
    }
}
