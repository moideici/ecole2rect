package fr.ecole2recte.app.domain

import kotlin.math.floor

/**
 * Le simulateur de notes : « quelle note pour atteindre X ? » et « et si j'obtiens Y ? ».
 *
 * Il s'appuie sur [Averages] (le calcul du site, à l'identique) et sur les notes que l'application voit : le
 * résultat est une estimation, pas la moyenne officielle (voir `docs/MOYENNES.md`). Toutes les saisies sont du
 * texte, avec virgule ou point. [grades] sont les notes d'une matière dans la période affichée.
 */
object GradeSimulation {
    /** Lit un nombre saisi (virgule ou point) ; `null` si ce n'est pas un nombre. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Ce qu'il faut à la prochaine évaluation pour atteindre l'objectif. */
    sealed interface Need {
        /** Il faut au moins [grade] sur 20 (non arrondi : l'affichage l'écrit au centième, comme le site). */
        data class Needed(val grade: Double) : Need

        /** Même 20/20 ne suffirait pas : il faudrait [grade]. */
        data class Unreachable(val grade: Double) : Need

        /** L'objectif est déjà acquis quelle que soit la prochaine note. */
        data object Achieved : Need

        data class Invalid(val reason: String) : Need
    }

    fun need(grades: List<GradeEntry>, targetText: String, coefficientText: String): Need {
        val target = parse(targetText) ?: return Need.Invalid("Saisis un objectif entre 0 et 20.")
        if (target < 0.0 || target > 20.0) return Need.Invalid("L'objectif doit être entre 0 et 20.")
        val coefficient = parse(coefficientText) ?: return Need.Invalid("Saisis le coefficient de la prochaine évaluation.")
        if (coefficient <= 0.0) return Need.Invalid("Le coefficient doit être supérieur à 0.")
        val (period, subject) = scope(grades)
        val required = Averages.requiredGrade(grades, period, subject, target, coefficient = coefficient)
            ?: return Need.Invalid("Calcul impossible.")
        // Les bornes du site : atteignable entre 0 et 20 compris. En dessous de 0, l'objectif est déjà acquis.
        return when {
            required.reachable -> Need.Needed(required.value)
            required.value < 0.0 -> Need.Achieved
            else -> Need.Unreachable(required.value)
        }
    }

    fun message(need: Need): String = when (need) {
        is Need.Needed -> "Il te faut au moins ${FrenchFormat.average(need.grade)} / 20 à la prochaine évaluation."
        is Need.Unreachable ->
            "Hors d'atteinte avec une seule évaluation : même 20 / 20 ne suffirait pas (il en faudrait ${FrenchFormat.average(need.grade)})."
        Need.Achieved -> "Objectif déjà acquis, quelle que soit la prochaine note."
        is Need.Invalid -> need.reason
    }

    /** La moyenne avec une note de plus. */
    sealed interface Projection {
        data class Value(val current: Double?, val projected: Double) : Projection
        data class Invalid(val reason: String) : Projection
    }

    fun projection(grades: List<GradeEntry>, gradeText: String, coefficientText: String): Projection {
        val grade = parse(gradeText) ?: return Projection.Invalid("Saisis une note entre 0 et 20.")
        if (grade < 0.0 || grade > 20.0) return Projection.Invalid("La note doit être entre 0 et 20.")
        val coefficient = parse(coefficientText) ?: return Projection.Invalid("Saisis le coefficient de l'évaluation.")
        if (coefficient <= 0.0) return Projection.Invalid("Le coefficient doit être supérieur à 0.")
        val (period, subject) = scope(grades)
        val simulation = Averages.simulate(grades, period, subject, grade, coefficient = coefficient)
        val projected = simulation.after ?: return Projection.Invalid("Calcul impossible.")
        return Projection.Value(simulation.before, projected)
    }

    fun message(projection: Projection): String = when (projection) {
        is Projection.Invalid -> projection.reason
        is Projection.Value -> {
            val now = projection.current?.let { FrenchFormat.average(it) }
            val next = FrenchFormat.average(projection.projected)
            if (now == null) "Ta moyenne serait de $next / 20." else "Ta moyenne passerait de $now à $next / 20."
        }
    }

    /** La période et la matière des notes reçues : le simulateur porte toujours sur une matière d'une période. */
    private fun scope(grades: List<GradeEntry>): Pair<String, String?> =
        grades.firstOrNull().let { (it?.periodCode ?: "") to it?.subject }

    /** Un objectif de départ sensé : un point de plus que la moyenne actuelle (arrondie vers le bas au dixième), au plus 20. */
    fun defaultTarget(grades: List<GradeEntry>): String {
        val (period, subject) = scope(grades)
        val current = Averages.weightedAverage(Averages.gradesFor(grades, period, subject)) ?: return ""
        return GradeMath.decimal((floor(current * 10) / 10 + 1.0).coerceAtMost(20.0))
    }
}
