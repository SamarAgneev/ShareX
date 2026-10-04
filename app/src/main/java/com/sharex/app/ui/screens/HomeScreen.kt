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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.app.net.NetworkStatus
import com.sharex.app.ui.components.DeviceAvatar
import com.sharex.app.ui.components.DeviceListItem
import com.sharex.app.ui.components.GradientButton
import com.sharex.app.ui.components.IconBubble
import com.sharex.app.ui.components.MenuAction
import com.sharex.app.ui.components.QRButton
import com.sharex.app.ui.components.SearchField
import com.sharex.app.ui.components.SectionHeader
import com.sharex.app.ui.components.ShareXCard
import com.sharex.app.ui.components.SoftButton
import com.sharex.app.ui.components.StatusDot
import com.sharex.app.ui.components.Wordmark
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.DeviceType
import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.DeviceFilter
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format

data class HomeActions(
    val onSend: () -> Unit,
    val onReceive: () -> Unit,
    val onScanQr: () -> Unit,
    val onShowQr: () -> Unit,
    val onToggleVisible: (Boolean) -> Unit,
    val onRefresh: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenTransfer: (String) -> Unit,
    val onGrantNearby: () -> Unit,
    val device: DeviceActions,
)

/** Everything a device row's menu can do; shared by Home and Devices so both behave identically. */
data class DeviceActions(
    val onOpenChat: (String) -> Unit,
    val onOpenDetails: (String) -> Unit,
    val onSendFiles: (String) -> Unit,
    val onTrust: (String, Boolean) -> Unit,
    val onRemove: (String) -> Unit,
)

fun DeviceActions.menuFor(entry: DeviceEntry): List<MenuAction> = buildList {
    add(MenuAction("Open conversation", Icons.Rounded.History) { onOpenChat(entry.id) })
    if (entry.online) add(MenuAction("Send files", Icons.Rounded.Upload) { onSendFiles(entry.id) })
    add(MenuAction("Device details", Icons.Rounded.Search) { onOpenDetails(entry.id) })
    if (entry.known) {
        add(MenuAction(if (entry.trusted) "Untrust device" else "Trust device", Icons.Rounded.Lock) { onTrust(entry.id, !entry.trusted) })
        add(MenuAction("Remove device", Icons.Rounded.Close, destructive = true) { onRemove(entry.id) })
    }
}

