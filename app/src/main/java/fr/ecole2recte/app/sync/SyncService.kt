package fr.ecole2recte.app.sync

import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.data.EcoleDirecteRepository
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.HomeworkStatusStore
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.active
import fr.ecole2recte.app.data.cache.ResponseCache
import fr.ecole2recte.app.session.ApiSession
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

    /** ÉcoleDirecte demande une vérification qu'on ne sait pas faire seul : l'utilisateur ouvrira l'app. */
    NeedsInteraction,

    /** Panne réseau ou serveur : on attend le prochain passage, sans réessayer. */
    Failed,

    /** Mot de passe refusé, compte bloqué ou réponse refusée : la synchronisation est arrêtée. */
    Stopped,
    Done,
}

/**
 * Une synchronisation en arrière-plan : se connecte une fois, lit les données, met le cache à
 * jour, prévient de ce qui est nouveau et rafraîchit les widgets.
 *
 * Garde-fous : une seule connexion par passage, jamais de nouvel essai, et la moindre réponse
 * « identifiants refusés / compte bloqué / réponse de sécurité refusée » arrête tout jusqu'à ce
 * que l'utilisateur réactive la synchronisation lui-même.
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
) {
    suspend fun run(): SyncOutcome {
        if (!prefs.isEnabled) return SyncOutcome.Disabled
        val saved = store.active() ?: return SyncOutcome.NoAccount

        val client = clientFactory()
        var result = attempt { client.login(saved.identifier, saved.password, saved.trustedAnswer) }
        if (result is LoginResult.SecurityQuestionRequired) {
            val birth = saved.birthDate
            val answer = birth?.let { findBirthAnswer(result.question, result.choices, it.day, it.month, it.year) }
                ?: return SyncOutcome.NeedsInteraction
            result = attempt { client.completeSecurityQuestion(answer, saved.identifier, saved.password) }
        }
        val accounts = when (result) {
            is LoginResult.Success -> result.accounts
            is LoginResult.SecurityQuestionRequired -> return SyncOutcome.NeedsInteraction
            is LoginResult.Failure -> return when (result.kind) {
                FailureKind.InvalidCredentials, FailureKind.Blocked, FailureKind.SecurityRefused -> {
                    prefs.disable(stopReason(result.kind))
                    SyncOutcome.Stopped
                }
                else -> SyncOutcome.Failed
            }
        }
        // Une réponse de confiance renouvelée est gardée, sans changer le compte actif.
        client.approvedAnswer?.takeIf { it != saved.trustedAnswer }?.let {
            store.save(saved.copy(trustedAnswer = it), makeActive = false)
        }

        val session = ApiSession()
        session.open(saved.identifier, client, accounts)
        val profile = saved.toActiveProfile()
        val repository = EcoleDirecteRepository(session, profile, cache, homeworkStatus, clock = clock)
        try {
            val home = repository.home()
            val week = repository.schedule()
            val homework = repository.homework()
            val inbox = repository.messagesPage(0)

            val today = LocalDate.now(clock)
            val owner = "${saved.identifier.lowercase()}|${profile.studentId.orEmpty()}"
            val snapshot = NotificationPlanner.snapshot(home.grades, inbox.items, week, homework, today)
            val plan = NotificationPlanner.plan(state.load(owner), snapshot, today)
            if (plan.events.isNotEmpty()) notifier.post(plan.events)
            state.save(owner, plan.next)

            widgets.publish(
                WidgetSnapshot(
                    updatedAt = clock.millis(),
                    nextClass = home.nextClass?.let {
                        WidgetLesson(it.subject.name, it.startLabel, it.subject.room.takeIf { room -> room.isNotBlank() }?.let { room -> "Salle $room" }.orEmpty())
                    },
                    tasks = homework.filter { !it.done }.take(5).map { WidgetTask(it.subject, it.title, it.due) },
                ),
            )
            prefs.recordSync(clock.millis())
            return SyncOutcome.Done
        } catch (error: EcoleDirecteException) {
            return SyncOutcome.Failed
        }
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
        else -> "La vérification de sécurité a été refusée. Ouvre l'application pour te reconnecter."
    }
}
