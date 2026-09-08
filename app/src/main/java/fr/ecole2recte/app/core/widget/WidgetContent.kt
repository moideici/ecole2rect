package fr.ecole2recte.app.core.widget

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.SubjectPalette
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Le contenu des widgets d'écran d'accueil Android, calculé sans rien connaître d'Android.
 *
 * Deux règles font tout le fichier.
 *
 * **Le contenu suit la taille.** Un widget n'a pas de dimensions fixes : on l'étire, et deux
 * lanceurs ne donnent pas la même place pour « 2×2 ». Chaque widget sait donc se dire de
 * quatre façons — une tuile, une ligne, une liste, une semaine — et c'est la place réellement
 * disponible qui choisit. Agrandir un widget ajoute de l'information, jamais du vide.
 *
 * **Un widget ne dit jamais « rien ».** Vendredi soir, « aucun cours aujourd'hui » n'apprend
 * rien ; ce qu'on veut savoir, c'est ce qui attend lundi. Chaque widget qui peut être vide a
 * donc une suite : le jour d'école suivant, la prochaine évaluation, le dernier message.
 */

/**
 * Gabarit d'affichage.
 *
 * [HOURS] est le seul qui ne montre pas une liste mais **trois repères** : le début, la pause
 * méridienne et la fin. C'est ce qu'on regarde le matin sans vouloir lire tout l'emploi du
 * temps, et une ligne de texte ne le dit pas aussi vite que trois chiffres alignés.
 */
enum class WidgetShape { TILE, ROW, HOURS, LIST, WEEK }

/** Écran ouvert quand on touche le widget. */
enum class WidgetTarget { HOME, TIMETABLE, HOMEWORK, GRADES, MESSAGES }

/**
 * Les quinze widgets.
 *
 * [natural] est la forme du widget à sa taille d'installation : c'est ce que l'utilisateur
 * choisit dans la liste du lanceur. Étiré, il prend la forme que sa place permet.
 */
enum class WidgetKind(
    val label: String,
    val description: String,
    val natural: WidgetShape,
    val target: WidgetTarget,
) {
    // 1×1 à l'installation
    AVERAGE("Moyenne générale", "La moyenne de la période, le détail par matière si on l'agrandit.", WidgetShape.TILE, WidgetTarget.GRADES),
    HOMEWORK_COUNT("Devoirs à faire", "Combien de devoirs restent, puis lesquels.", WidgetShape.TILE, WidgetTarget.HOMEWORK),
    UNREAD("Messages non lus", "Le nombre de non-lus, puis les derniers messages.", WidgetShape.TILE, WidgetTarget.MESSAGES),
    COUNTDOWN("Avant le prochain cours", "Le temps restant, la matière et la salle.", WidgetShape.TILE, WidgetTarget.TIMETABLE),
    NEXT_EXAM("Prochaine évaluation", "Le devoir surveillé qui arrive, puis tous ceux annoncés.", WidgetShape.TILE, WidgetTarget.HOMEWORK),
    ABSENCES("Absences à justifier", "Ce qui attend un justificatif, puis le détail.", WidgetShape.TILE, WidgetTarget.HOME),

    // 2×1 à l'installation
    NEXT_LESSON("Prochain cours", "Matière, salle et horaire — la journée si on l'agrandit.", WidgetShape.ROW, WidgetTarget.TIMETABLE),
    URGENT_HOMEWORK("Devoir urgent", "Le devoir à ne pas oublier, puis la liste.", WidgetShape.ROW, WidgetTarget.HOMEWORK),
    LAST_GRADE("Dernière note", "La note la plus récente, puis les précédentes.", WidgetShape.ROW, WidgetTarget.GRADES),
    LAST_MESSAGE("Dernier message", "Le message reçu le plus récent, puis les précédents.", WidgetShape.ROW, WidgetTarget.MESSAGES),

    DAY_HOURS(
        "Horaires du jour",
        "Début, pause méridienne et fin — de la journée en cours, ou de la prochaine.",
        WidgetShape.HOURS,
        WidgetTarget.TIMETABLE,
    ),

    // 2×2 à l'installation
    TODAY("Journée", "La suite de la journée, ou le prochain jour d'école.", WidgetShape.LIST, WidgetTarget.TIMETABLE),
    HOMEWORK_LIST("Prochains devoirs", "Les devoirs à rendre en premier.", WidgetShape.LIST, WidgetTarget.HOMEWORK),
    GRADES_LIST("Dernières notes", "Les notes les plus récentes.", WidgetShape.LIST, WidgetTarget.GRADES),
    SUBJECT_AVERAGES("Moyennes par matière", "Les moyennes de la période, matière par matière.", WidgetShape.LIST, WidgetTarget.GRADES),

    // 4×2 à l'installation
    WEEK("Semaine", "La charge de chaque jour ; rétréci, il devient une liste.", WidgetShape.WEEK, WidgetTarget.TIMETABLE),
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
    val shape: WidgetShape,
    val label: String,
    val title: String,
    val subtitle: String = "",
    val trailing: String = "",
    val accent: Long? = null,
    val lines: List<WidgetLine> = emptyList(),
    val days: List<WidgetDay> = emptyList(),
    /**
     * Avancement du cours en cours, de 0 à 1, quand il y en a un. C'est la seule chose qu'un
     * emploi du temps peut montrer et qu'un texte ne dit pas bien.
     */
    val progress: Float? = null,
    /** Vrai quand il n'y a rien à montrer : le gabarit affiche alors [title] seul. */
    val empty: Boolean = false,
) {

    /**
     * Ce qu'un lecteur d'écran doit dire du widget, en une phrase.
     *
     * Sans cela, TalkBack lit les vues une par une, dans l'ordre où elles sont posées :
     * « PROCHAIN COURS », « Mathématiques », « Salle C011 · M. Petit · dans 24 min »,
     * « 10:15 » — quatre arrêts pour une seule information. Une description unique sur la
     * carte remplace ce défilé par une phrase, et c'est aussi elle qu'annonce le lanceur
     * quand on parcourt l'écran d'accueil au doigt.
     */
    val spoken: String
        get() = buildList {
            add(label.lowercase().replaceFirstChar { it.uppercase() })
            add(title)
            if (subtitle.isNotBlank()) add(subtitle)
            if (trailing.isNotBlank()) add(trailing)
            lines.forEach { line ->
                add(
                    listOf(line.text, line.secondary, line.trailing)
                        .filter { it.isNotBlank() }
                        .joinToString(", "),
                )
            }
            days.filterNot { it.empty }.forEach { day ->
                add("${day.name} ${day.value}")
            }
        }.filter { it.isNotBlank() }.joinToString(". ")
}

