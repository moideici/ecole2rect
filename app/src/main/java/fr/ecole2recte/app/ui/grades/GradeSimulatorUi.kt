package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeSimulation
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.AverageEngine
import fr.ecole2recte.app.domain.FrenchFormat
import fr.ecole2recte.app.ui.common.averageDigits
import fr.ecole2recte.app.domain.SubjectAverage
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Carte « Simulateur » de la page Notes : on choisit une matière, puis « quelle note pour atteindre X ? »
 * ou « et si j'ai Y ? ». Repliée par défaut.
 */
@Composable
fun SimulatorCard(overview: GradeOverview, modifier: Modifier = Modifier) {
    val subjects = overview.bySubject
    val usable = remember(subjects) { subjects.filter { line -> line.grades.any { it.counted && it.value != null } } }
    if (usable.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf(usable.first().id) }
    val line = usable.firstOrNull { it.id == chosen } ?: usable.first()
    ListCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Simulateur", color = AppTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(if (open) "Replier" else "Ouvrir", color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (open) {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(usable, key = { it.id }) { option ->
                        FilterChip(
                            selected = option.id == line.id,
                            onClick = { chosen = option.id },
                            label = { Text(option.subject.name, fontSize = 13.sp) },
                        )
                    }
                }
                SimulatorPanel(line, overview)
            }
        }
    }
}

/** Les deux questions du simulateur pour une matière : l'objectif visé, ou la note hypothétique. */
@Composable
fun SimulatorPanel(line: SubjectAverage, overview: GradeOverview, modifier: Modifier = Modifier) {
    val grades = line.grades
    val subject = line.id
    // Mode « montrer à quelqu'un » : le simulateur calcule avec des notes, il reste donc caché.
    if (fr.ecole2recte.app.ui.common.LocalPrivacy.current.hidesGrades) return
    var hypothesis by rememberSaveable(subject) { mutableStateOf(false) }
    var target by rememberSaveable(subject) { mutableStateOf(GradeSimulation.defaultTarget(grades, GradeSimulation.parse(line.average))) }
    var coefficient by rememberSaveable(subject) { mutableStateOf("1") }
    var guess by rememberSaveable(subject) { mutableStateOf("") }
    var scale by rememberSaveable(subject) { mutableStateOf("20") }
    val digits = averageDigits()
    val displayed = GradeSimulation.parse(line.average)

    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Avant : ${line.average} / 20", color = AppTheme.colors.muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!hypothesis, { hypothesis = false }, label = { Text("Pour atteindre…", fontSize = 13.sp) })
            FilterChip(hypothesis, { hypothesis = true }, label = { Text("Si j'obtiens…", fontSize = 13.sp) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hypothesis) {
                OutlinedTextField(
                    guess, { guess = it }, label = { Text("Note") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            } else {
                OutlinedTextField(
                    target, { target = it }, label = { Text("Moyenne visée") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                coefficient, { coefficient = it }, label = { Text("Coefficient") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(scale, { scale = it }, label = { Text("Barème") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        val text: String
        val need = GradeSimulation.need(grades, target, coefficient, scale, displayed)
        val projection = GradeSimulation.projection(grades, if (hypothesis) guess else (need as? GradeSimulation.Need.Needed)?.grade?.toString().orEmpty(), coefficient, scale, displayed)
        val tint = if (hypothesis) {
            text = GradeSimulation.message(projection, digits)
            when (projection) {
                is GradeSimulation.Projection.Invalid -> AppTheme.colors.muted
                is GradeSimulation.Projection.Value -> AppTheme.colors.ink
            }
        } else {
            text = when (need) {
                GradeSimulation.Need.Achieved -> "✓ "
                is GradeSimulation.Need.Unreachable -> "✗ "
                else -> ""
            } + GradeSimulation.message(need, GradeSimulation.parse(scale) ?: 20.0, digits)
            when (need) {
                GradeSimulation.Need.Achieved -> AppTheme.colors.positive
                is GradeSimulation.Need.Unreachable -> AppTheme.colors.error
                is GradeSimulation.Need.Invalid -> AppTheme.colors.muted
                is GradeSimulation.Need.Needed -> AppTheme.colors.ink
            }
        }
        Text(
            text,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(tint.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(12.dp),
        )
        if (projection is GradeSimulation.Projection.Value) {
            val weights = overview.bySubject.map { AverageEngine.SubjectWeight(it.id, GradeSimulation.parse(it.average), it.coefficient, it.isSubSubject) }
            val general = AverageEngine.projectGeneral(weights, line.id, projection.projected, GradeSimulation.parse(overview.average), overview.averageSource.isOfficial)
            general?.let {
                Text("Moyenne générale : ${overview.average} → ${FrenchFormat.decimal(it.value, digits)}" + if (it.estimated) " (estimation)" else " (calculée par l'app)", color = AppTheme.colors.ink, fontSize = 13.sp)
            }
        }
        Text(
            "Estimation, pas la moyenne officielle.",
            color = AppTheme.colors.muted,
            fontSize = 12.sp,
        )
    }
}
