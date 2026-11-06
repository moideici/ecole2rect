package fr.ecole2recte.app.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Icon
import fr.ecole2recte.app.domain.Highlight
import fr.ecole2recte.app.domain.HighlightKind
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.ChildSummary
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.ui.accounts.Avatar
import fr.ecole2recte.app.widget.WidgetLogic
import fr.ecole2recte.app.widget.WidgetUpdater
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.NextClass
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.ui.common.HomeViewModel
import fr.ecole2recte.app.ui.common.PageHeader
import fr.ecole2recte.app.ui.schedule.DayTimelineView
import fr.ecole2recte.app.ui.schedule.rememberClock
import fr.ecole2recte.app.ui.common.ProgressRing
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.search.SearchEntry
import fr.ecole2recte.app.ui.common.rememberAnimatedDecimal
import fr.ecole2recte.app.ui.common.ScrollToTopOnReselect
import fr.ecole2recte.app.ui.common.SectionTitle
import fr.ecole2recte.app.ui.common.RefreshOnResume
import fr.ecole2recte.app.data.SchoolModule
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.SubjectChip
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.settings.HomeLayout
import fr.ecole2recte.app.settings.HomeSection
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.subjectColor

@Composable
fun HomeRoute(
    onOpenSchedule: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenMessages: () -> Unit,
    onSelectChild: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { HomeViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layout by rememberAppContainer().settings.homeLayout.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel)
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh) { data ->
        HomeScreen(
            data = data,
            layout = layout,
            onOpenSchedule = onOpenSchedule,
            onOpenHomework = onOpenHomework,
            onOpenGrades = onOpenGrades,
            onOpenMessages = onOpenMessages,
            onSelectChild = onSelectChild,
            onOpenSearch = onOpenSearch,
        )
        PublishWidgets(data, repository)
    }
}

/** Met à jour les widgets à chaque ouverture : ils marchent sans activer les notifications. */
@Composable
private fun PublishWidgets(data: HomeData, repository: fr.ecole2recte.app.domain.SchoolRepository) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(data) {
        // Tout est déjà en mémoire : ces appels ne coûtent pas de nouvelle requête.
        val week = runCatching { repository.schedule() }.getOrNull()
        val homework = runCatching { repository.homework() }.getOrDefault(emptyList())
        val inbox = runCatching { repository.messagesPage(0).items }.getOrDefault(emptyList())
        WidgetUpdater(context).publish(
            WidgetLogic.snapshot(System.currentTimeMillis(), java.time.LocalDate.now(), data, week, homework, data.grades, inbox),
        )
    }
}

/** L'accueil : l'en-tête, puis les blocs que l'utilisateur a choisis, dans l'ordre qu'il a choisi. */
@Composable
private fun HomeScreen(
    data: HomeData,
    layout: HomeLayout,
    onOpenSchedule: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenMessages: () -> Unit,
    onSelectChild: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect("accueil", listState)
    val closed = LocalActiveProfile.current?.closedModules.orEmpty()
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader(data.greeting, data.dateLabel) }
        item(key = "search") { SearchEntry(onOpenSearch) }
        layout.visible.forEachIndexed { rank, section ->
            when (section) {
                HomeSection.Family -> if (data.family.isNotEmpty()) {
                    item(key = "family-title") { SectionTitle("Ma famille", "", onClick = {}) }
                    item(key = "family") { FamilyCard(data.family, Modifier.entrance(rank), onSelectChild) }
                }
                HomeSection.NextClass -> item(key = "next") {
                    if (data.nextClass != null) NextClassCard(data.nextClass, Modifier.entrance(rank).clickable(onClick = onOpenSchedule))
                    else NoClassCard(Modifier.entrance(rank))
                }
                HomeSection.Today -> {
                    item(key = "today-title") { SectionTitle("Aujourd'hui", "Voir le planning", onClick = onOpenSchedule) }
                    item(key = "today") {
                        if (data.today.isEmpty()) {
                            Text("Pas de cours aujourd'hui.", color = AppTheme.colors.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 22.dp))
                        } else {
                            TodayCard(data.today, Modifier.entrance(rank), onOpenSchedule)
                        }
                    }
                }
                HomeSection.Highlights -> if (data.highlights.isNotEmpty()) {
                    item(key = "highlights-title") { SectionTitle("À savoir", "", onClick = {}) }
                    item(key = "highlights") {
                        Column {
                            data.highlights.forEachIndexed { index, highlight ->
                                HighlightCard(
                                    highlight,
                                    Modifier.entrance(rank + index),
                                    onClick = when (highlight.kind) {
                                        HighlightKind.Canceled, HighlightKind.Changed, HighlightKind.Exam -> onOpenSchedule
                                        HighlightKind.Grade -> onOpenGrades
                                        HighlightKind.Message -> onOpenMessages
                                    },
                                )
                            }
                        }
                    }
                }
                HomeSection.Reminder -> data.reminder?.takeIf { SchoolModule.Homework !in closed }?.let { reminder ->
                    item(key = "reminder-title") { SectionTitle("À ne pas oublier", "Tout voir", onClick = onOpenHomework) }
                    item(key = "reminder") { ReminderCard(reminder, Modifier.entrance(rank)) }
                }
                HomeSection.Grades -> if (SchoolModule.Grades !in closed) {
                    item(key = "grades-title") { SectionTitle("Tes dernières notes", "Voir les notes", onClick = onOpenGrades) }
                    item(key = "grades") { GradesSummary(data.grades, Modifier.entrance(rank)) }
                }
            }
        }
    }
}

