package fr.ecole2recte.app.core.parse

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Lecture défensive du JSON d'ÉcoleDirecte.
 *
 * L'API n'est pas documentée et n'est pas stable : un même champ arrive tantôt en nombre,
 * tantôt en chaîne, tantôt absent ; une virgule décimale française remplace le point ; une
 * liste devient un objet vide quand elle n'a rien à dire. Ces aides évitent de répéter
 * partout des `try` et rendent le parsing incapable de faire tomber un écran.
 */

fun JSONObject.string(vararg keys: String): String {
    keys.forEach { key ->
        val value = opt(key)
        if (value != null && value != JSONObject.NULL) {
            val text = value.toString().trim()
            if (text.isNotEmpty() && text != "null") return text
        }
    }
    return ""
}

fun JSONObject.stringOrNull(vararg keys: String): String? = string(*keys).takeIf { it.isNotBlank() }

fun JSONObject.bool(vararg keys: String): Boolean {
    keys.forEach { key ->
        when (val value = opt(key)) {
            is Boolean -> return value
            is Number -> return value.toInt() != 0
            is String -> value.trim().lowercase().let {
                if (it == "true" || it == "1") return true
                if (it == "false" || it == "0") return false
            }
        }
    }
    return false
}

/** Nombre décimal tolérant la virgule française, les espaces et les valeurs vides. */
fun JSONObject.decimal(vararg keys: String): Double? {
    keys.forEach { key ->
        val raw = string(key)
        if (raw.isNotEmpty()) {
            val cleaned = raw.replace(" ", "").replace("\u00A0", "").replace(',', '.')
            cleaned.toDoubleOrNull()?.let { return it }
        }
    }
    return null
}

fun JSONObject.int(vararg keys: String): Int? = decimal(*keys)?.toInt()

fun JSONObject.objects(vararg keys: String): List<JSONObject> {
    keys.forEach { key ->
        optJSONArray(key)?.let { return it.objects() }
        optJSONObject(key)?.let { return listOf(it) }
    }
    return emptyList()
}

fun JSONArray?.objects(): List<JSONObject> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(::add)
        }
    }
}

fun JSONArray?.strings(): List<String> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

private val DATE_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
)

private val DATE_TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
)

fun parseDate(value: String?): LocalDate? {
    val raw = value?.trim().orEmpty().take(10)
    if (raw.isEmpty()) return null
    DATE_FORMATS.forEach { format ->
        runCatching { return LocalDate.parse(raw, format) }
    }
    return null
}

fun parseDateTime(value: String?): LocalDateTime? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    DATE_TIME_FORMATS.forEach { format ->
        runCatching { return LocalDateTime.parse(raw, format) }
    }
    // Certaines réponses ne portent que la date : minuit est alors la seule heure honnête.
    return parseDate(raw)?.atStartOfDay()
}

fun parseTime(value: String?): LocalTime? {
    val raw = value?.trim().orEmpty()
    if (raw.length < 4) return null
    return runCatching { LocalTime.parse(raw.take(5)) }.getOrNull()
}
