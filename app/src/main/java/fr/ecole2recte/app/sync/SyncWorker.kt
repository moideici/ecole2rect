package fr.ecole2recte.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Le travail de fond. Il ne demande jamais à WorkManager de réessayer : un échec attend le
 * prochain passage, pour ne jamais multiplier les connexions à ÉcoleDirecte.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as Ecole2recteApplication).container
        val outcome = SyncService(
            store = container.credentialStore,
            cache = container.responseCache,
            homeworkStatus = container.homeworkStatus,
            prefs = container.syncPreferences,
            state = NotificationState(applicationContext),
            notifier = AndroidNotifier(applicationContext),
            widgets = WidgetUpdater(applicationContext),
            clientFactory = { EcoleDirecteClient(apiVersion = container.settings.apiVersion.value) },
            tokenLog = container.tokenLog,
        ).run()
        // Arrêtée (identifiants refusés, trop d'échecs, vérification à faire, version refusée) : plus aucun passage.
        if (outcome == SyncOutcome.Stopped || outcome == SyncOutcome.Disabled || outcome == SyncOutcome.NeedsInteraction) {
            SyncScheduler.cancel(applicationContext)
        }
        return Result.success()
    }
}

object SyncScheduler {
    private const val PERIODIC = "ecole2recte-sync"
    private const val ONCE = "ecole2recte-sync-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Planifie un passage toutes les [hours] heures (2, 4 ou 8), avec du réseau. */
    fun schedule(context: Context, hours: Int = 4) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.coerceIn(2, 24).toLong(), TimeUnit.HOURS)
            .setConstraints(network)
            // Gigue : tous les téléphones ne doivent pas interroger ÉcoleDirecte à la même minute.
            .setInitialDelay(jitterMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Délai aléatoire (1 à 10 minutes) avant le premier passage planifié. */
    fun jitterMinutes(random: kotlin.random.Random = kotlin.random.Random.Default): Long = random.nextLong(1, 11)

    /** Un passage tout de suite : à l'activation, il fixe la référence « ce qui existe déjà ». */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(ONCE)
    }
}
