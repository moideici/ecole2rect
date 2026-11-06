package fr.ecole2recte.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ecole2recte.app.data.Advice
import fr.ecole2recte.app.data.AdviceAction
import fr.ecole2recte.app.data.FailureAdvice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État d'un écran qui charge une donnée : chargement, contenu ou erreur. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val value: T) : UiState<T>

    /** Une erreur précise : un titre court, une explication et l'action utile (réessayer, reconnecter, diagnostic). */
    data class Error(
        val message: String,
        val title: String? = null,
        val action: AdviceAction = AdviceAction.Retry,
    ) : UiState<Nothing> {
        companion object {
            fun from(advice: Advice) = Error(advice.detail, advice.title, advice.action)
            fun from(error: Throwable) = from(FailureAdvice.forThrowable(error))
        }
    }
}

/** ViewModel de base pour un écran alimenté par un seul chargement relançable. */
abstract class LoadingViewModel<T>(
    private val load: suspend () -> T,
    /** Oublie la mémoire à court terme de la source avant un rechargement demandé par l'utilisateur. */
    private val invalidate: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = mutableState.asStateFlow()

    /** Quand les données affichées ont été lues pour la dernière fois (0 : jamais). */
    private var loadedAt = 0L

    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

    private val mutableNotices = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Petits messages à montrer un instant (« déjà à jour ») quand un tirage est refusé sans requête. */
    val notices: SharedFlow<String> = mutableNotices.asSharedFlow()

    init {
        refresh()
    }

    /**
     * Relit les données si elles ont plus de [thresholdMs] (dix minutes par défaut). Jamais en
     * boucle : appelé au retour au premier plan et à la réouverture de l'écran.
     */
    fun refreshIfStale(thresholdMs: Long = STALE_AFTER_MS, now: Long = System.currentTimeMillis()) {
        if (loadedAt != 0L && now - loadedAt > thresholdMs && !mutableRefreshing.value) pullToRefresh(now)
    }

    /** « Réessayer » après une erreur : interroge de nouveau la source. */
    fun retry() {
        invalidate()
        mutableState.value = UiState.Loading
        refresh()
    }

    /**
     * Tirer pour rafraîchir : le contenu reste affiché ; s'il échoue, on garde ce qu'on montrait.
     * Un tirage moins de 90 s après la dernière lecture n'émet aucune requête et le dit gentiment.
     */
    fun pullToRefresh(now: Long = System.currentTimeMillis()) {
        if (mutableRefreshing.value) return
        if (RefreshGate.isTooSoon(loadedAt, now)) {
            mutableNotices.tryEmit(RefreshGate.tooSoonMessage(loadedAt, now))
            return
        }
        mutableRefreshing.value = true
        invalidate()
        viewModelScope.launch {
            try {
                mutableState.value = UiState.Content(load())
                loadedAt = System.currentTimeMillis()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutableState.value !is UiState.Content) {
                    mutableState.value = UiState.Error.from(error)
                }
            } finally {
                mutableRefreshing.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = try {
                UiState.Content(load()).also { loadedAt = System.currentTimeMillis() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                UiState.Error.from(error)
            }
        }
    }
}

/** Âge à partir duquel un écran se relit au retour au premier plan. */
const val STALE_AFTER_MS = 10 * 60 * 1000L

/** Délai minimal entre deux lectures demandées par l'utilisateur : le même que la mémoire du dépôt. */
const val MIN_REFRESH_GAP_MS = 90_000L

/** Décide si un « tirer pour rafraîchir » doit vraiment relire, ou répondre « déjà à jour ». */
object RefreshGate {
    fun isTooSoon(loadedAt: Long, now: Long, minGapMs: Long = MIN_REFRESH_GAP_MS): Boolean =
        loadedAt != 0L && now - loadedAt in 0 until minGapMs

    fun tooSoonMessage(loadedAt: Long, now: Long, minGapMs: Long = MIN_REFRESH_GAP_MS): String {
        val age = ((now - loadedAt) / 1000).coerceAtLeast(0)
        val remaining = (((minGapMs - (now - loadedAt)) + 999) / 1000).coerceAtLeast(1)
        return "Déjà à jour : lu il y a $age s. Réessaie dans $remaining s, pour ne pas solliciter ÉcoleDirecte pour rien."
    }
}
