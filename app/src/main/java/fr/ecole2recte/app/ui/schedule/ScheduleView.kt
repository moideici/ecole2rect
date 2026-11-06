package fr.ecole2recte.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.CalendarExport
import fr.ecole2recte.app.domain.ChangeLine
import fr.ecole2recte.app.domain.ScheduleChanges
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.UpcomingExam
import fr.ecole2recte.app.ui.common.AddToCalendarRow
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.ExamCountdownCard
import fr.ecole2recte.app.ui.common.rememberUpcomingExams
import fr.ecole2recte.app.ui.common.ScheduleViewModel
import fr.ecole2recte.app.ui.common.RefreshOnResume
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Emploi du temps : un jour à la fois (on glisse d'un jour à l'autre) ou toute la semaine en grille. */
@Composable
fun ScheduleList(listState: LazyListState) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { ScheduleViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel)
    val exams = rememberUpcomingExams(repository)
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh) { week ->
        ScheduleContent(week, listState, exams)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleContent(week: ScheduleWeek, listState: LazyListState, exams: List<UpcomingExam>) {
    val settings = rememberAppContainer().settings
    val weekView by settings.scheduleWeekView.collectAsStateWithLifecycle()
    val zoom by settings.scheduleZoom.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ScheduleEntry?>(null) }
    val byDate = remember(week) { week.entries.groupBy { it.date } }
    val weekStart = remember(week) { runCatching { LocalDate.parse(week.weekStart) }.getOrNull() }
    val today = remember(week) { runCatching { LocalDate.parse(week.today) }.getOrNull() }
    if (weekStart == null) {
        EmptyState("Pas de cours", "Aucun cours n'est prévu cette semaine.")
        return
    }
    // Deux semaines de jours ; un samedi ou un dimanche sans cours n'a rien à montrer.
    val days = remember(week) {
        (0L..13L).map { weekStart.plusDays(it) }.filter { day ->
            day.dayOfWeek.value <= 5 || byDate.containsKey(day.toString())
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(!weekView, { settings.setScheduleWeekView(false) }, label = { Text("Jour", fontSize = 13.sp) })
            FilterChip(weekView, { settings.setScheduleWeekView(true) }, label = { Text("Semaine", fontSize = 13.sp) })
            Spacer(Modifier.weight(1f))
            ZoomControls(zoom, settings::setScheduleZoom)
        }
        ExamCountdownCard(exams, compact = true)
        if (!weekView) {
            DayView(days, byDate, today, zoom, settings::setScheduleZoom, listState) { selected = it }
        } else {
            WeekPane(days, byDate, today, zoom, settings::setScheduleZoom) { selected = it }
        }
    }
    selected?.let { entry ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AppTheme.colors.surface) {
            LessonDetail(entry)
        }
    }
}

/** Boutons − et + de l'échelle verticale ; le pincement à deux doigts fait la même chose. */
@Composable
private fun ZoomControls(zoom: Float, onZoom: (Float) -> Unit) {
    val percent = ScheduleZoom.percent(zoom)
    IconButton(
        onClick = { onZoom(ScheduleZoom.zoomOut(zoom)) },
        enabled = ScheduleZoom.canZoomOut(zoom),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(Icons.Outlined.ZoomOut, contentDescription = "Réduire l'échelle (actuellement $percent %)", tint = AppTheme.colors.ink)
    }
    IconButton(
        onClick = { onZoom(ScheduleZoom.zoomIn(zoom)) },
        enabled = ScheduleZoom.canZoomIn(zoom),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(Icons.Outlined.ZoomIn, contentDescription = "Agrandir l'échelle (actuellement $percent %)", tint = AppTheme.colors.ink)
    }
}

// --- Vue jour ------------------------------------------------------------------------------

