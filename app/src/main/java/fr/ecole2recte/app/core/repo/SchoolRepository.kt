package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.protocol.ApiErrorKind
import fr.ecole2recte.app.core.protocol.ApiResult
import fr.ecole2recte.app.core.protocol.EcoleDirecteApi
import fr.ecole2recte.app.core.protocol.LoginOutcome
import fr.ecole2recte.app.core.protocol.TrustedAnswer
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.store.CredentialStore
import fr.ecole2recte.app.core.store.SavedCredentials
import fr.ecole2recte.app.core.store.SettingsStore
import fr.ecole2recte.app.core.store.SnapshotStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Où en est la connexion — l'interface n'a besoin de rien d'autre pour décider quoi afficher. */
sealed interface AuthState {
    /** Au démarrage, avant d'avoir lu le stockage local. */
    data object Unknown : AuthState
    data object SignedOut : AuthState
    /**
     * Connexion en cours. « Silencieuse » quand elle rejoue des identifiants enregistrés au
     * lancement : l'interface montre alors la marque, pas un formulaire de connexion qui
     * clignoterait pour disparaître aussitôt.
     */
    data class Connecting(val silent: Boolean = false) : AuthState
    data class SecurityQuestion(val question: String, val choices: List<String>) : AuthState
    data class SignedIn(val account: Account, val accounts: List<Account>) : AuthState
    data class Failed(
        val message: String,
        val kind: ApiErrorKind,
        val diagnostic: String? = null,
    ) : AuthState
}

/** État de la synchronisation, section par section. */
data class SyncState(
    val running: Boolean = false,
    val errors: Map<DataSection, String> = emptyMap(),
    /** Vrai quand la dernière tentative a échoué faute de réseau : les données affichées datent. */
    val offline: Boolean = false,
) {
    fun errorFor(section: DataSection): String? = errors[section]
}

/**
 * Le dépôt : seule porte d'entrée de l'application vers les données scolaires.
 *
 * Il tient trois promesses :
 *
 *  1. **afficher tout de suite** — le cache est relu au lancement, avant tout appel réseau ;
 *  2. **ne jamais perdre une donnée valide** — une section qui échoue laisse la précédente
 *     en place et signale son erreur, au lieu de vider l'écran ;
 *  3. **se reconnecter en silence** — un jeton expiré déclenche une reconnexion et un seul
 *     réessai, sans renvoyer l'utilisateur sur l'écran de connexion.
 */
