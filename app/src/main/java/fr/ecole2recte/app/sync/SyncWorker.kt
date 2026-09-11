package fr.ecole2recte.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.core.notify.NotificationPlan
import fr.ecole2recte.app.widget.WidgetUpdater
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Synchronisation de fond.
 *
 * Sans elle, les widgets d'écran d'accueil n'affichent que ce que la dernière
 * ouverture de l'application avait ramené : un emploi du temps d'hier soir sur un écran
 * qu'on regarde le matin. Ce travail va chercher les données, décide s'il y a lieu de
 * notifier, puis repousse les widgets.
 *
 * Il ne fait rien tant que personne n'est connecté : pas de compte, pas de réseau consommé.
 */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? Ecole2recteApplication ?: return Result.success()
        val repository = application.container.repository
        return try {
            if (repository.currentAccount == null) repository.start()
            // Non connecté, ou connexion impossible pour l'instant : ce n'est pas un échec,
            // et réessayer en boucle ne ferait que vider la batterie.
            if (repository.currentAccount == null) return Result.success()

            repository.refresh()

            val plan = NotificationPlan.compute(
                snapshot = repository.snapshot.value,
                settings = repository.settings.value,
                now = LocalDateTime.now(),
            )
            Notifier(applicationContext).post(plan.notifications)
            repository.updateSettings { it.withNotified(plan.seen) }
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } catch (error: Throwable) {
            // Une panne passagère mérite un réessai temporisé, pas un abandon définitif.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/**
 * Programmation du travail de fond.
 *
 * Une fois par heure : assez pour qu'une note publiée le midi soit signalée l'après-midi,
 * assez peu pour ne pas marteler une API privée ni la batterie. Le système peut de toute
 * façon décaler l'exécution, et c'est très bien ainsi.
 */
object SyncScheduler {

    private const val PERIODIC_WORK = "ecole2recte-synchronisation"
    private const val IMMEDIATE_WORK = "ecole2recte-synchronisation-immediate"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    /** Synchronisation immédiate, par exemple juste après une connexion réussie. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK) }
    }
}
