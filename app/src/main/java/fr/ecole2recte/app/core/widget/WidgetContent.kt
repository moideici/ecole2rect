package fr.ecole2recte.app.core.widget

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.SubjectPalette
import java.time.Duration
import java.time.LocalDateTime

/**
 * Le contenu des widgets d'écran d'accueil Android, calculé sans rien connaître d'Android.
 *
 * Un widget est une phrase courte lue en une seconde depuis l'écran de verrouillage : ce qui
 * s'y affiche est une décision produit, pas de la mise en page. Tout est donc décidé ici, en
 * fonctions pures et testées, et la couche Android ne fait plus que poser les chaînes dans
 * quatre gabarits `RemoteViews`.
 */

/** Gabarit d'affichage — quatre formes suffisent aux quinze widgets. */
enum class WidgetShape { TILE, ROW, LIST, WEEK }

/** Écran ouvert quand on touche le widget. */
enum class WidgetTarget { HOME, TIMETABLE, HOMEWORK, GRADES, MESSAGES }

enum class WidgetKind(
    val label: String,
    val description: String,
    val shape: WidgetShape,
    val target: WidgetTarget,
) {
    // 1×1
    AVERAGE("Moyenne générale", "La moyenne de la période en cours.", WidgetShape.TILE, WidgetTarget.GRADES),
    HOMEWORK_COUNT("Devoirs à faire", "Combien de devoirs restent à faire.", WidgetShape.TILE, WidgetTarget.HOMEWORK),
    UNREAD("Messages non lus", "Le nombre de messages non lus.", WidgetShape.TILE, WidgetTarget.MESSAGES),
    COUNTDOWN("Avant le prochain cours", "Le temps restant, la matière et la salle.", WidgetShape.TILE, WidgetTarget.TIMETABLE),
    NEXT_EXAM("Prochaine évaluation", "Le devoir surveillé qui arrive, et dans combien de temps.", WidgetShape.TILE, WidgetTarget.HOMEWORK),
    ABSENCES("Absences à justifier", "Les absences du mois qui attendent un justificatif.", WidgetShape.TILE, WidgetTarget.HOME),

    // 2×1
    NEXT_LESSON("Prochain cours", "Matière, salle et horaire du prochain cours.", WidgetShape.ROW, WidgetTarget.TIMETABLE),
    URGENT_HOMEWORK("Devoir urgent", "Le devoir à ne pas oublier.", WidgetShape.ROW, WidgetTarget.HOMEWORK),
    LAST_GRADE("Dernière note", "La note publiée la plus récente.", WidgetShape.ROW, WidgetTarget.GRADES),
    LAST_MESSAGE("Dernier message", "Le message reçu le plus récent.", WidgetShape.ROW, WidgetTarget.MESSAGES),

    // 2×2
    TODAY("Journée", "Les prochains cours de la journée.", WidgetShape.LIST, WidgetTarget.TIMETABLE),
    HOMEWORK_LIST("Prochains devoirs", "Les devoirs à rendre en premier.", WidgetShape.LIST, WidgetTarget.HOMEWORK),
    GRADES_LIST("Dernières notes", "Les notes les plus récentes.", WidgetShape.LIST, WidgetTarget.GRADES),
    SUBJECT_AVERAGES("Moyennes par matière", "Les meilleures moyennes de la période.", WidgetShape.LIST, WidgetTarget.GRADES),

    // 4×2
    WEEK("Semaine", "La charge de chaque jour de la semaine.", WidgetShape.WEEK, WidgetTarget.TIMETABLE),
}

/** Une ligne de liste : un texte, un complément, une valeur à droite, une couleur. */
data class WidgetLine(
    val text: String,
    val secondary: String = "",
    val trailing: String = "",
    val accent: Long? = null,
)

data class WidgetDay(
    val name: String,
    val value: String,
    val today: Boolean,
    val empty: Boolean,
)

/** Ce qu'un widget affiche, quelle que soit sa forme. */
data class WidgetPayload(
    val kind: WidgetKind,
    val label: String,
    val title: String,
    val subtitle: String = "",
    val trailing: String = "",
    val accent: Long? = null,
    val lines: List<WidgetLine> = emptyList(),
    val days: List<WidgetDay> = emptyList(),
    /** Vrai quand il n'y a rien à montrer : le gabarit affiche alors [title] seul. */
    val empty: Boolean = false,
)

/**
 * Réglages posés à l'installation d'un widget.
 *
 * Un widget d'écran d'accueil est choisi une fois et regardé mille : qu'il montre la moyenne
 * générale ou celle d'une matière précise change tout, et personne ne veut rouvrir
 * l'application pour ça.
 */
data class WidgetOptions(val subject: String? = null) {
    companion object {
        /** Les seuls widgets qui ont quelque chose à configurer aujourd'hui. */
        val CONFIGURABLE = setOf(WidgetKind.AVERAGE)
    }
}

