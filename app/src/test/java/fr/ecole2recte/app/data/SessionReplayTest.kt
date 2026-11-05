package fr.ecole2recte.app.data

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val MINUTE = 60_000L

class TokenReplayPolicyTest {
    private fun session(idleMs: Long, ageMs: Long = 60 * MINUTE, now: Long = 1_000 * MINUTE, userAgent: String = USER_AGENT) = SavedSession(
        token = "t",
        cookies = emptyMap(),
        gtk = "g",
        userAgent = userAgent,
        issuedAt = now - ageMs,
        lastOkAt = now - idleMs,
        accounts = listOf(Account("1", "E", "A", "B")),
    )

    private val now = 1_000 * MINUTE

    private fun can(session: SavedSession, known: TokenLifetimes = TokenLifetimes()) =
        TokenReplayPolicy.canReplay(session, now, known, USER_AGENT)

    @Test
    fun `sans aucune mesure on ne rejoue qu'une session de moins de dix minutes`() {
        assertTrue(can(session(idleMs = 2 * MINUTE)))
        assertTrue(can(session(idleMs = 10 * MINUTE)))
        assertFalse(can(session(idleMs = 11 * MINUTE)))
    }

    @Test
    fun `un rejeu reussi elargit la fenetre, jusqu'a quatre heures`() {
        val known = TokenLifetimes(replayedAfterIdleMs = 30 * MINUTE)
        assertTrue(can(session(idleMs = 59 * MINUTE), known))
        assertFalse(can(session(idleMs = 61 * MINUTE), known))
        val huge = TokenLifetimes(replayedAfterIdleMs = 10 * 60 * MINUTE)
        assertEquals(TokenReplayPolicy.MAX_WINDOW_MS, TokenReplayPolicy.idleWindowMs(huge))
    }

    @Test
    fun `une expiration constatee reduit la fenetre`() {
        val known = TokenLifetimes(expiredAfterIdleMs = 5 * MINUTE)
        assertEquals(4 * MINUTE, TokenReplayPolicy.idleWindowMs(known))
        assertTrue(can(session(idleMs = 3 * MINUTE), known))
        assertFalse(can(session(idleMs = 5 * MINUTE), known))
    }

    @Test
    fun `un jeton qui expire en quelques minutes n'est jamais rejoue`() {
        assertFalse(can(session(idleMs = 0), TokenLifetimes(expiredAfterAgeMs = 2 * MINUTE)))
        assertEquals(0L, TokenReplayPolicy.idleWindowMs(TokenLifetimes(expiredAfterIdleMs = 2 * MINUTE)))
    }

    @Test
    fun `une limite absolue constatee protege les vieilles connexions`() {
        val known = TokenLifetimes(expiredAfterAgeMs = 120 * MINUTE)
        assertTrue(can(session(idleMs = MINUTE, ageMs = 90 * MINUTE), known))
        assertFalse(can(session(idleMs = MINUTE, ageMs = 100 * MINUTE), known))
    }

    @Test
    fun `une autre empreinte reseau empeche le rejeu`() {
        assertFalse(can(session(idleMs = MINUTE, userAgent = "Autre/1.0")))
    }

    @Test
    fun `une horloge reculee ou un jeton vide empechent le rejeu`() {
        assertFalse(can(session(idleMs = -5 * MINUTE)))
        assertFalse(can(session(idleMs = MINUTE).copy(token = "")))
    }

    @Test
    fun `le journal retient le plus court des jetons expires et le plus long des rejeux`() {
        val log = InMemoryTokenLog()
        log.recordExpiry(idleMs = 40 * MINUTE, ageMs = 90 * MINUTE)
        log.recordExpiry(idleMs = 25 * MINUTE, ageMs = 200 * MINUTE)
        log.recordReplay(8 * MINUTE)
        log.recordReplay(3 * MINUTE)
        assertEquals(TokenLifetimes(25 * MINUTE, 90 * MINUTE, 8 * MINUTE), log.load())
    }

    @Test
    fun `une expiration juste apres un appel reussi ne dit rien de l'inactivite`() {
        val log = InMemoryTokenLog()
        log.recordExpiry(idleMs = 20_000, ageMs = 180 * MINUTE)
        assertNull(log.load().expiredAfterIdleMs)
        assertEquals(180 * MINUTE, log.load().expiredAfterAgeMs)
    }

    @Test
    fun `la session gardee fait l'aller-retour du stockage sans rien perdre`() {
        val original = SavedSession(
            token = "jeton",
            cookies = mapOf("GTK" to "abc", "autre" to "x=y"),
            gtk = "gtk",
            userAgent = USER_AGENT,
            issuedAt = 1234,
            lastOkAt = 5678,
            accounts = listOf(Account("10", "1", "Marie", "DUPONT", listOf(Student("20", "Léa", "DUPONT", "6e A")))),
        )
        assertEquals(original, original.toJson().toSavedSession())
        assertNull(org.json.JSONObject().toSavedSession())
    }

