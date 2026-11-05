package fr.ecole2recte.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État d'un écran qui charge une donnée : chargement, contenu ou erreur. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val value: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** ViewModel de base pour un écran alimenté par un seul chargement relançable. */
abstract class LoadingViewModel<T>(
    private val load: suspend () -> T,
    /** Oublie la mémoire à court terme de la source avant un rechargement demandé par l'utilisateur. */
    private val invalidate: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = mutableState.asStateFlow()

    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

    init {
        refresh()
    }

    /** « Réessayer » après une erreur : interroge de nouveau la source. */
    fun retry() {
        invalidate()
        mutableState.value = UiState.Loading
        refresh()
    }

    /** Tirer pour rafraîchir : le contenu reste affiché ; s'il échoue, on garde ce qu'on montrait. */
    fun pullToRefresh() {
        if (mutableRefreshing.value) return
        mutableRefreshing.value = true
        invalidate()
        viewModelScope.launch {
            try {
                mutableState.value = UiState.Content(load())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutableState.value !is UiState.Content) {
                    mutableState.value = UiState.Error(error.message ?: "Chargement impossible. Réessaie dans un instant.")
                }
            } finally {
                mutableRefreshing.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = try {
                UiState.Content(load())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                UiState.Error(error.message ?: "Chargement impossible. Réessaie dans un instant.")
            }
        }
    }
}
