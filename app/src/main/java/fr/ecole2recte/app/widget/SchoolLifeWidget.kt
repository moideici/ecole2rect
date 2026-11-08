package fr.ecole2recte.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import fr.ecole2recte.app.domain.SchoolLifeEvent
import fr.ecole2recte.app.domain.SchoolLifeKind
import fr.ecole2recte.app.domain.SchoolLifeLogic
import fr.ecole2recte.app.settings.PrivacySelection
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Instantané minimal du profil affiché : ni motif ni commentaire dans le stockage du widget. */
class SchoolLifeSnapshotStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("school-life-widget", Context.MODE_PRIVATE)
    val owner: String get() = prefs.getString("owner", "").orEmpty()
    val updatedAt: Long get() = prefs.getLong("updated", 0)
    fun read(): List<SchoolLifeEvent>? = runCatching {
        val array = JSONArray(prefs.getString("events", null) ?: return null)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SchoolLifeEvent(SchoolLifeKind.valueOf(item.getString("kind")), "", "", "", item.getBoolean("justified"),
                id = item.getString("id"), date = item.getString("date"), countedMinutes = item.optInt("minutes", -1).takeIf { it >= 0 })
        }
    }.getOrNull()

    suspend fun publish(owner: String, events: List<SchoolLifeEvent>) {
        if (owner.isNotBlank() && fr.ecole2recte.app.sync.LocalFeaturePreferences(context).activeOwner != owner) return
        prefs.edit().putString("owner", owner).putLong("updated", System.currentTimeMillis()).putString("events", JSONArray(events.map {
            JSONObject().put("id", it.id).put("kind", it.kind.name).put("date", it.date).put("justified", it.justified).put("minutes", it.countedMinutes ?: -1)
        }).toString()).apply()
        runCatching { SchoolLifeWidget().updateAll(context) }
        runCatching { fr.ecole2recte.app.sync.AbsenceReminderScheduler.refresh(context) }
    }

    fun notified(): Set<String> = prefs.getStringSet("notified/$owner", emptySet()).orEmpty().toSet()
    fun markNotified(ids: Set<String>) { prefs.edit().putStringSet("notified/$owner", notified() + ids).apply() }
    fun clear() { prefs.edit().remove("owner").remove("events").remove("updated").apply() }
    fun forgetAccount(identifier: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("notified/$identifier/", ignoreCase = true) }.forEach(editor::remove)
        editor.apply()
    }
}

class SchoolLifeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(60.dp, 60.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 200.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = SchoolLifeSnapshotStore(context)
        val events = store.read().takeIf { store.owner.isNotBlank() && store.owner == fr.ecole2recte.app.sync.LocalFeaturePreferences(context).activeOwner }
        val hidden = PrivacySelection.mode(context).enabled
        val colors = WidgetColors.load(context)
        val textScale = fr.ecole2recte.app.ui.theme.TextScale.parse(context.getSharedPreferences(fr.ecole2recte.app.settings.ThemeSelection.PREFS, Context.MODE_PRIVATE).getString("text_scale", null))
        provideContent { WidgetTheming(colors) {
            SchoolLifeWidgetContent(events, hidden, store.updatedAt, context.resources.configuration.fontScale * textScale.multiplier, textScale.multiplier)
        } }
    }
}

/** Pas de taille fixe de contenu ; seuls les paliers du fournisseur fixent la variante. */
@androidx.compose.runtime.Composable
internal fun SchoolLifeWidgetContent(events: List<SchoolLifeEvent>?, hidden: Boolean, updatedAt: Long, fontScale: Float = 1f, textMultiplier: Float = 1f) {
    val c = LocalWidgetColors.current
    val expanded = SchoolLifeLogic.widgetExpanded(LocalSize.current.width.value, LocalSize.current.height.value, fontScale)
    val caption = SchoolLifeLogic.widgetShowsCaption(LocalSize.current.width.value, LocalSize.current.height.value, fontScale)
    val count = events?.count { it.kind == SchoolLifeKind.Absence && !it.justified }
    val value = SchoolLifeLogic.widgetValue(count, hidden)
    val valueSize = SchoolLifeLogic.widgetNumberSize(LocalSize.current.width.value, LocalSize.current.height.value, fontScale, value.length)
    fun size(base: Int) = (base * textMultiplier).roundToInt().coerceAtLeast(1)
    Column(GlanceModifier.fillMaxSize().background(c.surface).padding(6.dp).clickable(opens("vie-scolaire")).semantics {
        contentDescription = "Vie scolaire : ${SchoolLifeLogic.widgetValue(count, hidden)} absence(s) à justifier"
    }) {
        // À 1×1, le compteur et sa légende sont prioritaires. Le titre complet apparaît au palier supérieur.
        if (expanded) Text("Vie scolaire", style = style(c.accent, size(12), bold = true))
        Text(value, style = style(c.ink, size(valueSize), bold = true))
        if (caption) Text(if (events == null) "Ouvre l’app" else "À justifier", style = style(c.muted, size(11)))
        if (expanded && events != null && !hidden) {
            val minutes = SchoolLifeLogic.unjustifiedMinutes(events)
            Text("${minutes / 60} h ${minutes % 60} min connues", style = style(c.muted, size(12)))
            if (SchoolLifeLogic.unknownDurations(events) > 0) Text("Durées incomplètes", style = style(c.muted, size(11)))
            if (updatedAt > 0) Text("Mis à jour le " + java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), style = style(c.muted, size(10)))
        }
    }
}

class SchoolLifeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SchoolLifeWidget()
}

fun wantsSchoolLife(context: Context): Boolean =
    fr.ecole2recte.app.sync.LocalFeaturePreferences(context).absenceReminderDays != null ||
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SchoolLifeWidgetReceiver::class.java)).isNotEmpty()
