package fr.ecole2recte.app.ui.login

import java.text.Normalizer
import java.util.Locale

/**
 * Retrouve, parmi les propositions d'ÉcoleDirecte, celle qui correspond à la date de naissance
 * saisie par l'utilisateur. Ne devine jamais : sans correspondance exacte, renvoie `null`.
 */
internal fun findBirthAnswer(
    question: String?,
    choices: List<String>,
    day: String,
    month: String,
    year: String,
): String? {
    if (question.isNullOrBlank() || choices.isEmpty()) return null
    val normalizedQuestion = normalizeSecurityText(question)
    val candidates = when {
        listOf("mois", "month").any(normalizedQuestion::contains) ->
            birthMonthCandidates(month)
        listOf("annee", "year").any(normalizedQuestion::contains) ->
            birthYearCandidates(year)
        listOf("jour", "day").any(normalizedQuestion::contains) ->
            birthDayCandidates(day)
        else -> emptySet()
    }
    if (candidates.isEmpty()) return null
    return choices.firstOrNull { normalizeSecurityText(it) in candidates }
}

private fun birthDayCandidates(value: String): Set<String> =
    value.toIntOrNull()?.let { setOf(value, it.toString()) } ?: setOf(value)

private fun birthYearCandidates(value: String): Set<String> = setOf(value.trim())

private fun birthMonthCandidates(value: String): Set<String> {
    val number = value.toIntOrNull() ?: return setOf(normalizeSecurityText(value))
    val names = listOf(
        "janvier", "fevrier", "mars", "avril", "mai", "juin",
        "juillet", "aout", "septembre", "octobre", "novembre", "decembre",
    )
    val name = names.getOrNull(number - 1) ?: return setOf(value, number.toString())
    return setOf(value, number.toString(), name, name.take(3))
        .map(::normalizeSecurityText)
        .toSet()
}

internal fun normalizeSecurityText(value: String): String =
    Normalizer.normalize(value.trim().lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9]+".toRegex(), "")
