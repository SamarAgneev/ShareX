package com.sharex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.app.data.ThemeMode
import com.sharex.app.ui.components.DeviceAvatar
import com.sharex.app.ui.components.IconBubble
import com.sharex.app.ui.components.Pill
import com.sharex.app.ui.components.SectionLabel
import com.sharex.app.ui.components.SettingSwitch
import com.sharex.app.ui.components.ShareXAlertDialog
import com.sharex.app.ui.components.ShareXCard
import com.sharex.app.ui.components.Wordmark
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.DeviceType
import com.sharex.core.util.Format

data class ProfileState(
    val deviceName: String,
    val deviceType: DeviceType,
    val fingerprint: String,
    val visible: Boolean,
    val backgroundVisible: Boolean,
    val autoAcceptTrusted: Boolean,
    val theme: ThemeMode,
    val nearbySupported: Boolean,
    val nearbyAvailable: Boolean,
    /** Free bytes on shared storage; negative when storage can't be read. */
    val freeStorageBytes: Long,
)

data class ProfileActions(
    val onRename: (String) -> Unit,
    val onVisible: (Boolean) -> Unit,
    val onBackgroundVisible: (Boolean) -> Unit,
    val onAutoAccept: (Boolean) -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onGrantNearby: () -> Unit,
    val onCopyFingerprint: () -> Unit,
    val onOpenReceived: () -> Unit,
)

@Composable
fun ProfileScreen(state: ProfileState, actions: ProfileActions) {
    val colors = ShareX.colors
    var renaming by remember { mutableStateOf(false) }
    var showEncryption by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Wordmark(Modifier.padding(top = 4.dp)) }
        item { Text("Profile", style = MaterialTheme.typography.titleLarge, color = colors.text) }
        item {
            ShareXCard(Modifier.fillMaxWidth(), onClick = { renaming = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceAvatar(state.deviceType, size = 56.dp, highlighted = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.deviceName, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Tap to rename", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename device", tint = colors.textMuted)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted)
                }
            }
        }

        item { SectionLabel("Sharing") }
        item {
            ShareXCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Column {
                    SettingSwitch("Visible to nearby devices", state.visible, actions.onVisible, subtitle = "Others can find you and send requests", icon = Icons.Rounded.Visibility)
                    SettingSwitch(
                        "Receive in background",
                        state.backgroundVisible,
                        actions.onBackgroundVisible,
                        subtitle = "Stay visible after leaving the app (shows a notification)",
                        icon = Icons.Rounded.NightsStay,
                    )
                    SettingSwitch(
                        "Auto-accept trusted devices",
                        state.autoAcceptTrusted,
                        actions.onAutoAccept,
                        subtitle = "Skip the prompt for devices you've trusted",
                        icon = Icons.Rounded.AutoAwesome,
                    )
                }
            }
        }

        item { SectionLabel("Direct connections") }
        item {
            val enabled = state.nearbySupported && state.nearbyAvailable
            ShareXCard(Modifier.fillMaxWidth(), onClick = if (state.nearbySupported && !state.nearbyAvailable) actions.onGrantNearby else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.WifiTethering, size = 38.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth + Wi-Fi Direct", style = MaterialTheme.typography.titleSmall, color = colors.text)
                        Text(
                            when {
                                !state.nearbySupported -> "Not supported on this device"
                                state.nearbyAvailable -> "Phones connect directly when there's no shared network"
                                else -> "Needs nearby devices + precise location. Tap to allow"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        if (enabled) "On" else if (state.nearbySupported) "Off" else "Unavailable",
                        color = if (enabled) colors.success else colors.warning,
                    )
                }
            }
        }

        item { SectionLabel("Appearance") }
        item {
            ShareXCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.padding(start = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            ThemeChoice(mode.label(), selected = mode == state.theme, modifier = Modifier.weight(1f)) { actions.onTheme(mode) }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Security & storage") }
        item {
            ShareXCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                Column {
                    NavRow(Icons.Rounded.Key, "Your key fingerprint", state.fingerprint, onClick = actions.onCopyFingerprint, hint = "Copy fingerprint")
                    NavRow(
                        Icons.Rounded.Folder,
                        "Received files",
                        "Downloads/ShareX" + if (state.freeStorageBytes >= 0) " · ${Format.bytes(state.freeStorageBytes)} free" else "",
                        onClick = actions.onOpenReceived,
                        hint = "Open received files",
                    )
                    if (state.freeStorageBytes < 0) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = colors.warning, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Storage unavailable. Received files can't be saved until it is back.", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                        }
                    }
                    NavRow(Icons.Rounded.Lock, "Encryption", "ECDH P-256 · device signatures · AES-256-GCM", onClick = { showEncryption = true }, hint = "Show encryption details")
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(state.deviceName, onDismiss = { renaming = false }, onSave = {
            renaming = false
            actions.onRename(it)
        })
    }
    if (showEncryption) {
        ShareXAlertDialog(
            title = "How transfers are protected",
            confirmText = "Got it",
            dismissText = null,
            onConfirm = { showEncryption = false },
            onDismiss = { showEncryption = false },
        ) {
            Text(
                "Every transfer uses a fresh ECDH P-256 key exchange, device signatures and AES-256-GCM. " +
                    "Nothing leaves the local link and no server is involved.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ShareX.colors
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accentContainer else colors.surfaceHigh)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.outline, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$label theme" + if (selected) ", selected" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.accent else colors.textMuted)
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit, hint: String) {
    val colors = ShareX.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted)
    }
}
