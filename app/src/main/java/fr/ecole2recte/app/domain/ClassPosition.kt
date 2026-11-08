package fr.ecole2recte.app.domain

/** Statistiques reçues de l'établissement, jamais déduites d'une moyenne ni d'un histogramme. */
data class ClassPosition(
    val rank: Int? = null,
    val size: Int? = null,
    val minimum: Double? = null,
    val average: Double? = null,
    val maximum: Double? = null,
    val median: Double? = null,
) {
    data class Bar(val label: String, val value: Double) { val fraction: Float get() = (value / 20).toFloat().coerceIn(0f, 1f) }

    fun rankLabel(): String? = if (rank != null && rank > 0 && (size == null || size > 0 && rank <= size))
        "Rang : $rank" + size?.let { " sur $it" }.orEmpty() else null

    fun sizeLabel(): String? = size?.takeIf { it > 0 }?.let { "Effectif : $it" }

    fun bars(mine: Double?): List<Bar> = listOf("Minimum" to minimum, "Classe" to average, "Moi" to mine, "Médiane" to median, "Maximum" to maximum)
        .mapNotNull { (name, value) -> value?.takeIf { it.isFinite() && it in 0.0..20.0 }?.let { Bar(name, it) } }

    fun describe(title: String, mine: Double?, digits: Int): String {
        val bars = bars(mine)
        val rank = rankLabel()
        val position = rank ?: sizeLabel()
        if (bars.isEmpty() && position == null) return "$title : aucune comparaison de classe disponible."
        return "$title : " + position?.let { "$it. " }.orEmpty() + bars.joinToString(" ; ") { "${it.label} ${FrenchFormat.decimal(it.value, digits)} sur 20" }.let {
            if (it.isEmpty()) "" else "$it."
        }
    }
}
