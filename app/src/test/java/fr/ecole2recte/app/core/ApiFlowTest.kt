package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.AttachmentSource
import fr.ecole2recte.app.core.protocol.ApiErrorKind
import fr.ecole2recte.app.core.protocol.ApiResult
import fr.ecole2recte.app.core.protocol.EcoleDirecteApi
import fr.ecole2recte.app.core.protocol.LoginOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le protocole complet, rejoué sur des réponses enregistrées : connexion, question de
 * sécurité, session expirée. Ces chemins sont impossibles à tester avec un vrai compte sans
 * risquer de le bloquer — c'est précisément pour ça qu'ils sont simulés ici.
 */
class ApiFlowTest {

    private fun engineWithGtk() = FakeHttpEngine()
        .on(
            "gtk=1",
            body = "",
            headers = headers("X-GTK" to "jeton-gtk", "Set-Cookie" to "SID=abc; Path=/"),
            method = "GET",
        )

    @Test
    fun `une connexion reussie renvoie les comptes et garde le jeton`() {
        val engine = engineWithGtk().on("/login.awp", Payloads.LOGIN_SUCCESS)
        val api = EcoleDirecteApi(engine)

        val outcome = api.login("eleve", "secret")

        assertTrue(outcome is LoginOutcome.Success)
        assertEquals("Martin", (outcome as LoginOutcome.Success).accounts.single().firstName)
        assertEquals("jeton-de-session", api.token)

        // Le GTK et les cookies posés par l'appel préalable doivent accompagner le POST.
        val loginRequest = engine.requests.last()
        assertEquals("jeton-gtk", loginRequest.headers["X-GTK"])
        assertTrue(loginRequest.headers["Cookie"]!!.contains("SID=abc"))
        assertTrue(loginRequest.body!!.startsWith("data="))
    }

    @Test
    fun `un code 250 demande la question de securite`() {
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_TWO_FACTOR, headers = headers("X-Token" to "t", "2FA-Token" to "f"))
            .on("doubleauth", Payloads.DOUBLE_AUTH_QUESTION)
        val api = EcoleDirecteApi(engine)

        val outcome = api.login("eleve", "secret")

        assertTrue(outcome is LoginOutcome.SecurityQuestion)
        val question = outcome as LoginOutcome.SecurityQuestion
        assertEquals("Quel est ton mois de naissance ?", question.question)
        assertEquals(listOf("Mars", "Juin", "Septembre"), question.choices)
    }

    @Test
    fun `la reponse validee est rejouee dans une nouvelle connexion`() {
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_TWO_FACTOR, headers = headers("X-Token" to "t", "2FA-Token" to "f"))
            .on("doubleauth", Payloads.DOUBLE_AUTH_QUESTION)
            .on("doubleauth", Payloads.DOUBLE_AUTH_ANSWER)
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
        val api = EcoleDirecteApi(engine)
        api.login("eleve", "secret")

        val outcome = api.answerSecurityQuestion("Juin", "eleve", "secret")

        assertTrue(outcome is LoginOutcome.Success)
        assertNotNull("la réponse validée doit être conservée pour les reconnexions", api.approvedAnswer)
        assertEquals("identifiant-question", api.approvedAnswer!!.cn)
    }

    @Test
    fun `des identifiants refuses ne sont pas retentes`() {
        val engine = engineWithGtk().on("/login.awp", Payloads.LOGIN_INVALID)
        val api = EcoleDirecteApi(engine)

        val outcome = api.login("eleve", "faux")

        assertTrue(outcome is LoginOutcome.Failure)
        assertEquals(ApiErrorKind.INVALID_CREDENTIALS, (outcome as LoginOutcome.Failure).kind)
        // Deux requêtes seulement : le GTK puis la connexion. Aucune tentative en boucle.
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun `un jeton expire est signale comme tel et non comme une panne`() {
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .on("emploidutemps", Payloads.SESSION_EXPIRED)
        val api = EcoleDirecteApi(engine)
        api.login("eleve", "secret")

        val result = api.timetable("4242", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 27))

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorKind.SESSION_EXPIRED, (result as ApiResult.Failure).kind)
    }

    @Test
    fun `une piece jointe est telechargee avec le bon type de fichier`() {
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // « %PDF- »
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .onDownload("telechargement", pdf)
        val api = EcoleDirecteApi(engine)
        api.login("eleve", "secret")

        val result = api.downloadAttachment(
            Attachment(id = "7", name = "sujet.pdf", source = AttachmentSource.HOMEWORK),
        )

        assertTrue(result is ApiResult.Ok)
        assertEquals("sujet.pdf", (result as ApiResult.Ok).value.name)
        assertEquals(5, result.value.bytes.size)
        val url = engine.requests.last().url
        assertTrue(url.contains("fichierId=7"))
        assertTrue(url.contains("leTypeDeFichier=FICHIER_CDT"))
    }

    @Test
    fun `un refus deguise en JSON ne devient pas un fichier`() {
        val refusal = """{"code":403,"message":"Accès refusé"}""".toByteArray()
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .onDownload("telechargement", refusal)
        val api = EcoleDirecteApi(engine)
        api.login("eleve", "secret")

        val result = api.downloadAttachment(Attachment(id = "7", name = "sujet.pdf"))

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorKind.ACCESS_DENIED, (result as ApiResult.Failure).kind)
        assertEquals("Accès refusé", result.message)
    }

    @Test
    fun `la messagerie demande la page voulue`() {
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .on("messages.awp", Payloads.MESSAGES)
        val api = EcoleDirecteApi(engine)
        api.login("eleve", "secret")
        val account = Account(id = "4242", type = "E", firstName = "Martin", lastName = "Leroy")

        api.messages(account, page = 2)

        assertTrue(engine.requests.last().url.contains("page=2"))
    }

    @Test
    fun `les appels de donnees portent le jeton et les bons chemins`() {
        val engine = engineWithGtk()
            .on("/login.awp", Payloads.LOGIN_SUCCESS)
            .on("emploidutemps", Payloads.TIMETABLE)
            .on("cahierdetexte", Payloads.HOMEWORK_LIST)
            .on("notes.awp", Payloads.GRADES)
            .on("messages.awp", Payloads.MESSAGES)
        val api = EcoleDirecteApi(engine)
        api.login("eleve", "secret")
        val account = Account(id = "4242", type = "E", firstName = "Martin", lastName = "Leroy")

        val lessons = api.timetable(account.id, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 27))
        val homework = api.homework(account.id)
        val grades = api.grades(account.id)
        val messages = api.messages(account)

        assertEquals(2, (lessons as ApiResult.Ok).value.size)
        assertEquals(2, (homework as ApiResult.Ok).value.size)
        assertEquals(2, (grades as ApiResult.Ok).value.grades.size)
        assertEquals(2, (messages as ApiResult.Ok).value.size)

        val urls = engine.requests.map { it.url }
        assertTrue(urls.any { it.contains("/E/4242/emploidutemps.awp?verbe=get") })
        assertTrue(urls.any { it.contains("/Eleves/4242/cahierdetexte.awp") })
        assertTrue(urls.any { it.contains("/eleves/4242/notes.awp") })
        assertTrue(urls.any { it.contains("/eleves/4242/messages.awp") })
        assertTrue(engine.requests.last().headers.containsKey("X-Token"))
    }
}
