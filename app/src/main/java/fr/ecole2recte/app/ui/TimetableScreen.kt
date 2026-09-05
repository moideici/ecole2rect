package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SectionError
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * L'emploi du temps.
 *
 * Une semaine à la fois, un jour sélectionné, et les trous laissés visibles : savoir qu'on a
 * deux heures de libre est aussi utile que savoir où on doit être.
 */
@Composable
fun TimetableScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    onRefresh: () -> Unit,
) {
    val today = now.toLocalDate()
    var weekStart by remember { mutableStateOf(Dates.startOfWeek(today)) }
    var selectedDay by remember { mutableStateOf(today) }

    val days = (0..5).map { weekStart.plusDays(it.toLong()) }
    val lessons = Insights.lessonsOn(snapshot.lessons, selectedDay)

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            ScreenHeader(
                eyebrow = "Emploi du temps",
                title = Dates.relativeDay(selectedDay, today).replaceFirstChar { it.uppercase() },
                subtitle = "Semaine du ${Dates.dayAndMonth(weekStart)}",
                actions = { RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh) },
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill("‹ Semaine", selected = false) {
                    weekStart = weekStart.minusWeeks(1)
                    selectedDay = weekStart
                }
                FilterPill("Aujourd'hui", selected = selectedDay == today) {
                    weekStart = Dates.startOfWeek(today)
                    selectedDay = today
                }
                FilterPill("Semaine ›", selected = false) {
                    weekStart = weekStart.plusWeeks(1)
                    selectedDay = weekStart
                }
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days, key = { it.toString() }) { day ->
                    DayChip(
                        day = day,
                        selected = day == selectedDay,
                        isToday = day == today,
                        lessonCount = snapshot.lessons.count { it.date == day },
                        onClick = { selectedDay = day },
                    )
                }
            }
        }

        sync.errorFor(DataSection.TIMETABLE)?.let { error ->
            item { SectionError(error, onRefresh) }
        }

        if (lessons.isEmpty()) {
            item {
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
            itemsIndexed(lessons) { index, lesson ->
                val previous = lessons.getOrNull(index - 1)
                Column {
                    GapBetween(previous, lesson)
                    LessonCard(lesson, settings, now)
                }
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
    lessonCount: Int,
    onClick: () -> Unit,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
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
        Spacer(Modifier.height(4.dp))
        Text(
            if (lessonCount == 0) "—" else "$lessonCount",
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.7f),
        )
    }
}

/** Un trou d'au moins trente minutes mérite d'être montré, pas masqué. */
@Composable
private fun GapBetween(previous: Lesson?, lesson: Lesson) {
    if (previous == null) return
    val minutes = java.time.Duration.between(previous.end, lesson.start).toMinutes()
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
private fun LessonCard(lesson: Lesson, settings: AppSettings, now: LocalDateTime) {
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
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(52.dp)) {
                    Text(
                        Dates.time(lesson.start),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
                    if (lesson.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            lesson.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (lesson.canceled) Tag("Annulé", MaterialTheme.colorScheme.error)
                    if (lesson.exam) Tag("Devoir", accent)
                    if (current) Tag("En cours", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
