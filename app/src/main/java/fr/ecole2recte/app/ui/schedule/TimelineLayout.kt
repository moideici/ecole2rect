package fr.ecole2recte.app.ui.schedule

import fr.ecole2recte.app.domain.ScheduleEntry

/** Un cours ou plusieurs cours qui s'enchaînent (double heure), placé sur la frise d'une journée. */
data class TimelineLesson(
    /** Les cours regroupés : un seul pour une heure simple, deux pour une double heure, etc. */
    val entries: List<ScheduleEntry>,
    val startMin: Int,
    val endMin: Int,
    /** Colonne, quand plusieurs cours ont lieu en même temps (classe scindée en groupes). */
    val lane: Int,
    val lanes: Int,
) {
    val first: ScheduleEntry get() = entries.first()
    val duration: Int get() = endMin - startMin
    val isDouble: Boolean get() = entries.size > 1

    /** « Double heure », « Triple heure »… ou vide pour un cours simple. */
    val multiLabel: String
        get() = when (entries.size) {
            1 -> ""
            2 -> "Double heure"
            3 -> "Triple heure"
            else -> "${entries.size} heures de suite"
        }
}

/** Un temps sans cours entre deux cours : récréation, pause déjeuner, heure libre. */
data class TimelineGap(val startMin: Int, val endMin: Int, val label: String) {
    val duration: Int get() = endMin - startMin
}

data class DayTimeline(
    val lessons: List<TimelineLesson>,
    val gaps: List<TimelineGap>,
    /** Bornes arrondies à l'heure : la frise commence et finit sur une graduation. */
    val fromMin: Int,
    val toMin: Int,
)

fun minutesOf(time: String): Int = time.take(2).toInt() * 60 + time.takeLast(2).toInt()

fun formatMinutes(total: Int): String {
    val hours = total / 60
    val minutes = total % 60
    return when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h ${"%02d".format(minutes)}"
    }
}

private const val MAX_BETWEEN_DOUBLE = 10
private const val MIN_GAP = 15

/**
 * Met en forme une journée de cours :
 * - deux cours **de la même matière qui se suivent** (à 10 min près) forment une double heure ;
 * - des cours **simultanés** (groupes) sont placés côte à côte ;
 * - un temps sans cours d'au moins 15 minutes devient une pause nommée : récréation, pause,
 *   pause déjeuner ou heure libre.
 */
fun buildDayTimeline(entries: List<ScheduleEntry>): DayTimeline {
    val valid = entries.filter { it.start.length >= 5 && it.end.length >= 5 }.sortedWith(compareBy({ minutesOf(it.start) }, { minutesOf(it.end) }))
    if (valid.isEmpty()) return DayTimeline(emptyList(), emptyList(), 8 * 60, 17 * 60)

    // 1. Doubles heures
    val groups = mutableListOf<MutableList<ScheduleEntry>>()
    for (entry in valid) {
        val target = groups.lastOrNull { group ->
            val last = group.last()
            last.subject.name == entry.subject.name &&
                last.canceled == entry.canceled &&
                minutesOf(entry.start) - minutesOf(last.end) in 0..MAX_BETWEEN_DOUBLE
        }
        if (target != null) target += entry else groups += mutableListOf(entry)
    }
    val spans = groups.map { Triple(it.toList(), minutesOf(it.first().start), minutesOf(it.last().end)) }

    // 2. Colonnes pour les cours simultanés : un amas de cours qui se chevauchent partage la largeur
    val lanesOf = IntArray(spans.size)
    val laneCountOf = IntArray(spans.size)
    var clusterStart = 0
    var clusterEnd = -1
    val laneEnds = mutableListOf<Int>()
    val clusterIndexes = mutableListOf<Int>()
    fun closeCluster() {
        val count = laneEnds.size.coerceAtLeast(1)
        clusterIndexes.forEach { laneCountOf[it] = count }
        clusterIndexes.clear()
        laneEnds.clear()
    }
    spans.forEachIndexed { index, (_, start, end) ->
        if (clusterIndexes.isNotEmpty() && start >= clusterEnd) closeCluster()
        if (clusterIndexes.isEmpty()) {
            clusterStart = start
            clusterEnd = end
        }
        clusterEnd = maxOf(clusterEnd, end)
        var lane = laneEnds.indexOfFirst { it <= start }
        if (lane < 0) {
            laneEnds += end
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = end
        }
        lanesOf[index] = lane
        clusterIndexes += index
    }
    closeCluster()
    val lessons = spans.mapIndexed { i, (list, start, end) -> TimelineLesson(list, start, end, lanesOf[i], laneCountOf[i]) }

    // 3. Temps libres entre les cours
    val gaps = mutableListOf<TimelineGap>()
    var coveredUntil = lessons.first().endMin
    for (lesson in lessons.drop(1)) {
        if (lesson.startMin - coveredUntil >= MIN_GAP) gaps += TimelineGap(coveredUntil, lesson.startMin, gapLabel(coveredUntil, lesson.startMin))
        coveredUntil = maxOf(coveredUntil, lesson.endMin)
    }
    val from = lessons.minOf { it.startMin } / 60 * 60
    val to = (lessons.maxOf { it.endMin } + 59) / 60 * 60
    return DayTimeline(lessons, gaps, from, maxOf(to, from + 60))
}

/** Nomme un temps libre d'après sa durée et l'heure du déjeuner. */
internal fun gapLabel(start: Int, end: Int): String {
    val length = end - start
    val lunchOverlap = minOf(end, 14 * 60) - maxOf(start, 11 * 60 + 30)
    return when {
        length < 30 -> "Récréation"
        length < 45 -> "Pause"
        lunchOverlap >= 30 -> "Pause déjeuner"
        else -> "Heure libre"
    }
}