/** Une ligne par enfant : prochain cours, devoirs urgents, nouvelles notes, moyenne. On touche pour changer d'enfant. */
@Composable
private fun FamilyCard(children: List<ChildSummary>, modifier: Modifier, onSelect: (String) -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(AppTheme.colors.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            children.forEach { child ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(child.studentId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(child.initials, active = child.selected, size = 44)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            child.name + if (child.className.isNotBlank()) " · ${child.className}" else "",
                            color = AppTheme.colors.ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(child.nextClass?.let { "Ensuite : $it" } ?: "Plus de cours aujourd'hui", color = AppTheme.colors.muted, fontSize = 12.sp, maxLines = 1)
                        val tags = listOfNotNull(
                            child.urgentHomework.takeIf { it > 0 }?.let { if (it == 1) "1 devoir pour demain" else "$it devoirs pour demain" },
                            child.newGrades.takeIf { it > 0 }?.let { if (it == 1) "1 nouvelle note" else "$it nouvelles notes" },
                        )
                        if (tags.isNotEmpty()) {
                            Text(tags.joinToString(" · "), color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    child.average?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(it, color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("moyenne", color = AppTheme.colors.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(highlight: Highlight, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (icon, tint) = when (highlight.kind) {
        HighlightKind.Canceled -> Icons.Outlined.EventBusy to AppTheme.colors.error
        HighlightKind.Changed -> Icons.Outlined.EventRepeat to AppTheme.colors.error
        HighlightKind.Exam -> Icons.Outlined.EditNote to AppTheme.colors.accent
        HighlightKind.Message -> Icons.Outlined.MarkEmailUnread to AppTheme.colors.accent
        HighlightKind.Grade -> Icons.AutoMirrored.Outlined.TrendingUp to AppTheme.colors.positive
    }
    Card(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(AppTheme.colors.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(highlight.title, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (highlight.detail.isNotBlank()) {
                    Text(highlight.detail, color = AppTheme.colors.muted, fontSize = 12.sp, maxLines = 2)
                }
            }
            // Une note : le chiffre en grand à droite, son coefficient en petit dessous.
            highlight.value?.let { value ->
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                    Text(value, color = AppTheme.colors.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    highlight.valueSub?.let { Text(it, color = AppTheme.colors.muted, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun NoClassCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("PROCHAIN COURS", color = AppTheme.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Plus de cours à venir", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Profite de ta soirée.", color = AppTheme.colors.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun NextClassCard(next: NextClass, modifier: Modifier = Modifier) {
    val subject = next.subject
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.accentFill),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("PROCHAIN COURS", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(25.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(next.startLabel, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Text(subject.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(subject.room.takeIf { it.isNotBlank() }?.let { "Salle $it" }, subject.teacher.takeIf { it.isNotBlank() })
                            .filterNotNull().joinToString("  ·  "),
                        color = Color.White.copy(alpha = .7f),
                        fontSize = 12.sp,
                    )
                    next.afterLabel?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Color.White.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                ProgressRing(next.progress, size = 68.dp) { SubjectChip(subject) }
            }
        }
    }
}

/** La journée entière sur la même frise que l'agenda : pauses, doubles heures, cours en cours. */
@Composable
private fun TodayCard(entries: List<ScheduleEntry>, modifier: Modifier, onOpen: () -> Unit) {
    val now = rememberClock()
    Card(
        modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(AppTheme.colors.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            DayTimelineView(entries, now = now, compact = true, onSelect = { onOpen() })
        }
    }
}

@Composable
private fun ReminderCard(task: Homework, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(AppTheme.colors.inverseSurface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(subjectColor(task.subject, task.colorIndex).copy(alpha = .2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = subjectColor(task.subject, task.colorIndex), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 13.dp)) {
                Text("À NE PAS OUBLIER", color = Color.White.copy(alpha = .6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(task.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("À rendre ${task.due}", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GradesSummary(grades: GradeOverview, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(AppTheme.colors.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rememberAnimatedDecimal(grades.average), color = AppTheme.colors.ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(" / 20\nMoyenne générale", color = AppTheme.colors.muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (grades.trend.isNotBlank()) {
                Text(grades.trend, color = AppTheme.colors.positive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
