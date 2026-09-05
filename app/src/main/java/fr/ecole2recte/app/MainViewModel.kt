package fr.ecole2recte.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.ecole2recte.app.core.auth.SecurityAnswer
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.protocol.ApiResult
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.core.settings.NotificationSettings
import fr.ecole2recte.app.core.settings.ThemeChoice
import fr.ecole2recte.app.data.AttachmentOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * État de l'écran de connexion.
 *
 * La date de naissance ne sert qu'à reconnaître, parmi les propositions renvoyées par
 * ÉcoleDirecte, celle que l'utilisateur a lui-même saisie. Elle n'est ni envoyée ni
 * enregistrée tant qu'aucune question n'est posée.
 */
data class LoginForm(
    val identifier: String = "",
    val password: String = "",
    val birth: SecurityAnswer.BirthDate = SecurityAnswer.BirthDate(),
    val chosenAnswer: String = "",
) {
    val canSubmit: Boolean get() = identifier.isNotBlank() && password.isNotBlank()
}

/** Où en est l'ouverture d'une pièce jointe : c'est la seule action qui peut prendre du temps. */
sealed interface AttachmentState {
    data object Idle : AttachmentState
    data class Working(val id: String) : AttachmentState
    data class Ready(val id: String, val uri: Uri, val name: String) : AttachmentState
    data class Failed(val id: String, val message: String) : AttachmentState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as Ecole2recteApplication).container.repository

    val auth: StateFlow<AuthState> = repository.auth
    val snapshot = repository.snapshot
    val sync = repository.sync
    val settings: StateFlow<AppSettings> = repository.settings

    val errorLog = repository.errorLog.entries
    val messagesComplete = repository.messagesComplete
    val loadingMoreMessages = repository.loadingMoreMessages

    private val _attachment = MutableStateFlow<AttachmentState>(AttachmentState.Idle)
    val attachment: StateFlow<AttachmentState> = _attachment.asStateFlow()

    private val _loginForm = MutableStateFlow(LoginForm())
    val loginForm: StateFlow<LoginForm> = _loginForm.asStateFlow()

    /**
     * Horloge de l'interface. « dans 24 min » deviendrait faux au bout d'une minute : cette
     * horloge avance toute seule pour que l'accueil reste juste sans que rien ne soit touché.
     */
    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    init {
        viewModelScope.launch {
            repository.start()
            refresh()
        }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                _now.value = LocalDateTime.now()
            }
        }
    }

    // ── Connexion ────────────────────────────────────────────────────────────

    fun onLoginFormChange(transform: (LoginForm) -> LoginForm) = _loginForm.update(transform)

    /** Proposition correspondant à la date de naissance saisie, ou null s'il n'y en a pas. */
    fun suggestedAnswer(question: String, choices: List<String>): String? =
        SecurityAnswer.suggest(question, choices, _loginForm.value.birth)

    fun signIn() {
        val form = _loginForm.value
        if (!form.canSubmit) return
        viewModelScope.launch {
            repository.signIn(form.identifier.trim(), form.password)
            refresh()
        }
    }

    fun answerSecurityQuestion(choice: String) {
        if (choice.isBlank()) return
        viewModelScope.launch {
            repository.answerSecurityQuestion(choice)
            refresh()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _loginForm.value = LoginForm()
        }
    }

    fun selectAccount(account: Account) {
        viewModelScope.launch { repository.selectAccount(account) }
    }

    // ── Données ──────────────────────────────────────────────────────────────

    fun refresh(sections: Set<DataSection> = DataSection.entries.toSet()) {
        viewModelScope.launch { repository.refresh(sections) }
    }

    /** Synchronise seulement si la dernière donnée date de plus de cinq minutes. */
    fun refreshIfStale() {
        if (auth.value !is AuthState.SignedIn) return
        val last = snapshot.value.lastSyncedAt ?: 0L
        if (System.currentTimeMillis() - last < STALE_AFTER_MILLIS) return
        refresh()
    }

    fun loadHomeworkDetails(date: LocalDate) {
        viewModelScope.launch { repository.loadHomeworkDetails(date) }
    }

    fun openMessage(message: MessageItem) {
        viewModelScope.launch { repository.openMessage(message) }
    }

    fun toggleHomework(item: HomeworkItem) {
        viewModelScope.launch { repository.toggleHomeworkDone(item) }
    }

    fun loadMoreMessages() {
        viewModelScope.launch { repository.loadMoreMessages() }
    }

    /**
     * Télécharge une pièce jointe puis l'expose à l'interface, qui la fera ouvrir par
     * l'application capable de la lire. L'écriture du fichier se fait hors du fil principal.
     */
    fun openAttachment(attachment: Attachment) {
        if (_attachment.value is AttachmentState.Working) return
        _attachment.value = AttachmentState.Working(attachment.id)
        viewModelScope.launch {
            when (val result = repository.downloadAttachment(attachment)) {
                is ApiResult.Ok -> {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            AttachmentOpener(getApplication())
                                .save(result.value.name, result.value.bytes)
                        }
                    }
                    _attachment.value = saved.getOrNull()
                        ?.let { AttachmentState.Ready(attachment.id, it, result.value.name) }
                        ?: AttachmentState.Failed(attachment.id, "Impossible d'enregistrer le fichier.")
                }

                is ApiResult.Failure -> {
                    _attachment.value = AttachmentState.Failed(attachment.id, result.message)
                }
            }
        }
    }

    fun clearErrorLog() = repository.errorLog.clear()

    fun clearAttachmentState() {
        _attachment.value = AttachmentState.Idle
    }

    // ── Personnalisation ─────────────────────────────────────────────────────

    fun setTheme(choice: ThemeChoice) = updateSettings { it.copy(theme = choice) }

    fun setSubjectColor(subject: String, color: Long?) = updateSettings { it.withSubjectColor(subject, color) }

    fun moveWidget(widget: HomeWidget, offset: Int) = updateSettings { it.move(widget, offset) }

    fun toggleWidget(widget: HomeWidget) = updateSettings { it.toggleWidget(widget) }

    fun setDiagnosticsVisible(visible: Boolean) = updateSettings { it.copy(showDiagnostics = visible) }

    fun setNotifications(transform: (NotificationSettings) -> NotificationSettings) =
        updateSettings { it.copy(notifications = transform(it.notifications)) }

    fun setBiometricLock(enabled: Boolean) = updateSettings { it.copy(biometricLock = enabled) }

    fun setDynamicColor(enabled: Boolean) = updateSettings { it.copy(dynamicColor = enabled) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STALE_AFTER_MILLIS = 5 * 60_000L
    }
}
