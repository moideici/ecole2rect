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
) {
    private sealed interface Opened {
        class Ready(val client: EcoleDirecteClient, val accounts: List<Account>) : Opened
        class Stop(val outcome: SyncOutcome) : Opened
    }

    suspend fun run(): SyncOutcome {
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
        val quiet = rules.isQuiet(java.time.LocalTime.now(clock).hour)
        val toPost = mutableListOf<NotificationEvent>()
        var widgetSnapshot: WidgetSnapshot? = null

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
                // Une reconnexion refusée pendant la lecture (identifiants, blocage…) arrête tout, même si
                // le cache a permis de répondre.
                stoppedByReconnect?.let { return it }

                val label = profile.firstName.takeIf { family }.orEmpty()
                val ownerKey = "${saved.identifier.lowercase()}|${profile.studentId.orEmpty()}"
                val ownMessages = if (family) emptyList() else inbox?.items.orEmpty()
                plan(ownerKey, NotificationPlanner.snapshot(grades, ownMessages, week, homework, today), today, rules, label, quiet, toPost)
                if (main && family) {
                    val mailbox = NotificationPlanner.snapshot(GradeOverview("–", "", emptyList()), inbox?.items.orEmpty(), ScheduleWeek("", emptyList(), "", ""), emptyList(), today)
                    plan("${saved.identifier.lowercase()}|mailbox", mailbox, today, rules, "", quiet, toPost)
                }
                if (main && inbox != null) {
                    widgetSnapshot = fr.ecole2recte.app.widget.WidgetLogic.snapshot(
                        clock.millis(), today, nextClass, week, homework, grades, inbox.items,
                    )
                }
            }
            if (toPost.isNotEmpty() && !quiet) notifier.post(toPost)
            widgetSnapshot?.let { widgets.publish(it) }
            keepSession(saved, session)
            prefs.recordSync(clock.millis())
            prefs.resetFailures()
            return SyncOutcome.Done
        } catch (error: EcoleDirecteException) {
            stoppedByReconnect?.let { return it }
            if (error.kind == FailureKind.StaleVersion) return stop(stopReason(FailureKind.StaleVersion))
            return failed()
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
        label: String,
        quiet: Boolean,
        out: MutableList<NotificationEvent>,
    ) {
        val plan = NotificationPlanner.plan(state.load(owner), snapshot, today, rules)
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
                        stop(stopReason(result.kind))
                    else -> failed()
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

    private fun stop(reason: String): SyncOutcome {
        prefs.disable(reason)
        return SyncOutcome.Stopped
    }

    private fun needsInteraction(): SyncOutcome {
        prefs.disable(
            "ÉcoleDirecte demande une vérification que l'application ne peut pas faire seule. " +
                "Ouvre l'application, reconnecte-toi, puis réactive les notifications.",
        )
        return SyncOutcome.NeedsInteraction
    }

    /** Un échec : on attend le prochain passage, mais pas indéfiniment. */
    private fun failed(): SyncOutcome {
        if (prefs.recordFailure() < MAX_CONSECUTIVE_FAILURES) return SyncOutcome.Failed
        return stop(
            "La synchronisation a échoué $MAX_CONSECUTIVE_FAILURES fois de suite : elle s'est arrêtée pour ne pas " +
                "multiplier les connexions. Ouvre l'application pour vérifier ta connexion, puis réactive-la.",
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
        FailureKind.InvalidCredentials -> "Le mot de passe n'est plus accepté. Ouvre l'application pour te reconnecter, puis réactive les notifications."
        FailureKind.Blocked -> "ÉcoleDirecte a bloqué le compte. Aucune connexion automatique n'est tentée."
        FailureKind.StaleVersion -> "ÉcoleDirecte n'accepte plus la version d'API utilisée (517). Cherche une mise à jour de l'application, ou change la version dans Plus > Diagnostic, puis réactive les notifications."
        else -> "La vérification de sécurité a été refusée. Ouvre l'application pour te reconnecter."
    }
}
