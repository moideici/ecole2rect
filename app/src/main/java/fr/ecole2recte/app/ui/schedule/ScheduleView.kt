package fr.ecole2recte.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.ScheduleViewModel
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val MODE_DAY = 0
private const val MODE_WEEK = 1

/** Emploi du temps : un jour à la fois (on glisse d'un jour à l'autre) ou toute la semaine en grille. */
@Composable
fun ScheduleList(listState: LazyListState) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { ScheduleViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh) { week ->
        ScheduleContent(week, listState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleContent(week: ScheduleWeek, listState: LazyListState) {
    var mode by rememberSaveable { mutableIntStateOf(MODE_DAY) }
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
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(mode == MODE_DAY, { mode = MODE_DAY }, label = { Text("Jour", fontSize = 13.sp) })
            FilterChip(mode == MODE_WEEK, { mode = MODE_WEEK }, label = { Text("Semaine", fontSize = 13.sp) })
            Text(week.label, color = AppTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }
        if (mode == MODE_DAY) {
            DayView(days, byDate, today, listState) { selected = it }
        } else {
            WeekGrid(days, byDate, today) { selected = it }
        }
    }
    selected?.let { entry ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AppTheme.colors.surface) {
            LessonDetail(entry)
        }
    }
}

// --- Vue jour ------------------------------------------------------------------------------

@Composable
private fun DayView(
    days: List<LocalDate>,
    byDate: Map<String, List<ScheduleEntry>>,
    today: LocalDate?,
    listState: LazyListState,
    onSelect: (ScheduleEntry) -> Unit,
) {
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
    HorizontalPager(pagerState, Modifier.fillMaxSize()) { index ->
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
            if (entries.isEmpty()) item { EmptyState("Pas de cours", "Rien de prévu ce jour-là.") }
            itemsIndexed(entries) { i, entry ->
                val live = now != null && !entry.canceled && now >= LocalTime.parse(entry.start) && now < LocalTime.parse(entry.end)
                LessonRow(entry, live, Modifier.entrance(i)) { onSelect(entry) }
            }
        }
    }
}

/** L'heure actuelle, rafraîchie chaque minute : sert à repérer le cours en cours. */
@Composable
private fun rememberClock(): LocalTime {
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000)
        }
    }
    return time
}

@Composable
private fun LessonRow(entry: ScheduleEntry, live: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val subject = entry.subject
    val color = AppTheme.colors.subject[subject.colorIndex]
    Row(modifier.padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(48.dp).padding(top = 14.dp)) {
            Text(entry.start, color = AppTheme.colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(entry.end, color = AppTheme.colors.muted, fontSize = 12.sp)
        }
        ListCard(
            Modifier.weight(1f).then(if (live) Modifier.border(2.dp, AppTheme.colors.accent, RoundedCornerShape(15.dp)) else Modifier),
            padded = false,
            onClick = onClick,
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp, 44.dp).background(if (entry.canceled) AppTheme.colors.muted else color, RoundedCornerShape(3.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        subject.name,
                        color = if (entry.canceled) AppTheme.colors.muted else AppTheme.colors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textDecoration = if (entry.canceled) TextDecoration.LineThrough else null,
                    )
                    Text(
                        listOf(subject.room.takeIf { it.isNotBlank() }?.let { "Salle $it" }, subject.teacher.takeIf { it.isNotBlank() })
                            .filterNotNull().joinToString("  ·  "),
                        color = AppTheme.colors.muted,
                        fontSize = 12.sp,
                    )
                    val tags = listOfNotNull("Maintenant".takeIf { live }, "Annulé".takeIf { entry.canceled }, "Évaluation".takeIf { entry.exam })
                    if (tags.isNotEmpty()) {
                        Text(
                            tags.joinToString("  ·  "),
                            color = if (entry.canceled) AppTheme.colors.error else AppTheme.colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// --- Vue semaine ---------------------------------------------------------------------------

private fun minutes(time: String): Int = time.take(2).toInt() * 60 + time.takeLast(2).toInt()

@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    byDate: Map<String, List<ScheduleEntry>>,
    today: LocalDate?,
    onSelect: (ScheduleEntry) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val weeks = remember(days) { days.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toList() }
    val weekDays = weeks[page.coerceIn(0, weeks.size - 1)].second
    val entries = weekDays.flatMap { byDate[it.toString()].orEmpty() }
    val first = (entries.minOfOrNull { minutes(it.start) } ?: 8 * 60) / 60
    val last = maxOf(first + 1, ((entries.maxOfOrNull { minutes(it.end) } ?: 17 * 60) + 59) / 60)
    val hourHeight = 60.dp
    Column(Modifier.fillMaxSize()) {
        if (weeks.size > 1) {
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(page == 0, { page = 0 }, label = { Text("Cette semaine", fontSize = 13.sp) })
                FilterChip(page == 1, { page = 1 }, label = { Text("La suivante", fontSize = 13.sp) })
            }
        }
        Row(Modifier.padding(start = 40.dp, end = 10.dp, top = 6.dp)) {
            weekDays.forEach { day ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() } + " " + day.dayOfMonth,
                    modifier = Modifier.weight(1f),
                    color = if (day == today) AppTheme.colors.accent else AppTheme.colors.muted,
                    fontSize = 12.sp,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 20.dp)) {
            Column(Modifier.width(36.dp)) {
                for (hour in first until last) {
                    Text("${hour}h", color = AppTheme.colors.muted, fontSize = 12.sp, modifier = Modifier.height(hourHeight))
                }
            }
            weekDays.forEach { day ->
                Box(Modifier.weight(1f).height(hourHeight * (last - first)).padding(horizontal = 1.dp)) {
                    for (hour in first until last) {
                        Box(Modifier.offset(y = hourHeight * (hour - first)).fillMaxWidth().height(1.dp).background(AppTheme.colors.muted.copy(alpha = .12f)))
                    }
                    byDate[day.toString()].orEmpty().forEach { entry ->
                        val top: Dp = hourHeight * ((minutes(entry.start) - first * 60) / 60f)
                        val height: Dp = hourHeight * ((minutes(entry.end) - minutes(entry.start)) / 60f)
                        val color = AppTheme.colors.subject[entry.subject.colorIndex]
                        Column(
                            Modifier
                                .offset(y = top)
                                .fillMaxWidth()
                                .height(height - 2.dp)
                                .background(color.copy(alpha = if (entry.canceled) .18f else .32f), RoundedCornerShape(8.dp))
                                .border(1.dp, color.copy(alpha = .8f), RoundedCornerShape(8.dp))
                                .clickable { onSelect(entry) }
                                .padding(4.dp),
                        ) {
                            Text(
                                entry.subject.name,
                                color = AppTheme.colors.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (entry.canceled) TextDecoration.LineThrough else null,
                            )
                            if (height > 48.dp && entry.subject.room.isNotBlank()) {
                                Text(entry.subject.room, color = AppTheme.colors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
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
        if (entry.canceled) Text("Ce cours est annulé.", color = AppTheme.colors.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (entry.exam) Text("Une évaluation est prévue.", color = AppTheme.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (subject.room.isNotBlank()) DetailLine("Salle", subject.room)
        if (subject.teacher.isNotBlank()) DetailLine("Professeur", subject.teacher)
        if (entry.note.isNotBlank()) DetailLine("Note", entry.note)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, color = AppTheme.colors.muted, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