@Composable
fun HomeScreen(
    deviceName: String,
    deviceType: DeviceType,
    visible: Boolean,
    network: NetworkStatus,
    networkChecked: Boolean,
    nearbySupported: Boolean,
    nearbyAvailable: Boolean,
    refreshing: Boolean,
    devices: List<DeviceEntry>,
    activeTransfers: List<TransferInfo>,
    actions: HomeActions,
) {
    val colors = ShareX.colors
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val recent = DeviceDirectory.apply(DeviceDirectory.recent(devices), DeviceFilter.ALL, query)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Wordmark(Modifier.padding(top = 4.dp, bottom = 4.dp)) }

        item {
            CurrentDeviceCard(
                name = deviceName,
                type = deviceType,
                visible = visible,
                network = network,
                nearbyAvailable = nearbyAvailable,
                onToggleVisible = actions.onToggleVisible,
                onShowQr = actions.onShowQr,
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientButton("Send", onClick = actions.onSend, icon = Icons.Rounded.Upload, modifier = Modifier.weight(1f))
                SoftButton("Receive", onClick = actions.onReceive, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f))
            }
        }

        if (networkChecked && !network.connected && !nearbyAvailable) {
            item {
                ShareXCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.WifiOff, tint = colors.warning, background = colors.warning.copy(alpha = 0.14f), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Not connected to a network", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text("Join the same Wi-Fi as the other device to find it.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                        androidx.compose.material3.TextButton(onClick = actions.onRefresh) { Text("Retry", color = colors.accent) }
                    }
                }
            }
        }

        if (nearbySupported && !nearbyAvailable) {
            item {
                ShareXCard(Modifier.fillMaxWidth(), onClick = actions.onGrantNearby, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Lock, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Permission required", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                "Allow nearby devices access to connect to phones without Wi-Fi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Text("Allow", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                    }
                }
            }
        }

        if (activeTransfers.isNotEmpty()) {
            item { SectionHeader("Active transfers") }
            items(activeTransfers, key = { "active-" + it.id }) { transfer ->
                ActiveTransferRow(transfer, onClick = { actions.onOpenTransfer(transfer.id) })
            }
        }

        item {
            SectionHeader("Recent") {
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) { Icon(Icons.Rounded.Search, contentDescription = if (searching) "Close search" else "Search devices", tint = colors.text) }
                IconButton(onClick = actions.onOpenHistory) { Icon(Icons.Rounded.History, contentDescription = "Transfer history", tint = colors.text) }
                QRButton("Scan QR", Icons.Rounded.QrCodeScanner, actions.onScanQr)
                QRButton(if (refreshing) "Searching" else "Refresh", Icons.Rounded.Refresh, actions.onRefresh, enabled = !refreshing)
            }
        }

        if (searching) item { SearchField(query, { query = it }) }

        when {
            devices.isEmpty() && refreshing -> item { LoadingState("Looking for devices…", "Keep ShareX open on the other device.") }
            devices.isEmpty() -> item {
                EmptyState(
                    "No devices found",
                    "Devices you share with show up here. Open ShareX on another device or scan its QR code.",
                    Icons.Rounded.Search,
                    action = { GradientButton("Scan QR", onClick = actions.onScanQr, icon = Icons.Rounded.QrCodeScanner) },
                )
            }
            recent.isEmpty() -> item {
                EmptyState("No matching devices", "Nothing matches \"$query\".", Icons.Rounded.Search)
            }
            else -> items(recent, key = { "recent-" + it.id }) { entry ->
                DeviceListItem(
                    entry = entry,
                    onClick = { actions.device.onOpenChat(entry.id) },
                    menu = actions.device.menuFor(entry),
                    modifier = Modifier.animateContentSize(),
                )
            }
        }
    }
}

@Composable
private fun CurrentDeviceCard(
    name: String,
    type: DeviceType,
    visible: Boolean,
    network: NetworkStatus,
    nearbyAvailable: Boolean,
    onToggleVisible: (Boolean) -> Unit,
    onShowQr: () -> Unit,
) {
    val colors = ShareX.colors
    val status = when {
        !visible -> "Hidden from nearby devices"
        network.connected -> "Visible on ${network.kind ?: "your network"}"
        nearbyAvailable -> "Visible via Nearby"
        else -> "Visible, but no connection"
    }
    ShareXCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(type, size = 56.dp, highlighted = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(visible && (network.connected || nearbyAvailable))
                        Spacer(Modifier.width(7.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = visible,
                    onCheckedChange = onToggleVisible,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.onAccent,
                        uncheckedTrackColor = colors.surfaceHigh,
                        uncheckedBorderColor = colors.outline,
                        uncheckedThumbColor = colors.textMuted,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onShowQr)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("My QR code", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun ActiveTransferRow(transfer: TransferInfo, onClick: () -> Unit) {
    val colors = ShareX.colors
    ShareXCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(if (transfer.direction == Direction.SEND) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (transfer.direction == Direction.SEND) "To " else "From ") + transfer.peerName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        maxLines = 1,
                    )
                    val detail = when (transfer.phase) {
                        TransferPhase.CONNECTING -> "Connecting…"
                        TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for accept · PIN ${transfer.pin}"
                        TransferPhase.AWAITING_DECISION -> "Waiting for you"
                        else -> "${(transfer.fraction * 100).toInt()}% · ${Format.speed(transfer.bytesPerSecond)}"
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            if (transfer.phase == TransferPhase.TRANSFERRING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { transfer.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = colors.accent,
                    trackColor = colors.surfaceHigh,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
