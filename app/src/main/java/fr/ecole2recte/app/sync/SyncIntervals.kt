package fr.ecole2recte.app.sync

/**
 * Intervalles de synchronisation permis. Garde-fou envers ÉcoleDirecte : jamais plus souvent que
 * toutes les quatre heures, quoi qu'ait enregistré une version précédente.
 */
object SyncIntervals {
    const val MIN_HOURS = 4
    const val DEFAULT_HOURS = 4
    val choices: List<Int> = listOf(4, 8, 12)

    fun clamp(hours: Int): Int = hours.coerceIn(MIN_HOURS, 24)
}