    @Test
    fun `la plus recente de deux sessions l'emporte`() {
        val old = session(idleMs = 30 * MINUTE)
        val fresh = session(idleMs = MINUTE)
        assertEquals(fresh, old.newest(fresh))
        assertEquals(fresh, fresh.newest(old))
        assertEquals(fresh, fresh.newest(null))
    }
}

/** Rejouer une session gardée : le client envoie le jeton et les cookies sans se connecter. */
class SessionReplayClientTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()
    private val dataRequests = mutableListOf<RecordedRequest>()
    private var dataBody = """{"code":200,"data":[]}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val query = request.requestUrl!!.encodedQuery.orEmpty()
                paths += path
                val response = MockResponse()
                return when {
                    path.endsWith("/login.awp") && "gtk=1" in query ->
                        response.addHeader("X-GTK", "gtk-1").addHeader("Set-Cookie", "GTK=cookie-1; Path=/").setBody("{}")
                    path.endsWith("/login.awp") ->
                        response.setBody("""{"code":200,"token":"jeton-1","data":{"accounts":[{"id":"10","typeCompte":"E","prenom":"Jean","nom":"DUPONT"}]}}""")
                    else -> {
                        dataRequests += request
                        response.addHeader("X-Token", "jeton-2").setBody(dataBody)
                    }
                }
            }
        }
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun client() = EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/'))

    private fun loggedIn(): Pair<EcoleDirecteClient, SavedSession> {
        val client = client()
        val result = client.login("jdupont", "secret", null) as LoginResult.Success
        return client to client.snapshot(result.accounts)!!
    }

    @Test
    fun `apres une connexion on peut garder la session`() {
        val (_, session) = loggedIn()
        assertEquals("jeton-1", session.token)
        assertEquals("gtk-1", session.gtk)
        assertEquals("cookie-1", session.cookies["GTK"])
        assertEquals(USER_AGENT, session.userAgent)
        assertTrue(session.issuedAt > 0 && session.lastOkAt >= session.issuedAt)
        assertEquals(listOf("10"), session.accounts.map { it.id })
    }

    @Test
    fun `sans connexion il n'y a rien a garder`() {
        assertNull(client().snapshot(listOf(Account("10", "E", "J", "D"))))
    }

    @Test
    fun `un client restaure appelle l'API avec le jeton et les cookies, sans se connecter`() {
        val (_, session) = loggedIn()
        paths.clear()
        val replayed = client()
        assertTrue(replayed.restore(session.copy(lastOkAt = System.currentTimeMillis() - 5 * MINUTE)))
        replayed.call("/E/10/emploidutemps.awp", "get")

        assertEquals(0, paths.count { it.endsWith("/login.awp") })
        val request = dataRequests.single()
        assertEquals("jeton-1", request.getHeader("X-Token"))
        assertTrue(request.getHeader("Cookie")!!.contains("GTK=cookie-1"))
        assertEquals("gtk-1", request.getHeader("X-GTK"))
        // Le jeton renouvelé par ÉcoleDirecte remplace l'ancien dans la session à garder.
        assertEquals("jeton-2", replayed.snapshot(session.accounts)!!.token)
    }

    @Test
    fun `le premier appel reussi confirme le rejeu avec l'inactivite, une seule fois`() {
        val (_, session) = loggedIn()
        val confirmations = mutableListOf<Long>()
        val replayed = client()
        replayed.restore(session.copy(lastOkAt = System.currentTimeMillis() - 7 * MINUTE)) { confirmations += it }
        replayed.call("/E/10/emploidutemps.awp", "get")
        replayed.call("/E/10/emploidutemps.awp", "get")
        assertEquals(1, confirmations.size)
        assertTrue(confirmations.single() in 7 * MINUTE..8 * MINUTE)
    }

    @Test
    fun `un jeton refuse n'est pas confirme et l'appel signale l'expiration`() {
        val (_, session) = loggedIn()
        dataBody = """{"code":520}"""
        val confirmations = mutableListOf<Long>()
        val replayed = client()
        replayed.restore(session) { confirmations += it }
        try {
            replayed.call("/E/10/emploidutemps.awp", "get")
            fail("l'expiration aurait dû être signalée")
        } catch (expected: SessionExpiredException) {
            assertTrue(confirmations.isEmpty())
        }
    }

    @Test
    fun `une session d'une autre empreinte reseau n'est pas restauree`() {
        val (_, session) = loggedIn()
        assertFalse(client().restore(session.copy(userAgent = "Autre/1.0")))
    }

    @Test
    fun `le code 517 est reconnu comme une version d'API refusee`() {
        val (client, _) = loggedIn()
        dataBody = """{"code":517}"""
        try {
            client.call("/E/10/emploidutemps.awp", "get")
            fail("517 aurait dû lever une erreur")
        } catch (error: EcoleDirecteException) {
            assertEquals(FailureKind.StaleVersion, error.kind)
            assertNotNull(error.message)
        }
    }
}
