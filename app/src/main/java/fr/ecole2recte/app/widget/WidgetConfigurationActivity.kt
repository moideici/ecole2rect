package fr.ecole2recte.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import fr.ecole2recte.app.Ecole2recteApplication
import fr.ecole2recte.app.domain.GradeSimulation
import fr.ecole2recte.app.security.AppLock
import fr.ecole2recte.app.sync.WidgetSnapshotStore
import fr.ecole2recte.app.ui.LockScreen
import fr.ecole2recte.app.ui.theme.Ecole2recteTheme
import kotlinx.coroutines.launch

/** Configuration locale ; le widget n'est validé qu'après Enregistrer. Retour/annulation = RESULT_CANCELED. */
class WidgetConfigurationActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id) ?: run { finish(); return }
        if (id <= 0 || info.provider.packageName != packageName) { finish(); return }
        val settings = (application as Ecole2recteApplication).container.settings
        locked = settings.appLock.value
        if (locked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val mean = info.provider.className.endsWith("MeanWidgetReceiver")
        val goals = info.provider.className.endsWith("GoalsWidgetReceiver")
        setContent {
            val mode by settings.themeMode.collectAsState()
            val colors by settings.colorTheme.collectAsState()
            val scale by settings.textScale.collectAsState()
            Ecole2recteTheme(mode, colors, scale) {
                if (locked) LockScreen(message = null, onUnlock = {
                    AppLock.authenticate(this, "Déverrouiller la configuration", onSuccess = { locked = false })
                }) else {
                    WidgetConfiguration(id, mean, goals, onSave = { config ->
                        if (WidgetConfigStore.at(this).save(id, config)) lifecycleScope.launch {
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(id)
                            widgetForProvider(info.provider.className).update(this@WidgetConfigurationActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    }, onCancel = ::finish)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val settings = (application as Ecole2recteApplication).container.settings
        if (AppLock.timer.onForeground(settings.appLock.value, settings.lockDelay.value)) locked = true
    }

    override fun onStop() {
        super.onStop()
        AppLock.timer.onBackground()
    }
}

internal fun widgetForProvider(provider: String): androidx.glance.appwidget.GlanceAppWidget = when {
    provider.endsWith("MeanWidgetReceiver") -> MeanWidget()
    provider.endsWith("SmallNextClassWidgetReceiver") -> SmallNextClassWidget()
    provider.endsWith("RemainingHomeworkWidgetReceiver") -> RemainingHomeworkWidget()
    provider.endsWith("LastGradeWidgetReceiver") -> LastGradeWidget()
    provider.endsWith("UnreadMessagesWidgetReceiver") -> UnreadMessagesWidget()
    provider.endsWith("ExamCountdownWidgetReceiver") -> ExamCountdownWidget()
    provider.endsWith("GoalsWidgetReceiver") -> GoalsWidget()
    else -> MeanWidget()
}

@Composable
private fun WidgetConfiguration(id: Int, mean: Boolean, goals: Boolean, onSave: (MeanWidgetConfig) -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { WidgetConfigStore.at(context) }
    val initial = remember(id) { store.read(id) }
    val snapshots = remember { WidgetSnapshotStore(context).profiles() }
    val fallback = remember { WidgetSnapshotStore(context).read() }
    var profile by rememberSaveable { mutableStateOf(initial.profile.ifBlank { fallback?.profileKey.orEmpty() }) }
    var source by rememberSaveable { mutableStateOf(initial.source.name) }
    var period by rememberSaveable { mutableStateOf(initial.period.name) }
    var subject by rememberSaveable { mutableStateOf(initial.subject) }
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var target by rememberSaveable { mutableStateOf(initial.target.toString().replace('.', ',')) }
    var green by rememberSaveable { mutableStateOf(initial.greenFrom.toString().replace('.', ',')) }
    var orange by rememberSaveable { mutableStateOf(initial.orangeFrom.toString().replace('.', ',')) }
    var trend by rememberSaveable { mutableStateOf(initial.showTrend) }
    var error by remember { mutableStateOf<String?>(null) }
    val snapshot = snapshots.firstOrNull { it.profileKey == profile } ?: fallback?.takeIf { profile.isBlank() || it.profileKey == profile }
    val subjects = snapshot?.meanPeriods.orEmpty().flatMap { it.subjects }.distinctBy { it.id }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Configurer le widget", style = MaterialTheme.typography.headlineSmall)
            Text("Les données viennent de la dernière synchronisation. Ouvre l'application pour enregistrer un autre profil.")
            if (snapshots.isEmpty()) Text("Aucun profil enregistré pour le moment.")
            WidgetChoice("Profil / enfant", profile, snapshots.map { it.profileKey to it.profileLabel.ifBlank { "Profil" } }) { profile = it; subject = "" }
            if (mean) {
                WidgetChoice("Source", source, MeanSource.entries.map { it.name to it.label }) { source = it }
                WidgetChoice("Période", period, MeanPeriod.entries.map { it.name to it.label }) { period = it }
                if (source == MeanSource.Subject.name) WidgetChoice("Matière", subject, subjects.map { it.id to it.name }) { subject = it }
                if (source == MeanSource.GoalGap.name) NumberField("Objectif sur 20", target) { target = it }
                OutlinedTextField(title, { title = it }, label = { Text("Titre court (8 caractères maximum)") }, modifier = Modifier.fillMaxWidth())
                NumberField("Vert à partir de", green) { green = it }
                NumberField("Orange à partir de (rouge en dessous)", orange) { orange = it }
                Row { Checkbox(trend, { trend = it }); Text("Afficher la tendance") }
            }
            if (goals && !fr.ecole2recte.app.settings.PrivacySelection.mode(context).hidesGrades) {
                Text("Objectifs par matière", style = MaterialTheme.typography.titleMedium)
                subjects.forEach { fr.ecole2recte.app.ui.grades.GoalEditor(profile, it.id, it.name) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val config = MeanWidgetConfig(profile, MeanSource.valueOf(source), subject, MeanPeriod.valueOf(period), title,
                    GradeSimulation.parse(target) ?: Double.NaN, GradeSimulation.parse(green) ?: Double.NaN, GradeSimulation.parse(orange) ?: Double.NaN, trend)
                if (!config.valid()) error = "Vérifie le titre, l'objectif (0 à 20) et les seuils (vert ≥ orange)."
                else if (mean && config.source == MeanSource.Subject && subjects.none { it.id == subject }) error = "Choisis une matière."
                else onSave(config)
            }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
        }
    }
}

@Composable
internal fun WidgetChoice(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    options.forEach { (key, text) ->
        // Une colonne permet les noms longs et les grandes polices, y compris en paysage.
        TextButton(onClick = { onSelect(key) }, modifier = Modifier.fillMaxWidth()) { Text((if (key == selected) "● " else "○ ") + text) }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
