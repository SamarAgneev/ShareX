package com.sharex.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.core.DeviceType
import com.sharex.core.transfer.Decision
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format
import com.sharex.desktop.Platform
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Notes
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.File

@Composable
fun TransferCard(
    transfer: TransferInfo,
    canRetry: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val c = Theme.colors
    val sending = transfer.direction == Direction.SEND
    Card(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (transfer.phase) {
                    TransferPhase.COMPLETED -> Icons.Rounded.CheckCircle to c.success
                    TransferPhase.FAILED -> Icons.Rounded.ErrorOutline to c.danger
                    TransferPhase.REJECTED -> Icons.Rounded.Block to c.warning
                    TransferPhase.CANCELLED -> Icons.Rounded.Close to c.textMuted
                    else -> (if (sending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward) to c.accent
                }
                IconBubble(icon, size = 40.dp, tint = tint, background = tint.copy(alpha = 0.12f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (sending) "To " else "From ") + transfer.peerName,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val summary = if (transfer.items.size == 1) transfer.items.first().let { if (it.isText) "Text" else it.name } else "${transfer.items.size} items"
                    val status = when (transfer.phase) {
                        TransferPhase.CONNECTING -> "Connecting…"
                        TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for them to accept"
                        TransferPhase.AWAITING_DECISION -> "Waiting for you"
                        TransferPhase.TRANSFERRING -> "${(transfer.fraction * 100).toInt()}% · ${Format.speed(transfer.bytesPerSecond)}" +
                            (transfer.etaSeconds.takeIf { it >= 0 }?.let { " · ${Format.duration(it)} left" } ?: "")
                        TransferPhase.COMPLETED -> "Done · ${Format.bytes(transfer.totalBytes)}"
                        else -> transfer.message ?: transfer.phase.name.lowercase()
                    }
                    Text("$summary · $status", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (transfer.phase == TransferPhase.WAITING_FOR_ACCEPT && transfer.pin != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PIN", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                        Text(transfer.pin!!, style = MaterialTheme.typography.titleLarge, color = c.text)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                when {
                    transfer.isActive -> TextButton(onClick = onCancel) { Text("Cancel", color = c.danger) }
                    transfer.phase == TransferPhase.COMPLETED && !sending && transfer.received.any { it.location != null } -> {
                        SecondaryButton("Show", onClick = {
                            transfer.received.firstOrNull { it.location != null }?.location?.let { Platform.revealFile(File(it)) } ?: onOpenFolder()
                        }, icon = Icons.Rounded.FolderOpen)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                    }
                    transfer.phase != TransferPhase.COMPLETED && sending && canRetry -> {
                        SecondaryButton("Retry", onClick = onRetry, icon = Icons.Rounded.Refresh)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                    }
                    else -> IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                }
            }
            if (transfer.phase == TransferPhase.TRANSFERRING) {
                Spacer(Modifier.height(12.dp))
                Progress(transfer.fraction)
            }
            val texts = transfer.received.mapNotNull { it.text } + if (sending) emptyList() else transfer.items.filter { it.isText && transfer.phase == TransferPhase.COMPLETED }.mapNotNull { it.text }
            texts.distinct().forEach { text ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (text.startsWith("http://") || text.startsWith("https://")) {
                        IconButton(onClick = { Platform.openUrl(text) }) { Icon(Icons.Rounded.OpenInNew, "Open", tint = c.accent, modifier = Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = { Platform.copy(text) }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = c.accent, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
fun IncomingRequestOverlay(request: TransferInfo, onRespond: (Decision) -> Unit) {
    val c = Theme.colors
    var trust by remember(request.id) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                PulseRings(Modifier.size(116.dp))
                DeviceAvatar(request.peer?.type ?: DeviceType.PHONE, size = 60.dp, highlighted = true, trusted = request.peerTrusted)
            }
            Text("${request.peerName} wants to share", style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                if (request.items.all { it.isText }) "A text message" else "${request.items.size} ${if (request.items.size == 1) "item" else "items"} · ${Format.bytes(request.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                request.items.take(4).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(fileIcon(item.name, item.isText), null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (item.isText) item.text.orEmpty() else item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!item.isText) Text(Format.bytes(item.size), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    }
                }
                if (request.items.size > 4) Text("+ ${request.items.size - 4} more", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
            request.pin?.let {
                Spacer(Modifier.height(16.dp))
                Text("Make sure the PIN matches on ${request.peerName}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(8.dp))
                PinDigits(it)
            }
            if (!request.peerTrusted) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { trust = !trust }.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(trust, { trust = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textMuted))
                    Text("Always accept from this device", style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("Decline", onClick = { onRespond(Decision.DECLINE) }, modifier = Modifier.weight(1f))
                PrimaryButton("Accept", onClick = { onRespond(if (trust) Decision.ACCEPT_AND_TRUST else Decision.ACCEPT) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            }
        }
    }
}


fun fileIcon(name: String, isText: Boolean) = when {
    isText -> Icons.Rounded.Notes
    name.substringAfterLast('.', "").lowercase() in setOf("pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx") -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}

fun qrImage(content: String, size: Int = 360): androidx.compose.ui.graphics.ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) image.setRGB(x, y, if (matrix[x, y]) 0x0E1116 else 0xFFFFFF)
    return image.toComposeImageBitmap()
}
