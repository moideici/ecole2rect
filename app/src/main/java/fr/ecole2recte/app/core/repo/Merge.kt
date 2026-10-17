package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SessionContent
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
    fun messages(
        existing: List<MessageItem>,
        fetched: List<MessageItem>,
        folder: MessageFolder = MessageFolder.RECEIVED,
    ): List<MessageItem> {
        val known = existing.associateBy { it.id }
        // Une page de « reçus » ne dit rien des « envoyés » : les autres dossiers déjà
        // chargés restent en place, sinon changer d'onglet les rechargerait chaque fois.
        val untouched = existing.filter { it.folder != folder }
        val fresh = fetched.map { message ->
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
        }
        // La page fraîche est triée du plus récent au plus ancien et ne couvre que le haut de
        // la liste. Ce qui est plus ancien que son dernier message a été remonté page par
        // page, à la main, par quelqu'un qui cherchait quelque chose : une synchronisation de
        // fond n'a aucune raison de le jeter — et le rejeter le faisait repayer en données.
        // Une page vide, elle, dit que le dossier est vide côté serveur : là, on suit.
        val oldestFresh = fresh.mapNotNull { it.date }.minOrNull()
        val deeper = if (fresh.isEmpty() || oldestFresh == null) {
            emptyList()
        } else {
            // Un message sans date ne peut pas être situé : le garder vaut mieux que de le
            // perdre parce qu'on ne sait pas le comparer.
            existing.filter { it.folder == folder && it.date?.isBefore(oldestFresh) != false }
        }
        return (untouched + fresh + deeper)
            .distinctBy { it.id }
            .sortedByDescending { it.date }
    }

    /** Le contenu de séance d'un jour remplace celui du même jour, et lui seul. */
    fun sessionContents(
        existing: List<SessionContent>,
        fetched: List<SessionContent>,
        date: LocalDate,
    ): List<SessionContent> = (existing.filter { it.date != date } + fetched)
        .distinctBy { it.id }
        .sortedByDescending { it.date }

    /**
     * Page suivante de la messagerie : on ajoute sans jamais retirer, et sans doublon si le
     * serveur renvoie deux fois le même message à cheval sur deux pages.
     */
    fun appendMessages(existing: List<MessageItem>, page: List<MessageItem>): List<MessageItem> {
        val known = existing.associateBy { it.id }
        val additions = page.filterNot { it.id in known }
        return (existing + additions).sortedByDescending { it.date }
    }

    fun replaceMessage(existing: List<MessageItem>, message: MessageItem): List<MessageItem> =
        existing.map { if (it.id == message.id) message else it }
            .let { updated ->
                if (updated.any { it.id == message.id }) updated else updated + message
            }
            .sortedByDescending { it.date }
}
