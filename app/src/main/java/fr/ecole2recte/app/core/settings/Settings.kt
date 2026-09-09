package fr.ecole2recte.app.core.settings

import fr.ecole2recte.app.core.format.Text

/** Choix de thème, indépendant du réglage système pour ceux qui veulent forcer un mode. */
enum class ThemeChoice(val label: String) {
    SYSTEM("Comme le système"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

/** Ce qui décide des couleurs d'un widget d'écran d'accueil. */
enum class WidgetTheme(val label: String) {
    SYSTEM("Comme le système"),
    LIGHT("Toujours clair"),
    DARK("Toujours sombre"),
    CUSTOM("Personnalisées"),
}

/**
 * Les couleurs des widgets.
 *
 * Un widget ne vit pas dans l'application mais sur l'écran d'accueil, devant un fond d'écran
 * choisi par quelqu'un d'autre. Suivre le thème du téléphone est un bon défaut, ce n'est pas
 * toujours le bon choix : un fond d'écran clair rend un widget sombre illisible, et
 * inversement. D'où les deux réglages forcés, et la possibilité de poser ses propres
 * couleurs.
 *
 * Seuls le fond et le texte sont réglables. Le texte secondaire en est dérivé, et les
 * couleurs de matière restent celles de l'application : elles ont leur propre réglage, et
 * les voir changer d'un écran à l'autre serait pire que tout.
 */
data class WidgetColorSettings(
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Fond, en ARGB. `null` : celui du thème retenu. */
    val background: Long? = null,
    /** Texte principal, en ARGB. `null` : celui du thème retenu. */
    val ink: Long? = null,
) {
    val custom: Boolean get() = theme == WidgetTheme.CUSTOM

    /**
     * Le widget doit-il se dessiner en sombre ?
     *
     * En couleurs libres, c'est la **clarté du fond choisi** qui décide, et non le thème du
     * téléphone : c'est elle qui dit si un texte clair ou sombre sera lisible dessus. Sans
     * cela, un fond noir choisi à la main hériterait des accents du thème clair.
     */
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        WidgetTheme.SYSTEM -> systemDark
        WidgetTheme.LIGHT -> false
        WidgetTheme.DARK -> true
        WidgetTheme.CUSTOM -> background?.let { Contrast.luminance(it) < 0.5 } ?: systemDark
    }

    /** Le texte lisible sur le fond choisi, quand la personne n'en a pas désigné un. */
    fun inkOrDefault(): Long? = ink ?: background?.let { Contrast.readableInk(it) }
}

/**
 * De quoi ne jamais proposer une combinaison illisible.
 *
 * Une palette libre laisse écrire du gris sur du gris. Ces deux fonctions donnent la clarté
 * d'une couleur et le texte qui se lit dessus — c'est ce qui permet à l'écran de réglages de
 * prévenir plutôt que de laisser faire.
 */
object Contrast {

    /** Clarté perçue, de 0 (noir) à 1 (blanc). Pondérée comme l'œil, pas comme la machine. */
    fun luminance(argb: Long): Double {
        val red = ((argb shr 16) and 0xFF) / 255.0
        val green = ((argb shr 8) and 0xFF) / 255.0
        val blue = (argb and 0xFF) / 255.0
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun channel(value: Double): Double =
        if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)

    /** Le rapport de contraste entre deux couleurs, tel que le définit le WCAG. */
    fun ratio(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Noir ou blanc, celui des deux qui se lit sur ce fond. */
    fun readableInk(background: Long): Long =
        if (ratio(background, 0xFF000000L) >= ratio(background, 0xFFFFFFFFL)) 0xFF000000L else 0xFFFFFFFFL

    /** En deçà, le texte est pénible à lire. C'est le seuil « AA » pour un texte courant. */
    const val MINIMUM_RATIO = 4.5
}

/** Les couleurs proposées pour les widgets : assez pour choisir, pas assez pour se perdre. */
object WidgetPaletteChoices {

    val BACKGROUNDS: List<Long> = listOf(
        0xFFFFFFFF, // blanc
        0xFFF3F4F8, // gris très clair
        0xFFE8EAF6, // bleuté clair
        0xFFFDF3E3, // crème
        0xFF1B2430, // ardoise
        0xFF11131A, // presque noir
        0xFF000000, // noir
        0xFF1E2A24, // vert profond
        0xFF2A1F33, // prune
        0xFF23304A, // bleu nuit
    )

