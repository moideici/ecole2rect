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
import fr.ecole2recte.app.domain.Freshness
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeMath
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.PeriodAverage
import fr.ecole2recte.app.domain.Highlight
import fr.ecole2recte.app.domain.HighlightKind
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.HomeworkDetail
import fr.ecole2recte.app.domain.MessageDetail
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.MessagesPage
import fr.ecole2recte.app.domain.NextClass
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.domain.ScheduleSlot
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.domain.SubjectAverage
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.session.ApiSessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
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
) : SchoolRepository {
    private class Target(val studentId: String, val mailboxSegment: String, val mailboxId: String)

    private class Cached(val at: Long, val value: Any?)

    private val cache = HashMap<String, Cached>()
    private val locks = HashMap<String, Mutex>()
    private val lockGuard = Mutex()

    private val french = Locale.FRENCH

    private val owner = CacheOwner(profile.identifier, profile.studentId)
    private val staleResources = HashMap<String, Long>()
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
        fetch: (EcoleDirecteApi, Target) -> String,
        parse: (Any?) -> T,
    ): T = memo(name) {
        try {
            val raw = withApi(fetch)
            responseCache.put(owner, name, raw, clock.millis())
            markFresh(name)
            parse(JSONTokener(raw).nextValue())
        } catch (error: EcoleDirecteException) {
            val stored = responseCache.get(owner, name) ?: throw error
            markStale(name, stored.at)
            parse(JSONTokener(stored.json).nextValue())
        }
    }

    private fun markFresh(name: String) {
        synchronized(staleResources) {
            staleResources.remove(name)
            lastFetchAt = clock.millis()
            publishFreshness()
        }
    }

    private fun markStale(name: String, at: Long) {
        synchronized(staleResources) {
            staleResources[name] = at
            publishFreshness()
        }
    }

    private fun publishFreshness() {
        mutableFreshness.value =
            if (staleResources.isEmpty()) Freshness(lastFetchAt, stale = false)
            else Freshness(staleResources.values.min(), stale = true)
    }

    private suspend fun lessons(): List<ApiLesson> = resource(
        "lessons",
        fetch = { api, target ->
            val monday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            api.timetable(target.studentId, monday, monday.plusDays(13))
        },
        parse = Parsers::timetable,
    )

    /** Le cahier de textes, avec les cases cochées sur l'appareil qui l'emportent sur celles du serveur. */
    private suspend fun homeworkItems(): List<ApiHomework> {
        val items = resource("homework", { api, target -> api.homework(target.studentId) }, Parsers::homeworkSummary)
        val overrides = homeworkStatus.overrides(owner)
        return if (overrides.isEmpty()) items else items.map { it.copy(done = overrides[it.id] ?: it.done) }
    }

    private suspend fun dayContent(date: String): ApiDay = resource(
        "day-$date",
        { api, target -> api.dayContent(target.studentId, date) },
        { Parsers.dayContent(it, date) },
    )

    private suspend fun gradesPayload(): ApiGrades =
        resource("grades", { api, target -> api.grades(target.studentId) }, Parsers::grades)

    private suspend fun inbox(page: Int = 0): List<ApiMessage> = resource(
        if (page == 0) "messages" else "messages-$page",
        { api, target -> api.messages(target.mailboxSegment, target.mailboxId, page, MESSAGES_PER_PAGE) },
        Parsers::messages,
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
        if (errors.size == 4) throw errors.first()

        val current = now()
        val todays = lessons.filter { it.start.startsWith(today().toString()) && !it.canceled }
        val upcoming = lessons.filter { !it.canceled && parse(it.end) > current }.minByOrNull { it.start }
        return HomeData(
            greeting = "Bonjour ${profile.firstName}",
            dateLabel = today().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", french)).capitalized(),
            nextClass = upcoming?.let {
                NextClass(it.toSubject(), startLabel(it, current), progress(it, current), afterLabel(lessons, it))
            },
            today = todays.map { ScheduleSlot(it.start.substring(11, 16), it.toSubject()) },
            reminder = pendingHomework(homework).firstOrNull()?.toHomework(),
            highlights = highlights(lessons, homework, grades, inbox),
            grades = grades?.let(::overview) ?: GradeOverview("–", "", emptyList()),
            allSubjects = lessons.map { it.toSubject() }.distinctBy { it.name },
        )
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
            label = "Semaine du ${monday.format(DateTimeFormatter.ofPattern("d MMMM", french))}",
            entries = window.map { lesson ->
                ScheduleEntry(
                    subject = lesson.toSubject(),
                    whenLabel = "${dayName(lesson.start)} · ${lesson.start.substring(11, 16)} — ${lesson.end.substring(11, 16)}",
                    canceled = lesson.canceled,
                    date = lesson.start.substring(0, 10),
                    start = lesson.start.substring(11, 16),
                    end = lesson.end.substring(11, 16),
                    exam = lesson.exam,
                    note = lesson.note,
                )
            },
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
            lessonContent = lesson?.content.orEmpty(),
        )
    }

    override suspend fun setHomeworkDone(id: String, done: Boolean) {
        homeworkStatus.set(owner, id, done)
    }

    override suspend fun grades(): GradeOverview = overview(gradesPayload())

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
            { api, target -> api.messageDetail(target.mailboxSegment, target.mailboxId, id) },
            Parsers::messageDetail,
        ) ?: throw EcoleDirecteException("Ce message est illisible pour le moment.")
        return MessageDetail(
            subject = detail.subject,
            sender = detail.sender,
            dateLabel = dateLabel(detail.date, long = true),
            body = detail.body.ifBlank { "(message vide)" },
            recipients = detail.recipients,
            attachments = detail.attachments.map { it.name },
        )
    }

    /** « 10:15 » pour aujourd'hui, « 4 nov. » sinon ; la forme longue ajoute le jour et l'heure. */
    private fun dateLabel(iso: String?, long: Boolean = false): String {
        if (iso == null) return ""
        val moment = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return ""
        if (long) return moment.format(DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", french)).capitalized()
        return if (moment.toLocalDate() == today()) moment.format(DateTimeFormatter.ofPattern("HH:mm"))
        else moment.format(DateTimeFormatter.ofPattern("d MMM", french))
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

    /** Ce qui mérite un coup d'œil dès l'ouverture, du plus urgent au moins urgent. */
    private fun highlights(
        lessons: List<ApiLesson>,
        homework: List<ApiHomework>,
        grades: ApiGrades?,
        inbox: List<ApiMessage>,
    ): List<Highlight> {
        val today = today()
        val list = mutableListOf<Highlight>()
        lessons.filter { it.canceled }.forEach { lesson ->
            val day = LocalDate.parse(lesson.start.substring(0, 10))
            if (day == today || day == today.plusDays(1)) {
                val when_ = if (day == today) "aujourd'hui" else "demain"
                list += Highlight(
                    HighlightKind.Canceled,
                    "Cours annulé : ${lesson.subject.prettySubject()}",
                    "$when_ à ${lesson.start.substring(11, 16)}",
                )
            }
        }
        pendingHomework(homework).filter { it.exam && LocalDate.parse(it.dueDate) <= today.plusDays(3) }.forEach { task ->
            val day = LocalDate.parse(task.dueDate).format(DateTimeFormatter.ofPattern("EEEE d MMMM", french))
            list += Highlight(HighlightKind.Exam, "Évaluation en ${task.subject.prettySubject()}", day.capitalized())
        }
        grades?.grades
            ?.filter { it.counted && it.value != null && (it.date ?: "") >= today.minusDays(7).toString() }
            ?.take(2)
            ?.forEach { grade ->
                list += Highlight(
                    HighlightKind.Grade,
                    "Nouvelle note en ${grade.subject.prettySubject()}",
                    "${decimal(grade.value!!)} / ${grade.scale.toInt()}" + grade.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                )
            }
        val unread = inbox.count { !it.read }
        if (unread > 0) {
            list += Highlight(
                HighlightKind.Message,
                if (unread == 1) "1 message non lu" else "$unread messages non lus",
                inbox.firstOrNull { !it.read }?.let { "${it.sender} · ${it.subject}" }.orEmpty(),
            )
        }
        return list.take(5)
    }

    private fun afterLabel(lessons: List<ApiLesson>, current: ApiLesson): String? {
        val following = lessons.filter { !it.canceled && it.start >= current.end }.minByOrNull { it.start } ?: return null
        val sameDay = following.start.substring(0, 10) == current.start.substring(0, 10)
        val time = following.start.substring(11, 16)
        return "Ensuite : ${following.subject.prettySubject()} " + if (sameDay) "à $time" else "${dayName(following.start).lowercase(french)} à $time"
    }

    private fun ApiHomework.toHomework() = Homework(
        title = content.lineSequence().firstOrNull { it.isNotBlank() } ?: if (exam) "Évaluation prévue" else "Travail à faire",
        subject = subject.prettySubject(),
        due = LocalDate.parse(dueDate).format(DateTimeFormatter.ofPattern("EEEE d MMMM", french)).capitalized(),
        colorIndex = colorFor(subject),
        id = id,
        dueDate = dueDate,
        done = done,
        exam = exam,
        content = content,
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
    )

    private fun overview(payload: ApiGrades): GradeOverview {
        val periods = payload.periods
        val current = periods.firstOrNull { it.current && it.average != null }
            ?: periods.lastOrNull { it.average != null }
            ?: periods.lastOrNull()
        val previous = current?.let { c -> periods.takeWhile { it.code != c.code }.lastOrNull { it.average != null } }
        val delta = if (current?.average != null && previous?.average != null) current.average - previous.average else null

        val periodGrades = payload.grades.filter { current != null && it.periodCode == current.code }.map { it.toEntry() }
        val official = payload.subjectAverages.filter { current != null && it.periodCode == current.code }
        val names = (official.map { it.subject.prettySubject() } + periodGrades.map { it.subject }).distinct()
        val bySubject = names.mapNotNull { name ->
            val line = official.firstOrNull { it.subject.prettySubject() == name }
            val grades = periodGrades.filter { it.subject == name }
            // La moyenne de l'établissement prime ; à défaut, la moyenne pondérée des notes connues.
            val average = line?.average ?: GradeMath.weightedAverage(grades)
            if (average == null && grades.isEmpty()) return@mapNotNull null
            SubjectAverage(
                subject = Subject(name, line?.teacher.orEmpty(), "", colorFor(line?.subject ?: name)),
                average = average?.let(::decimal) ?: "–",
                classAverage = line?.classAverage?.let(::decimal),
                grades = grades,
            )
        }
        return GradeOverview(
            average = current?.average?.let(::decimal) ?: "–",
            trend = when {
                delta == null || abs(delta) < 0.05 -> ""
                delta > 0 -> "↗ +${decimal(delta)}"
                else -> "↘ −${decimal(-delta)}"
            },
            bySubject = bySubject,
            recent = payload.grades.filter { it.value != null }.take(6).map { it.toEntry() },
            evolution = periods.mapNotNull { p -> p.average?.let { PeriodAverage(p.name, it) } },
            classAverage = current?.classAverage?.let(::decimal),
        )
    }

    private fun decimal(value: Double): String = String.format(french, "%.1f", value)

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
            else -> "$time  ·  dans ${minutes / 60} h ${"%02d".format(minutes % 60)}"
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
    }
}
