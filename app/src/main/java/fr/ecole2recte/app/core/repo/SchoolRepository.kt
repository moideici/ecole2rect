package fr.ecole2recte.app.core.repo

import fr.ecole2recte.app.core.auth.AnswerMemory
import fr.ecole2recte.app.core.auth.AutoAnswer
import fr.ecole2recte.app.core.auth.BirthDates
import fr.ecole2recte.app.core.auth.RememberedAnswer
import fr.ecole2recte.app.core.auth.SecurityAnswer
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.diag.ErrorLog
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.protocol.ApiErrorKind
import fr.ecole2recte.app.core.protocol.ApiResult
import fr.ecole2recte.app.core.protocol.DownloadedFile
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
    /**
     * ÉcoleDirecte pose sa question et l'application n'a pas su y répondre seule. [notice]
     * dit pourquoi quand une réponse automatique vient d'être refusée : sans cela,
     * l'utilisateur verrait la question réapparaître sans comprendre.
     */
    data class SecurityQuestion(
        val question: String,
        val choices: List<String>,
        val notice: String? = null,
    ) : AuthState
    data class SignedIn(val account: Account, val accounts: List<Account>) : AuthState
    data class Failed(
        val message: String,
        val kind: ApiErrorKind,
        val diagnostic: String? = null,
    ) : AuthState
}

/**
 * Ce que l'application sait répondre à la place de l'utilisateur, tel que les réglages
 * l'affichent. Les dates de naissance et les réponses mémorisées lui appartiennent : il doit
 * pouvoir les voir, les corriger et les effacer.
 */
