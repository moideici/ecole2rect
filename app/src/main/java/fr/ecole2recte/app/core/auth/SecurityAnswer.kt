package fr.ecole2recte.app.core.auth

import fr.ecole2recte.app.core.format.Text

/**
 * Rapprochement entre la date de naissance saisie et les propositions du serveur.
 *
 * Règle intangible : on ne devine jamais une réponse de sécurité. Cette fonction ne fait que
 * repérer, parmi les propositions renvoyées par ÉcoleDirecte, celle qui correspond
 * exactement à une information que l'utilisateur a lui-même saisie. Si rien ne correspond,
 * elle renvoie null et l'utilisateur choisit — aucune tentative n'est enchaînée à l'aveugle.
 */
object SecurityAnswer {

    private val MONTHS = listOf(
        "janvier", "fevrier", "mars", "avril", "mai", "juin",
        "juillet", "aout", "septembre", "octobre", "novembre", "decembre",
    )

    data class BirthDate(val day: String = "", val month: String = "", val year: String = "")

    fun suggest(question: String?, choices: List<String>, birthDate: BirthDate): String? {
        if (question.isNullOrBlank() || choices.isEmpty()) return null
        val normalizedQuestion = Text.normalizeKey(question)
        val candidates = when {
            listOf("mois", "month").any(normalizedQuestion::contains) -> monthCandidates(birthDate.month)
            listOf("annee", "year").any(normalizedQuestion::contains) -> yearCandidates(birthDate.year)
            listOf("jour", "day").any(normalizedQuestion::contains) -> dayCandidates(birthDate.day)
            else -> emptySet()
        }
        if (candidates.isEmpty()) return null
        return choices.firstOrNull { Text.normalizeKey(it) in candidates }
    }

    private fun dayCandidates(value: String): Set<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptySet()
        val number = trimmed.toIntOrNull() ?: return setOf(Text.normalizeKey(trimmed))
        // « 05 », « 5 » et « 1er » désignent le même jour selon les établissements.
        return setOf(trimmed, number.toString(), "%02d".format(number), if (number == 1) "1er" else "")
            .filter { it.isNotEmpty() }
            .map(Text::normalizeKey)
            .toSet()
    }

    private fun yearCandidates(value: String): Set<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptySet()
        return setOf(trimmed).map(Text::normalizeKey).toSet()
    }

    private fun monthCandidates(value: String): Set<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptySet()
        val number = trimmed.toIntOrNull()
            ?: return setOf(Text.normalizeKey(trimmed))
        val name = MONTHS.getOrNull(number - 1)
            ?: return setOf(trimmed, number.toString()).map(Text::normalizeKey).toSet()
        return setOf(trimmed, number.toString(), "%02d".format(number), name, name.take(3))
            .map(Text::normalizeKey)
            .toSet()
    }
}
