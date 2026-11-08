package fr.ecole2recte.app.domain

/** Ce qui remplace une valeur masquée. */
const val MASKED = "•••"

/**
 * Mode « montrer à quelqu'un » : masque les notes (et, en option, les messages) partout, jusqu'à ce
 * qu'on le désactive. Fonctions pures qui transforment les modèles d'affichage : la même logique
 * sert l'accueil, la liste des notes, le simulateur, les courbes, la recherche et la liste des messages.
 * Les données enregistrées ne sont jamais modifiées.
 */
data class PrivacyMode(val enabled: Boolean = false, val hideMessages: Boolean = false) {
    val hidesGrades: Boolean get() = enabled
    val hidesMessages: Boolean get() = enabled && hideMessages

    /** Un texte de note (« 14,5/20 », « 12,3 ») ou « •••» si le mode est actif. */
    fun grade(text: String): String = if (hidesGrades) MASKED else text

    fun mask(entry: GradeEntry): GradeEntry =
        if (!hidesGrades) entry
        else entry.copy(value = null, rawValue = MASKED, classAverage = null, minimum = null, maximum = null)

    fun mask(line: SubjectAverage): SubjectAverage =
        if (!hidesGrades) line
        else line.copy(
            average = MASKED,
            classAverage = null,
            grades = line.grades.map(::mask),
            appreciation = "",
            minimum = null,
            maximum = null,
            verdict = null,
            classAverageValue = null,
            minimumValue = null,
            maximumValue = null,
            classPosition = null,
        )

    fun mask(overview: GradeOverview): GradeOverview =
        if (!hidesGrades) overview
        else overview.copy(
            average = MASKED,
            trend = "",
            bySubject = overview.bySubject.map(::mask),
            recent = overview.recent.map(::mask),
            all = overview.all.map(::mask),
            evolution = emptyList(),
            classAverage = null,
            periods = overview.periods.map { it.copy(average = it.average?.let { _ -> MASKED }, value = null) },
            appreciation = "",
            verdict = null,
            classAverageValue = null,
            classPosition = null,
        )

    fun mask(child: ChildSummary): ChildSummary =
        if (!hidesGrades) child else child.copy(average = child.average?.let { MASKED })

    fun mask(highlight: Highlight): Highlight = when {
        highlight.kind == HighlightKind.Grade && hidesGrades ->
            highlight.copy(detail = "", value = highlight.value?.let { MASKED }, valueSub = null)
        highlight.kind == HighlightKind.Message && hidesMessages ->
            highlight.copy(detail = MASKED)
        else -> highlight
    }

    fun mask(home: HomeData): HomeData =
        if (!enabled) home
        else home.copy(
            grades = mask(home.grades),
            highlights = home.highlights.map(::mask),
            family = home.family.map(::mask),
        )

    fun mask(message: MessagePreview): MessagePreview =
        if (!hidesMessages) message else message.copy(sender = MASKED, preview = MASKED)

    fun maskMessages(messages: List<MessagePreview>): List<MessagePreview> =
        if (!hidesMessages) messages else messages.map(::mask)

    fun mask(group: SearchGroup): SearchGroup {
        val hidden = when (group.kind) {
            SearchKind.Grade -> hidesGrades
            SearchKind.Message -> hidesMessages
            else -> false
        }
        if (!hidden) return group
        return group.copy(hits = group.hits.map { SearchHit(it.kind, MASKED, "") })
    }
}
