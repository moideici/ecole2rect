package fr.ecole2recte.app.ui.login

/**
 * Ce que l'utilisateur doit savoir avant d'entrer son identifiant, et que « Plus > À propos » redit.
 * Une seule source : l'écran du premier lancement, l'écran À propos et le README disent la même chose.
 */
object LegalNotice {
    const val TITLE = "Avant de commencer"

    val points: List<String> = listOf(
        "Application non officielle, non affiliée à ÉcoleDirecte.",
        "Tes identifiants ne servent qu'à te connecter : chiffrés sur ce téléphone, envoyés nulle part ailleurs.",
        "L'interface d'ÉcoleDirecte n'est pas documentée et peut changer : le site fait foi.",
        "Lecture seule, sauf si tu actives l'écriture dans Plus.",
    )

    const val ACCEPT = "J'ai compris"

    /** Le résumé des limites, pour « Plus > À propos ». */
    val limits: List<String> = listOf(
        "Un changement chez ÉcoleDirecte peut casser un écran.",
        "Comptes professeur non pris en charge.",
        "Pièces jointes limitées à 25 Mo ; ni programmes ni scripts.",
    )
}

/** L'écran du premier lancement ne s'affiche que devant la saisie d'identifiants, et une seule fois. */
fun needsNotice(accepted: Boolean, loginVisible: Boolean): Boolean = loginVisible && !accepted
