package fr.ecole2recte.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.settings.ThemeSelection
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore
import fr.ecole2recte.app.ui.theme.TextScale

val OneByOneSizes = setOf(DpSize(40.dp, 40.dp), DpSize(56.dp, 56.dp), DpSize(110.dp, 110.dp), DpSize(180.dp, 90.dp), DpSize(220.dp, 180.dp), DpSize(320.dp, 280.dp))

@Composable
internal fun widgetVariant(): SmallWidgetVariant {
    val size = LocalSize.current
    val context = LocalContext.current
    return SmallWidgetLayout.variant(size.width.value, size.height.value, size.width > size.height, widgetFontScale(context))
}

internal fun widgetFontScale(context: Context): Float {
    val scale = TextScale.parse(context.getSharedPreferences(ThemeSelection.PREFS, Context.MODE_PRIVATE).getString("text_scale", null))
    return scale.effective(context.resources.configuration.fontScale)
}

internal fun widgetTextScale(context: Context): Float = widgetFontScale(context) / context.resources.configuration.fontScale.coerceAtLeast(.1f)

internal fun instanceSnapshot(context: Context, config: MeanWidgetConfig): WidgetSnapshot? =
    WidgetSnapshotStore(context).read(config.profile)

@Composable
internal fun widgetOpens(tab: String, subject: String = "", profile: String = ""): Action = actionStartActivity(
    Intent(LocalContext.current, MainActivity::class.java).putExtra("tab", tab).putExtra("subject", subject).putExtra("widget_profile", profile)
        .setData(Uri.Builder().scheme("ecole2recte-widget").authority("ouvrir").appendPath(tab).appendQueryParameter("subject", subject).appendQueryParameter("profile", profile).build())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

@Composable
internal fun configureWidget(id: Int): Action = actionStartActivity(
    Intent(LocalContext.current, WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .setData(Uri.parse("ecole2recte-widget://configuration/$id")),
)

/** Gabarit commun : contenu pondéré, aucune taille de contenu imposée. Une troncature ouvre le détail au toucher. */
@Composable
fun OneByOne(
    icon: String, value: String, caption: String, action: Action, variant: SmallWidgetVariant,
    tone: MeanTone = MeanTone.Neutral, detail: String = "", badge: String = "", configId: Int = 0,
) {
    val c = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetTextScale(context)
    val size = LocalSize.current
    val layout = SmallWidgetLayout.content(size.width.value, size.height.value, size.width > size.height, widgetFontScale(context))
    val valueColor = when (tone) { MeanTone.Positive -> c.positive; MeanTone.Warning -> c.warning; MeanTone.Negative -> c.alert; else -> c.ink }
    Column(GlanceModifier.fillMaxSize().background(c.surface).cornerRadius(20.dp).padding(layout.padding.dp).clickable(action)
        .semantics { contentDescription = listOf(caption, value, detail, badge).filter { it.isNotBlank() }.joinToString(" · ") }) {
        // À 1×1 très petit, la valeur et la légende priment ; les détails restent accessibles dans l'app.
        if (layout.header) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = style(c.accent, 12))
                if (configId > 0) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text("⚙", modifier = GlanceModifier.clickable(configureWidget(configId)).padding(horizontal = 4.dp), style = style(c.muted, 14))
                }
            }
        }
        Text(value, style = style(valueColor, (layout.valueSize * scale).toInt(), bold = true), maxLines = 1)
        if (layout.captionLines > 0) Text((if (layout.header) "" else "$icon ") + caption, style = style(c.muted, (10 * scale).toInt()), maxLines = layout.captionLines)
        if (variant != SmallWidgetVariant.One && layout.detailLines > 0 && detail.isNotBlank()) Text(detail, style = style(c.muted, (11 * scale).toInt()), maxLines = layout.detailLines)
        if (layout.badge && badge.isNotBlank()) Text(badge, style = style(c.muted, (10 * scale).toInt()), maxLines = 1)
    }
}
