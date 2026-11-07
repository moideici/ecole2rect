package fr.ecole2recte.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import fr.ecole2recte.app.data.AdviceAction
import fr.ecole2recte.app.data.FailureAdvice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.Subject
import kotlinx.coroutines.flow.SharedFlow
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.Dimens
import fr.ecole2recte.app.ui.theme.subjectColor

/** Ce que les boutons d'un message d'erreur ou du bandeau d'ancienneté déclenchent ; fourni par l'écran principal. */
class AdviceActions(val reconnect: () -> Unit = {}, val openDiagnostic: () -> Unit = {})

val LocalAdviceActions = staticCompositionLocalOf { AdviceActions() }

/**
 * Bandeau d'ancienneté, en haut des écrans de données quand la lecture vient de l'enregistrement
 * (hors ligne, compte bloqué, service en panne…) : « Hors ligne · Données du 5 nov. à 18 h (il y a 3 h) »,
 * et l'action utile quand il y en a une.
 */
@Composable
fun FreshnessBanner(freshness: fr.ecole2recte.app.domain.Freshness, onAction: (AdviceAction) -> Unit) {
    val advice = remember(freshness) { FailureAdvice.banner(freshness, System.currentTimeMillis()) }
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.warningSurface).padding(horizontal = 18.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            advice.title,
            modifier = Modifier.weight(1f),
            color = AppTheme.colors.warningTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        if (advice.action != AdviceAction.None) {
            Text(
                advice.action.label,
                modifier = Modifier.clickable { onAction(advice.action) }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                color = AppTheme.colors.warningTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Bandeau discret affiché quand la reconnexion à ÉcoleDirecte a échoué. */
@Composable
fun ConnectionBanner(message: String?, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.ink).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message ?: "Hors ligne",
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            "Réessayer",
            modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

/**
 * Carte de liste standard : même marge, même arrondi, même couleur partout.
 * [padded] retire la marge extérieure quand la carte est déjà dans une ligne qui la porte.
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outer = modifier.fillMaxWidth().let {
        if (padded) it.padding(horizontal = Dimens.screenPadding, vertical = 5.dp) else it
    }
    val colors = CardDefaults.cardColors(AppTheme.colors.surface)
    val shape = RoundedCornerShape(Dimens.cardRadius)
    if (onClick == null) Card(outer, colors = colors, shape = shape, content = content)
    else Card(onClick, outer, colors = colors, shape = shape, content = content)
}

/** Pastille de couleur d'une matière. */
@Composable
fun SubjectDot(name: String, colorIndex: Int, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(subjectColor(name, colorIndex), RoundedCornerShape(size / 2)))
}

/** Écran ou section sans contenu. */
@Composable
fun EmptyState(title: String, message: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = AppTheme.colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (message.isNotBlank()) Text(message, color = AppTheme.colors.muted, fontSize = 14.sp)
    }
}

/** Affiche le chargement, l'erreur (précise, avec l'action utile) ou le contenu d'un [UiState]. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    /** Messages brefs (« déjà à jour ») affichés en bas quand un tirage est refusé sans requête. */
    notices: SharedFlow<String>? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> SkeletonPage()
        is UiState.Error -> ErrorPanel(state, onRetry)
        is UiState.Content -> {
            var notice by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(notices) {
                notices?.collectLatest {
                    notice = it
                    delay(NOTICE_MS)
                    notice = null
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (onRefresh == null) {
                    content(state.value)
                } else {
                    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        content(state.value)
                    }
                }
                notice?.let { text ->
                    Text(
                        text,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 14.dp)
                            .background(AppTheme.colors.inverseSurface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private const val NOTICE_MS = 3_500L

@Composable
private fun ErrorPanel(error: UiState.Error, onRetry: () -> Unit) {
    val actions = LocalAdviceActions.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        error.title?.let { Text(it, color = AppTheme.colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Text(error.message, color = AppTheme.colors.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        val primary: (() -> Unit)? = when (error.action) {
            AdviceAction.Retry -> onRetry
            AdviceAction.Reconnect -> actions.reconnect
            AdviceAction.OpenDiagnostic -> actions.openDiagnostic
            AdviceAction.None -> null
        }
        if (primary != null) {
            Text(
                error.action.label,
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = primary).padding(8.dp),
                color = AppTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        // Un compte bloqué ne se relance pas : insister aggraverait les choses.
        if (error.action != AdviceAction.Retry && error.action != AdviceAction.None) {
            Text(
                AdviceAction.Retry.label,
                modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
                color = AppTheme.colors.muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, color = AppTheme.colors.ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = AppTheme.colors.muted, fontSize = 12.sp)
        }
        action?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = AppTheme.colors.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action.isNotBlank()) Text(action, modifier = Modifier.clickable(onClick = onClick).padding(8.dp), color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SubjectChip(subject: Subject) {
    Box(Modifier.size(56.dp).background(Color.White.copy(alpha = .85f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
        Icon(subjectIcon(subject.name), contentDescription = null, tint = subjectColor(subject.name, subject.colorIndex), modifier = Modifier.size(30.dp))
    }
}

/** Signal « l'utilisateur a retouché l'onglet déjà ouvert » : les listes remontent en haut. */
val LocalScrollEvents = staticCompositionLocalOf<SharedFlow<String>?> { null }

@Composable
fun ScrollToTopOnReselect(route: String, state: LazyListState) {
    val events = LocalScrollEvents.current ?: return
    LaunchedEffect(events, route) {
        events.collect { if (it == route) state.animateScrollToItem(0) }
    }
}

/** Page à défilement : un en-tête puis une liste de cartes. */
@Composable
fun PageScaffold(route: String, title: String, subtitle: String = "", content: @Composable () -> Unit) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(route, listState)
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader(title, subtitle) }
        item { Column { content() } }
    }
}

/** Petit bouton « ? » qui ouvre une explication détaillée : le texte long n'encombre pas l'écran. */
@Composable
fun HelpButton(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Text(
        "?",
        modifier = modifier.clickable { open = true }.padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "En savoir plus : $title" },
        color = AppTheme.colors.accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Fermer") } },
        )
    }
}
