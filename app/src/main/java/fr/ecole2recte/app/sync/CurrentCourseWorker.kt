package fr.ecole2recte.app.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.R
import fr.ecole2recte.app.settings.PrivacySelection
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Une tâche locale aux bornes des cours. Aucun service, aucune contrainte réseau. */
class CurrentCourseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        CurrentCourseScheduler.refresh(applicationContext, fromWorker = true)
        return Result.success()
    }
}

object CurrentCourseScheduler {
    private const val WORK = "current-course-boundary"
    private const val CHANNEL = "current-course"
    private const val ID = 4100

    fun refresh(context: Context, fromWorker: Boolean = false) {
        val now = LocalDateTime.now()
        val enabled = LocalFeaturePreferences(context).currentCourse
        val snapshot = WidgetSnapshotStore(context).read()?.takeIf { it.owner.isNotBlank() && it.owner == LocalFeaturePreferences(context).activeOwner }
        val plan = CurrentCoursePlanner.plan(snapshot?.lessons.orEmpty(), now, enabled)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!enabled || plan.current == null || !notificationsAllowed(context)) manager.cancel(ID)
        if (enabled && plan.current != null && notificationsAllowed(context)) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Cours en cours", NotificationManager.IMPORTANCE_LOW))
            val hidden = PrivacySelection.mode(context).enabled
            val public = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_ecole2recte)
                .setContentTitle("Cours en cours").setContentText("Déverrouille ton téléphone pour voir le détail.").build()
            val open = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).putExtra("tab", "agenda"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_ecole2recte)
                .setContentTitle(if (hidden) "•••" else plan.current.subject)
                .setContentText(CurrentCoursePlanner.text(plan, hidden))
                .setStyle(Notification.BigTextStyle().bigText(CurrentCoursePlanner.text(plan, hidden)))
                .setProgress(100, plan.progress, false).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(open)
                // Android retire aussi la notification si l'exécution à la borne suivante est retardée.
                .setTimeoutAfter(Duration.between(now, LocalDateTime.parse("${plan.current.date}T${plan.current.end}")).toMillis().coerceAtLeast(1))
            applyNativeProgress(builder, plan.progress)
            runCatching { manager.notify(ID, builder.build()) }
        }
        val workManager = WorkManager.getInstance(context)
        if (!fromWorker) workManager.cancelUniqueWork(WORK)
        plan.nextRefresh(now)?.let { boundary ->
            val request = OneTimeWorkRequestBuilder<CurrentCourseWorker>()
                .setInitialDelay(Duration.between(now, boundary).toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS).build()
            // APPEND évite qu'une tâche en cours ne s'annule en programmant sa propre suite.
            workManager.enqueueUniqueWork(WORK, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
        }
    }

    private fun notificationsAllowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Le projet compile avec SDK 35 : accès optionnel au style public ajouté en 36, repli classique. */
    private fun applyNativeProgress(builder: Notification.Builder, progress: Int) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            val type = Class.forName("android.app.Notification\$ProgressStyle")
            val segmentType = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
            val native = type.getConstructor().newInstance()
            type.getMethod("setProgressSegments", List::class.java).invoke(native, listOf(segmentType.getConstructor(Int::class.javaPrimitiveType).newInstance(100)))
            type.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(native, progress)
            builder.setStyle(native as Notification.Style)
        }
    }
}