    val INKS: List<Long> = listOf(
        0xFF000000, // noir
        0xFF18202B, // encre
        0xFF3F4B5B, // ardoise
        0xFFFFFFFF, // blanc
        0xFFEDF1F7, // blanc cassé
        0xFFC7D0DC, // gris clair
    )
}

/**
 * Les briques de l'accueil.
 *
 * L'accueil n'est pas une page figée : chacun choisit ce qu'il veut voir en premier et
 * dans quel ordre. L'énumération porte l'identité stable (utilisée dans les réglages
 * enregistrés) ; l'ordre et la visibilité vivent dans [AppSettings].
 */
enum class HomeWidget(val label: String, val description: String) {
    NEXT_CLASS("Prochain cours", "Le cours en cours ou celui qui arrive, avec sa progression."),
    URGENT("À ne pas oublier", "Le devoir le plus urgent, mis en avant."),
    TODAY("Journée", "La suite des cours du jour."),
    HOMEWORK("Devoirs", "Les prochains devoirs à rendre."),
    EXAMS("Évaluations", "Les devoirs surveillés annoncés, avec le compte à rebours."),
    GRADES("Notes", "Moyenne générale, tendance et dernières notes."),
    SUBJECT_AVERAGES("Moyennes par matière", "Les moyennes de la période, matière par matière."),
    MESSAGES("Messages", "Les derniers messages reçus."),
    WEEK("Semaine", "La charge de chaque jour, du lundi au samedi."),
    TOMORROW("Demain", "Les cours et les devoirs de demain, pour préparer son sac."),
    SCHOOL_LIFE("Vie scolaire", "Absences, retards et sanctions du mois."),
    NEWS("Actualités", "Ce que l'établissement vient de publier."),
    ;

