package fr.ecole2recte.app.core

import fr.ecole2recte.app.core.protocol.Encoding
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodingTest {

    @Test
    fun `le JSON de connexion reprend les separateurs de Python`() {
        val payload = JSONObject()
            .put("identifiant", "eleve")
            .put("motdepasse", "secret")
            .put("isReLogin", false)
            .put("uuid", "")
            .put("fa", JSONArray())

        assertEquals(
            """{"identifiant": "eleve", "motdepasse": "secret", "isReLogin": false, "uuid": "", "fa": []}""",
            Encoding.pythonJson(payload, listOf("identifiant", "motdepasse", "isReLogin", "uuid", "fa")),
        )
    }

    @Test
    fun `les caracteres non ASCII sont echappes comme le fait ensure_ascii`() {
        val payload = JSONObject().put("question", "Prénom ?")

        assertEquals("""{"question": "Pr\u00e9nom ?"}""", Encoding.pythonJson(payload))
    }

    @Test
    fun `le corps de formulaire encode les espaces avec un plus`() {
        assertEquals("a+b", Encoding.formEncode("a b"))
        assertEquals("%C3%A9", Encoding.formEncode("é"))
        assertEquals("-_.~", Encoding.formEncode("-_.~"))
        assertEquals("%7B%22a%22%3A+1%7D", Encoding.formEncode("""{"a": 1}"""))
    }

    @Test
    fun `le base64 fait l aller-retour et tolere une valeur illisible`() {
        val encoded = Encoding.base64Encode("Quel est ton mois de naissance ?")

        assertEquals("Quel est ton mois de naissance ?", Encoding.base64Decode(encoded))
        assertNull(Encoding.base64Decode(""))
        assertEquals("texte déjà clair", Encoding.maybeBase64("texte déjà clair"))
        assertEquals("Mars", Encoding.maybeBase64(Encoding.base64Encode("Mars")))
    }

    @Test
    fun `un diagnostic ne transporte jamais de secret`() {
        val raw = """{"identifiant":"eleve","motdepasse":"tres-secret","token":"abcdef","message":"ok"}"""

        val redacted = Encoding.redactSecrets(raw)

        assertFalse(redacted.contains("tres-secret"))
        assertFalse(redacted.contains("abcdef"))
        assertTrue("le reste du diagnostic doit rester lisible", redacted.contains("ok"))
    }
}
