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
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.session.ApiSessionState
import fr.ecole2recte.app.session.findBirthAnswer
import fr.ecole2recte.app.session.toActiveProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

        val profile = saved.toActiveProfile()
        val repository = EcoleDirecteRepository(session, profile, cache, homeworkStatus, clock = clock)
        try {
            val home = repository.home()
            val week = repository.schedule()
            val homework = repository.homework()
            val inbox = repository.messagesPage(0)
            // Une reconnexion refusée pendant la lecture (identifiants, blocage…) arrête tout, même si
            // le cache a permis de répondre.
            stoppedByReconnect?.let { return it }

            val today = LocalDate.now(clock)
            val owner = "${saved.identifier.lowercase()}|${profile.studentId.orEmpty()}"
            val snapshot = NotificationPlanner.snapshot(home.grades, inbox.items, week, homework, today)
            val rules = prefs.rules
            val plan = NotificationPlanner.plan(state.load(owner), snapshot, today, rules)
            val hour = java.time.LocalTime.now(clock).hour
            if (plan.events.isNotEmpty() && rules.isQuiet(hour)) {
                // De nuit : on ne dérange pas et on ne retient rien, les nouveautés partiront au matin.
            } else {
                if (plan.events.isNotEmpty()) notifier.post(plan.events)
                state.save(owner, plan.next)
            }

            widgets.publish(
                fr.ecole2recte.app.widget.WidgetLogic.snapshot(clock.millis(), today, home, week, homework, home.grades, inbox.items),
            )
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
