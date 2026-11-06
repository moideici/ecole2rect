package fr.ecole2recte.app.ui.grades

import fr.ecole2recte.app.domain.FrenchFormat
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
import fr.ecole2recte.app.domain.AverageSource
import fr.ecole2recte.app.domain.GradeCurves
import fr.ecole2recte.app.domain.GradeEntry
import fr.ecole2recte.app.data.isNewGrade
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.rememberAppContainer
import androidx.compose.runtime.LaunchedEffect
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
import fr.ecole2recte.app.ui.common.RefreshOnResume
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.rememberAnimatedDecimal
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.AppTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.font.FontStyle
import fr.ecole2recte.app.domain.RawGrade
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.ecole2recte.app.ui.theme.subjectColor

@Composable
fun GradesRoute(onOpenMethod: () -> Unit = {}) {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { GradesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel)
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh, notices = viewModel.notices) { grades ->
        GradesContent(grades, viewModel::selectPeriod, onOpenMethod)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradesContent(grades: GradeOverview, onSelectPeriod: (String) -> Unit, onOpenMethod: () -> Unit) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect("notes", listState)
    var opened by remember { mutableStateOf<GradeEntry?>(null) }
    // Notes « nouvelles » : retenues par profil sur le téléphone, mises en avant pendant trois jours.
    val container = rememberAppContainer()
    val ownerKey = LocalActiveProfile.current?.key.orEmpty()
    var firstSeen by remember(ownerKey) { mutableStateOf(container.seenGrades.firstSeen(ownerKey)) }
    LaunchedEffect(grades.all, ownerKey) { firstSeen = container.seenGrades.record(ownerKey, grades.all.map { it.id }) }
    val now = remember(firstSeen) { System.currentTimeMillis() }
    val isNew: (GradeEntry) -> Boolean = { isNewGrade(firstSeen[it.id], now) }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Notes", grades.periodName.ifBlank { "Matière par matière" }) }
        if (grades.periods.size > 1) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(grades.periods, key = { it.code }) { period ->
                        FilterChip(
                            selected = period.selected,
                            onClick = { onSelectPeriod(period.code) },
                            label = { Text(period.name + period.average?.let { " · $it" }.orEmpty()) },
                        )
                    }
                }
            }
        }
        item { AverageCard(grades, onOpenMethod, Modifier.entrance(0)) }
        item { SimulatorCard(grades.bySubject, Modifier.entrance(1)) }
        if (grades.recent.isNotEmpty()) {
            item { SectionLabel("Dernières notes") }
            item {
                Column {
                    grades.recent.forEachIndexed { index, grade ->
                        GradeRow(grade, showSubject = true, isNew = isNew(grade), Modifier.entrance(index + 1)) { opened = grade }
                    }
                }
            }
        }
        item { SectionLabel("Par matière") }
        if (grades.bySubject.isEmpty()) item { EmptyState("Pas encore de notes", "Tes notes apparaîtront ici dès qu'elles seront saisies.") }
        item {
            Column {
                grades.bySubject.forEachIndexed { index, line -> SubjectCard(line, grades.all, isNew, Modifier.entrance(index)) { opened = it } }
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
private fun AverageCard(grades: GradeOverview, onOpenMethod: () -> Unit, modifier: Modifier) {
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
            AverageSourceTag(grades.averageSource)
            grades.classAverage?.let {
                Text("Moyenne de la classe : $it", color = AppTheme.colors.muted, fontSize = 13.sp)
            }
            if (grades.appreciation.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(grades.appreciation, color = AppTheme.colors.ink, fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic)
            }
            if (grades.evolution.size >= 2) {
                Spacer(Modifier.height(14.dp))
                Text("Moyenne générale par période (officielle)", color = AppTheme.colors.muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                EvolutionChart(grades.evolution)
            }
            Text(
                "Comment la moyenne est calculée ?",
                color = AppTheme.colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onOpenMethod).padding(vertical = 6.dp),
            )
        }
    }
}

/** Dit si la moyenne vient d'ÉcoleDirecte ou d'un calcul de l'application ; un calcul est signalé comme une estimation. */
@Composable
private fun AverageSourceTag(source: AverageSource) {
    if (source == AverageSource.None) return
    val official = source.isOfficial
    val tint = if (official) AppTheme.colors.positive else AppTheme.colors.warningTitle
    Text(
        (if (official) "✓ " else "≈ ") + source.label,
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 6.dp).background(if (official) tint.copy(alpha = .10f) else AppTheme.colors.warningSurface, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Courbe des moyennes de période (officielles : une période sans moyenne n'a pas de point). */
@Composable
private fun EvolutionChart(points: List<PeriodAverage>) {
    LineChart(
        line = points.map { ChartPoint(it.average, it.name.take(14)) },
        description = GradeCurves.describe("Moyenne générale par période", points.map { it.name }, points.map { it.average }),
    )
}

/** « Classe : 12,3 · de 6,5 à 18,0 » quand l'établissement donne les extrêmes. */
private fun classLine(average: String, line: SubjectAverage): String {
    val range = if (line.minimum != null && line.maximum != null) " · de ${line.minimum} à ${line.maximum}" else ""
    return "Classe : $average$range"
}

private fun format(value: Double) = FrenchFormat.decimal(value).removeSuffix(",0").let {
    if (it.contains(',')) it else it
}

private fun shortDate(iso: String?): String =
    iso?.let { runCatching { FrenchFormat.dayMonthShort(LocalDate.parse(it)) }.getOrNull() }.orEmpty()

@Composable
private fun GradeRow(grade: GradeEntry, showSubject: Boolean, isNew: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ListCard(modifier, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SubjectBadge(grade.subject, grade.colorIndex, size = 32.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showSubject) grade.subject else grade.label.ifBlank { "Évaluation" },
                        color = AppTheme.colors.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNew) NewBadge()
                }
                Text(
                    listOf(if (showSubject) grade.label.takeIf { it.isNotBlank() } else null, shortDate(grade.date).takeIf { it.isNotBlank() })
                        .filterNotNull().joinToString("  ·  "),
                    color = AppTheme.colors.muted,
                    fontSize = 12.sp,
                )
                ClassGap(grade)
            }
            GradePill(grade)
        }
    }
}

