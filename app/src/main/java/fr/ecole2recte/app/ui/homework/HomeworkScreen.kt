package fr.ecole2recte.app.ui.homework

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.HomeworkViewModel
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme

/** Travail à faire, affiché dans l'onglet Agenda. */
@Composable
fun HomeworkList(listState: LazyListState) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { HomeworkViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::refresh) { tasks ->
        if (tasks.isEmpty()) {
            EmptyState("Rien à faire", "Aucun devoir à venir pour le moment.")
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)) {
                item {
                    Text("${tasks.size} devoirs à venir", color = AppTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                }
                itemsIndexed(tasks) { index, task ->
                    ListCard(Modifier.entrance(index)) {
                        Column(Modifier.padding(17.dp)) {
                            Text(task.subject, color = AppTheme.colors.subject[task.colorIndex], fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(task.title, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Pour ${task.due}", color = AppTheme.colors.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
