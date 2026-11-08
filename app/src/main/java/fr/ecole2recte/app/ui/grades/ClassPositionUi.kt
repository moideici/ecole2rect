package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.ui.common.LocalPrivacy
import fr.ecole2recte.app.ui.common.averageDigits
import fr.ecole2recte.app.ui.theme.AppTheme

/** Histogramme horizontal sur une échelle commune 0–20, texte extensible et sans troncature. */
@Composable
internal fun ClassPositionChart(title: String, position: ClassPosition?, mine: String, modifier: Modifier = Modifier) {
    if (LocalPrivacy.current.hidesGrades || position == null) return
    val value = GradeSimulation.parse(mine)
    val bars = position.bars(value)
    val rank = position.rankLabel()
    // La seule barre « moi » n'est pas une comparaison.
    if (rank == null && position.sizeLabel() == null && bars.none { it.label != "Moi" }) return
    val digits = averageDigits()
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = position.describe(title, value, digits) },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Position dans la classe · $title", color = AppTheme.colors.ink)
        rank?.let { Text(it, color = AppTheme.colors.ink) }
        if (rank == null) position.sizeLabel()?.let { Text(it, color = AppTheme.colors.ink) }
        bars.forEach { bar ->
            Column(Modifier.fillMaxWidth()) {
                Text("${bar.label} : ${FrenchFormat.decimal(bar.value, digits)} / 20", color = AppTheme.colors.ink)
                LinearProgressIndicator(progress = { bar.fraction }, modifier = Modifier.fillMaxWidth(),
                    color = if (bar.label == "Moi") AppTheme.colors.accent else AppTheme.colors.muted)
            }
        }
        Text("Statistiques fournies par l'établissement. Le rang n'est pas estimé.", color = AppTheme.colors.muted)
    }
}