/**
 * Réglages posés à l'installation d'un widget.
 *
 * Un widget d'écran d'accueil est choisi une fois et regardé mille : qu'il montre la moyenne
 * générale ou celle d'une matière, les cours de l'aîné ou ceux de la cadette, change tout, et
 * personne ne veut rouvrir l'application pour ça.
 */
data class WidgetOptions(val subject: String? = null, val accountId: String? = null) {
    companion object {
        /** Les widgets à qui l'on peut désigner une matière précise. */
        val SUBJECT_AWARE = setOf(WidgetKind.AVERAGE)
    }
}

object WidgetContent {

    /** Ce que tient un widget de taille d'installation, quand personne ne l'a étiré. */
    const val MAX_LINES = 4

    /**
     * Le contenu d'un widget, décidé par sa nature *et* par la place qu'on lui laisse.
     *
     * [shape] et [maxLines] viennent de [WidgetSizing] ; les valeurs par défaut donnent le
     * widget tel qu'il apparaît à l'installation.
     */
    fun payload(
        kind: WidgetKind,
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        now: LocalDateTime,
        options: WidgetOptions = WidgetOptions(),
        shape: WidgetShape = kind.natural,
        maxLines: Int = MAX_LINES,
    ): WidgetPayload {
        val today = now.toLocalDate()
        val context = Context(snapshot, settings, now, today, options, shape, maxLines)
        return when (kind) {
            WidgetKind.AVERAGE, WidgetKind.SUBJECT_AVERAGES -> when (shape) {
                WidgetShape.TILE -> average(kind, context)
                WidgetShape.ROW -> averageRow(kind, context)
                else -> subjectAverages(kind, context)
            }

            WidgetKind.LAST_GRADE, WidgetKind.GRADES_LIST -> when (shape) {
                WidgetShape.TILE -> average(kind, context)
                WidgetShape.ROW -> lastGrade(kind, context)
                else -> gradesList(kind, context)
            }

            WidgetKind.HOMEWORK_COUNT, WidgetKind.URGENT_HOMEWORK, WidgetKind.HOMEWORK_LIST -> when (shape) {
                WidgetShape.TILE -> homeworkCount(kind, context)
                WidgetShape.ROW -> urgentHomework(kind, context)
                else -> homeworkList(kind, context)
            }

            WidgetKind.UNREAD, WidgetKind.LAST_MESSAGE -> when (shape) {
                WidgetShape.TILE -> unread(kind, context)
                WidgetShape.ROW -> lastMessage(kind, context)
                else -> messageList(kind, context)
            }

            WidgetKind.COUNTDOWN, WidgetKind.NEXT_LESSON, WidgetKind.TODAY -> when (shape) {
                WidgetShape.TILE -> countdown(kind, context)
                WidgetShape.ROW -> nextLesson(kind, context)
                WidgetShape.HOURS -> dayHours(kind, context)
                WidgetShape.LIST -> dayList(kind, context)
                WidgetShape.WEEK -> week(kind, context)
            }

            WidgetKind.DAY_HOURS -> when (shape) {
                WidgetShape.TILE -> countdown(kind, context)
                // Rétréci à une ligne, il perd la pause : mieux vaut deux repères justes
                // que trois chiffres serrés au point d'être illisibles.
                WidgetShape.ROW -> daySummaryRow(kind, context)
                WidgetShape.HOURS -> dayHours(kind, context)
                WidgetShape.LIST -> dayList(kind, context)
                WidgetShape.WEEK -> week(kind, context)
            }

            WidgetKind.NEXT_EXAM -> when (shape) {
                WidgetShape.TILE -> nextExam(kind, context)
                WidgetShape.ROW -> nextExamRow(kind, context)
                else -> examList(kind, context)
            }

            WidgetKind.ABSENCES -> when (shape) {
                WidgetShape.TILE -> absences(kind, context)
                WidgetShape.ROW -> lastSchoolLife(kind, context)
                else -> schoolLifeList(kind, context)
            }

            WidgetKind.WEEK -> when (shape) {
                WidgetShape.TILE -> countdown(kind, context)
                WidgetShape.ROW -> daySummaryRow(kind, context)
                WidgetShape.HOURS -> dayHours(kind, context)
                WidgetShape.LIST -> weekList(kind, context)
                WidgetShape.WEEK -> week(kind, context)
            }
        }
    }

