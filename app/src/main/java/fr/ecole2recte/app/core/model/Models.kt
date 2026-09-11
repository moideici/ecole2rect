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
    /** Groupe ou demi-classe : deux élèves d'une même classe n'ont pas toujours le même cours. */
    val group: String = "",
    /** « Devoir surveillé », « Permanence », « Sortie » : ce que l'établissement a saisi. */
    val kind: String = "",
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
    /**
     * Le professeur attend une copie déposée en ligne. L'application ne sait pas déposer —
     * c'est irréversible et invérifiable sans compte réel — mais le taire serait pire : un
     * élève croirait un devoir rendu en le cochant.
     */
    val onlineSubmission: Boolean = false,
) {
    /** Vrai quand le contenu n'a pas encore été chargé par l'appel détaillé du jour. */
    val hasContent: Boolean get() = content.isNotBlank()

    /**
     * Vrai quand l'identifiant vient d'ÉcoleDirecte.
     *
     * Le cahier de textes ne porte pas toujours d'identifiant de devoir ; l'analyse en
     * fabrique alors un, reproductible, pour ne pas créer de doublon d'une synchronisation à
     * l'autre. Cet identifiant-là ne désigne **rien** côté serveur : il sert à ranger
     * localement, jamais à écrire. Les identifiants du service sont numériques, ce préfixe
     * ne peut donc pas en masquer un.
     */
    val idFromServer: Boolean get() = !id.startsWith(LOCAL_HOMEWORK_ID_PREFIX)

    companion object {
        /** Préfixe des identifiants fabriqués ici, par [fr.ecole2recte.app.core.parse.Parsers]. */
        const val LOCAL_HOMEWORK_ID_PREFIX = "hw-"
    }
}

/**
 * Une compétence évaluée en même temps qu'une note.
 *
 * Le socle commun se note par niveau, pas par point : une note de 12 accompagnée de
 * « Résoudre un problème : maîtrise fragile » dit deux choses différentes, et la seconde est
 * souvent la plus utile.
 */
data class Skill(
    val label: String,
    val level: String = "",
    /** 1 à 4 quand l'établissement code le niveau ; sert à colorer la pastille. */
    val value: Int? = null,
)

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
    /** Note la plus basse et la plus haute de la classe : une moyenne seule ne situe pas. */
    val minimum: Double? = null,
    val maximum: Double? = null,
    /** « Devoir surveillé », « Interrogation orale »… tel que le professeur l'a saisi. */
    val kind: String = "",
    val skills: List<Skill> = emptyList(),
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
    /** Appréciation générale du conseil de classe : le bulletin en une phrase. */
    val appreciation: String = "",
    val headTeacher: String = "",
    val councilDate: LocalDate? = null,
    val councilTime: String = "",
    /** Extrêmes de la classe sur la période, quand l'établissement les publie. */
    val minimum: Double? = null,
    val maximum: Double? = null,
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
    val minimum: Double? = null,
    val maximum: Double? = null,
    /** Une matière a parfois plusieurs professeurs : les nommer tous évite de choisir. */
    val teachers: List<String> = emptyList(),
    /** Matière mère d'une sous-matière (« Écrit » sous « Français »), vide sinon. */
    val parentSubject: String = "",
) {
    val isSubSubject: Boolean get() = parentSubject.isNotBlank()
}

/**
 * Les boîtes de la messagerie.
 *
 * ÉcoleDirecte range les messages en trois dossiers, et l'application n'en lisait qu'un :
 * un message envoyé depuis le site n'existait tout simplement pas ici.
 */
/**
 * Les trois dossiers de la messagerie.
 *
 * [ARCHIVED] s'appelle « Corbeille » dans l'application : ÉcoleDirecte ne sait pas supprimer
 * une conversation, et l'application ne le fera pas semblant. La Corbeille range les messages
 * archivés côté serveur et ceux qu'on y met soi-même, sans que rien ne parte au serveur.
 */
