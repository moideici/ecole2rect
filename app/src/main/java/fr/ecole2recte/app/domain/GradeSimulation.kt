package fr.ecole2recte.app.domain

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Le simulateur de notes : « quelle note pour atteindre X ? » et « et si j'obtiens Y ? ».
 *
 * Il s'appuie sur [GradeMath] et sur les notes que l'application voit : le résultat est une estimation,
 * pas la moyenne officielle (voir `docs/MOYENNES.md`). Toutes les saisies sont du texte, avec virgule ou point.
 */
object GradeSimulation {
    /** Marge pour qu'un 20,0 arrondi par le calcul ne soit pas déclaré hors d'atteinte. */
    private const val EPSILON = 0.05

    /** Lit un nombre saisi (virgule ou point) ; `null` si ce n'est pas un nombre. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Ce qu'il faut à la prochaine évaluation pour atteindre l'objectif. */
    sealed interface Need {
        /** Il faut au moins [grade] sur 20 (arrondi au dixième supérieur, pour ne pas promettre trop peu). */
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
        val required = GradeMath.requiredGrade(grades, target, coefficient) ?: return Need.Invalid("Calcul impossible.")
        return when {
            required <= 0.0 -> Need.Achieved
            required > 20.0 + EPSILON -> Need.Unreachable(roundUp(required))
            else -> Need.Needed(roundUp(required).coerceAtMost(20.0))
        }
    }

    fun message(need: Need): String = when (need) {
        is Need.Needed -> "Il te faut au moins ${GradeMath.decimal(need.grade)} / 20 à la prochaine évaluation."
        is Need.Unreachable ->
            "Hors d'atteinte avec une seule évaluation : même 20 / 20 ne suffirait pas (il en faudrait ${GradeMath.decimal(need.grade)})."
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
        val projected = GradeMath.averageWith(grades, grade, coefficient) ?: return Projection.Invalid("Calcul impossible.")
        return Projection.Value(GradeMath.weightedAverage(grades), projected)
    }

    fun message(projection: Projection): String = when (projection) {
        is Projection.Invalid -> projection.reason
        is Projection.Value -> {
            val now = projection.current?.let { GradeMath.decimal(it) }
            val next = GradeMath.decimal(projection.projected)
            if (now == null) "Ta moyenne serait de $next / 20." else "Ta moyenne passerait de $now à $next / 20."
        }
    }

    private fun roundUp(value: Double): Double = ceil(value * 10 - 1e-9) / 10

    /** Un objectif de départ sensé : un point de plus que la moyenne actuelle (arrondie vers le bas au dixième), au plus 20. */
    fun defaultTarget(grades: List<GradeEntry>): String {
        val current = GradeMath.weightedAverage(grades) ?: return ""
        return GradeMath.decimal((floor(current * 10) / 10 + 1.0).coerceAtMost(20.0))
    }
}
