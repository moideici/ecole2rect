package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.api.Recipients
import fr.ecole2recte.app.data.api.Parsers
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.domain.MessageDraft
import fr.ecole2recte.app.domain.Recipient
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/**
 * Lot L : envoyer un message. Tout passe par un faux serveur local : aucun message réel n'est, ni ne
 * peut être, envoyé par ces tests.
 */
class MessageSendingTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private val sentBodies = mutableListOf<String>()
    private val sentBytes = mutableListOf<ByteArray>()
    private var postAnswer = """{"code":200,"data":{}}"""
    private var postResponse: (() -> MockResponse)? = null
    private val clock = Clock.fixed(Instant.parse("2026-11-05T08:50:00Z"), ZoneId.of("Europe/Paris"))

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath
                if (request.requestUrl!!.queryParameter("verbe") == "post") {
                    val bytes = request.body.readByteArray()
                    sentBytes += bytes
                    sentBodies += URLDecoder.decode(String(bytes, Charsets.UTF_8).removePrefix("data="), "UTF-8")
                    return postResponse?.invoke() ?: MockResponse().setBody(postAnswer)
                }
                val body = when {
                    path.endsWith("/professeurs.awp") ->
                        """{"code":200,"data":[{"id":12,"civilite":"M.","prenom":"Paul","nom":"PETIT","type":"P"},{"id":7,"prenom":"Anne","nom":"ABEL"}]}"""
                    path.endsWith("/administrations.awp") -> """{"code":200,"data":{"administrations":[{"id":3,"libelle":"Vie scolaire"}]}}"""
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

    private val paul = Recipient("12", "M. Paul PETIT", "P", "Enseignants")
    private val vieScolaire = Recipient("3", "Vie scolaire", "A", "Administration")
    private val draft = MessageDraft(listOf(paul, vieScolaire), "Absence du 6 novembre", "Bonjour,\nTom sera absent <jeudi> & vendredi.\nMerci.")

    private fun repository(allowed: () -> Boolean, account: Account = Account("10", "E", "Jean", "DUPONT"), configureSession: (ApiSession) -> Unit = {}): EcoleDirecteRepository {
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(account))
        configureSession(session)
        val writes = object : WriteAccess {
            override fun homeworkSync() = false
            override fun messageSending() = allowed()
        }
        return EcoleDirecteRepository(session, ActiveProfile("jdupont", null, "Jean", "Jean Dupont", "JD"), InMemoryResponseCache(), clock = clock, writes = writes)
    }

    private fun posts() = requests.filter { it.requestUrl!!.queryParameter("verbe") == "post" }

    private fun expectFailure(block: suspend () -> Unit, containing: String) = runTest {
        try {
            block()
            fail("l'envoi aurait dû être refusé")
        } catch (error: EcoleDirecteException) {
            assertTrue(error.message!!, containing in error.message!!)
        }
    }

    @Test
    fun `reglage eteint donc aucun envoi et aucun contact demande`() {
        val repository = repository(allowed = { false })
        expectFailure({ repository.sendMessage(draft, confirmed = true) }, "désactivé")
        expectFailure({ repository.recipients() }, "désactivé")
        assertEquals(emptyList<RecordedRequest>(), requests)
    }

    @Test
    fun `sans confirmation rien ne part, meme avec le reglage allume`() {
        expectFailure({ repository(allowed = { true }).sendMessage(draft, confirmed = false) }, "confirmé")
        assertEquals(emptyList<RecordedRequest>(), requests)
    }

    @Test
    fun `un message incomplet ne part pas`() {
        val repository = repository(allowed = { true })
        expectFailure({ repository.sendMessage(draft.copy(recipients = emptyList()), true) }, "destinataire")
        expectFailure({ repository.sendMessage(draft.copy(subject = " "), true) }, "objet")
        expectFailure({ repository.sendMessage(draft.copy(body = ""), true) }, "message")
        expectFailure({ repository.sendMessage(draft.copy(subject = "x".repeat(MessageDraft.MAX_SUBJECT + 1)), true) }, "long")
        expectFailure({ repository.sendMessage(draft.copy(body = "x".repeat(MessageDraft.MAX_BODY + 1)), true) }, "long")
        expectFailure({ repository.sendMessage(draft.copy(recipients = List(MessageDraft.MAX_RECIPIENTS + 1) { paul }), true) }, "destinataires")
        assertEquals(0, requests.size)
    }

    @Test
    fun `les octets de la requete restent identiques a l instantane fictif`() = runTest {
        repository(allowed = { true }).sendMessage(draft, confirmed = true)
        val expected = javaClass.getResource("/captures/message_send_request.form")!!.readText().trimEnd('\r', '\n').toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, sentBytes.single())
        assertEquals("application/x-www-form-urlencoded", posts().single().getHeader("Content-Type"))
    }

    @Test
    fun `une expiration pendant l envoi ne rouvre pas la session et ne rejoue pas le message`() {
        var reauthentications = 0
        val repository = repository(allowed = { true }, configureSession = { session ->
            session.reauthenticator = { reauthentications++; true }
        })
        postAnswer = """{"code":520}"""
        expectFailure({ repository.sendMessage(draft, true) }, "expiré")
        assertEquals(1, posts().size)
        assertEquals(0, reauthentications)
    }

    @Test
    fun `une coupure apres reception des octets ne declenche aucun second envoi`() {
        postResponse = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        expectFailure({ repository(allowed = { true }).sendMessage(draft, true) }, "réseau")
        assertEquals(1, posts().size)
    }

    @Test
    fun `une redirection ne transporte jamais le message vers une seconde adresse`() {
        postResponse = { MockResponse().setResponseCode(307).addHeader("Location", server.url("/v3/eleves/10/messages.awp?verbe=post")) }
        expectFailure({ repository(allowed = { true }).sendMessage(draft, true) }, "")
        assertEquals(1, requests.size)
    }

    @Test
    fun `la recherche de destinataires utilise une fixture fictive sans accents ni casse`() {
        val json = JSONObject(javaClass.getResource("/captures/recipients.json")!!.readText())
        val contacts = Recipients.parse(json, "Enseignants", "P")
        assertEquals(3, contacts.size)
        assertEquals(listOf("Élodie NOËL"), Recipients.search(contacts, "  ELODIE   noel ").map { it.name })
        assertEquals(contacts, Recipients.search(contacts, " "))
        assertTrue(Recipients.search(contacts, "inconnu").isEmpty())
    }

    @Test
    fun `la reponse reprend uniquement un expediteur identifie et son objet`() {
        val json = JSONObject(javaClass.getResource("/captures/message_reply.json")!!.readText())
        val detail = Parsers.messageDetail(json)!!
        assertNotNull(detail.replyTo)
        assertEquals("77", detail.replyTo!!.id)
        assertEquals("P", detail.replyTo!!.type)
        val reply = MessageDraft.replyTo(detail.subject, detail.replyTo!!)
        assertEquals(listOf(detail.replyTo), reply.recipients)
        assertEquals("Re : Correction du DM", reply.subject)
        assertEquals("", reply.body)
        assertEquals("Re : Correction du DM", MessageDraft.replyTo(reply.subject, detail.replyTo!!).subject)
        json.getJSONObject("from").remove("id")
        assertNull(Parsers.messageDetail(json)!!.replyTo)
    }

    @Test
    fun `un echec ne pretend pas que le serveur n a rien recu`() {
        val notice = MessageDraft.failureNotice("Connexion réseau impossible.")
        assertTrue(notice.contains("brouillon"))
        assertTrue(notice.contains("Vérifie"))
        assertFalse(notice.contains("Rien n'est parti"))
    }

    @Test
    fun `confirme et autorise donc un seul envoi, texte protege et encode`() = runTest {
        repository(allowed = { true }).sendMessage(draft, confirmed = true)

        val post = posts().single()
        assertEquals("/v3/eleves/10/messages.awp", post.requestUrl!!.encodedPath)
        val json = JSONObject(sentBodies.single())
        assertEquals("2026-2027", json.getString("anneeMessages"))
        val message = json.getJSONObject("message")
        assertEquals("Absence du 6 novembre", message.getString("subject"))
        val html = String(Base64.getDecoder().decode(message.getString("content")), Charsets.UTF_8)
        // Rien de ce qui est tapé n'est interprété comme une balise.
        assertEquals("Bonjour,<br>Tom sera absent &lt;jeudi&gt; &amp; vendredi.<br>Merci.", html)
        val to: JSONArray = message.getJSONArray("to")
        assertEquals(listOf(12, 3), (0 until to.length()).map { to.getJSONObject(it).getInt("id") })
        assertEquals(listOf("P", "A"), (0 until to.length()).map { to.getJSONObject(it).getString("type") })
    }

    @Test
    fun `un compte famille ecrit depuis la boite de la famille`() = runTest {
        val family = Account("1", "1", "Marie", "DUPONT", students = listOf(Student("20", "Léa", "D")))
        repository(allowed = { true }, account = family).sendMessage(draft, confirmed = true)
        assertEquals("/v3/familles/1/messages.awp", posts().single().requestUrl!!.encodedPath)
    }

    @Test
    fun `l annee scolaire de printemps est celle qui a commence en automne`() = runTest {
        val spring = Clock.fixed(Instant.parse("2027-03-10T08:00:00Z"), ZoneId.of("Europe/Paris"))
        val session = ApiSession()
        session.open("jdupont", EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')), listOf(Account("10", "E", "J", "D")))
        val writes = object : WriteAccess {
            override fun homeworkSync() = false
            override fun messageSending() = true
        }
        EcoleDirecteRepository(session, ActiveProfile("jdupont", null, "J", "J D", "JD"), InMemoryResponseCache(), clock = spring, writes = writes)
            .sendMessage(draft, confirmed = true)
        assertEquals("2026-2027", JSONObject(sentBodies.single()).getString("anneeMessages"))
    }

    @Test
    fun `un envoi refuse n'est jamais rejoue`() {
        postAnswer = """{"code":403,"message":"Accès refusé"}"""
        val repository = repository(allowed = { true })
        expectFailure({ repository.sendMessage(draft, confirmed = true) }, "accès")
        assertEquals(1, posts().size)
    }

    @Test
    fun `une panne reseau pendant l'envoi est signalee sans nouvel essai`() {
        val repository = repository(allowed = { true })
        server.shutdown()
        expectFailure({ repository.sendMessage(draft, confirmed = true) }, "réseau")
    }

    @Test
    fun `les contacts viennent des enseignants et de l'administration, tries`() = runTest {
        val contacts = repository(allowed = { true }).recipients()
        assertEquals(listOf("Anne ABEL", "M. Paul PETIT", "Vie scolaire"), contacts.map { it.name })
        assertEquals(listOf("Enseignants", "Enseignants", "Administration"), contacts.map { it.group })
        assertEquals("P", contacts.first { it.name == "Anne ABEL" }.type)
        assertEquals("A", contacts.last().type)
        assertTrue(requests.all { it.requestUrl!!.queryParameter("verbe") == "get" })
    }

    @Test
    fun `le texte du message est protege`() {
        assertEquals("a &lt;b&gt;<br>c &amp; d", Recipients.toHtml(" a <b>\r\nc & d "))
        assertEquals("", Recipients.toHtml("\n"))
        assertEquals(
            "Bonjour",
            String(Base64.getDecoder().decode(Recipients.encodedContent("Bonjour")), Charsets.UTF_8),
        )
    }

    @Test
    fun `un contact sans identifiant ou sans nom est ignore`() {
        val parsed = Recipients.parse(JSONArray("""[{"nom":"SANS ID"},{"id":4},{"id":5,"nom":"OK"},{"id":5,"nom":"OK"}]"""), "G", "P")
        assertEquals(listOf("OK"), parsed.map { it.name })
        assertEquals(emptyList<Recipient>(), Recipients.parse(null, "G", "P"))
    }

    @Test
    fun `la confirmation montre tous les destinataires, l'objet et le texte tels qu'ils partent`() {
        val text = draft.confirmation()
        assertTrue("À : M. Paul PETIT, Vie scolaire" in text)
        assertTrue("Objet : Absence du 6 novembre" in text)
        assertTrue("Tom sera absent <jeudi> & vendredi." in text)
    }

    @Test
    fun `les limites d'un message sont verifiees`() {
        assertTrue(draft.isReady)
        assertFalse(draft.copy(recipients = List(MessageDraft.MAX_RECIPIENTS + 1) { paul.copy(id = "$it") }).isReady)
        assertFalse(draft.copy(subject = "x".repeat(MessageDraft.MAX_SUBJECT + 1)).isReady)
        assertFalse(draft.copy(body = "x".repeat(MessageDraft.MAX_BODY + 1)).isReady)
        assertEquals(3, MessageDraft(emptyList(), "", "").problems().size)
    }
}
