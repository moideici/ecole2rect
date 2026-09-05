package fr.ecole2recte.app.core.parse

import fr.ecole2recte.app.core.format.Text
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.Grade
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.Period
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

    fun accounts(payload: JSONObject): List<Account> {
        val data = payload.optJSONObject("data") ?: payload
        return data.objects("accounts").mapNotNull { account ->
            val id = account.string("id", "idLogin").ifBlank { return@mapNotNull null }
            val profile = account.optJSONObject("profile")
            Account(
                id = id,
                type = account.string("typeCompte").ifBlank { "E" },
                firstName = account.string("prenom"),
                lastName = account.string("nom"),
                className = profile?.optJSONObject("classe")?.string("libelle", "code").orEmpty(),
                schoolName = account.string("nomEtablissement")
                    .ifBlank { profile?.string("nomEtablissement").orEmpty() },
            )
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
                attachments = attachments(toDo),
            )
        }
    }

    private fun attachments(entry: JSONObject?): List<Attachment> {
        val source = entry ?: return emptyList()
        return source.objects("documents", "files").mapNotNull { document ->
            val name = document.string("libelle", "fichier", "nom").ifBlank { return@mapNotNull null }
            Attachment(
                id = document.stringOrNull("id") ?: stableId("doc", name),
                name = name,
                type = document.string("type"),
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
            attachments = attachments(entry),
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
