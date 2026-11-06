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
import fr.ecole2recte.app.domain.SubjectAverage
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Carte « Simulateur » de la page Notes : on choisit une matière, puis « quelle note pour atteindre X ? »
 * ou « et si j'ai Y ? ». Repliée par défaut.
 */
@Composable
fun SimulatorCard(subjects: List<SubjectAverage>, modifier: Modifier = Modifier) {
    val usable = remember(subjects) { subjects.filter { line -> line.grades.any { it.counted && it.value != null } } }
    if (usable.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf(usable.first().subject.name) }
    val line = usable.firstOrNull { it.subject.name == chosen } ?: usable.first()
    ListCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Simulateur", color = AppTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Quelle note pour atteindre une moyenne ?", color = AppTheme.colors.muted, fontSize = 13.sp)
                }
                Text(if (open) "Replier" else "Ouvrir", color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (open) {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(usable, key = { it.subject.name }) { option ->
                        FilterChip(
                            selected = option.subject.name == line.subject.name,
                            onClick = { chosen = option.subject.name },
                            label = { Text(option.subject.name, fontSize = 13.sp) },
                        )
                    }
                }
                SimulatorPanel(line.grades, line.subject.name)
            }
        }
    }
}

/** Les deux questions du simulateur pour une matière : l'objectif visé, ou la note hypothétique. */
@Composable
fun SimulatorPanel(grades: List<GradeEntry>, subject: String, modifier: Modifier = Modifier) {
    var hypothesis by rememberSaveable(subject) { mutableStateOf(false) }
    var target by rememberSaveable(subject) { mutableStateOf(GradeSimulation.defaultTarget(grades)) }
    var coefficient by rememberSaveable(subject) { mutableStateOf("1") }
    var guess by rememberSaveable(subject) { mutableStateOf("") }

    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!hypothesis, { hypothesis = false }, label = { Text("Pour atteindre…", fontSize = 13.sp) })
            FilterChip(hypothesis, { hypothesis = true }, label = { Text("Si j'obtiens…", fontSize = 13.sp) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hypothesis) {
                OutlinedTextField(
                    guess, { guess = it }, label = { Text("Note sur 20") }, singleLine = true,
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
        val text: String
        val tint = if (hypothesis) {
            val projection = GradeSimulation.projection(grades, guess, coefficient)
            text = GradeSimulation.message(projection)
            when (projection) {
                is GradeSimulation.Projection.Invalid -> AppTheme.colors.muted
                is GradeSimulation.Projection.Value -> AppTheme.colors.ink
            }
        } else {
            val need = GradeSimulation.need(grades, target, coefficient)
            text = when (need) {
                GradeSimulation.Need.Achieved -> "✓ "
                is GradeSimulation.Need.Unreachable -> "✗ "
                else -> ""
            } + GradeSimulation.message(need)
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
        Text(
            "Estimation fondée sur les notes que l'application voit en $subject pour la période affichée, pas sur la moyenne officielle.",
            color = AppTheme.colors.muted,
            fontSize = 12.sp,
        )
    }
}
