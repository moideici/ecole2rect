package fr.ecole2recte.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.MessageFolder
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.NoticeCard
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SectionError
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.LocalDateTime

/**
 * La messagerie.
 *
 * Lecture seule et assumée : l'application ne prétend pas remplacer l'envoi de messages
 * d'ÉcoleDirecte, mais elle rend la lecture agréable et signale clairement le non-lu.
 *
 * Les trois dossiers du service sont là, et pas seulement les messages reçus : un message
 * envoyé depuis le site existait bel et bien, l'application se contentait de l'ignorer. Les
 * envoyés et la corbeille se chargent au premier passage sur leur onglet, jamais avant.
 *
 * La Corbeille remplace l'ancien onglet « Archivés ». Elle range côte à côte ce qu'ÉcoleDirecte
 * a archivé et ce qu'on y met soi-même — et le rangement s'arrête à l'appareil : le service ne
 * sait pas supprimer une conversation, l'application ne prétend donc pas le faire.
 */
@Composable
fun MessagesScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    folder: MessageFolder,
    onSelectFolder: (MessageFolder) -> Unit,
    onOpen: (MessageItem) -> Unit,
    onBin: (MessageItem, Boolean) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }

    val bin = folder == MessageFolder.ARCHIVED
    val unread = Insights.unreadMessages(snapshot.inbox, settings)
    // La Corbeille réunit ce que le serveur a archivé et ce qu'on y a rangé soi-même ; les
    // autres dossiers, eux, ne montrent plus ce qui est à la corbeille.
    val inFolder = snapshot.messages.filter { message ->
        val binned = settings.isMessageBinned(message.id)
        if (bin) message.folder == MessageFolder.ARCHIVED || binned
        else message.folder == folder && !binned
    }
    val visible = inFolder
        // Le non-lu ne veut rien dire dans les envoyés : le filtre disparaît avec l'onglet.
        .filter { folder != MessageFolder.RECEIVED || !unreadOnly || !settings.isMessageRead(it.id, it.read) }
        .filter { Insights.matches(query, it.subject, it.counterpart, it.preview) }

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            ScreenHeader(
                eyebrow = "Messagerie",
                title = "Messages",
                subtitle = when {
                    bin -> if (inFolder.size == 1) "1 conversation rangée" else "${inFolder.size} conversations rangées"
                    folder != MessageFolder.RECEIVED -> folder.label.lowercase().replaceFirstChar { it.uppercase() }
                    unread.isEmpty() -> "Tout est lu"
                    unread.size == 1 -> "1 message non lu"
                    else -> "${unread.size} messages non lus"
                },
                actions = {
                    RoundIconButton(Icons.Outlined.Search, "Rechercher", onClick = onSearch)
                    RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh)
                },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                placeholder = {
                    Text(
                        if (folder == MessageFolder.SENT) {
                            "Rechercher un destinataire, un objet…"
                        } else {
                            "Rechercher un expéditeur, un objet…"
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        item("dossiers") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MessageFolder.entries, key = { it.name }) { entry ->
                    FilterPill(text = entry.label, selected = folder == entry) { onSelectFolder(entry) }
                }
            }
        }

        if (folder == MessageFolder.RECEIVED) {
            item("filtre-lecture") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf(false, true), key = { it.toString() }) { onlyUnread ->
                        FilterPill(
                            text = if (onlyUnread) "Non lus" else "Tous",
                            selected = unreadOnly == onlyUnread,
                        ) { unreadOnly = onlyUnread }
                    }
                }
            }
        }

        if (bin) {
            item("corbeille-note") {
                NoticeCard(
                    "Rangement local. ÉcoleDirecte ne permet pas de supprimer une conversation : " +
                        "rien n'est effacé sur le service, et tout reste lisible ici.",
                )
            }
        }

        sync.errorFor(DataSection.MESSAGES)?.let { error ->
            item { SectionError(error, onRefresh) }
        }

        if (visible.isEmpty()) {
            item {
                StatePlaceholder(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = if (query.isBlank()) "Aucun message" else "Aucun résultat",
                    description = when {
                        query.isNotBlank() || unreadOnly -> "Rien ne correspond à ce filtre."
                        bin -> "Rien n'y a encore été rangé."
                        folder == MessageFolder.RECEIVED -> "La messagerie n'a pas encore été synchronisée."
                        else -> "Ce dossier est vide."
                    },
                    actionLabel = "Synchroniser",
                    onAction = onRefresh,
                )
            }
        }

        items(visible, key = { it.id }) { message ->
            val read = settings.isMessageRead(message.id, message.read)
            // Rangé ici, ou archivé sur ÉcoleDirecte : la Corbeille montre les deux, mais
            // seul le premier peut être défait — l'application n'écrit rien sur le service.
            val binnedHere = settings.isMessageBinned(message.id)
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard(onClick = { onOpen(message) }) {
                    Row(verticalAlignment = Alignment.Top) {
                        // Dans les envoyés, la vignette et la ligne de tête portent le
                        // destinataire : « moi » en tête de chaque message n'apprendrait rien.
                        val counterpart = message.counterpart
                        SubjectBadge(counterpart, subjectColor(counterpart, settings), 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    counterpart,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                message.date?.let { date ->
                                    Text(
                                        // L'heure pour aujourd'hui : « aujourd'hui » ne
                                        // distingue pas un message de 8 h d'un message reçu
                                        // il y a deux minutes.
                                        Dates.listStamp(date, now.toLocalDate()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                message.subject,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (message.preview.isNotBlank()) {
                                Text(
                                    message.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (!read && !bin) {
                            Spacer(Modifier.width(8.dp))
                            SubjectDot(MaterialTheme.colorScheme.primary, 8.dp)
                        }
                        // Ranger ou ressortir : le même geste dans les deux sens, et jamais
                        // un mot qui laisserait croire à une suppression.
                        Spacer(Modifier.width(4.dp))
                        when {
                            !bin -> RoundIconButton(
                                icon = Icons.Outlined.DeleteOutline,
                                contentDescription = "Ranger à la corbeille",
                                onClick = { onBin(message, true) },
                            )

                            binnedHere -> RoundIconButton(
                                icon = Icons.Outlined.RestoreFromTrash,
                                contentDescription = "Sortir de la corbeille",
                                onClick = { onBin(message, false) },
                            )

                            // Archivé sur le site : l'application ne peut pas l'en sortir,
                            // et un bouton qui ne ferait rien serait pire que pas de bouton.
                            else -> Tag("Archivé", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // La messagerie d'une année scolaire dépasse une page : on la charge à la demande,
        // pas d'un coup à l'ouverture de l'écran.
        if (canLoadMore && query.isBlank() && !unreadOnly) {
            item("plus") {
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)) {
                    AppCard(onClick = { if (!loadingMore) onLoadMore() }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                if (loadingMore) "Chargement…" else "Charger les messages plus anciens",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
