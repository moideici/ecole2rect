package fr.ecole2recte.app.ui.homework

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.CalendarExport
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.HomeworkDetail
import fr.ecole2recte.app.ui.common.AddToCalendarRow
import fr.ecole2recte.app.ui.common.AttachmentList
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.HomeworkViewModel
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.RefreshOnResume
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.subjectIcon
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.linkified
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.LocalDate
import fr.ecole2recte.app.ui.theme.subjectColor

/** Travail à faire, regroupé par échéance, filtrable par matière, avec cases à cocher. */
@Composable
fun HomeworkList(listState: LazyListState) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { HomeworkViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel)
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh) { tasks ->
        HomeworkContent(tasks, listState, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkContent(tasks: List<Homework>, listState: LazyListState, viewModel: HomeworkViewModel) {
    var subject by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<Homework?>(null) }
    val today = remember { LocalDate.now() }
    val container = rememberAppContainer()
    val ownerKey = LocalActiveProfile.current?.key.orEmpty()
    var filter by remember(ownerKey) { mutableStateOf(HomeworkFilter.parse(container.homeworkFilter.get(ownerKey))) }
    val counts = remember(tasks) { countByFilter(tasks) }
    val subjects = remember(tasks) { tasks.map { it.subject }.distinct().sorted() }
    val groups = remember(tasks, subject, filter) { groupHomework(filterHomework(tasks, filter), today, subject) }
    if (tasks.isEmpty()) {
        EmptyState("Rien à faire", "Aucun devoir à venir pour le moment.")
        return
    }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        notice?.let { message ->
            item(key = "notice") {
                Text(
                    "$message  Fermer",
                    color = AppTheme.colors.warningTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                        .background(AppTheme.colors.warningSurface, RoundedCornerShape(12.dp))
                        .clickable(onClick = viewModel::dismissNotice)
                        .padding(12.dp),
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HomeworkFilter.entries) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = {
                            filter = option
                            container.homeworkFilter.set(ownerKey, option.name)
                        },
                        label = { Text("${option.label} (${counts[option] ?: 0})", fontSize = 13.sp) },
                    )
                }
            }
        }
        if (subjects.size > 1) {
            item {
                LazyRow(modifier = Modifier.padding(top = 4.dp), contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(subject == null, { subject = null }, label = { Text("Toutes", fontSize = 13.sp) }) }
                    items(subjects) { name ->
                        FilterChip(subject == name, { subject = if (subject == name) null else name }, label = { Text(name, fontSize = 13.sp) })
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            item {
                if (subject != null) EmptyState("Rien dans cette matière", "Aucun devoir ne correspond ici.")
                else EmptyState(filter.emptyTitle, filter.emptyText)
            }
        }
        var rank = 0
        groups.forEach { group ->
            item(key = "title-${group.title}") {
                Text(
                    group.title,
                    color = AppTheme.colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            items(group.items, key = { "${it.dueDate}-${it.id}" }) { task ->
                HomeworkCard(task, Modifier.entrance(rank++), onToggle = { viewModel.toggleDone(task) }) { opened = task }
            }
        }
    }
    opened?.let { task ->
        ModalBottomSheet(onDismissRequest = { opened = null }, containerColor = AppTheme.colors.surface) {
            HomeworkSheet(task, viewModel) {
                viewModel.toggleDone(task)
                opened = null
            }
        }
    }
}

@Composable
private fun HomeworkCard(task: Homework, modifier: Modifier, onToggle: () -> Unit, onOpen: () -> Unit) {
    val tint by animateColorAsState(
        if (task.done) AppTheme.colors.positive else AppTheme.colors.muted,
        label = "done",
    )
    ListCard(modifier, onClick = onOpen) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (task.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (task.done) "Remettre à faire" else "Marquer comme fait",
                tint = tint,
                modifier = Modifier
                    .clickable(role = Role.Checkbox, onClick = onToggle)
                    .padding(10.dp)
                    .size(26.dp),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(subjectIcon(task.subject), contentDescription = null, tint = subjectColor(task.subject, task.colorIndex), modifier = Modifier.size(14.dp))
                    Text(task.subject, color = subjectColor(task.subject, task.colorIndex), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp))
                }
                Text(
                    task.title,
                    color = if (task.done) AppTheme.colors.muted else AppTheme.colors.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                Text(
                    if (task.exam) "Pour ${task.due} · Évaluation" else "Pour ${task.due}",
                    color = if (task.exam && !task.done) AppTheme.colors.accent else AppTheme.colors.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun HomeworkSheet(task: Homework, viewModel: HomeworkViewModel, onToggle: () -> Unit) {
    var detail by remember(task.id) { mutableStateOf<Result<HomeworkDetail>?>(null) }
    LaunchedEffect(task.id) { detail = runCatching { viewModel.detail(task) } }
    Column(
        Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp).semantics { contentDescription = "Détail du devoir" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(task.subject, color = subjectColor(task.subject, task.colorIndex), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Pour ${task.due}", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (task.exam) Text("Évaluation prévue", color = AppTheme.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        when (val loaded = detail) {
            null -> CircularProgressIndicator(color = AppTheme.colors.accent, modifier = Modifier.size(24.dp))
            else -> loaded.fold(
                onSuccess = { content ->
                    SelectionContainer {
                        Text(
                            linkified(content.content.ifBlank { "Aucun énoncé détaillé pour ce devoir." }),
                            color = AppTheme.colors.ink,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    AttachmentList(content.files, viewModel::download)
                    if (content.lessonContent.isNotBlank()) {
                        Text("Vu en cours", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        SelectionContainer {
                            Text(linkified(content.lessonContent), color = AppTheme.colors.muted, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                },
                onFailure = {
                    Text("Impossible de charger l'énoncé pour le moment.", color = AppTheme.colors.error, fontSize = 14.sp)
                },
            )
        }
        AddToCalendarRow(remember(task) { CalendarExport.homework(task) })
        Text(
            if (task.done) "Remettre à faire" else "Marquer comme fait",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp),
            color = AppTheme.colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
