package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.security.AppLock
import fr.ecole2recte.app.security.findFragmentActivity
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.ui.common.HomeViewModel
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.UiState
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.personalization.ColorEditor
import fr.ecole2recte.app.ui.theme.AppTheme

/** Choix de l'ordre des blocs de l'accueil et de ceux qu'on veut voir. */
@Composable
fun HomeLayoutCard() {
    val settings = rememberAppContainer().settings
    val layout by settings.homeLayout.collectAsStateWithLifecycle()
    ListCard {
        Column(Modifier.padding(vertical = 6.dp)) {
            layout.order.forEachIndexed { index, section ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = section !in layout.hidden,
                        onCheckedChange = { settings.setHomeLayout(layout.toggle(section)) },
                    )
                    Text(section.label, color = AppTheme.colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    IconButton(onClick = { settings.setHomeLayout(layout.move(section, -1)) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Monter ${section.label}")
                    }
                    IconButton(onClick = { settings.setHomeLayout(layout.move(section, +1)) }, enabled = index < layout.order.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Descendre ${section.label}")
                    }
                }
            }
        }
    }
}

/** Ouvre l'éditeur de couleurs des matières du profil affiché. */
@Composable
fun ColorsCard(profile: ActiveProfile?) {
    val repository = rememberSchoolRepository()
    val home = appViewModel { HomeViewModel(repository) }
    val state by home.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    ListCard(onClick = { editing = true }) {
        Column(Modifier.padding(16.dp)) {
            Text("Couleurs des matières", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Choisis la couleur de chaque matière.", color = AppTheme.colors.muted, fontSize = 13.sp)
        }
    }
    if (editing) {
        val subjects = (state as? UiState.Content)?.value?.allSubjects.orEmpty()
        ColorEditor(subjects = subjects, ownerKey = profile?.key.orEmpty(), onDismiss = { editing = false })
    }
}

/** Verrouillage de l'application et animations. */
@Composable
fun SecurityAndMotionCard() {
    val settings = rememberAppContainer().settings
    val context = LocalContext.current
    val lockEnabled by settings.appLock.collectAsStateWithLifecycle()
    val reduceMotion by settings.reduceMotion.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { toggleLock(context, lockEnabled, settings::setAppLock) { message = it } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Verrouiller l'application", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Demande ton empreinte, ton visage ou le code de ton téléphone à l'ouverture.",
                        color = AppTheme.colors.muted,
                        fontSize = 13.sp,
                    )
                }
                Switch(checked = lockEnabled, onCheckedChange = { toggleLock(context, lockEnabled, settings::setAppLock) { text -> message = text } })
            }
            message?.let { Text(it, color = AppTheme.colors.error, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth().clickable { settings.setReduceMotion(!reduceMotion) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Réduire les animations", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Les écrans apparaissent sans mouvement.", color = AppTheme.colors.muted, fontSize = 13.sp)
                }
                Switch(checked = reduceMotion, onCheckedChange = { settings.setReduceMotion(it) })
            }
        }
    }
}

/** Active ou désactive le verrou, après avoir vérifié que l'appareil sait authentifier (sinon on s'enfermerait dehors). */
private fun toggleLock(context: android.content.Context, enabled: Boolean, set: (Boolean) -> Unit, report: (String?) -> Unit) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        report("Le verrouillage n'est pas disponible ici.")
        return
    }
    if (!enabled && !AppLock.isAvailable(context)) {
        report("Configure d'abord un verrouillage d'écran (empreinte, visage ou code) dans les réglages du téléphone.")
        return
    }
    report(null)
    AppLock.authenticate(
        activity = activity,
        title = if (enabled) "Désactiver le verrouillage" else "Activer le verrouillage",
        onSuccess = { set(!enabled) },
        onFailure = { report(it) },
    )
}
