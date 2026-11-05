package fr.ecole2recte.app.ui.homework

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.ui.common.HomeworkViewModel
import fr.ecole2recte.app.ui.common.PageScaffold
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.theme.CardWhite
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.Muted
import fr.ecole2recte.app.ui.theme.SubjectColors

@Composable
fun HomeworkRoute() {
    val viewModel = appViewModel { HomeworkViewModel(it.schoolRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::refresh) { tasks ->
        PageScaffold("Devoirs", "${tasks.size} devoirs à venir") {
            tasks.forEach { task ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(CardWhite),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Text(task.subject, color = SubjectColors[task.colorIndex], fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(task.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Pour ${task.due}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
