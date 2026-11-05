package fr.ecole2recte.app.ui.grades

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.domain.GradeMath
import fr.ecole2recte.app.domain.GradeOverview
import fr.ecole2recte.app.domain.PeriodAverage
import fr.ecole2recte.app.domain.SubjectAverage
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.GradesViewModel
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.PageHeader
import fr.ecole2recte.app.ui.common.ScrollToTopOnReselect
import fr.ecole2recte.app.ui.common.SubjectBadge
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.rememberAnimatedDecimal
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.ecole2recte.app.ui.theme.subjectColor

@Composable
fun GradesRoute() {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { GradesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh) { grades ->
        GradesContent(grades)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradesContent(grades: GradeOverview) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect("notes", listState)
    var opened by remember { mutableStateOf<GradeEntry?>(null) }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Notes", grades.periodName.ifBlank { "Matière par matière" }) }
        item { AverageCard(grades, Modifier.entrance(0)) }
        if (grades.recent.isNotEmpty()) {
            item { SectionLabel("Dernières notes") }
            item {
                Column {
                    grades.recent.forEachIndexed { index, grade ->
                        GradeRow(grade, showSubject = true, Modifier.entrance(index + 1)) { opened = grade }
                    }
                }
            }
        }
        item { SectionLabel("Par matière") }
        if (grades.bySubject.isEmpty()) item { EmptyState("Pas encore de notes", "Tes notes apparaîtront ici dès qu'elles seront saisies.") }
        item {
            Column {
                grades.bySubject.forEachIndexed { index, line -> SubjectCard(line, Modifier.entrance(index)) { opened = it } }
            }
        }
    }
    opened?.let { grade ->
        ModalBottomSheet(onDismissRequest = { opened = null }, containerColor = AppTheme.colors.surface) {
            GradeDetail(grade)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AppTheme.colors.ink,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun AverageCard(grades: GradeOverview, modifier: Modifier) {
    ListCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rememberAnimatedDecimal(grades.average), color = AppTheme.colors.ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(" / 20", color = AppTheme.colors.muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                if (grades.trend.isNotBlank()) {
                    Text(
                        grades.trend,
                        color = if (grades.trend.startsWith("↗")) AppTheme.colors.positive else AppTheme.colors.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Text("Moyenne générale", color = AppTheme.colors.muted, fontSize = 13.sp)
            grades.classAverage?.let {
                Text("Moyenne de la classe : $it", color = AppTheme.colors.muted, fontSize = 13.sp)
            }
            if (grades.evolution.size >= 2) {
                Spacer(Modifier.height(14.dp))
                EvolutionChart(grades.evolution)
            }
        }
    }
}

/** Courbe des moyennes de période. */
@Composable
private fun EvolutionChart(points: List<PeriodAverage>) {
    val line = AppTheme.colors.accent
    val faint = AppTheme.colors.muted.copy(alpha = .25f)
    val low = (points.minOf { it.average } - 1.5).coerceAtLeast(0.0)
    val high = (points.maxOf { it.average } + 1.5).coerceAtMost(20.0).coerceAtLeast(low + 1)
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
        fun y(value: Double) = (size.height - ((value - low) / (high - low)).toFloat() * size.height)
        drawLine(faint, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { index, p ->
            val x = index * stepX
            if (index == 0) path.moveTo(x, y(p.average)) else path.lineTo(x, y(p.average))
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { index, p ->
            drawCircle(Color.White, 6.dp.toPx(), Offset(index * stepX, y(p.average)))
            drawCircle(line, 4.dp.toPx(), Offset(index * stepX, y(p.average)))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        points.forEach { Text(it.name.take(12), color = AppTheme.colors.muted, fontSize = 12.sp) }
    }
}

private fun format(value: Double) = String.format(Locale.FRENCH, "%.1f", value).removeSuffix(",0").let {
    if (it.contains(',')) it else it
}

private fun shortDate(iso: String?): String =
    iso?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)) }.getOrNull() }.orEmpty()

@Composable
private fun GradeRow(grade: GradeEntry, showSubject: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ListCard(modifier, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SubjectBadge(grade.subject, grade.colorIndex, size = 32.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (showSubject) grade.subject else grade.label.ifBlank { "Évaluation" },
                    color = AppTheme.colors.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    listOf(if (showSubject) grade.label.takeIf { it.isNotBlank() } else null, shortDate(grade.date).takeIf { it.isNotBlank() })
                        .filterNotNull().joinToString("  ·  "),
                    color = AppTheme.colors.muted,
                    fontSize = 12.sp,
                )
            }
            GradePill(grade)
        }
    }
}