data class AutoAnswerState(
    val birthDates: BirthDates = BirthDates(),
    val remembered: List<RememberedAnswer> = emptyList(),
)

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
    val errorLog: ErrorLog = ErrorLog(),
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

    /** Vrai quand la messagerie est entièrement chargée : il n'y a plus de page suivante. */
    private val _messagesComplete = MutableStateFlow(false)
    val messagesComplete: StateFlow<Boolean> = _messagesComplete.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMoreMessages: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var messagesPage = 0

    /** Jours dont le détail (devoirs et contenu de séance) a déjà été demandé. */
    private val loadedDays = mutableSetOf<LocalDate>()

    /** Profil dont le cache est actuellement chargé — l'identifiant du compte suivi. */
    private var profile: String? = null

    /** Une seule synchronisation à la fois : l'API n'aime pas les appels concurrents. */
    private val syncLock = Mutex()

    private val _autoAnswer = MutableStateFlow(AutoAnswerState())
    val autoAnswer: StateFlow<AutoAnswerState> = _autoAnswer.asStateFlow()

    /** Identifiants de la connexion en cours, le temps de la double authentification. */
    private var pending: SavedCredentials? = null

    /** Question posée en ce moment : c'est elle qu'on mémorise avec la réponse validée. */
    private var pendingQuestion: LoginOutcome.SecurityQuestion? = null

    /**
     * Une seule réponse automatique par tentative de connexion. Enchaîner les essais
     * ressemblerait à du forçage, et ÉcoleDirecte bloque les comptes pour moins que ça.
     */
    private var autoAnswerUsed = false

    /** Message à afficher avec la question quand la réponse enregistrée vient d'être refusée. */
    private var questionNotice: String? = null

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
            profile = _settings.value.selectedAccountId
            snapshots.read(profile)?.let { _snapshot.value = it }
        }
        val saved = withContext(io) { credentials.read() }
        publishAutoAnswer(saved)
        if (saved == null) {
            _auth.value = AuthState.SignedOut
            return
        }
        _auth.value = AuthState.Connecting(silent = true)
        signInWith(saved, persist = false)
    }

    /**
     * Connexion demandée par l'utilisateur.
     *
     * La date de naissance saisie rejoint le trousseau chiffré : c'est elle qui permettra,
     * des semaines plus tard et sans personne devant l'écran, de reconnaître la bonne
     * proposition si ÉcoleDirecte repose sa question.
     */
    suspend fun signIn(
        identifier: String,
        password: String,
        birthDate: SecurityAnswer.BirthDate = SecurityAnswer.BirthDate(),
    ) {
        _auth.value = AuthState.Connecting()
        val saved = withContext(io) { credentials.read() }
            ?.takeIf { it.identifier == identifier && it.password == password }
        val known = saved?.birthDates ?: BirthDates()
        signInWith(
            SavedCredentials(
                identifier = identifier,
                password = password,
                trustedAnswer = saved?.trustedAnswer,
                // Une date laissée vide n'efface pas celle déjà connue : se reconnecter sans
                // la ressaisir ne doit pas faire perdre la réponse automatique.
                birthDates = if (birthDate.isFilled) known.with(BirthDates.DEFAULT_KEY, birthDate) else known,
                answers = saved?.answers ?: AnswerMemory(),
            ),
            persist = true,
        )
    }

    /**
     * Envoie la réponse choisie par l'utilisateur, et la retient.
     *
     * La retenir n'est pas un détail : c'est ce qui fait qu'on ne repose pas la même question
     * à chaque expiration de jeton. Elle vaut jusqu'à la rentrée suivante, parce qu'une
     * classe change d'année en année.
     */
    suspend fun answerSecurityQuestion(choice: String) {
        val credentialsInProgress = pending ?: run {
            _auth.value = AuthState.SignedOut
            return
        }
        val question = pendingQuestion?.question
        _auth.value = AuthState.Connecting()
        val outcome = withContext(io) {
            api.answerSecurityQuestion(choice, credentialsInProgress.identifier, credentialsInProgress.password)
        }
        val updated = if (outcome is LoginOutcome.Success && question != null) {
            credentialsInProgress.copy(
                answers = credentialsInProgress.answers
                    .remembering(question, choice, today())
                    .purged(today()),
            )
        } else {
            credentialsInProgress
        }
        pending = updated
        handleOutcome(outcome, updated, persist = true)
    }

    private suspend fun signInWith(saved: SavedCredentials, persist: Boolean) {
        pending = saved
        autoAnswerUsed = false
        questionNotice = null
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
                val kept = saved.copy(trustedAnswer = answer, answers = saved.answers.purged(today()))
                withContext(io) {
                    if (persist || kept != saved) credentials.save(kept)
                }
                publishAutoAnswer(kept)
                pending = null
                pendingQuestion = null
                _sync.update { it.copy(offline = false) }
                _auth.value = AuthState.SignedIn(account, outcome.accounts)
                // Le cache lu au démarrage peut être celui d'un autre profil : si le compte
                // retenu diffère, on recharge le bon avant d'afficher quoi que ce soit.
                if (profile != account.id) {
                    profile = account.id
                    withContext(io) { snapshots.read(account.id) }?.let { _snapshot.value = it }
                }
                rememberAccount(account)
            }

            is LoginOutcome.SecurityQuestion -> {
                pending = saved
                val answered = autoAnswer(outcome, saved)
                if (answered == null) showQuestion(outcome) else handleOutcome(answered, saved, persist)
            }

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

    /**
     * Tente de franchir la question de sécurité sans déranger personne.
     *
     * Renvoie l'issue de la connexion relancée, ou `null` quand l'application n'a rien de
     * légitime à répondre — la question part alors à l'utilisateur. Une réponse mémorisée
     * refusée est effacée sur-le-champ : elle a cessé d'être vraie, la garder ferait échouer
     * toutes les connexions suivantes.
     */
    private suspend fun autoAnswer(
        question: LoginOutcome.SecurityQuestion,
        saved: SavedCredentials,
    ): LoginOutcome? {
        if (autoAnswerUsed) return null
        val decision = AutoAnswer.decide(
            question = question.question,
            choices = question.choices,
            birthDates = saved.birthDates,
            memory = saved.answers,
            today = today(),
        ) ?: return null
        autoAnswerUsed = true
        val outcome = withContext(io) {
            api.answerSecurityQuestion(decision.answer, saved.identifier, saved.password)
        }
        if (outcome is LoginOutcome.Failure && outcome.kind != ApiErrorKind.NETWORK) {
            if (decision.source == AutoAnswer.Source.REMEMBERED) {
                val cleaned = saved.copy(answers = saved.answers.forgetting(question.question))
                pending = cleaned
                withContext(io) { credentials.save(cleaned) }
                publishAutoAnswer(cleaned)
            }
            questionNotice = when (decision.source) {
                AutoAnswer.Source.REMEMBERED ->
                    "La réponse enregistrée n'a pas été acceptée : choisis-la à nouveau."

                AutoAnswer.Source.BIRTH_DATE ->
                    "La proposition correspondant à la date de naissance a été refusée. Vérifie la date dans les réglages."
            }
            // Un refus n'est jamais une panne réseau : c'est le genre de changement qu'on
            // veut retrouver dans le journal quand l'API bouge.
            errorLog.record("Question de sécurité", "Réponse automatique refusée", null)
            return null
        }
        return outcome
    }

    private fun showQuestion(outcome: LoginOutcome.SecurityQuestion) {
        pendingQuestion = outcome
        _auth.value = AuthState.SecurityQuestion(outcome.question, outcome.choices, questionNotice)
        questionNotice = null
    }

    private fun publishAutoAnswer(saved: SavedCredentials?) {
        _autoAnswer.value = AutoAnswerState(
            birthDates = saved?.birthDates ?: BirthDates(),
            remembered = saved?.answers?.valid(today()).orEmpty(),
        )
    }

    /**
     * Enregistre la date de naissance d'un élève suivi.
     *
     * Un parent qui suit deux enfants a deux dates à donner : la question de sécurité nomme
     * l'enfant concerné, et c'est ce nom qui sert de clé. Sans nom, la date vaut par défaut.
     */
    suspend fun setBirthDate(name: String, birthDate: SecurityAnswer.BirthDate) {
        val saved = withContext(io) { credentials.read() } ?: return
        val updated = saved.copy(birthDates = saved.birthDates.with(name, birthDate))
        withContext(io) { credentials.save(updated) }
        if (pending?.identifier == updated.identifier) pending = updated
        publishAutoAnswer(updated)
    }

    /** Oublie une réponse mémorisée : la question sera reposée à la prochaine occasion. */
    suspend fun forgetAnswer(question: String) {
        val saved = withContext(io) { credentials.read() } ?: return
        val updated = saved.copy(answers = saved.answers.forgetting(question))
        withContext(io) { credentials.save(updated) }
        if (pending?.identifier == updated.identifier) pending = updated
        publishAutoAnswer(updated)
    }

    private fun pickAccount(accounts: List<Account>): Account? {
        val preferred = _settings.value.selectedAccountId
        return accounts.firstOrNull { it.id == preferred && it.isStudent }
            ?: accounts.firstOrNull { it.isStudent }
            ?: accounts.firstOrNull()
    }

    private fun rememberAccount(account: Account) {
        _snapshot.update { it.copy(account = account) }
        val current = _settings.value
        if (current.selectedAccountId != account.id || current.parentAccount != account.viewedByFamily) {
            updateSettingsBlocking {
                it.copy(selectedAccountId = account.id, parentAccount = account.viewedByFamily)
            }
        }
    }

    /**
     * Bascule vers un autre compte de la même connexion (fratrie).
     *
     * Le cache du profil quitté est conservé, celui du profil rejoint est relu : revenir en
     * arrière est instantané, et personne ne repaie une synchronisation complète.
     */
    suspend fun selectAccount(account: Account) {
        val state = _auth.value as? AuthState.SignedIn ?: return
        if (account.id == profile) return
        withContext(io) { snapshots.write(profile, _snapshot.value) }
        profile = account.id
        loadedDays.clear()
        messagesPage = 0
        _messagesComplete.value = false
        _auth.value = AuthState.SignedIn(account, state.accounts)
        _snapshot.value = withContext(io) { snapshots.read(account.id) }
            ?: SchoolSnapshot(account = account)
        updateSettings { it.copy(selectedAccountId = account.id) }
        refresh()
    }

    suspend fun signOut() {
        api.clearSession()
        pending = null
        pendingQuestion = null
        autoAnswerUsed = false
        questionNotice = null
        _autoAnswer.value = AutoAnswerState()
        withContext(io) {
            credentials.clear()
            snapshots.clear()
        }
        _snapshot.value = SchoolSnapshot()
        _sync.value = SyncState()
        loadedDays.clear()
        messagesPage = 0
        _messagesComplete.value = false
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
                        // Une panne réseau n'apprend rien ; un refus ou un format inattendu,
                        // si : c'est ce qu'on veut retrouver quand l'API change.
                        if (result.kind != ApiErrorKind.NETWORK) {
                            errorLog.record(section.label, result.message, result.diagnostic)
                        }
                    }
                }
            }
            withContext(io) { snapshots.write(profile, _snapshot.value) }
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

        DataSection.MESSAGES -> withSession { api.messages(account, page = 0) }.onOk { messages ->
            messagesPage = 0
            _messagesComplete.value = messages.size < EcoleDirecteApi.MESSAGES_PER_PAGE
            _snapshot.update {
                it.copy(
                    messages = Merge.messages(it.messages, messages),
                    syncedAt = it.syncedAt + (section to now()),
                )
            }
        }

        DataSection.SCHOOL_LIFE -> withSession { api.schoolLife(account.id) }.onOk { events ->
            _snapshot.update {
                it.copy(schoolLife = events, syncedAt = it.syncedAt + (section to now()))
            }
        }

        DataSection.DOCUMENTS -> withSession { api.documents(account.id) }.onOk { documents ->
            _snapshot.update {
                it.copy(documents = documents, syncedAt = it.syncedAt + (section to now()))
            }
        }
    }

    /**
     * Page suivante de la messagerie.
     *
     * L'appel est explicite, déclenché par l'utilisateur en bas de liste : charger toute
     * l'année en arrivant sur l'écran serait long, inutile et impoli envers l'API.
     */
    suspend fun loadMoreMessages() {
        val account = currentAccount ?: return
        if (_messagesComplete.value || _loadingMore.value) return
        _loadingMore.value = true
        val nextPage = messagesPage + 1
        val result = withSession { api.messages(account, page = nextPage) }
        if (result is ApiResult.Ok) {
            messagesPage = nextPage
            _messagesComplete.value = result.value.size < EcoleDirecteApi.MESSAGES_PER_PAGE
            _snapshot.update { snapshot ->
                snapshot.copy(messages = Merge.appendMessages(snapshot.messages, result.value))
            }
            withContext(io) { snapshots.write(profile, _snapshot.value) }
        }
        _loadingMore.value = false
    }

    /**
     * Télécharge une pièce jointe. Le dépôt ne touche pas au disque : il rend les octets, et
     * la couche Android décide où les écrire et comment les ouvrir.
     */
    suspend fun downloadAttachment(attachment: Attachment): ApiResult<DownloadedFile> =
        withSession { api.downloadAttachment(attachment) }

    /**
     * Charge le contenu détaillé des devoirs d'un jour, à l'ouverture d'une fiche.
     * L'appel n'est fait qu'une fois : si le contenu est déjà là, on ne redemande rien.
     */
    suspend fun loadHomeworkDetails(date: LocalDate, force: Boolean = false) {
        val account = currentAccount ?: return
        // Le jour, pas les devoirs : une journée peut n'avoir aucun devoir mais un contenu
        // de séance, et l'inverse. On retient donc ce qui a été demandé, pas ce qui est revenu.
        if (!force && date in loadedDays) return
        loadedDays += date
        val result = withSession { api.dayContent(account.id, date) }
        if (result is ApiResult.Ok) {
            _snapshot.update {
                it.copy(
                    homework = Merge.homeworkForDate(it.homework, result.value.homework, date),
                    sessionContents = Merge.sessionContents(it.sessionContents, result.value.sessions, date),
                )
            }
            withContext(io) { snapshots.write(profile, _snapshot.value) }
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
        withContext(io) { snapshots.write(profile, _snapshot.value) }
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
        // Vue parent : la coche reste sur l'appareil. Le cahier de textes de l'élève ne doit
        // pas changer parce que sa mère a fait le point le soir — il verrait un devoir rendu
        // qu'il n'a pas rendu, ce qui est pire que pas de coche du tout.
        if (account.viewedByFamily) return
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
        autoAnswerUsed = false
        questionNotice = null
        val first = withContext(io) {
            api.login(saved.identifier, saved.password, saved.trustedAnswer)
        }
        // Une question reposée en pleine synchronisation de fond : si l'application sait y
        // répondre, l'utilisateur n'a rien à voir passer.
        val outcome = if (first is LoginOutcome.SecurityQuestion) {
            autoAnswer(first, saved) ?: first
        } else {
            first
        }
        return when (outcome) {
            is LoginOutcome.Success -> {
                val account = pickAccount(outcome.accounts) ?: return Reconnection.REFUSED
                val answer = api.approvedAnswer
                if (answer != null && answer != saved.trustedAnswer) {
                    val kept = saved.copy(trustedAnswer = answer)
                    withContext(io) { credentials.save(kept) }
                    publishAutoAnswer(kept)
                }
                _auth.value = AuthState.SignedIn(account, outcome.accounts)
                Reconnection.DONE
            }

            is LoginOutcome.SecurityQuestion -> {
                pending = saved
                showQuestion(outcome)
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
