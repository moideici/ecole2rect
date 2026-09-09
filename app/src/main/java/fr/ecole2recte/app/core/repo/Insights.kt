package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.settings.AppSettings
import java.time.Duration
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

    /**
     * Le prochain jour qui porte des cours, à partir du lendemain de [after].
     *
     * Un vendredi soir, « aucun cours » est une réponse inutile : ce qu'on veut savoir, c'est
     * ce qui attend lundi. Les cours annulés ne font pas un jour d'école.
     */
    fun nextLessonDay(lessons: List<Lesson>, after: LocalDate): LocalDate? =
        lessons.filterNot { it.canceled }
            .map { it.date }
            .filter { it.isAfter(after) }
            .minOrNull()

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

    /**
     * Les non-lus, corbeille exclue : ranger une conversation, c'est justement dire qu'on
     * ne veut plus la voir compter — dans l'onglet, dans les widgets, dans les notifications.
     */
    fun unreadMessages(messages: List<MessageItem>, settings: AppSettings): List<MessageItem> =
        messages.filterNot { settings.isMessageRead(it.id, it.read) || settings.isMessageBinned(it.id) }

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

    // ── Journée et semaine ───────────────────────────────────────────────────

    /** Ce qu'il faut savoir d'une journée avant même de dérouler la liste des cours. */
    data class DaySummary(
        val date: LocalDate,
        val lessons: Int,
        val minutes: Long,
        val start: LocalDateTime?,
        val end: LocalDateTime?,
        val exams: Int,
        val canceled: Int,
    ) {
        val isEmpty: Boolean get() = lessons == 0
    }

    fun daySummary(lessons: List<Lesson>, date: LocalDate): DaySummary {
        val day = lessonsOn(lessons, date)
        val active = day.filterNot { it.canceled }
        return DaySummary(
            date = date,
            lessons = active.size,
            minutes = active.sumOf { Duration.between(it.start, it.end).toMinutes() },
            start = active.minByOrNull { it.start }?.start,
            end = active.maxByOrNull { it.end }?.end,
            exams = day.count { it.exam },
            canceled = day.count { it.canceled },
        )
    }

    /** Les six jours d'une semaine scolaire, du lundi au samedi, résumés. */
    fun week(lessons: List<Lesson>, weekStart: LocalDate, days: Int = 6): List<DaySummary> =
        (0 until days).map { offset -> daySummary(lessons, weekStart.plusDays(offset.toLong())) }

    /**
     * Trous entre deux cours consécutifs, à partir d'un quart d'heure : c'est ce qui
     * distingue une journée de six heures d'une journée de six heures avec deux trous.
     */
    /** Un temps libre entre deux cours : ce que l'emploi du temps ne dit pas en toutes lettres. */
    data class Interval(val start: LocalDateTime, val end: LocalDateTime) {
        val minutes: Long get() = Duration.between(start, end).toMinutes()
        val middle: LocalDateTime get() = start.plusMinutes(minutes / 2)
    }

    /** Minutes communes à deux intervalles ; zéro s'ils ne se touchent pas. */
    private fun overlapMinutes(left: Interval, right: Interval): Long {
        val start = maxOf(left.start, right.start)
        val end = minOf(left.end, right.end)
        return if (end.isAfter(start)) Duration.between(start, end).toMinutes() else 0
    }

    /** Tous les temps libres d'une journée, cours annulés exclus. */
    private fun freeIntervals(lessons: List<Lesson>, date: LocalDate): List<Interval> =
        lessonsOn(lessons, date)
            .filterNot { it.canceled }
            .sortedBy { it.start }
            .zipWithNext()
            .map { (previous, next) -> Interval(previous.end, next.start) }
            .filter { it.minutes > 0 }

    /**
     * La pause méridienne.
     *
     * Elle n'est écrite nulle part dans la réponse d'ÉcoleDirecte : c'est un trou comme un
     * autre, qu'il faut reconnaître parmi les autres trous.
     *
     * **Le plus long trou n'est pas le bon critère**, et c'est l'erreur qui saute aux yeux
     * un jour de permanence : deux heures libres de 10 h à 12 h sont plus longues que le
     * déjeuner, et l'emportaient sur lui. Ce qui distingue le repas, ce n'est pas sa durée,
     * c'est **l'endroit de la journée qu'il occupe**. On garde donc le trou qui recouvre le
     * mieux le créneau où l'on mange, et non le plus large ; à recouvrement égal, celui dont
     * le milieu tombe le plus près de la mi-journée.
     *
     * Un trou qui n'entame ce créneau que de quelques minutes n'est pas un déjeuner : une
     * permanence qui finit à 11 h 40 reste une permanence.
     *
     * Rien n'est inventé quand il n'y a pas de trou : une journée continue n'a pas de pause,
     * et l'afficher à midi pile serait une supposition.
     */
    fun middayBreak(
        lessons: List<Lesson>,
        date: LocalDate,
        minimumMinutes: Long = 30,
    ): Interval? {
        val meal = Interval(date.atTime(11, 30), date.atTime(14, 0))
        // Le milieu géométrique du créneau tomberait à 12 h 45 ; un déjeuner réel penche
        // plus tôt. 12 h 30 départage donc mieux deux trous également plausibles.
        val noon = date.atTime(12, 30)
        return freeIntervals(lessons, date)
            .filter { it.minutes >= minimumMinutes && overlapMinutes(it, meal) >= MEAL_OVERLAP_MINUTES }
            .minWithOrNull(
                compareBy(
                    { -overlapMinutes(it, meal) },
                    { kotlin.math.abs(Duration.between(noon, it.middle).toMinutes()) },
                ),
            )
    }

    /** En deçà, le trou effleure l'heure du repas sans en être un. */
    private const val MEAL_OVERLAP_MINUTES = 25L

    fun gaps(lessons: List<Lesson>, minimumMinutes: Long = 15): List<Pair<Lesson, Long>> {
        val ordered = lessons.filterNot { it.canceled }.sortedBy { it.start }
        return ordered.zipWithNext()
            .map { (previous, next) -> next to Duration.between(previous.end, next.start).toMinutes() }
            .filter { (_, minutes) -> minutes >= minimumMinutes }
    }

    /** Devoirs à rendre pour un jour donné, cochés compris : l'agenda dit tout. */
    fun homeworkDueOn(homework: List<HomeworkItem>, date: LocalDate): List<HomeworkItem> =
        homework.filter { it.dueDate == date }.sortedBy { it.subject }

    /**
     * Devoirs à rendre pour un cours précis. C'est le croisement qui manque partout
     * ailleurs : voir « à rendre pour ce cours » dans la case de l'emploi du temps évite
     * d'ouvrir deux écrans pour préparer son sac.
     */
    fun homeworkForLesson(homework: List<HomeworkItem>, lesson: Lesson): List<HomeworkItem> =
        homeworkDueOn(homework, lesson.date)
            .filter { Text.normalizeKey(it.subject) == Text.normalizeKey(lesson.subject) }

    /** Évaluations annoncées à venir, la plus proche d'abord. */
    fun upcomingExams(
        homework: List<HomeworkItem>,
        settings: AppSettings,
        today: LocalDate,
        horizonDays: Long = 21,
    ): List<HomeworkItem> = pendingHomework(homework, settings, today, horizonDays)
        .filter { it.exam }

    // ── Notes ────────────────────────────────────────────────────────────────

    fun currentPeriod(periods: List<Period>, today: LocalDate): Period? =
        periods.firstOrNull { period ->
            val start = period.start
            val end = period.end
            start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
        } ?: periods.firstOrNull { it.current } ?: periods.firstOrNull()

    /**
     * Moyenne générale à afficher : celle de l'établissement si elle existe, sinon celle
     * calculée depuis les notes. Le drapeau dit laquelle, pour que l'interface ne fasse
     * jamais passer une estimation pour un bulletin.
     */
    data class Average(val value: Double, val official: Boolean, val periodName: String)

    fun generalAverage(snapshot: SchoolSnapshot, today: LocalDate): Average? {
        val period = currentPeriod(snapshot.periods, today)
        val grades = gradesForPeriod(snapshot.grades, period?.code)
        val official = period?.average
        val value = official ?: weightedAverage(grades) ?: return null
        return Average(value, official != null, period?.name.orEmpty())
    }

    /**
     * Un objectif de moyenne, et ce qu'il faudrait pour l'atteindre.
     *
     * Un écart seul — « il manque 0,2 » — ne dit pas quoi faire. Le levier le dit : dans
     * quelle matière une bonne note rapporterait le plus, et laquelle suffirait. C'est le
     * coefficient qui décide, pas la sympathie pour la matière.
     */
    data class Objective(
        val target: Double,
        val current: Double,
        /** Points manquants, zéro quand l'objectif est atteint. */
        val gap: Double,
        val lever: Lever?,
    ) {
        val reached: Boolean get() = gap <= 0.0
    }

    /** « un 17 en maths (coef. 4) suffit ». */
    data class Lever(val subject: String, val coefficient: Double, val mark: Double)

    fun objective(snapshot: SchoolSnapshot, today: LocalDate, target: Double?): Objective? {
        if (target == null) return null
        val period = currentPeriod(snapshot.periods, today)
        val grades = gradesForPeriod(snapshot.grades, period?.code)
            .filter { it.counted && it.value != null && it.scale > 0 && it.coefficient > 0 }
        val current = generalAverage(snapshot, today)?.value ?: return null
        val gap = (target - current).coerceAtLeast(0.0)
        return Objective(target = target, current = current, gap = gap, lever = lever(grades, target))
    }

    /**
     * La matière où une note de plus coûterait le moins cher.
     *
     * On simule l'ajout d'une note dans chaque matière, au coefficient qu'elle pratique déjà,
     * et on garde celle qui demande la note la plus basse — à condition qu'elle existe : une
     * matière qui exigerait un 23 sur 20 n'est pas un levier, c'est un mur.
     */
    private fun lever(grades: List<Grade>, target: Double): Lever? {
        if (grades.isEmpty()) return null
        val total = grades.sumOf { (it.outOfTwenty ?: 0.0) * it.coefficient }
        val weights = grades.sumOf { it.coefficient }
        if (weights <= 0.0) return null
        return grades.groupBy { it.subject }
            .mapNotNull { (subject, subjectGrades) ->
                val coefficient = subjectGrades.map { it.coefficient }.average()
                if (coefficient <= 0.0) return@mapNotNull null
                // (total + x × coef) / (poids + coef) = objectif
                val mark = (target * (weights + coefficient) - total) / coefficient
                if (mark > MAXIMUM_MARK) null else Lever(subject, coefficient, mark.coerceAtLeast(0.0))
            }
            .minByOrNull { it.mark }
    }

    private const val MAXIMUM_MARK = 20.0

    /**
     * Évolution de la moyenne au fil des notes.
     *
     * Chaque point est la moyenne pondérée de tout ce qui précède : c'est la courbe qu'on
     * trace mentalement en feuilletant un bulletin, et elle dit plus qu'un chiffre unique —
     * une moyenne de 13 qui monte n'est pas la même qu'une moyenne de 13 qui descend.
     */
    fun averageHistory(grades: List<Grade>): List<Pair<LocalDate, Double>> {
        val ordered = grades
            .filter { it.counted && it.value != null && it.date != null && it.scale > 0 }
            .sortedBy { it.date }
        val history = mutableListOf<Pair<LocalDate, Double>>()
        val seen = mutableListOf<Grade>()
        ordered.forEach { grade ->
            seen += grade
            weightedAverage(seen)?.let { average -> history += (grade.date ?: return@forEach) to average }
        }
        return history
    }

    /** Matières de la période, moyenne officielle en priorité, calculée à défaut. */
    fun subjectAverages(snapshot: SchoolSnapshot, today: LocalDate): List<Pair<String, Double>> {
        val period = currentPeriod(snapshot.periods, today)
        val official = snapshot.subjectAverages
            .filter { period == null || it.periodCode == period.code }
            .mapNotNull { average -> average.average?.let { average.subject to it } }
        if (official.isNotEmpty()) return official.sortedByDescending { it.second }
        return averagesBySubject(gradesForPeriod(snapshot.grades, period?.code))
            .toList()
            .sortedByDescending { it.second }
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
