package fr.ecole2recte.app.core.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Modèles applicatifs stables.
 *
 * Rien ici ne vient directement d'ÉcoleDirecte : les réponses de l'API privée sont
 * converties dans ces types par [fr.ecole2recte.app.core.parse]. Le jour où le protocole
 * change, seule la couche de parsing bouge — l'interface et le cache restent identiques.
 */

/** Un compte renvoyé par la connexion (élève ou famille). */
data class Account(
    val id: String,
    val type: String,
    val firstName: String,
    val lastName: String,
    val className: String = "",
    val schoolName: String = "",
) {
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    /** Les endpoints élève ne répondent pas pour un compte famille : on le sait au type. */
    val isStudent: Boolean get() = type.equals("E", ignoreCase = true)

    val initials: String
        get() = (firstName.take(1) + lastName.take(1)).uppercase().ifBlank { "É" }
}

/** Une heure de cours de l'emploi du temps. */
data class Lesson(
    val id: String,
    val subject: String,
    val teacher: String = "",
    val room: String = "",
    val start: LocalDateTime,
    val end: LocalDateTime,
    val canceled: Boolean = false,
    val exam: Boolean = false,
    val note: String = "",
) {
    val date: LocalDate get() = start.toLocalDate()
}

/** Une pièce jointe (devoir ou message). */
data class Attachment(
    val id: String,
    val name: String,
    val type: String = "",
)

/** Un devoir du cahier de textes. */
data class HomeworkItem(
    val id: String,
    val subject: String,
    val dueDate: LocalDate,
    val givenOn: LocalDate? = null,
    val content: String = "",
    val exam: Boolean = false,
    val done: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
) {
    /** Vrai quand le contenu n'a pas encore été chargé par l'appel détaillé du jour. */
    val hasContent: Boolean get() = content.isNotBlank()
}

/** Une note d'un devoir surveillé. */
data class Grade(
    val id: String,
    val subject: String,
    val label: String,
    val value: Double?,
    val scale: Double,
    val coefficient: Double,
    val classAverage: Double?,
    val date: LocalDate?,
    val periodCode: String,
    val counted: Boolean = true,
    val comment: String = "",
) {
    /** Note ramenée sur 20, seule façon de comparer un /10 et un /20 dans une moyenne. */
    val outOfTwenty: Double? get() = value?.takeIf { scale > 0 }?.let { it * 20.0 / scale }
}

/** Une période de notation (trimestre, semestre). */
data class Period(
    val code: String,
    val name: String,
    val average: Double? = null,
    val classAverage: Double? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val current: Boolean = false,
)

/** Moyenne d'une matière sur une période, telle que l'établissement la calcule. */
data class SubjectAverage(
    val periodCode: String,
    val subject: String,
    val average: Double?,
    val classAverage: Double? = null,
    val coefficient: Double? = null,
    val teacher: String = "",
    val appreciation: String = "",
)

/** Un message de la messagerie. */
data class MessageItem(
    val id: String,
    val subject: String,
    val sender: String,
    val date: LocalDateTime?,
    val read: Boolean = false,
    val preview: String = "",
    val body: String = "",
    val attachments: List<Attachment> = emptyList(),
) {
    val hasBody: Boolean get() = body.isNotBlank()
}

/** Les quatre familles de données synchronisées, chacune avec son état propre. */
enum class DataSection(val label: String) {
    TIMETABLE("Emploi du temps"),
    HOMEWORK("Devoirs"),
    GRADES("Notes"),
    MESSAGES("Messages"),
}

/**
 * Photo complète des données scolaires à un instant donné.
 *
 * C'est l'unité de cache : elle est écrite entière sur le disque après chaque
 * synchronisation réussie, et relue au lancement pour afficher quelque chose
 * immédiatement, même sans réseau.
 */
data class SchoolSnapshot(
    val account: Account? = null,
    val lessons: List<Lesson> = emptyList(),
    val homework: List<HomeworkItem> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val periods: List<Period> = emptyList(),
    val subjectAverages: List<SubjectAverage> = emptyList(),
    val messages: List<MessageItem> = emptyList(),
    /** Millisecondes epoch de la dernière synchronisation réussie, section par section. */
    val syncedAt: Map<DataSection, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = lessons.isEmpty() && homework.isEmpty() && grades.isEmpty() && messages.isEmpty()

    val lastSyncedAt: Long? get() = syncedAt.values.maxOrNull()
}
