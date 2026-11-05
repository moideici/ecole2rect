package fr.ecole2recte.app.domain

/** Une note, ramenée à ce que l'écran en montre. [value] est vide pour « Abs » ou un devoir non rendu. */
data class GradeEntry(
    val id: String,
    val subject: String,
    val colorIndex: Int,
    val label: String,
    val value: Double?,
    val scale: Double,
    val coefficient: Double,
    val date: String?,
    val classAverage: Double?,
    val minimum: Double?,
    val maximum: Double?,
    /** Faux quand la note ne compte pas dans la moyenne. */
    val counted: Boolean,
    val periodCode: String = "",
)

/** Une moyenne de période, pour la courbe d'évolution. */
data class PeriodAverage(val name: String, val average: Double)

/**
 * Calculs de moyenne, séparés de l'écran pour être testés.
 *
 * Toutes les notes sont ramenées sur 20 avant d'être combinées, et seules comptent celles qui
 * ont une valeur et qui comptent dans la moyenne (jamais « Abs », jamais un zéro inventé).
 */
object GradeMath {
    fun on20(value: Double, scale: Double): Double = if (scale <= 0) value else value / scale * 20.0

    private fun counted(grades: List<GradeEntry>) = grades.filter { it.counted && it.value != null && it.coefficient > 0 }

    /** Moyenne pondérée sur 20, ou `null` s'il n'y a rien à moyenner. */
    fun weightedAverage(grades: List<GradeEntry>): Double? {
        val used = counted(grades)
        val weight = used.sumOf { it.coefficient }
        if (weight <= 0) return null
        return used.sumOf { on20(it.value!!, it.scale) * it.coefficient } / weight
    }

    /**
     * La note (sur 20) à obtenir à une prochaine évaluation de coefficient [coefficient] pour
     * atteindre la moyenne [target]. Peut dépasser 20 (objectif hors d'atteinte) ou être
     * négative (objectif déjà acquis) : c'est à l'écran de le dire.
     */
    fun requiredGrade(grades: List<GradeEntry>, target: Double, coefficient: Double): Double? {
        if (coefficient <= 0) return null
        val used = counted(grades)
        val weight = used.sumOf { it.coefficient }
        val points = used.sumOf { on20(it.value!!, it.scale) * it.coefficient }
        return (target * (weight + coefficient) - points) / coefficient
    }

    /** La moyenne qu'on aurait avec une note supplémentaire. */
    fun averageWith(grades: List<GradeEntry>, value: Double, coefficient: Double): Double? {
        if (coefficient <= 0) return weightedAverage(grades)
        val used = counted(grades)
        val weight = used.sumOf { it.coefficient } + coefficient
        val points = used.sumOf { on20(it.value!!, it.scale) * it.coefficient } + value * coefficient
        return points / weight
    }
}
