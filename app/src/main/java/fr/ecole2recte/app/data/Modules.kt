package fr.ecole2recte.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Les modules d'ÉcoleDirecte que l'application lit. Chaque établissement choisit lesquels il ouvre :
 * appeler un module fermé ne donne rien et compte quand même comme du trafic.
 */
enum class SchoolModule(val codes: List<String>) {
    Schedule(listOf("EDT", "EMPLOI_DU_TEMPS")),
    Homework(listOf("CAHIER_DE_TEXTES", "CAHIER_TEXTES")),
    Grades(listOf("NOTES")),
    Messages(listOf("MESSAGERIE")),
    SchoolLife(listOf("VIE_SCOLAIRE")),
}

/**
 * Les modules fermés d'après la liste lue à la connexion (`code → ouvert`). Un module dont on ne sait
 * rien est considéré ouvert : on ne cache jamais une fonction sur une information absente.
 */
fun closedModules(modules: Map<String, Boolean>): Set<SchoolModule> =
    SchoolModule.entries.filterTo(HashSet()) { module ->
        val flags = module.codes.mapNotNull { modules[it] }
        flags.isNotEmpty() && flags.none { it }
    }

/**
 * Lit `modules: [{code, enable}]`. Le drapeau peut être un booléen, un nombre ou un texte selon les
 * versions du service ; sans drapeau lisible le module reste ouvert.
 */
internal fun parseModules(array: JSONArray?): Map<String, Boolean> {
    if (array == null) return emptyMap()
    val result = LinkedHashMap<String, Boolean>()
    for (index in 0 until array.length()) {
        val module = array.optJSONObject(index) ?: continue
        val code = module.optString("code").trim().uppercase()
        if (code.isEmpty()) continue
        val flag = listOf("enable", "enabled", "actif").firstNotNullOfOrNull { key -> module.opt(key).takeIf { it != null && it != JSONObject.NULL } }
        result[code] = when (flag) {
            is Boolean -> flag
            is Number -> flag.toInt() != 0
            is String -> flag.lowercase() !in setOf("false", "0", "non", "no")
            else -> true
        }
    }
    return result
}

internal fun Map<String, Boolean>.toModulesJson(): JSONObject = JSONObject().also { json -> forEach { (code, open) -> json.put(code, open) } }

internal fun JSONObject?.toModuleMap(): Map<String, Boolean> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optBoolean(it, true) }
}
