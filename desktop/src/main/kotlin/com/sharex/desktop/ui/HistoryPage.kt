package com.sharex.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.core.store.HistoryEntry
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopScreen
import com.sharex.desktop.Platform
import java.io.File

@Composable
fun HistoryPage(controller: DesktopController) {
    val c = Theme.colors
    val history by controller.history.collectAsState()
    val devices by controller.devices.collectAsState()
    val settings by controller.graph.settings.state.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val groups = remember(history) { history.sortedByDescending { it.time }.groupBy { dayLabel(it.time) } }
    val known = remember(devices) { devices.mapTo(HashSet()) { it.id } }

    PageScaffold(
        "Transfer history",
        "${history.size} transfers",
        onBack = controller::back,
        trailing = {
            Row {
                SecondaryButton("Open folder", onClick = { Platform.openFolder(File(settings.downloadDir)) }, icon = Icons.Rounded.FolderOpen)
                if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear", color = c.textMuted) }
            }
        },
    ) {
        if (history.isEmpty()) {
            EmptyState("No transfers yet", "Everything you send and receive shows up here.", Icons.Rounded.History)
        } else {
            groups.forEach { (day, entries) ->
                Text(day, style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                Card(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)) {
                    Column {
                        entries.forEachIndexed { i, entry ->
                            HistoryRow(entry, conversation = entry.peerId?.takeIf { it in known }, controller = controller)
                            if (i < entries.lastIndex) HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        DialogFrame(width = 420.dp, onDismiss = { confirmClear = false }) {
            Text("Clear history?", style = MaterialTheme.typography.headlineSmall, color = c.text)
            Gap(8.dp)
            Text("Received files stay in your ShareX folder. Only the list is cleared.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            Gap(18.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextAction("Cancel", onClick = { confirmClear = false }, color = c.textMuted)
                TextAction("Clear", onClick = { confirmClear = false; controller.graph.history.clear() })
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, conversation: String?, controller: DesktopController) {
    val c = Theme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { if (conversation != null) controller.navigate(DesktopScreen.Chat(conversation)) else expanded = !expanded }.pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = when {
                entry.isSuccess -> c.accent
                entry.status == TransferPhase.CANCELLED.name -> c.textMuted
                else -> c.danger
            }
            IconBubble(if (entry.isSend) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, size = 38.dp, tint = tint, background = tint.copy(alpha = 0.12f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = entry.items.singleOrNull()?.let { if (it.text != null) "Text" else it.name } ?: "${entry.items.size} items"
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when (entry.status) {
                    TransferPhase.COMPLETED.name -> Format.bytes(entry.totalBytes)
                    TransferPhase.REJECTED.name -> "Declined"
                    TransferPhase.CANCELLED.name -> "Cancelled"
                    else -> entry.message ?: "Failed"
                }
                Text("${if (entry.isSend) "Sent to" else "Received from"} ${entry.peerName} · $status", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(clockTime(entry.time), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.OpenInNew, "Details", tint = c.textMuted, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 50.dp, top = 8.dp)) {
                entry.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text ?: item.name, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        when {
                            item.text != null -> IconButton(onClick = { Platform.copy(item.text!!) }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = c.accent, modifier = Modifier.size(16.dp)) }
                            item.location != null -> {
                                IconButton(onClick = { Platform.openFile(File(item.location!!)) }) { Icon(Icons.Rounded.OpenInNew, "Open", tint = c.accent, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { Platform.revealFile(File(item.location!!)) }) { Icon(Icons.Rounded.Folder, "Show in folder", tint = c.accent, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                TextButton(onClick = { controller.graph.history.remove(entry.id) }) { Text("Remove from history", color = c.danger) }
            }
        }
    }
}
