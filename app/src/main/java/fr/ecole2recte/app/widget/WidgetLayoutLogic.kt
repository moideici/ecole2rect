package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.FrenchFormat
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.sync.WidgetSlot
import fr.ecole2recte.app.ui.schedule.TimelineLesson
import fr.ecole2recte.app.ui.schedule.buildDayTimeline
import fr.ecole2recte.app.ui.schedule.formatMinutes
import fr.ecole2recte.app.ui.schedule.gapLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

// ---------------------------------------------------------------------------------------------
// Mise en page des widgets « Semaine complète » et « Journée complète » : tout ce qui se calcule
// (niveau de détail selon la taille, jours affichés, lignes qui tiennent, troncature des noms,
// progression, « dans 25 min ») est ici, en fonctions pures. Les widgets Glance n'ont plus qu'à
// dessiner le résultat. Les tailles sont en dp, l'heure vient de l'appelant.
// ---------------------------------------------------------------------------------------------

/** Ce que le widget « Semaine complète » montre, du plus simple au plus détaillé. */
enum class WeekDetail {
    /** Petit (2×2, 3×2) : un jour par ligne, nombre de cours et première/dernière heure. */
    Summary,

    /** Moyen (4×2, 3×3) : cinq colonnes de pastilles colorées avec l'initiale de la matière. */
    Columns,

    /** Grand (4×3, 4×4) : grille jours × heures, cours colorés avec leur nom (et la salle si la place le permet). */
    Grid,

    /** Très grand (5×4 et plus) : la grille avec nom complet, salle, horaires et professeur. */
    Detailed,
}

/** Ce que le widget « Journée complète » montre. */
enum class DayDetail {
    /** Petit : le cours en cours (ou le prochain) en grand, et le suivant. */
    Focus,

    /** Moyen : la liste compacte de ce qu'il reste à faire, avec la barre de progression. */
    List,

    /** Grand : la frise horaire de toute la journée, pauses nommées, salles et professeurs. */
    Timeline,
}

/** Un cours (ou des cours simultanés, ou une double heure) placé sur une journée. */
data class WeekBlock(
    val startMin: Int,
    val endMin: Int,
    val title: String,
    val initials: String,
    val room: String,
    val teacher: String,
    val colorIndex: Int,
    val canceled: Boolean,
    val live: Boolean,
    /** Nombre d'heures de suite fondues dans ce bloc (2 pour une double heure). */
    val hours: Int = 1,
) {
    val start: String get() = WidgetLayoutLogic.clock(startMin)
    val end: String get() = WidgetLayoutLogic.clock(endMin)
    val timeLabel: String get() = "$start–$end"
    val duration: Int get() = endMin - startMin
}

data class WeekColumn(
    val date: LocalDate,
    /** « Lun » */
    val label: String,
    val number: Int,
    val today: Boolean,
    val past: Boolean,
    val blocks: List<WeekBlock>,
    /** Cours non annulés. */
    val lessons: Int,
    val canceled: Int,
    val first: String,
    val last: String,
)

data class WeekLayout(
    val detail: WeekDetail,
    /** Les jours à dessiner : tous en colonnes, ceux qui tiennent en liste. */
    val columns: List<WeekColumn>,
    val fromMin: Int,
    val toMin: Int,
    val dpPerMinute: Float,
    val gridHeightDp: Float,
    val columnWidthDp: Float,
    /** Combien de pastilles tiennent sous un en-tête de jour (niveau [WeekDetail.Columns]). */
    val chipCapacity: Int,
    /** « Semaine du 2 au 6 nov. » */
    val title: String,
    val showTitle: Boolean,
    val hourLabels: List<Int>,
    /** Nombre de jours qui n'ont pas pu tenir en liste (niveau [WeekDetail.Summary]). */
    val hiddenDays: Int,
)

enum class RowKind { Lesson, Gap }

/** Une ligne de la journée : un cours ou un temps libre nommé. */
data class DayRow(
    val kind: RowKind,
    val startMin: Int,
    val endMin: Int,
    val title: String,
    val room: String,
    val teacher: String,
    val colorIndex: Int,
    val canceled: Boolean,
    val live: Boolean,
    val past: Boolean,
    val hours: Int = 1,
    /** Hauteur à donner à la ligne (frise) ou 0 quand la liste décide seule. */
    val heightDp: Float = 0f,
    /** Les lignes de texte à écrire, déjà tronquées pour la largeur. */
    val lines: List<String> = emptyList(),
) {
    val start: String get() = WidgetLayoutLogic.clock(startMin)
    val end: String get() = WidgetLayoutLogic.clock(endMin)
    val duration: Int get() = endMin - startMin
}

