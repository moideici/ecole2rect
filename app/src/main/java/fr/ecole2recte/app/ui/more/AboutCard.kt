package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.login.LegalNotice
import fr.ecole2recte.app.ui.theme.AppTheme

/** « À propos » : l'avis du premier lancement et les limites connues, derrière une ligne ; la présentation à revoir. */
@Composable
fun AboutCard() {
    var open by remember { mutableStateOf(false) }
    val container = rememberAppContainer()
    ListCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            AboutRow("Avis et limites") { open = true }
            AboutRow("Revoir la présentation") { container.replayOnboarding.value = true }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(LegalNotice.TITLE) },
            text = {
                Column {
                    LegalNotice.points.forEach { Text(it, color = AppTheme.colors.ink, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp)) }
                    Text("Limites", color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    LegalNotice.limits.forEach { Text("• $it", color = AppTheme.colors.muted, fontSize = 13.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun AboutRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AppTheme.colors.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
