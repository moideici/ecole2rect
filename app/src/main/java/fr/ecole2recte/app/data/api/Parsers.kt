package fr.ecole2recte.app.data.api

import org.json.JSONObject

/** Ce qu'ÉcoleDirecte rend, débarrassé de la forme qu'il lui donne. Les dates sont des chaînes ISO locales. */

data class ApiLesson(
    val id: String,
    val subject: String,
    val teacher: String,
    val room: String,
    val start: String,
    val end: String,
    val canceled: Boolean,
    val exam: Boolean,
    val note: String,
)

data class ApiHomework(
    val id: String,
    val subject: String,
    val dueDate: String,
    val content: String,
    val exam: Boolean,
    val done: Boolean,
)

data class ApiPeriod(
    val code: String,
    val name: String,
    val current: Boolean,
    val average: Double?,
    val classAverage: Double?,
)

data class ApiSubjectAverage(
    val periodCode: String,
    val subject: String,
    val teacher: String,
    val average: Double?,
    val classAverage: Double?,
    val coefficient: Double?,
)

data class ApiGrade(
    val id: String,
    val subject: String,
    val label: String,
    val value: Double?,
    val scale: Double,
    val coefficient: Double,
    val periodCode: String,
    /** Faux quand la note ne compte pas dans la moyenne (`nonSignificatif`). */
    val counted: Boolean,
    val date: String?,
    val classAverage: Double?,
    val minimum: Double?,
    val maximum: Double?,
)

data class ApiGrades(
    val periods: List<ApiPeriod>,
    val grades: List<ApiGrade>,
    val subjectAverages: List<ApiSubjectAverage>,
)

data class ApiMessage(
    val id: String,
    val subject: String,
    val sender: String,
    val read: Boolean,
    val date: String?,
)

object Parsers {
    /**
     * Un cours annulé reste dans la réponse (la salle est réservée) ; deux cours peuvent se
     * superposer quand la classe est scindée en groupes. On garde tout, trié.
     */
    fun timetable(data: Any?): List<ApiLesson> =
        data.objects().mapNotNull { raw ->
            val start = raw.dateTime("start_date", "startDate", "debut") ?: return@mapNotNull null
            val end = raw.dateTime("end_date", "endDate", "fin") ?: return@mapNotNull null
            ApiLesson(
                id = raw.str("id").ifEmpty { "$start-${raw.str("matiere", "text")}" },
                subject = raw.str("matiere", "text", "libelle"),
                teacher = raw.str("prof", "professeur"),
                room = raw.str("salle"),
                start = start,
                end = end,
                canceled = raw.flag("isAnnule", "annule"),
                exam = raw.flag("isDevoir", "devoir", "interrogation"),
                note = raw.str("note", "commentaire"),
            )
        }.sortedBy { it.start }

    /**
     * Le cahier de textes : pour chaque date, les matières qui ont un devoir — sans son contenu,
     * qui n'arrive qu'avec le détail d'un jour.
     */
    fun homeworkSummary(data: Any?): List<ApiHomework> {
        val root = data.asObject() ?: return emptyList()
        val items = mutableListOf<ApiHomework>()
        for (key in root.keys()) {
            val dueDate = isoDate(key) ?: continue
            for (raw in root.opt(key).objects()) {
                val source = raw.optJSONObject("aFaire") ?: raw
                items += ApiHomework(
                    // `idDevoir` d'abord, où qu'il soit : la racine porte parfois un identifiant de séance.
                    id = source.str("idDevoir").ifEmpty { raw.str("idDevoir", "id") }
                        .ifEmpty { "$dueDate-${raw.str("matiere", "codeMatiere")}" },
                    subject = raw.str("matiere", "libelleMatiere").ifEmpty { source.str("matiere") },
                    dueDate = dueDate,
                    content = decodeContent(source),
                    exam = raw.flag("interrogation", "isInterrogation"),
                    done = source.flag("effectue", "isEffectue"),
                )
            }
        }
        return items.sortedBy { it.dueDate }
    }

    private fun decodeContent(source: JSONObject): String {
        val raw = source.str("contenu", "content")
        return if (raw.isEmpty()) "" else plainText(base64DecodeOrRaw(raw))
    }

    /**
     * Le bulletin. Une note peut ne pas compter (`nonSignificatif`), ou ne pas avoir de valeur
     * (« Abs ») : ce n'est jamais un zéro. Les moyennes de l'établissement priment sur celles
     * qu'on calculerait, les coefficients réels n'étant pas tous exposés.
     */
    fun grades(data: Any?): ApiGrades {
        val root = data.asObject() ?: return ApiGrades(emptyList(), emptyList(), emptyList())
        val periods = mutableListOf<ApiPeriod>()
        val averages = mutableListOf<ApiSubjectAverage>()
        for (raw in root.opt("periodes").objects()) {
            val code = raw.str("idPeriode", "codePeriode", "code")
            if (code.isEmpty()) continue
            periods += ApiPeriod(
                code = code,
                name = raw.str("periode", "libelle").ifEmpty { code },
                current = !raw.flag("cloture") && raw.flag("isCurrent", "courante"),
                average = raw.num("moyenneGenerale"),
                classAverage = raw.num("moyenneClasse"),
            )
            val set = raw.optJSONObject("ensembleMatieres")
            for (discipline in set?.opt("disciplines").objects()) {
                val subject = discipline.str("discipline", "libelle", "codeMatiere")
                if (subject.isEmpty()) continue
                averages += ApiSubjectAverage(
                    periodCode = code,
                    subject = subject,
                    teacher = discipline.opt("professeurs").objects().map { personName(it) }.firstOrNull { it.isNotEmpty() }
                        ?: discipline.str("professeur"),
                    average = discipline.num("moyenne"),
                    classAverage = discipline.num("moyenneClasse"),
                    coefficient = discipline.num("coef"),
                )
            }
        }
        val grades = root.opt("notes").objects().mapNotNull { raw ->
            val id = raw.str("id")
            if (id.isEmpty()) return@mapNotNull null
            ApiGrade(
                id = id,
                subject = raw.str("libelleMatiere", "matiere", "codeMatiere"),
                label = raw.str("devoir", "libelle"),
                value = raw.num("valeur"),
                scale = raw.num("noteSur") ?: 20.0,
                coefficient = raw.num("coef") ?: 1.0,
                periodCode = raw.str("codePeriode", "idPeriode"),
                counted = !raw.flag("nonSignificatif"),
                date = raw.date("date", "dateSaisie"),
                classAverage = raw.num("moyenneClasse"),
                minimum = raw.num("minClasse"),
                maximum = raw.num("maxClasse"),
            )
        }.distinctBy { it.id }.sortedByDescending { it.date.orEmpty() }
        return ApiGrades(
            periods = periods,
            grades = grades,
            subjectAverages = averages.distinctBy { "${it.periodCode} ${it.subject}" },
        )
    }

    /** La messagerie : le service rend les trois dossiers dans un même objet, on lit « reçus ». */
    fun messages(data: Any?): List<ApiMessage> {
        val root = data.asObject() ?: return emptyList()
        val container = root.optJSONObject("messages")
        val list = if (container != null) (container.opt("received") ?: container.opt("recus")) else root.opt("messages")
        return list.objects().map { raw ->
            ApiMessage(
                id = raw.str("id"),
                subject = raw.str("subject", "objet").ifEmpty { "(sans objet)" },
                sender = personName(raw.optJSONObject("from")).ifEmpty { raw.str("expediteur") },
                read = raw.flag("read", "lu"),
                date = raw.dateTime("date", "dateEnvoi"),
            )
        }
    }
}
