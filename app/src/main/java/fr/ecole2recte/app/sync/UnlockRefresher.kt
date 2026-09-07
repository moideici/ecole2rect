package fr.ecole2recte.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Rafraîchissement des widgets au déverrouillage.
 *
 * C'est le moment où l'on regarde son écran d'accueil, et c'est celui où un widget a le plus
 * de chances d'être faux : « dans 24 min » a vieilli, la barre d'avancement d'un cours a
 * couru, la journée d'hier est devenue celle d'aujourd'hui. Le système, lui, ne réveille un
 * widget qu'une fois par demi-heure au mieux.
 *
 * Le redessin est **gratuit** : il relit le cache sur disque, sans réseau. Une vraie
 * synchronisation n'est déclenchée que si les données ont vieilli, et elle passe par
 * `WorkManager`, qui attendra une connexion.
 *
 * **Ce que ce receveur ne peut pas faire.** `ACTION_USER_PRESENT` ne fait pas partie des
 * diffusions implicites qu'un receveur déclaré au manifeste a le droit de recevoir depuis
 * Android 8 : il ne peut être écouté qu'à l'exécution, donc seulement tant que notre
 * processus vit. C'est souvent le cas — l'application a été ouverte récemment, un widget
 * vient d'être réveillé, la synchronisation horaire tourne — mais ce n'est pas garanti.
 * Le travail périodique et le réveil système restent donc le filet en dessous.
 */
class UnlockRefresher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val application = context.applicationContext
        // Le disque et les `RemoteViews` n'ont rien à faire sur le fil principal, même pour
        // quelques millisecondes : `goAsync` laisse le temps de finir proprement.
        val pending = goAsync()
        SCOPE.launch {
            try {
                WidgetUpdater.updateAll(application)
                if (intent.action == Intent.ACTION_USER_PRESENT && isStale(application)) {
                    SyncScheduler.runNow(application)
                }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    /** Vrai quand la dernière synchronisation remonte à assez longtemps pour en refaire une. */
    private fun isStale(context: Context): Boolean {
        val repository = (context as? Ecole2recteApplication)?.container?.repository ?: return false
        if (repository.currentAccount == null) return false
        val last = repository.snapshot.value.lastSyncedAt ?: return true
        return System.currentTimeMillis() - last > STALE_AFTER_MILLIS
    }

    companion object {
        private val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** En deçà, redessiner le cache suffit : rien n'a pu changer côté établissement. */
        private const val STALE_AFTER_MILLIS = 20 * 60 * 1000L

        private val ACTIONS = setOf(Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON)

        /**
         * Écoute le déverrouillage tant que le processus vit.
         *
         * Le receveur n'est pas exporté : ces deux diffusions viennent du système et de lui
         * seul, aucune application tierce ne doit pouvoir les simuler.
         */
        fun listen(context: Context) {
            val filter = IntentFilter().apply {
                ACTIONS.forEach(::addAction)
            }
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    UnlockRefresher(),
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        }
    }
}
