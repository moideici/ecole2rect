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
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.domain.SchoolLifeKind
import fr.ecole2recte.app.domain.SchoolLifeLogic
import fr.ecole2recte.app.settings.PrivacySelection
import fr.ecole2recte.app.widget.SchoolLifeSnapshotStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class AbsenceReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AbsenceReminderScheduler.refresh(applicationContext, fromWorker = true)
        return Result.success()
    }
}

object AbsenceReminderScheduler {
    private const val WORK = "absence-reminder"
    private const val CHANNEL = "absence-reminder"
    private const val ID = 4102
    fun refresh(context: Context, fromWorker: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        if (!fromWorker) workManager.cancelUniqueWork(WORK)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ID)
        val days = LocalFeaturePreferences(context).absenceReminderDays ?: return
        if (PrivacySelection.mode(context).enabled) return
        val store = SchoolLifeSnapshotStore(context)
        if (store.owner.isBlank() || store.owner != LocalFeaturePreferences(context).activeOwner) return
        val events = store.read() ?: return
        val now = LocalDateTime.now()
        val reminders = SchoolLifeLogic.reminders(events, now.toLocalDate(), days, store.notified())
        if (reminders.isNotEmpty() && manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Justificatif attendu", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java).putExtra("tab", "vie-scolaire"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_ecole2recte).setContentTitle("Vie scolaire").build()
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_ecole2recte)
                .setContentTitle("Justificatif attendu").setContentText("${reminders.size} absence(s) non justifiée(s) depuis $days jours ou plus, selon les données enregistrées.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(open).setAutoCancel(true).build()
            runCatching { manager.notify(ID, notification) }.onSuccess { store.markNotified(reminders.map { it.id }.toSet()) }
        }
        val notified = store.notified()
        val next = events.filter { it.kind == SchoolLifeKind.Absence && !it.justified && it.id.isNotBlank() && it.id !in notified }
            .mapNotNull { runCatching { LocalDate.parse(it.date).plusDays(days.toLong()).atTime(8, 0) }.getOrNull() }
            .map { if (it <= now) now.toLocalDate().plusDays(1).atTime(8, 0) else it }.minOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<AbsenceReminderWorker>().setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build()
        workManager.enqueueUniqueWork(WORK, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
}
