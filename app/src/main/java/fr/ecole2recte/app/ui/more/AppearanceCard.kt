package fr.ecole2recte.app.ui.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.TextScale
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.ui.theme.ThemePalette
import fr.ecole2recte.app.ui.theme.ThemePalettes

/**
 * Plus > Apparence : mode (système, clair, sombre, noir), thème de couleurs avec aperçu et taille du texte.
 * Les changements s'appliquent tout de suite à toute l'application, et sont mémorisés.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppearanceCard() {
    val settings = rememberAppContainer().settings
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val colorTheme by settings.colorTheme.collectAsStateWithLifecycle()
    val textScale by settings.textScale.collectAsStateWithLifecycle()
    val appearance = themeMode.appearance(isSystemInDarkTheme())
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Mode", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { settings.setThemeMode(mode) },
                        label = { Text(mode.label, fontSize = 13.sp) },
                    )
                }
            }

            Text("Thème de couleurs", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorTheme.entries.forEach { theme ->
                    ThemeTile(ThemePalettes.of(theme, appearance), selected = theme == colorTheme) { settings.setColorTheme(theme) }
                }
            }

            Text("Taille du texte", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextScale.entries.forEach { scale ->
                    FilterChip(
                        selected = textScale == scale,
                        onClick = { settings.setTextScale(scale) },
                        label = { Text(scale.label, fontSize = 13.sp) },
                    )
                }
            }
            TextPreview()
        }
    }
}

/** Une pastille de thème : un petit écran peint dans ses couleurs, et son nom. */
@Composable
private fun ThemeTile(palette: ThemePalette, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val border = if (selected) BorderStroke(3.dp, AppTheme.colors.accent) else BorderStroke(1.dp, AppTheme.colors.muted.copy(alpha = .4f))
    Column(
        Modifier.width(104.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .border(border, shape)
            .background(Color(palette.background), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().background(Color(palette.surface), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(10.dp).background(Color(palette.accent), CircleShape))
                    Box(Modifier.height(5.dp).width(36.dp).background(Color(palette.ink), RoundedCornerShape(3.dp)))
                }
                Box(Modifier.height(4.dp).width(54.dp).background(Color(palette.muted), RoundedCornerShape(3.dp)))
                Box(Modifier.height(10.dp).width(40.dp).background(Color(palette.accentFill), RoundedCornerShape(5.dp)))
            }
        }
        Text(
            palette.theme.label,
            color = Color(palette.ink),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 14.sp,
        )
    }
}

/** Un exemple de texte pour juger la taille choisie (elle s'applique déjà à toute l'application). */
@Composable
private fun TextPreview() {
    Column(
        Modifier.fillMaxWidth().background(AppTheme.colors.background, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text("Maths · 10:15 · salle B204", color = AppTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Contrôle jeudi", color = AppTheme.colors.muted, fontSize = 14.sp)
    }
}
