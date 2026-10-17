package fr.ecole2recte.app.core.store

import fr.ecole2recte.app.core.auth.AnswerMemory
import fr.ecole2recte.app.core.auth.BirthDates
import fr.ecole2recte.app.core.auth.RememberedAnswer
import fr.ecole2recte.app.core.auth.SecurityAnswer
import fr.ecole2recte.app.core.protocol.TrustedAnswer
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Sérialisation des identifiants enregistrés.
 *
 * Séparée du chiffrement pour deux raisons : elle est testable sans appareil, et le jour où
 * une version ajoute un champ, une sauvegarde plus ancienne continue d'être relue — un
 * champ absent vaut sa valeur par défaut, jamais une lecture perdue qui renverrait
 * l'utilisateur sur l'écran de connexion.
 */
object CredentialsCodec {

    fun encode(credentials: SavedCredentials): String = JSONObject().apply {
        put("identifier", credentials.identifier)
        put("password", credentials.password)
        credentials.trustedAnswer?.let {
            put("trustedAnswer", JSONObject().put("cn", it.cn).put("cv", it.cv))
        }
        put(
            "birthDates",
            JSONArray().apply {
                credentials.birthDates.entries.forEach { (name, date) ->
                    put(
                        JSONObject()
                            .put("name", name)
                            .put("day", date.day)
                            .put("month", date.month)
                            .put("year", date.year),
                    )
                }
            },
        )
        put(
            "answers",
            JSONArray().apply {
                credentials.answers.answers.forEach { answer ->
                    put(
                        JSONObject()
                            .put("question", answer.question)
                            .put("answer", answer.answer)
                            .put("validUntil", answer.validUntil.toString())
                            .put("label", answer.label),
                    )
                }
            },
        )
    }.toString()

    fun decode(raw: String?): SavedCredentials? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val identifier = root.optString("identifier").takeIf { it.isNotBlank() } ?: return null
        val password = root.optString("password").takeIf { it.isNotBlank() } ?: return null
        return SavedCredentials(
            identifier = identifier,
            password = password,
            trustedAnswer = root.optJSONObject("trustedAnswer")?.let { answer ->
                val cn = answer.optString("cn")
                val cv = answer.optString("cv")
                if (cn.isBlank() || cv.isBlank()) null else TrustedAnswer(cn, cv)
            },
            birthDates = birthDates(root.optJSONArray("birthDates")),
            answers = answers(root.optJSONArray("answers")),
        )
    }

    private fun birthDates(array: JSONArray?): BirthDates {
        val entries = array.entries().mapNotNull { entry ->
            val date = SecurityAnswer.BirthDate(
                day = entry.optString("day"),
                month = entry.optString("month"),
                year = entry.optString("year"),
            )
            if (date.isFilled) entry.optString("name") to date else null
        }
        return BirthDates(entries.toMap())
    }

    private fun answers(array: JSONArray?): AnswerMemory {
        val entries = array.entries().mapNotNull { entry ->
            val question = entry.optString("question").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val answer = entry.optString("answer").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val validUntil = runCatching { LocalDate.parse(entry.optString("validUntil")) }.getOrNull()
                ?: return@mapNotNull null
            RememberedAnswer(question, answer, validUntil, entry.optString("label"))
        }
        return AnswerMemory(entries)
    }

    private fun JSONArray?.entries(): List<JSONObject> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }
}
