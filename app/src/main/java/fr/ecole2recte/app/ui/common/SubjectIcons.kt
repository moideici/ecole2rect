package fr.ecole2recte.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.ui.theme.subjectColor
import java.text.Normalizer
import java.util.Locale

private fun plain(name: String): String =
    Normalizer.normalize(name.lowercase(Locale.FRENCH), Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

/**
 * Une icône pour chaque matière, devinée d'après son nom. ÉcoleDirecte n'en fournit pas : on
 * reconnaît les mots usuels (« maths », « anglais », « histoire-géo »…) et, à défaut, on met
 * l'icône d'école. Fonction pure, testée.
 */
fun subjectIcon(name: String): ImageVector {
    val n = plain(name)
    fun has(vararg words: String) = words.any { n.contains(it) }
    return when {
        has("math") -> Icons.Outlined.Calculate
        has("philo") -> Icons.Outlined.Psychology
        has("francais", "lettres", "litterature") -> Icons.AutoMirrored.Outlined.MenuBook
        has("anglais", "allemand", "espagnol", "italien", "chinois", "arabe", "portugais", "russe", "langue", "lv1", "lv2", "lv3") -> Icons.Outlined.Translate
        has("histoire", "geo", "emc", "enseignement moral", "civique") -> Icons.Outlined.Public
        has("physique", "chimie", "science", "pc ", "labo") && !has("svt", "vie et de la terre", "econom", "sociales") -> Icons.Outlined.Science
        has("svt", "vie et de la terre", "biologie", "agronomie") -> Icons.Outlined.Eco
        has("eps", "sport", "education physique", "natation") -> Icons.Outlined.SportsSoccer
        has("musique", "chant", "chorale") -> Icons.Outlined.MusicNote
        has("art", "dessin", "plastique", "design") -> Icons.Outlined.Palette
        has("techno", "nsi", "informatique", "numerique", "snt", "sti2d", "ingenieur") -> Icons.Outlined.Memory
        has("ses", "econom", "gestion", "droit", "commerce") -> Icons.Outlined.Paid
        has("latin", "grec", "antiquite") -> Icons.Outlined.AccountBalance
        has("vie de classe", "accompagnement", "tutorat", "orientation", "aide", "etude") -> Icons.Outlined.Groups
        else -> Icons.Outlined.School
    }
}

/** Pastille ronde : l'icône de la matière sur fond clair de sa couleur. */
@Composable
fun SubjectBadge(name: String, colorIndex: Int, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val color = subjectColor(name, colorIndex)
    Box(
        modifier.size(size).background(color.copy(alpha = .18f), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(subjectIcon(name), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.58f))
    }
}
