package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.ProgressBar
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SectionError
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private enum class TimetableMode(val label: String) { DAY("Jour"), WEEK("Semaine") }

/** Le jour d'école suivant : le dimanche n'existe pas dans un emploi du temps. */
private fun nextSchoolDay(date: LocalDate): LocalDate =
    if (date.dayOfWeek == DayOfWeek.SATURDAY) date.plusDays(2) else date.plusDays(1)

private fun previousSchoolDay(date: LocalDate): LocalDate =
    if (date.dayOfWeek == DayOfWeek.MONDAY) date.minusDays(2) else date.minusDays(1)

/**
 * L'emploi du temps.
 *
 * Deux lectures : la journée, heure par heure, avec ses trous et les devoirs à rendre pour
 * chaque cours ; et la semaine, pour choisir où regarder. Le cours en cours est toujours
 * repérable d'un coup d'œil, et un cours annulé se voit sans se lire.
 */
@Composable
fun TimetableScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    onOpenLesson: (Lesson) -> Unit,
    onOpenHomework: (HomeworkItem) -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit = {},
) {
    val today = now.toLocalDate()
    var mode by remember { mutableStateOf(TimetableMode.DAY) }
    var weekStart by remember { mutableStateOf(Dates.startOfWeek(today)) }
    var selectedDay by remember { mutableStateOf(today) }

    val week = Insights.week(snapshot.lessons, weekStart)
    val summary = Insights.daySummary(snapshot.lessons, selectedDay)
    val lessons = Insights.lessonsOn(snapshot.lessons, selectedDay)
    val homework = Insights.homeworkDueOn(snapshot.homework, selectedDay)

    // Balayer horizontalement change de jour, comme dans un agenda. Le samedi mène au lundi
    // suivant : personne n'a cours le dimanche, et une journée vide n'est pas une étape.
    val swipe = Modifier.pointerInput(selectedDay, mode) {
        var travelled = 0f
        val threshold = 56.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (mode == TimetableMode.DAY && kotlin.math.abs(travelled) > threshold) {
                    val target = if (travelled < 0) nextSchoolDay(selectedDay) else previousSchoolDay(selectedDay)
                    selectedDay = target
                    if (target.isBefore(weekStart) || target.isAfter(weekStart.plusDays(5))) {
                        weekStart = Dates.startOfWeek(target)
                    }
                }
            },
        ) { _, amount -> travelled += amount }
    }

    LazyColumn(
        modifier = swipe,
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item("header") {
            ScreenHeader(
                eyebrow = "Emploi du temps",
                title = when (mode) {
                    TimetableMode.DAY -> Dates.relativeDay(selectedDay, today).replaceFirstChar { it.uppercase() }
                    TimetableMode.WEEK -> "Semaine du ${Dates.dayLabel(weekStart)} ${Dates.monthName(weekStart)}"
                },
                subtitle = when (mode) {
                    TimetableMode.DAY -> Dates.dayAndMonth(selectedDay)
                    TimetableMode.WEEK ->
                        "${week.sumOf { it.lessons }} cours · ${Dates.duration(week.sumOf { it.minutes })}"
                },
                actions = {
                    RoundIconButton(Icons.Outlined.Search, "Rechercher", onClick = onSearch)
                    RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh)
                },
            )
        }

        item("mode") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimetableMode.entries.forEach { entry ->
                    FilterPill(entry.label, selected = mode == entry) { mode = entry }
                }
                Spacer(Modifier.weight(1f))
                // Les flèches suivent le mode affiché : en « Jour » elles avancent d'un
                // jour d'école, comme le balayage horizontal — dont c'est le seul équivalent
                // pour qui navigue au lecteur d'écran.
                val byDay = mode == TimetableMode.DAY
                RoundIconButton(
                    Icons.Outlined.ChevronLeft,
                    if (byDay) "Jour précédent" else "Semaine précédente",
                ) {
                    if (byDay) {
                        val target = previousSchoolDay(selectedDay)
                        selectedDay = target
                        weekStart = Dates.startOfWeek(target)
                    } else {
                        weekStart = weekStart.minusWeeks(1)
                        selectedDay = weekStart
                    }
                }
                RoundIconButton(Icons.Outlined.CalendarMonth, "Revenir à aujourd'hui") {
                    weekStart = Dates.startOfWeek(today)
                    selectedDay = today
                }
                RoundIconButton(
                    Icons.Outlined.ChevronRight,
                    if (byDay) "Jour suivant" else "Semaine suivante",
                ) {
                    if (byDay) {
                        val target = nextSchoolDay(selectedDay)
                        selectedDay = target
                        weekStart = Dates.startOfWeek(target)
                    } else {
                        weekStart = weekStart.plusWeeks(1)
                        selectedDay = weekStart
                    }
                }
            }
        }

        sync.errorFor(DataSection.TIMETABLE)?.let { error ->
            item("error") { SectionError(error, onRefresh) }
        }

        when (mode) {
            TimetableMode.WEEK -> weekContent(
                week = week,
                snapshot = snapshot,
                settings = settings,
                today = today,
                onSelectDay = { day ->
                    selectedDay = day
                    mode = TimetableMode.DAY
                },
                onRefresh = onRefresh,
            )

            TimetableMode.DAY -> dayContent(
                summary = summary,
                lessons = lessons,
                homework = homework,
                snapshot = snapshot,
                settings = settings,
                today = today,
                now = now,
                weekStart = weekStart,
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it },
                onOpenLesson = onOpenLesson,
                onOpenHomework = onOpenHomework,
                onRefresh = onRefresh,
            )
        }
    }
}

