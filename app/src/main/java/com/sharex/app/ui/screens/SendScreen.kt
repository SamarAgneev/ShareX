package com.sharex.app.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharex.app.ui.SelectedItem
import com.sharex.app.ui.components.DeviceBadge
import com.sharex.app.ui.components.GradientButton
import com.sharex.app.ui.components.IconBubble
import com.sharex.app.ui.components.OnlineStatus
import com.sharex.app.ui.components.ScreenTopBar
import com.sharex.app.ui.components.SectionHeader
import com.sharex.app.ui.components.ShareXAlertDialog
import com.sharex.app.ui.components.ShareXInput
import com.sharex.app.ui.components.SoftButton
import com.sharex.app.ui.iconForFile
import com.sharex.app.ui.isVisualMedia
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.util.FileNames
import com.sharex.core.util.Format

enum class PickKind { FILES, PHOTOS, VIDEOS, APPS }

@Composable
fun SendScreen(
    targetId: String?,
    devices: List<DeviceEntry>,
    selection: List<SelectedItem>,
    resolving: Boolean,
    refreshing: Boolean,
    onBack: () -> Unit,
    onPick: (PickKind) -> Unit,
    onCompose: () -> Unit,
    onRemove: (String) -> Unit,
    onSendToDevice: (String) -> Unit,
    onScanQr: () -> Unit,
    onSendToAddress: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = ShareX.colors
    var showAddressDialog by remember { mutableStateOf(false) }
    var chosenId by rememberSaveable { mutableStateOf(targetId) }
    val chosen = devices.firstOrNull { it.id == chosenId }
    val total = selection.sumOf { it.size }

    val hint = when {
        selection.isEmpty() -> "Choose what to send first."
        chosen == null -> "Choose a device to send to."
        !chosen.online -> "${chosen.name} is offline. Bring it nearby and refresh."
        else -> null
    }
    val canSend = hint == null && chosen != null

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Send",
            subtitle = if (selection.isEmpty()) "Nothing selected" else "${selection.size} ${if (selection.size == 1) "item" else "items"} · ${Format.bytes(total)}",
            onBack = onBack,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryTile("Files", Icons.Rounded.InsertDriveFile, Modifier.weight(1f)) { onPick(PickKind.FILES) }
                    CategoryTile("Photos", Icons.Rounded.PhotoLibrary, Modifier.weight(1f)) { onPick(PickKind.PHOTOS) }
                    CategoryTile("Videos", Icons.Rounded.Videocam, Modifier.weight(1f)) { onPick(PickKind.VIDEOS) }
                    CategoryTile("Apps", Icons.Rounded.Android, Modifier.weight(1f)) { onPick(PickKind.APPS) }
                    CategoryTile("Text", Icons.Rounded.Notes, Modifier.weight(1f), onClick = onCompose)
                }
            }

            item {
                SectionHeader("Selected") {
                    if (resolving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
                    else if (selection.isNotEmpty()) Text("Total ${Format.bytes(total)}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                }
            }
            if (selection.isEmpty()) {
                item {
                    Text(
                        "Pick files, photos, videos, apps or write some text above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            } else {
                items(selection, key = { "sel-" + it.key }) { item -> SelectionRow(item, onRemove = { onRemove(item.key) }) }
            }

            item {
                SectionHeader("Send to") {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh devices", tint = if (refreshing) colors.textMuted else colors.accent)
                    }
                }
            }
            if (devices.isEmpty()) {
                item {
                    if (refreshing) LoadingState("Looking for nearby devices…")
                    else EmptyState(
                        "No devices found",
                        "Open ShareX on the other device and keep it visible, or scan its QR code.",
                        Icons.Rounded.QrCodeScanner,
                    )
                }
            } else {
                items(devices, key = { "dev-" + it.id }) { entry ->
                    TargetRow(entry, selected = entry.id == chosenId, onClick = { chosenId = entry.id })
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            GradientButton(
                text = if (chosen != null) "SEND TO ${chosen.name.uppercase()}" else "SEND TO DEVICE",
                onClick = { chosen?.let { onSendToDevice(it.id) } },
                icon = Icons.Rounded.Upload,
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Scan QR", onClick = onScanQr, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.weight(1f))
                SoftButton("Address", onClick = { showAddressDialog = true }, icon = Icons.Rounded.Keyboard, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showAddressDialog) {
        AddressDialog(
            onDismiss = { showAddressDialog = false },
            onConfirm = {
                showAddressDialog = false
                onSendToAddress(it)
            },
        )
    }
}

@Composable
private fun CategoryTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ShareX.colors
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.text, maxLines = 1)
    }
}

@Composable
private fun SelectionRow(item: SelectedItem, onRemove: () -> Unit) {
    val colors = ShareX.colors
    var imageFailed by remember(item.key) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.uri != null && isVisualMedia(item.name, item.mimeType) && !imageFailed) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceHigh)) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            IconBubble(iconForFile(item.name, item.mimeType, item.isText), size = 48.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (item.isText) (item.item.let { (it as com.sharex.core.transfer.TextSendItem).text }) else item.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = if (item.isText) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val kind = when {
                item.isText -> "Text"
                else -> {
                    val mime = item.mimeType ?: FileNames.mimeFromName(item.name).orEmpty()
                    when {
                        mime.startsWith("image/") -> "Image"
                        mime.startsWith("video/") -> "Video"
                        else -> FileNames.extension(item.name).uppercase().ifEmpty { "File" }
                    }
                }
            }
            Text("$kind · ${Format.bytes(item.size)}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${item.name}", tint = colors.textMuted) }
    }
}

@Composable
private fun TargetRow(entry: DeviceEntry, selected: Boolean, onClick: () -> Unit) {
    val colors = ShareX.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accentContainer else colors.surface)
            .clickable(enabled = entry.online, role = Role.RadioButton, onClick = onClick)
            .alpha(if (entry.online) 1f else 0.55f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceBadge(entry, size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OnlineStatus(entry.online)
        }
        if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium, color = colors.accent)
    }
}

@Composable
private fun AddressDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    ShareXAlertDialog(
        title = "Send to an address",
        confirmText = "Send",
        confirmEnabled = text.isNotBlank(),
        onConfirm = { onConfirm(text) },
        onDismiss = onDismiss,
    ) {
        Text(
            "Type the IP address shown on the other device, e.g. 192.168.1.20. You'll confirm the PIN on both screens.",
            style = MaterialTheme.typography.bodyMedium,
            color = ShareX.colors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        ShareXInput(
            value = text,
            onValueChange = { text = it.trim() },
            placeholder = "192.168.1.20",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
    }
}
