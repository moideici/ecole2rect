package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.ui.theme.AppTheme

/** Écran affiché tant que l'application est verrouillée : aucune donnée scolaire n'est montrée derrière. */
@Composable
fun LockScreen(message: String?, onUnlock: () -> Unit) {
    // Le verrou se présente tout seul à l'ouverture ; le bouton sert quand on l'a fermé.
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(48.dp))
        Text("ecole2recte est verrouillé", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            message ?: "Déverrouille l'application pour voir tes cours, tes notes et tes messages.",
            color = AppTheme.colors.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Text(
            "Déverrouiller",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .background(AppTheme.colors.accentFill, RoundedCornerShape(12.dp))
                .clickable(onClick = onUnlock)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
