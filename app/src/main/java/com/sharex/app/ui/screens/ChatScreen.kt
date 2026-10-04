package com.sharex.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharex.app.data.AndroidReceiveStorage
import com.sharex.app.ui.clockTime
import com.sharex.app.ui.components.DeviceBadge
import com.sharex.app.ui.components.IconBubble
import com.sharex.app.ui.components.OnlineStatus
import com.sharex.app.ui.components.OverflowMenu
import com.sharex.app.ui.components.MenuAction
import com.sharex.app.ui.components.ProgressRing
import com.sharex.app.ui.copyToClipboard
import com.sharex.app.ui.dayLabel
import com.sharex.app.ui.iconForFile
import com.sharex.app.ui.isVisualMedia
import com.sharex.app.ui.lastSeenText
import com.sharex.app.ui.looksLikeUrl
import com.sharex.app.ui.openLink
import com.sharex.app.ui.openReceivedFile
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.transfer.ChatItem
import com.sharex.core.transfer.ChatMessage
import com.sharex.core.transfer.ChatState
import com.sharex.core.util.FileNames
import com.sharex.core.util.Format

data class ChatActions(
    val onBack: () -> Unit,
    val onOpenDetails: () -> Unit,
    val onAttach: () -> Unit,
    val onGallery: () -> Unit,
    val onSendText: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onRetry: (String) -> Unit,
    val canRetry: (String) -> Boolean,
    val onRefresh: () -> Unit,
)

private sealed interface ChatRow {
    data class Day(val label: String) : ChatRow
    data class Msg(val message: ChatMessage) : ChatRow
}

private fun rowsOf(messages: List<ChatMessage>): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    var lastDay: String? = null
    for (message in messages) {
        val day = dayLabel(message.time)
        if (day != lastDay) {
            rows += ChatRow.Day(day)
            lastDay = day
        }
        rows += ChatRow.Msg(message)
    }
    return rows
}

@Composable
fun ChatScreen(entry: DeviceEntry?, messages: List<ChatMessage>, actions: ChatActions) {
    val colors = ShareX.colors
    if (entry == null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text) }
            }
            ErrorState("Device unavailable", "This device was removed or can no longer be found.", Icons.Rounded.WifiOff, retryLabel = "Go back", onRetry = actions.onBack)
        }
        return
    }
    val rows = remember(messages) { rowsOf(messages) }
    val listState = rememberLazyListState()
    var draft by rememberSaveable(entry.id) { mutableStateOf("") }

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        ChatTopBar(entry, actions)
        EncryptionBanner(entry)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                EmptyState(
                    "No transfers yet",
                    if (entry.online) "Send a file, photo or message to ${entry.name} to start." else "${entry.name} is offline. Transfers will show here once it's back.",
                    Icons.Rounded.History,
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = {
                        when (it) {
                            is ChatRow.Day -> "day-" + it.label
                            is ChatRow.Msg -> "msg-" + it.message.id
                        }
                    }) { row ->
                        when (row) {
                            is ChatRow.Day -> DayChip(row.label)
                            is ChatRow.Msg -> Bubble(row.message, entry.name, actions)
                        }
                    }
                }
            }
        }
        if (!entry.online) OfflineBar(entry.name, actions.onRefresh)
        Composer(
            draft = draft,
            onDraft = { draft = it },
            onSend = {
                actions.onSendText(draft)
                if (entry.online) draft = ""
            },
            onAttach = actions.onAttach,
            onGallery = actions.onGallery,
        )
    }
}

@Composable
private fun ChatTopBar(entry: DeviceEntry, actions: ChatActions) {
    val colors = ShareX.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text) }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = actions.onOpenDetails).padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceBadge(entry, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.online) OnlineStatus(true, label = "Connected · Online")
                else Text("Offline · " + lastSeenText(entry.lastSeen).replaceFirstChar { it.lowercase() }, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = actions.onOpenDetails) { Icon(Icons.Rounded.Info, "Device details", tint = colors.text) }
        OverflowMenu(
            listOf(
                MenuAction("Send files", Icons.Rounded.Upload) { actions.onAttach() },
                MenuAction("Send photos & videos", Icons.Rounded.PhotoLibrary) { actions.onGallery() },
                MenuAction("Refresh connection", Icons.Rounded.Refresh) { actions.onRefresh() },
            ),
        )
    }
}

