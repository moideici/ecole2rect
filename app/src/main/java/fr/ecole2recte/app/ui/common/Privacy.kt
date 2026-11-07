package fr.ecole2recte.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.PrivacyMode
import fr.ecole2recte.app.ui.theme.AppTheme

/** Le mode « montrer à quelqu'un » en cours : lu par tous les écrans qui affichent des notes ou des messages. */
val LocalPrivacy = staticCompositionLocalOf { PrivacyMode() }

/** Interrupteur rapide (œil) de l'en-tête : masque ou montre les notes (et les messages si l'option est cochée). */
@Composable
fun PrivacyToggle(modifier: Modifier = Modifier) {
    val settings = rememberAppContainer().settings
    val enabled by settings.privacyMode.collectAsStateWithLifecycle()
    val label = if (enabled) "Mode « montrer » activé : afficher les notes" else "Masquer les notes pour les montrer à quelqu'un"
    Row(
        modifier.size(40.dp)
            .background(if (enabled) AppTheme.colors.accentFill else AppTheme.colors.surface, CircleShape)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = settings::setPrivacyMode)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (enabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = null,
            tint = if (enabled) androidx.compose.ui.graphics.Color.White else AppTheme.colors.muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Plus > Sécurité : le mode « montrer à quelqu'un » et son option sur les messages. */
@Composable
fun PrivacyCard() {
    val settings = rememberAppContainer().settings
    val enabled by settings.privacyMode.collectAsStateWithLifecycle()
    val hideMessages by settings.privacyHideMessages.collectAsStateWithLifecycle()
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().clickable { settings.setPrivacyMode(!enabled) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mode « montrer »", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Cache les notes. Pas les widgets ni les notifications.", color = AppTheme.colors.muted, fontSize = 13.sp)
                }
                Switch(checked = enabled, onCheckedChange = settings::setPrivacyMode)
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled) { settings.setPrivacyHideMessages(!hideMessages) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cacher les messages", color = if (enabled) AppTheme.colors.ink else AppTheme.colors.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Switch(checked = hideMessages, enabled = enabled, onCheckedChange = settings::setPrivacyHideMessages)
            }
        }
    }
}
