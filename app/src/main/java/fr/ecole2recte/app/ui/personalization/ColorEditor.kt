package fr.ecole2recte.app.ui.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.theme.AppTheme

/** Maquette de l'éditeur de couleurs : « Modifier » ne fait encore rien (lot 14). */
@Composable
fun ColorEditor(subjects: List<Subject>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(AppTheme.colors.surface)) {
            Column(Modifier.padding(24.dp)) {
                Text("Couleurs par matière", color = AppTheme.colors.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Une couleur reste identique dans tout l'espace.",
                    color = AppTheme.colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                subjects.forEach { subject ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(12.dp).background(AppTheme.colors.subject[subject.colorIndex], RoundedCornerShape(6.dp)),
                        )
                        Text(subject.name, color = AppTheme.colors.ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                        Spacer(Modifier.weight(1f))
                        Text("Modifier", color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Fermer",
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End).padding(top = 7.dp).clickable(onClick = onDismiss).padding(8.dp),
                )
            }
        }
    }
}
