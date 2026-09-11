package fr.ecole2recte.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.core.notify.NotificationKind
import fr.ecole2recte.app.core.notify.PlannedNotification

/**
 * Affichage des notifications.
 *
 * Quoi notifier est décidé dans `core` ; ici on ne fait que poser les canaux et publier. Un
 * canal par famille, pour que couper les rappels d'évaluation n'oblige pas à tout couper —
 * et pour que le réglage système reste plus fin que le nôtre.
 */
class Notifier(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationKind.entries.forEach { kind ->
            val channel = NotificationChannel(
                kind.channelId,
                kind.channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = kind.channelDescription }
            runCatching { manager.createNotificationChannel(channel) }
        }
    }

    /** Vrai si le système nous laisse notifier : sinon, on ne fait rien, sans rien casser. */
    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun post(notifications: List<PlannedNotification>) {
        if (notifications.isEmpty() || !canNotify()) return
        ensureChannels()
        val manager = NotificationManagerCompat.from(context)
        notifications.forEach { planned ->
            val notification = NotificationCompat.Builder(context, planned.kind.channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(planned.title)
                .setContentText(planned.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(planned.text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openApp(planned))
                .setGroup(planned.kind.channelId)
                .build()
            // L'identifiant stable évite qu'une même note signalée deux fois s'empile.
            try {
                manager.notify(planned.id.hashCode(), notification)
            } catch (refusee: SecurityException) {
                // La permission a pu être retirée entre canNotify() et ici : rien à publier de
                // plus, et une notification perdue ne fait pas tomber la synchronisation.
                return
            } catch (limite: RuntimeException) {
                // Le système refuse au-delà d'un certain nombre de notifications par application.
                // On passe à la notification suivante.
            }
        }
    }

    private fun openApp(planned: PlannedNotification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // Même raison que pour les widgets : réutiliser l'écran ouvert plutôt que
            // de le recréer, pour que `onNewIntent` serve et que rien ne soit perdu.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, planned.target.name)
        }
        return PendingIntent.getActivity(
            context,
            planned.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
