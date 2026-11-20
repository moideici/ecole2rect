package fr.ecole2recte.app.domain

import kotlin.math.abs

/**
 * Une moyenne de matière telle que le bulletin la publie (ou pas) pour une période.
 *
 * [parentSubject] est renseigné pour une sous-matière (une spécialité sous son tronc commun) : elle est
 * déjà comptée dans sa mère, la moyenne générale ne l'additionne donc pas une seconde fois.
 */
data class PublishedAverage(
    val periodCode: String,
    val subject: String,
    val average: Double? = null,
    val coefficient: Double? = null,
    val parentSubject: String = "",
) {
    val isSubSubject: Boolean get() = parentSubject != ""
}

/**
 * D'où vient la moyenne affichée.
 *
 * - [Official] : celle de l'établissement, et elle connaît les dernières notes.
 * - [Recomputed] : celle de l'établissement a été calculée **avant** des notes déjà saisies ; on affiche la
 *   nôtre, à jour, et on dit lesquelles manquaient.
 * - [Computed] : l'établissement n'en publie pas ; on calcule, et on le dit.
 * - [None] : rien de chiffré à afficher.
 */
enum class AverageOrigin { Official, Recomputed, Computed, None }

data class AverageVerdict(
    /** La moyenne à afficher, sur 20. */
    val value: Double?,
    /** Vrai quand la valeur affichée sort de notre calcul et non du bulletin. */
    val computed: Boolean,
    val origin: AverageOrigin,
    /** Celle que l'établissement publie, quand il en publie une — gardée même écartée. */
    val official: Double?,
    /** Les notes que la moyenne publiée ne connaît pas encore, la plus récente en tête. */
    val behind: List<GradeEntry>,
)

data class SubjectVerdict(val average: PublishedAverage, val verdict: AverageVerdict)

/** Un point d'une courbe de moyenne : la moyenne cumulée à cette date. */
data class AveragePoint(val date: String, val value: Double)

data class Simulation(val before: Double?, val after: Double?, val delta: Double?)

data class RequiredGrade(val value: Double, val reachable: Boolean)

/**
 * Le calcul des moyennes, **identique à celui du site** (`ecole2recte`, `packages/protocol/src/insights.ts`
 * et `grade-trends.ts`) : mêmes règles, même arbitrage entre la moyenne publiée et la nôtre, mêmes chiffres.
 *
 * - Une note ne compte que si elle est significative et chiffrée : « Abs », « Disp », « NE »… ne sont pas des
 *   zéros, une note « non significative » n'entre dans aucune moyenne.
 * - Chaque note est ramenée sur 20 (valeur × 20 ÷ barème) ; un barème nul ou négatif l'écarte.
 * - Un coefficient nul ou négatif vaut 1 (pour une note comme pour une matière).
 * - La moyenne publiée prime, sauf quand elle date manifestement d'avant des notes déjà saisies.
 * - La moyenne générale est celle des matières, pondérées par leur coefficient, sans les sous-matières.
 *
 * Aucun arrondi n'est fait en cours de calcul : seul l'affichage arrondit.
 */
object Averages {
    /** L'écart en deçà duquel deux moyennes sont la même : une moyenne publiée arrive arrondie (« 14,26 », « 14,3 »). */
    const val AVERAGE_TOLERANCE = 0.055

    /** La note ramenée sur 20, seule façon de comparer un /5 et un /40. */
    fun outOfTwenty(grade: GradeEntry): Double? {
        val value = grade.value ?: return null
        if (grade.scale <= 0) return null
        return value * 20 / grade.scale
    }

    private fun weightOf(coefficient: Double): Double = if (coefficient > 0) coefficient else 1.0

    /** Les notes qui comptent dans une période (et une matière, si elle est donnée). */
    fun gradesFor(grades: List<GradeEntry>, periodCode: String, subject: String? = null): List<GradeEntry> =
        grades.filter { grade ->
            grade.periodCode == periodCode &&
                grade.counted &&
                grade.value != null &&
                (subject == null || grade.subject == subject)
        }

    /**
     * La moyenne pondérée, sur 20, ou `null` s'il n'y a rien à moyenner.
     *
     * Elle ne filtre pas les notes non significatives : c'est le rôle de [gradesFor] ou de [reconcileAverage].
     */
    fun weightedAverage(grades: List<GradeEntry>): Double? {
        var total = 0.0
        var weight = 0.0
        for (grade in grades) {
            val value = outOfTwenty(grade) ?: continue
            val coefficient = weightOf(grade.coefficient)
            total += value * coefficient
            weight += coefficient
        }
        return if (weight == 0.0) null else total / weight
    }

