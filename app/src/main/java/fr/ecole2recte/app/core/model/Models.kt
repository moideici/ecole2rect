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

/**
 * Un profil consultable : un élève, vu par lui-même ou par ses parents.
 *
 * Une connexion parent ne donne pas un compte, mais une famille et ses enfants. Comme tout
 * le reste de l'application parle d'un élève — son emploi du temps, ses notes, ses devoirs —
 * chaque enfant devient ici un profil à part entière, qui se souvient seulement de la
 * famille par laquelle on le regarde : c'est elle qui porte la messagerie, et c'est elle qui
 * change le ton de l'interface.
 */
data class Account(
    val id: String,
    val type: String,
    val firstName: String,
    val lastName: String,
    val className: String = "",
    val schoolName: String = "",
    /** Compte famille par lequel cet élève est consulté ; null quand l'élève est connecté. */
    val familyId: String? = null,
    /** Nom du responsable connecté, à ne jamais confondre avec celui de l'enfant. */
    val familyName: String = "",
) {
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    /** Les endpoints élève ne répondent pas pour un compte famille : on le sait au type. */
    val isStudent: Boolean get() = type.equals("E", ignoreCase = true)

    /** Vrai quand c'est un parent qui regarde : l'application se met alors à vouvoyer. */
    val viewedByFamily: Boolean get() = !familyId.isNullOrBlank()

    /** Propriétaire de la boîte aux lettres : la famille pour un parent, l'élève sinon. */
    val mailboxId: String get() = familyId?.takeIf { it.isNotBlank() } ?: id

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

/**
 * Une pièce jointe.
 *
 * Le téléchargement passe par un endpoint unique, mais qui exige de savoir d'où vient le
 * fichier : le cahier de textes et la messagerie ne portent pas le même « type de fichier ».
 */
data class Attachment(
    val id: String,
    val name: String,
    val type: String = "",
    val source: AttachmentSource = AttachmentSource.HOMEWORK,
    /**
     * Type de fichier attendu par l'endpoint de téléchargement, quand il ne découle pas
     * simplement de l'origine — c'est le cas des documents de l'espace numérique, dont le
     * type dépend de la catégorie (bulletin, pièce administrative, facture).
     */
    val downloadType: String = "",
) {
    val apiDownloadType: String get() = downloadType.ifBlank { source.apiType }
}

enum class AttachmentSource(val apiType: String) {
    HOMEWORK("FICHIER_CDT"),
    MESSAGE("PIECE_JOINTE"),
    RESOURCE("CLOUD"),
}

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

/**
 * Un événement de vie scolaire : absence, retard, sanction ou encouragement.
 *
 * Une seule forme pour les quatre, parce que c'est ainsi qu'ils se lisent — une liste
 * chronologique de ce qui a été noté sur l'élève — et que l'API les mélange déjà.
 */
data class SchoolLifeEvent(
    val id: String,
    val kind: SchoolLifeKind,
    val label: String,
    val start: LocalDateTime,
    val end: LocalDateTime? = null,
    val justified: Boolean = false,
    val reason: String = "",
    val comment: String = "",
) {
    val date: LocalDate get() = start.toLocalDate()
}

enum class SchoolLifeKind(val label: String) {
    ABSENCE("Absence"),
    LATE("Retard"),
    SANCTION("Sanction"),
    ENCOURAGEMENT("Encouragement"),
    OTHER("Autre"),
}

/**
 * Le contenu d'une séance : ce qui a été fait en cours.
 *
 * C'est la moitié du cahier de textes que l'application ignorait — celle qui sert à
 * rattraper un cours manqué, quand l'autre sert à préparer le suivant.
 */
data class SessionContent(
    val id: String,
    val subject: String,
    val date: LocalDate,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
)

/** Un document de l'espace numérique (bulletin, facture, pièce administrative). */
data class Document(
    val id: String,
    val name: String,
    val category: String = "",
    val date: LocalDate? = null,
    val type: String = "",
) {
    /**
     * Un document se télécharge par le même endpoint qu'une pièce jointe, mais son type
     * dépend de la catégorie dans laquelle l'établissement l'a rangé. La correspondance
     * ci-dessous couvre les catégories courantes ; pour les autres, le serveur refusera
     * proprement et l'interface affichera son message plutôt qu'un fichier illisible.
     */
    fun asAttachment(): Attachment = Attachment(
        id = id,
        name = name,
        type = type,
        source = AttachmentSource.RESOURCE,
        downloadType = when {
            category.contains("note", ignoreCase = true) -> "NOTE"
            category.contains("bulletin", ignoreCase = true) -> "NOTE"
            category.contains("administratif", ignoreCase = true) -> "ADMINISTRATIF"
            category.contains("facture", ignoreCase = true) -> "FACTURE"
            category.contains("scolaire", ignoreCase = true) -> "VIE_SCOLAIRE"
            else -> AttachmentSource.RESOURCE.apiType
        },
    )
}

/** Les familles de données synchronisées, chacune avec son état propre. */
enum class DataSection(val label: String) {
    TIMETABLE("Emploi du temps"),
    HOMEWORK("Devoirs"),
    GRADES("Notes"),
    MESSAGES("Messages"),
    SCHOOL_LIFE("Vie scolaire"),
    DOCUMENTS("Documents"),
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
    val schoolLife: List<SchoolLifeEvent> = emptyList(),
    val sessionContents: List<SessionContent> = emptyList(),
    val documents: List<Document> = emptyList(),
    /** Millisecondes epoch de la dernière synchronisation réussie, section par section. */
    val syncedAt: Map<DataSection, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = lessons.isEmpty() && homework.isEmpty() && grades.isEmpty() && messages.isEmpty() &&
            schoolLife.isEmpty() && documents.isEmpty()

    val lastSyncedAt: Long? get() = syncedAt.values.maxOrNull()
}
