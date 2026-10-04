package com.sharex.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.core.transfer.TextSendItem
import com.sharex.core.util.Format
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopDialog

@Composable
fun SendPage(controller: DesktopController, targetId: String?, window: java.awt.Window) {
    val c = Theme.colors
    val selection by controller.selection.collectAsState()
    val devices by controller.devices.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    var chosenId by remember { mutableStateOf(targetId) }
    var address by remember { mutableStateOf("") }
    val choices = devices.filter { it.online || it.id == targetId }
    val chosen = devices.firstOrNull { it.id == chosenId }
    val total = selection.sumOf { it.size }
    val hint = when {
        selection.isEmpty() -> "Choose what to send first."
        chosen == null -> "Choose a device to send to."
        !chosen.online -> "${chosen.name} is offline. Bring it nearby and refresh."
        else -> null
    }

    PageScaffold(
        "Send",
        if (selection.isEmpty()) "Nothing selected" else "${selection.size} ${if (selection.size == 1) "item" else "items"} · ${Format.bytes(total)}",
        onBack = controller::back,
    ) {
        run {
            val dragging = LocalDragging.current
            val border = if (dragging) c.accent else c.outline
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (dragging) c.accent.copy(alpha = 0.08f) else c.surface)
                    .drawBehind {
                        drawRoundRect(
                            color = border,
                            cornerRadius = CornerRadius(22.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx(), pathEffect = if (selection.isEmpty()) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null),
                        )
                    }
                    .padding(22.dp),
            ) {
                if (selection.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBubble(Icons.Rounded.CloudUpload, size = 60.dp)
                        Gap(12.dp)
                        Text(if (dragging) "Release to add" else "Drop files or folders here", style = MaterialTheme.typography.titleLarge, color = c.text)
                        Text("No size limit. Everything is end-to-end encrypted.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        selection.take(100).forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(fileIcon(item.name, item.isText), size = 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (item.isText) (item.item as TextSendItem).text else item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = c.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        (if (item.isText) "Text" else item.name.substringAfterLast('.', "").uppercase().ifEmpty { "File" }) + " · " + Format.bytes(item.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textMuted,
                                    )
                                }
                                IconButton(onClick = { controller.remove(item.key) }, modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand)) {
                                    Icon(Icons.Rounded.Close, "Remove ${item.name}", tint = c.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (selection.size > 100) {
                            Text("+ ${selection.size - 100} more files (all will be sent)", style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (selection.isEmpty()) "Choose files" else "Add more", onClick = { controller.requestPick(null) }, icon = Icons.Rounded.Add)
                    SecondaryButton("Text", onClick = { controller.showDialog(DesktopDialog.SendText(null)) }, icon = Icons.Rounded.TextFields)
                    if (selection.isNotEmpty()) {
                        Spacer(Modifier.weight(1f))
                        Text("Total ${Format.bytes(total)}", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                        TextButton(onClick = controller::clearSelection) { Text("Clear", color = c.textMuted) }
                    }
                }
            }
        }

        SectionHeader("Send to") {
            QuickAction(if (refreshing) "Searching" else "Refresh", Icons.Rounded.Refresh, controller::refreshDevices, enabled = !refreshing)
        }
        if (choices.isEmpty()) {
            if (refreshing) LoadingState("Looking for nearby devices…")
            else EmptyState("No devices found", "Open ShareX on the other device and keep it visible, or use a QR code or address below.", Icons.Rounded.QrCodeScanner)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { entry ->
                    val selected = entry.id == chosenId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) c.accentContainer else c.surface)
                            .clickable(enabled = entry.online) { chosenId = entry.id }
                            .pointerHoverIcon(if (entry.online) PointerIcon.Hand else PointerIcon.Default)
                            .alpha(if (entry.online) 1f else 0.55f)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeviceBadge(entry, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            OnlineStatus(entry.online)
                        }
                        if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    }
                }
            }
        }

        Gap(16.dp)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Gap(6.dp)
        }
        PrimaryButton(
            text = if (chosen != null) "SEND TO ${chosen.name.uppercase()}" else "SEND TO DEVICE",
            onClick = { chosen?.let { controller.sendSelectionTo(it.id) } },
            icon = Icons.Rounded.Upload,
            enabled = hint == null && chosen != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Scan QR", onClick = { controller.showDialog(DesktopDialog.ConnectByCode) }, icon = Icons.Rounded.QrCodeScanner)
            ShareXInput(address, { address = it.trim() }, Modifier.weight(1f), placeholder = "Or an address, e.g. 192.168.1.20")
            SecondaryButton("Send", onClick = { if (address.isNotBlank()) controller.sendToAddress(address) }, icon = Icons.Rounded.Keyboard)
        }
    }
}

