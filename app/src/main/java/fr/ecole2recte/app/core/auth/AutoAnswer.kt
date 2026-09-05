package fr.ecole2recte.app.core.auth

import fr.ecole2recte.app.core.format.Text
import java.time.LocalDate

/**
 * Ce que l'application a le droit de répondre toute seule à ÉcoleDirecte.
 *
 * La promesse est « on se connecte une fois » : une question de sécurité reposée trois mois
 * plus tard, au milieu d'une synchronisation de fond, ne doit pas obliger à rouvrir
 * l'application pour cliquer sur la bonne case. Mais répondre à la place de quelqu'un
 * demande des garde-fous, et ils sont tous ici :
 *
 *  1. **rien n'est deviné.** Une réponse automatique vient soit d'une date de naissance
 *     saisie par l'utilisateur, soit d'une réponse qu'il a lui-même validée auparavant ;
 *  2. **rien n'est approché.** La réponse retenue doit correspondre exactement à l'une des
 *     propositions du serveur, sinon la question est posée ;
 *  3. **rien n'est enchaîné.** Une seule tentative automatique par connexion : refusée, elle
 *     est oubliée et la main revient à l'utilisateur ;
 *  4. **rien n'est éternel.** Une réponse mémorisée expire au 1er septembre suivant, parce
 *     qu'une classe change d'année en année et qu'une vieille réponse deviendrait fausse.
 */
object AutoAnswer {

    /** Pourquoi cette réponse a été retenue — l'interface le dit, l'utilisateur peut refuser. */
    enum class Source {
        /** Reconstituée à partir de la date de naissance saisie. */
        BIRTH_DATE,

        /** Déjà validée par l'utilisateur lors d'une connexion précédente. */
        REMEMBERED,
    }

    data class Decision(val answer: String, val source: Source)

    fun decide(
        question: String?,
        choices: List<String>,
        birthDates: BirthDates,
        memory: AnswerMemory,
        today: LocalDate,
    ): Decision? {
        if (question.isNullOrBlank()) return null
        SecurityAnswer.suggest(question, choices, birthDates.forQuestion(question))
            ?.let { return Decision(it, Source.BIRTH_DATE) }
        return memory.answerFor(question, choices, today)?.let { Decision(it, Source.REMEMBERED) }
    }
}

/**
 * Les dates de naissance connues, celles des enfants suivis.
 *
 * Un compte parent voit plusieurs enfants, et la question porte sur l'un d'eux — jamais sur
 * le parent. On range donc une date par prénom, et la question désigne elle-même l'enfant
 * concerné. La clé vide est la date saisie à la connexion, avant de savoir qui est suivi.
 */
data class BirthDates(val entries: Map<String, SecurityAnswer.BirthDate> = emptyMap()) {

    val isEmpty: Boolean get() = entries.values.none { it.isFilled }

    fun forQuestion(question: String): SecurityAnswer.BirthDate {
        // Le prénom est cherché comme un mot entier : « Anne » ne doit pas être reconnue au
        // milieu de « Marianne », sans quoi une fratrie verrait ses dates échangées. Et si
        // deux prénoms correspondent, le plus long l'emporte — « Marie-Anne » avant « Anne ».
        val words = " ${Text.normalizeWords(question)} "
        val named = entries.entries
            .filter { (name, date) -> name.isNotEmpty() && date.isFilled && words.contains(" $name ") }
            .maxByOrNull { it.key.length }
        if (named != null) return named.value
        // Sans prénom reconnu : la date saisie à la connexion, ou l'unique date connue —
        // au-delà, on ne choisit pas à la place de l'utilisateur.
        entries[DEFAULT_KEY]?.takeIf { it.isFilled }?.let { return it }
        val filled = entries.values.filter { it.isFilled }
        return filled.singleOrNull() ?: SecurityAnswer.BirthDate()
    }

    fun with(name: String, date: SecurityAnswer.BirthDate): BirthDates {
        val key = keyOf(name)
        val updated = entries.toMutableMap()
        if (date.isFilled) updated[key] = date else updated.remove(key)
        return BirthDates(updated)
    }