class SchoolRepository(
    private val api: EcoleDirecteApi,
    private val credentials: CredentialStore,
    private val snapshots: SnapshotStore,
    private val settingsStore: SettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    private val _auth = MutableStateFlow<AuthState>(AuthState.Unknown)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _snapshot = MutableStateFlow(SchoolSnapshot())
    val snapshot: StateFlow<SchoolSnapshot> = _snapshot.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Une seule synchronisation à la fois : l'API n'aime pas les appels concurrents. */
    private val syncLock = Mutex()

    /** Identifiants de la connexion en cours, le temps de la double authentification. */
    private var pending: SavedCredentials? = null

    val currentAccount: Account? get() = (_auth.value as? AuthState.SignedIn)?.account

    // ── Démarrage ────────────────────────────────────────────────────────────

    /**
     * Relit le disque puis, si des identifiants sont enregistrés, reconnecte silencieusement.
     * Retourne quand l'état d'authentification est décidé ; la synchronisation, elle, est
     * lancée par l'appelant.
     */
    suspend fun start() {
        withContext(io) {
            _settings.value = settingsStore.read()
            snapshots.read()?.let { _snapshot.value = it }
        }
        val saved = withContext(io) { credentials.read() }
        if (saved == null) {
            _auth.value = AuthState.SignedOut
            return
        }
        _auth.value = AuthState.Connecting(silent = true)
        signInWith(saved, persist = false)
    }

    suspend fun signIn(identifier: String, password: String) {
        _auth.value = AuthState.Connecting()
        val saved = withContext(io) { credentials.read() }
            ?.takeIf { it.identifier == identifier && it.password == password }
        signInWith(
            SavedCredentials(identifier, password, saved?.trustedAnswer),
            persist = true,
        )
    }

    /** Envoie la réponse choisie par l'utilisateur à la question de sécurité. */
    suspend fun answerSecurityQuestion(choice: String) {
        val credentialsInProgress = pending ?: run {
            _auth.value = AuthState.SignedOut
            return
        }
        _auth.value = AuthState.Connecting()
        val outcome = withContext(io) {
            api.answerSecurityQuestion(choice, credentialsInProgress.identifier, credentialsInProgress.password)
        }
        handleOutcome(outcome, credentialsInProgress, persist = true)
    }

    private suspend fun signInWith(saved: SavedCredentials, persist: Boolean) {
        pending = saved
        val outcome = withContext(io) {
            api.login(saved.identifier, saved.password, saved.trustedAnswer)
        }
        handleOutcome(outcome, saved, persist)
    }

    private suspend fun handleOutcome(outcome: LoginOutcome, saved: SavedCredentials, persist: Boolean) {
        when (outcome) {
            is LoginOutcome.Success -> {
                val account = pickAccount(outcome.accounts)
                if (account == null) {
                    _auth.value = AuthState.Failed(
                        "Aucun compte élève n'est associé à cette connexion.",
                        ApiErrorKind.ACCESS_DENIED,
                    )
                    return
                }
                val answer = api.approvedAnswer ?: saved.trustedAnswer
                withContext(io) {
                    if (persist || answer != saved.trustedAnswer) {
                        credentials.save(saved.copy(trustedAnswer = answer))
                    }
                }
                pending = null
                _sync.update { it.copy(offline = false) }
                _auth.value = AuthState.SignedIn(account, outcome.accounts)
                rememberAccount(account)
            }

            is LoginOutcome.SecurityQuestion ->
                _auth.value = AuthState.SecurityQuestion(outcome.question, outcome.choices)

            is LoginOutcome.Failure -> {
                // Hors ligne avec des données en cache : on ouvre l'application en lecture
                // seule plutôt que de renvoyer sur l'écran de connexion, où l'utilisateur ne
                // pourrait de toute façon rien faire.
                val cachedAccount = _snapshot.value.account
                if (outcome.kind == ApiErrorKind.NETWORK && cachedAccount != null) {
                    _sync.update { it.copy(offline = true) }
                    _auth.value = AuthState.SignedIn(cachedAccount, listOf(cachedAccount))
                } else {
                    if (outcome.kind == ApiErrorKind.INVALID_CREDENTIALS) {
                        withContext(io) { credentials.clear() }
                    }
                    _auth.value = AuthState.Failed(outcome.message, outcome.kind, outcome.diagnostic)
                }
            }
        }
    }

    private fun pickAccount(accounts: List<Account>): Account? {
        val preferred = _settings.value.selectedAccountId
        return accounts.firstOrNull { it.id == preferred && it.isStudent }
            ?: accounts.firstOrNull { it.isStudent }
            ?: accounts.firstOrNull()
    }

    private fun rememberAccount(account: Account) {
        _snapshot.update { it.copy(account = account) }
        if (_settings.value.selectedAccountId != account.id) {
            updateSettingsBlocking { it.copy(selectedAccountId = account.id) }
        }
    }

    /** Bascule vers un autre compte de la même connexion (fratrie). */
    suspend fun selectAccount(account: Account) {
        val state = _auth.value as? AuthState.SignedIn ?: return
        _auth.value = AuthState.SignedIn(account, state.accounts)
        _snapshot.value = SchoolSnapshot(account = account)
        withContext(io) { snapshots.write(_snapshot.value) }
        updateSettings { it.copy(selectedAccountId = account.id) }
        refresh()
    }

    suspend fun signOut() {
        api.clearSession()
        pending = null
        withContext(io) {
            credentials.clear()
            snapshots.clear()
        }
        _snapshot.value = SchoolSnapshot()
        _sync.value = SyncState()
        _auth.value = AuthState.SignedOut
    }

    // ── Synchronisation ──────────────────────────────────────────────────────

    /**
     * Récupère les sections demandées. Chaque section est indépendante : l'échec de l'une
     * n'annule pas les autres, et laisse en place la donnée déjà connue.
     */
    suspend fun refresh(sections: Set<DataSection> = DataSection.entries.toSet()) {
        val account = currentAccount ?: return
        syncLock.withLock {
            _sync.update { it.copy(running = true) }
            var offline = false
            val errors = _sync.value.errors.toMutableMap()
            sections.forEach { section ->
                when (val result = fetch(section, account)) {
                    is ApiResult.Ok -> errors.remove(section)
                    is ApiResult.Failure -> {
                        if (result.kind == ApiErrorKind.NETWORK) offline = true
                        errors[section] = result.message
                    }
                }
            }
            withContext(io) { snapshots.write(_snapshot.value) }
            _sync.value = SyncState(running = false, errors = errors.toMap(), offline = offline)
        }
    }

    private suspend fun fetch(section: DataSection, account: Account): ApiResult<Unit> = when (section) {
        DataSection.TIMETABLE -> {
            val from = Dates.startOfWeek(today())
            val to = from.plusDays(TIMETABLE_WINDOW_DAYS)
            withSession { api.timetable(account.id, from, to) }.onOk { lessons ->
                _snapshot.update {
                    it.copy(
                        lessons = Merge.lessons(it.lessons, lessons, from, to),
                        syncedAt = it.syncedAt + (section to now()),
                    )
                }
            }
        }

        DataSection.HOMEWORK -> withSession { api.homework(account.id) }.onOk { items ->
            _snapshot.update {
                it.copy(
                    homework = Merge.homework(it.homework, items),
                    syncedAt = it.syncedAt + (section to now()),
                )
            }
        }

        DataSection.GRADES -> withSession { api.grades(account.id) }.onOk { payload ->
            _snapshot.update {
                it.copy(
                    grades = payload.grades,
                    periods = payload.periods,
                    subjectAverages = payload.subjectAverages,
                    syncedAt = it.syncedAt + (section to now()),
                )
            }
        }

        DataSection.MESSAGES -> withSession { api.messages(account) }.onOk { messages ->
            _snapshot.update {
                it.copy(
                    messages = Merge.messages(it.messages, messages),
                    syncedAt = it.syncedAt + (section to now()),
                )
            }
        }
    }

    /**
     * Charge le contenu détaillé des devoirs d'un jour, à l'ouverture d'une fiche.
     * L'appel n'est fait qu'une fois : si le contenu est déjà là, on ne redemande rien.
     */
    suspend fun loadHomeworkDetails(date: LocalDate, force: Boolean = false) {
        val account = currentAccount ?: return
        val alreadyLoaded = _snapshot.value.homework
            .filter { it.dueDate == date }
            .let { items -> items.isNotEmpty() && items.all { it.hasContent } }
        if (alreadyLoaded && !force) return
        val result = withSession { api.homeworkForDate(account.id, date) }
        if (result is ApiResult.Ok) {
            _snapshot.update {
                it.copy(homework = Merge.homeworkForDate(it.homework, result.value, date))
            }
            withContext(io) { snapshots.write(_snapshot.value) }
        }
    }

    /** Charge le corps d'un message et le marque lu localement. */
    suspend fun openMessage(message: MessageItem) {
        markMessageRead(message)
        val account = currentAccount ?: return
        if (message.hasBody) return
        val result = withSession { api.messageBody(account, message.id) }
        val loaded = (result as? ApiResult.Ok)?.value ?: return
        _snapshot.update {
            it.copy(
                messages = Merge.replaceMessage(
                    it.messages,
                    message.copy(
                        body = loaded.body.ifBlank { message.body },
                        preview = message.preview.ifBlank { loaded.preview },
                        attachments = loaded.attachments.ifEmpty { message.attachments },
                        read = true,
                    ),
                ),
            )
        }
        withContext(io) { snapshots.write(_snapshot.value) }
    }

    private suspend fun markMessageRead(message: MessageItem) {
        if (_settings.value.isMessageRead(message.id, message.read)) return
        updateSettings { it.withMessageRead(message.id) }
    }

    /**
     * Coche ou décoche un devoir. L'état local est appliqué immédiatement — l'application
     * doit répondre même hors ligne — puis poussé vers le serveur au mieux.
     */
    suspend fun toggleHomeworkDone(item: HomeworkItem) {
        val done = !_settings.value.isHomeworkDone(item.id, item.done)
        updateSettings { it.withHomeworkDone(item.id, done) }
        val account = currentAccount ?: return
        withSession { api.setHomeworkDone(account.id, item.id, done) }
    }

    // ── Réglages ─────────────────────────────────────────────────────────────

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        withContext(io) { settingsStore.write(updated) }
    }

    /** Variante non suspendue, pour les mises à jour déclenchées depuis un état interne. */
    private fun updateSettingsBlocking(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching { settingsStore.write(updated) }
    }

    // ── Session ──────────────────────────────────────────────────────────────

    /**
     * Exécute un appel, et le rejoue une fois après reconnexion si le jeton était expiré.
     * Un seul réessai : au-delà, c'est un vrai refus, et insister ressemblerait à du forçage.
     */
    private suspend fun <T> withSession(call: () -> ApiResult<T>): ApiResult<T> {
        val first = withContext(io) { call() }
        if (first !is ApiResult.Failure || first.kind != ApiErrorKind.SESSION_EXPIRED) return first
        return when (reconnect()) {
            Reconnection.DONE -> withContext(io) { call() }
            // La reconnexion n'a pas pu aboutir faute de réseau : c'est bien une panne de
            // réseau qu'il faut signaler, pas une session refusée, sinon l'interface
            // annoncerait une déconnexion là où il suffit d'attendre le retour du réseau.
            Reconnection.NETWORK -> ApiResult.Failure(
                ApiErrorKind.NETWORK,
                "Réseau indisponible : données du cache.",
            )

            Reconnection.REFUSED -> first
        }
    }

    private enum class Reconnection { DONE, NETWORK, REFUSED }

    private suspend fun reconnect(): Reconnection {
        val saved = withContext(io) { credentials.read() } ?: return Reconnection.REFUSED
        api.clearSession()
        val outcome = withContext(io) {
            api.login(saved.identifier, saved.password, saved.trustedAnswer)
        }
        return when (outcome) {
            is LoginOutcome.Success -> {
                val account = pickAccount(outcome.accounts) ?: return Reconnection.REFUSED
                _auth.value = AuthState.SignedIn(account, outcome.accounts)
                Reconnection.DONE
            }

            is LoginOutcome.SecurityQuestion -> {
                pending = saved
                _auth.value = AuthState.SecurityQuestion(outcome.question, outcome.choices)
                Reconnection.REFUSED
            }

            is LoginOutcome.Failure -> {
                if (outcome.kind == ApiErrorKind.NETWORK) {
                    Reconnection.NETWORK
                } else {
                    _auth.value = AuthState.Failed(outcome.message, outcome.kind, outcome.diagnostic)
                    Reconnection.REFUSED
                }
            }
        }
    }

    private inline fun <T> ApiResult<T>.onOk(block: (T) -> Unit): ApiResult<Unit> = when (this) {
        is ApiResult.Ok -> {
            block(value)
            ApiResult.Ok(Unit)
        }

        is ApiResult.Failure -> this
    }

    private companion object {
        /** Trois semaines : la semaine en cours et les deux suivantes, comme le site web. */
        const val TIMETABLE_WINDOW_DAYS = 20L
    }
}

/** Restaure un état de synchronisation lisible pour l'interface. */
fun SyncState.statusLabel(lastSyncMillis: Long?, nowMillis: Long): String = when {
    running -> "Synchronisation…"
    offline -> "Hors ligne · ${Dates.freshness(lastSyncMillis, nowMillis)}"
    errors.isNotEmpty() -> "Partiellement à jour · ${Dates.freshness(lastSyncMillis, nowMillis)}"
    else -> "À jour · ${Dates.freshness(lastSyncMillis, nowMillis)}"
}
