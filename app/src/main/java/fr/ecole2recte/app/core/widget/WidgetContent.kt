package fr.ecole2recte.app.core.widget

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
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
 * [HOURS] ne montre pas une liste mais **trois repères** : le début, la pause méridienne et
 * la fin. C'est ce qu'on regarde le matin sans vouloir lire tout l'emploi du temps.
 *
 * [GRID] est la forme qui remplit vraiment sa hauteur : chaque cours y occupe une place
 * **proportionnelle à sa durée**, du haut au bas du widget, et les trous restent des trous.
 * Un 2×2 rempli de quatre lignes collées en haut n'est qu'un 2×1 avec du vide dessous.
 *
 * [WEEK] applique la même échelle à cinq ou six colonnes : la forme de la semaine se lit
 * alors d'un coup — mercredi court, vendredi fini tôt.
 */
enum class WidgetShape { TILE, ROW, HOURS, GRID, LIST, WEEK }

/** Écran ouvert quand on touche le widget. */
enum class WidgetTarget { HOME, TIMETABLE, HOMEWORK, GRADES, MESSAGES }

/**
 * Les neuf widgets.
 *
 * Neuf entrées dans le tiroir du lanceur, pas neuf écrans figés : chacune change de forme
 * selon la place qu'on lui laisse. « Devoirs » est un compteur en 1×1, le devoir de ce soir
 * en 2×1, la liste en 2×2. C'est pourquoi seize widgets ont pu devenir neuf sans rien
 * perdre : le compte à rebours, le devoir urgent, la prochaine évaluation, le dernier
 * message et les moyennes par matière sont devenus des **formes**, au lieu d'occuper chacune
 * une ligne dans une liste que personne ne lit jusqu'au bout.
 *
 * [natural] est la forme du widget à sa taille d'installation.
 */
enum class WidgetKind(
    val label: String,
    val description: String,
    val natural: WidgetShape,
    val target: WidgetTarget,
    /** Un contenu horaire : c'est le seul qui gagne à être dessiné à l'échelle du temps. */
    val timetable: Boolean = false,
) {
    NEXT_LESSON(
        "Prochain cours",
        "Le temps restant, la matière et la salle ; agrandi, tout ce qui reste de la journée.",
        WidgetShape.ROW,
        WidgetTarget.TIMETABLE,
        timetable = true,
    ),
    DAY(
        "Journée",
        "Début, pause méridienne et fin ; agrandi, la journée entière à l'échelle du temps.",
        WidgetShape.HOURS,
        WidgetTarget.TIMETABLE,
        timetable = true,
    ),
    WEEK(
        "Semaine",
        "La semaine en colonnes, toutes sur la même échelle horaire.",
        WidgetShape.WEEK,
        WidgetTarget.TIMETABLE,
        timetable = true,
    ),
    HOMEWORK(
        "Devoirs",
        "Combien il en reste, celui de ce soir, puis la liste par échéance.",
        WidgetShape.TILE,
        WidgetTarget.HOMEWORK,
    ),
    GRADES(
        "Notes",
        "La moyenne, la dernière note, puis les dernières notes et les moyennes par matière.",
        WidgetShape.TILE,
        WidgetTarget.GRADES,
    ),
    OBJECTIVE(
        "Objectif",
        "Ce qu'il manque pour atteindre la moyenne visée, et la note qui suffirait.",
        WidgetShape.ROW,
        WidgetTarget.GRADES,
    ),
    SCHOOL_LIFE(
        "Vie scolaire",
        "Messages non lus, absences et retards, puis la prochaine évaluation.",
        WidgetShape.TILE,
        WidgetTarget.MESSAGES,
    ),
    CHANGES(
        "Ce qui a changé",
        "Cours annulé, salle déplacée, note tombée : seulement ce qui a bougé depuis hier.",
        WidgetShape.ROW,
        WidgetTarget.HOME,
    ),
    TOMORROW(
        "Demain",
        "Les cours de demain, les devoirs à rendre, et ce qu'il faut emporter.",
        WidgetShape.ROW,
        WidgetTarget.TIMETABLE,
        timetable = true,
    ),
}