object WidgetContent {

    /** Nombre maximum de lignes tenant dans un widget 2×2 sans devenir illisible. */
    const val MAX_LINES = 4

    fun payload(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        now: LocalDateTime,
        options: WidgetOptions = WidgetOptions(),
    ): WidgetPayload {
        val today = now.toLocalDate()
        return when (kind) {
            WidgetKind.AVERAGE -> average(kind, snapshot, settings, today, options)
            WidgetKind.HOMEWORK_COUNT -> homeworkCount(kind, snapshot, settings, today)
            WidgetKind.UNREAD -> unread(kind, snapshot, settings)
            WidgetKind.COUNTDOWN -> countdown(kind, snapshot, settings, now)
            WidgetKind.NEXT_EXAM -> nextExam(kind, snapshot, settings, today)
            WidgetKind.ABSENCES -> absences(kind, snapshot, today)
            WidgetKind.NEXT_LESSON -> nextLesson(kind, snapshot, settings, now)
            WidgetKind.URGENT_HOMEWORK -> urgentHomework(kind, snapshot, settings, today)
            WidgetKind.LAST_GRADE -> lastGrade(kind, snapshot, settings)
            WidgetKind.LAST_MESSAGE -> lastMessage(kind, snapshot, settings, today)
            WidgetKind.TODAY -> todayList(kind, snapshot, settings, now)
            WidgetKind.HOMEWORK_LIST -> homeworkList(kind, snapshot, settings, today)
            WidgetKind.GRADES_LIST -> gradesList(kind, snapshot, settings)
            WidgetKind.SUBJECT_AVERAGES -> subjectAverages(kind, snapshot, settings, today)
            WidgetKind.WEEK -> week(kind, snapshot, today)
        }
    }

    // ── 1×1 ──────────────────────────────────────────────────────────────────

    private fun average(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
        options: WidgetOptions,
    ): WidgetPayload {
        val subject = options.subject
        if (subject != null) {
            val value = Insights.subjectAverages(snapshot, today)
                .firstOrNull { it.first == subject }
                ?.second
                ?: return empty(kind, "MOYENNE", "—", subject)
            return WidgetPayload(
                kind = kind,
                label = "MOYENNE",
                title = number(value),
                subtitle = subject,
                accent = SubjectPalette.colorFor(subject, settings.subjectColors),
            )
        }
        val average = Insights.generalAverage(snapshot, today)
            ?: return empty(kind, "MOYENNE", "—", "aucune note")
        return WidgetPayload(
            kind = kind,
            label = "MOYENNE",
            title = number(average.value),
            subtitle = if (average.official) {
                average.periodName.ifBlank { "générale" }
            } else {
                "estimée"
            },
        )
    }

    private fun homeworkCount(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val pending = Insights.pendingHomework(snapshot.homework, settings, today)
        val late = pending.count { it.dueDate.isBefore(today) }
        return WidgetPayload(
            kind = kind,
            label = "DEVOIRS",
            title = pending.size.toString(),
            subtitle = when {
                pending.isEmpty() -> "rien à faire"
                late > 0 -> "dont $late en retard"
                else -> "à faire"
            },
        )
    }

    private fun unread(kind: WidgetKind, snapshot: SchoolSnapshot, settings: AppSettings): WidgetPayload {
        val unread = Insights.unreadMessages(snapshot.inbox, settings)
        return WidgetPayload(
            kind = kind,
            label = "MESSAGES",
            title = unread.size.toString(),
            subtitle = if (unread.isEmpty()) "tout est lu" else "non lus",
        )
    }

    private fun countdown(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        now: LocalDateTime,
    ): WidgetPayload {
        val lesson = Insights.currentOrNextLesson(snapshot.lessons, now)
            ?: return empty(kind, "PROCHAIN COURS", "—", "rien de prévu")
        val minutes = Duration.between(now, lesson.start).toMinutes()
        return WidgetPayload(
            kind = kind,
            label = "DANS",
            title = when {
                minutes <= 0 -> "en cours"
                minutes < 60 -> "$minutes min"
                else -> Dates.duration(minutes)
            },
            // La matière et la salle dans la même tuile : sans elles, « dans 24 min » oblige
            // à ouvrir l'application pour savoir où aller.
            subtitle = listOfNotNull(
                lesson.subject,
                lesson.room.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            accent = SubjectPalette.colorFor(lesson.subject, settings.subjectColors),
        )
    }

    /**
     * La prochaine évaluation annoncée.
     *
     * C'est la question que se pose un élève devant son écran d'accueil — bien plus que
     * l'heure de la dernière synchronisation, que ce widget remplace.
     */
    private fun nextExam(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val exam = Insights.upcomingExams(snapshot.homework, settings, today).firstOrNull()
            ?: return empty(kind, "ÉVALUATION", "—", "rien d'annoncé")
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, exam.dueDate)
        return WidgetPayload(
            kind = kind,
            label = "ÉVALUATION",
            title = when {
                days <= 0L -> "aujourd'hui"
                days == 1L -> "demain"
                days < 7L -> "dans $days j"
                else -> Dates.dayAndMonth(exam.dueDate)
            },
            subtitle = exam.subject,
            accent = SubjectPalette.colorFor(exam.subject, settings.subjectColors),
        )
    }

