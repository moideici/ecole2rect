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
        /** Il faut au moins [grade] dans le barème choisi (arrondi au dixième supérieur). */
        data class Needed(val grade: Double) : Need

        /** Même le maximum du barème ne suffirait pas : il faudrait [grade]. */
        data class Unreachable(val grade: Double) : Need

        /** L'objectif est déjà acquis quelle que soit la prochaine note. */
        data object Achieved : Need

        data class Invalid(val reason: String) : Need
    }

    fun need(grades: List<GradeEntry>, targetText: String, coefficientText: String, scaleText: String = "20", displayed: Double? = GradeMath.weightedAverage(grades)): Need {
        val scale = parse(scaleText)?.takeIf { it > 0 } ?: return Need.Invalid("Le barème doit être supérieur à 0.")
        val target = parse(targetText) ?: return Need.Invalid("Saisis un objectif entre 0 et 20.")
        if (target < 0.0 || target > 20.0) return Need.Invalid("L'objectif doit être entre 0 et 20.")
        val coefficient = parse(coefficientText) ?: return Need.Invalid("Saisis le coefficient de la prochaine évaluation.")
        if (coefficient <= 0.0) return Need.Invalid("Le coefficient doit être supérieur à 0.")
        val weight = weight(grades)
        val required = (target * (weight + coefficient) - (displayed ?: 0.0) * weight) / coefficient * scale / 20
        return when {
            required <= 0.0 -> Need.Achieved
            required > scale + EPSILON * scale / 20 -> Need.Unreachable(roundUp(required))
            else -> Need.Needed(roundUp(required).coerceAtMost(scale))
        }
    }

    fun message(need: Need, scale: Double = 20.0, digits: Int = 1): String = when (need) {
        is Need.Needed -> "Il te faut au moins ${FrenchFormat.decimal(need.grade, digits)} / ${scaleLabel(scale)} à la prochaine évaluation."
        is Need.Unreachable ->
            "Hors d'atteinte avec une seule évaluation : même ${scaleLabel(scale)} / ${scaleLabel(scale)} ne suffirait pas (il en faudrait ${FrenchFormat.decimal(need.grade, digits)})."
        Need.Achieved -> "Objectif déjà acquis, quelle que soit la prochaine note."
        is Need.Invalid -> need.reason
    }

    /** La moyenne avec une note de plus. */
    sealed interface Projection {
        data class Value(val current: Double?, val projected: Double) : Projection
        data class Invalid(val reason: String) : Projection
    }

    fun projection(grades: List<GradeEntry>, gradeText: String, coefficientText: String, scaleText: String = "20", displayed: Double? = GradeMath.weightedAverage(grades)): Projection {
        val scale = parse(scaleText)?.takeIf { it > 0 } ?: return Projection.Invalid("Le barème doit être supérieur à 0.")
        val grade = parse(gradeText) ?: return Projection.Invalid("Saisis une note entre 0 et ${scaleLabel(scale)}.")
        if (grade < 0.0 || grade > scale) return Projection.Invalid("La note doit être entre 0 et ${scaleLabel(scale)}.")
        val coefficient = parse(coefficientText) ?: return Projection.Invalid("Saisis le coefficient de l'évaluation.")
        if (coefficient <= 0.0) return Projection.Invalid("Le coefficient doit être supérieur à 0.")
        val weight = weight(grades)
        val projected = ((displayed ?: 0.0) * weight + GradeMath.on20(grade, scale) * coefficient) / (weight + coefficient)
        return Projection.Value(displayed, projected)
    }

    fun message(projection: Projection, digits: Int = 1): String = when (projection) {
        is Projection.Invalid -> projection.reason
        is Projection.Value -> {
            val now = projection.current?.let { FrenchFormat.decimal(it, digits) }
            val next = FrenchFormat.decimal(projection.projected, digits)
            if (now == null) "Ta moyenne serait de $next / 20." else "Ta moyenne passerait de $now à $next / 20."
        }
    }

    private fun roundUp(value: Double): Double = ceil(value * 10 - 1e-9) / 10

    private fun weight(grades: List<GradeEntry>) = grades.filter { it.counted && it.value != null && it.coefficient > 0 }.sumOf { it.coefficient }
    private fun scaleLabel(scale: Double) = if (scale % 1.0 == 0.0) scale.toInt().toString() else FrenchFormat.decimal(scale, 2)

    /** Un objectif de départ sensé : un point de plus que la moyenne actuelle (arrondie vers le bas au dixième), au plus 20. */
    fun defaultTarget(grades: List<GradeEntry>, displayed: Double? = GradeMath.weightedAverage(grades)): String {
        val current = displayed ?: return ""
        return GradeMath.decimal((floor(current * 10) / 10 + 1.0).coerceAtMost(20.0))
    }
}
