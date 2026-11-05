package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.login.LegalNotice
import fr.ecole2recte.app.ui.theme.AppTheme

/** « À propos » : ce que l'écran du premier lancement disait, plus les limites connues. */
@Composable
fun AboutCard() {
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegalNotice.points.forEach { Text(it, color = AppTheme.colors.ink, fontSize = 14.sp, lineHeight = 20.sp) }
            Text("Limites connues", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            LegalNotice.limits.forEach { Text("• $it", color = AppTheme.colors.muted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
    }
}
