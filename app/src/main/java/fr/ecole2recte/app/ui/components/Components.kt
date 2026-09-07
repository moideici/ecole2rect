package fr.ecole2recte.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Text as TextUtils
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.SubjectPalette

/**
 * Les briques visuelles communes.
 *
 * L'application n'a qu'un vocabulaire : une carte, un en-tête, une pastille de matière, un
 * état vide, une erreur. Les écrans les assemblent, ils n'en réinventent pas. C'est ce qui
 * donne le sentiment d'une application cohérente plutôt que d'une pile de fonctionnalités.
 */

val CardShape = RoundedCornerShape(22.dp)
val SmallCardShape = RoundedCornerShape(16.dp)
val ScreenPadding = 18.dp

@Composable
fun subjectColor(subject: String, settings: AppSettings): Color =
    Color(SubjectPalette.colorFor(subject, settings.subjectColors))

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: RoundedCornerShape = CardShape,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = null,
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** En-tête de page : la marque, le titre, une ligne de contexte et les actions. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                title,
                // Un lecteur d'écran saute de titre en titre : sans cette marque, il faut
                // parcourir tout l'écran pour savoir où l'on est.
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding + 4.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        if (action != null && onAction != null) {
            Text(
                action,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Pastille de couleur d'une matière. Décorative : jamais annoncée par le lecteur d'écran. */
@Composable
fun SubjectDot(color: Color, size: Dp = 9.dp) {
    Box(
        Modifier
            .size(size)
            .clearAndSetSemantics { }
            .background(color, CircleShape),
    )
}

/** Vignette d'une matière : deux lettres sur sa couleur. */
@Composable
fun SubjectBadge(subject: String, color: Color, size: Dp = 46.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(size / 3))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            TextUtils.initials(subject),
            color = color,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Barre de progression dessinée à la main : deux boîtes valent mieux qu'un composant. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 4.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(trackColor, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(indicatorColor, CircleShape),
        )
    }
}

/** Petite étiquette d'information (« évaluation », « annulé », « 2 urgents »). */
@Composable
fun Tag(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    container: Color = color.copy(alpha = 0.12f),
) {
    Text(
        text,
        modifier = Modifier
            .background(container, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** Bouton-filtre : sélectionné, il prend la couleur d'accent. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            // Un filtre est un bouton : il doit s'annoncer comme tel, se laisser cocher au
            // clavier, et rester atteignable au doigt même quand son texte est court.
            .minimumInteractiveComponentSize()
            .background(background, CircleShape)
            .border(1.dp, if (selected) background else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val container = if (enabled && !loading) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Bouton d'icône discret, avec sa description pour les lecteurs d'écran. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    // En dernier : c'est le paramètre auquel se lie une lambda finale, et un bouton
    // s'écrit naturellement `RoundIconButton(icon, "Action") { ... }`.
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(42.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * État vide ou en erreur.
 *
 * Un écran sans données doit dire ce qui se passe et proposer une suite : « rien à afficher »
 * tout seul est un cul-de-sac.
 */
@Composable
fun StatePlaceholder(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                actionLabel,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Bandeau d'erreur d'une section.
 *
 * Il accompagne les données au lieu de les remplacer : ce qui a déjà été synchronisé reste
 * affiché, et l'utilisateur sait simplement que cette partie n'a pas pu être rafraîchie.
 */
@Composable
fun SectionError(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 6.dp),
        shape = SmallCardShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (onRetry != null) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Réessayer",
                    modifier = Modifier
                        .clickable(onClick = onRetry)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
fun LoadingRow(label: String = "Chargement…") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
