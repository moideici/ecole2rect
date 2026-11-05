package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.session.ActiveProfile

/**
 * Un seul dépôt de données par profil (compte ou élève d'un compte famille).
 *
 * Avant, chaque écran créait le sien : la mémoire de 90 s qui évite les requêtes en double ne
 * servait à rien entre l'accueil, l'agenda et les pastilles, et ÉcoleDirecte voyait jusqu'à sept
 * fois la même demande. Ici tous les écrans d'un profil partagent la même instance.
 */
class ProfileRepositories(private val create: (ActiveProfile) -> SchoolRepository) {
    private val repositories = HashMap<String, SchoolRepository>()

    @Synchronized
    fun get(profile: ActiveProfile): SchoolRepository = repositories.getOrPut(profile.key.lowercase()) { create(profile) }

    /** Oublie les dépôts d'un compte et de tous ses élèves (retrait du compte). */
    @Synchronized
    fun forget(identifier: String) {
        val prefix = identifier.lowercase() + "/"
        repositories.keys.removeAll { it.startsWith(prefix) }
    }
}
