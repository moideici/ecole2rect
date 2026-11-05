package fr.ecole2recte.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ecole2recte.app.data.Account
import fr.ecole2recte.app.data.AccountProfile
import fr.ecole2recte.app.data.AuthClient
import fr.ecole2recte.app.data.BirthDate
import fr.ecole2recte.app.data.CredentialStore
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.EcoleDirecteException
import fr.ecole2recte.app.data.InMemoryTokenLog
import fr.ecole2recte.app.data.TokenLifetimeLog
import fr.ecole2recte.app.data.TokenReplayPolicy
import fr.ecole2recte.app.data.USER_AGENT
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.active
import fr.ecole2recte.app.data.find
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class AuthStatus { SignedOut, SignedIn }

/** État du lien avec ÉcoleDirecte quand l'utilisateur est connecté. */
enum class ConnectionStatus { Connecting, Connected, Offline }

data class SecurityChallenge(val question: String, val choices: List<String>)

data class LoginError(val message: String, val kind: FailureKind)

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val challenge: SecurityChallenge? = null,
    val error: LoginError? = null,
    val diagnostic: String? = null,
    val loading: Boolean = false,
)

data class SessionState(
    val status: AuthStatus,
    val connection: ConnectionStatus,
    val connectionMessage: String? = null,
    val login: LoginUiState = LoginUiState(),
    /** Vrai pendant l'ajout d'un deuxième compte (l'écran de connexion passe devant l'app). */
    val adding: Boolean = false,
    val accounts: List<AccountSummary> = emptyList(),
    val active: ActiveProfile? = null,
)

/**
 * Détient la session et les comptes enregistrés.
 *
 * - Au lancement, si un compte est enregistré, l'app s'ouvre **tout de suite** et la
 *   reconnexion se fait en arrière-plan : pas d'écran d'attente, pas de saisie.
 * - Plusieurs comptes peuvent coexister ; un seul est actif à la fois, et changer de compte
 *   repart d'un état vierge (aucune donnée ne passe d'un compte à l'autre).
 * - L'identifiant et le mot de passe ne sont demandés qu'une fois par compte. La date de
 *   naissance n'est demandée que si ÉcoleDirecte pose une question de sécurité, et elle est
 *   mémorisée pour y répondre seule si la question revient.
 * - Garde-fous : un identifiant refusé n'est jamais renvoyé tel quel, un compte bloqué n'est
 *   jamais réessayé, une réponse de sécurité n'est tentée qu'une fois, et seules les pannes
 *   réseau ou serveur sont réessayées automatiquement, avec un délai croissant.
 */
