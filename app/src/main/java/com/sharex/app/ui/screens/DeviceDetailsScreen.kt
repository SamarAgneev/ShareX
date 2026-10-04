package com.sharex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.app.ui.components.DeviceBadge
import com.sharex.app.ui.components.IconBubble
import com.sharex.app.ui.components.OnlineStatus
import com.sharex.app.ui.components.ScreenTopBar
import com.sharex.app.ui.components.ShareXAlertDialog
import com.sharex.app.ui.components.ShareXCard
import com.sharex.app.ui.lastSeenText
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.engine.DeviceEntry

data class DeviceDetailActions(
    val onBack: () -> Unit,
    val onSendFiles: () -> Unit,
    val onSendMedia: () -> Unit,
    val onSendText: () -> Unit,
    val onHistory: () -> Unit,
    val onTrust: (Boolean) -> Unit,
    val onReconnect: () -> Unit,
    val onRemove: () -> Unit,
)

@Composable
fun DeviceDetailsScreen(entry: DeviceEntry?, refreshing: Boolean, actions: DeviceDetailActions) {
    val colors = ShareX.colors
    var confirmRemove by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Device details", onBack = actions.onBack)
        if (entry == null) {
            ErrorState(
                title = "Device unavailable",
                body = "This device was removed or can no longer be found.",
                icon = Icons.Rounded.Search,
                retryLabel = "Go back",
                onRetry = actions.onBack,
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DeviceBadge(entry, size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(entry.name, style = MaterialTheme.typography.headlineSmall, color = colors.text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    OnlineStatus(entry.online)
                }
            }

            item {
                ShareXCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    Column {
                        InfoLine("Type", entry.type.name.lowercase().replaceFirstChar { it.uppercase() })
                        Divider()
                        InfoLine("Status", if (entry.online) "Online, in range" else "Offline")
                        Divider()
                        InfoLine("Last seen", if (entry.online) "Now" else lastSeenText(entry.lastSeen).removePrefix("Last seen ").replaceFirstChar { it.uppercase() })
                        Divider()
                        InfoLine("Trusted", if (entry.trusted) "Yes. Sends without a prompt" else if (entry.known) "No. Asks before accepting" else "Not yet. Available after the first transfer")
                        Divider()
                        InfoLine("Connection", if (entry.connections.isEmpty()) "Not in range" else entry.connections.joinToString(" · ") { if (it == "Nearby") "Direct (Bluetooth + Wi-Fi Direct)" else it })
                        Divider()
                        InfoLine("Encryption", "ECDH P-256 · AES-256-GCM")
                        entry.fingerprint?.let {
                            Divider()
                            InfoLine("Key fingerprint", it.chunked(4).take(8).joinToString(" ").uppercase())
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile("Send files", Icons.Rounded.InsertDriveFile, enabled = entry.online, onClick = actions.onSendFiles)
                    ActionTile("Send photos & videos", Icons.Rounded.Image, enabled = entry.online, onClick = actions.onSendMedia)
                    ActionTile("Send text", Icons.Rounded.Notes, enabled = entry.online, onClick = actions.onSendText)
                    ActionTile("View transfer history", Icons.Rounded.History, onClick = actions.onHistory)
                    ActionTile(
                        if (refreshing) "Looking for ${entry.name}…" else if (entry.online) "Refresh connection" else "Reconnect",
                        Icons.Rounded.Refresh,
                        enabled = !refreshing,
                        onClick = actions.onReconnect,
                    )
                    if (entry.known) {
                        ActionTile(
                            if (entry.trusted) "Untrust device" else "Trust device",
                            if (entry.trusted) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            onClick = { actions.onTrust(!entry.trusted) },
                        )
                        ActionTile("Remove device", Icons.Rounded.DeleteOutline, destructive = true, onClick = { confirmRemove = true })
                    }
                }
            }
            if (!entry.online) {
                item {
                    Text(
                        "Sending is available when the device is online. Open ShareX on it and tap Reconnect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }

    if (confirmRemove && entry != null) {
        ShareXAlertDialog(
            title = "Remove ${entry.name}?",
            confirmText = "Remove",
            confirmColor = ShareX.colors.danger,
            onConfirm = {
                confirmRemove = false
                actions.onRemove()
            },
            onDismiss = { confirmRemove = false },
        ) {
            Text(
                "ShareX forgets this device's key and trust. Your transfer history stays. You'll be asked to approve it again next time.",
                style = MaterialTheme.typography.bodyMedium,
                color = ShareX.colors.text,
            )
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = ShareX.colors.outline.copy(alpha = 0.6f))
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = ShareX.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.width(112.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = ShareX.colors
    val tint = when {
        !enabled -> colors.textMuted.copy(alpha = 0.6f)
        destructive -> colors.danger
        else -> colors.accent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint = tint, background = tint.copy(alpha = 0.12f), size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (destructive) colors.danger else if (enabled) colors.text else colors.textMuted,
        )
    }
}
