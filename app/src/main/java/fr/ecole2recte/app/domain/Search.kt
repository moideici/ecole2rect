package fr.ecole2recte.app.domain

import java.text.Normalizer
import java.time.LocalDate

/**
 * Recherche globale, **pure** et locale : elle ne lit que ce qui est déjà enregistré sur le téléphone
 * ([SearchCorpus]) et n'envoie rien nulle part.
 */
enum class SearchKind(val label: String, val tab: String) {
    Homework("Devoirs", "agenda_homework"),
    Message("Messages", "messages"),
    Grade("Notes", "notes"),
    Lesson("Cours", "agenda"),
}

/** Tout ce qui peut être cherché : les données déjà en cache, sans aucune requête. */
data class SearchCorpus(
    val homework: List<Homework> = emptyList(),
    val messages: List<MessagePreview> = emptyList(),
    val grades: List<GradeEntry> = emptyList(),
    val lessons: List<ScheduleEntry> = emptyList(),
)

data class SearchHit(val kind: SearchKind, val title: String, val subtitle: String)

/** Les résultats d'un type ; [total] peut dépasser le nombre de [hits] montrés. */
data class SearchGroup(val kind: SearchKind, val hits: List<SearchHit>, val total: Int)

object TextSearch {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val spaces = Regex("\\s+")

    /** Minuscules, sans accents, apostrophes et ligatures simplifiées, espaces réduits : « Éléphant » = « elephant ». */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace("œ", "oe").replace("æ", "ae")
            .replace('’', '\'').replace('ʼ', '\'')
            .replace(spaces, " ")
            .trim()

    /** Les mots de la recherche ; tous doivent se retrouver (dans n'importe quel ordre). */
    fun tokens(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    /** Vrai si chaque mot de [tokens] apparaît dans l'un des [fields]. */
    fun matches(tokens: List<String>, fields: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val haystack = fields.joinToString(" ") { normalize(it) }
        return tokens.all { it in haystack }
    }

    /** Classe un résultat : un mot qui commence un champ vaut mieux qu'un mot au milieu d'une phrase. */
    fun rank(tokens: List<String>, fields: List<String>): Int =
        tokens.count { token -> fields.any { field -> normalize(field).split(' ', '\'', '-').any { it.startsWith(token) } } }
}

object GlobalSearch {
    /** Nombre maximal de résultats montrés par type ; le reste est compté. */
    const val PER_KIND = 8

    /** Cherche [query] ; renvoie les types qui ont au moins un résultat, dans l'ordre des onglets. */
    fun search(corpus: SearchCorpus, query: String, today: LocalDate, perKind: Int = PER_KIND): List<SearchGroup> {
        val tokens = TextSearch.tokens(query)
        if (tokens.isEmpty()) return emptyList()

        fun <T> pick(items: List<T>, fields: (T) -> List<String>, hit: (T) -> SearchHit): List<SearchHit> =
            items.filter { TextSearch.matches(tokens, fields(it)) }
                .sortedByDescending { TextSearch.rank(tokens, fields(it)) } // tri stable : l'ordre d'origine départage
                .map(hit)

        val groups = listOf(
            SearchKind.Homework to pick(
                corpus.homework,
                { listOf(it.subject, it.title, it.content) },
                { SearchHit(SearchKind.Homework, "${it.subject} · ${it.title}", "Pour ${it.due}" + if (it.done) " · fait" else "") },
            ),
            SearchKind.Message to pick(
                corpus.messages,
                { listOf(it.sender, it.preview) },
                { SearchHit(SearchKind.Message, it.sender.ifBlank { "Message" }, it.preview + if (it.dateLabel.isNotBlank()) " · ${it.dateLabel}" else "") },
            ),
            SearchKind.Grade to pick(
                corpus.grades,
                { listOf(it.subject, it.label) },
                { gradeHit(it) },
            ),
            SearchKind.Lesson to lessonHits(corpus.lessons, tokens, today),
        )
        return groups.filter { it.second.isNotEmpty() }.map { (kind, hits) -> SearchGroup(kind, hits.take(perKind), hits.size) }
    }

    private fun gradeHit(grade: GradeEntry): SearchHit {
        val value = grade.value?.let { "${trim(it)}/${trim(grade.scale)}" } ?: RawGrade.describe(grade.rawValue)
        val details = listOf(grade.label.takeIf { it.isNotBlank() }, value, grade.date?.take(10)?.takeIf { it.isNotBlank() })
        return SearchHit(SearchKind.Grade, grade.subject, details.filterNotNull().joinToString(" · "))
    }

    /** Un cours revient chaque semaine : un seul résultat par matière et professeur, avec le prochain créneau. */
    private fun lessonHits(lessons: List<ScheduleEntry>, tokens: List<String>, today: LocalDate): List<SearchHit> {
        val matching = lessons.filter { TextSearch.matches(tokens, listOf(it.subject.name, it.subject.teacher, it.subject.room)) }
        return matching.groupBy { it.subject.name to it.subject.teacher }.values
            .map { group ->
                val next = group.filter { it.date >= today.toString() && !it.canceled }.minByOrNull { it.date + it.start }
                    ?: group.maxByOrNull { it.date + it.start }!!
                val who = listOf(next.subject.teacher, next.subject.room).filter { it.isNotBlank() }.joinToString(" · ")
                val slot = next.whenLabel + if (next.canceled) " · annulé" else ""
                val title = if (who.isBlank()) next.subject.name else "${next.subject.name} · ${next.subject.teacher.ifBlank { next.subject.room }}"
                SearchHit(SearchKind.Lesson, title, slot)
            }
            .sortedByDescending { TextSearch.rank(tokens, listOf(it.title)) }
    }

    private fun trim(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.FRENCH, "%.1f", value)
}
