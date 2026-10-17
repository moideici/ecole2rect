package fr.ecole2recte.app

import android.app.Application
import fr.ecole2recte.app.data.AppContainer
import fr.ecole2recte.app.sync.Notifier
import fr.ecole2recte.app.sync.SyncScheduler
import fr.ecole2recte.app.sync.UnlockRefresher
import fr.ecole2recte.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class Ecole2recteApplication : Application() {

    /** Créé à la demande : le premier écran n'attend pas la construction du graphe. */
    val container: AppContainer by lazy { AppContainer(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        observeForWidgets()
        // Les canaux existent dès le premier lancement : l'utilisateur peut ainsi les régler
        // depuis les paramètres du système avant même d'avoir reçu quoi que ce soit.
        Notifier(this).ensureChannels()
        SyncScheduler.ensureScheduled(this)
        // Les widgets se redessinent au déverrouillage : c'est le moment où on les regarde,
        // et celui où le temps qu'ils affichent a le plus vieilli.
        UnlockRefresher.listen(this)
    }

    /**
     * Les widgets ne sont plus réveillés par le système, sauf les deux qui affichent une heure.
     * L'application, elle, sait exactement quand la donnée change : fin de synchronisation,
     * devoir coché, couleur de matière modifiée. Elle pousse donc la mise à jour.
     *
     * Le `conflate` n'est pas un détail. Une synchronisation écrit l'instantané **section par
     * section** — emploi du temps, devoirs, notes, messages… — et chaque écriture changeait la
     * clé : les neuf widgets étaient donc reconstruits six ou sept fois de suite, chaque fois
     * en relisant et réanalysant tout le cache. On ne garde que le dernier état.
     */
    private fun observeForWidgets() {
        scope.launch {
            combine(container.repository.snapshot, container.repository.settings) { snapshot, settings ->
                listOf(
                    snapshot.lastSyncedAt,
                    snapshot.lessons.size,
                    snapshot.homework.size,
                    snapshot.grades.size,
                    snapshot.messages.size,
                    settings.doneHomework.size,
                    settings.readMessages.size,
                    settings.subjectColors.hashCode(),
                    // Sans cette clé, changer les couleurs des widgets ne les redessinerait
                    // qu'au prochain réveil du système — une demi-heure plus tard.
                    settings.widgetColors.hashCode(),
                )
            }
                .distinctUntilChanged()
                .conflate()
                .collect { WidgetUpdater.updateAll(this@Ecole2recteApplication) }
        }
    }
}
