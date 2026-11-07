package fr.ecole2recte.app.domain

/** Un point de la courbe d'une matière : la note (sur 20) et la moyenne de la matière à ce moment-là. */
data class SeriesPoint(val label: String, val date: String, val note: Double, val runningAverage: Double)

/** Calculs derrière les courbes d'évolution, séparés de l'écran pour être testés. */
object GradeCurves {
    /**
     * L'évolution d'une matière : ses notes dans l'ordre des dates, avec la moyenne pondérée **cumulée** après
     * chacune. C'est un calcul de l'application (toutes périodes confondues), pas la moyenne de l'établissement.
     */
    fun subjectSeries(all: List<GradeEntry>, subject: String): List<SeriesPoint> {
        val used = all.filter { it.subject == subject && it.counted && it.value != null && it.coefficient > 0 && !it.date.isNullOrBlank() }
            .sortedBy { it.date }
        return used.mapIndexed { index, grade ->
            SeriesPoint(
                label = grade.label,
                date = grade.date.orEmpty(),
                note = GradeMath.on20(grade.value!!, grade.scale),
                runningAverage = GradeMath.weightedAverage(used.take(index + 1)) ?: 0.0,
            )
        }
    }

    /** Bornes verticales d'une courbe : un peu de marge autour des valeurs, dans 0..20, avec une hauteur minimale de 2 points. */
    fun yRange(values: List<Double>, margin: Double = 1.5): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to 20.0
        var low = (values.min() - margin).coerceAtLeast(0.0)
        var high = (values.max() + margin).coerceAtMost(20.0)
        if (high - low < 2.0) {
            high = (low + 2.0).coerceAtMost(20.0)
            low = (high - 2.0).coerceAtLeast(0.0)
        }
        return low to high
    }

    /** Position d'une valeur entre les bornes, de 0 (bas) à 1 (haut). */
    fun position(value: Double, low: Double, high: Double): Float =
        if (high <= low) 0.5f else ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)

    /** La phrase lue par un lecteur d'écran à la place du dessin. */
    fun describe(title: String, labels: List<String>, values: List<Double>): String {
        if (values.isEmpty()) return "$title : aucune donnée."
        val steps = values.indices.joinToString(", ") { "${labels.getOrElse(it) { "point ${it + 1}" }} ${GradeMath.decimal(values[it])}" }
        return "$title : $steps."
    }
}
