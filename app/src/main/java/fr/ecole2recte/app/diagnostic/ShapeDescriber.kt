package fr.ecole2recte.app.diagnostic

import fr.ecole2recte.app.data.api.ApiSchoolLife
import fr.ecole2recte.app.data.api.ParseReport
import fr.ecole2recte.app.data.api.Parsers
import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.ResponseCache
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Clock

/**
 * Décrit la **forme** d'une réponse JSON : les clés, les types et les tailles, jamais une valeur.
 * Un texte devient `<texte:12>` (sa longueur seulement), un nombre `<nombre>`. Les clés qui sont
 * en réalité des valeurs (une date, un identifiant numérique) sont remplacées par `<date>` / `<id>`.
 *
 * C'est ce que l'utilisateur peut coller dans un ticket sans exposer de données d'élève, et ce qui
 * sert à écrire les vrais jeux de test.
 */
fun describeShape(value: Any?): String = buildString { describe(value, 0, this) }

private val DATE_KEY = Regex("""^\d{4}-\d{2}-\d{2}.*""")
private val ID_KEY = Regex("""^\d+$""")
private const val MAX_DEPTH = 8
private const val SAMPLE = 20

private fun safeKey(key: String): String = when {
    DATE_KEY.matches(key) -> "<date>"
    ID_KEY.matches(key) -> "<id>"
    key.length > 40 -> "<clé longue>"
    else -> key
}

private fun scalar(value: Any?): String? = when (value) {
    null, JSONObject.NULL -> "<vide>"
    is String -> "<texte:${value.length}>"
    is Number -> "<nombre>"
    is Boolean -> "<booléen>"
    else -> null
}

private fun describe(value: Any?, depth: Int, out: StringBuilder) {
    val pad = "  ".repeat(depth)
    scalar(value)?.let {
        out.append(pad).append(it).append('\n')
        return
    }
    if (depth >= MAX_DEPTH) {
        out.append(pad).append("…\n")
        return
    }
    when (value) {
        is JSONObject -> {
            // Les clés « date » ou « id » se regroupent : on décrit le premier exemple et on compte les autres.
            val groups = LinkedHashMap<String, MutableList<Any?>>()
            for (key in value.keys().asSequence().toList().sorted()) groups.getOrPut(safeKey(key)) { mutableListOf() }.add(value.opt(key))
            for ((name, values) in groups) {
                val count = if (values.size > 1) " (×${values.size})" else ""
                val first = values.first()
                val inline = scalar(first)
                if (inline != null) {
                    out.append(pad).append(name).append(": ").append(inline).append(count).append('\n')
                } else {
                    out.append(pad).append(name).append(count).append(":\n")
                    describe(if (values.size > 1) merge(values) else first, depth + 1, out)
                }
            }
        }
        is JSONArray -> {
            out.append(pad).append("[${value.length()} élément(s)]\n")
            if (value.length() > 0) {
                val sample = (0 until minOf(value.length(), SAMPLE)).map { value.opt(it) }
                describe(merge(sample), depth + 1, out)
            }
        }
        else -> out.append(pad).append("<inconnu>\n")
    }
}

/** Réunit les clés de plusieurs objets semblables pour que la forme montre tous les champs possibles. */
private fun merge(values: List<Any?>): Any? {
    val objects = values.filterIsInstance<JSONObject>()
    if (objects.isEmpty() || objects.size != values.size) return values.firstOrNull { it != null && it != JSONObject.NULL }
    val merged = JSONObject()
    for (item in objects) {
        for (key in item.keys()) {
            val existing = merged.opt(key)
            val candidate = item.opt(key)
            if (existing == null || existing == JSONObject.NULL || (existing is String && existing.isEmpty())) merged.put(key, candidate)
        }
    }
    return merged
}

/** Une ressource en cache, décrite sans valeur personnelle. */
data class DiagnosticSection(val name: String, val ageLabel: String, val shape: String, val rejected: Int?)

/**
 * Relit ce que l'application a enregistré (réponses brutes en cache) et en tire, pour chaque
 * ressource, sa forme et le nombre d'entrées que le lecteur a écartées sans rien dire.
 */
class DataDiagnostic(private val cache: ResponseCache, private val clock: Clock = Clock.systemDefaultZone()) {
    private val resources = listOf("lessons", "homework", "grades", "messages", "schoollife")

    fun sections(owner: CacheOwner): List<DiagnosticSection> = resources.mapNotNull { name ->
        val stored = cache.get(owner, name) ?: return@mapNotNull null
        val data = runCatching { JSONTokener(stored.json).nextValue() }.getOrNull()
        val report = ParseReport()
        runCatching {
            when (name) {
                "lessons" -> Parsers.timetable(data, report)
                "homework" -> Parsers.homeworkSummary(data, report)
                "grades" -> Parsers.grades(data, report)
                "messages" -> Parsers.messages(data, report)
                "schoollife" -> Parsers.schoolLife(data, report) as List<ApiSchoolLife>
            }
        }
        val minutes = ((clock.millis() - stored.at) / 60_000).coerceAtLeast(0)
        DiagnosticSection(
            name = name,
            ageLabel = when {
                minutes < 1 -> "à l'instant"
                minutes < 60 -> "il y a $minutes min"
                minutes < 24 * 60 -> "il y a ${minutes / 60} h"
                else -> "il y a ${minutes / (24 * 60)} j"
            },
            shape = describeShape(data),
            rejected = report.rejected,
        )
    }
}
