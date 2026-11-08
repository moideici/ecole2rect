package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.domain.MASKED
import fr.ecole2recte.app.domain.SchoolLifeEvent
import fr.ecole2recte.app.domain.SchoolLifeKind
import fr.ecole2recte.app.domain.SchoolLifeLogic
import fr.ecole2recte.app.domain.SchoolLifeSummary
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.LocalPrivacy
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.widget.SchoolLifeSnapshotStore
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SchoolLifeCard(onOpen: () -> Unit) {
    ListCard(onClick = onOpen) {
        Text("Vie scolaire", Modifier.fillMaxWidth().padding(16.dp), color = AppTheme.colors.ink, fontWeight = FontWeight.Bold)
    }
}

/** Écran en lecture seule : aucun appel d'écriture, aucune justification en ligne. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolLifeScreen(onClose: () -> Unit) {
    val repository = rememberSchoolRepository()
    val profile = LocalActiveProfile.current
    val context = LocalContext.current.applicationContext
    val closed = fr.ecole2recte.app.data.SchoolModule.SchoolLife in profile?.closedModules.orEmpty()
    val summary by produceState<Result<SchoolLifeSummary>?>(null, repository, closed) {
        if (!closed) {
            value = runCatching { repository.schoolLife() }
            value?.getOrNull()?.let { SchoolLifeSnapshotStore(context).publish(profile?.key.orEmpty(), it.events) }
        }
    }
    var selectedKind by remember { mutableStateOf<SchoolLifeKind?>(null) }
    var selectedMonth by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<SchoolLifeEvent?>(null) }
    val hidden = LocalPrivacy.current.enabled
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = SchoolLifeLogic.columns(maxWidth.value, maxHeight.value, maxWidth > maxHeight, LocalDensity.current.fontScale)
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick = onClose) { Text("Retour") }
                Text("Vie scolaire", style = MaterialTheme.typography.headlineMedium, color = AppTheme.colors.ink)
                Text("Lecture seule · Durées fournies par l’établissement", color = AppTheme.colors.muted)
            }
            when {
                closed -> item { Text("Module fermé par l’établissement.") }
                hidden -> item { Text(MASKED, color = AppTheme.colors.ink) }
                summary == null -> item { CircularProgressIndicator() }
                summary?.isFailure == true -> item { Text("Chargement impossible. Réouvre cet écran pour réessayer.", color = AppTheme.colors.error) }
                else -> {
                    val events = summary?.getOrNull()?.events.orEmpty()
                    val minutes = SchoolLifeLogic.unjustifiedMinutes(events)
                    val counters = listOf(
                        "${events.count { it.kind == SchoolLifeKind.Absence && !it.justified }} absence(s) non justifiée(s)",
                        "${minutes / 60} h ${minutes % 60} min non justifiées connues" + SchoolLifeLogic.unknownDurations(events).takeIf { it > 0 }?.let { " · $it durée(s) non fournie(s)" }.orEmpty(),
                        "${events.count { it.kind == SchoolLifeKind.Late }} retard(s)",
                        "${events.count { it.kind == SchoolLifeKind.Sanction }} sanction(s)",
                        "${events.count { it.kind == SchoolLifeKind.Encouragement }} encouragement(s)",
                    )
                    counters.chunked(columns).forEach { group ->
                        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { text -> ListCard(Modifier.weight(1f)) { Text(text, Modifier.padding(12.dp), color = AppTheme.colors.ink) } }
                        } }
                    }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selectedKind == null, { selectedKind = null }, { Text("Tout") }) }
                        SchoolLifeKind.entries.forEach { kind -> item { FilterChip(selectedKind == kind, { selectedKind = kind }, { Text(kind.label()) }) } }
                    } }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selectedMonth == null, { selectedMonth = null }, { Text("Tous les mois") }) }
                        SchoolLifeLogic.months(events).keys.forEach { month -> item {
                            FilterChip(selectedMonth == month, { selectedMonth = month }, { Text(monthLabel(month)) })
                        } }
                    } }
                    val filtered = SchoolLifeLogic.filter(events, selectedKind, selectedMonth)
                    if (filtered.isEmpty()) item { Text("Rien à signaler", color = AppTheme.colors.muted) }
                    SchoolLifeLogic.months(filtered).forEach { (month, monthEvents) ->
                        item { Text(monthLabel(month), fontWeight = FontWeight.Bold, color = AppTheme.colors.accent) }
                        monthEvents.forEach { event -> item {
                            ListCard(onClick = { selectedEvent = event }) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(event.title, color = AppTheme.colors.ink, fontWeight = FontWeight.Bold)
                                    Text(event.dateLabel, color = AppTheme.colors.muted)
                                    if (event.kind == SchoolLifeKind.Absence) Text(if (event.justified) "Justifiée" else "À justifier", color = AppTheme.colors.ink)
                                }
                            }
                        } }
                    }
                }
            }
        }
    }
    selectedEvent?.takeUnless { hidden }?.let { event ->
        AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text(event.title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(event.dateLabel)
                Text(event.detail.ifBlank { "Motif non fourni" })
                Text("Durée comptée par l’établissement : ${event.duration.ifBlank { "non fournie" }}")
                if (event.kind == SchoolLifeKind.Absence) Text(if (event.justified) "Justifiée" else "Non justifiée")
            }
        }, confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("Fermer") } })
    }
}

private fun SchoolLifeKind.label() = when (this) {
    SchoolLifeKind.Absence -> "Absences"
    SchoolLifeKind.Late -> "Retards"
    SchoolLifeKind.Sanction -> "Sanctions"
    SchoolLifeKind.Encouragement -> "Encouragements"
    SchoolLifeKind.Other -> "Autres"
}

private fun monthLabel(month: String): String = runCatching {
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE))
}.getOrDefault(month)