/** La note, en vert quand elle est au moins égale à la moyenne de la classe, quand celle-ci est connue. */
@Composable
private fun GradePill(grade: GradeEntry) {
    val value = grade.value
    val text = if (value == null) "Abs" else "${format(value)}/${grade.scale.toInt()}"
    val good = value != null && grade.classAverage != null && value >= grade.classAverage
    val tint = when {
        !grade.counted || value == null -> AppTheme.colors.muted
        good -> AppTheme.colors.positive
        else -> AppTheme.colors.ink
    }
    Text(
        text,
        color = tint,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(tint.copy(alpha = .12f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun SubjectCard(line: SubjectAverage, modifier: Modifier, onGrade: (GradeEntry) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListCard(modifier, onClick = { expanded = !expanded }) {
        Column {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                SubjectBadge(line.subject.name, line.subject.colorIndex, size = 36.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(line.subject.name, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    line.classAverage?.let { Text("Classe : $it", color = AppTheme.colors.muted, fontSize = 12.sp) }
                }
                Text(line.average, color = AppTheme.colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(expanded && line.grades.isNotEmpty()) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    line.grades.forEach { grade ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onGrade(grade) }.padding(horizontal = 17.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(grade.label.ifBlank { "Évaluation" }, color = AppTheme.colors.ink, fontSize = 14.sp)
                                Text(
                                    listOf(shortDate(grade.date), "coef. ${format(grade.coefficient)}").filter { it.isNotBlank() }.joinToString("  ·  "),
                                    color = AppTheme.colors.muted,
                                    fontSize = 12.sp,
                                )
                            }
                            GradePill(grade)
                        }
                    }
                    Simulator(line)
                }
            }
        }
    }
}

/** « Quelle note pour atteindre X de moyenne ? » */
@Composable
private fun Simulator(line: SubjectAverage) {
    val current = remember(line) { GradeMath.weightedAverage(line.grades) }
    var target by remember { mutableStateOf(current?.let { format((it + 1).coerceAtMost(20.0)) }.orEmpty()) }
    var coefficient by remember { mutableStateOf("1") }
    val goal = target.replace(',', '.').toDoubleOrNull()
    val coef = coefficient.replace(',', '.').toDoubleOrNull()
    val required = if (goal != null && coef != null) GradeMath.requiredGrade(line.grades, goal, coef) else null
    Column(Modifier.padding(horizontal = 17.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Simulateur", color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                target, { target = it }, label = { Text("Objectif") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                coefficient, { coefficient = it }, label = { Text("Coef.") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
        }
        Text(
            when {
                required == null -> "Saisis un objectif et un coefficient."
                required > 20.0 -> "Même 20/20 à la prochaine évaluation ne suffirait pas."
                required <= 0.0 -> "Objectif déjà acquis, quelle que soit la prochaine note."
                else -> "Il te faut au moins ${format(required)} / 20."
            },
            color = AppTheme.colors.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GradeDetail(grade: GradeEntry) {
    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(grade.subject, color = subjectColor(grade.subject, grade.colorIndex), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(grade.label.ifBlank { "Évaluation" }, color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                grade.value?.let { "${format(it)} / ${grade.scale.toInt()}" } ?: "Absent ou non noté",
                color = AppTheme.colors.ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!grade.counted) Text("Cette note ne compte pas dans la moyenne.", color = AppTheme.colors.muted, fontSize = 14.sp)
        DetailLine("Coefficient", format(grade.coefficient))
        shortDate(grade.date).takeIf { it.isNotBlank() }?.let { DetailLine("Date", it) }
        grade.classAverage?.let { DetailLine("Moyenne de la classe", format(it)) }
        grade.minimum?.let { DetailLine("Note la plus basse", format(it)) }
        grade.maximum?.let { DetailLine("Note la plus haute", format(it)) }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, color = AppTheme.colors.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
