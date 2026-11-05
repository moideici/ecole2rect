package fr.ecole2recte.app.domain

/**
 * Modèles propres à l'application. Ils ne dépendent ni de Compose ni du format du serveur :
 * le réseau (lot 7) et le cache (lot 8) les produiront, l'interface se contente de les afficher.
 */
data class Subject(val name: String, val teacher: String, val room: String, val colorIndex: Int)

data class Homework(val title: String, val subject: String, val due: String, val colorIndex: Int)

/** Un cours dans le résumé de la journée. */
data class ScheduleSlot(val time: String, val subject: Subject)

/** Un cours de l'emploi du temps, avec son créneau déjà formaté. */
data class ScheduleEntry(val subject: Subject, val whenLabel: String)

data class ScheduleWeek(val label: String, val entries: List<ScheduleEntry>)

data class NextClass(val subject: Subject, val startLabel: String, val progress: Float)

data class SubjectAverage(val subject: Subject, val average: String)

data class GradeOverview(val average: String, val trend: String, val bySubject: List<SubjectAverage>)

data class MessagePreview(val sender: String, val preview: String, val unread: Boolean = true)

/** Compteurs affichés en pastille sur les onglets. */
data class Badges(val newGrades: Int = 0, val urgentHomework: Int = 0, val unreadMessages: Int = 0)

data class HomeData(
    val greeting: String,
    val dateLabel: String,
    val nextClass: NextClass,
    val today: List<ScheduleSlot>,
    val reminder: Homework?,
    val grades: GradeOverview,
    val allSubjects: List<Subject>,
)
