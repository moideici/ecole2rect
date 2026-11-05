package fr.ecole2recte.app.data.api

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/*
 * Lecture défensive du JSON d'ÉcoleDirecte (même esprit que le paquet `protocol` du client web).
 * L'API n'est ni publique ni documentée : une clé absente chez un établissement est présente
 * chez le suivant, un nombre arrive tantôt en nombre tantôt en chaîne (« 12,5 »), une date
 * tantôt en `2026-09-07` tantôt en `07/09/2026`. Rien ici ne lève : une réponse à moitié
 * comprise vaut mieux qu'un écran vide.
 */

internal fun Any?.asObject(): JSONObject? = this as? JSONObject

internal fun Any?.objects(): List<JSONObject> {
    val array = this as? JSONArray ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun Any?.asText(): String = when (this) {
    null, JSONObject.NULL -> ""
    is String -> this
    is Number -> toString()
    is Boolean -> toString()
    else -> ""
}

/** Le premier champ renseigné parmi plusieurs orthographes possibles. */
internal fun JSONObject.str(vararg keys: String): String {
    for (key in keys) {
        val value = opt(key).asText()
        if (value.isNotEmpty()) return value
    }
    return ""
}

internal fun String.toDecimal(): Double? =
    trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Un nombre, y compris écrit « 12,5 ». « Abs » ou une chaîne vide n'en sont pas un. */
internal fun JSONObject.num(vararg keys: String): Double? {
    for (key in keys) {
        val parsed = when (val value = opt(key)) {
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is String -> value.toDecimal()
            else -> null
        }
        if (parsed != null) return parsed
    }
    return null
}

private fun Any?.asFlag(default: Boolean): Boolean = when (this) {
    is Boolean -> this
    is Number -> toInt() != 0
    is String -> when (trim().lowercase()) {
        "" -> default
        "true", "1", "oui" -> true
        else -> false
    }
    else -> default
}

internal fun JSONObject.flag(vararg keys: String, default: Boolean = false): Boolean {
    for (key in keys) {
        if (has(key) && !isNull(key)) return opt(key).asFlag(default)
    }
    return default
}

private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})""")
private val FRENCH_DATE = Regex("""^(\d{2})/(\d{2})/(\d{4})""")
private val TIME = Regex("""(\d{2}):(\d{2})(?::(\d{2}))?""")

/** Une date `2026-09-07`, quelle que soit la façon dont elle est arrivée. */
internal fun isoDate(value: Any?): String? {
    val raw = value.asText().trim()
    ISO_DATE.find(raw)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
    FRENCH_DATE.find(raw)?.let { return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
    return null
}

/** Un instant local `2026-09-07T10:15:00`, sans fuseau : l'heure d'un cours est celle de l'établissement. */
internal fun isoDateTime(value: Any?): String? {
    val raw = value.asText().trim()
    val date = isoDate(raw) ?: return null
    val time = TIME.find(raw.drop(10))
    return if (time == null) "${date}T00:00:00" else "${date}T${time.groupValues[1]}:${time.groupValues[2]}:${time.groupValues[3].ifEmpty { "00" }}"
}

internal fun JSONObject.date(vararg keys: String): String? {
    for (key in keys) isoDate(opt(key))?.let { return it }
    return null
}

internal fun JSONObject.dateTime(vararg keys: String): String? {
    for (key in keys) isoDateTime(opt(key))?.let { return it }
    return null
}

/** Le nom d'une personne, recollé depuis les morceaux que le service veut bien donner. */
internal fun personName(source: JSONObject?): String {
    if (source == null) return ""
    val built = listOf(source.str("civilite"), source.str("prenom"), source.str("particule"), source.str("nom"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .trim()
    return built.ifEmpty { source.str("nom_complet", "nomComplet", "libelle") }
}

/** Le contenu arrive parfois en base64, parfois en clair : on essaie, sinon on garde tel quel. */
internal fun base64DecodeOrRaw(value: String): String {
    if (value.isBlank()) return ""
    return runCatching { String(Base64.getMimeDecoder().decode(value), Charsets.UTF_8) }
        .getOrNull()
        ?.takeIf { decoded -> decoded.none { it == '�' } && decoded.isNotBlank() }
        ?: value
}

/** Retire le balisage HTML : un paragraphe devient une ligne vide, un `<br>` un retour. */
internal fun plainText(html: String): String = html
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("[ \\t]+\\n"), "\n")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
