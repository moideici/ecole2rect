package fr.ecole2recte.app.export

import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.schedule.buildDayTimeline

/** Une ligne de texte dans un bloc de cours. */
data class BlockLine(val text: String, val bold: Boolean)

/** Un cours posé sur la page, en points PDF (1/72 de pouce), l'origine en haut à gauche. */
data class LayoutBlock(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val subject: String,
    val colorIndex: Int,
    val lines: List<BlockLine>,
    /** Créneau qui revient toutes les semaines (sur la page de l'autre semaine aussi). */
    val everyWeek: Boolean,
)

data class DayHeader(val x: Float, val w: Float, val label: String)

data class HourLine(val y: Float, val label: String)

data class LegendItem(val x: Float, val y: Float, val subject: String, val teacher: String, val colorIndex: Int)

/** Tout ce qu'il faut pour dessiner une page : la mise en page, sans aucun dessin. */
data class PageLayout(
    val width: Float,
    val height: Float,
    val title: String,
    val subtitle: String,
    val gridLeft: Float,
    val gridTop: Float,
    val gridRight: Float,
    val gridBottom: Float,
    val headers: List<DayHeader>,
    val hours: List<HourLine>,
    val blocks: List<LayoutBlock>,
    val legend: List<LegendItem>,
    val footer: String,
    /** Avertissement affiché sous le titre (semaine inconnue…). */
    val note: String? = null,
)

/**
 * Mise en page d'un emploi du temps en A4 paysage : une page par semaine type (une seule sans
 * alternance), grille jours × heures, cours (doubles heures fondues, cours simultanés côte à côte), légende
 * des matières. **Aucun dessin ici** : le dessin ([TimetablePdf]) ne fait que lire cette mise en page, ce
 * qui permet de la tester sans Android.
 */
object TimetableLayout {
    const val PAGE_WIDTH = 842f
    const val PAGE_HEIGHT = 595f
    const val MARGIN = 28f
    const val TITLE_BLOCK = 40f
    const val TIME_COLUMN = 36f
    const val HEADER_HEIGHT = 20f
    const val LINE_HEIGHT = 10.5f
    const val BLOCK_PADDING = 3f
    const val BLOCK_GAP = 1.5f
    const val LEGEND_ROW = 13f
    const val LEGEND_COLUMN = 190f
    const val FOOTER_HEIGHT = 14f

    private val dayNames = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

