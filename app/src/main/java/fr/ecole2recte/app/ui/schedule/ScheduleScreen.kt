package fr.ecole2recte.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.ScheduleViewModel
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme

/** Emploi du temps de la semaine, affiché dans l'onglet Agenda. */
@Composable
fun ScheduleList(listState: LazyListState) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { ScheduleViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::refresh) { week ->
        LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)) {
            item {
                Text(week.label, color = AppTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            }
            if (week.entries.isEmpty()) item { EmptyState("Pas de cours", "Aucun cours n'est prévu cette semaine.") }
            itemsIndexed(week.entries) { index, entry -> ScheduleRow(entry, Modifier.entrance(index)) }
        }
    }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry, modifier: Modifier = Modifier) {
    val subject = entry.subject
    ListCard(modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp, 42.dp).background(AppTheme.colors.subject[subject.colorIndex], RoundedCornerShape(4.dp)))
            Column(Modifier.padding(start = 13.dp)) {
                Text(subject.name, color = AppTheme.colors.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    buildString {
                        append(entry.whenLabel)
                        if (subject.room.isNotBlank()) append("  ·  Salle ${subject.room}")
                        if (entry.canceled) append("  ·  Annulé")
                    },
                    color = if (entry.canceled) AppTheme.colors.error else AppTheme.colors.muted,
                    fontSize = 12.sp,
                )
                Text(subject.teacher, color = AppTheme.colors.muted, fontSize = 12.sp)
            }
        }
    }
}
