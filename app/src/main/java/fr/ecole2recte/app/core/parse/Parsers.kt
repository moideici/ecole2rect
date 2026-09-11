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
import fr.ecole2recte.app.core.model.CloudFile
import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.NewsItem
import fr.ecole2recte.app.core.model.SessionContent
import fr.ecole2recte.app.core.model.Skill
import fr.ecole2recte.app.core.model.Textbook
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
                // Le groupe distingue deux élèves de la même classe qui ne sont pas au même
                // cours ; il ne figure pas toujours dans le libellé de la matière. `classe`
                // n'en fait pas partie : pour un cours en classe entière, `groupe` est vide et
                // le repli affichait « Terminale B » comme s'il s'agissait d'un demi-groupe —
                // c'est-à-dire une mention de groupe sur *tous* les cours, l'inverse du but.
                group = entry.string("groupeCode", "groupe")
                    .takeIf { it.isNotBlank() && !it.equals(entry.string("matiere"), ignoreCase = true) }
                    .orEmpty(),
                kind = Text.prettySubject(entry.string("typeCours")),
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
        val id = entry.stringOrNull("idDevoir", "id")
            ?: stableId(HomeworkItem.LOCAL_HOMEWORK_ID_PREFIX.trimEnd('-'), subject, dueDate.toString())
        return HomeworkItem(
            id = id,
            subject = subject,
            dueDate = dueDate,
            givenOn = parseDate(entry.string("donneLe")),
            exam = entry.bool("interrogation"),
            done = entry.bool("effectue"),
            onlineSubmission = entry.bool("rendreEnLigne", "aRendre"),
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
                // Pas d'identifiant côté serveur : on en fabrique un, et `idFromServer`
                // saura que celui-là ne doit jamais repartir en écriture.
                ?: stableId(HomeworkItem.LOCAL_HOMEWORK_ID_PREFIX.trimEnd('-'), subject, dueDate.toString())
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
                onlineSubmission = toDo?.bool("rendreEnLigne", "aRendre") ?: false,
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
                // L'appréciation du conseil de classe est le bulletin en une phrase : elle
                // arrive tantôt à la racine de la période, tantôt dans l'ensemble des matières.
                appreciation = Text.fromHtml(
                    Encoding.maybeBase64(
                        entry.string("appreciationPP", "appreciation")
                            .ifBlank { ensemble?.string("appreciationPP", "appreciation").orEmpty() },
                    ),
                ),
                headTeacher = entry.string("nomProfesseurPrincipal", "professeurPrincipal"),
                councilDate = parseDate(entry.string("dateConseil")),
                councilTime = entry.string("heureConseil"),
                minimum = ensemble?.decimal("moyenneMin"),
                maximum = ensemble?.decimal("moyenneMax"),
            )
        }
        val averages = data.objects("periodes").flatMap { entry ->
            val code = entry.string("idPeriode", "codePeriode")
            entry.optJSONObject("ensembleMatieres").objectsOrEmpty("disciplines").mapNotNull { discipline ->
                val subject = Text.prettySubject(discipline.string("discipline", "codeMatiere").ifBlank { return@mapNotNull null })
                // Les regroupements (« Total », « Pôle scientifique ») ne sont pas des matières.
                if (discipline.bool("groupeMatiere")) return@mapNotNull null
                val teachers = discipline.objects("professeurs")
                    .map { it.string("nom", "libelle") }
                    .filter { it.isNotBlank() }
                SubjectAverage(
                    periodCode = code,
                    subject = subject,
                    average = discipline.decimal("moyenne"),
                    classAverage = discipline.decimal("moyenneClasse"),
                    coefficient = discipline.decimal("coef"),
                    teacher = teachers.firstOrNull().orEmpty(),
                    // Plusieurs appréciations quand plusieurs professeurs se partagent la
                    // matière : les coller les unes aux autres vaut mieux que n'en lire qu'une.
                    appreciation = discipline.objects("appreciations")
                        .map { it.string("appreciation", "libelle") }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n"),
                    minimum = discipline.decimal("moyenneMin", "minClasse"),
                    maximum = discipline.decimal("moyenneMax", "maxClasse"),
                    teachers = teachers,
                    // Une sous-matière (« Écrit », « Oral ») n'a de sens que rattachée à sa
                    // matière mère, sinon elle apparaît comme une matière de plus.
                    parentSubject = Text.prettySubject(discipline.string("codeSousMatiere").let { code ->
                        if (code.isBlank()) "" else discipline.string("codeMatiere")
                    }),
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
                minimum = entry.decimal("minClasse"),
                maximum = entry.decimal("maxClasse"),
                kind = Text.prettySubject(entry.string("typeDevoir")),
                skills = skills(entry),
            )
        }.sortedByDescending { it.date ?: LocalDate.MIN }
        return GradesPayload(periods, grades, averages)
    }

    /**
     * Les compétences évaluées avec une note.
     *
     * Le socle commun se note par niveau et non par point : l'API les range dans
     * `elementsProgramme`, avec un libellé et une valeur de 1 à 4 selon les établissements.
     */
    private fun skills(entry: JSONObject): List<Skill> =
        entry.objects("elementsProgramme", "competences").mapNotNull { element ->
            val label = element.string("libelleCompetence", "descriptif", "libelle")
                .ifBlank { return@mapNotNull null }
            val level = element.optJSONObject("valeur")?.string("libelle")
                ?: element.string("valeur", "niveau", "libelleValeur")
            Skill(
                label = label,
                level = Text.prettySubject(level),
                value = level.trim().toIntOrNull() ?: element.int("valeurNumerique"),
            )
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
                // Le motif vient de `motif`, et de lui seul. Le repli sur `libelle` affichait
                // la durée (« 2 demi-journées ») en gros, à la place du motif : mieux vaut pas
                // de motif qu'un motif qui n'en est pas un.
                reason = entry.string("motif"),
                comment = entry.string("commentaire"),
                duration = entry.string("duree", "nbHeures", "displayDuree", "libelle"),
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

    fun messages(payload: JSONObject, folder: MessageFolder = MessageFolder.RECEIVED): List<MessageItem> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        val container = data.optJSONObject("messages") ?: data
        // Le tableau porte le nom du dossier demandé ; « messages » sert de repli pour les
        // établissements dont la réponse ne le nomme pas.
        val entries = container.objects(folder.apiValue, "messages")
            .ifEmpty { data.objects(folder.apiValue, "messages") }
        return entries.mapNotNull { entry -> message(entry, folder) }
            .sortedByDescending { it.date }
    }

    /** Les classeurs créés par l'utilisateur sur le site : autant de dossiers en plus. */
    fun messageFolders(payload: JSONObject): List<Pair<String, String>> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        val container = data.optJSONObject("messages") ?: data
        return container.objects("classeurs").mapNotNull { entry ->
            val id = entry.stringOrNull("id") ?: return@mapNotNull null
            val label = entry.string("libelle", "nom").ifBlank { return@mapNotNull null }
            id to label
        }
    }

    fun message(entry: JSONObject, folder: MessageFolder = MessageFolder.RECEIVED): MessageItem? {
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
            folder = folder,
            recipients = recipients(entry),
        )
    }

    /**
     * Les destinataires d'un message envoyé.
     *
     * L'API en donne parfois des dizaines (une classe entière) : au-delà de quelques noms,
     * la liste est résumée, sinon l'aperçu du message disparaîtrait sous les adresses.
     */
    private fun recipients(entry: JSONObject): List<String> {
        val all = entry.objects("to", "destinataires").mapNotNull { person ->
            listOfNotNull(
                person.stringOrNull("civilite"),
                person.stringOrNull("prenom"),
                person.stringOrNull("nom", "name"),
            ).joinToString(" ").takeIf { it.isNotBlank() }
        }
        if (all.size <= MAX_VISIBLE_RECIPIENTS) return all
        return all.take(MAX_VISIBLE_RECIPIENTS) + "et ${all.size - MAX_VISIBLE_RECIPIENTS} autres"
    }

    private const val MAX_VISIBLE_RECIPIENTS = 3

    /** Détail d'un message : seul le corps et les pièces jointes sont nouveaux. */
    fun messageBody(payload: JSONObject, folder: MessageFolder = MessageFolder.RECEIVED): MessageItem? =
        payload.optJSONObject("data")?.let { message(it, folder) }

    // ── Vie de l'établissement ───────────────────────────────────────────────

    /**
     * Les actualités publiées par l'établissement.
     *
     * La réponse range les entrées sous des clés qui varient (`actualites`, `articles`,
     * parfois directement le tableau) : on prend la première qui donne quelque chose.
     */
    fun news(payload: JSONObject): List<NewsItem> {
        val data = payload.optJSONObject("data")
        val entries = data?.objects("actualites", "articles", "news")
            ?.ifEmpty { payload.optJSONArray("data").objects() }
            ?: payload.optJSONArray("data").objects()
        return entries.mapNotNull { entry ->
            val title = Encoding.maybeBase64(entry.string("titre", "libelle", "title"))
                .ifBlank { return@mapNotNull null }
            NewsItem(
                id = entry.stringOrNull("id") ?: stableId("news", title),
                title = title,
                content = Text.fromHtml(Encoding.maybeBase64(entry.string("contenu", "texte", "content"))),
                date = parseDate(entry.string("date", "dateDebut", "datePublication")),
                author = entry.string("auteur", "expediteur"),
                category = Text.prettySubject(entry.string("categorie", "type")),
                attachments = attachments(entry, AttachmentSource.RESOURCE),
            )
        }.sortedByDescending { it.date ?: LocalDate.MIN }
    }

    // ── Manuels numériques ───────────────────────────────────────────────────

    fun textbooks(payload: JSONObject): List<Textbook> {
        val data = payload.optJSONObject("data")
        val entries = data?.objects("manuels", "manuelsNumeriques")
            ?.ifEmpty { payload.optJSONArray("data").objects() }
            ?: payload.optJSONArray("data").objects()
        return entries.mapNotNull { entry ->
            val title = entry.string("libelle", "titre", "nom").ifBlank { return@mapNotNull null }
            Textbook(
                id = entry.stringOrNull("id") ?: stableId("manuel", title),
                title = title,
                subject = Text.prettySubject(entry.string("matiere", "discipline")),
                publisher = entry.string("editeur", "publisher"),
                url = entry.string("url", "lien", "href"),
            )
        }.sortedWith(compareBy({ it.subject }, { it.title }))
    }

    // ── Espace de stockage ───────────────────────────────────────────────────

    /**
     * Les fichiers du cloud, à plat.
     *
     * L'API renvoie une arborescence de dossiers imbriqués ; ce qui intéresse ici, c'est de
     * retrouver un fichier et de l'ouvrir. On descend donc l'arbre en gardant le chemin
     * comme simple libellé de dossier.
     */
    fun cloudFiles(payload: JSONObject): List<CloudFile> {
        val roots = payload.optJSONObject("data")?.objects("cloud", "espaces", "documents")
            ?.ifEmpty { payload.optJSONArray("data").objects() }
            ?: payload.optJSONArray("data").objects()
        return buildList { roots.forEach { root -> collectCloud(root, "", this) } }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.folder }, { it.name }))
    }

    private fun collectCloud(entry: JSONObject, path: String, into: MutableList<CloudFile>) {
        val name = entry.string("libelle", "nom", "fichier")
        val isFolder = entry.bool("isFolder", "estDossier") ||
            entry.string("type").equals("folder", ignoreCase = true) ||
            entry.optJSONArray("children") != null
        val here = listOf(path, name).filter { it.isNotBlank() }.joinToString(" / ")
        if (!isFolder && name.isNotBlank()) {
            into += CloudFile(
                id = entry.stringOrNull("id", "idFichier") ?: stableId("cloud", here),
                name = name,
                folder = path,
                date = parseDate(entry.string("date", "dateModification")),
                size = entry.int("taille", "size")?.toLong(),
                ownerId = entry.string("idEspace", "owner"),
            )
        }
        entry.objects("children", "documents", "fichiers", "contenu").forEach { child ->
            collectCloud(child, here, into)
        }
    }

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
