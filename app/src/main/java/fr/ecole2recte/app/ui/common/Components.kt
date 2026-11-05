package fr.ecole2recte.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.theme.Background
import fr.ecole2recte.app.ui.theme.Indigo
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.Muted
import fr.ecole2recte.app.ui.theme.SubjectColors

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Text("ecole2recte", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** Affiche le chargement, l'erreur (avec « Réessayer ») ou le contenu d'un [UiState]. */
@Composable
fun <T> StateHost(state: UiState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Indigo)
        }
        is UiState.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message, color = Ink, fontSize = 14.sp)
            Text(
                "Réessayer",
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRetry).padding(8.dp),
                color = Indigo,
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
            Text("ecole2recte", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(7.dp))
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
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
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(action, modifier = Modifier.clickable(onClick = onClick), color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SubjectChip(subject: Subject) {
    Box(Modifier.size(56.dp).background(Color.White.copy(alpha = .85f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
        Text(subject.name.take(2), color = SubjectColors[subject.colorIndex], fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Page à défilement : un en-tête puis une liste de cartes. */
@Composable
fun PageScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader(title, subtitle) }
        item { Column { content() } }
    }
}
