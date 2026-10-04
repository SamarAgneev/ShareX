package com.sharex.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.core.util.Format
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopDialog
import com.sharex.desktop.Platform
import java.io.File

@Composable
fun ProfilePage(controller: DesktopController) {
    val c = Theme.colors
    val settings by controller.graph.settings.state.collectAsState()
    var name by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
    val dir = File(settings.downloadDir)
    // Real free space of the volume that holds the receive folder; unknown if the folder's volume can't be read.
    val free = remember(settings.downloadDir) {
        generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace?.takeIf { it > 0 } ?: -1L
    }

    PageScaffold("Profile", "This computer and how it shares.", maxWidth = 820.dp) {
        Card(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceAvatar(controller.engine.self.type, size = 56.dp, highlighted = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(settings.deviceName, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Rename this device below", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Gap(14.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShareXInput(name, { name = it.take(40) }, Modifier.weight(1f), label = "Device name")
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(
                        "Save",
                        onClick = { controller.graph.settings.update { it.copy(deviceName = name.trim().ifEmpty { it.deviceName }) } },
                        enabled = name.isNotBlank() && name.trim() != settings.deviceName,
                    )
                }
            }
        }

        SectionHeader("Sharing")
        Card(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("Visible to nearby devices", "Others can find this computer and send requests", settings.visible, { v -> controller.graph.settings.update { it.copy(visible = v) } }, Icons.Rounded.Visibility)
                ToggleRow("Keep running in the tray", "Receive in the background after the window is closed", settings.closeToTray, { v -> controller.graph.settings.update { it.copy(closeToTray = v) } }, Icons.Rounded.Minimize)
                ToggleRow("Auto-accept trusted devices", "Skip the prompt for devices you've trusted", settings.autoAcceptTrusted, { v -> controller.graph.settings.update { it.copy(autoAcceptTrusted = v) } }, Icons.Rounded.AutoAwesome)
                ToggleRow("Show files after receiving", "Open the folder when a transfer finishes", settings.openFolderOnReceive, { v -> controller.graph.settings.update { it.copy(openFolderOnReceive = v) } }, Icons.Rounded.FolderOpen)
            }
        }

        SectionHeader("Direct connections")
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.WifiTethering, size = 38.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bluetooth + Wi-Fi Direct", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(
                        "Not available on Windows. Phones reach this computer over Wi-Fi, Ethernet or the phone's hotspot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                    )
                }
                Pill("Unavailable", color = c.warning)
            }
        }

        SectionHeader("Appearance")
        Card(Modifier.fillMaxWidth()) {
            Column {
                Text("Theme", style = MaterialTheme.typography.titleSmall, color = c.text)
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        val selected = settings.theme == value
                        Box(
                            Modifier
                                .height(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) c.accentContainer else c.surfaceHigh)
                                .border(1.dp, if (selected) c.accent.copy(alpha = 0.6f) else c.outline, CircleShape)
                                .clickable { controller.graph.settings.update { it.copy(theme = value) } }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.accent else c.textMuted) }
                    }
                }
            }
        }

        SectionHeader("Security & storage")
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Key, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your key fingerprint", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(controller.graph.identity.displayFingerprint, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    SecondaryButton("Copy", onClick = { Platform.copy(controller.graph.identity.displayFingerprint); controller.flash("Fingerprint copied") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Folder, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Received files", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(settings.downloadDir + if (free >= 0) " · ${Format.bytes(free)} free" else "", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (free < 0) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = c.warning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Storage unavailable. Choose another folder.", style = MaterialTheme.typography.bodySmall, color = c.warning)
                        }
                    }
                    SecondaryButton("Change", onClick = { controller.showDialog(DesktopDialog.ChooseFolder) })
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("Open", onClick = { Platform.openFolder(dir) })
                }
                InfoLine(
                    Icons.Rounded.Security,
                    "Encryption",
                    "Fresh ECDH P-256 keys per transfer with a key commitment, ECDSA device signatures, a 4-digit PIN and AES-256-GCM. No servers, no accounts.",
                )
                InfoLine(
                    Icons.Rounded.Lock,
                    "Windows Firewall",
                    "If phones can't see this PC, allow ShareX on private networks when Windows asks (TCP 47821, UDP 47820).",
                )
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, title: String, body: String) {
    val c = Theme.colors
    Row(verticalAlignment = Alignment.Top) {
        IconBubble(icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}
