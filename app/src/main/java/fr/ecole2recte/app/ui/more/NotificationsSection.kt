package fr.ecole2recte.app.ui.more

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.sync.SyncScheduler
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Carte « Notifications et widgets » de l'onglet Plus. Désactivé tant que l'utilisateur ne l'a pas demandé. */
@Composable
fun NotificationsSection() {
    val context = LocalContext.current
    val prefs = rememberAppContainer().syncPreferences
    val enabled by prefs.enabled.collectAsStateWithLifecycle()
    val lastSync by prefs.lastSyncMillis.collectAsStateWithLifecycle()
    val reason by prefs.disabledReason.collectAsStateWithLifecycle()
    var blocked by remember { mutableStateOf(false) }

    fun enable() {
        prefs.setEnabled(true)
        AndroidNotifier(context).ensureChannels()
        SyncScheduler.schedule(context)
        // Un premier passage fixe la référence : ce qui existe déjà ne sera pas notifié.
        SyncScheduler.runNow(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !granted
        // Même refusée, la synchronisation reste utile : les widgets et le cache restent à jour.
        enable()
    }

    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications et widgets", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Vérifie ÉcoleDirecte toutes les 4 heures : nouvelle note, message, cours annulé, devoir pour demain.",
                        color = AppTheme.colors.muted,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { wanted ->
                        if (!wanted) {
                            prefs.setEnabled(false)
                            SyncScheduler.cancel(context)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            blocked = false
                            enable()
                        }
                    },
                )
            }
            lastSync?.let {
                val moment = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                Text(
                    "Dernière vérification : " + moment.format(DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRENCH)),
                    color = AppTheme.colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            reason?.let {
                Text(it, color = AppTheme.colors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            if (blocked) {
                Text(
                    "Android bloque les notifications de l'application ; les widgets restent à jour. Autoriser dans les réglages",
                    color = AppTheme.colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