@Composable
private fun DayView(
    days: List<LocalDate>,
    byDate: Map<String, List<ScheduleEntry>>,
    today: LocalDate?,
    zoom: Float,
    onZoom: (Float) -> Unit,
    listState: LazyListState,
    onSelect: (ScheduleEntry) -> Unit,
) {
    val currentZoom by rememberUpdatedState(zoom)
    val initial = days.indexOfFirst { it == today }.takeIf { it >= 0 }
        ?: days.indexOfFirst { it > (today ?: days.first()) }.takeIf { it >= 0 }
        ?: 0
    val pagerState = rememberPagerState(initialPage = initial) { days.size }
    val scope = rememberCoroutineScope()
    val stripState = rememberLazyListState()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { stripState.animateScrollToItem((it - 1).coerceAtLeast(0)) }
    }
    LazyRow(
        state = stripState,
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(days) { index, day ->
            val selected = pagerState.currentPage == index
            val has = byDate[day.toString()].orEmpty().isNotEmpty()
            Column(
                Modifier
                    .width(52.dp)
                    .background(if (selected) AppTheme.colors.accentFill else AppTheme.colors.surface, RoundedCornerShape(14.dp))
                    .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() },
                    color = if (selected) Color.White else AppTheme.colors.muted,
                    fontSize = 12.sp,
                )
                Text(
                    day.dayOfMonth.toString(),
                    color = if (selected) Color.White else AppTheme.colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier.padding(top = 3.dp).size(5.dp).background(
                        when {
                            day == today -> if (selected) Color.White else AppTheme.colors.accent
                            has -> AppTheme.colors.muted.copy(alpha = .5f)
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(3.dp),
                    ),
                )
            }
        }
    }
    HorizontalPager(pagerState, Modifier.fillMaxSize().pinchToZoom({ currentZoom }, onZoom)) { index ->
        val day = days[index]
        val entries = byDate[day.toString()].orEmpty().sortedBy { it.start }
        val state = if (index == pagerState.currentPage) listState else rememberLazyListState()
        val now = if (day == today) rememberClock() else null
        LazyColumn(state = state, contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) {
            item {
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
                    color = AppTheme.colors.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                )
            }
            if (entries.isEmpty()) {
                item { EmptyState("Pas de cours", "Rien de prévu ce jour-là.") }
            } else {
                item {
                    DayTimelineView(
                        entries = entries,
                        now = now,
                        zoom = zoom,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

// --- Vue semaine ---------------------------------------------------------------------------

/** Une semaine à la fois (cette semaine, la suivante) sur une frise horaire commune. */
@Composable
private fun WeekPane(
    days: List<LocalDate>,
    byDate: Map<String, List<ScheduleEntry>>,
    today: LocalDate?,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onSelect: (ScheduleEntry) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val weeks = remember(days) { days.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toList() }
    val index = page.coerceIn(0, weeks.size - 1)
    Column(Modifier.fillMaxSize()) {
        if (weeks.size > 1) {
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weeks.forEachIndexed { i, (monday, _) ->
                    val label = monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
                    FilterChip(index == i, { page = i }, label = { Text("Sem. du $label", fontSize = 13.sp) })
                }
            }
        }
        WeekTimelineView(weeks[index].second, byDate, today, zoom, onZoom, Modifier.weight(1f), onSelect)
    }
}

// --- Détail ---------------------------------------------------------------------------------

@Composable
private fun LessonDetail(entry: ScheduleEntry) {
    val subject = entry.subject
    val day = runCatching {
        LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }.getOrDefault(entry.date)
    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(subject.name, color = AppTheme.colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("$day · ${entry.start} — ${entry.end}", color = AppTheme.colors.muted, fontSize = 14.sp)
        ScheduleChanges.tag(entry)?.let {
            Text("$it depuis la dernière lecture de l'emploi du temps.", color = AppTheme.colors.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        ScheduleChanges.lines(entry).forEach { ChangedLine(it) }
        if (entry.canceled) Text("Ce cours est annulé.", color = AppTheme.colors.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (entry.exam) Text("Une évaluation est prévue.", color = AppTheme.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (subject.room.isNotBlank()) DetailLine("Salle", subject.room)
        if (subject.teacher.isNotBlank()) DetailLine("Professeur", subject.teacher)
        if (entry.note.isNotBlank()) DetailLine("Note", entry.note)
        AddToCalendarRow(remember(entry) { CalendarExport.lesson(entry, ZoneId.systemDefault()) })
    }
}

/** Une valeur qui a changé : l'ancienne barrée, puis la nouvelle. */
@Composable
private fun ChangedLine(line: ChangeLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(line.label, color = AppTheme.colors.muted, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(line.old, color = AppTheme.colors.muted, fontSize = 14.sp, textDecoration = TextDecoration.LineThrough)
        Text("  →  ", color = AppTheme.colors.muted, fontSize = 14.sp)
        Text(line.new, color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, color = AppTheme.colors.muted, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
