package fr.ecole2recte.app.core.store

import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.protocol.TrustedAnswer
import fr.ecole2recte.app.core.settings.AppSettings

/**
 * Ce que l'application garde entre deux lancements, décrit sous forme d'interfaces.
 *
 * Le dépôt ne connaît que ces trois contrats : l'implémentation Android (Keystore, fichier,
 * préférences partagées) reste remplaçable, et les tests utilisent des versions en mémoire.
 */

/**
 * Identifiants nécessaires à la reconnexion silencieuse.
 *
 * Le mot de passe est indispensable : l'API ne délivre pas de jeton de rafraîchissement, et
 * exige une connexion complète dès que le jeton expire. Il est donc stocké chiffré et n'est
 * jamais écrit dans un journal ni dans un diagnostic.
 */
data class SavedCredentials(
    val identifier: String,
    val password: String,
    val trustedAnswer: TrustedAnswer? = null,
)

interface CredentialStore {
    fun read(): SavedCredentials?
    fun save(credentials: SavedCredentials)
    fun clear()
}

interface SnapshotStore {
    fun read(): SchoolSnapshot?
    fun write(snapshot: SchoolSnapshot)
    fun clear()
}

interface SettingsStore {
    fun read(): AppSettings
    fun write(settings: AppSettings)
}
