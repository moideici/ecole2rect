package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.settings.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Les questions que l'accueil pose aux données.
 *
 * « Quel est le prochain cours ? », « Qu'est-ce qui est urgent ? », « Ai-je progressé ? » :
 * ce sont des règles produit, pas de l'affichage. Elles vivent donc ici, en fonctions pures
 * et testables, plutôt que dispersées dans des composables.
 */
object Insights {

    /** Le cours en cours s'il y en a un, sinon le prochain à venir. */
    fun currentOrNextLesson(lessons: List<Lesson>, now: LocalDateTime): Lesson? =
        lessons.filterNot { it.canceled }
            .sortedBy { it.start }
            .firstOrNull { !it.end.isBefore(now) }

    fun lessonsOn(lessons: List<Lesson>, date: LocalDate): List<Lesson> =
        lessons.filter { it.date == date }.sortedBy { it.start }

    /** Jours de la semaine qui portent au moins un cours — les autres n'ont pas d'onglet. */
    fun daysWithLessons(lessons: List<Lesson>, from: LocalDate, to: LocalDate): List<LocalDate> =
        lessons.map { it.date }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .distinct()
            .sorted()

    /**
     * Devoirs à faire, du plus urgent au plus lointain, en gardant les retards en tête :
     * un devoir dépassé et non coché reste la première chose à voir.
     */
    fun pendingHomework(
        homework: List<HomeworkItem>,
        settings: AppSettings,
        today: LocalDate,
        horizonDays: Long = 30,
    ): List<HomeworkItem> = homework
        .filterNot { settings.isHomeworkDone(it.id, it.done) }
        .filter { !it.dueDate.isAfter(today.plusDays(horizonDays)) }
        .sortedWith(compareBy({ it.dueDate }, { it.subject }))

    /** Le devoir mis en avant : le plus proche, en privilégiant les évaluations. */
    fun urgentHomework(
        homework: List<HomeworkItem>,
        settings: AppSettings,
        today: LocalDate,
    ): HomeworkItem? = pendingHomework(homework, settings, today)
        .minWithOrNull(compareBy({ it.dueDate }, { if (it.exam) 0 else 1 }))

    fun unreadMessages(messages: List<MessageItem>, settings: AppSettings): List<MessageItem> =
        messages.filterNot { settings.isMessageRead(it.id, it.read) }

    /** Notes de la période, les plus récentes d'abord. */
    fun gradesForPeriod(grades: List<Grade>, periodCode: String?): List<Grade> =
        grades.filter { periodCode == null || it.periodCode == periodCode }

    /**
     * Moyenne pondérée, ramenée sur 20.
     *
     * L'établissement calcule la sienne avec ses propres règles : quand l'API la fournit,
     * c'est elle qui est affichée. Ce calcul ne sert que de repli, et il est signalé comme
     * tel dans l'interface pour ne pas faire passer une estimation pour un bulletin.
     */
    fun weightedAverage(grades: List<Grade>): Double? {
        val usable = grades.filter { it.counted && it.value != null && it.scale > 0 && it.coefficient > 0 }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { (it.outOfTwenty ?: 0.0) * it.coefficient }
        val weights = usable.sumOf { it.coefficient }
        if (weights <= 0) return null
        return total / weights
    }

    /** Moyennes par matière calculées depuis les notes, quand l'API n'en donne pas. */
    fun averagesBySubject(grades: List<Grade>): Map<String, Double> = grades
        .groupBy { it.subject }
        .mapNotNull { (subject, subjectGrades) -> weightedAverage(subjectGrades)?.let { subject to it } }
        .toMap()

    /**
     * Écart entre les dernières notes et les précédentes, en points sur 20 : la petite
     * flèche « ↗ +1,2 » de l'accueil. Null quand il n'y a pas assez de matière pour comparer.
     */
    fun trend(grades: List<Grade>, recentCount: Int = 5): Double? {
        val ordered = grades.filter { it.counted && it.value != null }
            .sortedByDescending { it.date ?: LocalDate.MIN }
        if (ordered.size < recentCount * 2) return null
        val recent = weightedAverage(ordered.take(recentCount)) ?: return null
        val previous = weightedAverage(ordered.drop(recentCount).take(recentCount)) ?: return null
        return recent - previous
    }

    /** Matières connues, tous écrans confondus : sert à l'éditeur de couleurs. */
    fun knownSubjects(snapshot: SchoolSnapshot): List<String> =
        (snapshot.lessons.map { it.subject } +
            snapshot.homework.map { it.subject } +
            snapshot.grades.map { it.subject } +
            snapshot.subjectAverages.map { it.subject })
            .filter { it.isNotBlank() }
            .distinctBy { Text.normalizeKey(it) }
            .sorted()

    /** Recherche simple sur plusieurs champs, sans tenir compte des accents ni de la casse. */
    fun matches(query: String, vararg fields: String): Boolean {
        val needle = Text.normalizeKey(query)
        if (needle.isEmpty()) return true
        return fields.any { Text.normalizeKey(it).contains(needle) }
    }
}
