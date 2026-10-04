package com.sharex.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.transfer.ChatItem
import com.sharex.core.transfer.ChatMessage
import com.sharex.core.transfer.ChatState
import com.sharex.core.util.FileNames
import com.sharex.core.util.Format
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopScreen
import com.sharex.desktop.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO

private sealed interface ChatRow {
    data class Day(val label: String) : ChatRow
    data class Msg(val message: ChatMessage) : ChatRow
}

private fun rowsOf(messages: List<ChatMessage>): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    var last: String? = null
    for (m in messages) {
        val d = dayLabel(m.time)
        if (d != last) {
            rows += ChatRow.Day(d)
            last = d
        }
        rows += ChatRow.Msg(m)
    }
    return rows
}

@Composable
fun ChatPage(controller: DesktopController, id: String, window: java.awt.Window) {
    val c = Theme.colors
    val devices by controller.devices.collectAsState()
    val entry = devices.firstOrNull { it.id == id }
    val messages by remember(id) { controller.chatFor(id) }.collectAsState()
    val rows = remember(messages) { rowsOf(messages) }
    val listState = rememberLazyListState()
    var draft by remember(id) { mutableStateOf("") }

    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex) }

    if (entry == null) {
        PageScaffold("Conversation", onBack = controller::back) {
            ErrorState("Device unavailable", "This device was removed or can no longer be found.", Icons.Rounded.WifiOff, retryLabel = "Go back", onRetry = controller::back)
        }
        return
    }

    val commands = LocalScrollCommands.current
    LaunchedEffect(commands, rows.size) {
        commands?.collect { command ->
            val page = 520f
            when (command) {
                com.sharex.desktop.ScrollCommand.LINE_UP -> listState.animateScrollBy(-90f)
                com.sharex.desktop.ScrollCommand.LINE_DOWN -> listState.animateScrollBy(90f)
                com.sharex.desktop.ScrollCommand.PAGE_UP -> listState.animateScrollBy(-page)
                com.sharex.desktop.ScrollCommand.PAGE_DOWN -> listState.animateScrollBy(page)
                com.sharex.desktop.ScrollCommand.TOP -> if (rows.isNotEmpty()) listState.animateScrollToItem(0)
                com.sharex.desktop.ScrollCommand.BOTTOM -> if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
            }
        }
    }

    run {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 980.dp).fillMaxSize()) {
                ChatHeader(entry, controller)
                EncryptionBanner(entry)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (rows.isEmpty()) {
                        EmptyState(
                            "No transfers yet",
                            if (entry.online) "Send or drop a file, or write a message to ${entry.name} to start." else "${entry.name} is offline. Transfers will show here once it's back.",
                            Icons.Rounded.History,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
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
                                    is ChatRow.Msg -> Bubble(row.message, controller)
                                }
                            }
                        }
                    }
                    if (rows.isNotEmpty() && (listState.canScrollForward || listState.canScrollBackward)) {
                        androidx.compose.foundation.VerticalScrollbar(
                            adapter = androidx.compose.foundation.rememberScrollbarAdapter(listState),
                            style = scrollbarStyle(),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp, horizontal = 3.dp),
                        )
                    }
                }
                if (!entry.online) {
                    Row(
                        Modifier.fillMaxWidth().background(c.warning.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.WifiOff, null, tint = c.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("${entry.name} is offline. Sending resumes when it's back.", style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = controller::refreshDevices) { Text("Reconnect", color = c.accent) }
                    }
                }
                Composer(
                    draft = draft,
                    onDraft = { draft = it },
                    onSend = {
                        if (draft.isNotBlank()) {
                            controller.sendTextTo(entry.id, draft)
                            if (entry.online) draft = ""
                        }
                    },
                    onAttach = { controller.requestPick(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(entry: DeviceEntry, controller: DesktopController) {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = controller::back, modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text)
        }
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { controller.navigate(DesktopScreen.Device(entry.id)) }.pointerHoverIcon(PointerIcon.Hand).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceBadge(entry, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.online) OnlineStatus(true, "Connected · Online")
                else Text("Offline · " + lastSeenText(entry.lastSeen).replaceFirstChar { it.lowercase() }, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1)
            }
        }
        IconButton(onClick = { controller.navigate(DesktopScreen.Device(entry.id)) }, modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)) {
            Icon(Icons.Rounded.Info, "Device details", tint = c.text)
        }
        OverflowMenu(
            listOf(
                MenuAction("Send files", Icons.Rounded.AttachFile) { controller.requestPick(entry.id) },
                MenuAction("Refresh connection", Icons.Rounded.Refresh) { controller.refreshDevices() },
            ),
        )
    }
}