    /** Tout ce dont un constructeur de contenu a besoin, pour ne pas répéter six paramètres. */
    private data class Context(
        val snapshot: SchoolSnapshot,
        val settings: AppSettings,
        val now: LocalDateTime,
        val today: LocalDate,
        val options: WidgetOptions,
        val shape: WidgetShape,
        val maxLines: Int,
    )

    // ── Notes ────────────────────────────────────────────────────────────────

    private fun average(kind: WidgetKind, context: Context): WidgetPayload {
        val subject = context.options.subject
        if (subject != null) {
            val value = Insights.subjectAverages(context.snapshot, context.today)
                .firstOrNull { it.first == subject }
                ?.second
                ?: return empty(kind, context, "MOYENNE", "—", subject)
            return payloadOf(
                kind, context,
                label = "MOYENNE",
                title = number(value),
                subtitle = subject,
                accent = SubjectPalette.colorFor(subject, context.settings.subjectColors),
            )
        }
        val average = Insights.generalAverage(context.snapshot, context.today)
            ?: return empty(kind, context, "MOYENNE", "—", "aucune note")
        return payloadOf(
            kind, context,
            label = "MOYENNE",
            title = number(average.value),
            subtitle = if (average.official) average.periodName.ifBlank { "générale" } else "estimée",
        )
    }

    /** La moyenne accompagnée de sa tendance : un chiffre seul ne dit pas s'il monte. */
    private fun averageRow(kind: WidgetKind, context: Context): WidgetPayload {
        val average = Insights.generalAverage(context.snapshot, context.today)
            ?: return empty(kind, context, "MOYENNE", "Aucune note", "rien de publié")
        val period = Insights.currentPeriod(context.snapshot.periods, context.today)
        val grades = Insights.gradesForPeriod(context.snapshot.grades, period?.code)
        val trend = Insights.trend(grades)
        return payloadOf(
            kind, context,
            label = "MOYENNE",
            title = number(average.value),
            subtitle = listOfNotNull(
                average.periodName.takeIf { it.isNotBlank() },
                period?.classAverage?.let { "classe ${number(it)}" },
                trend?.let { if (it >= 0) "↗ +${number(it)}" else "↘ ${number(it)}" },
            ).joinToString(" · "),
            trailing = "/20",
        )
    }

    private fun lastGrade(kind: WidgetKind, context: Context): WidgetPayload {
        val grade = context.snapshot.grades.firstOrNull { it.value != null }
            ?: return empty(kind, context, "DERNIÈRE NOTE", "Aucune note", "rien de publié")
        return payloadOf(
            kind, context,
            label = "DERNIÈRE NOTE",
            title = grade.subject,
            subtitle = listOfNotNull(
                grade.label.takeIf { it.isNotBlank() },
                grade.classAverage?.let { "classe ${number(it)}" },
                grade.date?.let { shortDay(it, context.today) },
            ).joinToString(" · "),
            trailing = "${number(grade.value ?: 0.0)}/${number(grade.scale)}",
            accent = SubjectPalette.colorFor(grade.subject, context.settings.subjectColors),
        )
    }