enum class MessageFolder(val label: String, val apiValue: String) {
    RECEIVED("Reçus", "received"),
    SENT("Envoyés", "sent"),
    ARCHIVED("Corbeille", "archived"),
}

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
    val folder: MessageFolder = MessageFolder.RECEIVED,
    /** Destinataires, pour les messages envoyés — « à qui ai-je écrit, déjà ? ». */
    val recipients: List<String> = emptyList(),
) {
    val hasBody: Boolean get() = body.isNotBlank()

    /** Qui s'affiche en tête de ligne : l'expéditeur, ou les destinataires si c'est nous. */
    val counterpart: String
        get() = if (folder == MessageFolder.SENT && recipients.isNotEmpty()) {
            recipients.joinToString(", ")
        } else {
            sender
        }
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
    /** Durée telle que l'établissement la compte (« 2h », « 1 demi-journée »). */
    val duration: String = "",
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

/**
 * Une actualité de l'établissement.
 *
 * Le module « vie de l'établissement » porte ce que l'école adresse à tout le monde :
 * sorties, réunions, fermetures, menus. C'est souvent là que se trouve l'information qu'un
 * élève apprend en dernier.
 */
data class NewsItem(
    val id: String,
    val title: String,
    val content: String = "",
    val date: LocalDate? = null,
    val author: String = "",
    val category: String = "",
    val attachments: List<Attachment> = emptyList(),
)

/** Un manuel numérique mis à disposition par l'établissement. */
data class Textbook(
    val id: String,
    val title: String,
    val subject: String = "",
    val publisher: String = "",
    val url: String = "",
) {
    val hasLink: Boolean get() = url.startsWith("http")
}

/**
 * Un fichier de l'espace de stockage.
 *
 * L'arborescence du cloud ÉcoleDirecte est aplatie ici : ce qui intéresse, c'est de
 * retrouver un fichier et de l'ouvrir, pas de reproduire un explorateur.
 */
data class CloudFile(
    val id: String,
    val name: String,
    val folder: String = "",
    val date: LocalDate? = null,
    val size: Long? = null,
    /** Identifiant d'espace exigé par le téléchargement, quand l'API le fournit. */
    val ownerId: String = "",
) {
    fun asAttachment(): Attachment = Attachment(
        id = id,
        name = name,
        source = AttachmentSource.RESOURCE,
        downloadType = "CLOUD",
    )
}

/**
 * Les familles de données synchronisées, chacune avec son état propre.
 *
 * Les sections facultatives correspondent à des modules qu'un établissement peut ne pas
 * ouvrir. Les demander toutes les heures en arrière-plan coûterait trois requêtes refusées
 * par synchronisation : elles sont donc chargées à l'ouverture de leur écran.
 */
enum class DataSection(val label: String, val optional: Boolean = false) {
    TIMETABLE("Emploi du temps"),
    HOMEWORK("Devoirs"),
    GRADES("Notes"),
    MESSAGES("Messages"),
    SCHOOL_LIFE("Vie scolaire"),
    DOCUMENTS("Documents"),
    NEWS("Actualités", optional = true),
    TEXTBOOKS("Manuels numériques", optional = true),
    CLOUD("Espace de stockage", optional = true),
    ;

    companion object {
        /** Ce qui est synchronisé systématiquement, en fond comme au lancement. */
        val ESSENTIAL: Set<DataSection> get() = entries.filterNot { it.optional }.toSet()
    }
}

/** Ce qu'un changement peut être. L'ordre décide de la priorité d'affichage. */
enum class ChangeKind(val label: String) {
    LESSON_CANCELED("Cours annulé"),
    LESSON_MOVED("Cours déplacé"),
    LESSON_ADDED("Cours ajouté"),
    LESSON_REMOVED("Cours retiré"),
    GRADE("Note"),
    HOMEWORK("Devoir"),
    MESSAGE("Message"),
    SCHOOL_LIFE("Vie scolaire"),
}

/**
 * Un fait nouveau depuis la dernière synchronisation.
 *
 * [id] est stable : c'est lui qui garantit qu'un même changement n'apparaît qu'une fois dans
 * l'historique, même si dix synchronisations le retrouvent.
 */
data class Change(
    val id: String,
    val kind: ChangeKind,
    val text: String,
    val detail: String = "",
    val subject: String = "",
    /** Jour où le changement a été repéré : c'est lui qui groupe la liste. */
    val noticedOn: LocalDate,
    /** Instant du repérage, pour l'ordre et pour « il y a vingt minutes ». */
    val atMillis: Long,
)

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
    val news: List<NewsItem> = emptyList(),
    val textbooks: List<Textbook> = emptyList(),
    val cloudFiles: List<CloudFile> = emptyList(),
    /**
     * Ce qui a changé depuis les dernières synchronisations, le plus récent d'abord.
     *
     * L'historique vit dans l'instantané parce qu'il se calcule à partir de lui et se relit
     * avec lui : widgets et notifications n'ont ainsi rien de plus à charger.
     */
    val changes: List<Change> = emptyList(),
    /** Millisecondes epoch de la dernière synchronisation réussie, section par section. */
    val syncedAt: Map<DataSection, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = lessons.isEmpty() && homework.isEmpty() && grades.isEmpty() && messages.isEmpty() &&
            schoolLife.isEmpty() && documents.isEmpty()

    val lastSyncedAt: Long? get() = syncedAt.values.maxOrNull()

    /**
     * Les messages reçus, seuls concernés par les compteurs de non-lus, les notifications
     * et les widgets : un message envoyé n'est pas une nouvelle à signaler.
     */
    val inbox: List<MessageItem> get() = messages.filter { it.folder == MessageFolder.RECEIVED }
}