@Composable
private fun EncryptionBanner(entry: DeviceEntry) {
    val c = Theme.colors
    val route = entry.connections.firstOrNull()
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(CircleShape).background(c.surfaceHigh).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = c.success, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("End-to-end encrypted" + if (entry.online && route != null) " · $route" else "", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
    }
}

@Composable
private fun DayChip(label: String) {
    val c = Theme.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.clip(CircleShape).background(c.surfaceHigh).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun Bubble(message: ChatMessage, controller: DesktopController) {
    val c = Theme.colors
    val outgoing = message.outgoing
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (outgoing) 18.dp else 4.dp, bottomEnd = if (outgoing) 4.dp else 18.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(min = 260.dp, max = 460.dp).clip(shape).background(if (outgoing) c.bubbleOut else c.bubbleIn).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val items = message.items
            when {
                items.size == 1 && items[0].isText -> TextBody(items[0].text.orEmpty())
                items.size == 1 -> FileBody(items[0], message, controller)
                else -> GroupBody(message, controller)
            }
            if (message.state == ChatState.IN_PROGRESS) ProgressBlock(message)
            if (message.state == ChatState.FAILED && !message.detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(message.detail.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.danger)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val label = when (message.state) {
                    ChatState.FAILED -> "Failed"
                    ChatState.DECLINED -> "Declined"
                    ChatState.CANCELLED -> "Cancelled"
                    else -> null
                }
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = c.danger)
                    Spacer(Modifier.width(8.dp))
                }
                Text(clockTime(message.time), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                if (outgoing && message.state == ChatState.COMPLETED) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.DoneAll, "Delivered", tint = c.accent, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun TextBody(text: String) {
    val c = Theme.colors
    val link = text.trim().let { !it.contains(' ') && !it.contains('\n') && (it.startsWith("http://") || it.startsWith("https://")) }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = if (link) c.accent else c.text, maxLines = 14, overflow = TextOverflow.Ellipsis)
    Row {
        IconButton(onClick = { Platform.copy(text) }, modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)) {
            Icon(Icons.Rounded.ContentCopy, "Copy text", tint = c.textMuted, modifier = Modifier.size(16.dp))
        }
        if (link) IconButton(onClick = { Platform.openUrl(text) }, modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)) {
            Icon(Icons.Rounded.OpenInNew, "Open link", tint = c.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FileBody(item: ChatItem, message: ChatMessage, controller: DesktopController) {
    val c = Theme.colors
    val saved = item.location?.takeIf { message.state == ChatState.COMPLETED && !message.outgoing }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Thumb(item, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${Format.bytes(item.size)} · ${typeLabel(item)}", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        StateActions(message, controller, saved)
    }
}

@Composable
private fun GroupBody(message: ChatMessage, controller: DesktopController) {
    val c = Theme.colors
    var expanded by remember(message.id) { mutableStateOf(false) }
    val items = message.items
    val allMedia = items.isNotEmpty() && items.all { (it.mime ?: FileNames.mimeFromName(it.name))?.let { m -> m.startsWith("image/") || m.startsWith("video/") } == true }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(if (allMedia) Icons.Rounded.AttachFile else Icons.Rounded.FolderOpen, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable { expanded = !expanded }.pointerHoverIcon(PointerIcon.Hand)) {
            Text(if (allMedia) "Photos & videos" else "Files", style = MaterialTheme.typography.titleSmall, color = c.text)
            Text("${items.size} files · ${Format.bytes(message.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.width(6.dp))
        StateActions(message, controller, null)
    }
    Spacer(Modifier.height(8.dp))
    (if (expanded) items else items.take(3)).forEach { item ->
        val location = item.location?.takeIf { message.state == ChatState.COMPLETED && !message.outgoing }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 30.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = location != null) { location?.let { Platform.openFile(File(it)) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(fileIcon(item.name, item.isText), null, tint = c.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (item.isText) "Text" else item.name, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(Format.bytes(item.size), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
    if (!expanded && items.size > 3) {
        Text("+${items.size - 3} more", style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = true }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 6.dp))
    }
}

@Composable
private fun StateActions(message: ChatMessage, controller: DesktopController, savedLocation: String?) {
    val c = Theme.colors
    when (message.state) {
        ChatState.IN_PROGRESS -> if (message.live) {
            IconButton(onClick = { controller.cancel(message.id) }, modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Box(contentAlignment = Alignment.Center) {
                    if (message.fraction > 0f) CircularProgressIndicator(progress = { message.fraction }, modifier = Modifier.size(34.dp), strokeWidth = 3.dp, color = c.accent, trackColor = c.surfaceHigh)
                    else CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp, color = c.accent, trackColor = c.surfaceHigh)
                    Icon(Icons.Rounded.Stop, "Cancel transfer", tint = c.text, modifier = Modifier.size(16.dp))
                }
            }
        }
        ChatState.COMPLETED -> if (savedLocation != null) {
            IconButton(onClick = { Platform.openFile(File(savedLocation)) }, modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.OpenInNew, "Open file", tint = c.accent)
            }
            IconButton(onClick = { Platform.revealFile(File(savedLocation)) }, modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.FolderOpen, "Show in folder", tint = c.accent)
            }
        }
        else -> if (message.outgoing && controller.canRetry(message.id)) {
            IconButton(onClick = { controller.retry(message.id) }, modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.Refresh, "Try again", tint = c.accent)
            }
        } else {
            Icon(Icons.Rounded.ErrorOutline, "Not delivered", tint = c.danger, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ProgressBlock(message: ChatMessage) {
    val c = Theme.colors
    Spacer(Modifier.height(8.dp))
    if (message.bytesDone <= 0L) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = c.accent, trackColor = c.background.copy(alpha = 0.35f))
        Spacer(Modifier.height(4.dp))
        Text(if (message.outgoing) "Waiting for the other device…" else "Starting…", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        return
    }
    LinearProgressIndicator(progress = { message.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = c.accent, trackColor = c.background.copy(alpha = 0.35f), drawStopIndicator = {})
    Spacer(Modifier.height(4.dp))
    val parts = buildList {
        add("${(message.fraction * 100).toInt()}%")
        add("${Format.bytes(message.bytesDone)} / ${Format.bytes(message.totalBytes)}")
        if (message.bytesPerSecond > 0) add(Format.speed(message.bytesPerSecond))
        if (message.etaSeconds >= 0) add("${Format.duration(message.etaSeconds)} left")
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
}

/** Image thumbnail for received images (decoded off the UI thread); a typed icon for everything else. */
@Composable
private fun Thumb(item: ChatItem, size: Dp) {
    val c = Theme.colors
    val isImage = (item.mime ?: FileNames.mimeFromName(item.name))?.startsWith("image/") == true
    val location = item.location
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, location) {
        value = if (isImage && location != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(location)
                    if (!file.isFile || file.length() > 40L * 1024 * 1024) null else ImageIO.read(file)?.toComposeImageBitmap()
                }.getOrNull()
            }
        } else null
    }
    val shown = bitmap
    if (shown != null) {
        Image(shown, item.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)))
    } else {
        IconBubble(fileIcon(item.name, item.isText), size = size)
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

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, onAttach: () -> Unit) {
    val c = Theme.colors
    val canSend = draft.isNotBlank()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(26.dp)).background(c.surfaceHigh).padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                maxLines = 4,
                cursorBrush = SolidColor(c.accent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                modifier = Modifier.weight(1f).padding(vertical = 13.dp).onPreviewKeyEvent { e ->
                    // Enter sends, Shift+Enter inserts a new line.
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                        onSend()
                        true
                    } else false
                },
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text("Type a message… (drop files here to send)", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                        inner()
                    }
                },
            )
            IconButton(onClick = onAttach, modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.AttachFile, "Attach files", tint = c.textMuted)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (canSend) c.accent else c.surfaceHigh).clickable(enabled = canSend, onClick = onSend).pointerHoverIcon(if (canSend) PointerIcon.Hand else PointerIcon.Default),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Send message", tint = if (canSend) c.onAccent else c.textMuted)
        }
    }
}
