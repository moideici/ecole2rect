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
    val modified: Boolean = false,
)

data class ApiAttachment(val id: String, val name: String, val type: String)

data class ApiHomework(
    val id: String,
    val subject: String,
    val dueDate: String,
    val content: String,
    val exam: Boolean,
    val done: Boolean,
    val attachments: List<ApiAttachment> = emptyList(),
)

/** Ce qui a été fait en cours un jour donné. */
data class ApiSessionContent(val subject: String, val content: String, val attachments: List<ApiAttachment>)

/** Le détail d'un jour du cahier de textes : les énoncés et le contenu des séances. */
data class ApiDay(val homework: List<ApiHomework>, val sessions: List<ApiSessionContent>)

data class ApiPeriod(
    val code: String,
    val name: String,
    val current: Boolean,
    val average: Double?,
    val classAverage: Double?,
    /** Période « Année » qui chapeaute les trimestres : jamais celle qu'on affiche par défaut. */
    val annual: Boolean = false,
    val start: String? = null,
    val end: String? = null,
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

/** Un message ouvert : le corps complet, en texte, et ses pièces jointes. */
data class ApiMessageDetail(
    val id: String,
    val subject: String,
    val sender: String,
    val date: String?,
    val body: String,
    val recipients: List<String>,
    val attachments: List<ApiAttachment>,
)

data class ApiSchoolLife(
    val id: String,
    /** absence, retard, sanction, encouragement ou autre. */
    val kind: String,
    val label: String,
    val start: String,
    val end: String?,
    val justified: Boolean,
    val reason: String,
    val comment: String,
    /** Durée telle que l'établissement la compte. */
    val duration: String,
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
                modified = raw.flag("isModifie", "modifie"),
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
            for (raw in root.opt(key).objects()) items += homeworkFrom(raw, dueDate)
        }
        return items.sortedBy { it.dueDate }
    }

    private fun homeworkFrom(raw: JSONObject, dueDate: String): ApiHomework {
        val source = raw.optJSONObject("aFaire") ?: raw
        return ApiHomework(
            // `idDevoir` d'abord, où qu'il soit : la racine du détail porte un identifiant de séance, différent.
            id = source.str("idDevoir").ifEmpty { raw.str("idDevoir", "id") }
                .ifEmpty { "$dueDate-${raw.str("matiere", "codeMatiere")}" },
            subject = raw.str("matiere", "libelleMatiere").ifEmpty { source.str("matiere") },
            dueDate = dueDate,
            content = decodeContent(source),
            exam = raw.flag("interrogation", "isInterrogation"),
            done = source.flag("effectue", "isEffectue"),
            attachments = attachmentsOf(source),
        )
    }

    /** Les listes de pièces jointes portent plusieurs noms selon l'endpoint. */
    internal fun attachmentsOf(source: JSONObject): List<ApiAttachment> {
        for (key in listOf("documents", "files", "fichiers", "piecesJointes")) {
            val found = source.opt(key).objects().mapNotNull { raw ->
                val id = raw.str("id", "idDocument", "fichierId")
                if (id.isEmpty()) null else ApiAttachment(id, raw.str("libelle", "nom", "name").ifEmpty { "Document" }, raw.str("type"))
            }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    fun dayContent(data: Any?, date: String): ApiDay {
        val root = data.asObject() ?: return ApiDay(emptyList(), emptyList())
        val homework = mutableListOf<ApiHomework>()
        val sessions = mutableListOf<ApiSessionContent>()
        for (raw in root.opt("matieres").objects()) {
            val subject = raw.str("matiere", "libelleMatiere")
            if (raw.optJSONObject("aFaire") != null) homework += homeworkFrom(raw, date)
            val lesson = raw.optJSONObject("contenuDeSeance") ?: continue
            val content = decodeContent(lesson)
            val attachments = attachmentsOf(lesson)
            // Une séance sans contenu ni document n'apprend rien : mieux vaut ne rien montrer qu'une carte vide.
            if (content.isEmpty() && attachments.isEmpty()) continue
            sessions += ApiSessionContent(subject, content, attachments)
        }
        return ApiDay(homework, sessions)
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
            val set = raw.optJSONObject("ensembleMatieres")
            periods += ApiPeriod(
                code = code,
                name = raw.str("periode", "libelle").ifEmpty { code },
                current = !raw.flag("cloture") && raw.flag("isCurrent", "courante"),
                // La moyenne générale est tantôt sur la période, tantôt dans `ensembleMatieres`.
                average = raw.num("moyenneGenerale") ?: set?.num("moyenneGenerale"),
                classAverage = raw.num("moyenneClasse") ?: set?.num("moyenneClasse"),
                annual = raw.flag("annuel"),
                start = raw.date("dateDebut"),
                end = raw.date("dateFin"),
            )
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

    /**
     * La vie scolaire. La durée est reprise **telle que l'établissement la compte** : une absence de
     * 8 h à 10 h peut valoir une ou deux heures selon l'emploi du temps, et c'est son décompte qui fait foi.
     */
    fun schoolLife(data: Any?): List<ApiSchoolLife> {
        val root = data.asObject() ?: return emptyList()
        val events = mutableListOf<ApiSchoolLife>()
        for (key in listOf("absencesRetards", "sanctionsEncouragements", "evenements")) {
            for (raw in root.opt(key).objects()) {
                val start = raw.dateTime("date", "dateDebut", "displayDate") ?: continue
                val label = raw.str("typeElement", "type").lowercase()
                events += ApiSchoolLife(
                    id = raw.str("id").ifEmpty { "$key-$start" },
                    kind = when {
                        "absence" in label -> "absence"
                        "retard" in label -> "retard"
                        "sanction" in label || "punition" in label -> "sanction"
                        "encouragement" in label || "valorisation" in label -> "encouragement"
                        else -> "autre"
                    },
                    label = raw.str("libelle", "typeElement"),
                    start = start,
                    end = raw.dateTime("dateFin"),
                    justified = raw.flag("justifie", "justifiee"),
                    reason = raw.str("motif", "parMotif"),
                    comment = raw.str("commentaire"),
                    duration = raw.str("displayDate", "duree"),
                )
            }
        }
        return events.sortedByDescending { it.start }
    }

    /** Le détail d'un message : le corps arrive en base64 et contient du HTML. */
    fun messageDetail(data: Any?): ApiMessageDetail? {
        val root = data.asObject() ?: return null
        val source = root.optJSONObject("message") ?: root
        val id = source.str("id")
        if (id.isEmpty()) return null
        val encoded = source.str("content", "contenu")
        return ApiMessageDetail(
            id = id,
            subject = source.str("subject", "objet").ifEmpty { "(sans objet)" },
            sender = personName(source.optJSONObject("from")).ifEmpty { source.str("expediteur") },
            date = source.dateTime("date", "dateEnvoi"),
            body = if (encoded.isEmpty()) "" else plainText(base64DecodeOrRaw(encoded)),
            recipients = source.opt("to").objects().map { personName(it) }.filter { it.isNotEmpty() },
            attachments = attachmentsOf(source),
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
