package fr.ecole2recte.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette actuelle, déplacée telle quelle. Le lot 5 la transformera en jetons de design.
internal val Ink = Color(0xFF18202B)
internal val Muted = Color(0xFF7F8998)
internal val Background = Color(0xFFF5F7FB)
internal val Indigo = Color(0xFF5C62E8)
internal val CardWhite = Color.White
internal val Positive = Color(0xFF2AA270)
internal val PositiveDark = Color(0xFF2A8B68)
internal val ErrorRed = Color(0xFFB74D58)
internal val ReminderSurface = Color(0xFF202935)
internal val DiagnosticSurface = Color(0xFFFFF7ED)
internal val DiagnosticTitle = Color(0xFF9A5B1A)
internal val DiagnosticText = Color(0xFF5F4A32)

internal val SubjectColors = listOf(
    Color(0xFF7779E8),
    Color(0xFF63A6E9),
    Color(0xFFEFA36A),
    Color(0xFF5CBD92),
)

@Composable
fun Ecole2recteTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
