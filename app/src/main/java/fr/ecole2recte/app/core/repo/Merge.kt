package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import java.time.LocalDate

/**
 * Fusion d'une réponse fraîche avec ce qui est déjà en cache.
 *
 * Une synchronisation ne doit ni créer de doublons, ni perdre ce que l'application a déjà
 * chargé en détail (contenu d'un devoir, corps d'un message). Ces fonctions sont pures :
 * elles décrivent la règle de fusion, et sont testées pour elle-même.
 */
object Merge {

    /**
     * L'emploi du temps n'est demandé que sur une fenêtre de dates. Les cours en dehors de
     * cette fenêtre restent valables : les remplacer par « rien » afficherait un agenda vide
     * dès qu'on regarde la semaine suivante.
     */
    fun lessons(
        existing: List<Lesson>,
        fetched: List<Lesson>,
        from: LocalDate,
        to: LocalDate,
    ): List<Lesson> {
        val outsideWindow = existing.filter { it.date.isBefore(from) || it.date.isAfter(to) }
        return (outsideWindow + fetched)
            .distinctBy { it.id }
            .sortedBy { it.start }
    }

    /**
     * La liste du cahier de textes ne porte pas les contenus : on garde ceux déjà chargés
     * pour le même devoir, sinon ouvrir un devoir puis synchroniser le viderait.
     */
    fun homework(existing: List<HomeworkItem>, fetched: List<HomeworkItem>): List<HomeworkItem> {
        val known = existing.associateBy { it.id }
        return fetched.map { item ->
            val previous = known[item.id]
            if (previous != null && !item.hasContent && previous.hasContent) {
                item.copy(content = previous.content, attachments = previous.attachments)
            } else {
                item
            }
        }.sortedWith(compareBy({ it.dueDate }, { it.subject }))
    }

    /** Détail d'un jour : il remplace les entrées de ce jour, et lui seul. */
    fun homeworkForDate(
        existing: List<HomeworkItem>,
        fetched: List<HomeworkItem>,
        date: LocalDate,
    ): List<HomeworkItem> {
        val untouched = existing.filter { it.dueDate != date }
        return (untouched + fetched)
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.dueDate }, { it.subject }))
    }

    /** Même principe pour la messagerie : la liste n'apporte pas les corps déjà lus. */
    fun messages(existing: List<MessageItem>, fetched: List<MessageItem>): List<MessageItem> {
        val known = existing.associateBy { it.id }
        return fetched.map { message ->
            val previous = known[message.id]
            if (previous != null && !message.hasBody && previous.hasBody) {
                message.copy(
                    body = previous.body,
                    preview = message.preview.ifBlank { previous.preview },
                    attachments = message.attachments.ifEmpty { previous.attachments },
                )
            } else {
                message
            }
        }.sortedByDescending { it.date }
    }

    fun replaceMessage(existing: List<MessageItem>, message: MessageItem): List<MessageItem> =
        existing.map { if (it.id == message.id) message else it }
            .let { updated ->
                if (updated.any { it.id == message.id }) updated else updated + message
            }
            .sortedByDescending { it.date }
}
