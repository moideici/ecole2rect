package fr.ecole2recte.app.ui.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.ecole2recte.app.domain.Subject
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.LocalSubjectColorOverrides
import fr.ecole2recte.app.ui.theme.subjectColor
import fr.ecole2recte.app.ui.theme.subjectKey

/**
 * Choix de la couleur de chaque matière. Une couleur choisie suit la matière partout (accueil,
 * agenda, devoirs, notes) et reste propre au compte, ou à l'élève, affiché.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorEditor(subjects: List<Subject>, ownerKey: String, onDismiss: () -> Unit) {
    val store = rememberAppContainer().subjectColors
    val overrides = LocalSubjectColorOverrides.current
    val palette = AppTheme.colors.subject
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(AppTheme.colors.surface)) {
            Column(Modifier.padding(24.dp)) {
                Text("Couleurs", color = AppTheme.colors.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.padding(top = 12.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (subjects.isEmpty()) {
                        Text("Aucune matière pour l'instant.", color = AppTheme.colors.muted, fontSize = 14.sp)
                    }
                    subjects.forEach { subject ->
                        val chosen = overrides[subjectKey(subject.name)]
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(12.dp).background(subjectColor(subject.name, subject.colorIndex), CircleShape),
                                )
                                Text(subject.name, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                            }
                            FlowRow(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                palette.forEachIndexed { index, color ->
                                    val selected = (chosen ?: subject.colorIndex) == index
                                    androidx.compose.foundation.layout.Box(
                                        Modifier
                                            .size(34.dp)
                                            .background(color, CircleShape)
                                            .then(if (selected) Modifier.border(3.dp, AppTheme.colors.ink, CircleShape) else Modifier)
                                            .semantics { contentDescription = "Couleur ${index + 1}" + if (selected) ", choisie" else "" }
                                            .clickable { store.set(ownerKey, subjectKey(subject.name), index) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            if (chosen != null) {
                                Text(
                                    "Couleur d'origine",
                                    color = AppTheme.colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { store.set(ownerKey, subjectKey(subject.name), null) }.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    "Fermer",
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 7.dp).clickable(onClick = onDismiss).padding(8.dp),
                )
            }
        }
    }
}