/** Pastille « Nouveau » : une note vue pour la première fois il y a moins de trois jours. */
@Composable
private fun NewBadge() {
    Text(
        "Nouveau",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).background(AppTheme.colors.accentFill, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Indicateur compact face à la moyenne de la classe à ce devoir, quand elle est connue : le sens est écrit, pas seulement coloré. */
@Composable
private fun ClassGap(grade: GradeEntry) {
    val gap = GradeMath.classGap(grade) ?: return
    val rounded = Math.round(gap * 10) / 10.0
    Text(
        GradeMath.classGapLabel(gap),
        color = if (rounded > 0) AppTheme.colors.positive else if (rounded < 0) AppTheme.colors.error else AppTheme.colors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** La note, en vert quand elle est au moins égale à la moyenne de la classe, quand celle-ci est connue. */
@Composable
private fun GradePill(grade: GradeEntry) {
    val value = grade.value
    val text = if (value == null) grade.rawValue.ifBlank { "–" } else "${format(value)}/${grade.scale.toInt()}"
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
private fun SubjectCard(line: SubjectAverage, all: List<GradeEntry>, isNew: (GradeEntry) -> Boolean, modifier: Modifier, onGrade: (GradeEntry) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListCard(modifier, onClick = { expanded = !expanded }) {
        Column {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                SubjectBadge(line.subject.name, line.subject.colorIndex, size = 36.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(line.subject.name, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    line.classAverage?.let { Text(classLine(it, line), color = AppTheme.colors.muted, fontSize = 12.sp) }
                    if (line.appreciation.isNotBlank()) {
                        Text(
                            line.appreciation,
                            color = AppTheme.colors.ink,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(line.average, color = AppTheme.colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (line.average != "–") {
                        Text(
                            if (line.averageOfficial) "officielle" else "calculée",
                            color = if (line.averageOfficial) AppTheme.colors.muted else AppTheme.colors.warningTitle,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            AnimatedVisibility(expanded && line.grades.isNotEmpty()) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    line.grades.forEach { grade ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onGrade(grade) }.padding(horizontal = 17.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(grade.label.ifBlank { "Évaluation" }, color = AppTheme.colors.ink, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                                    if (isNew(grade)) NewBadge()
                                }
                                Text(
                                    listOf(shortDate(grade.date), "coef. ${format(grade.coefficient)}").filter { it.isNotBlank() }.joinToString("  ·  "),
                                    color = AppTheme.colors.muted,
                                    fontSize = 12.sp,
                                )
                                ClassGap(grade)
                            }
                            GradePill(grade)
                        }
                    }
                    SubjectCurve(line.subject.name, all)
                    SimulatorPanel(line.grades, line.subject.name, Modifier.padding(horizontal = 17.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** Évolution des notes d'une matière : la moyenne cumulée (courbe) et chaque note (carrés), dans l'ordre des dates. */
@Composable
private fun SubjectCurve(subject: String, all: List<GradeEntry>) {
    val series = remember(subject, all) { GradeCurves.subjectSeries(all, subject) }
    if (series.size < 2) return
    Column(Modifier.padding(horizontal = 17.dp).padding(top = 10.dp)) {
        Text("Évolution (calculée par l'app, toute l'année)", color = AppTheme.colors.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        LineChart(
            line = series.map { ChartPoint(it.runningAverage, shortDate(it.date)) },
            dots = series.map { it.note },
            description = GradeCurves.describe(
                "Moyenne de $subject après chaque note",
                series.map { shortDate(it.date) },
                series.map { it.runningAverage },
            ),
        )
        Text("Courbe : moyenne après chaque note · carrés : les notes sur 20", color = AppTheme.colors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GradeDetail(grade: GradeEntry) {
    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(grade.subject, color = subjectColor(grade.subject, grade.colorIndex), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(grade.label.ifBlank { "Évaluation" }, color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                grade.value?.let { "${format(it)} / ${grade.scale.toInt()}" } ?: RawGrade.describe(grade.rawValue),
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