enum class DayState {
    /** Journée en cours ou à venir : il reste des cours. */
    Active,

    /** On montre le prochain jour de cours parce que celui d'aujourd'hui est fini. */
    NextDay,

    /** Aujourd'hui est fini et rien ne suit dans les données. */
    Finished,

    /** Pas de cours aujourd'hui ni les jours suivants. */
    NoLessons,
}

data class DayLayout(
    val detail: DayDetail,
    val state: DayState,
    val date: LocalDate,
    /** « Jeudi 5 novembre » */
    val title: String,
    /** « Aujourd'hui », « Demain », « Lundi » */
    val relative: String,
    /** 0 à 1 : avancement de la journée de cours (0 pour un jour à venir). */
    val progress: Float,
    /** « Fin à 16:30 · reste 3 h 10 » ou vide. */
    val remaining: String,
    val current: DayRow?,
    val next: DayRow?,
    /** « dans 25 min », « demain 08:00 »… pour [next]. */
    val nextIn: String,
    /** Les lignes à dessiner pour [DayDetail.List] et [DayDetail.Timeline] (vide pour [DayDetail.Focus]). */
    val rows: List<DayRow>,
    /** Cours non montrés faute de place. */
    val hiddenLessons: Int,
    val lessonCount: Int,
)

object WidgetLayoutLogic {
    // Mesures communes (dp) : les widgets utilisent les mêmes valeurs pour dessiner.
    const val PAD = 10f
    const val TITLE_H = 16f
    const val HEADER_H = 18f
    const val RAIL_W = 22f
    const val CHIP_ROW = 18f
    const val SUMMARY_ROW = 26f
    const val LIST_LESSON_H = 38f
    const val LIST_GAP_H = 14f
    const val TIMELINE_MIN = 30f
    const val TIMELINE_MAX = 84f
    const val TIMELINE_GAP_H = 15f
    const val DAY_HEADER_H = 62f
    private const val STRIPE_W = 3f

    fun clock(minutes: Int): String = FrenchFormat.twoDigits(minutes / 60) + ":" + FrenchFormat.twoDigits(minutes % 60)

    // ----- niveaux de détail ----------------------------------------------------------------

    fun weekDetail(widthDp: Float, heightDp: Float): WeekDetail = when {
        widthDp < 170f || (widthDp < 240f && heightDp < 150f) -> WeekDetail.Summary
        widthDp < 240f || heightDp < 190f -> WeekDetail.Columns
        widthDp >= 300f && heightDp >= 280f -> WeekDetail.Detailed
        else -> WeekDetail.Grid
    }

    fun dayDetail(widthDp: Float, heightDp: Float): DayDetail = when {
        heightDp < 150f -> DayDetail.Focus
        heightDp >= 270f && widthDp >= 200f -> DayDetail.Timeline
        else -> DayDetail.List
    }

    // ----- textes ---------------------------------------------------------------------------

    /** Combien de caractères d'une police de [fontSp] tiennent sur [widthDp]. */
    fun charsThatFit(widthDp: Float, fontSp: Float): Int = (widthDp / (fontSp * 0.54f)).toInt().coerceAtLeast(0)

    /** Coupe [text] à [maxChars] caractères en ajoutant « … » ; vide si rien ne tient. */
    fun truncate(text: String, maxChars: Int): String = when {
        maxChars <= 0 -> ""
        text.length <= maxChars -> text
        maxChars == 1 -> text.take(1)
        else -> text.take(maxChars - 1).trimEnd() + "…"
    }

    private val stopWords = setOf("de", "du", "des", "la", "le", "les", "et", "d", "l", "en", "au", "aux", "à")

    /** « Mathématiques » → « Ma », « Histoire-Géographie » → « HG », « Éducation physique » → « EP ». */
    fun initials(subject: String): String {
        val words = subject.split(' ', '-', '\'', '’', '.', '/').filter { it.isNotBlank() && it.lowercase() !in stopWords }
        val letters = words.map { it.first() }
        return when {
            words.isEmpty() -> subject.take(2)
            words.size == 1 -> {
                val word = words.first()
                word.take(1).uppercase() + word.drop(1).take(1).lowercase()
            }
            else -> letters.take(2).joinToString("").uppercase()
        }
    }

