package fr.ecole2recte.app.core.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Encodages exigés par l'API privée.
 *
 * Ces fonctions ont l'air anodines, mais elles décident si la connexion passe : le
 * pare-feu applicatif d'ÉcoleDirecte compare le corps envoyé à celui du client web, y
 * compris les espaces de `json.dumps` et l'encodage `+` des espaces. Elles sont donc
 * figées, documentées, et couvertes par des tests.
 */
object Encoding {

    /**
     * Reproduit `json.dumps(...)` de Python : séparateurs `", "` et `": "`, et
     * échappement systématique des caractères non ASCII (`ensure_ascii=True`).
     */
    fun pythonJson(value: JSONObject, keyOrder: List<String> = emptyList()): String = buildString {
        append('{')
        orderedKeys(value, keyOrder).forEachIndexed { index, key ->
            if (index > 0) append(", ")
            append(pythonString(key))
            append(": ")
            append(pythonValue(value.opt(key)))
        }
        append('}')
    }

    /**
     * `JSONObject` ne garantit pas l'ordre des clés : il le conserve sur Android, mais pas
     * dans l'implémentation de référence utilisée par les tests. Le corps envoyé au serveur
     * doit pourtant être reproductible, d'où cet ordre explicite, complété alphabétiquement.
     */
    private fun orderedKeys(value: JSONObject, keyOrder: List<String>): List<String> {
        val present = value.keys().asSequence().toList()
        val ordered = keyOrder.filter { it in present }
        return ordered + present.filterNot { it in ordered }.sorted()
    }

    private fun pythonValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> pythonJson(value)
        is JSONArray -> buildString {
            append('[')
            for (index in 0 until value.length()) {
                if (index > 0) append(", ")
                append(pythonValue(value.opt(index)))
            }
            append(']')
        }
        is String -> pythonString(value)
        is Boolean -> if (value) "true" else "false"
        is Number -> value.toString()
        else -> pythonString(value.toString())
    }

    private fun pythonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20 || character.code > 0x7E) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    /** `application/x-www-form-urlencoded`, espaces en `+`, hexadécimal en majuscules. */
    fun formEncode(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val hex = "0123456789ABCDEF"
        return buildString(bytes.size * 3) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                when {
                    unsigned == 0x20 -> append('+')
                    unsigned in 0x30..0x39 || unsigned in 0x41..0x5A || unsigned in 0x61..0x7A ||
                        unsigned == '-'.code || unsigned == '_'.code ||
                        unsigned == '.'.code || unsigned == '~'.code -> append(unsigned.toChar())
                    else -> {
                        append('%')
                        append(hex[unsigned ushr 4])
                        append(hex[unsigned and 0x0F])
                    }
                }
            }
        }
    }

    fun base64Encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    /**
     * L'API encode en base64 des questions de sécurité et des contenus HTML, parfois sans
     * remplissage et avec des retours à la ligne. Un décodage raté ne doit jamais faire
     * tomber un écran : on renvoie null et l'appelant garde la valeur brute.
     */
    fun base64Decode(value: String?): String? {
        val cleaned = value?.trim()?.replace("\n", "")?.replace("\r", "") ?: return null
        if (cleaned.isEmpty()) return null
        return runCatching {
            String(Base64.getMimeDecoder().decode(cleaned), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    /** Décode si c'est bien du base64 lisible, sinon rend la valeur telle quelle. */
    fun maybeBase64(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        if (!raw.matches(BASE64_SHAPE)) return raw
        val decoded = base64Decode(raw) ?: return raw
        // Un décodage qui produit des octets de contrôle n'était pas du texte.
        return if (decoded.isEmpty() || decoded.any { it.code < 9 }) raw else decoded
    }

    private val BASE64_SHAPE = Regex("^[A-Za-z0-9+/\\r\\n]+={0,2}$")

    private val SECRET_PATTERN = Regex(
        "(?i)(motdepasse|password|\"token\"|x-token|2fa-token|cookie|set-cookie|identifiant)" +
            "\"?\\s*[:=]\\s*\"?[^\",}\\s]+",
    )

    /**
     * Masque les secrets d'un diagnostic réseau. Un diagnostic sert à être collé dans un
     * ticket : il ne doit jamais transporter de mot de passe, de jeton ni de cookie.
     */
    fun redactSecrets(value: String): String = SECRET_PATTERN.replace(value) { match ->
        "${match.groupValues[1]}=<masqué>"
    }
}
