package fr.ecole2recte.app.ui.schedule

import fr.ecole2recte.app.domain.ScheduleEntry
import kotlin.math.roundToInt

/**
 * Échelle verticale de l'agenda (vue jour et vue semaine). 1,0 est l'échelle d'origine ; le pincement
 * et les boutons + / − la font varier entre [MIN] et [MAX]. Logique pure : testée sans Android.
 */
object ScheduleZoom {
    const val MIN = 0.5f
    const val MAX = 3f
    const val DEFAULT = 1f

    /** Un cran des boutons + / −. */
    const val STEP = 1.25f

    /** Ramène une valeur lue (réglage abîmé, NaN) dans les bornes. */
    fun clamp(value: Float): Float = if (value.isNaN() || value.isInfinite()) DEFAULT else round(value.coerceIn(MIN, MAX))

    /** Applique le facteur d'un geste de pincement (1,2 : les doigts s'écartent de 20 %). */
    fun pinch(current: Float, gesture: Float): Float = if (gesture <= 0f || gesture.isNaN()) clamp(current) else clamp(current * gesture)

    fun zoomIn(current: Float): Float = clamp(current * STEP)

    fun zoomOut(current: Float): Float = clamp(current / STEP)

    fun canZoomIn(current: Float): Boolean = clamp(current) < MAX

    fun canZoomOut(current: Float): Boolean = clamp(current) > MIN

    /** « 125 % » : de quoi l'afficher entre les deux boutons. */
    fun percent(current: Float): Int = (clamp(current) * 100).roundToInt()

    private fun round(value: Float): Float = (value * 100).roundToInt() / 100f
}

/** Une colonne de la vue semaine : un jour et sa frise (doubles heures, cours simultanés regroupés). */
data class WeekDayColumn(val date: String, val timeline: DayTimeline)

/** La semaine sur **une** échelle horaire commune : les jours se comparent à l'œil, ligne à ligne. */
data class WeekTimeline(val days: List<WeekDayColumn>, val fromMin: Int, val toMin: Int) {
    val duration: Int get() = toMin - fromMin
}

/**
 * Prépare la vue semaine en réutilisant [buildDayTimeline] jour par jour, puis en alignant toutes les
 * colonnes sur les bornes communes (le premier cours de la semaine et le dernier, arrondis à l'heure).
 * Une semaine sans cours garde une plage par défaut de 8 h à 17 h.
 */
fun buildWeekTimeline(dates: List<String>, byDate: Map<String, List<ScheduleEntry>>): WeekTimeline {
    val columns = dates.map { WeekDayColumn(it, buildDayTimeline(byDate[it].orEmpty())) }
    val busy = columns.filter { it.timeline.lessons.isNotEmpty() }
    if (busy.isEmpty()) return WeekTimeline(columns, 8 * 60, 17 * 60)
    val from = busy.minOf { it.timeline.fromMin }
    val to = busy.maxOf { it.timeline.toMin }
    return WeekTimeline(columns, from, maxOf(to, from + 60))
}

/** Ce qu'un bloc de cours a la place d'écrire, selon sa hauteur et sa largeur en dp. */
data class BlockContent(val titleLines: Int, val showRoom: Boolean, val showTime: Boolean, val showTag: Boolean)

private const val LINE_HEIGHT_DP = 14f
private const val BLOCK_PADDING_DP = 6f

/**
 * Le nom de la matière est toujours écrit. La salle vient ensuite, puis l'horaire, puis les mentions
 * (annulé, modifié) ; ce qui ne tient pas est omis plutôt que coupé. Un cours annulé ou modifié reste
 * repérable même sans mention : il est barré, atténué ou encadré (voir le dessin).
 */
fun blockContent(heightDp: Float, widthDp: Float): BlockContent {
    val lines = ((heightDp - BLOCK_PADDING_DP) / LINE_HEIGHT_DP).toInt().coerceAtLeast(1)
    val narrow = widthDp < 56f
    val titleLines = if (lines >= 4 || (lines == 3 && narrow)) 2 else 1
    val spare = lines - titleLines
    return BlockContent(
        titleLines = titleLines,
        showRoom = spare >= 1,
        showTime = spare >= 2 && !narrow,
        showTag = spare >= if (narrow) 2 else 3,
    )
}
