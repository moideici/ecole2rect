package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import fr.ecole2recte.app.ui.common.GradesViewModel
import fr.ecole2recte.app.ui.common.PageScaffold
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.CardWhite
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.SubjectColors

@Composable
fun GradesRoute() {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { GradesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::refresh) { grades ->
        PageScaffold("Notes", "Moyenne générale · ${grades.average} / 20") {
            grades.bySubject.forEach { line ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(CardWhite),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(SubjectColors[line.subject.colorIndex], RoundedCornerShape(5.dp)))
                        Text(line.subject.name, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                        Spacer(Modifier.weight(1f))
                        Text(line.average, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
