package fr.ecole2recte.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.AttachmentState
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Text as Format
import fr.ecole2recte.app.core.model.Attachment
import fr.ecole2recte.app.core.model.CloudFile
import fr.ecole2recte.app.core.model.NewsItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.model.Textbook
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import fr.ecole2recte.app.core.settings.AppSettings

/**
 * Les modules que l'établissement ouvre, ou non.
 *
 * Actualités, manuels numériques et espace de stockage existent dans ÉcoleDirecte mais ne
 * sont pas activés partout. Chacun se charge à l'ouverture de son écran plutôt qu'à chaque
 * synchronisation : demander toutes les heures un module fermé serait trois requêtes
 * refusées par heure, et un journal d'erreurs qu'on apprendrait à ignorer. Un module fermé
 * n'affiche donc pas une erreur, mais un écran qui dit ce qu'il en est.
 */

/** Les actualités de l'établissement : ce que l'école adresse à tout le monde. */
@Composable
fun NewsScreen(
    snapshot: SchoolSnapshot,
    attachmentState: AttachmentState,
    onOpenAttachment: (Attachment) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val news = snapshot.news
    DetailScaffold(
        title = "Actualités",
        subtitle = if (news.isEmpty()) null else Format.count(news.size, "publication"),
        onClose = onClose,
    ) {
        if (news.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.Campaign,
                title = "Aucune actualité",
                description = "L'établissement n'a rien publié, ou le module « vie de " +
                    "l'établissement » n'est pas ouvert pour ce compte.",
                // Ces écrans ne se chargent qu'à leur ouverture : sans ce bouton, un échec
                // réseau resterait un écran vide jusqu'à la synchronisation suivante.
                actionLabel = "Réessayer",
                onAction = onRefresh,
            )
            return@DetailScaffold
        }
        news.forEach { item -> NewsCard(item, attachmentState, onOpenAttachment) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    attachmentState: AttachmentState,
    onOpenAttachment: (Attachment) -> Unit,
) {
    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            item.date?.let { Dates.dayAndMonth(it) },
                            item.author.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { "Publication" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.category.isNotBlank()) Tag(item.category)
            }
            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                SelectionContainer {
                    Text(item.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (item.attachments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AttachmentList(item.attachments, attachmentState, onOpenAttachment)
            }
        }
    }
}

/** Les manuels numériques, groupés par matière comme le reste de l'application. */
@Composable
fun TextbooksScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    onOpen: (Textbook) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val textbooks = snapshot.textbooks
    DetailScaffold(
        title = "Manuels numériques",
        subtitle = if (textbooks.isEmpty()) null else Format.count(textbooks.size, "manuel"),
        onClose = onClose,
    ) {
        if (textbooks.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.MenuBook,
                title = "Aucun manuel",
                description = "Aucun manuel numérique n'est associé à ce compte, ou le module " +
                    "n'est pas ouvert par l'établissement.",
                actionLabel = "Réessayer",
                onAction = onRefresh,
            )
            return@DetailScaffold
        }
        textbooks.forEach { book ->
            // Un manuel sans lien ne s'ouvre pas : la carte ne fait alors pas semblant
            // d'être cliquable.
            val open: (() -> Unit)? = if (book.hasLink) {
                { onOpen(book) }
            } else {
                null
            }
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard(onClick = open) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val subject = book.subject.ifBlank { book.title }
                        SubjectBadge(subject, subjectColor(subject, settings), 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(book.subject, book.publisher)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "Manuel numérique" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (book.hasLink) Tag("Ouvrir")
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** L'espace de stockage, à plat : retrouver un fichier et l'ouvrir, rien de plus. */
@Composable
fun CloudScreen(
    snapshot: SchoolSnapshot,
    attachmentState: AttachmentState,
    onOpen: (CloudFile) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val files = snapshot.cloudFiles
    DetailScaffold(
        title = "Espace de stockage",
        subtitle = if (files.isEmpty()) null else Format.count(files.size, "fichier"),
        onClose = onClose,
    ) {
        if (files.isEmpty()) {
            StatePlaceholder(
                icon = Icons.Outlined.CloudQueue,
                title = "Espace vide",
                description = "Aucun fichier partagé, ou l'espace de stockage n'est pas " +
                    "ouvert pour ce compte.",
                actionLabel = "Réessayer",
                onAction = onRefresh,
            )
            return@DetailScaffold
        }
        files.groupBy { it.folder.ifBlank { "Racine" } }.forEach { (folder, entries) ->
            Text(
                folder.uppercase(),
                modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entries.forEach { file ->
                val working = (attachmentState as? AttachmentState.Working)?.id == file.id
                val failure = (attachmentState as? AttachmentState.Failed)?.takeIf { it.id == file.id }
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                    AppCard(onClick = { onOpen(file) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        file.date?.let { Dates.dayAndMonth(it) },
                                        file.size?.let(::readableSize),
                                    ).joinToString(" · ").ifBlank { "Fichier partagé" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (working) Tag("Ouverture…")
                        }
                        if (failure != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                failure.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** « 1,2 Mo » plutôt que « 1258291 » : la taille sert à choisir, pas à compter. */
private fun readableSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f Mo".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} ko"
    else -> "$bytes o"
}
