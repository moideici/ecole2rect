package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.SchoolLifeKind
import fr.ecole2recte.app.domain.SchoolLifeSummary
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme

/** Absences, retards, sanctions et encouragements de l'élève affiché, tels que l'établissement les compte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolLifeCard() {
    val repository = rememberSchoolRepository()
    var open by remember { mutableStateOf(false) }
    ListCard(onClick = { open = true }) {
        Column(Modifier.padding(16.dp)) {
            Text("Vie scolaire", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, containerColor = AppTheme.colors.surface) {
            val summary by produceState<Result<SchoolLifeSummary>?>(null) { value = runCatching { repository.schoolLife() } }
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vie scolaire", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                when (val loaded = summary) {
                    null -> CircularProgressIndicator(Modifier.size(24.dp), color = AppTheme.colors.accent)
                    else -> loaded.fold(
                        onSuccess = { SchoolLifeContent(it) },
                        onFailure = { Text("Chargement impossible.", color = AppTheme.colors.error, fontSize = 14.sp) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolLifeContent(summary: SchoolLifeSummary) {
    if (summary.events.isEmpty()) {
        EmptyState("Rien à signaler")
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Counter("Absences", summary.absences, if (summary.unjustified > 0) "${summary.unjustified} non justifiée(s)" else "justifiées", Modifier.weight(1f))
        Counter("Retards", summary.lates, null, Modifier.weight(1f))
        Counter("Sanctions", summary.sanctions, null, Modifier.weight(1f))
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
        summary.events.forEach { event ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.title, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (event.kind == SchoolLifeKind.Absence) {
                        Text(
                            if (event.justified) "Justifiée" else "À justifier",
                            color = if (event.justified) AppTheme.colors.positive else AppTheme.colors.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(event.dateLabel, color = AppTheme.colors.muted, fontSize = 13.sp)
                if (event.detail.isNotBlank()) Text(event.detail, color = AppTheme.colors.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, note: String?, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(value.toString(), color = AppTheme.colors.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppTheme.colors.muted, fontSize = 13.sp)
        note?.let { Text(it, color = AppTheme.colors.muted, fontSize = 12.sp) }
    }
}
