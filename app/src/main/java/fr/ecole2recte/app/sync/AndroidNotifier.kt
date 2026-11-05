package fr.ecole2recte.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R

/**
 * Poste les notifications : un canal par famille (l'utilisateur règle chacune dans Android),
 * une notification par événement, regroupées sous un résumé quand il y en a plusieurs.
 * Si Android bloque les notifications (permission refusée), ne fait rien.
 */
class AndroidNotifier(private val context: Context) : NotificationSink {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        EventKind.entries.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(kind.channelId, kind.channelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    override fun post(events: List<NotificationEvent>) {
        val manager = NotificationManagerCompat.from(context)
        if (events.isEmpty() || !manager.areNotificationsEnabled()) return
        ensureChannels()
        events.forEach { event ->
            val notification = NotificationCompat.Builder(context, event.kind.channelId)
                .setSmallIcon(R.drawable.ic_stat_ecole2recte)
                .setContentTitle(event.title)
                .setContentText(event.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.text))
                .setContentIntent(openTab(event.kind.tab))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            runCatching { manager.notify(event.id.hashCode(), notification) }
        }
        if (events.size > 1) {
            val summary = NotificationCompat.Builder(context, events.first().kind.channelId)
                .setSmallIcon(R.drawable.ic_stat_ecole2recte)
                .setContentTitle("${events.size} nouveautés")
                .setStyle(NotificationCompat.InboxStyle().also { style -> events.take(5).forEach { style.addLine(it.title) } })
                .setContentIntent(openTab("accueil"))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .build()
            runCatching { manager.notify(SUMMARY_ID, summary) }
        }
    }

    private fun openTab(tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(TAB_EXTRA, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, tab.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        /** Extra d'intent qui dit sur quel onglet ouvrir l'app (notifications, raccourcis, widgets). */
        const val TAB_EXTRA = "tab"
        private const val GROUP = "ecole2recte.news"
        private const val SUMMARY_ID = 1
    }
}