    /** La plus récente en tête ; tri stable, une note sans date passe en dernier. */
    private fun byDateDesc(grades: List<GradeEntry>): List<GradeEntry> = grades.sortedByDescending { it.date.orEmpty() }

    /**
     * Les notes arrivées après le calcul de la moyenne publiée.
     *
     * ÉcoleDirecte n'expose aucune date de calcul : on retire les notes une à une, de la plus récente à la plus
     * ancienne, et on regarde si la moyenne publiée réapparaît. Une note sans date arrête la recherche.
     */
    private fun gradesBehind(grades: List<GradeEntry>, official: Double): List<GradeEntry> {
        val ordered = byDateDesc(grades)
        for (dropped in 1 until ordered.size) {
            if (ordered[dropped - 1].date == null) break
            val partial = weightedAverage(ordered.drop(dropped))
            if (partial != null && abs(partial - official) <= AVERAGE_TOLERANCE) return ordered.take(dropped)
        }
        return emptyList()
    }

    /**
     * La moyenne d'un lot de notes, confrontée à celle que l'établissement publie.
     *
     * L'établissement prime, sauf quand sa moyenne est manifestement celle d'**avant** des notes déjà saisies :
     * on affiche alors la nôtre et on dit lesquelles manquaient.
     */
    fun reconcileAverage(official: Double?, grades: List<GradeEntry>): AverageVerdict {
        val usable = grades.filter { it.counted && outOfTwenty(it) != null }
        val computed = weightedAverage(usable)

        if (official == null) {
            return AverageVerdict(
                value = computed,
                computed = computed != null,
                origin = if (computed == null) AverageOrigin.None else AverageOrigin.Computed,
                official = null,
                behind = emptyList(),
            )
        }
        if (computed == null || abs(computed - official) <= AVERAGE_TOLERANCE) {
            return AverageVerdict(official, false, AverageOrigin.Official, official, emptyList())
        }
        val behind = gradesBehind(usable, official)
        if (behind.isEmpty()) {
            // Un écart sans explication n'est pas un retard : coefficients cachés, règle maison. Le bulletin a raison.
            return AverageVerdict(official, false, AverageOrigin.Official, official, emptyList())
        }
        return AverageVerdict(computed, true, AverageOrigin.Recomputed, official, behind)
    }

    /** Le même arbitrage, matière par matière, sur une période. */
    fun reconcileSubjectAverages(averages: List<PublishedAverage>, grades: List<GradeEntry>, periodCode: String): List<SubjectVerdict> =
        averages
            .filter { it.periodCode == periodCode }
            .map { SubjectVerdict(it, reconcileAverage(it.average, gradesFor(grades, periodCode, it.subject))) }

    /** La moyenne générale à partir des moyennes de matières, pondérées par leur coefficient, sans les sous-matières. */
    fun averageOfSubjects(subjects: List<SubjectVerdict>): Double? {
        var total = 0.0
        var weight = 0.0
        for (entry in subjects) {
            if (entry.average.isSubSubject) continue
            val value = entry.verdict.value ?: continue
            val coefficient = weightOf(entry.average.coefficient ?: 1.0)
            total += value * coefficient
            weight += coefficient
        }
        return if (weight == 0.0) null else total / weight
    }

    /**
     * La moyenne générale de la période.
     *
     * Sans moyennes de matières, on retombe sur la moyenne à plat des notes. Dès qu'**une** matière a été notée
     * après le calcul du bulletin, la moyenne générale publiée est antérieure elle aussi : elle est recalculée.
     */
    fun generalAverage(
        official: Double?,
        grades: List<GradeEntry>,
        periodCode: String,
        averages: List<PublishedAverage> = emptyList(),
    ): AverageVerdict {
        val subjects = reconcileSubjectAverages(averages, grades, periodCode)
        val behind = byDateDesc(subjects.flatMap { it.verdict.behind })
        val mine = averageOfSubjects(subjects) ?: weightedAverage(gradesFor(grades, periodCode))

        if (official == null) {
            return AverageVerdict(
                value = mine,
                computed = mine != null,
                origin = if (mine == null) AverageOrigin.None else AverageOrigin.Computed,
                official = null,
                behind = emptyList(),
            )
        }
        if (behind.isEmpty() || mine == null) {
            return AverageVerdict(official, false, AverageOrigin.Official, official, emptyList())
        }
        return AverageVerdict(mine, true, AverageOrigin.Recomputed, official, behind)
    }

