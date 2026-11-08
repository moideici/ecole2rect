package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.api.ApiDay
import fr.ecole2recte.app.data.api.ApiGrade
import fr.ecole2recte.app.data.api.ApiGrades
import fr.ecole2recte.app.data.api.ApiHomework
import fr.ecole2recte.app.data.api.ApiLesson
import fr.ecole2recte.app.data.api.ApiMessage
import fr.ecole2recte.app.data.api.EcoleDirecteApi
import fr.ecole2recte.app.data.api.Parsers
import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.data.cache.ResponseCache
import fr.ecole2recte.app.domain.Badges
import fr.ecole2recte.app.domain.ChildSummary
import fr.ecole2recte.app.domain.SchoolLifeEvent
import fr.ecole2recte.app.domain.SchoolLifeKind
import fr.ecole2recte.app.domain.SchoolLifeSummary
import fr.ecole2recte.app.data.api.ParseReport
import fr.ecole2recte.app.domain.Attachment
import fr.ecole2recte.app.domain.AttachmentFile
import fr.ecole2recte.app.domain.AttachmentSource
import fr.ecole2recte.app.domain.AttachmentSafety
import fr.ecole2recte.app.data.api.Recipients
import fr.ecole2recte.app.domain.Freshness
import fr.ecole2recte.app.domain.MessageDraft
import fr.ecole2recte.app.domain.Recipient
import fr.ecole2recte.app.domain.HomeworkWrite
import fr.ecole2recte.app.domain.GradePeriod
import fr.ecole2recte.app.domain.StaleCause
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.AverageEngine
import fr.ecole2recte.app.domain.SubjectIdentity
import fr.ecole2recte.app.domain.AverageReconciliation
import fr.ecole2recte.app.domain.AverageOrigin
import fr.ecole2recte.app.domain.AverageVerdict
import fr.ecole2recte.app.domain.AveragePresentation
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.AverageSource
import fr.ecole2recte.app.domain.PeriodAverage
import fr.ecole2recte.app.domain.SearchCorpus
import fr.ecole2recte.app.domain.ExamCountdown
import fr.ecole2recte.app.domain.Highlight
import fr.ecole2recte.app.domain.LessonChangeKind
import fr.ecole2recte.app.domain.ScheduleChanges
import fr.ecole2recte.app.domain.HighlightKind
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.HomeworkDetail
import fr.ecole2recte.app.domain.MessageDetail
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.MessagesPage
import fr.ecole2recte.app.domain.NextClass
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.domain.SubjectAverage
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.session.ApiSessionState
import fr.ecole2recte.app.session.initialsOf
import fr.ecole2recte.app.session.titleCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import fr.ecole2recte.app.domain.FrenchFormat
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Les données réelles d'un profil (un compte, ou un élève d'un compte famille).
 *
 * Chaque ressource est mémorisée quelques instants et son chargement n'est lancé qu'une fois
 * même si plusieurs écrans la demandent en même temps : l'accueil, l'agenda et les pastilles
 * lisent les mêmes cours et les mêmes devoirs, et ÉcoleDirecte voit d'un mauvais œil les
 * rafales de requêtes.
 */