    private fun gradesList(kind: WidgetKind, context: Context): WidgetPayload {
        val grades = context.snapshot.grades.filter { it.value != null }
        return payloadOf(
            kind, context,
            label = "DERNIÈRES NOTES",
            title = if (grades.isEmpty()) "Aucune note" else "${grades.size} notes",
            subtitle = Insights.generalAverage(context.snapshot, context.today)
                ?.let { "moyenne ${number(it.value)}" }
                .orEmpty(),
            lines = grades.take(context.maxLines).map { grade ->
                WidgetLine(
                    text = grade.subject,
                    secondary = grade.label,
                    trailing = "${number(grade.value ?: 0.0)}/${number(grade.scale)}",
                    accent = SubjectPalette.colorFor(grade.subject, context.settings.subjectColors),
                )
            },
            empty = grades.isEmpty(),
        ).withEmptyText("Aucune note publiée")
    }

    private fun subjectAverages(kind: WidgetKind, context: Context): WidgetPayload {
        val averages = Insights.subjectAverages(context.snapshot, context.today)
        val general = Insights.generalAverage(context.snapshot, context.today)
        return payloadOf(
            kind, context,
            label = "MOYENNES",
            title = general?.let { number(it.value) } ?: "—",
            subtitle = general?.periodName.orEmpty(),
            lines = averages.take(context.maxLines).map { (subject, value) ->
                WidgetLine(
                    text = subject,
                    trailing = number(value),
                    accent = SubjectPalette.colorFor(subject, context.settings.subjectColors),
                )
            },
            empty = averages.isEmpty(),
        ).withEmptyText("Aucune moyenne publiée")
    }

    // ── Devoirs ──────────────────────────────────────────────────────────────

    private fun homeworkCount(kind: WidgetKind, context: Context): WidgetPayload {
        val pending = Insights.pendingHomework(context.snapshot.homework, context.settings, context.today)
        val late = pending.count { it.dueDate.isBefore(context.today) }
        return payloadOf(
            kind, context,
            label = "DEVOIRS",
            title = pending.size.toString(),
            subtitle = when {
                pending.isEmpty() -> "rien à faire"
                late > 0 -> "dont $late en retard"
                else -> "à faire"
            },
        )
    }

    private fun urgentHomework(kind: WidgetKind, context: Context): WidgetPayload {
        val item = Insights.urgentHomework(context.snapshot.homework, context.settings, context.today)
            ?: return nextExamRow(kind, context, fallbackLabel = "À NE PAS OUBLIER")
        return payloadOf(
            kind, context,
            label = if (item.exam) "ÉVALUATION" else "À NE PAS OUBLIER",
            title = item.subject,
            subtitle = Text.preview(item.content, 60).ifBlank { Dates.dueLabel(item.dueDate, context.today) },
            trailing = shortDay(item.dueDate, context.today),
            accent = SubjectPalette.colorFor(item.subject, context.settings.subjectColors),
        )
    }

    private fun homeworkList(kind: WidgetKind, context: Context): WidgetPayload {
        val pending = Insights.pendingHomework(context.snapshot.homework, context.settings, context.today)
        val late = pending.count { it.dueDate.isBefore(context.today) }
        return payloadOf(
            kind, context,
            label = "À FAIRE",
            title = when (pending.size) {
                0 -> "Rien à faire"
                1 -> "1 devoir"
                else -> "${pending.size} devoirs"
            },
            subtitle = if (late > 0) "$late en retard" else "",
            lines = pending.take(context.maxLines).map { item ->
                WidgetLine(
                    text = item.subject,
                    secondary = if (item.exam) "évaluation" else Text.preview(item.content, 40),
                    trailing = shortDay(item.dueDate, context.today),
                    accent = SubjectPalette.colorFor(item.subject, context.settings.subjectColors),
                )
            },
            empty = pending.isEmpty(),
        ).withEmptyText("Tout est coché")
    }

    // ── Évaluations ──────────────────────────────────────────────────────────

    private fun nextExam(kind: WidgetKind, context: Context): WidgetPayload {
        val exam = Insights.upcomingExams(context.snapshot.homework, context.settings, context.today).firstOrNull()
            ?: return empty(kind, context, "ÉVALUATION", "—", "rien d'annoncé")
        return payloadOf(
            kind, context,
            label = "ÉVALUATION",
            title = countdownLabel(exam.dueDate, context.today),
            subtitle = exam.subject,
            accent = SubjectPalette.colorFor(exam.subject, context.settings.subjectColors),
        )
    }