    /**
     * Les matières d'une période : celles que le bulletin publie, plus celles qui ont des notes sans moyenne
     * publiée (sans elles, leurs notes seraient chargées puis jamais comptées), comme sur la page Notes du site.
     */
    fun subjectsOfPeriod(published: List<PublishedAverage>, grades: List<GradeEntry>, periodCode: String): List<PublishedAverage> {
        val own = published.filter { it.periodCode == periodCode }
        val known = own.map { it.subject }.toSet()
        val implicit = grades.filter { it.periodCode == periodCode }
            .map { it.subject }
            .distinct()
            .filter { it != "" && it !in known }
            .map { PublishedAverage(periodCode, it) }
        return own + implicit
    }

    private fun hypotheticalGrade(periodCode: String, subject: String?, value: Double, scale: Double, coefficient: Double) =
        GradeEntry(
            id = "__simulation__",
            subject = subject.orEmpty(),
            colorIndex = 0,
            label = "Simulation",
            value = value,
            scale = scale,
            coefficient = coefficient,
            date = null,
            classAverage = null,
            minimum = null,
            maximum = null,
            counted = true,
            periodCode = periodCode,
        )

    /** « Si je prends 14 au prochain devoir, ça donne quoi ? » */
    fun simulate(
        grades: List<GradeEntry>,
        periodCode: String,
        subject: String?,
        value: Double,
        scale: Double = 20.0,
        coefficient: Double = 1.0,
    ): Simulation {
        val existing = gradesFor(grades, periodCode, subject)
        val before = weightedAverage(existing)
        val after = weightedAverage(existing + hypotheticalGrade(periodCode, subject, value, scale, coefficient))
        return Simulation(before, after, if (before != null && after != null) after - before else null)
    }

    /**
     * La note à viser (dans le barème [scale]) pour atteindre [target] — ou l'aveu qu'elle est hors d'atteinte.
     * `null` pour un coefficient nul ou négatif.
     */
    fun requiredGrade(
        grades: List<GradeEntry>,
        periodCode: String,
        subject: String?,
        target: Double,
        scale: Double = 20.0,
        coefficient: Double = 1.0,
    ): RequiredGrade? {
        var total = 0.0
        var weight = 0.0
        for (grade in gradesFor(grades, periodCode, subject)) {
            val value = outOfTwenty(grade) ?: continue
            val own = weightOf(grade.coefficient)
            total += value * own
            weight += own
        }
        if (coefficient <= 0) return null
        val neededOutOfTwenty = (target * (weight + coefficient) - total) / coefficient
        val value = neededOutOfTwenty * scale / 20
        return RequiredGrade(value, value >= 0 && value <= scale)
    }

    /**
     * La courbe d'une moyenne dans une période : un point par date de note, la moyenne cumulée des notes jusqu'à
     * cette date. Pour une matière, sa moyenne pondérée ; sans matière, la moyenne des matières pondérées par
     * leur coefficient (sans les sous-matières).
     */
    fun averageSeries(grades: List<GradeEntry>, periodCode: String, subjects: List<PublishedAverage>, subject: String? = null): List<AveragePoint> {
        val dated = gradesFor(grades, periodCode, subject).filter { it.date != null }
        val dates = dated.map { it.date!! }.distinct().sorted()
        return dates.mapNotNull { date ->
            val partial = dated.filter { it.date!! <= date }
            var value = weightedAverage(partial)
            if (subject == null) {
                var total = 0.0
                var weight = 0.0
                for (name in partial.map { it.subject }.distinct()) {
                    val metadata = subjects.firstOrNull { it.periodCode == periodCode && it.subject == name }
                    if (metadata != null && metadata.isSubSubject) continue
                    val mean = weightedAverage(partial.filter { it.subject == name }) ?: continue
                    val coefficient = weightOf(metadata?.coefficient ?: 1.0)
                    total += mean * coefficient
                    weight += coefficient
                }
                value = if (weight > 0) total / weight else null
            }
            value?.let { AveragePoint(date, it) }
        }
    }
}
