package fr.ecole2recte.app.widget

import fr.ecole2recte.app.domain.MASKED
import fr.ecole2recte.app.domain.PrivacyMode
import fr.ecole2recte.app.sync.WidgetSnapshot

/**
 * Mode « montrer à quelqu'un » pour les widgets : les notes et la moyenne deviennent « ••• », les
 * messages aussi quand l'option est cochée. Appliqué sur l'instantané **avant** de le donner aux vues,
 * si bien qu'aucun widget ne voit les vraies valeurs tant que le mode est actif.
 *
 * L'instantané enregistré reste complet : on masque à la lecture et non à la publication, pour que
 * désactiver le mode rende les notes aussitôt, sans attendre une synchronisation (donc sans réseau).
 * Les réglages déclenchent un redessin immédiat des widgets (voir [WidgetRefresh]).
 */
object WidgetPrivacy {
    fun apply(snapshot: WidgetSnapshot, mode: PrivacyMode): WidgetSnapshot {
        if (!mode.enabled) return snapshot
        var result = snapshot
        if (mode.hidesGrades) {
            result = result.copy(
                grades = result.grades.map { it.copy(value = MASKED, label = "") },
                average = if (result.average.isBlank()) "" else MASKED,
                averageLabel = "",
                officialAverage = "",
                averageBehind = "",
            )
        }
        if (mode.hidesMessages) {
            result = result.copy(mails = result.mails.map { it.copy(sender = MASKED, subject = MASKED) })
        }
        return result
    }
}
