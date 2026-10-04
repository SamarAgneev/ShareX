package com.sharex.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.app.ui.clockTime
import com.sharex.app.ui.components.IconBubble
import com.sharex.app.ui.components.ScreenTopBar
import com.sharex.app.ui.components.ShareXAlertDialog
import com.sharex.app.ui.components.ShareXCard
import com.sharex.app.ui.copyToClipboard
import com.sharex.app.ui.dayLabel
import com.sharex.app.ui.iconForFile
import com.sharex.app.ui.openReceivedFile
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.store.HistoryEntry
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    knownDeviceIds: Set<String>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val colors = ShareX.colors
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val groups = remember(entries) { entries.sortedByDescending { it.time }.groupBy { dayLabel(it.time) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Transfer history",
            subtitle = "${entries.size} transfers",
            onBack = onBack,
            actions = {
                if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear", color = colors.accent) }
            },
        )
        if (entries.isEmpty()) {
            EmptyState("No transfers yet", "Everything you send and receive shows up here.", Icons.Rounded.History)
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            groups.forEach { (day, dayEntries) ->
                item(key = "day-$day") {
                    Text(day, style = MaterialTheme.typography.labelLarge, color = colors.textMuted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                }
                items(dayEntries, key = { it.id }) { entry ->
                    val conversation = entry.peerId?.takeIf { it in knownDeviceIds }
                    Column(Modifier.animateContentSize()) {
                        HistoryRow(
                            entry,
                            onClick = {
                                if (conversation != null) onOpenConversation(conversation)
                                else expanded = if (expanded == entry.id) null else entry.id
                            },
                            onToggle = { expanded = if (expanded == entry.id) null else entry.id },
                        )
                        if (expanded == entry.id) {
                            ShareXCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = PaddingValues(12.dp)) {
                                Column {
                                    Text(
                                        clockTime(entry.time) + (entry.message?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textMuted,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    entry.items.forEach { item ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                            Icon(iconForFile(item.name, item.mime, item.text != null), null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                item.text ?: item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.text,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (item.text == null) Text(Format.bytes(item.size), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                            when {
                                                item.text != null -> IconButton(onClick = { copyToClipboard(context, item.text!!) }) {
                                                    Icon(Icons.Rounded.ContentCopy, "Copy", tint = colors.accent, modifier = Modifier.size(18.dp))
                                                }
                                                item.location != null -> IconButton(onClick = { openReceivedFile(context, item.location!!, item.mime, item.name) }) {
                                                    Icon(Icons.Rounded.OpenInNew, "Open", tint = colors.accent, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 6.dp))
                                    TextButton(onClick = { onRemove(entry.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Remove from history", color = colors.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ShareXAlertDialog(
            title = "Clear history?",
            confirmText = "Clear",
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        ) {
            Text("Received files stay in Downloads/ShareX. Only the list is cleared.", style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, onToggle: () -> Unit) {
    val colors = ShareX.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            entry.isSuccess -> colors.accent
            entry.status == TransferPhase.CANCELLED.name -> colors.textMuted
            else -> colors.danger
        }
        IconBubble(
            if (entry.isSend) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            tint = tint,
            background = tint.copy(alpha = 0.12f),
            size = 42.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = entry.items.singleOrNull()?.let { if (it.text != null) "Text" else it.name } ?: "${entry.items.size} items"
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (entry.status) {
                TransferPhase.COMPLETED.name -> Format.bytes(entry.totalBytes)
                TransferPhase.REJECTED.name -> "Declined"
                TransferPhase.CANCELLED.name -> "Cancelled"
                else -> "Failed"
            }
            Text(
                "${if (entry.isSend) "Sent to" else "Received from"} ${entry.peerName} · $status",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(clockTime(entry.time), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        IconButton(onClick = onToggle) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = "Details", tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}
