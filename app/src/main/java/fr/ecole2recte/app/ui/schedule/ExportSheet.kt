package fr.ecole2recte.app.ui.schedule

import fr.ecole2recte.app.domain.FrenchFormat
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fr.ecole2recte.app.domain.ScheduleEntry
import fr.ecole2recte.app.export.Alternation
import fr.ecole2recte.app.export.ExportChoices
import fr.ecole2recte.app.export.ExportFiles
import fr.ecole2recte.app.export.ExportFormat
import fr.ecole2recte.app.export.IcsOptions
import fr.ecole2recte.app.export.IcsWriter
import fr.ecole2recte.app.export.PdfStyle
import fr.ecole2recte.app.export.ScheduleExport
import fr.ecole2recte.app.export.TimetableLayout
import fr.ecole2recte.app.export.TimetablePdf
import fr.ecole2recte.app.export.TypicalWeeks
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.LightSubjectColors
import fr.ecole2recte.app.ui.theme.LocalSubjectColorOverrides
import fr.ecole2recte.app.ui.theme.subjectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PROBLEM_NO_APP = "Aucune application pour ce fichier."

/**
 * Exporter l'emploi du temps en PDF (A4 paysage, une page par semaine type) ou en ICS (événements
 * récurrents pour un calendrier). Le fichier est écrit dans le cache privé de l'application puis remis à
 * l'application choisie par l'utilisateur : rien n'est envoyé par ecole2recte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheetContent(entries: List<ScheduleEntry>, weekStart: LocalDate) {
    val context = LocalContext.current
    val settings = rememberAppContainer().settings
    val profile = LocalActiveProfile.current
    val profileKey = profile?.key ?: "-"
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val initial = remember(profileKey) { settings.alternation(profileKey) }

    var formatIndex by rememberSaveable { mutableIntStateOf(0) }
    var alternates by rememberSaveable { mutableStateOf(initial.active) }
    var currentCycle by rememberSaveable { mutableIntStateOf(if (initial.active) initial.cycleOf(weekStart) else 1) }
    var weeksChoice by rememberSaveable { mutableIntStateOf(0) } // 0 = les deux, 1, 2
    var grayscale by rememberSaveable { mutableStateOf(false) }
    var fromEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var untilEpoch by rememberSaveable { mutableStateOf(TypicalWeeks.defaultEnd(today).toEpochDay()) }
    var picking by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    val format = ExportFormat.entries[formatIndex]
    val alternation = if (alternates) Alternation.with(weekStart, currentCycle) else Alternation()
    val schedule = remember(entries, alternates, currentCycle) { TypicalWeeks.build(entries, alternation) }
    val weeks = when (weeksChoice) { 1 -> setOf(1); 2 -> setOf(2); else -> setOf(1, 2) }
    val choices = ExportChoices(format, weeks, LocalDate.ofEpochDay(fromEpoch), LocalDate.ofEpochDay(untilEpoch), grayscale)
    val warnings = ScheduleExport.warnings(schedule, choices)
    val suggestAlternation = !alternates && remember(entries) { TypicalWeeks.looksAlternating(entries) }

    // Les couleurs d'impression : celles de la matière dans l'application (choix de l'utilisateur compris), version claire.
    val overrides = LocalSubjectColorOverrides.current
    val colorOf: (String, Int) -> Int = { subject, index ->
        LightSubjectColors[Math.floorMod(overrides[subjectKey(subject)] ?: index, LightSubjectColors.size)].toArgb()
    }
    val dateFormat = remember { FrenchFormat.formatter("d MMM yyyy") }

    fun export(open: Boolean) {
        busy = true
        problem = null
        scope.launch {
            try {
                val label = profile?.firstName.orEmpty()
                val name = ScheduleExport.fileName(format, label, schedule.alternating, weeks)
                val bytes = withContext(Dispatchers.Default) {
                    if (format == ExportFormat.Pdf) {
                        val subtitle = listOf(profile?.displayName.orEmpty(), "exporté le ${today.format(dateFormat)}").filter { it.isNotBlank() }.joinToString(" · ")
                        val footer = "Généré par ecole2recte (application non officielle) d'après les semaines enregistrées sur le téléphone · vacances et exceptions non prises en compte"
                        val pages = TimetableLayout.pages(schedule, weeks.toList(), "Emploi du temps", subtitle, footer)
                        TimetablePdf.render(pages, PdfStyle(grayscale), colorOf)
                    } else {
                        val options = IcsOptions(choices.from, choices.until, profileKey, weeks, "Emploi du temps ${label}".trim(), Instant.now())
                        IcsWriter.write(schedule, options).toByteArray(Charsets.UTF_8)
                    }
                }
                val file = withContext(Dispatchers.IO) { ExportFiles.at(context).write(bytes, name) }
                if (!deliver(context, file, format.mimeType, open)) problem = PROBLEM_NO_APP
            } catch (error: Exception) {
                problem = "L'export a échoué : ${error.message ?: "erreur inconnue"}."
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Exporter", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEachIndexed { index, item ->
                FilterChip(formatIndex == index, { formatIndex = index }, label = { Text(item.label, fontSize = 13.sp) })
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Semaines 1 / 2 (ou A / B)", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Switch(alternates, {
                alternates = it
                settings.setAlternation(profileKey, if (it) Alternation.with(weekStart, currentCycle) else Alternation())
            })
        }
        if (suggestAlternation) {
            Text(
                "Tes semaines diffèrent : l'emploi du temps alterne peut-être.",
                color = AppTheme.colors.accent,
                fontSize = 13.sp,
            )
        }
        if (alternates) {
            Text(
                "Semaine du ${FrenchFormat.dayMonth(weekStart)} :",
                color = AppTheme.colors.ink,
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2).forEach { cycle ->
                    FilterChip(currentCycle == cycle, {
                        currentCycle = cycle
                        settings.setAlternation(profileKey, Alternation.with(weekStart, cycle))
                    }, label = { Text("Semaine $cycle", fontSize = 13.sp) })
                }
            }
            Text("Semaines à exporter", color = AppTheme.colors.ink, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Les deux", "Semaine 1", "Semaine 2").forEachIndexed { index, label ->
                    FilterChip(weeksChoice == index, { weeksChoice = index }, label = { Text(label, fontSize = 13.sp) })
                }
            }
        }

        if (format == ExportFormat.Pdf) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Niveaux de gris", color = AppTheme.colors.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(grayscale, { grayscale = it })
            }
        } else {
            Text("Période", color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "from" }) { Text("Du ${choices.from.format(dateFormat)}", fontSize = 13.sp) }
                OutlinedButton(onClick = { picking = "until" }) { Text("au ${choices.until.format(dateFormat)}", fontSize = 13.sp) }
            }
        }

        warnings.forEach { Text(it, color = AppTheme.colors.error, fontSize = 13.sp) }
        problem?.let { Text(it, color = AppTheme.colors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

        Button(onClick = { export(open = false) }, enabled = !busy && !schedule.isEmpty, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Création…" else "Créer et partager")
        }
        OutlinedButton(onClick = { export(open = true) }, enabled = !busy && !schedule.isEmpty, modifier = Modifier.fillMaxWidth()) {
            Text("Créer et ouvrir")
        }
    }

    picking?.let { which ->
        val start = if (which == "from") choices.from else choices.until
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "from") fromEpoch = picked.toEpochDay() else untilEpoch = picked.toEpochDay()
                    }
                    picking = null
                }) { Text("Choisir") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
}

/** Remet le fichier à une application : partage (choix de l'application) ou ouverture directe. */
private fun deliver(context: Context, file: java.io.File, mimeType: String, open: Boolean): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = if (open) {
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
    } else {
        Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, file.name)
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (missing: ActivityNotFoundException) {
        false
    }
}