    // ----- journées -------------------------------------------------------------------------

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun toEntries(slots: List<WidgetSlot>): List<ScheduleEntry> =
        slots.map { ScheduleEntry(Subject(it.subject, it.teacher, it.room, it.colorIndex), "", it.canceled, it.date, it.start, it.end) }

    private fun joinDistinct(values: List<String>, separator: String) = values.filter { it.isNotBlank() }.distinct().joinToString(separator)

    /**
     * Les blocs d'une journée : doubles heures fondues ([buildDayTimeline]), cours simultanés (groupes)
     * regroupés en un seul bloc « A / B », cours annulé remplacé par un autre ignoré.
     */
    internal fun dayBlocks(slots: List<WidgetSlot>, nowMin: Int?): List<WeekBlock> {
        val timeline = runCatching { buildDayTimeline(toEntries(slots)) }.getOrNull() ?: return emptyList()
        val clusters = mutableListOf<MutableList<TimelineLesson>>()
        for (lesson in timeline.lessons) {
            val last = clusters.lastOrNull()
            if (last != null && lesson.startMin < last.maxOf { it.endMin }) last += lesson else clusters += mutableListOf(lesson)
        }
        return clusters.map { cluster ->
            val active = cluster.filter { !it.first.canceled }.ifEmpty { cluster }
            val canceled = active.all { it.first.canceled }
            val names = active.map { it.first.subject.name }.distinct()
            val start = active.minOf { it.startMin }
            val end = active.maxOf { it.endMin }
            WeekBlock(
                startMin = start,
                endMin = end,
                title = names.joinToString(" / "),
                initials = initials(names.first()),
                room = joinDistinct(active.map { it.first.subject.room }, "/"),
                teacher = joinDistinct(active.map { it.first.subject.teacher }, ", "),
                colorIndex = active.first().first.subject.colorIndex,
                canceled = canceled,
                live = nowMin != null && !canceled && nowMin in start until end,
                hours = if (active.size == 1) active.first().entries.size else 1,
            )
        }
    }

    // ----- semaine --------------------------------------------------------------------------