@Composable
private fun EncryptionBanner(entry: DeviceEntry) {
    val colors = ShareX.colors
    val route = entry.connections.firstOrNull()
    val text = "End-to-end encrypted" + when {
        !entry.online -> ""
        route == "Nearby" -> " · Direct connection"
        route != null -> " · $route"
        else -> ""
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(CircleShape).background(colors.surfaceHigh).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = colors.success, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OfflineBar(name: String, onRetry: () -> Unit) {
    val colors = ShareX.colors
    Row(
        Modifier.fillMaxWidth().background(colors.warning.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = colors.warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("$name is offline. Sending resumes when it's back.", style = MaterialTheme.typography.bodySmall, color = colors.text, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onRetry) { Text("Reconnect", color = colors.accent) }
    }
}

@Composable
private fun DayChip(label: String) {
    val colors = ShareX.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            modifier = Modifier.clip(CircleShape).background(colors.surfaceHigh).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage, deviceName: String, actions: ChatActions) {
    val colors = ShareX.colors
    val context = LocalContext.current
    val outgoing = message.outgoing
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (outgoing) 18.dp else 4.dp,
        bottomEnd = if (outgoing) 4.dp else 18.dp,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 330.dp)
                .fillMaxWidth(0.9f)
                .clip(shape)
                .background(if (outgoing) colors.bubbleOut else colors.bubbleIn)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .animateContentSize(),
        ) {
            val items = message.items
            when {
                items.size == 1 && items[0].isText -> TextBody(items[0].text.orEmpty(), onCopy = { copyToClipboard(context, it) }, onOpenLink = { openLink(context, it) })
                items.size == 1 -> FileBody(items[0], message, actions)
                else -> GroupBody(message, actions)
            }
            if (message.state == ChatState.IN_PROGRESS) ProgressBlock(message, deviceName)
            if (message.state == ChatState.FAILED && !message.detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(message.detail.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            Spacer(Modifier.height(4.dp))
            Footer(message)
        }
    }
}

@Composable
private fun TextBody(text: String, onCopy: (String) -> Unit, onOpenLink: (String) -> Unit) {
    val colors = ShareX.colors
    val link = looksLikeUrl(text)
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (link) colors.accent else colors.text,
        maxLines = 14,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = if (link) "Open link" else "Copy text") {
            if (link) onOpenLink(text) else onCopy(text)
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onCopy(text) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ContentCopy, "Copy text", tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
        if (link) {
            IconButton(onClick = { onOpenLink(text) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.OpenInNew, "Open link", tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FileBody(item: ChatItem, message: ChatMessage, actions: ChatActions) {
    val colors = ShareX.colors
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilePreview(item, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${Format.bytes(item.size)} · ${typeLabel(item)}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        StateAction(
            message = message,
            canRetry = actions.canRetry(message.id),
            onCancel = { actions.onCancel(message.id) },
            onRetry = { actions.onRetry(message.id) },
            onOpen = item.location?.takeIf { message.state == ChatState.COMPLETED && !message.outgoing }?.let { location ->
                { openReceivedFile(context, location, item.mime, item.name) }
            },
        )
    }
}

@Composable
private fun GroupBody(message: ChatMessage, actions: ChatActions) {
    val colors = ShareX.colors
    val context = LocalContext.current
    var expanded by remember(message.id) { mutableStateOf(false) }
    val items = message.items
    val allMedia = items.isNotEmpty() && items.all { isVisualMedia(it.name, it.mime) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(if (allMedia) Icons.Rounded.PhotoLibrary else Icons.Rounded.Folder, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Show files") { expanded = !expanded }) {
            Text(if (allMedia) "Photos & videos" else "Files", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text("${items.size} files · ${Format.bytes(message.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        StateAction(
            message = message,
            canRetry = actions.canRetry(message.id),
            onCancel = { actions.onCancel(message.id) },
            onRetry = { actions.onRetry(message.id) },
            onOpen = null,
        )
    }
    val shown = if (expanded) items else items.take(3)
    Spacer(Modifier.height(8.dp))
    shown.forEach { item ->
        val location = item.location?.takeIf { message.state == ChatState.COMPLETED && !message.outgoing }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = location != null, role = Role.Button) { location?.let { openReceivedFile(context, it, item.mime, item.name) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(iconForFile(item.name, item.mime, item.isText), null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (item.isText) "Text" else item.name, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(Format.bytes(item.size), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
    if (!expanded && items.size > 3) {
        Text(
            "+${items.size - 3} more",
            style = MaterialTheme.typography.labelMedium,
            color = colors.accent,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { expanded = true }.padding(vertical = 6.dp),
        )
    }
}

/** Thumbnail for saved images/videos; a typed icon for everything else (including files we only sent). */
@Composable
private fun FilePreview(item: ChatItem, size: Dp) {
    val colors = ShareX.colors
    val context = LocalContext.current
    val visual = isVisualMedia(item.name, item.mime)
    var failed by remember(item.location) { mutableStateOf(false) }
    if (visual && item.location != null && !failed) {
        Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(colors.surfaceHigh), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = AndroidReceiveStorage.uriFor(context, item.location!!),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
            if ((item.mime ?: FileNames.mimeFromName(item.name))?.startsWith("video/") == true) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        }
    } else {
        IconBubble(iconForFile(item.name, item.mime, item.isText), size = size)
    }
}

@Composable
private fun StateAction(
    message: ChatMessage,
    canRetry: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val colors = ShareX.colors
    when (message.state) {
        ChatState.IN_PROGRESS -> {
            if (message.live) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Cancel transfer", onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    ProgressRing(message.fraction.takeIf { it > 0f }, Modifier.size(40.dp), strokeWidth = 3.dp) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Cancel transfer", tint = colors.text, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        ChatState.COMPLETED -> if (onOpen != null) {
            IconButton(onClick = onOpen) { Icon(Icons.Rounded.OpenInNew, contentDescription = "Open file", tint = colors.accent) }
        }
        ChatState.FAILED, ChatState.CANCELLED, ChatState.DECLINED -> {
            if (message.outgoing && canRetry) {
                IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = "Try again", tint = colors.accent) }
            } else {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = "Not delivered", tint = colors.danger, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun ProgressBlock(message: ChatMessage, deviceName: String) {
    val colors = ShareX.colors
    Spacer(Modifier.height(8.dp))
    if (message.bytesDone <= 0L) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = colors.accent,
            trackColor = colors.background.copy(alpha = 0.35f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (message.outgoing) "Waiting for $deviceName…" else "Starting…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        return
    }
    LinearProgressIndicator(
        progress = { message.fraction },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = colors.accent,
        trackColor = colors.background.copy(alpha = 0.35f),
        drawStopIndicator = {},
    )
    Spacer(Modifier.height(4.dp))
    val parts = buildList {
        add("${(message.fraction * 100).toInt()}%")
        add("${Format.bytes(message.bytesDone)} / ${Format.bytes(message.totalBytes)}")
        if (message.bytesPerSecond > 0) add(Format.speed(message.bytesPerSecond))
        if (message.etaSeconds >= 0) add("${Format.duration(message.etaSeconds)} left")
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
}

@Composable
private fun Footer(message: ChatMessage) {
    val colors = ShareX.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        val label = when (message.state) {
            ChatState.FAILED -> "Failed"
            ChatState.DECLINED -> "Declined"
            ChatState.CANCELLED -> "Cancelled"
            else -> null
        }
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.danger)
            Spacer(Modifier.width(8.dp))
        }
        Text(clockTime(message.time), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        if (message.outgoing && message.state == ChatState.COMPLETED) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.DoneAll, contentDescription = "Delivered", tint = colors.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onGallery: () -> Unit,
) {
    val colors = ShareX.colors
    val canSend = draft.isNotBlank()
    Row(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceHigh)
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                maxLines = 4,
                cursorBrush = SolidColor(colors.accent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text("Type a message…", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                        inner()
                    }
                },
            )
            IconButton(onClick = onAttach) { Icon(Icons.Rounded.AttachFile, contentDescription = "Attach files", tint = colors.textMuted) }
            IconButton(onClick = onGallery) { Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Send photos or videos", tint = colors.textMuted) }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (canSend) colors.accent else colors.surfaceHigh)
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                .semantics { contentDescription = "Send message" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = if (canSend) colors.onAccent else colors.textMuted)
        }
    }
}

private fun typeLabel(item: ChatItem): String {
    val mime = item.mime ?: FileNames.mimeFromName(item.name).orEmpty()
    return when {
        mime.startsWith("image/") -> "Image"
        mime.startsWith("video/") -> "Video"
        mime.startsWith("audio/") -> "Audio"
        else -> FileNames.extension(item.name).uppercase().ifEmpty { "File" }
    }
}
