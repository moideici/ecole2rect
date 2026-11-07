package fr.ecole2recte.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.domain.ExamCountdown
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.domain.UpcomingExam
import fr.ecole2recte.app.ui.theme.AppTheme
import fr.ecole2recte.app.ui.theme.subjectColor
import java.time.LocalDate

/**
 * Compte à rebours avant les prochains contrôles : « Contrôle de Maths dans 3 jours ». Le même composant
 * sert à l'accueil et dans l'agenda ; [compact] n'en garde que le plus proche, sur une ligne.
 */
@Composable
fun ExamCountdownCard(
    exams: List<UpcomingExam>,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (exams.isEmpty()) return
    val shown = if (compact) exams.take(1) else exams
    val first = shown.first()
    val days = ExamCountdown.daysUntil(first, today)
    val urgent = days <= 1
    Card(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 0.dp)
            .semantics { contentDescription = ExamCountdown.label(first, today) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(AppTheme.colors.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = subjectColor(first.subject, first.colorIndex))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Contrôle de ${first.subject.ifBlank { "classe" }}", color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (!compact && first.detail.isNotBlank()) {
                        Text(first.detail, color = AppTheme.colors.muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Text(
                    ExamCountdown.delay(days),
                    color = if (urgent) AppTheme.colors.error else AppTheme.colors.accent,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            shown.drop(1).forEach { exam ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 36.dp)) {
                    Text("Contrôle de ${exam.subject.ifBlank { "classe" }}", color = AppTheme.colors.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(ExamCountdown.delay(ExamCountdown.daysUntil(exam, today)), color = AppTheme.colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Les prochains contrôles pour l'agenda. Lit les devoirs par le dépôt, qui les garde en mémoire quelques
 * instants : l'onglet « Travail à faire » juste à côté les demande déjà, donc aucune requête de plus.
 */
@Composable
fun rememberUpcomingExams(repository: SchoolRepository): List<UpcomingExam> {
    var exams by remember(repository) { mutableStateOf(emptyList<UpcomingExam>()) }
    LaunchedEffect(repository) {
        exams = runCatching { ExamCountdown.upcoming(repository.homework(), LocalDate.now()) }.getOrDefault(emptyList())
    }
    return exams
}
