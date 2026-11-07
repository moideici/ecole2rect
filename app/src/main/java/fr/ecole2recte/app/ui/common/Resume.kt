package fr.ecole2recte.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf

/** Compteur incrémenté chaque fois que l'application revient au premier plan. */
val LocalResumeTick = compositionLocalOf { 0 }

/**
 * Rafraîchit l'écran quand l'application revient au premier plan **ou** quand l'écran est rouvert,
 * mais seulement si ses données ont plus de dix minutes ([LoadingViewModel.refreshIfStale]) : ni
 * rafale de requêtes, ni données du matin affichées le soir.
 */
@Composable
fun RefreshOnResume(viewModel: LoadingViewModel<*>) {
    val tick = LocalResumeTick.current
    LaunchedEffect(tick) { viewModel.refreshIfStale() }
}
