package fr.ecole2recte.app.core.settings

import fr.ecole2recte.app.core.format.Text

/** Choix de thème, indépendant du réglage système pour ceux qui veulent forcer un mode. */
enum class ThemeChoice(val label: String) {
    SYSTEM("Comme le système"),
    LIGHT("Clair"),
    DARK("Sombre"),
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
    ;

    companion object {
        val DEFAULT_ORDER: List<HomeWidget> = listOf(
            NEXT_CLASS, URGENT, TODAY, HOMEWORK, EXAMS, GRADES, SUBJECT_AVERAGES, MESSAGES,
            WEEK, TOMORROW, SCHOOL_LIFE,
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
) {
    val anyEnabled: Boolean get() = enabled && (grades || homework || messages || examReminder)
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