class EcoleDirecteRepository(
    private val session: ApiSession,
    private val profile: ActiveProfile,
    private val responseCache: ResponseCache = InMemoryResponseCache(),
    private val homeworkStatus: HomeworkStatusStore = InMemoryHomeworkStatusStore(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ttlMs: Long = 90_000,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Ce que l'utilisateur a autorisé à écrire chez ÉcoleDirecte (rien par défaut). */
    private val writes: WriteAccess = NoWrites,
) : SchoolRepository {
    private class Target(val studentId: String, val mailboxSegment: String, val mailboxId: String)

    private class Cached(val at: Long, val value: Any?)

    private val cache = HashMap<String, Cached>()
    private val locks = HashMap<String, Mutex>()
    private val lockGuard = Mutex()

    private val french = FrenchFormat.locale

    private val owner = CacheOwner(profile.identifier, profile.studentId)
    private val staleResources = HashMap<String, Pair<Long, StaleCause>>()
    private var lastFetchAt: Long? = null
    private val mutableFreshness = MutableStateFlow(Freshness())
    override val freshness: StateFlow<Freshness> = mutableFreshness.asStateFlow()

    override fun invalidate() {
        synchronized(cache) { cache.clear() }
    }

    // --- Accès réseau ------------------------------------------------------------------------

    private suspend fun <T> memo(key: String, load: suspend () -> T): T {
        val mutex = lockGuard.withLock { locks.getOrPut(key) { Mutex() } }
        return mutex.withLock {
            val hit = cache[key]
            val now = clock.millis()
            @Suppress("UNCHECKED_CAST")
            if (hit != null && now - hit.at < ttlMs) return@withLock hit.value as T
            load().also { cache[key] = Cached(clock.millis(), it) }
        }
    }

    /** Lance un appel ; si le jeton a expiré, rouvre la session une fois et rejoue l'appel une fois. */
    private suspend fun <T> withApi(block: (EcoleDirecteApi, Target) -> T): T {
        var reopened = false
        while (true) {
            val ready = session.await(profile.identifier)
            val account = ready.accounts.firstOrNull()
                ?: throw EcoleDirecteException("ÉcoleDirecte n'a renvoyé aucun profil pour ce compte.")
            if (account.type.equals("P", ignoreCase = true)) {
                throw EcoleDirecteException("Les comptes professeur ne sont pas encore pris en charge.")
            }
            val student = profile.studentId
                ?: account.id.takeIf { account.students.isEmpty() }
                ?: account.students.first().id
            val family = account.students.isNotEmpty() || !account.type.equals("E", ignoreCase = true)
            val target = Target(student, if (family) "familles" else "eleves", account.id)
            try {
                return withContext(ioDispatcher) { block(EcoleDirecteApi(ready.client), target) }
            } catch (expired: SessionExpiredException) {
                session.noteExpired()
                if (reopened || !session.reauthenticate()) throw EcoleDirecteException(
                    "La session ÉcoleDirecte a expiré. Réessaie dans un instant.",
                    expired,
                    FailureKind.Service,
                )
                reopened = true
            }
        }
    }

    /**
     * Charge une ressource : le réseau d'abord ; s'il échoue, la dernière réponse enregistrée,
     * et l'écran sait qu'elle est périmée. Sans réseau ni enregistrement, l'erreur remonte.
     */
    private suspend fun <T> resource(
        name: String,
        module: SchoolModule,
        fetch: (EcoleDirecteApi, Target) -> String,
        parse: (Any?, ParseReport) -> T,
        /** Appelé juste avant d'enregistrer une réponse neuve, avec la précédente (`null` si aucune). */
        onStore: ((previous: String?, fresh: String) -> Unit)? = null,
    ): T = memo(name) {
        try {
            // Module fermé par l'établissement : aucune requête (elle compterait comme du trafic) et rien d'inventé.
            if (module in closedModulesOfProfile()) return@memo parse(null, ParseReport())
            val raw = withApi(fetch)
            val report = ParseReport()
            val value = readJson(raw)
            val parsed = parse(value, report)
            if (value == null || parsed == null || (isEmptyResult(parsed) && report.rejected > 0)) {
                // Réponse vide ou illisible : si on a mieux en réserve, on le garde (et on le dit).
                val stored = responseCache.get(owner, name)
                if (stored != null) throw UnusableResponseException("ÉcoleDirecte a répondu sans contenu exploitable.")
                if (parsed == null) throw UnusableResponseException("ÉcoleDirecte a répondu sans contenu exploitable.")
                markFresh(name)
                return@memo parsed
            }
            onStore?.let { hook -> runCatching { hook(responseCache.get(owner, name)?.json, raw) } }
            responseCache.put(owner, name, raw, clock.millis())
            markFresh(name)
            parsed
        } catch (error: EcoleDirecteException) {
            val stored = responseCache.get(owner, name) ?: throw error
            markStale(name, stored.at, causeOf(error))
            parse(readJson(stored.json), ParseReport())
        }
    }

    /** Les modules que l'établissement n'ouvre pas pour ce profil, d'après la liste lue à la connexion. */
    private suspend fun closedModulesOfProfile(): Set<SchoolModule> {
        val account = session.await(profile.identifier).accounts.firstOrNull() ?: return emptySet()
        val student = account.students.firstOrNull { it.id == profile.studentId }
        return closedModules(student?.modules?.takeIf { it.isNotEmpty() } ?: account.modules)
    }

    /** La réponse brute en JSON, ou null si elle est vide, absente (`null`) ou illisible. */
    private fun readJson(raw: String): Any? = try {
        JSONTokener(raw).nextValue().takeIf { it != JSONObject.NULL }
    } catch (error: JSONException) {
        null
    }

    private fun isEmptyResult(parsed: Any?): Boolean = when (parsed) {
        is Collection<*> -> parsed.isEmpty()
        is ApiGrades -> parsed.periods.isEmpty() && parsed.grades.isEmpty() && parsed.subjectAverages.isEmpty()
        else -> false
    }

    private fun causeOf(error: EcoleDirecteException): StaleCause = when {
        error is UnusableResponseException -> StaleCause.Unusable
        error.kind == FailureKind.Network -> StaleCause.Network
        error.kind == FailureKind.Blocked -> StaleCause.Blocked
        error.kind == FailureKind.StaleVersion -> StaleCause.StaleVersion
        error.kind == FailureKind.Service && error.cause is SessionExpiredException -> StaleCause.Expired
        error.kind == FailureKind.Service -> StaleCause.Service
        else -> StaleCause.Other
    }

    private fun markFresh(name: String) {
        synchronized(staleResources) {
            staleResources.remove(name)
            lastFetchAt = clock.millis()
            publishFreshness()
        }
    }

    private fun markStale(name: String, at: Long, cause: StaleCause) {
        synchronized(staleResources) {
            staleResources[name] = at to cause
            publishFreshness()
        }
    }

    private fun publishFreshness() {
        mutableFreshness.value =
            if (staleResources.isEmpty()) Freshness(lastFetchAt, stale = false)
            else staleResources.values.minBy { it.first }.let { (at, cause) -> Freshness(at, stale = true, cause = cause) }
    }

    private suspend fun lessons(): List<ApiLesson> = resource(
        "lessons",
        SchoolModule.Schedule,
        fetch = { api, target ->
            val monday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            api.timetable(target.studentId, monday, monday.plusDays(13))
        },
        parse = { data, report -> Parsers.timetable(data, report) },
        onStore = ::rememberLessonChanges,
    )

    /**
     * Quand une lecture neuve de l'emploi du temps change des cours à venir (déplacé, remplacé, salle,
     * professeur…), garde l'ancienne réponse à part : l'agenda s'en sert pour barrer l'ancienne valeur.
     * Rien d'autre n'est conservé et rien n'est envoyé nulle part.
     */
    private fun rememberLessonChanges(previous: String?, fresh: String) {
        if (previous == null || previous == fresh) return
        val before = Parsers.timetable(readJson(previous)).map { it.toEntry() }
        val after = Parsers.timetable(readJson(fresh)).map { it.toEntry() }
        val upcoming = after.map { it.date }.filter { it >= today().toString() }.toSet()
        if (ScheduleChanges.diff(before, after, upcoming).any { it.kind != LessonChangeKind.Restored }) {
            responseCache.put(owner, LESSONS_BEFORE, previous, clock.millis())
        }
    }

    /** Les cours à venir, avec ce qu'ils étaient avant leur dernier changement. */
    private fun withChangeHistory(entries: List<ScheduleEntry>): List<ScheduleEntry> {
        val stored = responseCache.get(owner, LESSONS_BEFORE) ?: return entries
        val before = Parsers.timetable(readJson(stored.json)).map { it.toEntry() }
        val upcoming = entries.map { it.date }.filter { it >= today().toString() }.toSet()
        return ScheduleChanges.annotate(before, entries, upcoming)
    }

    /** Le cahier de textes, avec les cases cochées sur l'appareil qui l'emportent sur celles du serveur. */
    private suspend fun homeworkItems(): List<ApiHomework> {
        val items = resource("homework", SchoolModule.Homework, { api, target -> api.homework(target.studentId) }, { data, report -> Parsers.homeworkSummary(data, report) })
        val overrides = homeworkStatus.overrides(owner)
        return if (overrides.isEmpty()) items else items.map { it.copy(done = overrides[it.id] ?: it.done) }
    }

    private suspend fun dayContent(date: String): ApiDay = resource(
        "day-$date",
        SchoolModule.Homework,
        { api, target -> api.dayContent(target.studentId, date) },
        { data, _ -> Parsers.dayContent(data, date) },
    )

    private suspend fun gradesPayload(): ApiGrades =
        resource("grades", SchoolModule.Grades, { api, target -> api.grades(target.studentId) }, { data, report -> Parsers.grades(data, report) })

    private suspend fun inbox(page: Int = 0): List<ApiMessage> = resource(
        if (page == 0) "messages" else "messages-$page",
        SchoolModule.Messages,
        { api, target -> api.messages(target.mailboxSegment, target.mailboxId, page, MESSAGES_PER_PAGE) },
        { data, report -> Parsers.messages(data, report) },
    )

    private fun today(): LocalDate = LocalDate.now(clock)
    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    /** Une section qui échoue n'empêche pas l'accueil de s'afficher ; si tout échoue, l'erreur remonte. */
    private suspend fun <T> optional(errors: MutableList<Throwable>, fallback: T, load: suspend () -> T): T =
        try {
            load()
        } catch (error: SessionExpiredException) {
            errors += error
            fallback
        } catch (error: EcoleDirecteException) {
            errors += error
            fallback
        }

    // --- SchoolRepository --------------------------------------------------------------------

    override suspend fun home(): HomeData {
        val errors = mutableListOf<Throwable>()
        val lessons = optional(errors, emptyList()) { lessons() }
        val homework = optional(errors, emptyList()) { homeworkItems() }
        val grades = optional(errors, null) { gradesPayload() }
        val inbox = optional(errors, emptyList()) { inbox() }
        val family = optional(errors, emptyList()) { family() }
        if (errors.size >= 5) throw errors.first()

        val current = now()
        val upcoming = lessons.filter { !it.canceled && parse(it.end) > current }.minByOrNull { it.start }
        return HomeData(
            greeting = "Bonjour ${profile.firstName}",
            dateLabel = FrenchFormat.capitalized(FrenchFormat.weekdayDayMonthYear(today())),
            nextClass = upcoming?.let {
                NextClass(it.toSubject(), startLabel(it, current), progress(it, current), afterLabel(lessons, it))
            },
            today = lessons.filter { it.start.startsWith(today().toString()) }.map { it.toEntry() },
            reminder = pendingHomework(homework).firstOrNull()?.toHomework(),
            highlights = highlights(lessons, homework, grades, inbox),
            family = family,
            exams = ExamCountdown.upcoming(homework.map { it.toHomework() }, today()),
            grades = grades?.let(::overview) ?: GradeOverview("–", "", emptyList()),
            allSubjects = lessons.map { it.toSubject() }.distinctBy { it.name },
        )
    }

    override suspend fun nextClass(): NextClass? {
        val lessons = lessons()
        val current = now()
        val upcoming = lessons.filter { !it.canceled && parse(it.end) > current }.minByOrNull { it.start } ?: return null
        return NextClass(upcoming.toSubject(), startLabel(upcoming, current), progress(upcoming, current), afterLabel(lessons, upcoming))
    }

    override suspend fun schedule(): ScheduleWeek {
        val monday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        // La semaine en cours et la suivante : de quoi regarder vendredi soir ce qui attend lundi.
        val window = lessons().filter { lesson ->
            val day = LocalDate.parse(lesson.start.substring(0, 10))
            day in monday..sunday.plusDays(7)
        }
        return ScheduleWeek(
            label = "Semaine du ${FrenchFormat.dayMonth(monday)}",
            entries = withChangeHistory(window.map { it.toEntry() }),
            weekStart = monday.toString(),
            today = today().toString(),
        )
    }

    override suspend fun homework(): List<Homework> {
        val today = today().toString()
        return homeworkItems().filter { it.dueDate >= today }.sortedBy { it.dueDate }.map { it.toHomework() }
    }

    override suspend fun homeworkDetail(homework: Homework): HomeworkDetail {
        val day = dayContent(homework.dueDate)
        val match = day.homework.firstOrNull { it.id == homework.id }
            ?: day.homework.firstOrNull { it.subject.equals(homework.subject, ignoreCase = true) }
        val lesson = day.sessions.firstOrNull { it.subject.prettySubject() == homework.subject }
        return HomeworkDetail(
            content = match?.content.orEmpty(),
            attachments = match?.attachments?.map { it.name }.orEmpty(),
            files = match?.attachments?.map { Attachment(it.id, it.name, it.type, AttachmentSource.Homework, it.sizeBytes, it.mimeType) }.orEmpty(),
            lessonContent = lesson?.content.orEmpty(),
        )
    }

    override suspend fun setHomeworkDone(id: String, done: Boolean): HomeworkWrite {
        // Toujours sur le téléphone d'abord : cocher ne dépend jamais du réseau.
        homeworkStatus.set(owner, id, done)
        if (!writes.homeworkSync()) return HomeworkWrite.LocalOnly
        return try {
            withApi { api, target -> api.markHomework(target.studentId, id, done) }
            // ÉcoleDirecte a la même case : le serveur redevient la référence, le prochain chargement la relira.
            homeworkStatus.remove(owner, id)
            invalidate()
            HomeworkWrite.Synced
        } catch (error: EcoleDirecteException) {
            HomeworkWrite.SyncFailed
        }
    }

    override suspend fun grades(): GradeOverview = overview(gradesPayload())

    override suspend fun grades(periodCode: String?): GradeOverview = overview(gradesPayload(), periodCode)

    override suspend fun cachedGradePeriods(): List<GradeOverview> = withContext(ioDispatcher) {
        if (SchoolModule.Grades in profile.closedModules) return@withContext emptyList()
        val stored = responseCache.get(owner, "grades") ?: return@withContext emptyList()
        val payload = Parsers.grades(readJson(stored.json))
        payload.periods.filter { !it.annual }.ifEmpty { payload.periods }.map { overview(payload, it.code) }
    }

    override suspend fun messages(): List<MessagePreview> = messagesPage(0).items

    override suspend fun messagesPage(page: Int): MessagesPage {
        val messages = inbox(page)
        return MessagesPage(
            items = messages.map {
                MessagePreview(
                    sender = it.sender,
                    preview = it.subject,
                    unread = !it.read,
                    id = it.id,
                    dateLabel = dateLabel(it.date),
                )
            },
            hasMore = messages.size >= MESSAGES_PER_PAGE,
        )
    }

    override suspend fun messageDetail(id: String): MessageDetail {
        val detail = resource(
            "message-$id",
            SchoolModule.Messages,
            { api, target -> api.messageDetail(target.mailboxSegment, target.mailboxId, id) },
            { data, _ -> Parsers.messageDetail(data) },
        ) ?: throw EcoleDirecteException("Ce message est illisible pour le moment.")
        return MessageDetail(
            subject = detail.subject,
            sender = detail.sender,
            dateLabel = dateLabel(detail.date, long = true),
            body = detail.body.ifBlank { "(message vide)" },
            recipients = detail.recipients,
            attachments = detail.attachments.map { it.name },
            files = detail.attachments.map { Attachment(it.id, it.name, it.type, AttachmentSource.Message, it.sizeBytes, it.mimeType) },
            replyTo = detail.replyTo,
        )
    }

    override suspend fun recipients(): List<Recipient> {
        if (!writes.messageSending()) throw EcoleDirecteException("L'envoi de messages est désactivé dans Plus.")
        val groups = listOf("professeurs" to ("Enseignants" to "P"), "administrations" to ("Administration" to "A"))
        val found = mutableListOf<Recipient>()
        var failure: EcoleDirecteException? = null
        for ((path, info) in groups) {
            try {
                val raw = withApi { api, _ -> api.contacts(path) }
                found += Recipients.parse(readJson(raw), info.first, info.second)
            } catch (error: EcoleDirecteException) {
                failure = error
            }
        }
        if (found.isEmpty() && failure != null) throw failure
        return found
    }

    override suspend fun sendMessage(draft: MessageDraft, confirmed: Boolean) {
        // Trois verrous, dans cet ordre, avant toute requête : confirmation, autorisation, message complet.
        if (!confirmed) throw EcoleDirecteException("L'envoi doit être confirmé par l'utilisateur.")
        if (!writes.messageSending()) throw EcoleDirecteException("L'envoi de messages est désactivé dans Plus.")
        draft.problems().firstOrNull()?.let { throw EcoleDirecteException(it) }
        val year = today().let { if (it.monthValue >= 8) "${it.year}-${it.year + 1}" else "${it.year - 1}-${it.year}" }
        // Une seule tentative : un envoi qui échoue n'est jamais renvoyé par l'application.
        withApi { api, target ->
            api.sendMessage(target.mailboxSegment, target.mailboxId, draft.subject, draft.recipients, draft.body, year)
        }
    }

    override suspend fun downloadAttachment(attachment: Attachment): AttachmentFile {
        AttachmentSafety.blockedReason(attachment.name, attachment.mimeType)?.let { throw EcoleDirecteException(it) }
        if ((attachment.sizeBytes ?: 0) > MAX_DOWNLOAD_BYTES) throw EcoleDirecteException("Ce fichier est trop volumineux (plus de 25 Mo).")
        val download = withApi { api, _ -> api.attachment(attachment.id, attachment.type, attachment.source) }
        val name = download.fileName ?: attachment.name
        AttachmentSafety.blockedReason(name, download.contentType)?.let { throw EcoleDirecteException(it) }
        // Le nom et le type de la liste restent contrôlés même si l'en-tête change le nom.
        AttachmentSafety.blockedReason(attachment.name, download.contentType)?.let { throw EcoleDirecteException(it) }
        return AttachmentFile(download.bytes, AttachmentSafety.mimeType(download.contentType, name), AttachmentSafety.safeFileName(name))
    }

    /**
     * Pour la recherche : relit **uniquement** les réponses déjà enregistrées (aucune requête, aucune
     * reconnexion). Une ressource jamais enregistrée donne simplement une liste vide.
     */
    override suspend fun searchCorpus(): SearchCorpus = withContext(ioDispatcher) {
        fun stored(name: String): Any? = responseCache.get(owner, name)?.let { readJson(it.json) }
        val today = today().toString()
        val overrides = homeworkStatus.overrides(owner)
        val homework = stored("homework")?.let { Parsers.homeworkSummary(it) }.orEmpty()
            .map { it.copy(done = overrides[it.id] ?: it.done) }
            .filter { it.dueDate >= today }.sortedBy { it.dueDate }.map { it.toHomework() }
        val lessons = stored("lessons")?.let { Parsers.timetable(it) }.orEmpty().map { it.toEntry() }
        val grades = stored("grades")?.let { Parsers.grades(it) }?.grades.orEmpty().map { it.toEntry() }
        // La première page de la boîte, plus les pages suivantes si l'utilisateur les a déjà chargées.
        val messages = buildList {
            for (page in 0 until MAX_SEARCH_PAGES) {
                val name = if (page == 0) "messages" else "messages-$page"
                val items = stored(name)?.let { Parsers.messages(it) } ?: break
                addAll(items)
            }
        }.map { MessagePreview(it.sender, it.subject, unread = !it.read, id = it.id, dateLabel = dateLabel(it.date)) }
        SearchCorpus(homework, messages, grades, lessons)
    }

    /** « 10:15 » pour aujourd'hui, « 4 nov. » sinon ; la forme longue ajoute le jour et l'heure. */
    private fun dateLabel(iso: String?, long: Boolean = false): String {
        if (iso == null) return ""
        val moment = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return ""
        if (long) return FrenchFormat.capitalized(FrenchFormat.weekdayDayMonthAtTime(moment))
        return if (moment.toLocalDate() == today()) FrenchFormat.time(moment)
        else FrenchFormat.dayMonthShort(moment.toLocalDate())
    }

    override suspend fun schoolLife(): SchoolLifeSummary {
        val events = resource("schoollife", SchoolModule.SchoolLife, { api, target -> api.schoolLife(target.studentId) }, { data, report -> Parsers.schoolLife(data, report) })
        val kinds = mapOf(
            "absence" to SchoolLifeKind.Absence,
            "retard" to SchoolLifeKind.Late,
            "sanction" to SchoolLifeKind.Sanction,
            "encouragement" to SchoolLifeKind.Encouragement,
        )
        val mapped = events.map { event ->
            val kind = kinds[event.kind] ?: SchoolLifeKind.Other
            SchoolLifeEvent(
                kind = kind,
                title = event.label.ifBlank {
                    when (kind) {
                        SchoolLifeKind.Absence -> "Absence"
                        SchoolLifeKind.Late -> "Retard"
                        SchoolLifeKind.Sanction -> "Sanction"
                        SchoolLifeKind.Encouragement -> "Encouragement"
                        SchoolLifeKind.Other -> "Vie scolaire"
                    }
                }.capitalized(),
                dateLabel = shortDay(event.start.take(10)).orEmpty() + event.duration.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                detail = listOf(event.reason, event.comment).filter { it.isNotBlank() }.joinToString(" — "),
                justified = event.justified,
                id = event.id,
                date = event.start.take(10),
                duration = event.duration,
                countedMinutes = fr.ecole2recte.app.domain.SchoolLifeLogic.countedMinutes(event.duration),
            )
        }
        return SchoolLifeSummary(
            absences = mapped.count { it.kind == SchoolLifeKind.Absence },
            unjustified = mapped.count { it.kind == SchoolLifeKind.Absence && !it.justified },
            lates = mapped.count { it.kind == SchoolLifeKind.Late },
            sanctions = mapped.count { it.kind == SchoolLifeKind.Sanction },
            encouragements = mapped.count { it.kind == SchoolLifeKind.Encouragement },
            events = mapped,
        )
    }

    /**
     * Un résumé par enfant d'un compte famille : prochain cours, devoirs urgents, nouvelles notes,
     * moyenne. L'enfant affiché réutilise les données déjà chargées ; les autres ont leur propre
     * mémoire et leur propre cache.
     */
    private suspend fun family(): List<ChildSummary> {
        val account = session.await(profile.identifier).accounts.firstOrNull() ?: return emptyList()
        if (account.students.isEmpty()) return emptyList()
        val selected = profile.studentId ?: account.students.first().id
        return account.students.map { student ->
            val repository = if (student.id == selected) this else EcoleDirecteRepository(
                session,
                ActiveProfile(
                    identifier = profile.identifier,
                    studentId = student.id,
                    firstName = titleCase(student.firstName),
                    displayName = "${titleCase(student.firstName)} ${titleCase(student.lastName)}".trim(),
                    initials = initialsOf(student.firstName, student.lastName, profile.identifier),
                ),
                responseCache,
                homeworkStatus,
                clock,
                ttlMs,
                ioDispatcher,
            )
            repository.summary(student, student.id == selected)
        }
    }

    private suspend fun summary(student: Student, selected: Boolean): ChildSummary {
        val errors = mutableListOf<Throwable>()
        val lessons = optional(errors, emptyList()) { lessons() }
        val homework = optional(errors, emptyList()) { homeworkItems() }
        val grades = optional(errors, null) { gradesPayload() }
        val current = now()
        val next = lessons.filter { !it.canceled && parse(it.end) > current }.minByOrNull { it.start }
        val limit = today().plusDays(1)
        val since = today().minusDays(7).toString()
        return ChildSummary(
            studentId = student.id,
            name = "${titleCase(student.firstName)} ${titleCase(student.lastName)}".trim(),
            initials = initialsOf(student.firstName, student.lastName, student.id),
            className = student.className,
            nextClass = next?.let { "${it.subject.prettySubject()} · ${startLabel(it, current)}" },
            urgentHomework = pendingHomework(homework).count { LocalDate.parse(it.dueDate) <= limit },
            newGrades = grades?.grades?.count { it.counted && it.value != null && (it.date ?: "") >= since } ?: 0,
            average = grades?.let { overview(it).average }?.takeIf { it != "–" },
            selected = selected,
        )
    }

    override suspend fun badges(): Badges {
        val errors = mutableListOf<Throwable>()
        val unread = optional(errors, 0) { inbox().count { !it.read } }
        val urgent = optional(errors, 0) {
            val limit = today().plusDays(1)
            pendingHomework(homeworkItems()).count { LocalDate.parse(it.dueDate) <= limit }
        }
        val fresh = optional(errors, 0) {
            val since = today().minusDays(7).toString()
            gradesPayload().grades.count { it.counted && it.value != null && (it.date ?: "") >= since }
        }
        if (errors.size == 3) throw errors.first()
        return Badges(newGrades = fresh, urgentHomework = urgent, unreadMessages = unread)
    }

    // --- Conversions -------------------------------------------------------------------------

    private fun pendingHomework(items: List<ApiHomework>): List<ApiHomework> {
        val today = today().toString()
        return items.filter { !it.done && it.dueDate >= today }.sortedBy { it.dueDate }
    }

    /**
     * Ce qui mérite un coup d'œil dès l'ouverture, du plus pressant au moins pressant :
     * cours annulé, cours modifié, évaluation proche, message non lu, nouvelle note.
     */
    private fun highlights(
        lessons: List<ApiLesson>,
        homework: List<ApiHomework>,
        grades: ApiGrades?,
        inbox: List<ApiMessage>,
    ): List<Highlight> {
        val today = today()
        val list = mutableListOf<Highlight>()
        fun whenLabel(lesson: ApiLesson): String? {
            val day = LocalDate.parse(lesson.start.substring(0, 10))
            val word = when (day) {
                today -> "aujourd'hui"
                today.plusDays(1) -> "demain"
                else -> return null
            }
            return "$word à ${lesson.start.substring(11, 16)}"
        }
        lessons.filter { it.canceled }.forEach { lesson ->
            whenLabel(lesson)?.let {
                list += Highlight(HighlightKind.Canceled, "Cours annulé : ${lesson.subject.prettySubject()}", it)
            }
        }
        lessons.filter { it.modified && !it.canceled }.forEach { lesson ->
            whenLabel(lesson)?.let {
                list += Highlight(
                    HighlightKind.Changed,
                    "Cours modifié : ${lesson.subject.prettySubject()}",
                    listOfNotNull(it, lesson.room.takeIf { room -> room.isNotBlank() }?.let { room -> "salle $room" }).joinToString(" · "),
                )
            }
        }
        pendingHomework(homework).filter { it.exam && LocalDate.parse(it.dueDate) <= today.plusDays(3) }.forEach { task ->
            val day = FrenchFormat.weekdayDayMonth(LocalDate.parse(task.dueDate))
            list += Highlight(HighlightKind.Exam, "Évaluation en ${task.subject.prettySubject()}", day.capitalized())
        }
        val unread = inbox.count { !it.read }
        if (unread > 0) {
            list += Highlight(
                HighlightKind.Message,
                if (unread == 1) "1 message non lu" else "$unread messages non lus",
                inbox.firstOrNull { !it.read }?.let { "${it.sender} · ${it.subject}" }.orEmpty(),
            )
        }
        // Une note : la matière à gauche, la note avec son coefficient en grand à droite, le détail en petit.
        grades?.grades
            ?.filter { it.counted && it.value != null && (it.date ?: "") >= today.minusDays(7).toString() }
            ?.take(2)
            ?.forEach { grade ->
                list += Highlight(
                    kind = HighlightKind.Grade,
                    title = grade.subject.prettySubject(),
                    detail = listOfNotNull(
                        grade.label.takeIf { it.isNotBlank() },
                        shortDay(grade.date),
                        grade.classAverage?.let { "classe ${decimal(it)}" },
                    ).joinToString(" · "),
                    value = "${coefficientText(grade.value!!)}/${grade.scale.toInt()}",
                    valueSub = "coef. ${coefficientText(grade.coefficient)}",
                )
            }
        return list.take(6)
    }

    private fun coefficientText(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else decimal(value)

    private fun shortDay(iso: String?): String? =
        iso?.let { runCatching { FrenchFormat.dayMonthShort(LocalDate.parse(it)) }.getOrNull() }

    private fun afterLabel(lessons: List<ApiLesson>, current: ApiLesson): String? {
        val following = lessons.filter { !it.canceled && it.start >= current.end }.minByOrNull { it.start } ?: return null
        val sameDay = following.start.substring(0, 10) == current.start.substring(0, 10)
        val time = following.start.substring(11, 16)
        return "Ensuite : ${following.subject.prettySubject()} " + if (sameDay) "à $time" else "${dayName(following.start).lowercase(french)} à $time"
    }

    private fun ApiHomework.toHomework() = Homework(
        title = content.lineSequence().firstOrNull { it.isNotBlank() } ?: if (exam) "Évaluation prévue" else "Travail à faire",
        subject = subject.prettySubject(),
        due = FrenchFormat.capitalized(FrenchFormat.weekdayDayMonth(LocalDate.parse(dueDate))),
        colorIndex = colorFor(subject),
        id = id,
        dueDate = dueDate,
        done = done,
        exam = exam,
        content = content,
    )

    private fun ApiLesson.toEntry() = ScheduleEntry(
        subject = toSubject(),
        whenLabel = "${dayName(start)} · ${start.substring(11, 16)} — ${end.substring(11, 16)}",
        canceled = canceled,
        date = start.substring(0, 10),
        start = start.substring(11, 16),
        end = end.substring(11, 16),
        exam = exam,
        note = note,
        modified = modified,
        kind = kind,
    )

    private fun ApiLesson.toSubject() = Subject(subject.prettySubject(), teacher, room, colorFor(subject))

    private fun ApiGrade.toEntry() = GradeEntry(
        id = id,
        subject = subject.prettySubject(),
        colorIndex = colorFor(subject),
        label = label,
        value = value,
        scale = scale,
        coefficient = coefficient,
        date = date,
        classAverage = classAverage,
        minimum = minimum,
        maximum = maximum,
        counted = counted,
        periodCode = periodCode,
        rawValue = rawValue,
        subjectCode = subjectCode,
    )

    private fun overview(payload: ApiGrades, requestedCode: String? = null): GradeOverview {
        fun decimal(value: Double) = FrenchFormat.decimal(value, 2)
        val periods = payload.periods.filter { !it.annual }.ifEmpty { payload.periods }
        val current = periods.firstOrNull { it.code == requestedCode }
            ?: PeriodSelection.select(payload.periods, payload.grades, today())

        val periodGrades = payload.grades.filter { current != null && it.periodCode == current.code }.map { it.toEntry() }
        val official = payload.subjectAverages.filter { current != null && it.periodCode == current.code }
        val identities = official.map { SubjectIdentity(it.subjectCode, it.subCode, it.subject.prettySubject()) }.toMutableList()
        val matched = periodGrades.groupBy { grade ->
            SubjectIdentity.match(grade.subjectCode, grade.subject, identities) ?: identities.size.also {
                identities += SubjectIdentity(grade.subjectCode, "", grade.subject)
            }
        }
        val ordered = identities.indices.filter { official.getOrNull(it)?.isSubSubject != true }.flatMap { parent ->
            listOf(parent) + identities.indices.filter { child ->
                official.getOrNull(child)?.isSubSubject == true && identities[child].code.isNotBlank() && identities[child].code == identities[parent].code
            }
        }.distinct().let { it + identities.indices.filter { index -> index !in it } }
        val bySubject = ordered.mapNotNull { index ->
            val identity = identities[index]
            val name = identity.name
            val line = official.getOrNull(index)
            val grades = matched[index].orEmpty().map { it.copy(subject = name) }
            // La moyenne de l'établissement prime ; à défaut, la moyenne pondérée des notes connues.
            val verdict = AverageReconciliation.reconcile(line?.average, grades, current?.computedOn)
            val average = verdict.value
            if (average == null && grades.isEmpty()) return@mapNotNull null
            SubjectAverage(
                subject = Subject(name, line?.teacher.orEmpty(), "", colorFor(line?.subject ?: name)),
                average = average?.let(::decimal) ?: "–",
                classAverage = line?.classAverage?.let(::decimal),
                grades = grades,
                coefficient = line?.coefficient,
                appreciation = line?.appreciation.orEmpty(),
                minimum = line?.minimum?.let(::decimal),
                maximum = line?.maximum?.let(::decimal),
                averageOfficial = verdict.origin == AverageOrigin.Official,
                id = identity.key,
                subjectCode = identity.code,
                isSubSubject = line?.isSubSubject == true,
                verdict = verdict,
                classAverageValue = line?.classAverage,
                minimumValue = line?.minimum,
                maximumValue = line?.maximum,
                classPosition = line?.classPosition,
            )
        }
        // Moyenne générale : celle de l'établissement, sinon la moyenne des matières (pondérée par
        // leur coefficient quand il est connu), sinon celle de toutes les notes.
        val behind = bySubject.flatMap { it.verdict?.behind.orEmpty() }.distinctBy { it.id }
        val generalVerdict = AverageReconciliation.reconcile(current?.average, periodGrades, current?.computedOn)
        val mustRecompute = behind.isNotEmpty() || generalVerdict.origin == AverageOrigin.Recomputed
        val weights = bySubject.filter { mustRecompute || it.verdict?.official != null }.map { AverageEngine.SubjectWeight(it.id, it.verdict?.value, it.coefficient, it.isSubSubject) }
        val result = AverageEngine.general(if (mustRecompute) null else current?.average, weights, periodGrades)
        val general = result.value
        val source = if (mustRecompute && general != null) AverageSource.Recomputed else result.source
        val verdict = AverageVerdict(general, when {
            source == AverageSource.Recomputed -> AverageOrigin.Recomputed
            source == AverageSource.Official -> AverageOrigin.Official
            general != null -> AverageOrigin.Computed
            else -> AverageOrigin.None
        }, current?.average, (behind + generalVerdict.behind).distinctBy { it.id })
        val previous = current?.let { c -> periods.takeWhile { it.code != c.code }.asReversed().firstNotNullOfOrNull { p ->
            overview(payload, p.code).takeIf { it.verdict?.value != null }
        } }
        return GradeOverview(
            average = general?.let(::decimal) ?: "–",
            trend = AveragePresentation.trend(general, source, previous?.verdict?.value, previous?.averageSource ?: AverageSource.None, 2),
            bySubject = bySubject,
            recent = payload.grades.filter { it.value != null }.take(6).map { it.toEntry() },
            all = payload.grades.filter { it.value != null }.map { it.toEntry() },
            evolution = periods.mapNotNull { p -> p.average?.let { PeriodAverage(p.name, it) } },
            classAverage = current?.classAverage?.let(::decimal),
            periodName = current?.name.orEmpty(),
            periodCode = current?.code.orEmpty(),
            periods = periods.map { GradePeriod(it.code, it.name, selected = it.code == current?.code, average = it.average?.let(::decimal), value = it.average, coefficient = it.coefficient, annual = it.annual) },
            appreciation = current?.appreciation.orEmpty(),
            averageSource = source,
            verdict = verdict,
            classAverageValue = current?.classAverage,
            classPosition = current?.classPosition,
        )
    }

    private fun decimal(value: Double): String = FrenchFormat.decimal(value)

    private fun parse(iso: String): LocalDateTime = LocalDateTime.parse(iso)

    private fun dayName(iso: String): String =
        LocalDate.parse(iso.substring(0, 10)).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, french).capitalized()

    private fun startLabel(lesson: ApiLesson, now: LocalDateTime): String {
        val start = parse(lesson.start)
        val time = lesson.start.substring(11, 16)
        if (start <= now) return "En cours  ·  jusqu'à ${lesson.end.substring(11, 16)}"
        val minutes = Duration.between(now, start).toMinutes()
        return when {
            start.toLocalDate() != now.toLocalDate() -> "${dayName(lesson.start)}  ·  $time"
            minutes < 60 -> "$time  ·  dans $minutes min"
            else -> "$time  ·  dans ${minutes / 60} h ${FrenchFormat.twoDigits((minutes % 60).toInt())}"
        }
    }

    private fun progress(lesson: ApiLesson, now: LocalDateTime): Float {
        val start = parse(lesson.start)
        val end = parse(lesson.end)
        if (now <= start) return 0f
        val total = Duration.between(start, end).seconds.coerceAtLeast(1)
        return (Duration.between(start, now).seconds.toFloat() / total).coerceIn(0f, 1f)
    }

    private fun colorFor(subject: String): Int = abs(subject.trim().lowercase(french).hashCode()) % PALETTE_SIZE

    private fun String.capitalized() = replaceFirstChar { it.titlecase(french) }

    /** ÉcoleDirecte écrit les matières en majuscules : « PHILOSOPHIE » devient « Philosophie ». */
    private fun String.prettySubject() = trim().lowercase(french).capitalized()

    private companion object {
        const val PALETTE_SIZE = 10
        const val MESSAGES_PER_PAGE = 50

        /** Nom, dans le cache, de la lecture de l'emploi du temps d'avant le dernier changement. */
        const val LESSONS_BEFORE = "lessons-before"
        const val MAX_SEARCH_PAGES = 20
    }
}
