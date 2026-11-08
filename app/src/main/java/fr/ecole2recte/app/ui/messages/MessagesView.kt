package fr.ecole2recte.app.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.IconButton
import fr.ecole2recte.app.data.applyReadMarks
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.domain.MessageDetail
import fr.ecole2recte.app.domain.MessagePreview
import fr.ecole2recte.app.domain.MessageDraft
import fr.ecole2recte.app.ui.common.AttachmentList
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.MessagesViewModel
import fr.ecole2recte.app.ui.common.PageHeader
import fr.ecole2recte.app.ui.common.ScrollToTopOnReselect
import fr.ecole2recte.app.ui.common.RefreshOnResume
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberAppContainer
import fr.ecole2recte.app.ui.common.entrance
import fr.ecole2recte.app.ui.common.linkified
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.common.LocalPrivacy
import fr.ecole2recte.app.ui.theme.AppTheme
import java.text.Normalizer

@Composable
fun MessagesRoute() {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { MessagesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel)
    StateHost(state, onRetry = viewModel::retry, refreshing = refreshing, onRefresh = viewModel::pullToRefresh, notices = viewModel.notices) { first ->
        MessagesContent(first, viewModel)
    }
}

private fun String.plain(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesContent(first: List<MessagePreview>, viewModel: MessagesViewModel) {
    val more by viewModel.more.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val readHere by viewModel.readHere.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect("messages", listState)
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var unreadOnly by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<MessagePreview?>(null) }
    val canSend by rememberAppContainer().settings.sendMessages.collectAsStateWithLifecycle()
    var composing by remember { mutableStateOf(false) }
    var initialDraft by remember { mutableStateOf<MessageDraft?>(null) }
    var sentNotice by remember { mutableStateOf(false) }

    // Marques locales (lu / non lu choisis ici, jamais écrits chez ÉcoleDirecte) : elles passent après « ouvert pendant cette visite ».
    val container = rememberAppContainer()
    val ownerKey = LocalActiveProfile.current?.key.orEmpty()
    val marks by remember(ownerKey) { container.messageMarks.marks(ownerKey) }.collectAsStateWithLifecycle(emptyMap())
    val privacy = LocalPrivacy.current
    val all = remember(first, more, readHere, marks, privacy) {
        privacy.maskMessages(applyReadMarks((first + more).map { if (it.id in readHere) it.copy(unread = false) else it }, marks))
    }
    val shown = remember(all, query, unreadOnly) {
        val needle = query.plain().trim()
        all.filter { message ->
            (!unreadOnly || message.unread) &&
                (needle.isEmpty() || message.sender.plain().contains(needle) || message.preview.plain().contains(needle))
        }
    }
    val unreadCount = all.count { it.unread }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeader(
                "Messages",
                if (unreadCount == 0) "" else if (unreadCount == 1) "1 non lu" else "$unreadCount non lus",
            )
        }
        if (canSend) {
            item {
                Text(
                    if (sentNotice) "Envoyé · Nouveau message" else "Nouveau message",
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { initialDraft = null; composing = true }.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(unreadOnly, { unreadOnly = !unreadOnly }, label = { Text("Non lus", fontSize = 13.sp) })
                FilterChip(
                    searching || query.isNotEmpty(),
                    { searching = !searching; if (!searching) query = "" },
                    label = { Text("Rechercher", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (searching || query.isNotEmpty()) {
            item {
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text("Expéditeur ou objet") },
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                if (all.isEmpty()) EmptyState("Aucun message")
                else EmptyState("Aucun résultat")
            }
        }
        itemsIndexed(shown, key = { index, message -> message.id.ifEmpty { "m$index" } }) { index, message ->
            MessageRow(
                message,
                Modifier.entrance(index.coerceAtMost(8)),
                onToggleRead = { container.messageMarks.set(ownerKey, message.id, read = message.unread) },
            ) {
                // Mode « montrer à quelqu'un » : on n'ouvre pas un message masqué (l'ouvrir le marquerait lu chez ÉcoleDirecte).
                if (!privacy.hidesMessages) opened = message
            }
        }
        if (hasMore && all.size >= 50) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (loadingMore) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = AppTheme.colors.accent)
                    } else {
                        Text(
                            "Messages plus anciens",
                            color = AppTheme.colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { viewModel.loadMore() }.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
    opened?.let { message ->
        ModalBottomSheet(onDismissRequest = { opened = null }, containerColor = AppTheme.colors.surface) {
            MessageSheet(message, viewModel, canReply = canSend,
                onOpened = { container.messageMarks.forget(ownerKey, message.id) },
                onReply = { draft ->
                    initialDraft = draft
                    opened = null
                    composing = true
                },
            )
        }
    }
    if (composing && canSend) {
        ModalBottomSheet(onDismissRequest = { composing = false }, containerColor = AppTheme.colors.surface) {
            ComposeSheet(viewModel, initialDraft = initialDraft, onSent = {
                composing = false
                sentNotice = true
            })
        }
    }
}

@Composable
private fun MessageRow(message: MessagePreview, modifier: Modifier, onToggleRead: () -> Unit, onClick: () -> Unit) {
    ListCard(modifier, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 6.dp).size(9.dp)
                    .background(if (message.unread) AppTheme.colors.accent else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row {
                    Text(
                        message.sender,
                        color = AppTheme.colors.ink,
                        fontWeight = if (message.unread) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(message.dateLabel, color = AppTheme.colors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    message.preview,
                    color = if (message.unread) AppTheme.colors.ink else AppTheme.colors.muted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (message.id.isNotBlank()) {
                IconButton(onClick = onToggleRead, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (message.unread) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread,
                        contentDescription = if (message.unread) "Marquer comme lu sur ce téléphone" else "Marquer comme non lu sur ce téléphone",
                        tint = AppTheme.colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageSheet(message: MessagePreview, viewModel: MessagesViewModel, canReply: Boolean, onOpened: () -> Unit, onReply: (MessageDraft) -> Unit) {
    var detail by remember(message.id) { mutableStateOf<Result<MessageDetail>?>(null) }
    LaunchedEffect(message.id) {
        detail = runCatching { viewModel.open(message) }
        // Ouvert, donc lu chez ÉcoleDirecte : la marque locale n'a plus lieu d'être.
        if (detail?.isSuccess == true) onOpened()
    }
    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message.preview, color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message.sender, color = AppTheme.colors.muted, fontSize = 14.sp)
        when (val loaded = detail) {
            null -> CircularProgressIndicator(Modifier.size(24.dp), color = AppTheme.colors.accent)
            else -> loaded.fold(
                onSuccess = { content ->
                    Text(content.dateLabel, color = AppTheme.colors.muted, fontSize = 13.sp)
                    if (content.recipients.isNotEmpty()) {
                        Text("À : " + content.recipients.joinToString(", "), color = AppTheme.colors.muted, fontSize = 13.sp)
                    }
                    SelectionContainer {
                        Text(linkified(content.body), color = AppTheme.colors.ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    AttachmentList(content.files, viewModel::download)
                    if (canReply) content.replyTo?.let { sender ->
                        TextButton(onClick = { onReply(MessageDraft.replyTo(content.subject, sender)) }) { Text("Répondre") }
                    }
                },
                onFailure = {
                    Text("Ouverture impossible.", color = AppTheme.colors.error, fontSize = 14.sp)
                },
            )
        }
    }
}