/**
 * Une ligne de liste.
 *
 * [leading] est la colonne de gauche — une heure, dans un emploi du temps. Elle est vide pour
 * les widgets qui n'ont rien à y mettre, et la colonne disparaît alors. C'est ce qui permet à
 * la matière d'occuper toute la ligne au lieu d'être coupée par une valeur posée au milieu.
 */
data class WidgetLine(
    val text: String,
    val secondary: String = "",
    val trailing: String = "",
    val accent: Long? = null,
    val leading: String = "",
)

/**
 * Un bloc de grille : sa **hauteur vaut sa durée**.
 *
 * Les trois niveaux de texte ne sont pas trois lignes garanties — c'est le gabarit qui décide
 * de ce qui tient dans la hauteur calculée. Un cours de cinquante minutes n'affiche que sa
 * matière ; un cours de deux heures a la place de son horaire, de sa salle et du professeur.
 * Un bloc qui n'a rien à mettre à un niveau le laisse vide plutôt que d'inventer.
 */
data class WidgetBlock(
    val text: String,
    val secondary: String = "",
    val detail: String = "",
    val minutes: Long,
    val accent: Long? = null,
    /** Pause, permanence, trou : fond neutre et libellé en creux. */
    val free: Boolean = false,
    /** Espace nu : un intercours de cinq minutes n'a rien à dire, mais occupe sa place. */
    val blank: Boolean = false,
)

