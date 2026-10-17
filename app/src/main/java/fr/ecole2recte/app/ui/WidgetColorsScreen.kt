package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.Contrast
import fr.ecole2recte.app.core.settings.WidgetColorSettings
import fr.ecole2recte.app.core.settings.WidgetPaletteChoices
import fr.ecole2recte.app.core.settings.WidgetTheme
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.ScreenPadding

/**
 * Les couleurs des widgets d'écran d'accueil.
 *
 * Un widget ne vit pas dans l'application : il se pose devant un fond d'écran que personne
 * ici n'a choisi. Suivre le thème du téléphone est un bon défaut, ce n'est pas une vérité —
 * un fond d'écran très clair rend un widget sombre illisible, et l'inverse est vrai aussi.
 *
 * L'aperçu n'est pas décoratif : sans lui, on choisit deux couleurs à l'aveugle et on
 * découvre le résultat sur l'écran d'accueil, où il faut tout recommencer. Et quand le couple
 * choisi descend sous le seuil de lisibilité, l'écran le dit — il ne l'interdit pas, mais il
 * ne laisse pas non plus écrire du gris sur du gris sans prévenir.
 */
@Composable
fun WidgetColorsScreen(
    settings: AppSettings,
    onChange: ((WidgetColorSettings) -> WidgetColorSettings) -> Unit,
    onClose: () -> Unit,
) {
    val colors = settings.widgetColors
    val dark = colors.isDark(isSystemInDarkTheme())
    val chosenBackground = colors.background
    val chosenInk = colors.inkOrDefault()
    val background = previewBackground(chosenBackground.takeIf { colors.custom }, dark)
    val ink = previewInk(chosenInk.takeIf { colors.custom }, dark)
    val muted = ink.copy(alpha = 0.62f)
    val risky = colors.custom && chosenBackground != null && chosenInk != null &&
        Contrast.ratio(chosenBackground, chosenInk) < Contrast.MINIMUM_RATIO

    DetailScaffold("Couleurs des widgets", "Écran d'accueil du téléphone", onClose) {
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)) {
            AppCard {
                Text(
                    "Aperçu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                WidgetPreview(background = background, ink = ink, muted = muted)
                if (risky) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Ce texte est peu lisible sur ce fond. Le widget restera affiché, mais " +
                            "il sera pénible à lire d'un coup d'œil — et c'est tout ce qu'on " +
                            "demande à un widget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        SettingsBlock("Quand utiliser quoi") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    WidgetTheme.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { choice ->
                                FilterPill(choice.label, selected = colors.theme == choice) {
                                    onChange { current -> current.copy(theme = choice) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Text(
                "« Comme le système » suit le thème du téléphone. Les deux réglages forcés " +
                    "servent quand le fond d'écran ne va pas avec ce thème.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (colors.custom) {
            SettingsBlock("Fond") {
                Swatches(
                    values = WidgetPaletteChoices.BACKGROUNDS,
                    selected = colors.background,
                    onPick = { value -> onChange { current -> current.copy(background = value) } },
                )
            }

            SettingsBlock("Texte") {
                Text(
                    "Sans choix, le texte est le noir ou le blanc qui se lit le mieux sur le fond.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Swatches(
                    values = WidgetPaletteChoices.INKS,
                    selected = colors.ink,
                    onPick = { value -> onChange { current -> current.copy(ink = value) } },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Revenir au texte automatique",
                    modifier = Modifier
                        .clickable { onChange { current -> current.copy(ink = null) } }
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingsBlock("Bon à savoir") {
            Text(
                "La couleur exacte d'un fond demande Android 12 ou plus : le système ne sait " +
                    "pas teinter un widget avant. En dessous, le widget garde le fond clair ou " +
                    "sombre le plus proche de celui choisi — une couleur approchée avec les " +
                    "bons coins arrondis vaut mieux qu'une couleur exacte dans un rectangle brut.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Les couleurs de matière ne changent pas ici : elles ont leur propre réglage, " +
                    "et les voir différer d'un écran à l'autre serait pire que tout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Un faux widget « journée », dessiné avec les couleurs choisies. */
@Composable
private fun WidgetPreview(background: Color, ink: Color, muted: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text("AUJOURD'HUI", style = MaterialTheme.typography.labelSmall, color = muted)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mar. 8",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Text("5 cours · 5 h 55", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple("08:05", "Mathématiques", "801"),
            Triple("11:55", "Pause", "1 h 10"),
            Triple("13:05", "Sciences Ingénieur", "C11"),
        ).forEachIndexed { index, (time, subject, trailing) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = muted)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(7.dp)
                        .background(previewDot(index), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    subject,
                    style = MaterialTheme.typography.bodySmall,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/** Les couleurs de matière ne sont pas réglables ici : l'aperçu montre celles qui existent. */
private fun previewDot(index: Int): Color = when (index) {
    0 -> Color(0xFF7779E8)
    1 -> Color(0xFF9AA3B2)
    else -> Color(0xFFC77BD1)
}

@Composable
private fun Swatches(values: List<Long>, selected: Long?, onPick: (Long) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(values, key = { it }) { value ->
            val color = Color(value)
            Box(
                Modifier
                    .size(34.dp)
                    .background(color, CircleShape)
                    .border(
                        // Un contour permanent : un blanc sur fond blanc serait invisible
                        // sans lui, et on choisirait une couleur qu'on ne voit pas.
                        width = if (value == selected) 3.dp else 1.dp,
                        color = if (value == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onPick(value) },
            )
        }
    }
}

@Composable
private fun SettingsBlock(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(Modifier.padding(horizontal = ScreenPadding)) {
        AppCard(content = content)
    }
}

private fun previewBackground(chosen: Long?, dark: Boolean): Color = when {
    chosen != null -> Color(chosen)
    dark -> Color(0xFF171D26)
    else -> Color(0xFFFFFFFF)
}

private fun previewInk(chosen: Long?, dark: Boolean): Color = when {
    chosen != null -> Color(chosen)
    dark -> Color(0xFFEDF1F7)
    else -> Color(0xFF18202B)
}
