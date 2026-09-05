package fr.ecole2recte.app.core.parse

import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.AttachmentSource
import fr.ecole2recte.app.core.model.Document
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
import fr.ecole2recte.app.core.model.SchoolLifeEvent
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SessionContent
import fr.ecole2recte.app.core.model.SubjectAverage
import fr.ecole2recte.app.core.protocol.Encoding
import org.json.JSONObject
import java.time.LocalDate

/**
 * Conversion des réponses ÉcoleDirecte vers les modèles de l'application.
 *
 * Deux règles tiennent tout ce fichier :
 *
 *  1. une entrée illisible est ignorée, jamais fatale — une seule ligne cassée dans une
 *     semaine de cours ne doit pas vider l'emploi du temps ;
 *  2. chaque élément reçoit un identifiant stable, dérivé des données quand l'API n'en
 *     fournit pas, pour qu'une synchronisation répétée ne crée pas de doublons.
 */
object Parsers {

    /**
     * Les profils consultables après une connexion.
     *
     * Un élève se connecte : son compte est le profil. Un parent se connecte : son compte est
     * une famille, et les profils sont les enfants rangés dans `profile.eleves`. Sans cette
     * traduction, une connexion parent donnerait un identifiant de famille aux appels élève,
     * qui répondraient tous « accès refusé » — c'est exactement ce que faisait l'application
     * avant. Une famille sans enfant lisible reste renvoyée telle quelle, pour que la
     * messagerie fonctionne malgré tout.
     */
    fun accounts(payload: JSONObject): List<Account> {
        val data = payload.optJSONObject("data") ?: payload
        return data.objects("accounts").flatMap { account ->
            val id = account.string("id", "idLogin").ifBlank { return@flatMap emptyList() }
            val profile = account.optJSONObject("profile")
            val type = account.string("typeCompte").ifBlank { "E" }
            val schoolName = account.string("nomEtablissement")
                .ifBlank { profile?.string("nomEtablissement").orEmpty() }
            val holder = Account(
                id = id,
                type = type,
                firstName = account.string("prenom"),
                lastName = account.string("nom"),
                className = profile?.optJSONObject("classe")?.string("libelle", "code").orEmpty(),
                schoolName = schoolName,
            )
            if (holder.isStudent) return@flatMap listOf(holder)
            val children = profile?.objects("eleves").orEmpty().mapNotNull { child ->
                val childId = child.string("id", "idEleve").ifBlank { return@mapNotNull null }
                Account(
                    id = childId,
                    type = "E",
                    firstName = child.string("prenom"),
                    lastName = child.string("nom").ifBlank { holder.lastName },
                    // « classe » est tantôt un objet, tantôt le libellé lui-même.
                    className = child.optJSONObject("classe")?.string("libelle", "code")
                        ?: child.string("classe", "libelleClasse"),
                    schoolName = child.string("nomEtablissement").ifBlank { schoolName },
                    familyId = id,
                    familyName = holder.displayName,
                )
            }
            children.ifEmpty { listOf(holder) }
        }
    }

    // ── Emploi du temps ──────────────────────────────────────────────────────

    fun lessons(payload: JSONObject): List<Lesson> {
        val entries = payload.optJSONArray("data").objects()
            .ifEmpty { payload.optJSONObject("data")?.objects("cours", "lessons").orEmpty() }
        return entries.mapNotNull { entry ->
            val start = parseDateTime(entry.string("start_date", "startDate", "date_debut")) ?: return@mapNotNull null
            val end = parseDateTime(entry.string("end_date", "endDate", "date_fin")) ?: start.plusHours(1)
            val subject = Text.prettySubject(
                entry.string("matiere", "text", "libelle").ifBlank { "Cours" },
            )
            Lesson(
                id = entry.stringOrNull("id") ?: stableId("lesson", subject, start.toString()),
                subject = subject,
                teacher = entry.string("prof", "nomProf"),
                room = entry.string("salle"),
                start = start,
                end = end,
                canceled = entry.bool("isAnnule", "annule"),
                exam = entry.bool("isDevoir", "devoir") ||
                    entry.string("typeCours").contains("DEVOIR", ignoreCase = true),
                note = entry.string("commentaire"),
            )
        }.sortedBy { it.start }
    }

