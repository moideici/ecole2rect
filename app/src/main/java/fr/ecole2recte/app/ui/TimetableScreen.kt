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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import fr.ecole2recte.app.ui.components.LoadingRow
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
import java.time.LocalDate
import java.time.LocalDateTime

enum class TimetableMode(val label: String) { DAY("Jour"), WEEK("Semaine") }

/**
 * Ce que l'écran doit retrouver quand on en revient.
 *
 * L'état vivait dans l'écran ; ouvrir un devoir le sortait de la composition, et le retour
 * ramenait sur aujourd'hui au lieu du jour qu'on regardait. Il est donc tenu au-dessus de la
 * fiche de détail, et enregistré : une rotation ne le perd pas non plus.
 */
@Stable
class TimetableState internal constructor(
    day: LocalDate,
    week: LocalDate = Dates.startOfWeek(day),
    mode: TimetableMode = TimetableMode.DAY,
) {
    var mode by mutableStateOf(mode)
    var weekStart by mutableStateOf(week)
    var selectedDay by mutableStateOf(day)

    /** Montrer un jour, c'est aussi ouvrir la semaine qui le contient. */
    fun showDay(day: LocalDate) {
        selectedDay = day
        weekStart = Dates.startOfWeek(day)
    }

    /** Changer de semaine amène sur son lundi : c'est le repère de la vue semaine. */
    fun showWeek(start: LocalDate) {
        weekStart = start
        selectedDay = start
    }

    companion object {
        val Saver: Saver<TimetableState, List<String>> = listSaver(
            save = { listOf(it.selectedDay.toString(), it.weekStart.toString(), it.mode.name) },
            restore = {
                TimetableState(
                    day = LocalDate.parse(it[0]),
                    week = LocalDate.parse(it[1]),
                    mode = TimetableMode.valueOf(it[2]),
                )
            },
        )
    }
}

@Composable
fun rememberTimetableState(today: LocalDate): TimetableState =
    rememberSaveable(saver = TimetableState.Saver) { TimetableState(today) }

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
 *
 * Une seule semaine est chargée à la fois : ouvrir une semaine qu'on n'a pas encore vue la
 * demande, et la barre de chargement dit qu'elle arrive plutôt que de laisser croire au vide.
 */
@Composable
fun TimetableScreen(
    state: TimetableState,
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    loadingWeeks: Set<LocalDate>,
    onLoadWeek: (LocalDate) -> Unit,
    onOpenLesson: (Lesson) -> Unit,
    onOpenHomework: (HomeworkItem) -> Unit,
    onRefreshWeek: (LocalDate) -> Unit,
    onSearch: () -> Unit = {},
) {
    val today = now.toLocalDate()
    val mode = state.mode
    val weekStart = state.weekStart
    val selectedDay = state.selectedDay
    val loading = weekStart in loadingWeeks
    val onRefresh: () -> Unit = { onRefreshWeek(weekStart) }

    val week = Insights.week(snapshot.lessons, weekStart)
    val summary = Insights.daySummary(snapshot.lessons, selectedDay)
    val lessons = Insights.lessonsOn(snapshot.lessons, selectedDay)
    val homework = Insights.homeworkDueOn(snapshot.homework, selectedDay)

    // Changer de semaine la demande, une fois. Le dépôt ignore une semaine déjà connue,
    // donc revenir en arrière n'appelle rien.
    LaunchedEffect(weekStart) { onLoadWeek(weekStart) }

    // Balayer horizontalement change de jour, comme dans un agenda. Le samedi mène au lundi
    // suivant : personne n'a cours le dimanche, et une journée vide n'est pas une étape.
    val swipe = Modifier.pointerInput(selectedDay, mode) {
        var travelled = 0f
        val threshold = 56.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (mode == TimetableMode.DAY && kotlin.math.abs(travelled) > threshold) {
                    state.showDay(
                        if (travelled < 0) nextSchoolDay(selectedDay) else previousSchoolDay(selectedDay),
                    )
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
                    FilterPill(entry.label, selected = mode == entry) { state.mode = entry }
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
                    if (byDay) state.showDay(previousSchoolDay(selectedDay))
                    else state.showWeek(weekStart.minusWeeks(1))
                }
                RoundIconButton(Icons.Outlined.CalendarMonth, "Revenir à aujourd'hui") {
                    state.showDay(today)
                }
                RoundIconButton(
                    Icons.Outlined.ChevronRight,
                    if (byDay) "Jour suivant" else "Semaine suivante",
                ) {
                    if (byDay) state.showDay(nextSchoolDay(selectedDay))
                    else state.showWeek(weekStart.plusWeeks(1))
                }
            }
        }

        sync.errorFor(DataSection.TIMETABLE)?.let { error ->
            item("error") { SectionError(error, onRefresh) }
        }

        if (loading) {
            item("loading") {
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    LoadingRow("Chargement de la semaine…")
                }
            }
        }

        when (mode) {
            TimetableMode.WEEK -> weekContent(
                week = week,
                snapshot = snapshot,
                settings = settings,
                today = today,
                loading = loading,
                onSelectDay = { day ->
                    state.selectedDay = day
                    state.mode = TimetableMode.DAY
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
                loading = loading,
                onSelectDay = { state.selectedDay = it },
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
    loading: Boolean,
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
        item("summary") {
            DaySummaryCard(
                summary = summary,
                pause = Insights.middayBreak(snapshot.lessons, selectedDay),
                now = now,
            )
        }
    }

    if (lessons.isEmpty()) {
        if (!loading) {
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
        }
    } else {
        // La grille remplace la liste : un cours de trois heures y est trois fois plus haut
        // qu'un cours d'une heure, et les trous se voient sans qu'on ait à les annoncer.
        item("grille") {
            DayGrid(
                lessons = lessons,
                settings = settings,
                date = selectedDay,
                now = now,
                onOpenLesson = onOpenLesson,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
                homeworkFor = { lesson -> Insights.homeworkForLesson(snapshot.homework, lesson).size },
            )
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
    loading: Boolean,
    onSelectDay: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
) {
    if (week.all { it.isEmpty }) {
        // Une semaine qui n'est pas encore arrivée n'est pas une semaine vide : la barre
        // de chargement le dit déjà au-dessus, inutile d'annoncer un creux qui n'existe pas.
        if (loading) return
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
    // Six colonnes sur une même règle d'heures : c'est ce qui permet de comparer deux
    // journées d'un regard — voir que le mardi finit plus tôt, que le mercredi est vide.
    // Une liste de six résumés ne le montre pas.
    item("grille-semaine") {
        WeekGrid(
            days = week.map { it.date },
            lessonsOf = { day -> Insights.lessonsOn(snapshot.lessons, day) },
            settings = settings,
            today = today,
            onSelectDay = onSelectDay,
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
        )
    }
}

/** Le résumé d'une journée : ce qu'on veut savoir avant même de lire la grille. */
@Composable
private fun DaySummaryCard(
    summary: Insights.DaySummary,
    pause: Insights.Interval?,
    now: LocalDateTime,
) {
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
                            // Les trois repères de la journée, dans l'ordre où on les vit.
                            // La pause n'est écrite nulle part côté serveur : elle est
                            // reconnue, et tue quand la journée est continue.
                            listOfNotNull(
                                "${Dates.time(start)} — ${Dates.time(end)}",
                                pause?.let { "pause ${Dates.time(it.start)}–${Dates.time(it.end)}" },
                            ).joinToString(" · "),
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
