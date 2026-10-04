package com.sharex.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.DeviceFilter
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopDialog
import com.sharex.desktop.DesktopScreen

/** What a device row's menu can do; shared by Home and Devices so both behave identically. */
fun DesktopController.menuFor(entry: DeviceEntry): List<MenuAction> = buildList {
    add(MenuAction("Open conversation", Icons.Rounded.History) { navigate(DesktopScreen.Chat(entry.id)) })
    if (entry.online) add(MenuAction("Send files", Icons.Rounded.Upload) { requestPick(entry.id) })
    add(MenuAction("Device details", Icons.Rounded.Info) { navigate(DesktopScreen.Device(entry.id)) })
    if (entry.known) {
        add(MenuAction(if (entry.trusted) "Untrust device" else "Trust device", Icons.Rounded.Lock) { setTrusted(entry.id, !entry.trusted) })
        add(MenuAction("Remove device", Icons.Rounded.Close, destructive = true) { forgetDevice(entry.id) })
    }
}

@Composable
fun HomePage(controller: DesktopController) {
    val c = Theme.colors
    val settings by controller.graph.settings.state.collectAsState()
    val devices by controller.devices.collectAsState()
    val transfers by controller.engine.transfers.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    val addresses by controller.addresses.collectAsState()
    val checked by controller.networkChecked.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val recent = DeviceDirectory.apply(DeviceDirectory.recent(devices), DeviceFilter.ALL, query)
    val active = transfers.filter { it.isActive && it.phase != TransferPhase.AWAITING_DECISION }
    val offline = checked && addresses.isEmpty()

    PageScaffold("ShareX", "Send files and text to phones and computers nearby.") {
        Card(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceAvatar(controller.engine.self.type, size = 56.dp, highlighted = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(settings.deviceName, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1)
                        val online = settings.visible && !offline
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(online)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when {
                                    !settings.visible -> "Hidden from nearby devices"
                                    offline -> "Visible, but no network connection"
                                    else -> "Visible on your network"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textMuted,
                            )
                        }
                    }
                    Switch(
                        checked = settings.visible,
                        onCheckedChange = { v -> controller.graph.settings.update { it.copy(visible = v) } },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                            uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.outline, uncheckedThumbColor = c.textMuted,
                        ),
                    )
                }
                Gap(12.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = c.success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("End-to-end encrypted", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                    TextAction("My QR code", onClick = { controller.navigate(DesktopScreen.MyQr) }, icon = Icons.Rounded.QrCode2)
                }
            }
        }
        Gap(14.dp)
        FullRow {
            PrimaryButton("Send", onClick = { controller.navigate(DesktopScreen.Send()) }, icon = Icons.Rounded.Upload, modifier = Modifier.weight(1f))
            SecondaryButton("Receive", onClick = { controller.navigate(DesktopScreen.Receive) }, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f))
        }

        if (offline) {
            Gap(14.dp)
            Card(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.WifiOff, size = 40.dp, tint = c.warning, background = c.warning.copy(alpha = 0.14f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Not connected to a network", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text("Join the same Wi-Fi, Ethernet or hotspot as the other device to find it.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    TextButton(onClick = controller::refreshDevices) { Text("Retry", color = c.accent) }
                }
            }
        }

        if (active.isNotEmpty()) {
            SectionHeader("Active transfers")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                active.forEach { t -> ActiveRow(t.id, t.direction == Direction.SEND, t.peerName, t.phase, t.fraction, t.bytesPerSecond, t.pin) { controller.navigate(DesktopScreen.Transfer(t.id)) } }
            }
        }

        SectionHeader("Recent") {
            QuickAction(if (searching) "Close" else "Search", Icons.Rounded.Search, { searching = !searching; if (!searching) query = "" })
            QuickAction("History", Icons.Rounded.History, { controller.navigate(DesktopScreen.History) })
            QuickAction("Scan QR", Icons.Rounded.QrCodeScanner, { controller.showDialog(DesktopDialog.ConnectByCode) })
            QuickAction(if (refreshing) "Searching" else "Refresh", Icons.Rounded.Refresh, controller::refreshDevices, enabled = !refreshing)
        }
        if (searching) {
            SearchField(query, { query = it })
            Gap(8.dp)
        }
        when {
            devices.isEmpty() && refreshing -> LoadingState("Looking for devices…", "Keep ShareX open on the other device.")
            devices.isEmpty() -> EmptyState(
                "No devices found",
                "Devices you share with show up here. Open ShareX on a phone or PC on the same network, or scan its QR code.",
                Icons.Rounded.Search,
                action = { PrimaryButton("Scan QR", onClick = { controller.showDialog(DesktopDialog.ConnectByCode) }, icon = Icons.Rounded.QrCodeScanner) },
            )
            recent.isEmpty() -> EmptyState("No matching devices", "Nothing matches \"$query\".", Icons.Rounded.Search)
            else -> Card(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                Column {
                    recent.forEach { entry ->
                        DeviceListItem(entry, onClick = { controller.navigate(DesktopScreen.Chat(entry.id)) }, menu = controller.menuFor(entry))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActiveRow(
    id: String,
    sending: Boolean,
    peerName: String,
    phase: TransferPhase,
    fraction: Float,
    bytesPerSecond: Long,
    pin: String?,
    onClick: () -> Unit,
) {
    val c = Theme.colors
    Box(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)) {
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(if (sending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (sending) "To " else "From ") + peerName, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1)
                        Text(
                            when (phase) {
                                TransferPhase.CONNECTING -> "Connecting…"
                                TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for accept · PIN $pin"
                                TransferPhase.AWAITING_DECISION -> "Waiting for you"
                                else -> "${(fraction * 100).toInt()}% · ${Format.speed(bytesPerSecond)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                        )
                    }
                }
                if (phase == TransferPhase.TRANSFERRING) {
                    Spacer(Modifier.height(10.dp))
                    Progress(fraction)
                }
            }
        }
    }
}