/** Vue jour : sélecteur, résumé, cours avec leurs trous, puis les devoirs du jour. */
private fun LazyListScope.dayContent(
    summary: Insights.DaySummary,
    lessons: List<Lesson>,
    homework: List<HomeworkItem>,
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    now: LocalDateTime,
    weekStart: LocalDate,
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onOpenLesson: (Lesson) -> Unit,
    onOpenHomework: (HomeworkItem) -> Unit,
    onRefresh: () -> Unit,
) {
    item("days") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0..5).map { weekStart.plusDays(it.toLong()) }, key = { it.toString() }) { day ->
                DayChip(
                    day = day,
                    selected = day == selectedDay,
                    isToday = day == today,
                    summary = Insights.daySummary(snapshot.lessons, day),
                    onClick = { onSelectDay(day) },
                )
            }
        }
    }

    if (!summary.isEmpty) {
        item("summary") { DaySummaryCard(summary, now) }
    }

    if (lessons.isEmpty()) {
        item("empty") {
            StatePlaceholder(
                icon = Icons.Outlined.CalendarMonth,
                title = "Aucun cours ce jour-là",
                description = if (snapshot.lessons.isEmpty()) {
                    "L'emploi du temps n'a pas encore été synchronisé."
                } else {
                    "Journée libre, ou emploi du temps non publié pour cette date."
                },
                actionLabel = "Synchroniser",
                onAction = onRefresh,
            )
        }
    } else {
        itemsIndexed(lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
            Column {
                GapBetween(lessons.getOrNull(index - 1), lesson)
                LessonCard(
                    lesson = lesson,
                    settings = settings,
                    now = now,
                    homework = Insights.homeworkForLesson(snapshot.homework, lesson),
                    onClick = { onOpenLesson(lesson) },
                )
            }
        }
    }

    if (homework.isNotEmpty()) {
        item("homework-title") {
            Text(
                "À RENDRE CE JOUR-LÀ",
                modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 20.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(homework, key = { "hw-${it.id}" }) { item ->
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard(onClick = { onOpenHomework(item) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(subjectColor(item.subject, settings), 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.subject,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.content.isNotBlank()) {
                                Text(
                                    item.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (item.exam) Tag("Évaluation", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Vue semaine : six journées résumées, une carte par jour, toutes cliquables. */
private fun LazyListScope.weekContent(
    week: List<Insights.DaySummary>,
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
) {
    if (week.all { it.isEmpty }) {
        item("week-empty") {
            StatePlaceholder(
                icon = Icons.Outlined.CalendarMonth,
                title = "Semaine vide",
                description = "Aucun cours n'est publié pour cette semaine.",
                actionLabel = "Synchroniser",
                onAction = onRefresh,
            )
        }
        return
    }
    items(week, key = { "week-${it.date}" }) { day ->
        val lessons = Insights.lessonsOn(snapshot.lessons, day.date)
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
            AppCard(
                containerColor = if (day.date == today) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                onClick = { onSelectDay(day.date) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Dates.dayName(day.date).replaceFirstChar { it.uppercase() } +
                                " ${day.date.dayOfMonth}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (day.isEmpty) {
                                "Pas de cours"
                            } else {
                                "${day.lessons} cours · ${Dates.time(day.start!!)} — ${Dates.time(day.end!!)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (day.exams > 0) Tag("${day.exams} éval.", MaterialTheme.colorScheme.error)
                    if (day.canceled > 0) {
                        Spacer(Modifier.width(6.dp))
                        Tag("${day.canceled} annulé", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (lessons.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    lessons.forEach { lesson ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Dates.time(lesson.start),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp),
                            )
                            SubjectDot(
                                subjectColor(lesson.subject, settings)
                                    .copy(alpha = if (lesson.canceled) 0.35f else 1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                lesson.subject,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (lesson.canceled) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (lesson.room.isNotBlank()) {
                                Text(
                                    lesson.room,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Le résumé d'une journée : ce qu'on veut savoir avant de dérouler la liste. */
@Composable
private fun DaySummaryCard(summary: Insights.DaySummary, now: LocalDateTime) {
    val start = summary.start
    val end = summary.end
    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${summary.lessons} cours · ${Dates.duration(summary.minutes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (start != null && end != null) {
                        Text(
                            "${Dates.time(start)} — ${Dates.time(end)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (summary.exams > 0) Tag("${summary.exams} évaluation", MaterialTheme.colorScheme.error)
                if (summary.canceled > 0) {
                    Spacer(Modifier.width(6.dp))
                    Tag("${summary.canceled} annulé", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Avancement de la journée : utile le matin pour savoir ce qu'il reste.
            if (start != null && end != null && summary.date == now.toLocalDate()) {
                Spacer(Modifier.height(12.dp))
                ProgressBar(fraction = Dates.progress(start, end, now))
            }
        }
    }
}

/** Les chips de jour donnent la charge de la journée avant même de l'ouvrir. */
@Composable
private fun DayChip(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    summary: Insights.DaySummary,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .background(container, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Dates.dayName(day).take(3), style = MaterialTheme.typography.labelMedium, color = content)
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = content)
        Spacer(Modifier.height(5.dp))
        Text(
            if (summary.isEmpty) "—" else Dates.duration(summary.minutes),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.75f),
        )
    }
}

/** Un trou d'au moins trente minutes mérite d'être montré, pas masqué. */
@Composable
private fun GapBetween(previous: Lesson?, lesson: Lesson) {
    if (previous == null) return
    val minutes = Duration.between(previous.end, lesson.start).toMinutes()
    if (minutes < 30) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding + 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "${Dates.duration(minutes)} de libre",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    settings: AppSettings,
    now: LocalDateTime,
    homework: List<HomeworkItem>,
    onClick: () -> Unit,
) {
    val accent = subjectColor(lesson.subject, settings)
    val past = lesson.end.isBefore(now)
    val current = !past && !lesson.start.isAfter(now)
    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
        AppCard(
            containerColor = if (current) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            onClick = onClick,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(52.dp)) {
                    Text(
                        Dates.time(lesson.start),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (past) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        Dates.time(lesson.end),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .width(4.dp)
                        .height(46.dp)
                        .background(
                            accent.copy(alpha = if (past) 0.35f else 1f),
                            RoundedCornerShape(4.dp),
                        ),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        lesson.subject,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (lesson.canceled) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOfNotNull(
                        lesson.room.takeIf { it.isNotBlank() }?.let { "Salle $it" },
                        lesson.teacher.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (lesson.canceled) Tag("Annulé", MaterialTheme.colorScheme.error)
                    if (lesson.exam) Tag("Devoir", accent)
                    if (current) Tag("En cours", MaterialTheme.colorScheme.primary)
                }
            }
            // Le croisement qui manque partout ailleurs : ce qu'il faut rendre à ce cours-là.
            if (homework.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                homework.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectDot(accent, 6.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (item.exam) "Évaluation" else "À rendre",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.content.ifBlank { "voir le cahier de textes" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (lesson.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    lesson.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
