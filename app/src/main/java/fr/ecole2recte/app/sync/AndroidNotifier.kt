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

/**
 * Poste les notifications : un canal par famille (l'utilisateur règle chacune dans Android),
 * une notification par événement, regroupées sous un résumé quand il y en a plusieurs.
 * Si Android bloque les notifications (permission refusée), ne fait rien.
 */
class AndroidNotifier(private val context: Context) : NotificationSink {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // Les anciens canaux (tout en importance normale) sont retirés : cours et devoirs passent en discret.
        EventKind.legacyChannelIds.forEach { manager.deleteNotificationChannel(it) }
        EventKind.entries.forEach { kind ->
            val importance = if (kind.quiet) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
            manager.createNotificationChannel(NotificationChannel(kind.channelId, kind.channelName, importance))
        }
    }

    override fun post(events: List<NotificationEvent>) {
        val manager = NotificationManagerCompat.from(context)
        if (events.isEmpty() || !manager.areNotificationsEnabled()) return
        // Depuis Android 13 la permission peut être refusée : on ne notifie pas sans elle.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
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
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                // Écran verrouillé : ni note, ni nom d'enseignant, ni message ; seulement la famille de la nouveauté.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(event.kind.channelId, event.kind.publicTitle))
                .apply { event.owner.takeIf { it.isNotBlank() }?.let { setSubText(it) } }
                .build()
            runCatching { manager.notify((event.owner + "|" + event.id).hashCode(), notification) }
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
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(events.first().kind.channelId, "${events.size} nouveautés"))
                .build()
            runCatching { manager.notify(SUMMARY_ID, summary) }
        }
    }

    /** Ce qu'on voit sans déverrouiller le téléphone : un titre générique, jamais le contenu. */
    private fun publicVersion(channelId: String, title: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_ecole2recte)
            .setContentTitle(title)
            .setContentText("Déverrouille ton téléphone pour voir le détail.")
            .build()

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
