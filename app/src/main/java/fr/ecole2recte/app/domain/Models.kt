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
)

/** Un cours dans le résumé de la journée. */
data class ScheduleSlot(val time: String, val subject: Subject)

/** Un cours de l'emploi du temps, avec son créneau déjà formaté. */
data class ScheduleEntry(val subject: Subject, val whenLabel: String, val canceled: Boolean = false)

data class ScheduleWeek(val label: String, val entries: List<ScheduleEntry>)

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

data class SubjectAverage(val subject: Subject, val average: String)

data class GradeOverview(val average: String, val trend: String, val bySubject: List<SubjectAverage>)

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
