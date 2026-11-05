package fr.ecole2recte.app.ui.login

/**
 * Ce que l'utilisateur doit savoir avant d'entrer son identifiant, et que « Plus > À propos » redit.
 * Une seule source : l'écran du premier lancement, l'écran À propos et le README disent la même chose.
 */
object LegalNotice {
    const val TITLE = "Avant de commencer"

    val points: List<String> = listOf(
        "ecole2recte est une application non officielle. Elle n'est ni éditée, ni approuvée, ni affiliée à ÉcoleDirecte ou à son éditeur.",
        "Ton identifiant et ton mot de passe ne servent qu'à te connecter à ÉcoleDirecte. Ils restent chiffrés sur ce téléphone et ne sont envoyés nulle part ailleurs.",
        "L'application utilise l'interface privée d'ÉcoleDirecte, qui n'est pas documentée : elle peut cesser de fonctionner du jour au lendemain, et un affichage peut différer du site officiel. En cas de doute, le site fait foi.",
        "Elle ne fait que lire tes données. Rien n'est envoyé ni modifié chez ÉcoleDirecte sans que tu l'actives toi-même dans Plus.",
    )

    const val ACCEPT = "J'ai compris"

    /** Le résumé des limites, pour « Plus > À propos ». */
    val limits: List<String> = listOf(
        "Un changement côté ÉcoleDirecte peut casser un écran sans prévenir.",
        "Les comptes professeur ne sont pas pris en charge.",
        "Les pièces jointes sont limitées à 25 Mo ; programmes, scripts et pages web ne sont jamais ouverts.",
        "Certaines formes de réponse (modules, appréciations, extrêmes de classe) ne sont pas vérifiées sur tous les établissements : le Diagnostic aide à le signaler.",
    )
}

/** L'écran du premier lancement ne s'affiche que devant la saisie d'identifiants, et une seule fois. */
fun needsNotice(accepted: Boolean, loginVisible: Boolean): Boolean = loginVisible && !accepted