    private fun nextExamRow(
        kind: WidgetKind,
        context: Context,
        fallbackLabel: String = "ÉVALUATION",
    ): WidgetPayload {
        val exam = Insights.upcomingExams(context.snapshot.homework, context.settings, context.today).firstOrNull()
            ?: return empty(kind, context, fallbackLabel, "Rien à faire", "aucune évaluation annoncée")
        return payloadOf(
            kind, context,
            label = fallbackLabel,
            title = exam.subject,
            subtitle = Text.preview(exam.content, 60).ifBlank { "évaluation annoncée" },
            trailing = countdownLabel(exam.dueDate, context.today),
            accent = SubjectPalette.colorFor(exam.subject, context.settings.subjectColors),
        )
    }

    private fun examList(kind: WidgetKind, context: Context): WidgetPayload {
        val exams = Insights.upcomingExams(context.snapshot.homework, context.settings, context.today)
        return payloadOf(
            kind, context,
            label = "ÉVALUATIONS",
            title = when (exams.size) {
                0 -> "Aucune évaluation"
                1 -> "1 évaluation"
                else -> "${exams.size} évaluations"
            },
            subtitle = exams.firstOrNull()?.let { countdownLabel(it.dueDate, context.today) }.orEmpty(),
            lines = exams.take(context.maxLines).map { exam ->
                WidgetLine(
                    text = exam.subject,
                    secondary = Text.preview(exam.content, 40),
                    trailing = shortDay(exam.dueDate, context.today),
                    accent = SubjectPalette.colorFor(exam.subject, context.settings.subjectColors),
                )
            },
            empty = exams.isEmpty(),
        ).withEmptyText("Rien d'annoncé pour l'instant")
    }

    // ── Messagerie ───────────────────────────────────────────────────────────

    private fun unread(kind: WidgetKind, context: Context): WidgetPayload {
        val unread = Insights.unreadMessages(context.snapshot.inbox, context.settings)
        return payloadOf(
            kind, context,
            label = "MESSAGES",
            title = unread.size.toString(),
            subtitle = if (unread.isEmpty()) "tout est lu" else "non lus",
        )
    }

    private fun lastMessage(kind: WidgetKind, context: Context): WidgetPayload {
        val message = context.snapshot.inbox.firstOrNull()
            ?: return empty(kind, context, "DERNIER MESSAGE", "Aucun message", "boîte vide")
        val unread = !context.settings.isMessageRead(message.id, message.read)
        return payloadOf(
            kind, context,
            label = if (unread) "MESSAGE NON LU" else "DERNIER MESSAGE",
            title = message.sender,
            subtitle = message.subject,
            trailing = message.date?.let { shortDay(it.toLocalDate(), context.today) }.orEmpty(),
            accent = SubjectPalette.colorFor(message.sender, context.settings.subjectColors),
        )
    }

    private fun messageList(kind: WidgetKind, context: Context): WidgetPayload {
        val messages = context.snapshot.inbox
        val unread = Insights.unreadMessages(messages, context.settings)
        return payloadOf(
            kind, context,
            label = "MESSAGES",
            title = when (unread.size) {
                0 -> "Tout est lu"
                1 -> "1 non lu"
                else -> "${unread.size} non lus"
            },
            lines = messages.take(context.maxLines).map { message ->
                WidgetLine(
                    text = message.sender,
                    secondary = message.subject,
                    trailing = message.date?.let { shortDay(it.toLocalDate(), context.today) }.orEmpty(),
                    accent = SubjectPalette.colorFor(message.sender, context.settings.subjectColors),
                )
            },
            empty = messages.isEmpty(),
        ).withEmptyText("Boîte vide")
    }

    // ── Vie scolaire ─────────────────────────────────────────────────────────

    private fun recentSchoolLife(context: Context) =
        context.snapshot.schoolLife.filter { it.date.isAfter(context.today.minusDays(30)) }

    private fun absences(kind: WidgetKind, context: Context): WidgetPayload {
        val unjustified = recentSchoolLife(context)
            .count { it.kind == SchoolLifeKind.ABSENCE && !it.justified }
        return payloadOf(
            kind, context,
            label = "À JUSTIFIER",
            title = unjustified.toString(),
            subtitle = when (unjustified) {
                0 -> "rien en attente"
                1 -> "absence ce mois"
                else -> "absences ce mois"
            },
        )
    }