    /**
     * Les absences du mois restées sans justificatif.
     *
     * Un chiffre qui appelle une action — écrire un mot, signer — là où la salle du prochain
     * cours, que ce widget remplace, répétait ce que le widget « prochain cours » disait déjà.
     */
    private fun absences(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val month = snapshot.schoolLife.filter { it.date.isAfter(today.minusDays(30)) }
        val unjustified = month.count { it.kind == SchoolLifeKind.ABSENCE && !it.justified }
        return WidgetPayload(
            kind = kind,
            label = "À JUSTIFIER",
            title = unjustified.toString(),
            subtitle = when {
                unjustified == 0 -> "rien en attente"
                unjustified == 1 -> "absence ce mois"
                else -> "absences ce mois"
            },
        )
    }

    // ── 2×1 ──────────────────────────────────────────────────────────────────

    private fun nextLesson(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        now: LocalDateTime,
    ): WidgetPayload {
        val lesson = Insights.currentOrNextLesson(snapshot.lessons, now)
            ?: return empty(kind, "PROCHAIN COURS", "Rien de prévu", "aucun cours à venir")
        return WidgetPayload(
            kind = kind,
            label = "PROCHAIN COURS",
            title = lesson.subject,
            subtitle = listOfNotNull(
                lesson.room.takeIf { it.isNotBlank() }?.let { "Salle $it" },
                lesson.teacher.takeIf { it.isNotBlank() },
                Dates.relativeToNow(lesson.start, lesson.end, now),
            ).joinToString(" · "),
            trailing = Dates.time(lesson.start),
            accent = SubjectPalette.colorFor(lesson.subject, settings.subjectColors),
        )
    }

    private fun urgentHomework(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val item = Insights.urgentHomework(snapshot.homework, settings, today)
            ?: return empty(kind, "À NE PAS OUBLIER", "Rien à faire", "tout est coché")
        return WidgetPayload(
            kind = kind,
            label = if (item.exam) "ÉVALUATION" else "À NE PAS OUBLIER",
            title = item.subject,
            subtitle = Text.preview(item.content, 60).ifBlank { Dates.dueLabel(item.dueDate, today) },
            trailing = shortDay(item.dueDate, today),
            accent = SubjectPalette.colorFor(item.subject, settings.subjectColors),
        )
    }

    private fun lastGrade(kind: WidgetKind, snapshot: SchoolSnapshot, settings: AppSettings): WidgetPayload {
        val grade = snapshot.grades.firstOrNull { it.value != null }
            ?: return empty(kind, "DERNIÈRE NOTE", "Aucune note", "rien de publié")
        return WidgetPayload(
            kind = kind,
            label = "DERNIÈRE NOTE",
            title = grade.subject,
            subtitle = grade.label,
            trailing = "${number(grade.value ?: 0.0)}/${number(grade.scale)}",
            accent = SubjectPalette.colorFor(grade.subject, settings.subjectColors),
        )
    }

    private fun lastMessage(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val message = snapshot.inbox.firstOrNull()
            ?: return empty(kind, "DERNIER MESSAGE", "Aucun message", "boîte vide")
        val unread = !settings.isMessageRead(message.id, message.read)
        return WidgetPayload(
            kind = kind,
            label = if (unread) "MESSAGE NON LU" else "DERNIER MESSAGE",
            title = message.sender,
            subtitle = message.subject,
            trailing = message.date?.let { shortDay(it.toLocalDate(), today) }.orEmpty(),
            accent = SubjectPalette.colorFor(message.sender, settings.subjectColors),
        )
    }

    // ── 2×2 ──────────────────────────────────────────────────────────────────

    private fun todayList(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        now: LocalDateTime,
    ): WidgetPayload {
        val today = now.toLocalDate()
        // Les cours terminés ne servent plus à rien sur un écran d'accueil : on montre la
        // suite de la journée, et seulement elle.
        val remaining = Insights.lessonsOn(snapshot.lessons, today).filter { it.end.isAfter(now) }
        val summary = Insights.daySummary(snapshot.lessons, today)
        return WidgetPayload(
            kind = kind,
            label = "AUJOURD'HUI",
            title = Dates.compactDay(today).replaceFirstChar { it.uppercase() },
            subtitle = if (summary.isEmpty) "aucun cours" else "${summary.lessons} cours · ${Dates.duration(summary.minutes)}",
            lines = remaining.take(MAX_LINES).map { lesson ->
                WidgetLine(
                    text = lesson.subject,
                    secondary = lesson.room,
                    trailing = Dates.time(lesson.start),
                    accent = SubjectPalette.colorFor(lesson.subject, settings.subjectColors),
                )
            },
            empty = remaining.isEmpty(),
        ).withEmptyText(if (summary.isEmpty) "Aucun cours aujourd'hui" else "Journée terminée")
    }

