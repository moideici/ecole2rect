package fr.ecole2recte.app.data

import android.content.Context
import fr.ecole2recte.app.core.protocol.EcoleDirecteApi
import fr.ecole2recte.app.core.protocol.RetryingHttpEngine
import fr.ecole2recte.app.core.repo.SchoolRepository

/**
 * Assemblage de l'application : un seul endroit où les implémentations Android rencontrent
 * le cœur métier. Il n'y a pas de conteneur d'injection, et il n'en faut pas : le graphe
 * tient en cinq objets, et le voir en entier vaut mieux qu'une annotation.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val credentials = SecureCredentialStore(applicationContext)
    val snapshots = FileSnapshotStore(applicationContext)
    val settings = PreferencesSettingsStore(applicationContext)

    /**
     * Le transport est enveloppé : une coupure d'une demi-seconde ne doit pas coûter une
     * synchronisation entière, mais un refus du serveur n'est jamais rejoué.
     */
    val api = EcoleDirecteApi(RetryingHttpEngine(AndroidHttpEngine()))

    val repository = SchoolRepository(
        api = api,
        credentials = credentials,
        snapshots = snapshots,
        settingsStore = settings,
    )
}