    companion object {
        val DEFAULT_ORDER: List<HomeWidget> = listOf(
            NEXT_CLASS, URGENT, TODAY, HOMEWORK, EXAMS, GRADES, SUBJECT_AVERAGES, MESSAGES,
            WEEK, TOMORROW, SCHOOL_LIFE, NEWS,
        )
    }
}

data class HomeWidgetSetting(val widget: HomeWidget, val visible: Boolean = true)

/**
 * Ce que l'application a le droit de signaler.
 *
 * Une notification scolaire non désirée est pire qu'aucune notification : elle apprend à
 * l'élève à balayer sans lire. Chaque type est donc désactivable séparément, et rien n'est
 * signalé deux fois.
 */
data class NotificationSettings(
    val enabled: Boolean = true,
    val grades: Boolean = true,
    val homework: Boolean = true,
    val messages: Boolean = true,
    val examReminder: Boolean = true,
    /** Cours annulé, déplacé ou ajouté : le seul changement qu'on veut connaître le matin. */
    val timetable: Boolean = true,
) {
    val anyEnabled: Boolean
        get() = enabled && (grades || homework || messages || examReminder || timetable)
}

/**
 * Tous les réglages et états locaux, dans un seul objet immuable.
 *
 * Y compris les états que le serveur ne garde pas toujours (devoir coché hors ligne,
 * message lu) : c'est ce qui permet à l'application de rester juste sans réseau.
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Couleur choisie par matière, en ARGB, indexée par clé normalisée de matière. */
    val subjectColors: Map<String, Long> = emptyMap(),
    val homeWidgets: List<HomeWidgetSetting> = HomeWidget.DEFAULT_ORDER.map { HomeWidgetSetting(it) },
    val doneHomework: Set<String> = emptySet(),
    val undoneHomework: Set<String> = emptySet(),
    val readMessages: Set<String> = emptySet(),
    /**
     * Conversations rangées à la Corbeille, par identifiant.
     *
     * Purement local : ÉcoleDirecte n'offre aucune suppression, et l'application n'écrit
     * rien pour autant. C'est un classement pour s'y retrouver, pas un effacement — la
     * Corbeille le dit d'ailleurs en toutes lettres.
     */
    val binnedMessages: Set<String> = emptySet(),
    val selectedAccountId: String? = null,
    /** Affiche les diagnostics réseau sur l'écran de connexion et dans les réglages. */
    val showDiagnostics: Boolean = false,
    val notifications: NotificationSettings = NotificationSettings(),
    /**
     * Ce qui a déjà été signalé, pour ne jamais notifier deux fois la même note. La liste
     * est bornée : au-delà, les plus anciens identifiants ne peuvent de toute façon plus
     * revenir dans une synchronisation.
     */
    val notified: List<String> = emptyList(),
    /** Verrouillage de l'application par empreinte ou code de l'appareil. */
    val biometricLock: Boolean = false,
    /** Reprendre les couleurs du fond d'écran (Android 12+) au lieu de l'indigo de la marque. */
    val dynamicColor: Boolean = false,
    /**
     * La dernière connexion était celle d'un parent. Sert à rouvrir l'écran de connexion
     * dans le bon mode — la question de sécurité porte alors sur la date de naissance de
     * l'enfant, pas sur celle du parent, et l'écran doit le dire avant qu'on la saisisse.
     */
    val parentAccount: Boolean = false,
    /** Couleurs des widgets d'écran d'accueil, indépendantes du thème de l'application. */
    val widgetColors: WidgetColorSettings = WidgetColorSettings(),
) {

    /** Widgets visibles, dans l'ordre choisi ; les nouveaux widgets arrivent à la fin. */
    val visibleWidgets: List<HomeWidget>
        get() = orderedWidgets.filter { it.visible }.map { it.widget }

    /**
     * Ordre complet, complété par les widgets ajoutés depuis le dernier enregistrement.
     * Sans ça, une mise à jour de l'application ferait disparaître les nouveautés.
     */
    val orderedWidgets: List<HomeWidgetSetting>
        get() {
            val known = homeWidgets.map { it.widget }.toSet()
            return homeWidgets + HomeWidget.DEFAULT_ORDER.filterNot { it in known }.map { HomeWidgetSetting(it) }
        }

    fun move(widget: HomeWidget, offset: Int): AppSettings {
        val current = orderedWidgets.toMutableList()
        val index = current.indexOfFirst { it.widget == widget }
        val target = index + offset
        if (index < 0 || target !in current.indices) return this
        val moved = current.removeAt(index)
        current.add(target, moved)
        return copy(homeWidgets = current)
    }

    fun toggleWidget(widget: HomeWidget): AppSettings = copy(
        homeWidgets = orderedWidgets.map {
            if (it.widget == widget) it.copy(visible = !it.visible) else it
        },
    )

    fun withSubjectColor(subject: String, color: Long?): AppSettings {
        val key = Text.normalizeKey(subject)
        val colors = subjectColors.toMutableMap()
        if (color == null) colors.remove(key) else colors[key] = color
        return copy(subjectColors = colors)
    }

    /**
     * État « fait » d'un devoir : le choix local prime sur la valeur du serveur, sinon un
     * devoir coché hors ligne redeviendrait à faire à la synchronisation suivante.
     */
    fun isHomeworkDone(id: String, remoteDone: Boolean): Boolean = when {
        id in doneHomework -> true
        id in undoneHomework -> false
        else -> remoteDone
    }

    fun withHomeworkDone(id: String, done: Boolean): AppSettings = copy(
        doneHomework = if (done) doneHomework + id else doneHomework - id,
        undoneHomework = if (done) undoneHomework - id else undoneHomework + id,
    )

    fun hasBeenNotified(id: String): Boolean = id in notified

    /** Retient ce qui vient d'être signalé, sans laisser la liste grossir sans fin. */
    fun withNotified(ids: Collection<String>): AppSettings {
        if (ids.isEmpty()) return this
        val merged = (notified + ids).distinct()
        return copy(notified = merged.takeLast(MAX_NOTIFIED))
    }

    fun isMessageRead(id: String, remoteRead: Boolean): Boolean = remoteRead || id in readMessages

    fun withMessageRead(id: String): AppSettings = copy(readMessages = readMessages + id)

    fun isMessageBinned(id: String): Boolean = id in binnedMessages

    /** Range une conversation à la Corbeille, ou l'en ressort. Rien ne quitte l'appareil. */
    fun withMessageBinned(id: String, binned: Boolean): AppSettings =
        copy(binnedMessages = if (binned) binnedMessages + id else binnedMessages - id)

    private companion object {
        const val MAX_NOTIFIED = 400
    }
}

/**
 * Palette des matières.
 *
 * Une matière garde la même couleur partout — accueil, emploi du temps, devoirs, notes —
 * parce que c'est cette constance qui rend une semaine lisible d'un coup d'œil. Sans choix
 * explicite, la couleur est dérivée du nom : stable d'un appareil à l'autre, et jamais
 * deux matières voisines de la même teinte par hasard.
 */
object SubjectPalette {

    val COLORS: List<Long> = listOf(
        0xFF7779E8, // indigo
        0xFF63A6E9, // bleu
        0xFFEFA36A, // orange
        0xFF5CBD92, // vert
        0xFFE87B98, // rose
        0xFF9B7BE8, // violet
        0xFF4FBFC4, // turquoise
        0xFFD9A441, // ambre
        0xFF6F8FE8, // bleu profond
        0xFFC77BD1, // mauve
        0xFF71B14C, // vert olive
        0xFFE0705F, // terracotta
    )

    fun defaultColor(subject: String): Long {
        val key = Text.normalizeKey(subject)
        if (key.isEmpty()) return COLORS.first()
        var hash = 0
        key.forEach { character -> hash = (hash * 31 + character.code) and 0x7FFFFFFF }
        return COLORS[hash % COLORS.size]
    }

    fun colorFor(subject: String, overrides: Map<String, Long>): Long =
        overrides[Text.normalizeKey(subject)] ?: defaultColor(subject)
}