    /**
     * Les pages à produire pour les semaines [weeks] (1 et/ou 2 ; sans alternance, la seule semaine type).
     * Toutes les pages partagent la même échelle horaire, pour que deux semaines se comparent à l'œil.
     */
    fun pages(schedule: TypicalSchedule, weeks: List<Int>, title: String, subtitle: String, footer: String): List<PageLayout> {
        val wanted = if (schedule.alternating) weeks.filter { it in 1..2 }.distinct().sorted() else listOf(1)
        if (wanted.isEmpty()) return emptyList()
        val pageSlots = wanted.associateWith { schedule.slotsOf(it) }
        val all = pageSlots.values.flatten()
        val fromMin = (all.minOfOrNull { minutes(it.start) } ?: 8 * 60) / 60 * 60
        val toMin = maxOf(((all.maxOfOrNull { minutes(it.end) } ?: 17 * 60) + 59) / 60 * 60, fromMin + 60)
        val lastDay = maxOf(5, all.maxOfOrNull { it.day } ?: 5)
        val legendSubjects = all.distinctBy { it.subject.lowercase() }.sortedBy { it.subject.lowercase() }

        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN
        val columnsPerRow = ((right - left) / LEGEND_COLUMN).toInt().coerceAtLeast(1)
        val legendRows = (legendSubjects.size + columnsPerRow - 1) / columnsPerRow
        val legendHeight = legendRows * LEGEND_ROW
        val legendTop = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT - legendHeight
        val gridTop = MARGIN + TITLE_BLOCK + HEADER_HEIGHT
        val gridBottom = legendTop - 8f
        val gridLeft = left + TIME_COLUMN
        val columnWidth = (right - gridLeft) / lastDay
        val perMinute = (gridBottom - gridTop) / (toMin - fromMin)

        val legend = legendSubjects.mapIndexed { index, slot ->
            LegendItem(
                x = left + (index % columnsPerRow) * LEGEND_COLUMN,
                y = legendTop + (index / columnsPerRow) * LEGEND_ROW,
                subject = slot.subject,
                teacher = slot.teacher,
                colorIndex = slot.colorIndex,
            )
        }
        val headers = (1..lastDay).map { DayHeader(gridLeft + (it - 1) * columnWidth, columnWidth, dayNames[it - 1]) }
        val hours = (fromMin / 60..toMin / 60).map { HourLine(gridTop + (it * 60 - fromMin) * perMinute, "$it h") }

        return wanted.map { week ->
            val slots = pageSlots.getValue(week)
            val blocks = (1..lastDay).flatMap { day ->
                val ofDay = slots.filter { it.day == day }
                // Les créneaux sont déjà fondus ; la frise de l'agenda sert ici à placer les cours simultanés côte à côte.
                val entries = ofDay.mapIndexed { index, slot ->
                    ScheduleEntry(Subject(slot.subject, slot.teacher, slot.room, slot.colorIndex), "", date = "2000-01-0$day", start = slot.start, end = slot.end, note = index.toString())
                }
                buildDayTimeline(entries).lessons.map { lesson ->
                    val slot = ofDay[lesson.first.note.toInt()]
                    val laneWidth = columnWidth / lesson.lanes
                    val height = lesson.duration * perMinute
                    LayoutBlock(
                        x = gridLeft + (day - 1) * columnWidth + lesson.lane * laneWidth + BLOCK_GAP,
                        y = gridTop + (lesson.startMin - fromMin) * perMinute + BLOCK_GAP,
                        w = laneWidth - 2 * BLOCK_GAP,
                        h = height - 2 * BLOCK_GAP,
                        subject = slot.subject,
                        colorIndex = slot.colorIndex,
                        lines = linesFor(slot, height - 2 * BLOCK_GAP),
                        everyWeek = schedule.alternating && slot.cycle == WeekSelector.Every,
                    )
                }
            }
            val unknown = schedule.alternating && week !in schedule.knownCycles
            PageLayout(
                width = PAGE_WIDTH,
                height = PAGE_HEIGHT,
                title = if (schedule.alternating) "$title — Semaine $week" else title,
                subtitle = subtitle,
                gridLeft = gridLeft,
                gridTop = gridTop,
                gridRight = right,
                gridBottom = gridBottom,
                headers = headers,
                hours = hours,
                blocks = blocks,
                legend = legend,
                footer = footer,
                note = "Cette semaine n'est pas connue de l'application : seuls les cours qui reviennent toutes les semaines peuvent y figurer.".takeIf { unknown },
            )
        }
    }

    /** Le texte qui tient dans un bloc de hauteur [height] : matière d'abord, puis salle, horaire, professeur. */
    fun linesFor(slot: TypicalSlot, height: Float): List<BlockLine> {
        val capacity = ((height - 2 * BLOCK_PADDING) / LINE_HEIGHT).toInt().coerceAtLeast(1)
        val candidates = listOfNotNull(
            BlockLine(slot.subject.ifBlank { "Cours" } + if (slot.permanence) " (permanence)" else "", bold = true),
            slot.room.takeIf { it.isNotBlank() }?.let { BlockLine("Salle $it", bold = false) },
            BlockLine("${slot.start}–${slot.end}", bold = false),
            slot.teacher.takeIf { it.isNotBlank() }?.let { BlockLine(it, bold = false) },
        )
        return candidates.take(capacity)
    }

    private fun minutes(time: String): Int = time.take(2).toInt() * 60 + time.takeLast(2).toInt()
}
