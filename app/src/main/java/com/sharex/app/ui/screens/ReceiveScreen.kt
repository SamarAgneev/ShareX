package com.sharex.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.app.net.NetworkStatus
import com.sharex.app.ui.components.DeviceAvatar
import com.sharex.app.ui.components.PulseRings
import com.sharex.app.ui.components.ScreenTopBar
import com.sharex.app.ui.components.ShareXCard
import com.sharex.app.ui.components.SoftButton
import com.sharex.app.ui.openDownloads
import com.sharex.app.ui.openReceivedFile
import com.sharex.app.ui.shareReceivedFile
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.DeviceType
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format

data class ReceiveActions(
    val onBack: () -> Unit,
    val onMakeVisible: () -> Unit,
    val onShowQr: () -> Unit,
    val onOpenTransfer: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onDismiss: (String) -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
fun ReceiveScreen(
    deviceName: String,
    deviceType: DeviceType,
    visible: Boolean,
    network: NetworkStatus,
    networkChecked: Boolean,
    nearbyAvailable: Boolean,
    transfers: List<TransferInfo>,
    actions: ReceiveActions,
) {
    val colors = ShareX.colors
    val active = transfers.filter { it.isActive }
    val offline = networkChecked && !network.connected && !nearbyAvailable

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Receive", onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            if (active.isEmpty()) {
                when {
                    !visible -> item {
                        ErrorState(
                            "You're hidden",
                            "Other devices can't find you. Turn on visibility to receive files.",
                            Icons.Rounded.Visibility,
                            retryLabel = "Become visible",
                            onRetry = actions.onMakeVisible,
                        )
                    }
                    offline -> item {
                        ErrorState(
                            "No connection",
                            "Join a Wi-Fi network, or allow nearby devices access so phones can connect directly.",
                            Icons.Rounded.WifiOff,
                            retryLabel = "Check again",
                            onRetry = actions.onRefresh,
                        )
                    }
                    else -> item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                PulseRings(Modifier.size(220.dp))
                                DeviceAvatar(deviceType, size = 68.dp, highlighted = true)
                            }
                            Text("Waiting for a sender…", style = MaterialTheme.typography.titleLarge, color = colors.text, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Visible as $deviceName. Ask the sender to pick this device or scan your QR code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            SoftButton("My QR code", onClick = actions.onShowQr, icon = Icons.Rounded.QrCode2)
                        }
                    }
                }
            }
            items(transfers, key = { "rx-" + it.id }) { transfer ->
                ReceiveCard(transfer, actions)
            }
        }
    }
}

@Composable
private fun ReceiveCard(transfer: TransferInfo, actions: ReceiveActions) {
    val colors = ShareX.colors
    val context = LocalContext.current
    val title = transfer.items.singleOrNull()?.let { if (it.isText) "Text" else it.name } ?: "${transfer.items.size} items"
    val saved = transfer.received.filter { it.location != null }
    val single = saved.singleOrNull()
    ShareXCard(Modifier.fillMaxWidth(), onClick = { actions.onOpenTransfer(transfer.id) }, contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (transfer.phase) {
                    TransferPhase.COMPLETED -> Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(28.dp))
                    TransferPhase.FAILED, TransferPhase.CANCELLED, TransferPhase.REJECTED ->
                        Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(28.dp))
                    else -> Unit
                }
                if (transfer.phase.isFinished) Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (transfer.phase) {
                            TransferPhase.COMPLETED -> "Transfer complete"
                            TransferPhase.FAILED -> "Transfer failed"
                            TransferPhase.CANCELLED -> "Transfer cancelled"
                            TransferPhase.REJECTED -> "Declined"
                            TransferPhase.AWAITING_DECISION -> "${transfer.peerName} wants to share"
                            else -> "Receiving from ${transfer.peerName}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                    )
                    Text("From ${transfer.peerName}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                if (transfer.phase.isFinished) {
                    IconButton(onClick = { actions.onDismiss(transfer.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = colors.textMuted)
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Format.bytes(transfer.totalBytes), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)

            if (transfer.phase == TransferPhase.TRANSFERRING) {
                LinearProgressIndicator(
                    progress = { transfer.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = colors.accent,
                    trackColor = colors.surfaceHigh,
                    drawStopIndicator = {},
                )
                val parts = buildList {
                    add("${(transfer.fraction * 100).toInt()}%")
                    add("${Format.bytes(transfer.bytesDone)} / ${Format.bytes(transfer.totalBytes)}")
                    if (transfer.bytesPerSecond > 0) add(Format.speed(transfer.bytesPerSecond))
                    if (transfer.etaSeconds >= 0) add("${Format.duration(transfer.etaSeconds)} left")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            if (transfer.isActive) {
                TextButton(onClick = { actions.onCancel(transfer.id) }) { Text("Cancel", color = colors.danger) }
            }
            if ((transfer.phase == TransferPhase.FAILED || transfer.phase == TransferPhase.CANCELLED) && !transfer.message.isNullOrBlank()) {
                Text(transfer.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            if (transfer.phase == TransferPhase.COMPLETED) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (single != null) {
                        SoftButton("Open", onClick = { openReceivedFile(context, single.location!!, single.mimeType, single.name) }, icon = Icons.Rounded.OpenInNew, modifier = Modifier.weight(1f))
                    }
                    SoftButton("Folder", onClick = { openDownloads(context) }, icon = Icons.Rounded.Folder, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Details", onClick = { actions.onOpenTransfer(transfer.id) }, icon = Icons.Rounded.Info, modifier = Modifier.weight(1f))
                    if (single != null) {
                        SoftButton("Share", onClick = { shareReceivedFile(context, single.location!!, single.mimeType, single.name) }, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