    private fun lastSchoolLife(kind: WidgetKind, context: Context): WidgetPayload {
        val event = recentSchoolLife(context).firstOrNull()
            ?: return empty(kind, context, "VIE SCOLAIRE", "Rien à signaler", "aucun événement ce mois")
        return payloadOf(
            kind, context,
            label = "VIE SCOLAIRE",
            title = event.kind.label,
            subtitle = listOfNotNull(
                event.reason.takeIf { it.isNotBlank() },
                if (event.justified) "justifié" else "à justifier",
            ).joinToString(" · "),
            trailing = shortDay(event.date, context.today),
        )
    }

    private fun schoolLifeList(kind: WidgetKind, context: Context): WidgetPayload {
        val events = recentSchoolLife(context)
        val unjustified = events.count { it.kind == SchoolLifeKind.ABSENCE && !it.justified }
        return payloadOf(
            kind, context,
            label = "VIE SCOLAIRE",
            title = if (unjustified == 0) "Rien à justifier" else "$unjustified à justifier",
            subtitle = "${events.size} ce mois",
            lines = events.take(context.maxLines).map { event ->
                WidgetLine(
                    text = event.kind.label,
                    secondary = event.reason.ifBlank { if (event.justified) "justifié" else "à justifier" },
                    trailing = shortDay(event.date, context.today),
                )
            },
            empty = events.isEmpty(),
        ).withEmptyText("Rien à signaler ce mois")
    }

    // ── Emploi du temps ──────────────────────────────────────────────────────