    fun forName(name: String): SecurityAnswer.BirthDate =
        entries[keyOf(name)] ?: SecurityAnswer.BirthDate()

    companion object {
        const val DEFAULT_KEY = ""

        fun keyOf(name: String): String = Text.normalizeWords(name)

        fun of(date: SecurityAnswer.BirthDate): BirthDates =
            if (date.isFilled) BirthDates(mapOf(DEFAULT_KEY to date)) else BirthDates()
    }
}

/** Une réponse validée par l'utilisateur, rejouable tant qu'elle reste vraie. */
data class RememberedAnswer(
    /** Question normalisée : la formulation exacte varie d'un établissement à l'autre. */
    val question: String,
    val answer: String,
    /** Dernier jour de validité ; passé cette date, la question est reposée. */
    val validUntil: LocalDate,
    /** La question telle qu'elle a été posée, pour pouvoir la relire dans les réglages. */
    val label: String = "",
) {
    val readableQuestion: String get() = label.ifBlank { question }

    fun isValid(today: LocalDate): Boolean = !today.isAfter(validUntil)
}

/**
 * Les réponses déjà validées.
 *
 * « En quelle classe est votre enfant ? » a une réponse stable pendant toute l'année
 * scolaire : la redemander à chaque expiration de jeton n'apporte aucune sécurité, seulement
 * une connexion manuelle de plus. Elle est donc mémorisée — jusqu'à la rentrée suivante,
 * puisque c'est là qu'elle cesse d'être vraie.
 */
data class AnswerMemory(val answers: List<RememberedAnswer> = emptyList()) {

    fun valid(today: LocalDate): List<RememberedAnswer> = answers.filter { it.isValid(today) }

    /**
     * La proposition du serveur correspondant à la réponse mémorisée.
     *
     * On renvoie la formulation du serveur, pas la nôtre : c'est elle qu'il attend, à
     * l'accent et à la majuscule près.
     */
    fun answerFor(question: String, choices: List<String>, today: LocalDate): String? {
        val key = Text.normalizeKey(question)
        val remembered = answers.firstOrNull { it.question == key && it.isValid(today) } ?: return null
        if (choices.isEmpty()) return remembered.answer
        val target = Text.normalizeKey(remembered.answer)
        return choices.firstOrNull { Text.normalizeKey(it) == target }
    }

    fun remembering(question: String, answer: String, today: LocalDate): AnswerMemory {
        if (question.isBlank() || answer.isBlank()) return this
        val key = Text.normalizeKey(question)
        val entry = RememberedAnswer(key, answer, SchoolYear.lastDayOfYear(today), question.trim())
        return AnswerMemory(answers.filterNot { it.question == key } + entry)
    }

    fun forgetting(question: String): AnswerMemory {
        val key = Text.normalizeKey(question)
        return AnswerMemory(answers.filterNot { it.question == key })
    }

    fun purged(today: LocalDate): AnswerMemory = AnswerMemory(valid(today))
}

/**
 * L'année scolaire, vue de l'application.
 *
 * Tout ce qui dépend de la classe — la réponse à la question de sécurité comme les moyennes
 * — cesse d'être vrai au 1er septembre. C'est la seule date charnière dont l'application a
 * besoin, et elle vaut mieux qu'un délai de trois cents jours qui tomberait n'importe quand.
 */
object SchoolYear {

    private const val ROLLOVER_MONTH = 9
    private const val ROLLOVER_DAY = 1

    /** Le prochain 1er septembre, celui où l'année en cours laisse la place à la suivante. */
    fun nextRollover(today: LocalDate): LocalDate {
        val thisYear = LocalDate.of(today.year, ROLLOVER_MONTH, ROLLOVER_DAY)
        return if (today.isBefore(thisYear)) thisYear else thisYear.plusYears(1)
    }

    /** Le dernier jour de l'année scolaire en cours : la veille de la rentrée suivante. */
    fun lastDayOfYear(today: LocalDate): LocalDate = nextRollover(today).minusDays(1)
}
