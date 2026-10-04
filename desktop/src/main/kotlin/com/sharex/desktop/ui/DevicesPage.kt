package com.sharex.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceFilter
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopDialog
import com.sharex.desktop.DesktopScreen

@Composable
fun DevicesPage(controller: DesktopController) {
    val c = Theme.colors
    val devices by controller.devices.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    val addresses by controller.addresses.collectAsState()
    val checked by controller.networkChecked.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DeviceFilter.ALL) }
    val shown = DeviceDirectory.apply(devices, filter, query)
    val mine = DeviceDirectory.mine(shown)
    val nearby = DeviceDirectory.nearby(shown)
    val offlineNetwork = checked && addresses.isEmpty()

    PageScaffold("Devices", "Your devices and everything discoverable nearby.") {
        SearchField(query, { query = it })
        Gap(10.dp)
        FilterChips(filter, { filter = it })

        if (offlineNetwork) {
            ErrorState(
                "Not connected to a network",
                "Join the same Wi-Fi, Ethernet or hotspot as the other device.",
                Icons.Rounded.WifiOff,
                retryLabel = "Check again",
                onRetry = controller::refreshDevices,
            )
        }

        SectionHeader("My Devices")
        if (mine.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    if (devices.none { it.known }) "Devices you exchange files with appear here. Trusted devices can send without a prompt." else "No devices match this filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                )
            }
        } else {
            Card(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                Column {
                    mine.forEachIndexed { i, entry ->
                        DeviceListItem(entry, onClick = { controller.navigate(DesktopScreen.Device(entry.id)) }, menu = controller.menuFor(entry))
                        if (i < mine.lastIndex) HorizontalDivider(color = c.outline.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }

        SectionHeader("Nearby Devices") {
            QuickAction("Scan QR", Icons.Rounded.QrCodeScanner, { controller.showDialog(DesktopDialog.ConnectByCode) })
            QuickAction(if (refreshing) "Searching" else "Refresh", Icons.Rounded.Refresh, controller::refreshDevices, enabled = !refreshing)
        }
        when {
            nearby.isNotEmpty() -> Card(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                Column {
                    nearby.forEachIndexed { i, entry ->
                        DeviceListItem(
                            entry,
                            onClick = { controller.navigate(DesktopScreen.Device(entry.id)) },
                            menu = controller.menuFor(entry),
                            onConnect = { controller.navigate(DesktopScreen.Chat(entry.id)) },
                        )
                        if (i < nearby.lastIndex) HorizontalDivider(color = c.outline.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
            refreshing && devices.none { !it.known } -> LoadingState("Looking for nearby devices…", "Open ShareX on the other device and keep it visible.")
            query.isNotBlank() || filter != DeviceFilter.ALL -> EmptyState("No matching devices", "Try a different search or filter.", Icons.Rounded.Search)
            else -> EmptyState(
                "No devices found",
                "Make sure the other device has ShareX open and is visible, then refresh. You can also scan its QR code.",
                Icons.Rounded.Search,
                action = { PrimaryButton("Refresh", onClick = controller::refreshDevices, icon = Icons.Rounded.Refresh) },
            )
        }
    }
}

@Composable
fun DeviceDetailsPage(controller: DesktopController, id: String, window: java.awt.Window) {
    val c = Theme.colors
    val devices by controller.devices.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    val entry = devices.firstOrNull { it.id == id }
    var confirmRemove by remember { mutableStateOf(false) }

    PageScaffold("Device details", onBack = controller::back, maxWidth = 760.dp) {
        if (entry == null) {
            ErrorState("Device unavailable", "This device was removed or can no longer be found.", Icons.Rounded.Search, retryLabel = "Go back", onRetry = controller::back)
            return@PageScaffold
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DeviceBadge(entry, 84.dp)
            Gap(10.dp)
            Text(entry.name, style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center)
            Gap(4.dp)
            OnlineStatus(entry.online)
        }
        Gap(16.dp)
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            Column {
                Info("Type", entry.type.name.lowercase().replaceFirstChar { it.uppercase() })
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Info("Status", if (entry.online) "Online, in range" else "Offline")
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Info("Last seen", if (entry.online) "Now" else lastSeenText(entry.lastSeen).removePrefix("Last seen ").replaceFirstChar { it.uppercase() })
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Info("Trusted", if (entry.trusted) "Yes. Sends without a prompt" else if (entry.known) "No. Asks before accepting" else "Not yet. Available after the first transfer")
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Info("Connection", if (entry.connections.isEmpty()) "Not in range" else entry.connections.joinToString(" · "))
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Info("Encryption", "ECDH P-256 · AES-256-GCM")
                entry.fingerprint?.let {
                    HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                    Info("Key fingerprint", it.chunked(4).take(8).joinToString(" ").uppercase())
                }
            }
        }
        Gap(14.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FullRow {
                PrimaryButton("Send files", onClick = { controller.requestPick(entry.id) }, icon = Icons.Rounded.InsertDriveFile, enabled = entry.online, modifier = Modifier.weight(1f))
                SecondaryButton("Send text", onClick = { controller.showDialog(DesktopDialog.SendText(entry.id)) }, icon = Icons.Rounded.Notes, modifier = Modifier.weight(1f))
            }
            FullRow {
                SecondaryButton("Transfer history", onClick = { controller.navigate(DesktopScreen.Chat(entry.id)) }, icon = Icons.Rounded.History, modifier = Modifier.weight(1f))
                SecondaryButton(
                    if (refreshing) "Looking…" else if (entry.online) "Refresh connection" else "Reconnect",
                    onClick = controller::refreshDevices,
                    icon = Icons.Rounded.Refresh,
                    modifier = Modifier.weight(1f),
                )
            }
            if (entry.known) {
                FullRow {
                    SecondaryButton(
                        if (entry.trusted) "Untrust device" else "Trust device",
                        onClick = { controller.setTrusted(entry.id, !entry.trusted) },
                        icon = if (entry.trusted) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton("Remove device", onClick = { confirmRemove = true }, icon = Icons.Rounded.DeleteOutline, tint = c.danger, modifier = Modifier.weight(1f))
                }
            }
        }
        if (!entry.online) {
            Gap(10.dp)
            Text("Sending is available when the device is online. Open ShareX on it and click Reconnect.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }

    if (confirmRemove && entry != null) {
        DialogFrame(width = 420.dp, onDismiss = { confirmRemove = false }) {
            Text("Remove ${entry.name}?", style = MaterialTheme.typography.headlineSmall, color = c.text)
            Gap(8.dp)
            Text(
                "ShareX forgets this device's key and trust. Your transfer history stays. You'll be asked to approve it again next time.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
            Gap(18.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Cancel", onClick = { confirmRemove = false }, color = c.textMuted)
                Spacer(Modifier.width(6.dp))
                TextAction("Remove", onClick = {
                    confirmRemove = false
                    controller.forgetDevice(entry.id)
                    controller.back()
                }, color = c.danger)
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.width(130.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
    }
}