/** Une colonne de la semaine. Toutes partagent la même échelle horaire, sinon rien ne s'aligne. */
data class WidgetColumn(
    val name: String,
    val today: Boolean,
    val blocks: List<WidgetBlock> = emptyList(),
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
    /** Grille d'une journée : les blocs se partagent la hauteur au prorata de leur durée. */
    val blocks: List<WidgetBlock> = emptyList(),
    /** Grille d'une semaine : une colonne par jour, toutes sur la même échelle. */
    val columns: List<WidgetColumn> = emptyList(),
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
                    listOf(line.leading, line.text, line.secondary, line.trailing)
                        .filter { it.isNotBlank() }
                        .joinToString(", "),
                )
            }
            blocks.filterNot { it.blank }.forEach { block ->
                add(listOf(block.text, block.secondary).filter { it.isNotBlank() }.joinToString(", "))
            }
            columns.forEach { column ->
                val content = column.blocks.filterNot { it.blank }.joinToString(", ") { it.text }
                if (content.isNotBlank()) add("${column.name} : $content")
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
        val SUBJECT_AWARE = setOf(WidgetKind.GRADES)
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
            WidgetKind.NEXT_LESSON -> when (shape) {
                WidgetShape.TILE -> countdown(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> nextLesson(kind, context)
                WidgetShape.GRID -> remainingGrid(kind, context)
                WidgetShape.LIST -> dayList(kind, context)
                WidgetShape.WEEK -> weekGrid(kind, context)
            }

            WidgetKind.DAY -> when (shape) {
                WidgetShape.TILE -> countdown(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> dayHours(kind, context)
                WidgetShape.GRID -> dayGrid(kind, context)
                WidgetShape.LIST -> dayList(kind, context)
                WidgetShape.WEEK -> weekGrid(kind, context)
            }

            WidgetKind.WEEK -> when (shape) {
                WidgetShape.TILE -> countdown(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> dayHours(kind, context)
                WidgetShape.GRID -> dayGrid(kind, context)
                WidgetShape.LIST -> weekList(kind, context)
                WidgetShape.WEEK -> weekGrid(kind, context)
            }

            WidgetKind.HOMEWORK -> when (shape) {
                WidgetShape.TILE -> homeworkCount(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> urgentHomework(kind, context)
                else -> homeworkList(kind, context)
            }

            // En hauteur, les dernières notes cèdent la place aux moyennes par matière :
            // à partir de cinq lignes, la question n'est plus « qu'ai-je eu ? » mais
            // « où est-ce que je décroche ? ».
            WidgetKind.GRADES -> when (shape) {
                WidgetShape.TILE -> average(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> lastGrade(kind, context)
                else -> if (maxLines >= SUBJECT_VIEW_LINES) {
                    subjectAverages(kind, context)
                } else {
                    gradesList(kind, context)
                }
            }

            WidgetKind.OBJECTIVE -> when (shape) {
                WidgetShape.TILE -> objectiveTile(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> objectiveRow(kind, context)
                else -> objectiveList(kind, context)
            }

            WidgetKind.SCHOOL_LIFE -> when (shape) {
                WidgetShape.TILE -> schoolLifeTile(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> schoolLifeRow(kind, context)
                else -> schoolLifeList(kind, context)
            }

            WidgetKind.CHANGES -> when (shape) {
                WidgetShape.TILE -> changesTile(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> changesRow(kind, context)
                else -> changesList(kind, context)
            }

            WidgetKind.TOMORROW -> when (shape) {
                WidgetShape.TILE -> tomorrowTile(kind, context)
                WidgetShape.ROW, WidgetShape.HOURS -> tomorrowRow(kind, context)
                WidgetShape.GRID -> tomorrowGrid(kind, context)
                else -> tomorrowList(kind, context)
            }
        }
    }

    /** À partir de cinq lignes, le widget Notes bascule sur les moyennes par matière. */
    private const val SUBJECT_VIEW_LINES = 5

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
                // La forme tuile du même widget disait déjà « estimée » ; cette forme-ci
                // affichait un chiffre recalculé avec la moyenne de classe à côté, donc avec
                // l'allure d'un bulletin.
                "calculée".takeUnless { average.official },
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
            title = when (grades.size) {
                0 -> "Aucune note"
                1 -> "1 note"
                else -> "${grades.size} notes"
            },
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
     * Chaque cours porte son **heure de début** dans une colonne étroite, à gauche, comme sur
     * un emploi du temps papier : c'est elle qu'on parcourt du regard, et la matière garde
     * toute la largeur restante. Une plage complète — « 08:00 – 09:00 » — doublerait cette
     * colonne et rognerait le nom de la matière, pour une information que la forme grille, elle,
     * porte déjà dans la hauteur des blocs. (Cette documentation promettait la plage horaire ;
     * le code faisait autre chose, et le test censé le vérifier validait les heures de début
     * sous un nom qui annonçait l'inverse.)
     *
     * La pause méridienne, en revanche, apparaît en toutes lettres, à sa place dans la journée,
     * au lieu de se deviner à un trou entre deux lignes — c'est ce qui manque le plus à une
     * liste pour ressembler à un emploi du temps.
     */
    private fun dayLines(
        lessons: List<Lesson>,
        date: LocalDate?,
        context: Context,
    ): List<WidgetLine> {
        val pause = date?.let { Insights.middayBreak(context.snapshot.lessons, it) }
        val lines = mutableListOf<WidgetLine>()
        // Un drapeau plutôt qu'une recherche sur le libellé : chercher le texte de la ligne
        // pour savoir si on l'a déjà posée casse le jour où on renomme ce texte — ce qui est
        // arrivé, et a fait apparaître la pause deux fois.
        var pauseInserted = false
        lessons.forEach { lesson ->
            if (pause != null && !pauseInserted && lines.isNotEmpty() && !lesson.start.isBefore(pause.end)) {
                pauseInserted = true
                lines += WidgetLine(
                    leading = Dates.time(pause.start),
                    text = "Pause",
                    trailing = Dates.duration(pause.minutes),
                )
            }
            lines += WidgetLine(
                // L'heure à gauche, comme sur un emploi du temps papier : c'est elle qu'on
                // parcourt du regard, et la matière garde toute la largeur restante.
                leading = Dates.time(lesson.start),
                text = lesson.subject,
                trailing = if (lesson.canceled) "annulé" else lesson.room,
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

    // ── Grilles proportionnelles ─────────────────────────────────────────────

    /** En deçà, un trou n'a rien à dire : il occupe sa place et se tait. */
    private const val NAMED_GAP_MINUTES = 25L

    /** Une journée qui déborde de ces bornes reste dessinée, mais l'échelle part de là. */
    private const val DEFAULT_DAY_START_HOUR = 8
    private const val DEFAULT_DAY_END_HOUR = 17

    /**
     * Une journée en blocs, du premier au dernier cours.
     *
     * C'est la seule construction de ce fichier qui ne produit pas des lignes : une ligne
     * traite un cours d'une heure et un cours de trois heures de la même façon, et laisse le
     * reste du widget vide. Ici la hauteur est le temps, les trous sont visibles, et un
     * widget agrandi montre plus de journée au lieu de montrer plus de blanc.
     */
    private fun dayBlocks(
        lessons: List<Lesson>,
        date: LocalDate,
        context: Context,
        from: LocalDateTime? = null,
    ): List<WidgetBlock> {
        val ordered = lessons.sortedBy { it.start }
        if (ordered.isEmpty()) return emptyList()
        // Le plan de travail du jour : un devoir par trou, jamais deux fois le même. Sans
        // lui, les trois trous d'une journée portaient tous le même exercice.
        val plan = Insights.workPlan(
            lessons = context.snapshot.lessons,
            homework = context.snapshot.homework,
            settings = context.settings,
            date = date,
        ).associateBy { it.interval.start }
        val blocks = mutableListOf<WidgetBlock>()
        var cursor = from?.coerceAtLeast(ordered.first().start) ?: ordered.first().start

        ordered.forEach { lesson ->
            if (lesson.end.isBefore(cursor) || lesson.end == cursor) return@forEach
            val gap = Duration.between(cursor, lesson.start).toMinutes()
            if (gap > 0) blocks += gapBlock(cursor, gap, plan[cursor])
            val start = maxOf(cursor, lesson.start)
            val minutes = Duration.between(start, lesson.end).toMinutes()
            if (minutes > 0) blocks += lessonBlock(lesson, minutes, context)
            cursor = lesson.end
        }
        return blocks
    }

    private fun lessonBlock(lesson: Lesson, minutes: Long, context: Context) = WidgetBlock(
        text = lesson.subject,
        secondary = listOfNotNull(
            Dates.time(lesson.start),
            lesson.room.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        detail = if (lesson.canceled) "annulé" else lesson.teacher,
        minutes = minutes,
        accent = SubjectPalette.colorFor(lesson.subject, context.settings.subjectColors),
    )

    /**
     * Un trou.
     *
     * La pause méridienne se nomme et reste libre — déjeuner est aussi une occupation. Les
     * autres portent le devoir que le plan leur a attribué : c'est ce qui transforme un blanc
     * dans l'emploi du temps en une heure utilisable.
     */
    private fun gapBlock(start: LocalDateTime, minutes: Long, slot: Insights.WorkSlot?): WidgetBlock {
        if (minutes < NAMED_GAP_MINUTES) return WidgetBlock(text = "", minutes = minutes, blank = true)
        val homework = slot?.homework
        return WidgetBlock(
            text = if (slot?.midday == true) "Pause" else "Temps libre",
            secondary = Dates.duration(minutes),
            detail = homework?.let { "${it.subject} · ${Text.preview(it.content, 30)}" }.orEmpty(),
            minutes = minutes,
            free = true,
        )
    }

    /** La journée entière — celle d'aujourd'hui, ou celle du prochain jour d'école. */
    private fun dayGrid(kind: WidgetKind, context: Context): WidgetPayload = grid(kind, context, from = null)

    /** Ce qu'il reste de la journée : la grille démarre à l'heure qu'il est. */
    private fun remainingGrid(kind: WidgetKind, context: Context): WidgetPayload =
        grid(kind, context, from = context.now)

    private fun grid(kind: WidgetKind, context: Context, from: LocalDateTime?): WidgetPayload {
        val remaining = Insights.lessonsOn(context.snapshot.lessons, context.today)
            .filter { it.end.isAfter(context.now) }
        val date = if (remaining.isNotEmpty()) {
            context.today
        } else {
            Insights.nextLessonDay(context.snapshot.lessons, context.today)
        } ?: return empty(kind, context, "EMPLOI DU TEMPS", "Aucun cours", "rien de programmé")

        val lessons = Insights.lessonsOn(context.snapshot.lessons, date)
        val summary = Insights.daySummary(context.snapshot.lessons, date)
        // La grille du jour même part de maintenant ; celle d'un autre jour part de son début,
        // sinon elle commencerait à une heure qui n'a aucun sens pour ce jour-là.
        val origin = from?.takeIf { date == context.today }
        val blocks = dayBlocks(lessons, date, context, origin)
        return payloadOf(
            kind, context,
            label = when (date) {
                context.today -> if (origin != null) "IL RESTE" else "AUJOURD'HUI"
                else -> dayLabel(date, context.today).uppercase()
            },
            title = Dates.compactDay(date).replaceFirstChar { it.uppercase() },
            subtitle = summary.start?.let { start ->
                summary.end?.let { end -> "${Dates.time(start)} – ${Dates.time(end)}" }
            }.orEmpty(),
            trailing = "${summary.lessons} cours",
            blocks = blocks,
            empty = blocks.isEmpty(),
        ).withEmptyText("Aucun cours à venir")
    }

    /**
     * La semaine, cinq ou six colonnes sur **une seule échelle horaire**.
     *
     * Chaque colonne est complétée en haut et en bas pour que toutes commencent et finissent
     * à la même heure : sans ce remplissage, les journées glisseraient les unes par rapport
     * aux autres et la comparaison — mercredi court, vendredi fini tôt — ne se lirait plus.
     *
     * Le samedi n'apparaît que s'il porte des cours : une colonne vide chaque semaine vole un
     * sixième de la largeur aux cinq autres.
     */
    private fun weekGrid(kind: WidgetKind, context: Context): WidgetPayload {
        val weekStart = Dates.startOfWeek(context.today)
        val columns = weekColumns(
            snapshot = context.snapshot,
            settings = context.settings,
            weekStart = weekStart,
            today = context.today,
        )
        if (columns.isEmpty()) return empty(kind, context, "SEMAINE", "Semaine vide", "aucun cours publié")

        val week = Insights.week(context.snapshot.lessons, weekStart)
        return payloadOf(
            kind, context,
            label = "SEMAINE",
            title = "Semaine du ${Dates.dayAndMonth(weekStart)}",
            subtitle = Dates.duration(week.sumOf { it.minutes }),
            trailing = "${week.sumOf { it.lessons }} cours",
            columns = columns,
            empty = false,
        )
    }

    /**
     * Les colonnes d'une semaine, **toutes sur la même échelle horaire**.
     *
     * Chaque colonne est complétée en haut et en bas pour que toutes commencent et finissent
     * à la même heure : sans ce remplissage, les journées glisseraient les unes par rapport
     * aux autres et la comparaison — mercredi court, vendredi fini tôt — ne se lirait plus.
     *
     * Le samedi n'apparaît que s'il porte des cours : une colonne vide chaque semaine vole un
     * sixième de la largeur aux cinq autres.
     *
     * Publique parce que l'image de semaine partageable s'en sert aussi : la mise en page
     * d'une semaine se décide à un seul endroit, et le widget comme l'image en héritent.
     */
    fun weekColumns(
        snapshot: SchoolSnapshot,
        settings: AppSettings,
        weekStart: LocalDate,
        today: LocalDate,
    ): List<WidgetColumn> {
        val days = (0..5).map { weekStart.plusDays(it.toLong()) }
        val byDay = days.associateWith { Insights.lessonsOn(snapshot.lessons, it) }
        val visible = if (byDay[days.last()].isNullOrEmpty()) days.dropLast(1) else days

        val starts = byDay.values.flatten().minOfOrNull { it.start.toLocalTime() } ?: return emptyList()
        val ends = byDay.values.flatten().maxOfOrNull { it.end.toLocalTime() } ?: return emptyList()

        val context = Context(
            snapshot = snapshot,
            settings = settings,
            now = today.atTime(starts),
            today = today,
            options = WidgetOptions(),
            shape = WidgetShape.WEEK,
            maxLines = MAX_LINES,
        )
        return visible.map { day ->
            val lessons = byDay[day].orEmpty()
            val blocks = mutableListOf<WidgetBlock>()
            val dayStart = day.atTime(starts)
            val dayEnd = day.atTime(ends)
            val head = lessons.minOfOrNull { it.start } ?: dayEnd
            val tail = lessons.maxOfOrNull { it.end } ?: dayEnd
            val lead = Duration.between(dayStart, head).toMinutes()
            if (lead > 0) blocks += WidgetBlock(text = "", minutes = lead, blank = true)
            blocks += dayBlocks(lessons, day, context)
            val trail = Duration.between(tail, dayEnd).toMinutes()
            if (trail > 0) blocks += WidgetBlock(text = "", minutes = trail, blank = true)
            WidgetColumn(
                name = Dates.dayName(day).take(3).replaceFirstChar { it.uppercase() },
                today = day == today,
                blocks = blocks,
            )
        }
    }

    // ── Objectif ─────────────────────────────────────────────────────────────

    private fun objectiveTile(kind: WidgetKind, context: Context): WidgetPayload {
        val objective = Insights.objective(context.snapshot, context.today, context.settings.averageTarget)
            ?: return empty(kind, context, "OBJECTIF", "—", "à fixer dans les réglages")
        return payloadOf(
            kind, context,
            label = "OBJECTIF",
            title = if (objective.reached) "atteint" else number(objective.gap),
            subtitle = if (objective.reached) number(objective.target) else "à combler",
        )
    }

    /**
     * L'écart, et surtout ce qui le comble.
     *
     * « Il manque 0,2 » ne dit pas quoi faire ; « un 17 en maths (coef. 4) suffit », si.
     * C'est le coefficient qui choisit la matière, pas la sympathie qu'on lui porte.
     */
    private fun objectiveRow(kind: WidgetKind, context: Context): WidgetPayload {
        val objective = Insights.objective(context.snapshot, context.today, context.settings.averageTarget)
            ?: return empty(kind, context, "OBJECTIF", "Aucun objectif", "à fixer dans les réglages")
        return payloadOf(
            kind, context,
            label = "OBJECTIF ${number(objective.target)}",
            title = if (objective.reached) "Objectif atteint" else "Il manque ${number(objective.gap)}",
            subtitle = objective.lever?.let { lever ->
                "un ${number(lever.mark)} en ${lever.subject} (coef. ${number(lever.coefficient)}) suffit"
            }.orEmpty(),
            trailing = number(objective.current),
        )
    }

    private fun objectiveList(kind: WidgetKind, context: Context): WidgetPayload {
        val objective = Insights.objective(context.snapshot, context.today, context.settings.averageTarget)
            ?: return empty(kind, context, "OBJECTIF", "Aucun objectif", "à fixer dans les réglages")
        val averages = Insights.subjectAverages(context.snapshot, context.today)
        return payloadOf(
            kind, context,
            label = "OBJECTIF ${number(objective.target)}",
            title = if (objective.reached) "Objectif atteint" else "Il manque ${number(objective.gap)}",
            // « Calculée » plutôt que « moyenne » tout court : l'écart et le levier se
            // résolvent sur les notes de la période, pas sur la moyenne du bulletin, et
            // l'établissement affiche parfois un autre chiffre. Deux mots suffisent à ne pas
            // faire passer l'une pour l'autre.
            subtitle = "moyenne calculée ${number(objective.current)}",
            // Les matières qui décrochent en premier : ce sont elles qui tiennent l'écart.
            lines = averages.sortedBy { it.second }.take(context.maxLines).map { (subject, value) ->
                val distance = value - objective.target
                WidgetLine(
                    text = subject,
                    secondary = if (distance >= 0) "au-dessus" else "${number(distance)} sous l'objectif",
                    trailing = number(value),
                    accent = SubjectPalette.colorFor(subject, context.settings.subjectColors),
                )
            },
            empty = averages.isEmpty(),
        ).withEmptyText("Aucune moyenne publiée")
    }

    // ── Ce qui a changé ──────────────────────────────────────────────────────

    /**
     * Les changements que le widget montre, quelle que soit sa forme.
     *
     * L'horizon est celui que le dépôt conserve — une semaine. Le faire varier avec la taille
     * posée donnait deux comptes au même widget : « 2 · depuis hier » en 1×1, « 11
     * changements » en 2×2, sans que rien n'ait bougé entre les deux. Une semaine, c'est
     * aussi ce qu'il faut pour qu'un retour de week-end rattrape ce qu'il a manqué.
     */
    private fun recentChanges(context: Context) = context.snapshot.changes

    private fun changesTile(kind: WidgetKind, context: Context): WidgetPayload {
        val changes = recentChanges(context)
        return payloadOf(
            kind, context,
            label = "CHANGÉ",
            title = changes.size.toString(),
            subtitle = if (changes.isEmpty()) "rien de neuf" else "sur 7 jours",
        )
    }

    private fun changesRow(kind: WidgetKind, context: Context): WidgetPayload {
        val changes = recentChanges(context)
        val first = changes.firstOrNull()
            ?: return empty(kind, context, "CE QUI A CHANGÉ", "Rien de neuf", "aucun changement")
        return payloadOf(
            kind, context,
            label = "CE QUI A CHANGÉ",
            title = first.text,
            subtitle = first.detail,
            trailing = if (changes.size > 1) "+${changes.size - 1}" else "",
            accent = first.subject.takeIf { it.isNotBlank() }
                ?.let { SubjectPalette.colorFor(it, context.settings.subjectColors) },
        )
    }

    private fun changesList(kind: WidgetKind, context: Context): WidgetPayload {
        val changes = recentChanges(context)
        return payloadOf(
            kind, context,
            label = "CE QUI A CHANGÉ",
            title = when (changes.size) {
                0 -> "Rien de neuf"
                1 -> "1 changement"
                else -> "${changes.size} changements"
            },
            subtitle = "7 derniers jours",
            lines = changes.take(context.maxLines).map { change ->
                WidgetLine(
                    // Le jour tient lieu de colonne d'heure : c'est lui qui groupe la lecture.
                    leading = shortDay(change.noticedOn, context.today),
                    text = change.text,
                    secondary = change.detail,
                    accent = change.subject.takeIf { it.isNotBlank() }
                        ?.let { SubjectPalette.colorFor(it, context.settings.subjectColors) },
                )
            },
            empty = changes.isEmpty(),
        ).withEmptyText("Rien n'a bougé depuis une semaine")
    }

    // ── Demain ───────────────────────────────────────────────────────────────

    private fun tomorrowDate(context: Context): LocalDate? =
        Insights.nextLessonDay(context.snapshot.lessons, context.today)

    /** Ce qu'il faut emporter : les matières de demain, pas un inventaire inventé. */
    private fun satchel(lessons: List<Lesson>, homework: List<HomeworkItem>): String {
        val subjects = Insights.satchel(lessons)
        if (subjects.isEmpty()) return ""
        val list = subjects.joinToString(", ")
        return if (homework.isEmpty()) list else "$list · ${homework.size} à rendre"
    }

    private fun tomorrowTile(kind: WidgetKind, context: Context): WidgetPayload {
        val date = tomorrowDate(context)
            ?: return empty(kind, context, "DEMAIN", "—", "rien de prévu")
        val summary = Insights.daySummary(context.snapshot.lessons, date)
        return payloadOf(
            kind, context,
            label = dayLabel(date, context.today).uppercase(),
            title = summary.start?.let { Dates.time(it) } ?: "—",
            subtitle = "${summary.lessons} cours",
        )
    }

    private fun tomorrowRow(kind: WidgetKind, context: Context): WidgetPayload {
        val date = tomorrowDate(context)
            ?: return empty(kind, context, "DEMAIN", "Rien de prévu", "aucun cours à venir")
        val lessons = Insights.lessonsOn(context.snapshot.lessons, date)
        val summary = Insights.daySummary(context.snapshot.lessons, date)
        val homework = Insights.homeworkDueOn(context.snapshot.homework, date)
        return payloadOf(
            kind, context,
            label = nextDayLabel(date, context.today),
            title = listOfNotNull(
                summary.start?.let { Dates.time(it) },
                summary.end?.let { Dates.time(it) },
            ).joinToString(" – ").ifBlank { "Journée libre" },
            subtitle = satchel(lessons, homework),
            trailing = "${summary.lessons} cours",
        )
    }

    private fun tomorrowGrid(kind: WidgetKind, context: Context): WidgetPayload {
        val date = tomorrowDate(context)
            ?: return empty(kind, context, "DEMAIN", "Rien de prévu", "aucun cours à venir")
        val lessons = Insights.lessonsOn(context.snapshot.lessons, date)
        val homework = Insights.homeworkDueOn(context.snapshot.homework, date)
        val summary = Insights.daySummary(context.snapshot.lessons, date)
        return payloadOf(
            kind, context,
            label = nextDayLabel(date, context.today),
            title = Dates.compactDay(date).replaceFirstChar { it.uppercase() },
            subtitle = satchel(lessons, homework),
            trailing = "${summary.lessons} cours",
            blocks = dayBlocks(lessons, date, context),
            empty = lessons.isEmpty(),
        ).withEmptyText("Journée libre")
    }

    private fun tomorrowList(kind: WidgetKind, context: Context): WidgetPayload {
        val date = tomorrowDate(context)
            ?: return empty(kind, context, "DEMAIN", "Rien de prévu", "aucun cours à venir")
        val lessons = Insights.lessonsOn(context.snapshot.lessons, date)
        val homework = Insights.homeworkDueOn(context.snapshot.homework, date)
        return payloadOf(
            kind, context,
            label = nextDayLabel(date, context.today),
            title = Dates.compactDay(date).replaceFirstChar { it.uppercase() },
            subtitle = satchel(lessons, homework),
            lines = lessons.take(context.maxLines).map { lesson ->
                WidgetLine(
                    leading = Dates.time(lesson.start),
                    text = lesson.subject,
                    trailing = if (lesson.canceled) "annulé" else lesson.room,
                    accent = SubjectPalette.colorFor(lesson.subject, context.settings.subjectColors),
                )
            },
            empty = lessons.isEmpty(),
        ).withEmptyText("Journée libre")
    }

    // ── Vie scolaire et messagerie ───────────────────────────────────────────

    /**
     * En 1×1, un seul compteur : celui qui appelle une action.
     *
     * Les messages non lus passent devant, parce qu'ils attendent une réponse ; à défaut, les
     * absences à justifier. L'en-tête dit toujours lequel — un chiffre sans étiquette qui
     * change de sens selon le jour serait pire que pas de chiffre du tout.
     */
    private fun schoolLifeTile(kind: WidgetKind, context: Context): WidgetPayload {
        val unread = Insights.unreadMessages(context.snapshot.inbox, context.settings)
        if (unread.isNotEmpty()) {
            return payloadOf(kind, context, label = "MESSAGES", title = unread.size.toString(), subtitle = "non lus")
        }
        val unjustified = recentSchoolLife(context)
            .count { it.kind == SchoolLifeKind.ABSENCE && !it.justified }
        if (unjustified > 0) {
            return payloadOf(
                kind, context,
                label = "À JUSTIFIER",
                title = unjustified.toString(),
                subtitle = if (unjustified == 1) "absence" else "absences",
            )
        }
        val events = recentSchoolLife(context)
        return payloadOf(
            kind, context,
            label = "VIE SCO.",
            title = events.size.toString(),
            subtitle = if (events.isEmpty()) "rien ce mois" else "ce mois",
        )
    }

    /** Les trois compteurs côte à côte : messages, retards, absences. */
    private fun schoolLifeRow(kind: WidgetKind, context: Context): WidgetPayload {
        val unread = Insights.unreadMessages(context.snapshot.inbox, context.settings).size
        val events = recentSchoolLife(context)
        val late = events.count { it.kind == SchoolLifeKind.LATE }
        val absences = events.count { it.kind == SchoolLifeKind.ABSENCE }
        return payloadOf(
            kind, context,
            label = "VIE SCOLAIRE",
            title = "$unread message${if (unread > 1) "s" else ""} non lu${if (unread > 1) "s" else ""}",
            subtitle = "$late retard${if (late > 1) "s" else ""} · $absences absence${if (absences > 1) "s" else ""}",
            trailing = Dates.compactDay(context.today),
            lines = listOf(
                WidgetLine(text = "messages", trailing = unread.toString()),
                WidgetLine(text = "retards", trailing = late.toString()),
                WidgetLine(text = "absences", trailing = absences.toString()),
            ),
        )
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
        blocks: List<WidgetBlock> = emptyList(),
        columns: List<WidgetColumn> = emptyList(),
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
        blocks = blocks,
        columns = columns,
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
    /**
     * L'étiquette du widget « Demain ».
     *
     * Il montre le prochain jour d'école, qui le vendredi soir est un lundi : écrire
     * « DEMAIN · LUNDI » se contredit dans la même ligne. On ne dit « demain » que quand
     * c'en est un ; sinon le nom du jour suffit, et il est exact.
     */
    private fun nextDayLabel(date: LocalDate, today: LocalDate): String =
        if (date == today.plusDays(1)) "DEMAIN · ${Dates.dayName(date).uppercase()}"
        else Dates.dayName(date).uppercase()

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
    fun number(value: Double): String = Text.number(value)
}
