package fr.ecole2recte.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ecole2recte.app.AppContainer
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.domain.Badges
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.HomeData
import fr.ecole2recte.app.domain.Homework
import fr.ecole2recte.app.domain.HomeworkDetail
import fr.ecole2recte.app.domain.MessageDetail
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.ScheduleWeek
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.session.ActiveProfile

/** Profil dont les données sont affichées ; change quand on change de compte ou d'élève. */
val LocalActiveProfile = staticCompositionLocalOf<ActiveProfile?> { null }

/** Conteneur de dépendances de l'application. */
@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as Ecole2recteApplication).container

/** Dépôt de données du profil actif. */
@Composable
fun rememberSchoolRepository(): SchoolRepository {
    val container = (LocalContext.current.applicationContext as Ecole2recteApplication).container
    val profile = LocalActiveProfile.current
    return remember(profile?.key) { container.schoolRepositoryFor(profile) }
}

/** Crée (ou retrouve) un ViewModel en lui donnant le conteneur de dépendances. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val application = LocalContext.current.applicationContext as Ecole2recteApplication
    return viewModel(key = key, factory = viewModelFactory { initializer { create(application.container) } })
}

class BadgesViewModel(repository: SchoolRepository) : LoadingViewModel<Badges>(repository::badges, repository::invalidate)

class HomeViewModel(repository: SchoolRepository) : LoadingViewModel<HomeData>(repository::home, repository::invalidate)

class ScheduleViewModel(repository: SchoolRepository) : LoadingViewModel<ScheduleWeek>(repository::schedule, repository::invalidate)

class HomeworkViewModel(private val repository: SchoolRepository) :
    LoadingViewModel<List<Homework>>(repository::homework, repository::invalidate) {
    /** Coche ou décoche un devoir : la liste se met à jour tout de suite, sans nouvel appel réseau. */
    fun toggleDone(task: Homework) {
        viewModelScope.launch {
            val written = repository.setHomeworkDone(task.id, !task.done)
            mutableNotice.value = if (written == fr.ecole2recte.app.domain.HomeworkWrite.SyncFailed) SYNC_FAILED else null
            refresh()
        }
    }

    private val mutableNotice = MutableStateFlow<String?>(null)

    /** Un message à montrer quand la case n'a pu être enregistrée que sur le téléphone. */
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()

    fun dismissNotice() {
        mutableNotice.value = null
    }

    companion object {
        const val SYNC_FAILED = "Coché sur ce téléphone, mais ÉcoleDirecte n'a pas pu l'enregistrer. Rien ne sera renvoyé tout seul."
    }

    suspend fun detail(task: Homework): HomeworkDetail = repository.homeworkDetail(task)

    suspend fun download(attachment: fr.ecole2recte.app.domain.Attachment) = repository.downloadAttachment(attachment)
}

class GradesViewModel private constructor(
    repository: SchoolRepository,
    private val period: java.util.concurrent.atomic.AtomicReference<String?>,
) : LoadingViewModel<GradeOverview>({ repository.grades(period.get()) }, repository::invalidate) {
    constructor(repository: SchoolRepository) : this(repository, java.util.concurrent.atomic.AtomicReference(null))

    /** Affiche une autre période du même bulletin : aucune nouvelle requête, la réponse est déjà là. */
    fun selectPeriod(code: String) {
        period.set(code)
        refresh()
    }
}

class MessagesViewModel(private val repository: SchoolRepository) :
    LoadingViewModel<List<MessagePreview>>(repository::messages, repository::invalidate) {
    private val mutableMore = MutableStateFlow<List<MessagePreview>>(emptyList())
    private val mutableHasMore = MutableStateFlow(true)
    private val mutableLoadingMore = MutableStateFlow(false)
    private val mutableRead = MutableStateFlow<Set<String>>(emptySet())

    /** Les pages suivantes, ajoutées à la première. */
    val more: StateFlow<List<MessagePreview>> = mutableMore.asStateFlow()
    val hasMore: StateFlow<Boolean> = mutableHasMore.asStateFlow()
    val loadingMore: StateFlow<Boolean> = mutableLoadingMore.asStateFlow()

    /** Messages ouverts pendant cette visite : lus tout de suite à l'écran, en attendant le serveur. */
    val readHere: StateFlow<Set<String>> = mutableRead.asStateFlow()

    private var nextPage = 1

    fun loadMore() {
        if (mutableLoadingMore.value || !mutableHasMore.value) return
        mutableLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = repository.messagesPage(nextPage)
                mutableMore.value = mutableMore.value + page.items
                mutableHasMore.value = page.hasMore
                nextPage++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Le bouton « Charger plus » reste disponible pour réessayer.
            } finally {
                mutableLoadingMore.value = false
            }
        }
    }

    suspend fun open(message: MessagePreview): MessageDetail {
        val detail = repository.messageDetail(message.id)
        mutableRead.value = mutableRead.value + message.id
        return detail
    }

    suspend fun download(attachment: fr.ecole2recte.app.domain.Attachment) = repository.downloadAttachment(attachment)
}
