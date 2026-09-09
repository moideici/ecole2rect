package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.AttachmentState
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Voice
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SessionContent
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.Duration
import java.time.LocalDate

/** Ossature commune des vues de détail : un retour, un titre, du contenu défilant. */
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        content()
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Fiche d'un cours.
 *
 * Tout ce que l'API sait de cette heure-là, plus ce qu'elle ne relie pas d'elle-même : les
 * devoirs à rendre pour cette matière ce jour-là.
 */
@Composable
fun LessonDetailScreen(
    lesson: Lesson?,
    settings: AppSettings,
    homework: List<HomeworkItem>,
    sessions: List<SessionContent>,
    onOpenHomework: (HomeworkItem) -> Unit,
    onOpenTeacher: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (lesson == null) {
        DetailScaffold("Cours", null, onClose) {
            StatePlaceholder(
                icon = Icons.Outlined.Schedule,
                title = "Cours introuvable",
                description = "Il a peut-être disparu de l'emploi du temps synchronisé.",
            )
        }
        return
    }
    val accent = subjectColor(lesson.subject, settings)
    DetailScaffold(
        title = lesson.subject,
        subtitle = "${Dates.dayAndMonth(lesson.date)} · ${Dates.range(lesson.start, lesson.end)}",
        onClose = onClose,
    ) {
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectBadge(lesson.subject, accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                        Text(
                            Dates.duration(Duration.between(lesson.start, lesson.end).toMinutes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lesson.canceled) Tag("Annulé", MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                DetailLine("Horaire", Dates.range(lesson.start, lesson.end))
                DetailLine("Salle", lesson.room.ifBlank { "non précisée" })
                // Le nom mène à sa fiche : c'est le seul endroit où l'on peut voir d'un
                // coup ses cours de la semaine, ses moyennes et son appréciation.
                if (lesson.teacher.isBlank()) {
                    DetailLine("Professeur", "non précisé")
                } else {
                    Box(Modifier.clickable { onOpenTeacher(lesson.teacher) }) {
                        DetailLine("Professeur", lesson.teacher)
                    }
                }
                // Le groupe change tout quand la classe est dédoublée : deux élèves de la
                // même classe ne sont alors pas au même cours.
                if (lesson.group.isNotBlank()) DetailLine("Groupe", lesson.group)
                if (lesson.exam) {
                    DetailLine("Type", "Devoir surveillé")
                } else if (lesson.kind.isNotBlank()) {
                    DetailLine("Type", lesson.kind)
                }
                if (lesson.note.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(lesson.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (sessions.isNotEmpty()) {
            Text(
                "CONTENU DE SÉANCE",
                modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sessions.forEach { session ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard {
                        SelectionContainer {
                            Text(session.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (homework.isNotEmpty()) {
            Text(
                "À RENDRE POUR CE COURS",
                modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            homework.forEach { item ->
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard(onClick = { onOpenHomework(item) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (item.exam) "Évaluation" else "Devoir",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent,
                                )
                                Text(
                                    item.content.ifBlank { "Contenu non publié" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Fiche d'un devoir.
 *
 * Le contenu complet n'est chargé qu'à l'ouverture — inutile de télécharger le cahier de
 * textes entier pour afficher une liste. En attendant, la fiche montre ce qu'elle sait déjà.
 */
@Composable
fun HomeworkDetailScreen(
    item: HomeworkItem?,
    settings: AppSettings,
    attachmentState: AttachmentState,
    parentView: Boolean = false,
    onToggle: (HomeworkItem) -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
    onClose: () -> Unit,
) {
    if (item == null) {
        DetailScaffold("Devoir", null, onClose) {
            StatePlaceholder(
                icon = Icons.Outlined.AttachFile,
                title = "Devoir introuvable",
                description = "Il a peut-être été retiré du cahier de textes.",
            )
        }
        return
    }
    val accent = subjectColor(item.subject, settings)
    val today = LocalDate.now()
    val done = settings.isHomeworkDone(item.id, item.done)

    DetailScaffold(
        title = item.subject,
        subtitle = Dates.dueLabel(item.dueDate, today).replaceFirstChar { it.uppercase() },
        onClose = onClose,
    ) {
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectBadge(item.subject, accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.subject, style = MaterialTheme.typography.titleMedium)
                        Text(
                            item.givenOn?.let { "Donné le ${Dates.dayAndMonth(it)}" } ?: "Date d'attribution inconnue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.exam) Tag("Évaluation", accent)
                }
                if (item.onlineSubmission) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "À rendre en ligne sur ÉcoleDirecte. L'application ne dépose pas de " +
                            "copie : cocher ce devoir ici ne le rend pas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.palette.warning,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (item.content.isBlank()) {
                    Text(
                        "Aucun énoncé n'a été publié pour ce devoir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Text(item.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (item.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    AttachmentList(item.attachments, attachmentState, onOpenAttachment)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (done) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { onToggle(item) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (done) "Marquer comme à faire" else "Marquer comme fait",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (done) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Depuis un compte parent, la coche ne quitte pas l'appareil : le dire évite
                // de croire qu'on rend un devoir à la place de son enfant.
                if (parentView) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        Voice.PARENT_HOMEWORK_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Un message, corps complet et pièces jointes listées. */
@Composable
fun MessageDetailScreen(
    message: MessageItem?,
    attachmentState: AttachmentState,
    onOpenAttachment: (Attachment) -> Unit,
    onClose: () -> Unit,
) {
    if (message == null) {
        DetailScaffold("Message", null, onClose) {
            StatePlaceholder(
                icon = Icons.Outlined.AttachFile,
                title = "Message introuvable",
                description = "Il a peut-être été supprimé de la messagerie.",
            )
        }
        return
    }
    DetailScaffold(
        title = message.subject,
        subtitle = listOfNotNull(
            message.sender.takeIf { it.isNotBlank() },
            message.date?.let { "${Dates.dayAndMonth(it.toLocalDate())} à ${Dates.time(it)}" },
        ).joinToString(" · "),
        onClose = onClose,
    ) {
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
            AppCard {
                if (message.body.isBlank()) {
                    Text(
                        "Le contenu de ce message n'a pas pu être chargé. Réessaie une fois le réseau revenu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Text(message.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (message.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    AttachmentList(message.attachments, attachmentState, onOpenAttachment)
                }
            }
        }
    }
}

/**
 * Pièces jointes.
 *
 * Toucher une ligne télécharge le fichier dans le cache privé et le confie à l'application
 * qui sait l'ouvrir. Le téléchargement est visible, et un refus du serveur s'affiche tel
 * quel — mieux vaut un message clair qu'un fichier de trente octets qui ne s'ouvre pas.
 */
@Composable
internal fun AttachmentList(
    attachments: List<Attachment>,
    state: AttachmentState,
    onOpen: (Attachment) -> Unit,
) {
    Text(
        "PIÈCES JOINTES",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    attachments.forEach { attachment ->
        val working = (state as? AttachmentState.Working)?.id == attachment.id
        val failure = (state as? AttachmentState.Failed)?.takeIf { it.id == attachment.id }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(attachment) }
                .padding(vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            if (failure != null) {
                Text(
                    failure.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
