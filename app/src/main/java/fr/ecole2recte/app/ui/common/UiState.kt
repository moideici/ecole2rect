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
abstract class LoadingViewModel<T>(private val load: suspend () -> T) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = mutableState.asStateFlow()

    init {
        refresh()
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
