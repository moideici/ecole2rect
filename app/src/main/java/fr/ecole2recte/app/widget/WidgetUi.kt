package fr.ecole2recte.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import fr.ecole2recte.app.MainActivity
import fr.ecole2recte.app.settings.PrivacySelection
import fr.ecole2recte.app.settings.ThemeSelection
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.sync.WidgetSnapshot
import fr.ecole2recte.app.sync.WidgetSnapshotStore

// ---------------------------------------------------------------------------------------------
// Briques communes des widgets : couleurs du thème choisi, cadre, pastilles, zones cliquables.
// ---------------------------------------------------------------------------------------------

/** Les couleurs du thème choisi dans l'application, sous forme de [GlanceColor] jour/nuit. */
class WidgetColors(private val theme: WidgetTheme) {
    private fun pair(day: Int, night: Int): GlanceColor = ColorProvider(day = Color(day), night = Color(night))

    private fun pick(read: (WidgetRoles) -> Int) = pair(read(theme.day), read(theme.night))

    private fun pickList(read: (WidgetRoles) -> List<Int>): List<GlanceColor> {
        val day = read(theme.day)
        val night = read(theme.night)
        return day.indices.map { pair(day[it], night[it]) }
    }

    val surface = pick { it.surface }
    val ink = pick { it.ink }
    val muted = pick { it.muted }
    val accent = pick { it.accent }
    val accentSoft = pick { it.accentSoft }
    val alert = pick { it.alert }
    val alertSoft = pick { it.alertSoft }
    val soft = pick { it.soft }
    val line = pick { it.line }
    val hero = pick { it.hero }
    val onHero = pick { it.onHero }
    val onHeroSoft = pick { it.onHeroSoft }
    val heroChip = pick { it.heroChip }

    private val subjects = pickList { it.subject }
    private val subjectsSoft = pickList { it.subjectSoft }
    private val onSubjects = pickList { it.onSubject }

    fun subject(index: Int) = subjects[Math.floorMod(index, subjects.size)]
    fun subjectSoft(index: Int) = subjectsSoft[Math.floorMod(index, subjectsSoft.size)]
    fun onSubject(index: Int) = onSubjects[Math.floorMod(index, onSubjects.size)]

    companion object {
        /** Lit le thème et le mode choisis dans Plus > Apparence : à chaque dessin, donc toujours à jour. */
        fun load(context: Context): WidgetColors =
            WidgetColors(WidgetThemes.of(ThemeSelection.colorTheme(context), ThemeSelection.mode(context)))
    }
}

val LocalWidgetColors = staticCompositionLocalOf<WidgetColors> { error("Couleurs de widget non fournies") }

/** Pose les couleurs du thème pour tout le contenu d'un widget. */
@Composable
fun WidgetTheming(colors: WidgetColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWidgetColors provides colors, content = content)
}

/** L'instantané publié, déjà passé par le mode « montrer à quelqu'un » : les vues ne voient jamais les valeurs masquées. */
fun loadSnapshot(context: Context): WidgetSnapshot? =
    WidgetSnapshotStore(context).read()?.let {
        val digits = fr.ecole2recte.app.settings.AverageDecimals.read(context)
        val formatted = it.copy(
            average = fr.ecole2recte.app.domain.AveragePresentation.number(it.average, digits),
            averageLabel = it.averageLabel + it.officialAverage.takeIf { value -> value.isNotBlank() }?.let { value -> " · bulletin ${fr.ecole2recte.app.domain.AveragePresentation.number(value, digits)}" }.orEmpty(),
        )
        WidgetPrivacy.apply(formatted, PrivacySelection.mode(context))
    }

fun style(color: GlanceColor, size: Int, bold: Boolean = false, strike: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textDecoration = if (strike) TextDecoration.LineThrough else null,
)

/** Les tailles auxquelles chaque widget change de disposition ; Android choisit la plus proche. */
val BasicSizes = setOf(
    DpSize(110.dp, 110.dp),
    DpSize(180.dp, 110.dp),
    DpSize(180.dp, 200.dp),
    DpSize(260.dp, 200.dp),
    DpSize(260.dp, 320.dp),
)

/**
 * Ouvre l'application sur [tab]. [zone] distingue deux zones cliquables d'un même widget : l'URI sert
 * uniquement à rendre l'intention unique, sans quoi Android confondrait deux widgets qui ne diffèrent
 * que par leur extra et ferait ouvrir à tous le dernier onglet enregistré.
 */
@Composable
fun opens(tab: String, zone: String = "frame"): Action =
    actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java)
            .putExtra(AndroidNotifier.TAB_EXTRA, tab)
            .setData(Uri.parse("ecole2recte-widget://ouvrir/$tab/$zone"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/** Cadre commun : fond arrondi (couleur pleine du thème pour [hero]), titre, un toucher ouvre l'onglet voulu. */
@Composable
fun Frame(label: String, tab: String, hero: Boolean = false, content: @Composable () -> Unit) {
    val c = LocalWidgetColors.current
    Column(
        GlanceModifier.fillMaxSize().background(if (hero) c.hero else c.surface).cornerRadius(24.dp).padding(14.dp).clickable(opens(tab)),
    ) {
        Text(label.uppercase(), style = style(if (hero) c.onHeroSoft else c.accent, 11, bold = true), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

@Composable
fun Empty(message: String, hero: Boolean = false) {
    val c = LocalWidgetColors.current
    Text(message, style = style(if (hero) c.onHero else c.ink, 14, bold = true))
}

/** Une pastille de texte : fond doux, texte en gras. */
@Composable
fun Chip(text: String, background: GlanceColor, color: GlanceColor, size: Int = 12) {
    Box(GlanceModifier.background(background).cornerRadius(12.dp).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, style = style(color, size, bold = true), maxLines = 1)
    }
}

/** Une ligne de liste : barre de la couleur de la matière, texte sur deux niveaux, valeur à droite. */
@Composable
fun Line(
    color: GlanceColor,
    first: String,
    second: String?,
    trailing: @Composable (() -> Unit)? = null,
    highlighted: Boolean = false,
    dim: Boolean = false,
) {
    val c = LocalWidgetColors.current
    val base = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
    Row(
        (if (highlighted) base.background(c.accentSoft).cornerRadius(12.dp).padding(horizontal = 6.dp, vertical = 4.dp) else base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(if (highlighted) 6.dp else 4.dp).height(32.dp).background(color).cornerRadius(3.dp)) {}
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(first, style = style(if (dim) c.muted else c.ink, 15, bold = true), maxLines = 1)
            if (!second.isNullOrBlank()) Text(second, style = style(c.muted, 12), maxLines = 1)
        }
        if (trailing != null) {
            Spacer(GlanceModifier.width(6.dp))
            trailing()
        }
    }
}
