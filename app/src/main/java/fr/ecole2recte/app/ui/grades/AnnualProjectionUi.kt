package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.ui.common.*
import fr.ecole2recte.app.ui.theme.AppTheme

@Composable
internal fun AnnualProjectionCard(overview: GradeOverview) {
    if (LocalPrivacy.current.hidesGrades) return
    val owner = LocalActiveProfile.current?.key ?: "demo"
    var remaining by rememberSaveable(owner, overview.periodCode) { mutableStateOf("3") }
    val digits = averageDigits()
    val annual = AnnualProjection.annual(AnnualProjection.periods(overview))
    fun number(value: Double?) = value?.let { FrenchFormat.decimal(it, digits) } ?: "indisponible"
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Moyenne annuelle · calculée par l'app", color = AppTheme.colors.ink)
            Text("${number(annual?.value)} / 20", color = AppTheme.colors.ink)
            if (annual != null) Text(
                (if (annual.weighted) "Périodes pondérées selon les coefficients fournis." else "Moyenne à plat : coefficients de période inconnus ou incomplets.") +
                    " ${annual.periodCount} période(s) avec une moyenne ; les périodes sans moyenne sont exclues.", color = AppTheme.colors.muted)
            Text("Projection de fin d'année · estimation", color = AppTheme.colors.ink)
            Text("Hypothèse : ce nombre de devoirs de coefficient 1 reste à venir dans chaque matière de ${overview.periodName.ifBlank { "la période affichée" }}. Les autres périodes connues restent inchangées ; aucune hypothèse sur les périodes sans moyenne.", color = AppTheme.colors.muted)
            DecimalField(remaining, { remaining = it }, "Évaluations restantes par matière (0 à 1 000)", Modifier.fillMaxWidth())
            val count = remaining.trim().toIntOrNull()
            val cases = count?.let { AnnualProjection.project(overview, it) }
            if (cases == null) Text("Choisis un nombre entier de 0 à 1 000. Une moyenne et des matières sont nécessaires.", color = AppTheme.colors.muted)
            else AnalysisFields(listOf(
                { ProjectionCase("Meilleur cas : 20 / 20", cases.best, digits, it) },
                { ProjectionCase("Pire cas : 0 / 20", cases.worst, digits, it) },
                { ProjectionCase("Cas probable : moyenne actuelle maintenue", cases.probable, digits, it) },
            ))
            Text("Ces cas illustrent des hypothèses ; ils ne prédisent pas les résultats futurs. L'annuelle porte sur les périodes disponibles.", color = AppTheme.colors.muted)
        }
    }
}

@Composable
private fun ProjectionCase(title: String, case: AnnualProjection.Case, digits: Int, modifier: Modifier) {
    fun number(value: Double?) = value?.let { FrenchFormat.decimal(it, digits) } ?: "non estimable"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = AppTheme.colors.ink)
        Text("Période : ${number(case.period)} / 20 (estimation)", color = AppTheme.colors.ink)
        Text("Annuelle : ${number(case.annual?.value)} / 20 (estimation)", color = AppTheme.colors.ink)
    }
}
