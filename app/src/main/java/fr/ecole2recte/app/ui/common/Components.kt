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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Carte de liste standard : même marge, même arrondi, même couleur partout. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 5.dp),
        colors = CardDefaults.cardColors(AppTheme.colors.surface),
        shape = RoundedCornerShape(Dimens.cardRadius),
        content = content,
    )
}

/** Pastille de couleur d'une matière. */
@Composable
fun SubjectDot(colorIndex: Int, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(AppTheme.colors.subject[colorIndex], RoundedCornerShape(size / 2)))
}

/** Écran ou section sans contenu. */
@Composable
fun EmptyState(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = AppTheme.colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(message, color = AppTheme.colors.muted, fontSize = 14.sp)
    }
}

/** Affiche le chargement, l'erreur (avec « Réessayer ») ou le contenu d'un [UiState]. */
@Composable
fun <T> StateHost(state: UiState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Loading -> SkeletonPage()
        is UiState.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message, color = AppTheme.colors.ink, fontSize = 14.sp)
            Text(
                "Réessayer",
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRetry).padding(8.dp),
                color = AppTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        is UiState.Content -> content(state.value)
    }
}

@Composable
fun PageHeader(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("ecole2recte", color = AppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(7.dp))
            Text(title, color = AppTheme.colors.ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(subtitle, color = AppTheme.colors.muted, fontSize = 12.sp)
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
        Text(action, modifier = Modifier.clickable(onClick = onClick).padding(8.dp), color = AppTheme.colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SubjectChip(subject: Subject) {
    Box(Modifier.size(56.dp).background(Color.White.copy(alpha = .85f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
        Text(subject.name.take(2), color = AppTheme.colors.subject[subject.colorIndex], fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
fun PageScaffold(route: String, title: String, subtitle: String, content: @Composable () -> Unit) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(route, listState)
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader(title, subtitle) }
        item { Column { content() } }
    }
}
