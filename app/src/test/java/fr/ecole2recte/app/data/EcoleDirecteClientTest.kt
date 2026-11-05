package fr.ecole2recte.app.data

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/** Teste le client contre un faux serveur local : aucun compte réel, aucun accès à ÉcoleDirecte. */
class EcoleDirecteClientTest {
    private lateinit var server: MockWebServer
    private val loginBodies = ArrayDeque<String>()
    private val loginRequests = mutableListOf<String>()
    private var challengeBody = ""
    private var answerBody = ""

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun client() = EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/'))

    private fun handle(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath
        val query = request.requestUrl!!.encodedQuery.orEmpty()
        val requestBody = request.body.readUtf8()
        val response = MockResponse()
            .addHeader("X-Token", "token-test")
            .addHeader("2FA-Token", "deuxfa-test")
        val body = when {
            path.endsWith("/login.awp") && "gtk=1" in query -> {
                response.addHeader("X-GTK", "gtk-test")
                "{}"
            }
            path.endsWith("/login.awp") -> {
                loginRequests += requestBody
                loginBodies.removeFirst()
            }
            path.endsWith("/doubleauth.awp") && "verbe=get" in query -> challengeBody
            path.endsWith("/doubleauth.awp") -> answerBody
            else -> ""
        }
        return response.setBody(body)
    }

    private fun b64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())

    @Test
    fun `200 renvoie les comptes`() {
        loginBodies += """{"code":200,"token":"abc","data":{"accounts":[
            {"id":"42","typeCompte":"E","prenom":"Jean","nom":"Dupont"}]}}"""
        val result = client().login("jdupont", "secret", null)
        val accounts = (result as LoginResult.Success).accounts
        assertEquals("Jean", accounts.single().firstName)
        assertEquals("42", accounts.single().id)
    }

    @Test
    fun `250 renvoie la question de securite decodee`() {
        loginBodies += """{"code":250}"""
        challengeBody = """{"code":200,"data":{"question":"${b64("Quel est ton mois de naissance ?")}",
            "propositions":["${b64("Janvier")}","${b64("Février")}"]}}"""
        val result = client().login("jdupont", "secret", null) as LoginResult.SecurityQuestionRequired
        assertEquals("Quel est ton mois de naissance ?", result.question)
        assertEquals(listOf("Janvier", "Février"), result.choices)
    }

    @Test
    fun `505 est un identifiant refuse`() {
        loginBodies += """{"code":505,"message":"Identifiant incorrect"}"""
        val result = client().login("jdupont", "faux", null) as LoginResult.Failure
        assertEquals(FailureKind.InvalidCredentials, result.kind)
    }

    @Test
    fun `516 et 535 sont des comptes bloques`() {
        loginBodies += """{"code":516}"""
        loginBodies += """{"code":535}"""
        val first = client().login("jdupont", "x", null) as LoginResult.Failure
        val second = client().login("jdupont", "x", null) as LoginResult.Failure
        assertEquals(FailureKind.Blocked, first.kind)
        assertEquals(FailureKind.Blocked, second.kind)
    }

    @Test
    fun `520 garde le message du serveur`() {
        loginBodies += """{"code":520,"message":"Jeton invalide"}"""
        val result = client().login("jdupont", "x", null) as LoginResult.Failure
        assertEquals("Jeton invalide", result.message)
        assertEquals(FailureKind.Other, result.kind)
    }

    @Test
    fun `une reponse vide est une panne du service`() {
        loginBodies += ""
        val result = client().login("jdupont", "x", null) as LoginResult.Failure
        assertEquals(FailureKind.Service, result.kind)
    }

    @Test
    fun `repondre a la question enregistre la reponse de confiance puis se reconnecte avec`() {
        loginBodies += """{"code":250}"""
        loginBodies += """{"code":200,"token":"abc","data":{"accounts":[]}}"""
        challengeBody = """{"code":200,"data":{"question":"${b64("Mois ?")}","propositions":["${b64("Février")}"]}}"""
        answerBody = """{"code":200,"data":{"cn":"cn-test","cv":"cv-test"}}"""
        val client = client()
        client.login("jdupont", "secret", null)
        val result = client.completeSecurityQuestion("Février", "jdupont", "secret")
        assertTrue(result is LoginResult.Success)
        assertEquals(TrustedAnswer("cn-test", "cv-test"), client.approvedAnswer)
        assertTrue("la reconnexion doit présenter la réponse de confiance", loginRequests.last().contains("cn-test"))
    }

    @Test
    fun `sans reseau l'echec est de type reseau`() {
        val client = client()
        server.shutdown()
        val error = runCatching { client.login("jdupont", "x", null) }.exceptionOrNull() as EcoleDirecteException
        assertEquals(FailureKind.Network, error.kind)
    }
}
