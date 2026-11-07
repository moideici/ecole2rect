package fr.ecole2recte.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.ui.theme.AppTheme

/** Le premier lancement : ce que fait (et ne fait pas) l'application, à accepter une fois avant la saisie. */
@Composable
fun NoticeScreen(onAccept: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = AppTheme.colors.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp)
                .semantics { contentDescription = "Informations avant la connexion" },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(LegalNotice.TITLE, color = AppTheme.colors.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            LegalNotice.points.forEach { point ->
                Text(point, color = AppTheme.colors.ink, fontSize = 16.sp, lineHeight = 23.sp)
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(LegalNotice.ACCEPT) }
        }
    }
}
