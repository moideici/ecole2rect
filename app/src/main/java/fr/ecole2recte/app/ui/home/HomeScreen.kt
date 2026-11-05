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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.NextClass
import fr.ecole2recte.app.domain.ScheduleSlot
import fr.ecole2recte.app.ui.common.HomeViewModel
import fr.ecole2recte.app.ui.common.PageHeader
import fr.ecole2recte.app.ui.common.SectionTitle
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.SubjectChip
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.personalization.ColorEditor
import fr.ecole2recte.app.ui.theme.CardWhite
import fr.ecole2recte.app.ui.theme.Indigo
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.Muted
import fr.ecole2recte.app.ui.theme.Positive
import fr.ecole2recte.app.ui.theme.ReminderSurface
import fr.ecole2recte.app.ui.theme.SubjectColors

@Composable
fun HomeRoute(onOpenSchedule: () -> Unit, onOpenHomework: () -> Unit, onOpenGrades: () -> Unit) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { HomeViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showColorEditor by rememberSaveable { mutableStateOf(false) }
    StateHost(state, onRetry = viewModel::refresh) { data ->
        HomeScreen(
            data = data,
            onOpenColors = { showColorEditor = true },
            onOpenSchedule = onOpenSchedule,
            onOpenHomework = onOpenHomework,
            onOpenGrades = onOpenGrades,
        )
        if (showColorEditor) {
            ColorEditor(subjects = data.allSubjects, onDismiss = { showColorEditor = false })
        }
    }
}

@Composable
private fun HomeScreen(
    data: HomeData,
    onOpenColors: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenGrades: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column {
                PageHeader(data.greeting, data.dateLabel)
                NextClassCard(data.nextClass)
            }
        }
        item { SectionTitle("Aujourd'hui", "Voir le planning", onClick = onOpenSchedule) }
        item { ScheduleSummary(data.today) }
        data.reminder?.let { reminder ->
            item { SectionTitle("À ne pas oublier", "Tout voir", onClick = onOpenHomework) }
            item { ReminderCard(reminder) }
        }
        item { SectionTitle("Tes dernières notes", "Voir les notes", onClick = onOpenGrades) }
        item { GradesSummary(data.grades) }
        item {
            Text(
                "Couleurs par matière",
                modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp).clickable(onClick = onOpenColors),
                color = Indigo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NextClassCard(next: NextClass) {
    val subject = next.subject
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Indigo),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("PROCHAIN COURS", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(25.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(next.startLabel, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Text(subject.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Salle ${subject.room}  ·  ${subject.teacher}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
                SubjectChip(subject)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .25f), RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(next.progress).height(4.dp).background(Color.White, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun ScheduleSummary(slots: List<ScheduleSlot>) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(CardWhite),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            slots.forEach { slot ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.time, color = Muted, fontSize = 11.sp, modifier = Modifier.size(45.dp, 20.dp))
                    Box(Modifier.size(8.dp).background(SubjectColors[slot.subject.colorIndex], RoundedCornerShape(4.dp)))
                    Text(slot.subject.name, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    Text(slot.subject.room, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(task: Homework) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(ReminderSurface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(SubjectColors[task.colorIndex].copy(alpha = .2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = SubjectColors[task.colorIndex], fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 13.dp)) {
                Text("À NE PAS OUBLIER", color = Color.White.copy(alpha = .6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(task.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("À rendre ${task.due}", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GradesSummary(grades: GradeOverview) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(CardWhite),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(grades.average, color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(" / 20\nMoyenne générale", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(grades.trend, color = Positive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
