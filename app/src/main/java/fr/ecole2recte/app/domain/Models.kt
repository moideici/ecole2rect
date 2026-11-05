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

enum class HighlightKind { Canceled, Exam, Grade, Message }

/** Une chose à savoir dès l'ouverture : cours annulé, évaluation proche, nouvelle note, message. */
data class Highlight(val kind: HighlightKind, val title: String, val detail: String)

data class SubjectAverage(
    val subject: Subject,
    val average: String,
    val classAverage: String? = null,
    val grades: List<GradeEntry> = emptyList(),
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
)

data class MessagePreview(val sender: String, val preview: String, val unread: Boolean = true)

/**
 * Ce que l'on sait de l'âge des données affichées. [stale] est vrai quand le réseau a échoué
 * et que l'écran montre la dernière réponse enregistrée, datée de [lastSyncMillis].
 */
data class Freshness(val lastSyncMillis: Long? = null, val stale: Boolean = false)

/** Compteurs affichés en pastille sur les onglets. */
data class Badges(val newGrades: Int = 0, val urgentHomework: Int = 0, val unreadMessages: Int = 0)

data class HomeData(
    val greeting: String,
    val dateLabel: String,
    /** Absent quand il n'y a plus de cours à venir. */
    val nextClass: NextClass?,
    val today: List<ScheduleSlot>,
    val reminder: Homework?,
    val highlights: List<Highlight> = emptyList(),
    val grades: GradeOverview,
    val allSubjects: List<Subject>,
)