    private fun homeworkList(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val pending = Insights.pendingHomework(snapshot.homework, settings, today)
        return WidgetPayload(
            kind = kind,
            label = "À FAIRE",
            title = when (pending.size) {
                0 -> "Rien à faire"
                1 -> "1 devoir"
                else -> "${pending.size} devoirs"
            },
            lines = pending.take(MAX_LINES).map { item ->
                WidgetLine(
                    text = item.subject,
                    secondary = if (item.exam) "évaluation" else Text.preview(item.content, 40),
                    trailing = shortDay(item.dueDate, today),
                    accent = SubjectPalette.colorFor(item.subject, settings.subjectColors),
                )
            },
            empty = pending.isEmpty(),
        ).withEmptyText("Tout est coché")
    }

    private fun gradesList(kind: WidgetKind, snapshot: SchoolSnapshot, settings: AppSettings): WidgetPayload {
        val grades = snapshot.grades.filter { it.value != null }
        return WidgetPayload(
            kind = kind,
            label = "DERNIÈRES NOTES",
            title = if (grades.isEmpty()) "Aucune note" else "${grades.size} notes",
            lines = grades.take(MAX_LINES).map { grade ->
                WidgetLine(
                    text = grade.subject,
                    secondary = grade.label,
                    trailing = "${number(grade.value ?: 0.0)}/${number(grade.scale)}",
                    accent = SubjectPalette.colorFor(grade.subject, settings.subjectColors),
                )
            },
            empty = grades.isEmpty(),
        ).withEmptyText("Aucune note publiée")
    }

    private fun subjectAverages(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        today: java.time.LocalDate,
    ): WidgetPayload {
        val averages = Insights.subjectAverages(snapshot, today)
        val general = Insights.generalAverage(snapshot, today)
        return WidgetPayload(
            kind = kind,
            label = "MOYENNES",
            title = general?.let { number(it.value) } ?: "—",
            subtitle = general?.periodName.orEmpty(),
            lines = averages.take(MAX_LINES).map { (subject, value) ->
                WidgetLine(
                    text = subject,
                    trailing = number(value),
                    accent = SubjectPalette.colorFor(subject, settings.subjectColors),
                )
            },
            empty = averages.isEmpty(),
        ).withEmptyText("Aucune moyenne publiée")
    }

    // ── 4×2 ──────────────────────────────────────────────────────────────────

    private fun week(kind: WidgetKind, snapshot: SchoolSnapshot, today: java.time.LocalDate): WidgetPayload {
        val weekStart = Dates.startOfWeek(today)
        val week = Insights.week(snapshot.lessons, weekStart)
        return WidgetPayload(
            kind = kind,
            label = "SEMAINE",
            title = "Semaine du ${Dates.dayAndMonth(weekStart)}",
            subtitle = "${week.sumOf { it.lessons }} cours",
            days = week.map { day ->
                WidgetDay(
                    name = Dates.dayName(day.date).take(1).uppercase(),
                    value = if (day.isEmpty) "—" else day.lessons.toString(),
                    today = day.date == today,
                    empty = day.isEmpty,
                )
            },
            empty = week.all { it.isEmpty },
        ).withEmptyText("Emploi du temps non synchronisé")
    }

    // ── Aides ────────────────────────────────────────────────────────────────

    private fun empty(kind: WidgetKind, label: String, title: String, subtitle: String) =
        WidgetPayload(kind = kind, label = label, title = title, subtitle = subtitle, empty = true)

    /** Le texte affiché à la place des lignes quand il n'y a rien : il remplace le sous-titre. */
    private fun WidgetPayload.withEmptyText(text: String): WidgetPayload =
        if (empty) copy(subtitle = text) else this

    /** « lun. 7 », « auj. », « dem. » : trois caractères valent mieux qu'une date complète. */
    private fun shortDay(date: java.time.LocalDate, today: java.time.LocalDate): String = when {
        date == today -> "auj."
        date == today.plusDays(1) -> "dem."
        date.isBefore(today) -> "retard"
        else -> Dates.compactDay(date)
    }

    /** Une note ou une moyenne, à la française et sans décimale inutile. */
    fun number(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) {
            rounded.toInt().toString()
        } else {
            rounded.toString().replace('.', ',')
        }
    }
}
