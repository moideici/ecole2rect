package fr.ecole2recte.app.ui.more

import fr.ecole2recte.app.domain.FrenchFormat
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
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
import fr.ecole2recte.app.sync.NotificationRules
import fr.ecole2recte.app.sync.SyncIntervals
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
    val rules by prefs.rulesFlow.collectAsStateWithLifecycle()
    val interval by prefs.intervalHours.collectAsStateWithLifecycle()
    var blocked by remember { mutableStateOf(false) }

    fun enable() {
        prefs.setEnabled(true)
        // Chaque étape est isolée : si Android refuse l'une (canaux, planification), l'écran ne plante pas
        // et l'autre étape est tout de même faite.
        runCatching { AndroidNotifier(context).ensureChannels() }
        runCatching { SyncScheduler.schedule(context, prefs.intervalHours.value) }
        // Un premier passage fixe la référence : ce qui existe déjà ne sera pas notifié.
        runCatching { SyncScheduler.runNow(context) }
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
                    Text("Notifications", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            if (enabled) {
                Column(Modifier.padding(top = 10.dp)) {
                    RuleRow("Emploi du temps", rules.schedule) { prefs.setRules(rules.copy(schedule = it)) }
                    RuleRow("Notes", rules.grades) { prefs.setRules(rules.copy(grades = it)) }
                    RuleRow("Messages", rules.messages) { prefs.setRules(rules.copy(messages = it)) }
                    RuleRow("Devoirs pour demain", rules.homework) { prefs.setRules(rules.copy(homework = it)) }
                    if (rules.homework) {
                        Text("Rappel dès", color = AppTheme.colors.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NotificationRules.EVENING_HOURS.forEach { hour ->
                                FilterChip(
                                    selected = rules.eveningHour == hour,
                                    onClick = { prefs.setRules(rules.copy(eveningHour = hour)) },
                                    label = { Text("$hour h", fontSize = 13.sp) },
                                )
                            }
                        }
                    }
                    RuleRow("Silence de 22 h à 7 h", rules.quietNight) { prefs.setRules(rules.copy(quietNight = it)) }
                    Text("Vérifier toutes les", color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SyncIntervals.choices.forEach { hours ->
                            FilterChip(
                                selected = interval == hours,
                                onClick = {
                                    prefs.setIntervalHours(hours)
                                    SyncScheduler.schedule(context, hours)
                                },
                                label = { Text("$hours h", fontSize = 13.sp) },
                            )
                        }
                    }
                }
            }
            lastSync?.let {
                val moment = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                Text(
                    "Vérifié " + FrenchFormat.weekdayDayMonthAtTime(moment),
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
                    "Notifications bloquées par Android. Autoriser",
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

/** Une ligne de réglage : un titre et un interrupteur. */
@Composable
private fun RuleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