    // ── Cahier de textes ─────────────────────────────────────────────────────

    /**
     * Liste courte du cahier de textes : `data` est un objet dont les clés sont les dates.
     * Elle donne les matières et l'état « fait », mais pas le contenu — celui-ci arrive par
     * [homeworkForDate], appelé seulement pour les jours réellement consultés.
     */
    fun homeworkSummaries(payload: JSONObject): List<HomeworkItem> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        return data.keyList().flatMap { key ->
            val dueDate = parseDate(key) ?: return@flatMap emptyList()
            data.optJSONArray(key).objects().mapNotNull { entry ->
                homeworkFromSummary(entry, dueDate)
            }
        }.sortedWith(compareBy({ it.dueDate }, { it.subject }))
    }

    private fun homeworkFromSummary(entry: JSONObject, dueDate: LocalDate): HomeworkItem? {
        val subject = Text.prettySubject(entry.string("matiere", "codeMatiere").ifBlank { return null })
        val id = entry.stringOrNull("idDevoir", "id") ?: stableId("hw", subject, dueDate.toString())
        return HomeworkItem(
            id = id,
            subject = subject,
            dueDate = dueDate,
            givenOn = parseDate(entry.string("donneLe")),
            exam = entry.bool("interrogation"),
            done = entry.bool("effectue"),
        )
    }

    /**
     * Détail d'un jour : contenu HTML encodé en base64, pièces jointes et état « fait ».
     * Le résultat remplace la version courte du même identifiant.
     */
    fun homeworkForDate(payload: JSONObject, date: LocalDate): List<HomeworkItem> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        val dueDate = parseDate(data.string("date")) ?: date
        return data.objects("matieres").mapNotNull { entry ->
            val toDo = entry.optJSONObject("aFaire")
            val subject = Text.prettySubject(entry.string("matiere", "codeMatiere").ifBlank { return@mapNotNull null })
            val id = toDo?.stringOrNull("idDevoir")
                ?: entry.stringOrNull("id", "idDevoir")
                ?: stableId("hw", subject, dueDate.toString())
            // Sans « aFaire », l'entrée n'est qu'un contenu de séance : rien à rendre.
            if (toDo == null && !entry.bool("interrogation")) return@mapNotNull null
            HomeworkItem(
                id = id,
                subject = subject,
                dueDate = dueDate,
                givenOn = parseDate(toDo?.string("donneLe")),
                content = Text.fromHtml(Encoding.maybeBase64(toDo?.string("contenu"))),
                exam = entry.bool("interrogation"),
                done = toDo?.bool("effectue") ?: false,
                attachments = attachments(toDo, AttachmentSource.HOMEWORK),
            )
        }
    }

    private fun attachments(entry: JSONObject?, origin: AttachmentSource): List<Attachment> {
        val source = entry ?: return emptyList()
        return source.objects("documents", "files").mapNotNull { document ->
            val name = document.string("libelle", "fichier", "nom").ifBlank { return@mapNotNull null }
            Attachment(
                id = document.stringOrNull("id") ?: stableId("doc", name),
                name = name,
                type = document.string("type"),
                source = origin,
            )
        }
    }

    // ── Notes ────────────────────────────────────────────────────────────────

    data class GradesPayload(
        val periods: List<Period> = emptyList(),
        val grades: List<Grade> = emptyList(),
        val subjectAverages: List<SubjectAverage> = emptyList(),
    )

    fun grades(payload: JSONObject, today: LocalDate = LocalDate.now()): GradesPayload {
        val data = payload.optJSONObject("data") ?: return GradesPayload()
        val periods = data.objects("periodes").mapNotNull { entry ->
            val code = entry.string("idPeriode", "codePeriode").ifBlank { return@mapNotNull null }
            val ensemble = entry.optJSONObject("ensembleMatieres")
            val start = parseDate(entry.string("dateDebut"))
            val end = parseDate(entry.string("dateFin"))
            Period(
                code = code,
                name = entry.string("periode", "libelle").ifBlank { code },
                average = ensemble?.decimal("moyenneGenerale"),
                classAverage = ensemble?.decimal("moyenneClasse"),
                start = start,
                end = end,
                current = start != null && end != null && !today.isBefore(start) && !today.isAfter(end),
            )
        }
        val averages = data.objects("periodes").flatMap { entry ->
            val code = entry.string("idPeriode", "codePeriode")
            entry.optJSONObject("ensembleMatieres").objectsOrEmpty("disciplines").mapNotNull { discipline ->
                val subject = Text.prettySubject(discipline.string("discipline", "codeMatiere").ifBlank { return@mapNotNull null })
                // Les regroupements (« Total », « Pôle scientifique ») ne sont pas des matières.
                if (discipline.bool("groupeMatiere")) return@mapNotNull null
                SubjectAverage(
                    periodCode = code,
                    subject = subject,
                    average = discipline.decimal("moyenne"),
                    classAverage = discipline.decimal("moyenneClasse"),
                    coefficient = discipline.decimal("coef"),
                    teacher = discipline.objects("professeurs").firstOrNull()?.string("nom").orEmpty(),
                    appreciation = discipline.objects("appreciations").firstOrNull()?.string("appreciation").orEmpty(),
                )
            }
        }
        val grades = data.objects("notes").mapNotNull { entry ->
            val subject = Text.prettySubject(
                entry.string("libelleMatiere", "codeMatiere").ifBlank { return@mapNotNull null },
            )
            val date = parseDate(entry.string("date", "dateSaisie"))
            Grade(
                id = entry.stringOrNull("id") ?: stableId("grade", subject, entry.string("devoir"), date?.toString().orEmpty()),
                subject = subject,
                label = entry.string("devoir").ifBlank { "Évaluation" },
                value = entry.decimal("valeur"),
                scale = entry.decimal("noteSur") ?: 20.0,
                coefficient = entry.decimal("coef") ?: 1.0,
                classAverage = entry.decimal("moyenneClasse"),
                date = date,
                periodCode = entry.string("codePeriode"),
                counted = !entry.bool("nonSignificatif"),
                comment = entry.string("commentaire"),
            )
        }.sortedByDescending { it.date ?: LocalDate.MIN }
        return GradesPayload(periods, grades, averages)
    }

    private fun JSONObject?.objectsOrEmpty(vararg keys: String): List<JSONObject> =
        this?.objects(*keys).orEmpty()

    // ── Vie scolaire ─────────────────────────────────────────────────────────

    /**
     * Absences, retards, sanctions et encouragements.
     *
     * L'API les range dans des tableaux distincts selon les établissements, parfois dans un
     * seul. On lit les deux formes, et le type de chaque entrée décide de la famille.
     */
    fun schoolLife(payload: JSONObject): List<SchoolLifeEvent> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        // `objects(vararg)` s'arrête à la première clé trouvée : ici il faut au contraire
        // réunir tous les tableaux, car absences et sanctions cohabitent dans la réponse.
        val entries = SCHOOL_LIFE_KEYS.flatMap { key -> data.objects(key) }
        return entries.mapNotNull { entry ->
            val start = parseDateTime(entry.string("dateDebut", "date", "displayDate"))
                ?: return@mapNotNull null
            val rawType = entry.string("typeElement", "type", "libelle")
            SchoolLifeEvent(
                id = entry.stringOrNull("id") ?: stableId("vs", rawType, start.toString()),
                kind = schoolLifeKind(rawType),
                label = Text.prettySubject(rawType).ifBlank { "Événement" },
                start = start,
                end = parseDateTime(entry.string("dateFin")),
                justified = entry.bool("justifie", "justifiee"),
                reason = entry.string("motif", "libelle"),
                comment = entry.string("commentaire"),
            )
        }.distinctBy { it.id }.sortedByDescending { it.start }
    }

    private val SCHOOL_LIFE_KEYS = listOf(
        "absencesRetards", "absences", "retards", "sanctionsEncouragements", "evenements",
    )

    private fun schoolLifeKind(raw: String): SchoolLifeKind {
        val key = Text.normalizeKey(raw)
        return when {
            key.contains("retard") -> SchoolLifeKind.LATE
            key.contains("absence") -> SchoolLifeKind.ABSENCE
            key.contains("encouragement") || key.contains("felicitation") -> SchoolLifeKind.ENCOURAGEMENT
            key.contains("sanction") || key.contains("punition") ||
                key.contains("exclusion") || key.contains("retenue") -> SchoolLifeKind.SANCTION
            else -> SchoolLifeKind.OTHER
        }
    }

    // ── Contenus de séance ───────────────────────────────────────────────────

    /** Ce qui a été fait en cours, extrait du même appel que les devoirs du jour. */
    fun sessionContents(payload: JSONObject, date: LocalDate): List<SessionContent> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        val day = parseDate(data.string("date")) ?: date
        return data.objects("matieres").mapNotNull { entry ->
            val session = entry.optJSONObject("contenuDeSeance") ?: return@mapNotNull null
            val subject = Text.prettySubject(entry.string("matiere", "codeMatiere").ifBlank { return@mapNotNull null })
            val content = Text.fromHtml(Encoding.maybeBase64(session.string("contenu")))
            if (content.isBlank()) return@mapNotNull null
            SessionContent(
                id = entry.stringOrNull("id") ?: stableId("seance", subject, day.toString()),
                subject = subject,
                date = day,
                content = content,
                attachments = attachments(session, AttachmentSource.HOMEWORK),
            )
        }
    }

    // ── Documents ────────────────────────────────────────────────────────────

    /**
     * Les documents de l'espace numérique. Les catégories varient d'un établissement à
     * l'autre : on parcourt donc toutes les clés dont la valeur est un tableau d'objets.
     */
    fun documents(payload: JSONObject): List<Document> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        return data.keyList().flatMap { category ->
            data.optJSONArray(category).objects().mapNotNull { entry ->
                val name = entry.string("libelle", "nom", "fichier").ifBlank { return@mapNotNull null }
                Document(
                    id = entry.stringOrNull("id") ?: stableId("doc", category, name),
                    name = name,
                    category = Text.prettySubject(category),
                    date = parseDate(entry.string("date", "dateDocument")),
                    type = entry.string("type"),
                )
            }
        }.sortedByDescending { it.date ?: LocalDate.MIN }
    }

    // ── Messagerie ───────────────────────────────────────────────────────────

    fun messages(payload: JSONObject): List<MessageItem> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        val container = data.optJSONObject("messages") ?: data
        val entries = container.objects("received", "messages")
            .ifEmpty { data.objects("received") }
        return entries.mapNotNull { entry -> message(entry) }
            .sortedByDescending { it.date }
    }

    fun message(entry: JSONObject): MessageItem? {
        val id = entry.stringOrNull("id") ?: return null
        val from = entry.optJSONObject("from")
        val sender = listOfNotNull(
            from?.stringOrNull("civilite"),
            from?.stringOrNull("prenom"),
            from?.stringOrNull("nom", "name"),
        ).joinToString(" ").ifBlank { entry.string("from_name", "expediteur").ifBlank { "ÉcoleDirecte" } }
        val body = Text.fromHtml(Encoding.maybeBase64(entry.string("content", "contenu")))
        return MessageItem(
            id = id,
            subject = Encoding.maybeBase64(entry.string("subject", "sujet")).ifBlank { "(sans objet)" },
            sender = sender,
            date = parseDateTime(entry.string("date", "dateEnvoi")),
            read = entry.bool("read", "lu"),
            preview = Text.preview(body),
            body = body,
            attachments = attachments(entry, AttachmentSource.MESSAGE),
        )
    }

    /** Détail d'un message : seul le corps et les pièces jointes sont nouveaux. */
    fun messageBody(payload: JSONObject): MessageItem? =
        payload.optJSONObject("data")?.let { message(it) }

    /**
     * Identifiant reproductible pour les entrées sans clé côté serveur : même entrée,
     * même identifiant, donc pas de doublon d'une synchronisation à l'autre.
     */
    private fun stableId(prefix: String, vararg parts: String): String {
        val joined = parts.joinToString("|")
        var hash = 0
        joined.forEach { character -> hash = hash * 31 + character.code }
        return "$prefix-${Integer.toHexString(hash)}"
    }
}
