package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.AverageMethod
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.theme.AppTheme

/** « Comment la moyenne est calculée ? » : le texte de `docs/MOYENNES.md`, lisible dans l'application. */
@Composable
fun AverageMethodScreen(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // La marge à droite laisse la place au bouton de compte, posé par-dessus l'écran.
        Row(Modifier.padding(start = 6.dp, end = 74.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour", tint = AppTheme.colors.ink)
            }
            Column {
                Text("ecole2recte", color = AppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Comment la moyenne est calculée",
                    color = AppTheme.colors.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            AverageMethod.sections.forEach { section ->
                item(key = section.title) {
                    Text(
                        section.title,
                        color = AppTheme.colors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 6.dp).semantics { heading() },
                    )
                    ListCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            section.paragraphs.forEach { paragraph ->
                                val bullet = paragraph.startsWith("- ")
                                val text = paragraph.removePrefix("- ")
                                // Une étape déjà numérotée (« 1. ») garde son numéro ; les autres puces prennent un point.
                                val numbered = bullet && text.firstOrNull()?.isDigit() == true
                                Text(
                                    if (bullet && !numbered) "•  $text" else text,
                                    color = AppTheme.colors.ink,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
