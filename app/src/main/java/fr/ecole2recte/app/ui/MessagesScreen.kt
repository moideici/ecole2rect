package fr.ecole2recte.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import fr.ecole2recte.app.core.model.MessageItem
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SectionError
import fr.ecole2recte.app.ui.components.StatePlaceholder
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.subjectColor
import java.time.LocalDateTime

/**
 * La messagerie.
 *
 * Lecture seule et assumée : l'application ne prétend pas remplacer l'envoi de messages
 * d'ÉcoleDirecte, mais elle rend la lecture agréable et signale clairement le non-lu.
 */
@Composable
fun MessagesScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    onOpen: (MessageItem) -> Unit,
    onRefresh: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }

    val unread = Insights.unreadMessages(snapshot.messages, settings)
    val visible = snapshot.messages
        .filter { !unreadOnly || !settings.isMessageRead(it.id, it.read) }
        .filter { Insights.matches(query, it.subject, it.sender, it.preview) }

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            ScreenHeader(
                eyebrow = "Messagerie",
                title = "Messages",
                subtitle = when (unread.size) {
                    0 -> "Tout est lu"
                    1 -> "1 message non lu"
                    else -> "${unread.size} messages non lus"
                },
                actions = { RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh) },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                placeholder = { Text("Rechercher un expéditeur, un objet…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 10.dp),
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

        sync.errorFor(DataSection.MESSAGES)?.let { error ->
            item { SectionError(error, onRefresh) }
        }

        if (visible.isEmpty()) {
            item {
                StatePlaceholder(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = if (query.isBlank()) "Aucun message" else "Aucun résultat",
                    description = if (snapshot.messages.isEmpty()) {
                        "La messagerie n'a pas encore été synchronisée."
                    } else {
                        "Rien ne correspond à ce filtre."
                    },
                    actionLabel = "Synchroniser",
                    onAction = onRefresh,
                )
            }
        }

        items(visible, key = { it.id }) { message ->
            val read = settings.isMessageRead(message.id, message.read)
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)) {
                AppCard(onClick = { onOpen(message) }) {
                    Row(verticalAlignment = Alignment.Top) {
                        SubjectBadge(message.sender, subjectColor(message.sender, settings), 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    message.sender,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                message.date?.let { date ->
                                    Text(
                                        Dates.relativeDay(date.toLocalDate(), now.toLocalDate()),
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
                        if (!read) {
                            Spacer(Modifier.width(8.dp))
                            SubjectDot(MaterialTheme.colorScheme.primary, 8.dp)
                        }
                    }
                }
            }
        }
    }
}
