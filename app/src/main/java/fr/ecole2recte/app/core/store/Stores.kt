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

/**
 * Cache des données scolaires, un par profil.
 *
 * Une famille suit deux enfants : passer de l'un à l'autre ne doit pas jeter les données du
 * premier, sinon changer de profil coûte une synchronisation complète à chaque aller-retour.
 * L'identifiant de profil est celui du compte ; `null` désigne le cache d'avant la connexion.
 */
interface SnapshotStore {
    fun read(profile: String?): SchoolSnapshot?
    fun write(profile: String?, snapshot: SchoolSnapshot)

    /** Efface tous les profils : c'est la déconnexion. */
    fun clear()
}

interface SettingsStore {
    fun read(): AppSettings
    fun write(settings: AppSettings)
}
