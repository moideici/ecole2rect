package fr.ecole2recte.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import fr.ecole2recte.app.ui.common.PageScaffold
import fr.ecole2recte.app.ui.common.ScheduleViewModel
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.CardWhite
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.Muted
import fr.ecole2recte.app.ui.theme.SubjectColors

@Composable
fun ScheduleRoute() {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { ScheduleViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::refresh) { week ->
        PageScaffold("Emploi du temps", week.label) {
            week.entries.forEach { ScheduleRow(it) }
        }
    }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry) {
    val subject = entry.subject
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(CardWhite),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp, 42.dp).background(SubjectColors[subject.colorIndex], RoundedCornerShape(4.dp)))
            Column(Modifier.padding(start = 13.dp)) {
                Text(subject.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${entry.whenLabel}  ·  Salle ${subject.room}", color = Muted, fontSize = 11.sp)
                Text(subject.teacher, color = Muted, fontSize = 11.sp)
            }
        }
    }
}
