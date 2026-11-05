package fr.ecole2recte.app.domain

/**
 * Modèles propres à l'application. Ils ne dépendent ni de Compose ni du format du serveur :
 * le réseau (lot 7) et le cache (lot 8) les produiront, l'interface se contente de les afficher.
 */
data class Subject(val name: String, val teacher: String, val room: String, val colorIndex: Int)

data class Homework(
    val title: String,
    val subject: String,
    val due: String,
    val colorIndex: Int,
    val id: String = "",
    val dueDate: String = "",
    val done: Boolean = false,
    val exam: Boolean = false,
    val content: String = "",
)

/** Le contenu complet d'un devoir, chargé à l'ouverture de sa fiche. */
data class HomeworkDetail(
    val content: String,
    val attachments: List<String> = emptyList(),
    /** Ce qui a été vu en cours ce jour-là dans la même matière. */
    val lessonContent: String = "",
)

/** Un cours dans le résumé de la journée. */
data class ScheduleSlot(val time: String, val subject: Subject)

/** Un cours de l'emploi du temps, avec son créneau déjà formaté. */
data class ScheduleEntry(
    val subject: Subject,
    val whenLabel: String,
    val canceled: Boolean = false,
    /** Jour du cours, `2026-11-05`. */
    val date: String = "",
    /** Heures locales `10:15`. */
    val start: String = "",
    val end: String = "",
    val exam: Boolean = false,
    val note: String = "",
    /** ÉcoleDirecte signale le cours comme modifié (salle, professeur, horaire). */
    val modified: Boolean = false,
    /** `typeCours` d'ÉcoleDirecte : COURS, PERMANENCE… */
    val kind: String = "",
)

/**
 * Les cours de la semaine en cours et de la suivante. [weekStart] est le lundi de la première,
 * [today] le jour affiché par défaut ; les deux sont des dates ISO.
 */
data class ScheduleWeek(
    val label: String,
    val entries: List<ScheduleEntry>,
    val weekStart: String = "",
    val today: String = "",
)

data class NextClass(
    val subject: Subject,
    val startLabel: String,
    val progress: Float,
    /** Le cours d'après, pour répondre à « et ensuite ? » sans changer d'écran. */
    val afterLabel: String? = null,
)

enum class HighlightKind { Canceled, Changed, Exam, Message, Grade }

/** Une chose à savoir dès l'ouverture : cours annulé, évaluation proche, nouvelle note, message. */
data class Highlight(
    val kind: HighlightKind,
    val title: String,
    val detail: String,
    /** Valeur mise en avant à droite (la note), avec son complément en petit (le coefficient). */
    val value: String? = null,
    val valueSub: String? = null,
)

data class SubjectAverage(
    val subject: Subject,
    val average: String,
    val classAverage: String? = null,
    val grades: List<GradeEntry> = emptyList(),
    val coefficient: Double? = null,
)

data class GradeOverview(
    val average: String,
    val trend: String,
    val bySubject: List<SubjectAverage>,
    /** Les notes les plus récentes, toutes périodes confondues. */
    val recent: List<GradeEntry> = emptyList(),
    /** Moyenne générale de chaque période, pour la courbe d'évolution. */
    val evolution: List<PeriodAverage> = emptyList(),
    val classAverage: String? = null,
    /** Nom de la période affichée (« 1er trimestre »). */
    val periodName: String = "",
)

data class MessagePreview(
    val sender: String,
    val preview: String,
    val unread: Boolean = true,
    val id: String = "",
    /** « 10:15 » aujourd'hui, sinon « 4 nov. ». */
    val dateLabel: String = "",
)

data class MessagesPage(val items: List<MessagePreview>, val hasMore: Boolean)

data class MessageDetail(
    val subject: String,
    val sender: String,
    val dateLabel: String,
    val body: String,
    val recipients: List<String>,
    val attachments: List<String>,
)

/**
 * Ce que l'on sait de l'âge des données affichées. [stale] est vrai quand le réseau a échoué
 * et que l'écran montre la dernière réponse enregistrée, datée de [lastSyncMillis].
 */
data class Freshness(val lastSyncMillis: Long? = null, val stale: Boolean = false, val cause: StaleCause? = null)

/** Pourquoi l'écran montre une réponse enregistrée plutôt qu'une réponse toute fraîche. */
enum class StaleCause(val label: String) {
    Network("pas de réseau"),
    Service("ÉcoleDirecte ne répond pas"),
    Expired("la session a expiré"),
    Blocked("compte bloqué par ÉcoleDirecte"),
    StaleVersion("version d'API refusée (517)"),
    Unusable("réponse d'ÉcoleDirecte inexploitable"),
    Other("lecture impossible"),
}

/** Un enfant d'un compte famille, résumé pour la vue d'ensemble de l'accueil. */
data class ChildSummary(
    val studentId: String,
    val name: String,
    val initials: String,
    val className: String,
    /** « Maths · 10:15 · dans 25 min », ou rien s'il n'y a plus de cours. */
    val nextClass: String?,
    val urgentHomework: Int,
    val newGrades: Int,
    val average: String?,
    val selected: Boolean,
)

enum class SchoolLifeKind { Absence, Late, Sanction, Encouragement, Other }

data class SchoolLifeEvent(
    val kind: SchoolLifeKind,
    val title: String,
    val dateLabel: String,
    val detail: String,
    val justified: Boolean,
)

/** Absences, retards, sanctions et encouragements, décomptés comme l'établissement les compte. */
data class SchoolLifeSummary(
    val absences: Int = 0,
    val unjustified: Int = 0,
    val lates: Int = 0,
    val sanctions: Int = 0,
    val encouragements: Int = 0,
    val events: List<SchoolLifeEvent> = emptyList(),
)

/** Compteurs affichés en pastille sur les onglets. */
data class Badges(val newGrades: Int = 0, val urgentHomework: Int = 0, val unreadMessages: Int = 0)

data class HomeData(
    val greeting: String,
    val dateLabel: String,
    /** Absent quand il n'y a plus de cours à venir. */
    val nextClass: NextClass?,
    /** Les cours de la journée, annulés compris, pour la frise de l'accueil. */
    val today: List<ScheduleEntry>,
    val reminder: Homework?,
    val highlights: List<Highlight> = emptyList(),
    /** Un résumé par enfant pour un compte famille ; vide pour un élève. */
    val family: List<ChildSummary> = emptyList(),
    val grades: GradeOverview,
    val allSubjects: List<Subject>,
)