class SessionViewModel(
    private val store: CredentialStore,
    private val clientFactory: () -> AuthClient = { EcoleDirecteClient() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retryDelaysMs: List<Long> = listOf(5_000L, 20_000L),
    private val apiSession: ApiSession? = null,
    /** Appelé quand un compte est retiré : efface ses données enregistrées. */
    private val onAccountRemoved: (identifier: String) -> Unit = {},
    /** Mesures de la durée de vie du jeton : décident si une session gardée est rejouée. */
    private val tokenLog: TokenLifetimeLog = InMemoryTokenLog(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private class Pending(
        val client: AuthClient,
        val identifier: String,
        val password: String,
        val saved: SavedLogin?,
    )

    private class Outcome(val client: AuthClient, val result: LoginResult)

    private var pending: Pending? = null
    private var rejected: Pair<String, String>? = null
    private var restoreJob: Job? = null

    private val mutableState = MutableStateFlow(
        build(
            status = if (store.active() != null) AuthStatus.SignedIn else AuthStatus.SignedOut,
            connection = if (store.active() != null) ConnectionStatus.Connecting else ConnectionStatus.Connected,
            login = if (store.isUnreadable()) LoginUiState(error = LoginError(UNREADABLE_MESSAGE, FailureKind.Other)) else LoginUiState(),
        ),
    )
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    init {
        apiSession?.reauthenticator = ::reauthenticate
        store.active()?.let(::restore)
    }

    private fun build(
        status: AuthStatus,
        connection: ConnectionStatus,
        message: String? = null,
        login: LoginUiState = LoginUiState(),
        adding: Boolean = false,
    ): SessionState {
        val active = store.active()
        return SessionState(
            status = status,
            connection = connection,
            connectionMessage = message,
            login = login,
            adding = adding,
            accounts = store.readAll().map { it.toSummary(isActive = it.identifier == active?.identifier) },
            active = active?.toActiveProfile(),
        )
    }

    // --- Reconnexion silencieuse -------------------------------------------------------------

    private fun restore(saved: SavedLogin, allowReplay: Boolean = true) {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            // D'abord, la session gardée : si le jeton est encore plausible, aucune connexion du tout.
            // Si ÉcoleDirecte le refuse, le premier appel de données rouvre la session (une fois).
            if (allowReplay && replay(saved)) {
                mutableState.update { build(AuthStatus.SignedIn, ConnectionStatus.Connected, adding = it.adding, login = it.login) }
                return@launch
            }
            var attempt = 0
            while (true) {
                setConnection(ConnectionStatus.Connecting)
                apiSession?.opening(saved.identifier)
                val outcome = authenticate(saved.identifier, saved.password, saved)
                when (val result = outcome.result) {
                    is LoginResult.Success -> {
                        persist(outcome.client, saved.identifier, saved.password, saved, null, result.accounts, false)
                        openApi(saved.identifier, outcome.client, result.accounts)
                        mutableState.update {
                            build(AuthStatus.SignedIn, ConnectionStatus.Connected, adding = it.adding, login = it.login)
                        }
                        return@launch
                    }
                    is LoginResult.SecurityQuestionRequired -> {
                        pending = Pending(outcome.client, saved.identifier, saved.password, saved)
                        signOut(
                            LoginUiState(
                                identifier = saved.identifier,
                                password = saved.password,
                                challenge = result.toChallenge(),
                            ),
                        )
                        return@launch
                    }
                    is LoginResult.Failure -> when (result.kind) {
                        FailureKind.Network, FailureKind.Service -> {
                            setConnection(ConnectionStatus.Offline, result.message)
                            apiSession?.fail(saved.identifier, result.message, result.kind)
                            if (attempt >= retryDelaysMs.size) return@launch
                            delay(retryDelaysMs[attempt++])
                        }
                        FailureKind.InvalidCredentials, FailureKind.Blocked -> {
                            rejected = saved.identifier to saved.password
                            signOut(
                                LoginUiState(
                                    identifier = saved.identifier,
                                    error = LoginError(
                                        if (result.kind == FailureKind.Blocked) result.message
                                        else "ÉcoleDirecte n'accepte plus ce mot de passe. Saisis le nouveau.",
                                        result.kind,
                                    ),
                                    diagnostic = result.diagnostic,
                                ),
                            )
                            return@launch
                        }
                        FailureKind.SecurityRefused -> {
                            signOut(
                                LoginUiState(
                                    identifier = saved.identifier,
                                    password = saved.password,
                                    error = LoginError(result.message, result.kind),
                                    diagnostic = result.diagnostic,
                                ),
                            )
                            return@launch
                        }
                        FailureKind.Other, FailureKind.StaleVersion -> {
                            setConnection(ConnectionStatus.Offline, result.message)
                            apiSession?.fail(saved.identifier, result.message, result.kind)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun replay(saved: SavedLogin): Boolean {
        val stored = saved.session ?: return false
        if (!TokenReplayPolicy.canReplay(stored, now(), tokenLog.load(), USER_AGENT)) return false
        val client = clientFactory()
        if (!client.restore(stored, onConfirmed = tokenLog::recordReplay)) return false
        openApi(saved.identifier, client, stored.accounts)
        return true
    }

    /**
     * Garde la session en cours (jeton renouvelé, cookies) pour la rejouer au prochain lancement.
     * Appelé quand l'application quitte le premier plan.
     */
    fun saveSession() {
        val ready = apiSession?.state?.value as? ApiSessionState.Ready ?: return
        val snapshot = ready.client.snapshot(ready.accounts) ?: return
        store.update(ready.identifier) { it.copy(session = snapshot.newest(it.session)) }
    }

    /** Relance à la main la reconnexion quand l'app est hors ligne : une vraie connexion, jamais un rejeu. */
    fun retryConnection() {
        val current = mutableState.value
        if (current.status != AuthStatus.SignedIn || current.connection == ConnectionStatus.Connecting) return
        store.active()?.let { restore(it, allowReplay = false) }
    }

    // --- Comptes -----------------------------------------------------------------------------

    /** Passe à un autre compte enregistré : repart d'un état vierge et se reconnecte en arrière-plan. */
    fun switchAccount(identifier: String) {
        val target = store.find(identifier) ?: return
        if (target.identifier == store.active()?.identifier && !mutableState.value.adding) return
        activate(target)
    }

    private fun activate(target: SavedLogin) {
        pending = null
        rejected = null
        store.setActive(target.identifier)
        mutableState.value = build(AuthStatus.SignedIn, ConnectionStatus.Connecting)
        restore(target)
    }

    /** Affiche l'écran de connexion devant l'app pour ajouter un compte. */
    fun startAddAccount() {
        pending = null
        mutableState.update { it.copy(adding = true, login = LoginUiState()) }
    }

    fun cancelAddAccount() {
        pending = null
        mutableState.update { it.copy(adding = false, login = LoginUiState()) }
    }

    /** Retire un compte de l'appareil. Si c'était le compte actif, bascule sur un autre ou se déconnecte. */
    fun removeAccount(identifier: String) {
        val wasActive = store.active()?.identifier.equals(identifier, ignoreCase = true)
        store.remove(identifier)
        onAccountRemoved(identifier)
        val next = store.active()
        when {
            next == null -> {
                restoreJob?.cancel()
                pending = null
                signOut(LoginUiState())
            }
            wasActive -> activate(next)
            else -> mutableState.update { it.copy(accounts = build(it.status, it.connection).accounts) }
        }
    }

    /** Choisit l'élève affiché d'un compte famille. */
    fun selectStudent(studentId: String) {
        val login = store.active() ?: return
        if (login.profile?.students?.none { it.id == studentId } != false) return
        store.save(login.copy(activeStudentId = studentId))
        mutableState.update {
            val rebuilt = build(it.status, it.connection, it.connectionMessage, it.login, it.adding)
            rebuilt
        }
    }

    // --- Connexion manuelle ------------------------------------------------------------------

    fun login(identifier: String, password: String) {
        val id = identifier.trim()
        if (mutableState.value.login.loading || id.isBlank() || password.isBlank()) return
        if (rejected == id to password) {
            updateLogin {
                it.copy(
                    error = LoginError(
                        "Ces identifiants ont déjà été refusés. Modifie-les avant de réessayer.",
                        FailureKind.InvalidCredentials,
                    ),
                )
            }
            return
        }
        updateLogin { it.copy(loading = true, error = null, diagnostic = null) }
        viewModelScope.launch {
            val existing = store.find(id)
            val outcome = authenticate(id, password, existing)
            when (val result = outcome.result) {
                is LoginResult.Success -> {
                    persist(outcome.client, id, password, existing, null, result.accounts, true)
                    openApi(id, outcome.client, result.accounts)
                    signIn()
                }
                is LoginResult.SecurityQuestionRequired -> {
                    pending = Pending(outcome.client, id, password, existing)
                    updateLogin {
                        it.copy(loading = false, challenge = result.toChallenge(), error = null, diagnostic = null)
                    }
                }
                is LoginResult.Failure -> {
                    if (result.kind == FailureKind.InvalidCredentials || result.kind == FailureKind.Blocked) {
                        rejected = id to password
                    }
                    updateLogin {
                        it.copy(
                            loading = false,
                            error = LoginError(result.message, result.kind),
                            diagnostic = result.diagnostic,
                        )
                    }
                }
            }
        }
    }

    /**
     * Répond à la question de sécurité. Une seule tentative par question : si ÉcoleDirecte la
     * refuse, l'utilisateur doit se reconnecter pour en obtenir une nouvelle.
     * [birthDate] est mémorisée (chiffrée) pour répondre seule la prochaine fois.
     */
    fun answerSecurity(answer: String, birthDate: BirthDate? = null) {
        val current = pending ?: return
        if (mutableState.value.login.loading || answer.isBlank()) return
        updateLogin { it.copy(loading = true, error = null, diagnostic = null) }
        viewModelScope.launch {
            val result = safely {
                withContext(ioDispatcher) {
                    current.client.completeSecurityQuestion(answer, current.identifier, current.password)
                }
            }
            when (result) {
                is LoginResult.Success -> {
                    pending = null
                    persist(current.client, current.identifier, current.password, current.saved, birthDate, result.accounts, true)
                    openApi(current.identifier, current.client, result.accounts)
                    signIn()
                }
                is LoginResult.SecurityQuestionRequired -> {
                    pending = null
                    updateLogin {
                        it.copy(
                            loading = false,
                            challenge = null,
                            error = LoginError(
                                "ÉcoleDirecte a redemandé une vérification. Reconnecte-toi pour obtenir une nouvelle question.",
                                FailureKind.SecurityRefused,
                            ),
                        )
                    }
                }
                is LoginResult.Failure -> {
                    val retryable = result.kind == FailureKind.Network || result.kind == FailureKind.Service
                    if (!retryable) pending = null
                    updateLogin {
                        it.copy(
                            loading = false,
                            challenge = if (retryable) it.challenge else null,
                            error = LoginError(
                                if (retryable) result.message
                                else "${result.message} Reconnecte-toi pour obtenir une nouvelle question.",
                                result.kind,
                            ),
                            diagnostic = result.diagnostic,
                        )
                    }
                }
            }
        }
    }

    /** Revient à la saisie de l'identifiant sans répondre à la question. */
    fun cancelSecurity() {
        pending = null
        updateLogin { it.copy(challenge = null, error = null, diagnostic = null, loading = false) }
    }

    // --- Mécanique interne -------------------------------------------------------------------

    private suspend fun authenticate(identifier: String, password: String, saved: SavedLogin?): Outcome {
        val client = clientFactory()
        val sameAccount = saved?.takeIf { it.identifier.equals(identifier, ignoreCase = true) }
        var result = safely {
            withContext(ioDispatcher) { client.login(identifier, password, sameAccount?.trustedAnswer) }
        }
        val question = result as? LoginResult.SecurityQuestionRequired
        val birth = sameAccount?.birthDate
        if (question != null && birth != null) {
            val automatic = findBirthAnswer(question.question, question.choices, birth.day, birth.month, birth.year)
            if (automatic != null) {
                result = safely {
                    withContext(ioDispatcher) { client.completeSecurityQuestion(automatic, identifier, password) }
                }
            }
        }
        return Outcome(client, result)
    }

    /** Enregistre le compte sans jamais perdre la réponse de confiance, la date de naissance ni l'élève choisi. */
    private fun persist(
        client: AuthClient,
        identifier: String,
        password: String,
        previous: SavedLogin?,
        birthDate: BirthDate?,
        accounts: List<Account>,
        makeActive: Boolean,
    ) {
        val sameAccount = previous?.takeIf { it.identifier.equals(identifier, ignoreCase = true) }
        val account = accounts.firstOrNull()
        val profile = account?.let { AccountProfile(it.firstName, it.lastName, it.type, it.students) }
            ?: sameAccount?.profile
        val activeStudent = sameAccount?.activeStudentId?.takeIf { id -> profile?.students?.any { it.id == id } == true }
        store.save(
            SavedLogin(
                identifier = identifier,
                password = password,
                trustedAnswer = client.approvedAnswer ?: sameAccount?.trustedAnswer,
                birthDate = birthDate ?: sameAccount?.birthDate,
                profile = profile,
                activeStudentId = activeStudent,
                session = client.snapshot(accounts),
            ),
            makeActive = makeActive,
        )
    }

    private fun signIn() {
        restoreJob?.cancel()
        rejected = null
        mutableState.value = build(AuthStatus.SignedIn, ConnectionStatus.Connected)
    }

    private fun openApi(identifier: String, client: AuthClient, accounts: List<Account>) {
        (client as? EcoleDirecteClient)?.let { apiSession?.open(identifier, it, accounts) }
    }

    /**
     * Rouvre la session quand un appel de données a trouvé le jeton expiré. Un seul essai,
     * sans toucher à l'écran : si ça échoue, l'appel remonte son erreur et c'est tout.
     */
    private suspend fun reauthenticate(): Boolean {
        val saved = store.active() ?: return false
        val outcome = authenticate(saved.identifier, saved.password, saved)
        val result = outcome.result as? LoginResult.Success ?: return false
        persist(outcome.client, saved.identifier, saved.password, saved, null, result.accounts, false)
        openApi(saved.identifier, outcome.client, result.accounts)
        return true
    }

    private fun signOut(login: LoginUiState) {
        apiSession?.close()
        mutableState.value = build(AuthStatus.SignedOut, ConnectionStatus.Connected, login = login)
    }

    private fun setConnection(status: ConnectionStatus, message: String? = null) {
        mutableState.update { it.copy(connection = status, connectionMessage = message) }
    }

    private fun updateLogin(transform: (LoginUiState) -> LoginUiState) {
        mutableState.update { it.copy(login = transform(it.login)) }
    }

    private fun LoginResult.SecurityQuestionRequired.toChallenge() =
        SecurityChallenge(question ?: DEFAULT_QUESTION, choices)

    private suspend fun safely(block: suspend () -> LoginResult): LoginResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: EcoleDirecteException) {
        LoginResult.Failure(error.message ?: NETWORK_MESSAGE, kind = error.kind)
    } catch (error: IOException) {
        LoginResult.Failure(NETWORK_MESSAGE, kind = FailureKind.Network)
    } catch (error: Exception) {
        LoginResult.Failure("Connexion impossible pour le moment.", kind = FailureKind.Other)
    }

    private companion object {
        const val UNREADABLE_MESSAGE =
            "Les comptes enregistrés sur ce téléphone sont illisibles (la clé de sécurité de l'appareil a changé). " +
                "Reconnecte-toi : rien n'a été effacé tant que tu ne l'as pas fait."
        const val DEFAULT_QUESTION = "Réponds à la question demandée par ÉcoleDirecte."
        const val NETWORK_MESSAGE = "Pas de connexion à ÉcoleDirecte. Vérifie ton réseau puis réessaie."
    }
}
