package fr.ecole2recte.app.data

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
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.MessagePreview
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

    private suspend fun homeworkItems(): List<ApiHomework> =
        resource("homework", { api, target -> api.homework(target.studentId) }, Parsers::homeworkSummary)

    private suspend fun gradesPayload(): ApiGrades =
        resource("grades", { api, target -> api.grades(target.studentId) }, Parsers::grades)

    private suspend fun inbox(): List<ApiMessage> = resource(
        "messages",
        { api, target -> api.messages(target.mailboxSegment, target.mailboxId) },
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
        if (errors.size == 3) throw errors.first()

        val current = now()
        val todays = lessons.filter { it.start.startsWith(today().toString()) && !it.canceled }
        val upcoming = lessons.filter { !it.canceled && parse(it.end) > current }.minByOrNull { it.start }
        return HomeData(
            greeting = "Bonjour ${profile.firstName}",
            dateLabel = today().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", french)).capitalized(),
            nextClass = upcoming?.let { NextClass(it.toSubject(), startLabel(it, current), progress(it, current)) },
            today = todays.map { ScheduleSlot(it.start.substring(11, 16), it.toSubject()) },
            reminder = pendingHomework(homework).firstOrNull()?.toHomework(),
            grades = grades?.let(::overview) ?: GradeOverview("–", "", emptyList()),
            allSubjects = lessons.map { it.toSubject() }.distinctBy { it.name },
        )
    }

    override suspend fun schedule(): ScheduleWeek {
        val monday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val week = lessons().filter { lesson ->
            val day = LocalDate.parse(lesson.start.substring(0, 10))
            day in monday..sunday
        }
        return ScheduleWeek(
            label = "Semaine du ${monday.format(DateTimeFormatter.ofPattern("d MMMM", french))}",
            entries = week.map { lesson ->
                ScheduleEntry(
                    subject = lesson.toSubject(),
                    whenLabel = "${dayName(lesson.start)} · ${lesson.start.substring(11, 16)} — ${lesson.end.substring(11, 16)}",
                    canceled = lesson.canceled,
                )
            },
        )
    }

    override suspend fun homework(): List<Homework> = pendingHomework(homeworkItems()).map { it.toHomework() }

    override suspend fun grades(): GradeOverview = overview(gradesPayload())

    override suspend fun messages(): List<MessagePreview> =
        inbox().map { MessagePreview(sender = it.sender, preview = it.subject, unread = !it.read) }

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

    private fun ApiHomework.toHomework() = Homework(
        title = content.lineSequence().firstOrNull { it.isNotBlank() } ?: if (exam) "Évaluation prévue" else "Travail à faire",
        subject = subject,
        due = LocalDate.parse(dueDate).format(DateTimeFormatter.ofPattern("EEEE d MMMM", french)).capitalized(),
        colorIndex = colorFor(subject),
        id = id,
        dueDate = dueDate,
        done = done,
        exam = exam,
    )

    private fun ApiLesson.toSubject() = Subject(subject, teacher, room, colorFor(subject))

    private fun overview(payload: ApiGrades): GradeOverview {
        val periods = payload.periods
        val current = periods.firstOrNull { it.current && it.average != null }
            ?: periods.lastOrNull { it.average != null }
            ?: periods.lastOrNull()
        val previous = current?.let { c -> periods.takeWhile { it.code != c.code }.lastOrNull { it.average != null } }
        val delta = if (current?.average != null && previous?.average != null) current.average - previous.average else null
        val bySubject = payload.subjectAverages
            .filter { current != null && it.periodCode == current.code && it.average != null }
            .map { SubjectAverage(Subject(it.subject, it.teacher, "", colorFor(it.subject)), decimal(it.average!!)) }
        return GradeOverview(
            average = current?.average?.let(::decimal) ?: "–",
            trend = when {
                delta == null || abs(delta) < 0.05 -> ""
                delta > 0 -> "↗ +${decimal(delta)}"
                else -> "↘ −${decimal(-delta)}"
            },
            bySubject = bySubject,
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

    private companion object {
        const val PALETTE_SIZE = 4
    }
}
