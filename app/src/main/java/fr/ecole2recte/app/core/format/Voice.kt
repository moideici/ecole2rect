package fr.ecole2recte.app.core.format

import fr.ecole2recte.app.core.model.Account

/**
 * Le ton de l'application.
 *
 * Une même phrase ne peut pas s'adresser à l'élève et à ses parents : « tes devoirs de
 * demain » n'a aucun sens pour une mère qui regarde le cahier de textes de sa fille, et
 * « la date de naissance » ne désigne pas la même personne selon qui se connecte. Plutôt
 * que de disperser des `if (parent)` dans quinze écrans, les quelques phrases qui changent
 * sont réunies ici — et elles ne changent qu'au singulier près.
 */
object Voice {

    /** Titre de l'accueil : on salue l'élève, on annonce l'enfant au parent. */
    fun homeTitle(account: Account?): String {
        val firstName = account?.firstName?.takeIf { it.isNotBlank() }
        return when {
            account?.viewedByFamily == true -> firstName ?: "Votre enfant"
            firstName != null -> "Bonjour $firstName"
            else -> "Bonjour"
        }
    }

    /** Libellé du champ de date de naissance, sur l'écran de connexion. */
    fun birthDateLabel(parent: Boolean): String =
        if (parent) "Date de naissance de l'enfant" else "Ta date de naissance"

    fun birthDateHelp(parent: Boolean): String = if (parent) {
        "ÉcoleDirecte interroge sur l'enfant, jamais sur vous. Cette date reste sur l'appareil " +
            "et sert seulement à reconnaître la bonne proposition."
    } else {
        "Sert seulement à reconnaître la bonne proposition si une question de sécurité est " +
            "posée. Reste sur cet appareil."
    }

    /** Ce qu'on dit d'un devoir coché depuis un compte parent. */
    const val PARENT_HOMEWORK_NOTE: String =
        "Coché ici seulement : le cahier de textes de votre enfant n'est pas modifié."
}
