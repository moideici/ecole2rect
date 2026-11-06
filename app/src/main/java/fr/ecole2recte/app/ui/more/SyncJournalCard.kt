package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.sync.SyncResultCode
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * « Journal des synchronisations » : les trente derniers passages de la vérification en arrière-plan,
 * avec l'heure, la durée, le résultat et le nombre de nouveautés. Aucune donnée personnelle n'y figure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncJournalCard() {
    val journal = rememberAppContainer().syncJournal
    var open by remember { mutableStateOf(false) }
    ListCard(onClick = { open = true }) {
        Column(Modifier.padding(16.dp)) {
            Text("Journal des synchronisations", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Les 30 derniers passages en arrière-plan : heure, durée, résultat. Aucune donnée personnelle.",
                color = AppTheme.colors.muted,
                fontSize = 13.sp,
            )
        }
    }
    if (open) {
        var entries by remember { mutableStateOf(journal.entries()) }
        ModalBottomSheet(onDismissRequest = { open = false }, containerColor = AppTheme.colors.surface) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Journal des synchronisations", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (entries.isEmpty()) {
                    Text(
                        "Aucun passage pour l'instant. La synchronisation en arrière-plan se règle dans Notifications.",
                        color = AppTheme.colors.muted,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        "Du plus récent au plus ancien.",
                        color = AppTheme.colors.muted,
                        fontSize = 13.sp,
                    )
                    Text(
                        "Effacer le journal",
                        color = AppTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            journal.clear()
                            entries = journal.entries()
                        }.padding(vertical = 8.dp),
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries) { entry ->
                            val good = entry.result == SyncResultCode.Done
                            Text(
                                entry.describe(),
                                color = if (good) AppTheme.colors.ink else AppTheme.colors.error,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
