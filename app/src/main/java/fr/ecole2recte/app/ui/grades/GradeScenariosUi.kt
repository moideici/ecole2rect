package fr.ecole2recte.app.ui.grades

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.ui.common.*
import fr.ecole2recte.app.ui.theme.AppTheme
import java.util.UUID

/** Les champs s'empilent sur téléphone et avec une grande police, sans largeur fixe. */
@Composable
internal fun AnalysisFields(fields: List<@Composable (Modifier) -> Unit>) {
    val configuration = LocalConfiguration.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout = AnalysisLayout.choose(maxWidth.value.toInt(), configuration.screenHeightDp,
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, configuration.fontScale)
        if (layout == AnalysisLayout.Stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { fields.forEach { it(Modifier.fillMaxWidth()) } }
        } else {
            val columns = if (layout == AnalysisLayout.Wide) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.chunked(columns).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { group.forEach { it(Modifier.weight(1f)) } }
                }
            }
        }
    }
}

@Composable
internal fun DecimalField(value: String, change: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(value, change, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

@Composable
internal fun GradeScenariosPanel(overview: GradeOverview, line: SubjectAverage) {
    if (LocalPrivacy.current.hidesGrades) return
    val owner = LocalActiveProfile.current?.key ?: "demo"
    val store = rememberAppContainer().gradeScenarios
    val period = overview.periodCode
    val digits = averageDigits()
    // Un nouveau profil ou une nouvelle période ne réutilise jamais le brouillon précédent.
    key(owner, period) {
        var edits by remember { mutableStateOf(emptyList<ScenarioEdit>()) }
        var saved by remember { mutableStateOf(store.list(owner, period)) }
        var name by remember { mutableStateOf("") }
        var savedId by remember { mutableStateOf<String?>(null) }
        var editing by remember { mutableStateOf<Int?>(null) }
        var realId by remember(line.id) { mutableStateOf<String?>(null) }
        var note by remember(line.id) { mutableStateOf("") }
        var scale by remember(line.id) { mutableStateOf("20") }
        var coefficient by remember(line.id) { mutableStateOf("1") }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(line.id) { editing = null; realId = null }
        fun resetEditor() { editing = null; realId = null; note = ""; scale = "20"; coefficient = "1" }
        val result = GradeScenario.project(overview, edits)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scénarios sauvegardés · estimation", color = AppTheme.colors.ink)
            Text("Ajoute plusieurs devoirs ou essaie une modification d'une note réelle. Ces changements restent sur le téléphone.", color = AppTheme.colors.muted)
            AnalysisFields(listOf(
                { DecimalField(note, { note = it }, "Note hypothétique", it) },
                { DecimalField(scale, { scale = it }, "Barème", it) },
                { DecimalField(coefficient, { coefficient = it }, "Coefficient", it) },
            ))
            if (realId != null) Text("Remplacement de la note : ${line.grades.firstOrNull { it.id == realId }?.label.orEmpty()}", color = AppTheme.colors.muted)
            Button(onClick = {
                val edit = GradeScenario.edit(line.id, realId, note, scale, coefficient)
                if (edit == null) error = "Saisis une note dans son barème et un coefficient supérieur à 0."
                else {
                    val index = editing
                    edits = if (index != null) edits.mapIndexed { i, old -> if (i == index) edit else old }
                    else edits.filterNot { realId != null && it.subjectId == line.id && it.gradeId == realId } + edit
                    error = null
                    resetEditor()
                }
            }) { Text(if (editing != null || realId != null) "Appliquer la modification" else "Ajouter un devoir hypothétique") }
            if (editing != null || realId != null) TextButton(onClick = { resetEditor() }) { Text("Annuler la saisie") }
            line.grades.filter { it.counted && it.value != null && it.coefficient > 0 }.forEach { grade ->
                ListCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("${grade.label.ifBlank { "Évaluation" }} · ${FrenchFormat.decimal(grade.value!!, digits)} / ${FrenchFormat.decimal(grade.scale, digits)}", color = AppTheme.colors.ink)
                        TextButton(onClick = {
                            editing = edits.indexOfFirst { it.subjectId == line.id && it.gradeId == grade.id }.takeIf { it >= 0 }
                            realId = grade.id; note = grade.value.toString(); scale = grade.scale.toString(); coefficient = grade.coefficient.toString()
                        }) { Text("Modifier dans le scénario") }
                        TextButton(onClick = {
                            edits = edits.filterNot { it.subjectId == line.id && it.gradeId == grade.id } + ScenarioEdit(line.id, grade.id, removed = true)
                            resetEditor()
                        }) { Text("Retirer dans le scénario") }
                    }
                }
            }
            edits.forEachIndexed { index, edit ->
                val subject = overview.bySubject.firstOrNull { it.id == edit.subjectId }
                ListCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("${subject?.subject?.name ?: "Matière indisponible"} · " + when {
                            edit.removed -> "retrait de ${subject?.grades?.firstOrNull { it.id == edit.gradeId }?.label ?: "note réelle"}"
                            else -> "${FrenchFormat.decimal(edit.value ?: 0.0, digits)} / ${FrenchFormat.decimal(edit.scale, digits)} · coef. ${FrenchFormat.decimal(edit.coefficient, digits)}"
                        }, color = AppTheme.colors.ink)
                        if (!edit.removed && edit.subjectId == line.id) TextButton(onClick = {
                            editing = index; realId = edit.gradeId; note = edit.value?.toString().orEmpty(); scale = edit.scale.toString(); coefficient = edit.coefficient.toString()
                        }) { Text("Modifier cette hypothèse") }
                        TextButton(onClick = { edits = edits.filterIndexed { i, _ -> i != index }; resetEditor() }) { Text("Enlever cette modification") }
                    }
                }
            }
            if (edits.isNotEmpty()) when (result) {
                is ScenarioResult.Invalid -> Text(result.reason, color = AppTheme.colors.error)
                is ScenarioResult.Value -> {
                    edits.map { it.subjectId }.distinct().forEach { id ->
                        val subject = overview.bySubject.first { it.id == id }
                        Text("${subject.subject.name} : ${subject.average} → ${result.subjects[id]?.let { FrenchFormat.decimal(it, digits) } ?: "indisponible"} / 20 (estimation)", color = AppTheme.colors.ink)
                    }
                    Text("Moyenne générale : ${overview.average} → ${result.general?.let { FrenchFormat.decimal(it, digits) } ?: "non estimable avec ces données"} (estimation)", color = AppTheme.colors.ink)
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nom du scénario") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = name.isNotBlank() && edits.isNotEmpty() && result is ScenarioResult.Value, onClick = {
                runCatching {
                    val id = savedId ?: UUID.randomUUID().toString()
                    store.save(owner, period, SavedGradeScenario(id, name, edits)); savedId = id
                    saved = store.list(owner, period)
                }.onFailure { error = "Impossible d'enregistrer le scénario sur ce téléphone." }
            }) { Text("Sauvegarder le scénario") }
            TextButton(onClick = { edits = emptyList(); name = ""; savedId = null; error = null; resetEditor() }) { Text("Nouveau scénario") }
            saved.forEach { scenario ->
                ListCard {
                    Column(Modifier.padding(12.dp)) {
                        Text(scenario.name, color = AppTheme.colors.ink)
                        TextButton(onClick = { edits = scenario.edits; name = scenario.name; savedId = scenario.id; error = null; resetEditor() }) { Text("Charger ce scénario") }
                        TextButton(onClick = {
                            runCatching {
                                store.delete(owner, period, scenario.id); saved = store.list(owner, period)
                                if (savedId == scenario.id) savedId = null
                            }.onFailure { error = "Impossible de supprimer le scénario sur ce téléphone." }
                        }) { Text("Supprimer ce scénario") }
                    }
                }
            }
            error?.let { Text(it, color = AppTheme.colors.error) }
            Text("Estimation à partir des données visibles ; aucune note réelle n'est modifiée.", color = AppTheme.colors.muted)
        }
    }
}
