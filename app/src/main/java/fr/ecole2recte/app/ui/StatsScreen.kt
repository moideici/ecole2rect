package fr.ecole2recte.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text as Format
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ProgressBar
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * L'année en chiffres.
 *
 * Un bilan que le service ne donne nulle part et qu'on ne peut pas faire de tête : combien de
 * notes, dans quelle matière on tient et dans laquelle on lâche, combien de devoirs cochés,
 * combien d'absences.
 *
 * Chaque carte dit **sur quoi** elle porte, et c'est le point délicat — y compris quand la
 * réponse est décevante. Notes, vie scolaire et messagerie couvrent l'année, parce
 * qu'ÉcoleDirecte les donne en entier ; la moyenne, elle, est recalculée, l'établissement n'en
 * publiant que par période. L'emploi du temps ne se charge qu'une semaine à la fois, donc la
 * carte des cours dit « cette semaine », et rien de plus. Le cahier de textes part du jour
 * présent : son total ne couvre pas l'année non plus, et la carte le dit.
 */
@Composable
fun StatsScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onClose: () -> Unit,
) {
    val stats = Insights.yearStats(snapshot, settings, today)
    DetailScaffold(
        title = "En chiffres",
        subtitle = "Ce que l'année dit jusqu'ici",
        onClose = onClose,
    ) {
        if (snapshot.isEmpty) {
            StatePlaceholder(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                title = "Rien à compter",
                description = "Les chiffres arrivent avec la première synchronisation.",
            )
            return@DetailScaffold
        }

        StatsCard(title = "Notes", scope = "année · moyenne recalculée") {
            Row(verticalAlignment = Alignment.Bottom) {
                BigFigure(stats.gradeCount.toString(), if (stats.gradeCount > 1) "notes" else "note")
                Spacer(Modifier.width(24.dp))
                stats.average?.let { BigFigure(Format.number(it), "de moyenne") }
            }
            // Ce que les cinq dernières notes font vraiment à la moyenne — la moyenne telle
            // qu'elle est, moins celle qu'elle serait sans elles. La tendance de l'accueil
            // répond à une autre question (l'écart avec les cinq d'avant) et le chiffre y est
            // environ deux fois plus gros : l'afficher ici disait faux.
            stats.recentEffect?.let { effect ->
                Spacer(Modifier.height(10.dp))
                Text(
                    if (effect >= 0) {
                        "Les cinq dernières notes valent +${Format.number(effect)} sur la moyenne"
                    } else {
                        "Les cinq dernières notes coûtent ${Format.number(effect)} à la moyenne"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (effect >= 0) AppTheme.palette.success else AppTheme.palette.warning,
                )
            }
            if (stats.uncountedGrades > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${stats.uncountedGrades} note${if (stats.uncountedGrades > 1) "s" else ""} " +
                        "publiée${if (stats.uncountedGrades > 1) "s" else ""} hors moyenne, " +
                        "non comptée${if (stats.uncountedGrades > 1) "s" else ""} ici",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            stats.bestSubject?.let { (subject, value) ->
                Spacer(Modifier.height(10.dp))
                StatLine("Matière la plus forte", "$subject · ${Format.number(value)}")
            }
            // La plus faible n'est affichée que s'il y a de quoi comparer : avec une seule
            // matière notée, « la plus faible » serait aussi la plus forte, ce qui est idiot.
            stats.weakestSubject?.takeIf { it != stats.bestSubject }?.let { (subject, value) ->
                StatLine("Matière la plus fragile", "$subject · ${Format.number(value)}")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                // L'établissement ne publie de moyenne que par période : celle-ci est
                // pondérée par les coefficients, toutes périodes confondues. Ce n'est pas
                // le chiffre du bulletin, et le taire serait le faire passer pour lui.
                "Moyenne pondérée sur toute l'année, calculée depuis les notes : " +
                    "l'établissement n'en publie que par période.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatsCard(title = "Devoirs", scope = "le cahier de textes chargé") {
            Row(verticalAlignment = Alignment.Bottom) {
                BigFigure("${stats.homeworkDone}", "cochés")
                Spacer(Modifier.width(24.dp))
                BigFigure("${stats.homeworkTotal}", "au total")
            }
            if (stats.homeworkTotal > 0) {
                Spacer(Modifier.height(12.dp))
                ProgressBar(fraction = stats.homeworkDone.toFloat() / stats.homeworkTotal)
            }
            if (stats.examCount > 0) {
                Spacer(Modifier.height(10.dp))
                StatLine("Évaluations annoncées", stats.examCount.toString())
            }
            Spacer(Modifier.height(10.dp))
            Text(
                // Annoncer « tout le cahier de textes » était faux : le travail chargé part
                // d'aujourd'hui, donc un devoir ancien finit par sortir du compte.
                "Le cahier de textes chargé porte sur les jours à venir : un devoir ancien " +
                    "n'y reste pas, et ce total n'est donc pas celui de l'année.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatsCard(title = "Cours", scope = "cette semaine seulement") {
            Row(verticalAlignment = Alignment.Bottom) {
                BigFigure(stats.lessonsThisWeek.toString(), "cours")
                Spacer(Modifier.width(24.dp))
                BigFigure(Dates.duration(stats.minutesThisWeek), "de classe")
            }
            stats.busiestDay?.let { (date, minutes) ->
                Spacer(Modifier.height(10.dp))
                StatLine(
                    "Journée la plus chargée",
                    "${Dates.dayName(date).replaceFirstChar { it.uppercase() }} · ${Dates.duration(minutes)}",
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                // Le dire, plutôt que de laisser croire à un total annuel qui n'existe pas.
                "L'emploi du temps se charge une semaine à la fois : un total depuis " +
                    "septembre serait un chiffre inventé.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatsCard(title = "Vie scolaire", scope = "depuis le début de l'année") {
            Row(verticalAlignment = Alignment.Bottom) {
                BigFigure(stats.absences.toString(), if (stats.absences > 1) "absences" else "absence")
                Spacer(Modifier.width(24.dp))
                BigFigure(stats.late.toString(), if (stats.late > 1) "retards" else "retard")
            }
            if (stats.unjustifiedAbsences > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${stats.unjustifiedAbsences} absence${if (stats.unjustifiedAbsences > 1) "s" else ""} " +
                        "encore à justifier",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.palette.warning,
                )
            }
        }

        StatsCard(title = "Messagerie", scope = "ce qui a été chargé") {
            Row(verticalAlignment = Alignment.Bottom) {
                BigFigure(stats.messagesReceived.toString(), "reçus")
                Spacer(Modifier.width(24.dp))
                BigFigure(stats.messagesSent.toString(), "envoyés")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "La messagerie se charge page par page : ces nombres grandissent à mesure " +
                    "qu'on remonte dans les anciens messages.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Une carte de chiffres, avec sa portée annoncée en tête : sans elle, un total ment. */
@Composable
private fun StatsCard(
    title: String,
    scope: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
        AppCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    scope,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun BigFigure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
