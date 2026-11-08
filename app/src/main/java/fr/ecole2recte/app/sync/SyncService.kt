package fr.ecole2recte.app.sync

import fr.ecole2recte.app.data.Account
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.data.EcoleDirecteRepository
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.HomeworkStatusStore
import fr.ecole2recte.app.data.InMemoryTokenLog
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.TokenLifetimeLog
import fr.ecole2recte.app.data.TokenReplayPolicy
import fr.ecole2recte.app.data.USER_AGENT
import fr.ecole2recte.app.data.active
import fr.ecole2recte.app.data.cache.ResponseCache
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.session.ApiSessionState
import fr.ecole2recte.app.session.findBirthAnswer
import fr.ecole2recte.app.session.toActiveProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

/** Où partent les notifications. Abstrait pour les tests. */
interface NotificationSink {
    fun post(events: List<NotificationEvent>)
}

/** Où part l'instantané des widgets. Abstrait pour les tests. */
interface WidgetSink {
    suspend fun publish(snapshot: WidgetSnapshot)
    val wantsSchoolLife: Boolean get() = false
    suspend fun publishSchoolLife(owner: String, events: List<fr.ecole2recte.app.domain.SchoolLifeEvent>) {}
}

enum class SyncOutcome {
    /** Synchronisation désactivée par l'utilisateur. */
    Disabled,
    NoAccount,

    /** ÉcoleDirecte demande une vérification qu'on ne sait pas faire seul : la synchro s'arrête, l'utilisateur ouvrira l'app. */
    NeedsInteraction,

    /** Panne réseau ou serveur : on attend le prochain passage, sans réessayer. */
    Failed,

    /** Mot de passe refusé, compte bloqué, réponse refusée, version d'API refusée ou trop d'échecs : arrêtée. */
    Stopped,
    Done,
}

/** Nombre d'échecs de suite après lequel la synchronisation s'arrête et l'explique. */
const val MAX_CONSECUTIVE_FAILURES = 3

/**
 * Une synchronisation en arrière-plan : lit les données, met le cache à jour, prévient de ce qui
 * est nouveau et rafraîchit les widgets.
 *
 * Garde-fous : une session gardée est rejouée avant toute connexion ; au plus une connexion par
 * passage, jamais de nouvel essai ; la moindre réponse « identifiants refusés / compte bloqué /
 * réponse de sécurité refusée / version d'API refusée (517) », une vérification à faire à la main
 * ou trois échecs de suite arrêtent tout jusqu'à ce que l'utilisateur réactive la synchronisation.
 */
