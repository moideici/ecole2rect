package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.SchoolLifeKind
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

    /**
     * Ce que les dernières notes valent réellement sur la moyenne.
     *
     * [trend] compare deux paquets de notes **entre eux** : c'est la flèche « ↗ » de
     * l'accueil, et elle ne répond pas à la question « qu'est-ce que ces notes m'ont
     * rapporté ». Confondre les deux gonfle le chiffre d'un facteur deux, parce qu'un écart
     * entre deux paquets n'est pas un effet sur l'ensemble. Ici la réponse est exacte : la
     * moyenne telle qu'elle est, moins la moyenne qu'elle serait sans ces notes-là.
     */
    fun recentEffect(grades: List<Grade>, recentCount: Int = 5): Double? {
        val ordered = usableGrades(grades).sortedByDescending { it.date ?: LocalDate.MIN }
        // Sans note antérieure, il n'y a pas de « moyenne sans elles » à comparer.
        if (ordered.size <= recentCount) return null
        val overall = weightedAverage(ordered) ?: return null
        val without = weightedAverage(ordered.drop(recentCount)) ?: return null
        return overall - without
    }

    /** Les notes qui entrent dans un calcul de moyenne : partout le même filtre. */
    private fun usableGrades(grades: List<Grade>): List<Grade> =
        grades.filter { it.counted && it.value != null && it.scale > 0 && it.coefficient > 0 }

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
    fun freeIntervals(lessons: List<Lesson>, date: LocalDate): List<Interval> =
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

    /**
     * Un trou de la journée, et ce qu'on pourrait y faire.
     *
     * [homework] est nul quand rien ne s'y prête : un trou sans devoir à y mettre reste un
     * trou, et le dire vaut mieux que de recycler le même devoir dans les trois créneaux.
     */
    data class WorkSlot(val interval: Interval, val homework: HomeworkItem?, val midday: Boolean)

    /**
     * Un plan de travail dans les trous de la journée.
     *
     * L'emploi du temps montre les blancs ; il ne dit pas quoi en faire. Ici chaque trou
     * assez long reçoit **un** devoir, du plus urgent au plus lointain, et jamais deux fois
     * le même : une permanence de deux heures et un trou de trente minutes ne peuvent pas
     * accueillir le même exercice.
     *
     * Ce que ce calcul **ne fait pas** : estimer combien de temps prend un devoir.
     * ÉcoleDirecte ne le dit pas, et l'inventer donnerait des durées fausses la moitié du
     * temps. On s'appuie sur le seul indice que le service fournit — une évaluation demande
     * plus de temps qu'un exercice — pour réserver les longs créneaux aux révisions, et on
     * affiche la durée du trou pour que l'élève juge lui-même.
     *
     * La pause méridienne est signalée sans se voir attribuer de devoir : déjeuner est aussi
     * une occupation, et un plan de travail qui prend l'heure du repas ne sera pas suivi.
     */
    fun workPlan(
        lessons: List<Lesson>,
        homework: List<HomeworkItem>,
        settings: AppSettings,
        date: LocalDate,
        minimumMinutes: Long = USABLE_GAP_MINUTES,
    ): List<WorkSlot> {
        val free = freeIntervals(lessons, date).filter { it.minutes >= minimumMinutes }
        if (free.isEmpty()) return emptyList()
        val midday = middayBreak(lessons, date)
        val pending = pendingHomework(homework, settings, date).toMutableList()

        return free.map { interval ->
            if (interval == midday) return@map WorkSlot(interval, null, midday = true)
            // Les longs créneaux d'abord aux révisions : c'est le seul indice de durée que
            // le service donne, et il vaut mieux que rien.
            val wanted = if (interval.minutes >= LONG_GAP_MINUTES) {
                pending.firstOrNull { it.exam } ?: pending.firstOrNull()
            } else {
                pending.firstOrNull { !it.exam } ?: pending.firstOrNull()
            }
            if (wanted != null) pending.remove(wanted)
            WorkSlot(interval, wanted, midday = false)
        }
    }

    /** En deçà, un trou ne sert qu'à changer de salle. */
    private const val USABLE_GAP_MINUTES = 25L

    /** Au-delà, il y a la place de réviser plutôt que d'expédier un exercice. */
    private const val LONG_GAP_MINUTES = 55L

    fun gaps(lessons: List<Lesson>, minimumMinutes: Long = 15): List<Pair<Lesson, Long>> {
        val ordered = lessons.filterNot { it.canceled }.sortedBy { it.start }
        return ordered.zipWithNext()
            .map { (previous, next) -> next to Duration.between(previous.end, next.start).toMinutes() }
            .filter { (_, minutes) -> minutes >= minimumMinutes }
    }

    /**
     * Ce qu'il faut emporter : les matières de la journée, et rien de plus.
     *
     * La tentation serait d'écrire « manuel de SVT, calculatrice, tenue d'EPS ». L'application
     * ne sait rien de tout cela : elle connaît les matières, pas le contenu des cartables.
     * Lister les matières est utile et vrai ; inventer une liste de fournitures serait faux
     * la moitié du temps, et une liste fausse ne se relit pas deux fois.
     */
    fun satchel(lessons: List<Lesson>): List<String> =
        lessons.filterNot { it.canceled }.map { it.subject }.distinct()

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
     * Ce que chaque note a fait à la moyenne générale, en points sur 20.
     *
     * La question que tout le monde se pose devant un bulletin — « ce 8 en histoire, ça m'a
     * coûté combien ? » — et à laquelle un coefficient seul ne répond pas : un 8 de coefficient
     * 1 dans une matière où l'on a déjà dix notes ne pèse presque rien, un 8 de coefficient 4
     * dans une période qui en compte cinq pèse lourd.
     *
     * La réponse est exacte, pas estimée : c'est la différence entre la moyenne telle qu'elle
     * est et la moyenne qu'elle serait si cette note n'existait pas.
     */
    fun impacts(grades: List<Grade>): Map<String, Double> {
        val usable = grades.filter { it.counted && it.value != null && it.scale > 0 && it.coefficient > 0 }
        // Avec une seule note, la question n'a pas de sens : il n'y a pas de « sans elle ».
        if (usable.size < 2) return emptyMap()
        val overall = weightedAverage(usable) ?: return emptyMap()
        return usable.associate { grade ->
            val without = weightedAverage(usable.filterNot { it.id == grade.id }) ?: overall
            grade.id to (overall - without)
        }
    }

    /**
     * Un objectif de moyenne, et ce qu'il faudrait pour l'atteindre.
     *
     * Un écart seul — « il manque 0,2 » — ne dit pas quoi faire. Le levier le dit : dans
     * quelle matière une bonne note rapporterait le plus, et laquelle suffirait. C'est le
     * coefficient qui décide, pas la sympathie pour la matière.
     *
     * L'écart et le levier se calculent sur **la même** base : les notes de la période. Les
     * mélanger — l'écart pris sur la moyenne du bulletin, le levier résolu sur les notes —
     * fait dire à la même carte deux choses contradictoires, « il manque 0,2 » au-dessus d'un
     * conseil calculé sur un socle où l'objectif est déjà dépassé. Quand l'établissement
     * publie sa moyenne, [official] la porte : c'est un repère à afficher, pas le socle.
     */
    data class Objective(
        val target: Double,
        val current: Double,
        /** Points manquants, zéro quand l'objectif est atteint. */
        val gap: Double,
        val lever: Lever?,
        /** La moyenne du bulletin, quand l'établissement la publie. */
        val official: Double? = null,
    ) {
        val reached: Boolean get() = gap <= 0.0
    }

    /** « un 17 en maths (coef. 4) suffit ». */
    data class Lever(val subject: String, val coefficient: Double, val mark: Double)

    fun objective(snapshot: SchoolSnapshot, today: LocalDate, target: Double?): Objective? {
        if (target == null) return null
        val period = currentPeriod(snapshot.periods, today)
        val grades = usableGrades(gradesForPeriod(snapshot.grades, period?.code))
        val current = weightedAverage(grades) ?: return null
        val gap = (target - current).coerceAtLeast(0.0)
        return Objective(
            target = target,
            current = current,
            gap = gap,
            lever = lever(grades, target),
            official = period?.average,
        )
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

    /**
     * Ce que l'on sait d'un professeur.
     *
     * ÉcoleDirecte ne publie pas de fiche : le nom apparaît sur les cours et sur les
     * moyennes, et c'est tout. On rassemble donc ce qui le concerne à partir de ces deux
     * sources, et le lien vers les devoirs et les notes passe par la **matière** — le
     * service ne dit pas qui a donné un devoir. L'écran le formule comme tel : « les devoirs
     * de ses matières », et non « les devoirs qu'il a donnés », qui serait une affirmation
     * que rien ne soutient.
     */
    data class TeacherProfile(
        val name: String,
        val subjects: List<String>,
        val rooms: List<String>,
        val lessonsThisWeek: Int,
        val minutesThisWeek: Long,
        /** Professeur principal de la période en cours. */
        val headTeacher: Boolean,
    )

    /** Tous les professeurs connus, par ordre alphabétique. */
    fun teachers(snapshot: SchoolSnapshot, today: LocalDate): List<TeacherProfile> {
        val fromLessons = snapshot.lessons.map { it.teacher }
        val fromAverages = snapshot.subjectAverages
            .flatMap { average -> average.teachers + average.teacher }
        return (fromLessons + fromAverages)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { teacherProfile(snapshot, today, it) }
    }

    fun teacherProfile(snapshot: SchoolSnapshot, today: LocalDate, name: String): TeacherProfile {
        val key = Text.normalizeKey(name)
        val week = Dates.startOfWeek(today)
        val mine = snapshot.lessons.filter { Text.normalizeKey(it.teacher) == key }
        // La semaine en cours seulement : additionner des semaines chargées au hasard
        // donnerait un « par semaine » qui n'en est pas un.
        val thisWeek = mine.filter { !it.date.isBefore(week) && it.date.isBefore(week.plusWeeks(1)) }
            .filterNot { it.canceled }
        val subjects = (
            mine.map { it.subject } +
                snapshot.subjectAverages
                    .filter { average -> (average.teachers + average.teacher).any { Text.normalizeKey(it) == key } }
                    .map { it.subject }
            ).filter { it.isNotBlank() }.distinct().sorted()
        return TeacherProfile(
            name = name,
            subjects = subjects,
            rooms = mine.map { it.room }.filter { it.isNotBlank() }.distinct().sorted(),
            lessonsThisWeek = thisWeek.size,
            minutesThisWeek = thisWeek.sumOf { Duration.between(it.start, it.end).toMinutes() },
            headTeacher = snapshot.periods.any { Text.normalizeKey(it.headTeacher) == key },
        )
    }

    /** Les cours d'un professeur dans la semaine affichée, du lundi au samedi. */
    fun lessonsOfTeacher(lessons: List<Lesson>, name: String, weekStart: LocalDate): List<Lesson> {
        val key = Text.normalizeKey(name)
        return lessons
            .filter { Text.normalizeKey(it.teacher) == key }
            .filter { !it.date.isBefore(weekStart) && it.date.isBefore(weekStart.plusWeeks(1)) }
            .sortedBy { it.start }
    }

    /**
     * L'année en chiffres.
     *
     * Un bilan que le service ne donne nulle part et qu'on ne peut pas faire de tête. Chaque
     * chiffre porte sa portée réelle, et c'est le point délicat : l'emploi du temps ne se
     * charge plus qu'une semaine à la fois, donc « des heures de cours depuis septembre »
     * serait un mensonge. Ce qui relève de la semaine est nommé comme tel ; le reste — notes,
     * devoirs, vie scolaire, messagerie — couvre bien l'année, parce que le service le donne
     * en entier.
     */
    data class YearStats(
        /** Notes qui entrent dans la moyenne : le même ensemble que [average]. */
        val gradeCount: Int,
        /** Notes publiées mais hors moyenne — « non significatif ». Les taire fausse le total. */
        val uncountedGrades: Int,
        val bestSubject: Pair<String, Double>?,
        val weakestSubject: Pair<String, Double>?,
        val average: Double?,
        val trend: Double?,
        /** Effet réel des cinq dernières notes sur la moyenne, pas un écart entre paquets. */
        val recentEffect: Double?,
        val homeworkDone: Int,
        val homeworkTotal: Int,
        val examCount: Int,
        val absences: Int,
        val unjustifiedAbsences: Int,
        val late: Int,
        val messagesReceived: Int,
        val messagesSent: Int,
        /** Cours et minutes de la semaine en cours : la seule qu'on soit sûr d'avoir. */
        val lessonsThisWeek: Int,
        val minutesThisWeek: Long,
        val busiestDay: Pair<LocalDate, Long>?,
    )

    fun yearStats(snapshot: SchoolSnapshot, settings: AppSettings, today: LocalDate): YearStats {
        // Un seul ensemble : compter 40 notes sous une moyenne qui n'en utilise que 35
        // donne deux chiffres qui ne parlent pas de la même chose.
        val graded = usableGrades(snapshot.grades)
        val subjects = averagesBySubject(graded).toList()
        val week = week(snapshot.lessons, Dates.startOfWeek(today))
        return YearStats(
            gradeCount = graded.size,
            uncountedGrades = snapshot.grades.count { it.value != null } - graded.size,
            bestSubject = subjects.maxByOrNull { it.second },
            weakestSubject = subjects.minByOrNull { it.second },
            average = weightedAverage(graded),
            trend = trend(graded),
            recentEffect = recentEffect(graded),
            homeworkDone = snapshot.homework.count { settings.isHomeworkDone(it.id, it.done) },
            homeworkTotal = snapshot.homework.size,
            examCount = snapshot.homework.count { it.exam },
            absences = snapshot.schoolLife.count { it.kind == SchoolLifeKind.ABSENCE },
            unjustifiedAbsences = snapshot.schoolLife
                .count { it.kind == SchoolLifeKind.ABSENCE && !it.justified },
            late = snapshot.schoolLife.count { it.kind == SchoolLifeKind.LATE },
            messagesReceived = snapshot.messages.count { it.folder == MessageFolder.RECEIVED },
            messagesSent = snapshot.messages.count { it.folder == MessageFolder.SENT },
            lessonsThisWeek = week.sumOf { it.lessons },
            minutesThisWeek = week.sumOf { it.minutes },
            busiestDay = week.filterNot { it.isEmpty }.maxByOrNull { it.minutes }
                ?.let { it.date to it.minutes },
        )
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