    private fun countdown(kind: WidgetKind, context: Context): WidgetPayload {
        val lesson = Insights.currentOrNextLesson(context.snapshot.lessons, context.now)
            ?: return empty(kind, context, "PROCHAIN COURS", "—", "rien de prévu")
        val minutes = Duration.between(context.now, lesson.start).toMinutes()
        val started = minutes <= 0
        return payloadOf(
            kind, context,
            label = if (lesson.date == context.today) "DANS" else dayLabel(lesson.date, context.today).uppercase(),
            title = when {
                started -> "en cours"
                minutes < 60 -> "$minutes min"
                else -> Dates.duration(minutes)
            },
            // La matière et la salle dans la même tuile : sans elles, « dans 24 min » oblige
            // à ouvrir l'application pour savoir où aller.
            subtitle = listOfNotNull(
                lesson.subject,
                lesson.room.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            accent = SubjectPalette.colorFor(lesson.subject, context.settings.subjectColors),
            progress = if (started) Dates.progress(lesson.start, lesson.end, context.now) else null,
        )
    }

    private fun nextLesson(kind: WidgetKind, context: Context): WidgetPayload {
        val lesson = Insights.currentOrNextLesson(context.snapshot.lessons, context.now)
            ?: return empty(kind, context, "PROCHAIN COURS", "Rien de prévu", "aucun cours à venir")
        val started = !lesson.start.isAfter(context.now)
        return payloadOf(
            kind, context,
            label = if (started) "COURS EN COURS" else "PROCHAIN COURS",
            title = lesson.subject,
            subtitle = listOfNotNull(
                lesson.room.takeIf { it.isNotBlank() }?.let { "Salle $it" },
                lesson.teacher.takeIf { it.isNotBlank() },
                // Le jour d'abord quand ce n'est pas aujourd'hui : « dans 18 h » ne dit pas
                // qu'il s'agit de demain matin.
                if (lesson.date == context.today) {
                    Dates.relativeToNow(lesson.start, lesson.end, context.now)
                } else {
                    dayLabel(lesson.date, context.today)
                },
            ).joinToString(" · "),
            trailing = Dates.time(lesson.start),
            accent = SubjectPalette.colorFor(lesson.subject, context.settings.subjectColors),
            progress = if (started) Dates.progress(lesson.start, lesson.end, context.now) else null,
        )
    }

    /**
     * La suite de la journée — et, quand elle est finie, celle du prochain jour d'école.
     *
     * C'est la différence entre un widget qu'on regarde et un widget qu'on décroche : à
     * 17 heures, « journée terminée » n'apprend rien, « demain, 4 cours à partir de 8 h » si.
     */
    private fun dayList(kind: WidgetKind, context: Context): WidgetPayload {
        val remaining = Insights.lessonsOn(context.snapshot.lessons, context.today)
            .filter { it.end.isAfter(context.now) }
        val date = if (remaining.isNotEmpty()) {
            context.today
        } else {
            Insights.nextLessonDay(context.snapshot.lessons, context.today)
        }
        val lessons = if (remaining.isNotEmpty()) {
            remaining
        } else {
            date?.let { Insights.lessonsOn(context.snapshot.lessons, it) }.orEmpty()
        }
        val summary = date?.let { Insights.daySummary(context.snapshot.lessons, it) }
        val current = lessons.firstOrNull { !it.start.isAfter(context.now) && it.end.isAfter(context.now) }
        return payloadOf(
            kind, context,
            label = when {
                date == null -> "EMPLOI DU TEMPS"
                date == context.today -> "AUJOURD'HUI"
                else -> dayLabel(date, context.today).uppercase()
            },
            title = date?.let { Dates.compactDay(it).replaceFirstChar { first -> first.uppercase() } } ?: "—",
            subtitle = when {
                summary == null || summary.isEmpty -> "aucun cours"
                else -> "${summary.lessons} cours · ${Dates.duration(summary.minutes)}"
            },
            lines = dayLines(lessons, date, context),
            progress = current?.let { Dates.progress(it.start, it.end, context.now) },
            empty = lessons.isEmpty(),
        ).withEmptyText("Aucun cours à venir")
    }

    /**
     * Les trois heures qui structurent une journée : début, pause méridienne, fin.
     *
     * C'est ce qu'on veut savoir le matin sans lire l'emploi du temps : à quelle heure il
     * faut être là, quand on mange, quand on sort. Le soir venu — ou le week-end — ce sont
     * les mêmes trois repères, mais de la **prochaine journée d'école** : « fin : 17:00 »
     * n'apprend rien à 18 h.
     *
     * La pause est reconnue, jamais supposée : une journée continue affiche un tiret plutôt
     * qu'un midi inventé.
     */
    private fun dayHours(kind: WidgetKind, context: Context): WidgetPayload {
        val remaining = Insights.lessonsOn(context.snapshot.lessons, context.today)
            .filter { it.end.isAfter(context.now) }
        val date = if (remaining.isNotEmpty()) {
            context.today
        } else {
            Insights.nextLessonDay(context.snapshot.lessons, context.today)
        } ?: return empty(kind, context, "HORAIRES", "Aucun cours", "rien de programmé")

        val summary = Insights.daySummary(context.snapshot.lessons, date)
        val start = summary.start
        val end = summary.end
        if (summary.isEmpty || start == null || end == null) {
            return empty(kind, context, "HORAIRES", "Aucun cours", "journée libre")
        }
        val pause = Insights.middayBreak(context.snapshot.lessons, date)
        return payloadOf(
            kind, context,
            label = when (date) {
                context.today -> "AUJOURD'HUI"
                else -> dayLabel(date, context.today).uppercase()
            },
            title = "${Dates.time(start)} – ${Dates.time(end)}",
            subtitle = "${summary.lessons} cours · ${Dates.duration(summary.minutes)}",
            lines = listOf(
                WidgetLine(text = "début", trailing = Dates.time(start)),
                WidgetLine(
                    text = "pause",
                    secondary = pause?.let { Dates.duration(it.minutes) }.orEmpty(),
                    trailing = pause?.let { Dates.time(it.start) } ?: "—",
                ),
                WidgetLine(text = "fin", trailing = Dates.time(end)),
            ),
        )
    }

    /**
     * Les lignes d'une journée, lues comme un emploi du temps et non comme une liste.
     *
     * Chaque cours porte sa **plage horaire** et pas seulement son heure de début : « 8:00 »
     * ne dit pas si le cours dure une heure ou trois. Et la pause méridienne apparaît en
     * toutes lettres, à sa place dans la journée, au lieu de se deviner à un trou entre deux
     * lignes — c'est ce qui manque le plus à une liste pour ressembler à un emploi du temps.
     */
    private fun dayLines(
        lessons: List<fr.ecole2recte.app.core.model.Lesson>,
        date: LocalDate?,
        context: Context,
    ): List<WidgetLine> {
        val pause = date?.let { Insights.middayBreak(context.snapshot.lessons, it) }
        val lines = mutableListOf<WidgetLine>()
        lessons.forEach { lesson ->
            if (pause != null && lines.isNotEmpty() && !lesson.start.isBefore(pause.end)) {
                val already = lines.any { it.text == "Pause méridienne" }
                if (!already) {
                    lines += WidgetLine(
                        text = "Pause méridienne",
                        secondary = Dates.duration(pause.minutes),
                        trailing = Dates.time(pause.start),
                    )
                }
            }
            lines += WidgetLine(
                text = lesson.subject,
                secondary = listOfNotNull(
                    lesson.room.takeIf { it.isNotBlank() },
                    "annulé".takeIf { lesson.canceled },
                ).joinToString(" · "),
                trailing = "${Dates.time(lesson.start)}–${Dates.time(lesson.end)}",
                accent = SubjectPalette.colorFor(lesson.subject, context.settings.subjectColors),
            )
        }
        return lines.take(context.maxLines)
    }

    /** Le résumé d'une journée en une ligne : ce que la semaine dit d'un seul jour. */
    private fun daySummaryRow(kind: WidgetKind, context: Context): WidgetPayload {
        val summary = Insights.daySummary(context.snapshot.lessons, context.today)
        if (summary.isEmpty) return nextLesson(kind, context)
        val remaining = Insights.lessonsOn(context.snapshot.lessons, context.today)
            .count { it.end.isAfter(context.now) }
        return payloadOf(
            kind, context,
            label = "AUJOURD'HUI",
            title = "${summary.lessons} cours",
            subtitle = listOfNotNull(
                Dates.duration(summary.minutes),
                summary.start?.let { "de ${Dates.time(it)}" },
                summary.end?.let { "à ${Dates.time(it)}" },
            ).joinToString(" "),
            trailing = if (remaining > 0) "$remaining restants" else "terminé",
        )
    }

    private fun week(kind: WidgetKind, context: Context): WidgetPayload {
        val weekStart = Dates.startOfWeek(context.today)
        val week = Insights.week(context.snapshot.lessons, weekStart)
        return payloadOf(
            kind, context,
            label = "SEMAINE",
            title = "Semaine du ${Dates.dayAndMonth(weekStart)}",
            subtitle = "${week.sumOf { it.lessons }} cours",
            days = week.map { day ->
                WidgetDay(
                    name = Dates.dayName(day.date).take(1).uppercase(),
                    value = if (day.isEmpty) "—" else day.lessons.toString(),
                    today = day.date == context.today,
                    empty = day.isEmpty,
                )
            },
            empty = week.all { it.isEmpty },
        ).withEmptyText("Emploi du temps non synchronisé")
    }

    /** La semaine quand la largeur manque : un jour par ligne plutôt que six colonnes serrées. */
    private fun weekList(kind: WidgetKind, context: Context): WidgetPayload {
        val weekStart = Dates.startOfWeek(context.today)
        val week = Insights.week(context.snapshot.lessons, weekStart)
        return payloadOf(
            kind, context,
            label = "SEMAINE",
            title = "${week.sumOf { it.lessons }} cours",
            subtitle = "depuis le ${Dates.dayAndMonth(weekStart)}",
            lines = week.take(context.maxLines).map { day ->
                WidgetLine(
                    text = Dates.dayName(day.date).replaceFirstChar { it.uppercase() },
                    secondary = if (day.isEmpty) "" else Dates.duration(day.minutes),
                    trailing = if (day.isEmpty) "—" else "${day.lessons}",
                )
            },
            empty = week.all { it.isEmpty },
        ).withEmptyText("Emploi du temps non synchronisé")
    }

    // ── Aides ────────────────────────────────────────────────────────────────

    private fun payloadOf(
        kind: WidgetKind,
        context: Context,
        label: String,
        title: String,
        subtitle: String = "",
        trailing: String = "",
        accent: Long? = null,
        lines: List<WidgetLine> = emptyList(),
        days: List<WidgetDay> = emptyList(),
        progress: Float? = null,
        empty: Boolean = false,
    ) = WidgetPayload(
        kind = kind,
        shape = context.shape,
        label = label,
        title = title,
        subtitle = subtitle,
        trailing = trailing,
        accent = accent,
        lines = lines,
        days = days,
        progress = progress,
        empty = empty,
    )

    private fun empty(
        kind: WidgetKind,
        context: Context,
        label: String,
        title: String,
        subtitle: String,
    ) = payloadOf(kind, context, label = label, title = title, subtitle = subtitle, empty = true)

    /** Le texte affiché à la place des lignes quand il n'y a rien : il remplace le sous-titre. */
    private fun WidgetPayload.withEmptyText(text: String): WidgetPayload =
        if (empty) copy(subtitle = text) else this

    /** « lun. 7 », « auj. », « dem. » : trois caractères valent mieux qu'une date complète. */
    private fun shortDay(date: LocalDate, today: LocalDate): String = when {
        date == today -> "auj."
        date == today.plusDays(1) -> "dem."
        date.isBefore(today) -> "retard"
        else -> Dates.compactDay(date)
    }

    /** « demain », « lundi », « dans 12 jours » : de quoi situer un jour sans calculer. */
    private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
        date == today -> "aujourd'hui"
        date == today.plusDays(1) -> "demain"
        date.isBefore(today.plusDays(7)) -> Dates.dayName(date)
        else -> Dates.dayAndMonth(date)
    }

    /** Le même repère, mais tourné vers l'échéance : « demain », « dans 3 j », une date. */
    private fun countdownLabel(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return when {
            days <= 0L -> "aujourd'hui"
            days == 1L -> "demain"
            days < 7L -> "dans $days j"
            else -> Dates.shortDate(date, today)
        }
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