class SyncService(
    private val store: CredentialStore,
    private val cache: ResponseCache,
    private val homeworkStatus: HomeworkStatusStore,
    private val prefs: SyncPrefs,
    private val state: NotificationStateStore,
    private val notifier: NotificationSink,
    private val widgets: WidgetSink,
    private val clientFactory: () -> EcoleDirecteClient = { EcoleDirecteClient() },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tokenLog: TokenLifetimeLog = InMemoryTokenLog(),
    /** Pause entre deux enfants d'un compte famille : les lectures ne partent jamais en rafale. */
    private val pauseBetweenChildrenMs: Long = 2_000,
    /** Le journal des passages (heure, durée, résultat, nouveautés) ; aucune donnée personnelle. */
    private val journal: SyncJournal? = null,
) {
    /** Ce que le passage en cours a rencontré, pour le journal. Réinitialisé à chaque [run]. */
    private class Trace {
        var failure: SyncResultCode? = null
        var newItems = 0
    }

    private var trace = Trace()

    private sealed interface Opened {
        class Ready(val client: EcoleDirecteClient, val accounts: List<Account>) : Opened
        class Stop(val outcome: SyncOutcome) : Opened
    }

    suspend fun run(): SyncOutcome {
        val startedAt = clock.millis()
        val current = Trace().also { trace = it }
        val outcome = try {
            runPass()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            record(startedAt, SyncResultCode.Error, 0)
            throw error
        }
        val code = when (outcome) {
            SyncOutcome.Disabled, SyncOutcome.NoAccount -> null // rien ne s'est passé : rien à journaliser
            SyncOutcome.Done -> SyncResultCode.Done
            SyncOutcome.NeedsInteraction -> SyncResultCode.NeedsInteraction
            SyncOutcome.Failed, SyncOutcome.Stopped -> current.failure ?: SyncResultCode.Error
        }
        if (code != null) record(startedAt, code, if (code == SyncResultCode.Done) current.newItems else 0)
        return outcome
    }

    private fun record(startedAt: Long, code: SyncResultCode, newItems: Int) {
        val ended = clock.millis()
        // Le journal ne doit jamais faire échouer une synchronisation.
        runCatching { journal?.record(SyncJournalEntry(startedAt, (ended - startedAt).coerceAtLeast(0), code, newItems)) }
    }

    private suspend fun runPass(): SyncOutcome {
        if (!prefs.isEnabled) return SyncOutcome.Disabled
        val saved = store.active() ?: return SyncOutcome.NoAccount

        val session = ApiSession(now = clock::millis, tokenLog = tokenLog)
        // Si le jeton gardé est refusé, la seule reconnexion permise est celle de la session (une fois).
        var stoppedByReconnect: SyncOutcome? = null

        val replay = saved.session
            ?.takeIf { TokenReplayPolicy.canReplay(it, clock.millis(), tokenLog.load(), USER_AGENT) }
            ?.let { stored -> clientFactory().takeIf { it.restore(stored, tokenLog::recordReplay) }?.let { it to stored.accounts } }
        if (replay != null) {
            session.open(saved.identifier, replay.first, replay.second)
            session.reauthenticator = {
                when (val opened = openFresh(saved)) {
                    is Opened.Ready -> {
                        session.open(saved.identifier, opened.client, opened.accounts)
                        true
                    }
                    is Opened.Stop -> {
                        stoppedByReconnect = opened.outcome
                        false
                    }
                }
            }
        } else {
            when (val opened = openFresh(saved)) {
                is Opened.Stop -> return opened.outcome
                is Opened.Ready -> session.open(saved.identifier, opened.client, opened.accounts)
            }
        }

        val activeProfile = saved.toActiveProfile()
        val family = saved.profile?.students.orEmpty().isNotEmpty()
        val profiles = syncProfiles(saved, activeProfile)
        val today = LocalDate.now(clock)
        val rules = prefs.rules
        val hour = java.time.LocalTime.now(clock).hour
        val quiet = rules.isQuiet(hour)
        val toPost = mutableListOf<NotificationEvent>()
        var widgetSnapshot: WidgetSnapshot? = null
        val widgetProfiles = mutableListOf<WidgetSnapshot>()
        var widgetInbox = emptyList<fr.ecole2recte.app.domain.MessagePreview>()

        // Un passage par enfant du compte, l'un après l'autre et avec une pause : jamais en rafale.
        // Le premier est l'enfant affiché (les widgets le montrent) ; la boîte de réception est celle
        // de la famille, lue une seule fois et suivie à part pour ne pas notifier chaque enfant.
        try {
            for ((index, profile) in profiles.withIndex()) {
                if (index > 0 && pauseBetweenChildrenMs > 0) delay(pauseBetweenChildrenMs)
                val repository = EcoleDirecteRepository(session, profile, cache, homeworkStatus, clock = clock)
                val main = index == 0
                val nextClass = if (main) repository.nextClass() else null
                val week = repository.schedule()
                val homework = repository.homework()
                val grades = repository.grades()
                val inbox = if (main) repository.messagesPage(0) else null
                if (inbox != null) widgetInbox = inbox.items
                stoppedByReconnect?.let { return it }
                if (main && widgets.wantsSchoolLife && fr.ecole2recte.app.data.SchoolModule.SchoolLife !in profile.closedModules) {
                    val life = repository.schoolLife()
                    stoppedByReconnect?.let { return it }
                    widgets.publishSchoolLife(profile.key, life.events)
                }
                // Une reconnexion refusée pendant la lecture (identifiants, blocage…) arrête tout, même si
                // le cache a permis de répondre.
                stoppedByReconnect?.let { return it }

                val label = profile.firstName.takeIf { family }.orEmpty()
                val ownerKey = "${saved.identifier.lowercase()}|${profile.studentId.orEmpty()}"
                val ownMessages = if (family) emptyList() else inbox?.items.orEmpty()
                plan(ownerKey, NotificationPlanner.snapshot(grades, ownMessages, week, homework, today), today, rules, hour, label, quiet, toPost)
                if (main && family) {
                    val mailbox = NotificationPlanner.snapshot(GradeOverview("–", "", emptyList()), inbox?.items.orEmpty(), ScheduleWeek("", emptyList(), "", ""), emptyList(), today)
                    plan("${saved.identifier.lowercase()}|mailbox", mailbox, today, rules, hour, "", quiet, toPost)
                }
                val periods = repository.cachedGradePeriods()
                val published = fr.ecole2recte.app.widget.WidgetLogic.snapshot(
                    repository.freshness.value.lastSyncMillis ?: clock.millis(), today, nextClass, week, homework, grades,
                    widgetInbox,
                ).copy(profileKey = profile.key.lowercase(), profileLabel = profile.displayName,
                    owner = profile.key, shortcutSubjects = grades.bySubject.map { it.id },
                    meanPeriods = periods.takeIf { it.isNotEmpty() }?.map { fr.ecole2recte.app.widget.WidgetMeanPeriod.fromOverview(it, it.periodCode == grades.periodCode) }
                        ?: listOf(fr.ecole2recte.app.widget.WidgetMeanPeriod.fromOverview(grades)))
                if (main) widgetSnapshot = published else widgetProfiles += published
            }
            trace.newItems = if (quiet) 0 else toPost.size
            if (toPost.isNotEmpty() && !quiet) notifier.post(toPost)
            widgetSnapshot?.let { widgets.publish(it.copy(profiles = widgetProfiles)) }
            keepSession(saved, session)
            prefs.recordSync(clock.millis())
            prefs.resetFailures()
            return SyncOutcome.Done
        } catch (error: EcoleDirecteException) {
            stoppedByReconnect?.let { return it }
            if (error.kind == FailureKind.StaleVersion) return stop(stopReason(FailureKind.StaleVersion), FailureKind.StaleVersion)
            return failed(error.kind)
        }
    }

    /** L'enfant affiché d'abord, puis les autres ; un seul profil pour un compte élève. */
    private fun syncProfiles(saved: SavedLogin, active: ActiveProfile): List<ActiveProfile> {
        val students = saved.profile?.students.orEmpty()
        if (students.isEmpty()) return listOf(active)
        return students.map { saved.copy(activeStudentId = it.id).toActiveProfile() }
            .sortedBy { if (it.studentId == active.studentId) 0 else 1 }
    }

    /** Décide quoi notifier pour un propriétaire (enfant ou boîte de réception) et retient ce qui l'a été. */
    private fun plan(
        owner: String,
        snapshot: SyncSnapshot,
        today: LocalDate,
        rules: NotificationRules,
        hour: Int,
        label: String,
        quiet: Boolean,
        out: MutableList<NotificationEvent>,
    ) {
        val plan = NotificationPlanner.plan(state.load(owner), snapshot, today, rules, hour)
        // De nuit : on ne dérange pas et on ne retient rien, les nouveautés partiront au matin.
        if (plan.events.isNotEmpty() && quiet) return
        out += plan.events.map { it.copy(owner = label) }
        state.save(owner, plan.next)
    }

    /** Une vraie connexion (avec la réponse de confiance ou la date de naissance mémorisées). */
    private suspend fun openFresh(saved: SavedLogin): Opened {
        val client = clientFactory()
        var result = attempt { client.login(saved.identifier, saved.password, saved.trustedAnswer) }
        if (result is LoginResult.SecurityQuestionRequired) {
            val birth = saved.birthDate
            val answer = birth?.let { findBirthAnswer(result.question, result.choices, it.day, it.month, it.year) }
                ?: return Opened.Stop(needsInteraction())
            result = attempt { client.completeSecurityQuestion(answer, saved.identifier, saved.password) }
        }
        val accounts = when (result) {
            is LoginResult.Success -> result.accounts
            is LoginResult.SecurityQuestionRequired -> return Opened.Stop(needsInteraction())
            is LoginResult.Failure -> return Opened.Stop(
                when (result.kind) {
                    FailureKind.InvalidCredentials, FailureKind.Blocked, FailureKind.SecurityRefused, FailureKind.StaleVersion ->
                        stop(stopReason(result.kind), result.kind)
                    else -> failed(result.kind)
                },
            )
        }
        // Une réponse de confiance renouvelée est gardée, sans toucher au reste du compte.
        client.approvedAnswer?.takeIf { it != saved.trustedAnswer }?.let { answer ->
            store.update(saved.identifier) { it.copy(trustedAnswer = answer) }
        }
        return Opened.Ready(client, accounts)
    }

    /** Garde le jeton renouvelé pour le prochain passage, sans toucher au reste du compte. */
    private fun keepSession(saved: SavedLogin, session: ApiSession) {
        val ready = session.state.value as? ApiSessionState.Ready ?: return
        val snapshot = ready.client.snapshot(ready.accounts) ?: return
        store.update(saved.identifier) { it.copy(session = snapshot.newest(it.session)) }
    }

    private fun stop(reason: String, kind: FailureKind?): SyncOutcome {
        trace.failure = kind?.let(SyncResultCode::fromFailure) ?: SyncResultCode.TooManyFailures
        prefs.disable(reason)
        return SyncOutcome.Stopped
    }

    private fun needsInteraction(): SyncOutcome {
        prefs.disable(
            "Une vérification est requise : reconnecte-toi, puis réactive.",
        )
        return SyncOutcome.NeedsInteraction
    }

    /** Un échec : on attend le prochain passage, mais pas indéfiniment. */
    private fun failed(kind: FailureKind): SyncOutcome {
        trace.failure = SyncResultCode.fromFailure(kind)
        if (prefs.recordFailure() < MAX_CONSECUTIVE_FAILURES) return SyncOutcome.Failed
        return stop(
            "Échec $MAX_CONSECUTIVE_FAILURES fois de suite : arrêt. Vérifie ta connexion, puis réactive.",
            null,
        )
    }

    private suspend fun attempt(block: () -> LoginResult): LoginResult = try {
        withContext(ioDispatcher) { block() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: EcoleDirecteException) {
        LoginResult.Failure(error.message ?: "Connexion impossible.", kind = error.kind)
    } catch (error: IOException) {
        LoginResult.Failure("Pas de connexion.", kind = FailureKind.Network)
    }

    private fun stopReason(kind: FailureKind) = when (kind) {
        FailureKind.InvalidCredentials -> "Le mot de passe est refusé : reconnecte-toi, puis réactive."
        FailureKind.Blocked -> "Compte verrouillé : aucune connexion automatique."
        FailureKind.StaleVersion -> "Version d'API refusée (517) : mise à jour ou Plus > Diagnostic, puis réactive."
        else -> "Vérification refusée : reconnecte-toi."
    }
}