    /**
     * Les jours de la semaine à montrer : lundi à vendredi (samedi et dimanche s'ils ont cours). Le
     * week-end, une fois les cours de la semaine finis, on passe à la semaine suivante si elle est connue.
     */
    fun weekColumns(slots: List<WidgetSlot>, now: LocalDateTime): List<WeekColumn> {
        val today = now.toLocalDate()
        val dated = slots.mapNotNull { slot -> parseDate(slot.date)?.let { it to slot } }
        var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (today.dayOfWeek >= DayOfWeek.SATURDAY) {
            val next = monday.plusWeeks(1)
            val hasNext = dated.any { it.first >= next && it.first < next.plusDays(7) }
            val stillThisWeek = dated.any { it.first >= today && it.first < next }
            if (hasNext && !stillThisWeek) monday = next
        }
        val week = dated.filter { it.first >= monday && it.first < monday.plusDays(7) }
        val lastDay = when {
            week.any { it.first.dayOfWeek == DayOfWeek.SUNDAY } -> 6
            week.any { it.first.dayOfWeek == DayOfWeek.SATURDAY } -> 5
            else -> 4
        }
        val nowMin = now.hour * 60 + now.minute
        return (0..lastDay).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val daySlots = week.filter { it.first == date }.map { it.second }
            val real = daySlots.filter { !it.canceled }
            WeekColumn(
                date = date,
                label = FrenchFormat.weekdayShort(date),
                number = date.dayOfMonth,
                today = date == today,
                past = date < today,
                blocks = dayBlocks(daySlots, if (date == today) nowMin else null),
                lessons = real.size,
                canceled = daySlots.size - real.size,
                first = real.minOfOrNull { it.start }.orEmpty(),
                last = real.maxOfOrNull { it.end }.orEmpty(),
            )
        }
    }

    /** Les [capacity] jours à montrer en liste : tous, ou ceux qui commencent à aujourd'hui. */
    internal fun visibleDays(columns: List<WeekColumn>, capacity: Int): List<WeekColumn> {
        val room = capacity.coerceAtLeast(1)
        if (columns.size <= room) return columns
        val anchor = columns.indexOfFirst { it.today }.takeIf { it >= 0 } ?: columns.indexOfFirst { !it.past }.coerceAtLeast(0)
        val start = anchor.coerceAtMost(columns.size - room)
        return columns.subList(start, start + room)
    }

    fun weekTitle(columns: List<WeekColumn>): String {
        val first = columns.firstOrNull()?.date ?: return "Semaine"
        val last = columns.last().date
        val end = FrenchFormat.dayMonthShort(last)
        return if (first.month == last.month) "Semaine du ${first.dayOfMonth} au $end" else "Semaine du ${FrenchFormat.dayMonthShort(first)} au $end"
    }

    fun weekLayout(widthDp: Float, heightDp: Float, slots: List<WidgetSlot>, now: LocalDateTime): WeekLayout {
        val detail = weekDetail(widthDp, heightDp)
        val all = weekColumns(slots, now)
        val showTitle = detail == WeekDetail.Grid || detail == WeekDetail.Detailed || heightDp >= 150f
        val titleH = if (showTitle) TITLE_H + 4f else 0f
        val inner = widthDp - 2 * PAD
        val blocks = all.flatMap { it.blocks }
        val from = ((blocks.minOfOrNull { it.startMin } ?: (8 * 60)) / 60 * 60)
        val to = ((blocks.maxOfOrNull { it.endMin } ?: (17 * 60)) + 59) / 60 * 60
        val toMin = maxOf(to, from + 4 * 60)
        val gridHeight = (heightDp - 2 * PAD - titleH - HEADER_H - 4f).coerceAtLeast(20f)
        val shownColumns = when (detail) {
            WeekDetail.Summary -> visibleDays(all, ((heightDp - 2 * PAD - titleH) / SUMMARY_ROW).toInt())
            else -> all
        }
        val columnWidth = when (detail) {
            WeekDetail.Grid, WeekDetail.Detailed -> (inner - RAIL_W) / all.size
            else -> inner / all.size
        }
        return WeekLayout(
            detail = detail,
            columns = shownColumns,
            fromMin = from,
            toMin = toMin,
            dpPerMinute = gridHeight / (toMin - from),
            gridHeightDp = gridHeight,
            columnWidthDp = columnWidth,
            chipCapacity = ((heightDp - 2 * PAD - titleH - HEADER_H - 4f) / CHIP_ROW).toInt().coerceAtLeast(1),
            title = weekTitle(all),
            showTitle = showTitle,
            hourLabels = (from / 60 until toMin / 60).toList(),
            hiddenDays = all.size - shownColumns.size,
        )
    }

    /**
     * Les lignes de texte d'un bloc de la grille, déjà tronquées : nom, puis salle, puis horaire, puis
     * professeur, selon la hauteur et la largeur dont le bloc dispose. Vide si le bloc est trop petit.
     */
    fun blockLines(block: WeekBlock, heightDp: Float, widthDp: Float, detail: WeekDetail): List<String> {
        val detailed = detail == WeekDetail.Detailed
        val font = if (detailed) 10f else 9f
        val lineHeight = font * 1.3f
        val room = ((heightDp - 4f) / lineHeight).toInt()
        if (room < 1) return emptyList()
        val chars = charsThatFit(widthDp - STRIPE_W - 6f, font)
        if (chars < 2) return emptyList()
        /** La première forme qui tient dans la largeur, sinon la dernière tronquée. */
        fun pick(vararg options: String): String = options.firstOrNull { it.length <= chars } ?: truncate(options.last(), chars)
        val title = if (chars <= 3) block.initials else truncate(block.title, chars)
        val lines = mutableListOf(title)
        val extras = mutableListOf<String>()
        if (block.room.isNotBlank()) extras += if (detailed) pick("salle ${block.room}", block.room) else pick(block.room)
        if (detailed) extras += pick(block.timeLabel, block.start)
        if (detailed && block.teacher.isNotBlank()) extras += truncate(block.teacher, chars)
        else if (!detailed && block.hours > 1) extras += pick("${block.hours} h")
        for (extra in extras) {
            if (lines.size >= room) break
            lines += extra
        }
        return lines
    }

    // ----- journée --------------------------------------------------------------------------

    /**
     * Le jour à montrer : aujourd'hui tant qu'il reste un cours non annulé, sinon le prochain jour de
     * cours connu, sinon aujourd'hui (journée finie ou sans cours).
     */
    fun dayTarget(slots: List<WidgetSlot>, now: LocalDateTime): LocalDate {
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val remaining = slots.mapNotNull { slot ->
            val date = parseDate(slot.date) ?: return@mapNotNull null
            if (slot.canceled || date < today) return@mapNotNull null
            val end = runCatching { minutesOfClock(slot.end) }.getOrNull() ?: return@mapNotNull null
            if (date == today && end <= nowMin) null else date
        }
        return remaining.minOrNull() ?: today
    }

    private fun minutesOfClock(time: String): Int = time.take(2).toInt() * 60 + time.takeLast(2).toInt()

    /** « dans 25 min », « dans 1 h 05 », « demain 08:00 », « lundi 08:00 ». */
    fun startsIn(startMin: Int, date: LocalDate, now: LocalDateTime): String {
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        return when {
            date == today && startMin <= nowMin -> "maintenant"
            date == today -> "dans " + FrenchFormat.hoursMinutes(startMin - nowMin)
            date == today.plusDays(1) -> "demain ${clock(startMin)}"
            else -> "${FrenchFormat.weekdayFull(date)} ${clock(startMin)}"
        }
    }

    private fun relativeDay(date: LocalDate, today: LocalDate) = when (date) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        else -> FrenchFormat.capitalized(FrenchFormat.weekdayFull(date))
    }

    private fun rowOf(block: WeekBlock, past: Boolean) = DayRow(
        RowKind.Lesson, block.startMin, block.endMin, block.title, block.room, block.teacher,
        block.colorIndex, block.canceled, block.live, past, block.hours,
    )

    fun dayLayout(widthDp: Float, heightDp: Float, slots: List<WidgetSlot>, now: LocalDateTime): DayLayout {
        val detail = dayDetail(widthDp, heightDp)
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val date = dayTarget(slots, now)
        val isToday = date == today
        val daySlots = slots.filter { it.date == date.toString() }
        val blocks = dayBlocks(daySlots, if (isToday) nowMin else null)
        val real = blocks.filter { !it.canceled }
        val title = FrenchFormat.capitalized(FrenchFormat.weekdayDayMonth(date))

        val state = when {
            real.isEmpty() && slots.any { it.date == today.toString() && !it.canceled } -> DayState.Finished
            real.isEmpty() -> DayState.NoLessons
            !isToday -> DayState.NextDay
            real.all { it.endMin <= nowMin } -> DayState.Finished
            else -> DayState.Active
        }
        val base = DayLayout(
            detail, state, date, title, relativeDay(date, today), 0f, "", null, null, "", emptyList(), 0, real.size,
        )
        if (real.isEmpty()) return base

        val rows = blocks.map { rowOf(it, past = isToday && it.endMin <= nowMin) }
        val current = rows.firstOrNull { it.live }
        val next = rows.firstOrNull { !it.canceled && it.startMin > (if (isToday) nowMin else -1) && !it.live }
        val dayStart = real.minOf { it.startMin }
        val dayEnd = real.maxOf { it.endMin }
        val progress = if (isToday) ((nowMin - dayStart).toFloat() / (dayEnd - dayStart)).coerceIn(0f, 1f) else 0f
        val remaining = if (isToday && nowMin in dayStart until dayEnd) "Fin à ${clock(dayEnd)} · reste ${FrenchFormat.hoursMinutes(dayEnd - nowMin)}" else ""
        val withMeta = base.copy(
            progress = progress,
            remaining = remaining,
            current = current,
            next = next,
            nextIn = next?.let { startsIn(it.startMin, date, now) }.orEmpty(),
        )
        return when (detail) {
            DayDetail.Focus -> withMeta
            DayDetail.List -> listRows(withMeta, rows, heightDp, widthDp, nowMin)
            DayDetail.Timeline -> timelineRows(withMeta, rows, heightDp, widthDp)
        }
    }

    /** Les temps libres entre deux cours, nommés comme dans l'agenda. */
    private fun withGaps(lessons: List<DayRow>): List<DayRow> {
        val result = mutableListOf<DayRow>()
        var covered = lessons.firstOrNull()?.startMin ?: 0
        lessons.forEach { row ->
            if (result.isNotEmpty() && row.startMin - covered >= 15) {
                result += DayRow(
                    RowKind.Gap, covered, row.startMin, gapLabel(covered, row.startMin), "", "", 0,
                    canceled = false, live = false, past = row.past,
                )
            }
            result += row
            covered = maxOf(covered, row.endMin)
        }
        return result
    }

    private fun listRows(layout: DayLayout, lessons: List<DayRow>, heightDp: Float, widthDp: Float, nowMin: Int): DayLayout {
        val upcoming = lessons.filter { !it.past }
        val available = heightDp - DAY_HEADER_H
        val chars = charsThatFit(widthDp - 2 * PAD - 60f, 12f)
        val shown = mutableListOf<DayRow>()
        var used = 0f
        for (row in withGaps(upcoming)) {
            val h = if (row.kind == RowKind.Gap) LIST_GAP_H else LIST_LESSON_H
            if (used + h > available && shown.any { it.kind == RowKind.Lesson }) break
            shown += if (row.kind == RowKind.Gap) row.copy(lines = listOf("${row.title} · ${formatMinutes(row.duration)}"))
            else row.copy(lines = listLines(row, chars))
            used += h
        }
        while (shown.lastOrNull()?.kind == RowKind.Gap) shown.removeAt(shown.lastIndex)
        val hidden = upcoming.count { it.kind == RowKind.Lesson } - shown.count { it.kind == RowKind.Lesson }
        return layout.copy(rows = shown, hiddenLessons = hidden.coerceAtLeast(0))
    }

    private fun listLines(row: DayRow, chars: Int): List<String> {
        val place = row.room.takeIf { it.isNotBlank() }?.let { "salle $it" }
        val time = "${row.start} – ${row.end}"
        val suffix = if (row.hours > 1) " · ${row.hours} h" else ""
        val detail = listOfNotNull(time + suffix, place).joinToString(" · ")
        val title = if (row.canceled) row.title + " (annulé)" else row.title
        return listOf(truncate(title, chars + 6), truncate(detail, chars + 6))
    }

    private fun timelineRows(layout: DayLayout, lessons: List<DayRow>, heightDp: Float, widthDp: Float): DayLayout {
        val all = withGaps(lessons)
        val available = heightDp - DAY_HEADER_H
        val firstUpcoming = all.indexOfFirst { !it.past }.let { if (it < 0) all.size else it }
        fun minimum(list: List<DayRow>) = list.sumOf { (if (it.kind == RowKind.Gap) TIMELINE_GAP_H else TIMELINE_MIN).toDouble() }.toFloat()

        // 1. On sacrifie d'abord les cours déjà passés, 2. puis la fin de journée.
        var from = 0
        while (from < firstUpcoming && minimum(all.drop(from)) > available) from++
        var slice = all.drop(from)
        while (slice.count { it.kind == RowKind.Lesson } > 1 && minimum(slice) > available) slice = slice.dropLast(1)
        while (slice.lastOrNull()?.kind == RowKind.Gap) slice = slice.dropLast(1)

        // 3. La place qui reste est répartie entre les cours selon leur durée.
        val lessonsInSlice = slice.filter { it.kind == RowKind.Lesson }
        val spare = (available - minimum(slice)).coerceAtLeast(0f)
        val totalDuration = lessonsInSlice.sumOf { it.duration }.coerceAtLeast(1)
        val chars = charsThatFit(widthDp - 2 * PAD - 44f - 12f, 12f)
        val sized = slice.map { row ->
            if (row.kind == RowKind.Gap) {
                row.copy(heightDp = TIMELINE_GAP_H, lines = listOf("${row.title} · ${formatMinutes(row.duration)}"))
            } else {
                val h = (TIMELINE_MIN + spare * row.duration / totalDuration).coerceAtMost(TIMELINE_MAX)
                row.copy(heightDp = h, lines = timelineLines(row, h, chars))
            }
        }
        val skippedPast = all.take(from).count { it.kind == RowKind.Lesson }
        return layout.copy(rows = sized, hiddenLessons = (lessons.size - lessonsInSlice.size - skippedPast).coerceAtLeast(0))
    }

    private fun timelineLines(row: DayRow, heightDp: Float, chars: Int): List<String> {
        val room = ((heightDp - 6f) / 14.5f).toInt().coerceAtLeast(1)
        val title = (if (row.canceled) row.title + " (annulé)" else row.title) + if (row.hours > 1) " · ${row.hours} h" else ""
        val place = row.room.takeIf { it.isNotBlank() }?.let { "salle $it" }
        val lines = mutableListOf(truncate(title, chars))
        if (room == 1) return lines
        lines += truncate(listOfNotNull("${row.start} – ${row.end}", place).joinToString(" · "), chars + 4)
        if (room >= 3 && row.teacher.isNotBlank()) lines += truncate(row.teacher, chars + 4)
        return lines
    }
}
