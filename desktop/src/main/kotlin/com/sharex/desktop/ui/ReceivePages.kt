package com.sharex.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferPhase
import com.sharex.core.util.Format
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopScreen
import com.sharex.desktop.Platform
import java.io.File

@Composable
fun ReceivePage(controller: DesktopController) {
    val c = Theme.colors
    val settings by controller.graph.settings.state.collectAsState()
    val transfers by controller.engine.transfers.collectAsState()
    val addresses by controller.addresses.collectAsState()
    val checked by controller.networkChecked.collectAsState()
    val incoming = transfers.filter { it.direction == Direction.RECEIVE && it.phase != TransferPhase.AWAITING_DECISION }
    val active = incoming.filter { it.isActive }
    val offline = checked && addresses.isEmpty()

    PageScaffold("Receive", "Files from other devices land in ${File(settings.downloadDir).name}.", onBack = controller::back, maxWidth = 760.dp) {
        if (active.isEmpty()) {
            when {
                !settings.visible -> ErrorState(
                    "You're hidden",
                    "Other devices can't find this computer. Turn on visibility to receive files.",
                    Icons.Rounded.Visibility,
                    retryLabel = "Become visible",
                    onRetry = { controller.graph.settings.update { it.copy(visible = true) } },
                )
                offline -> ErrorState(
                    "No connection",
                    "Connect this computer to Wi-Fi, Ethernet or a phone hotspot. If phones can't see it, allow ShareX on private networks in Windows Firewall (TCP 47821, UDP 47820).",
                    Icons.Rounded.WifiOff,
                    retryLabel = "Check again",
                    onRetry = controller::refreshDevices,
                )
                else -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        PulseRings(Modifier.size(200.dp))
                        DeviceAvatar(controller.engine.self.type, size = 64.dp, highlighted = true)
                    }
                    Text("Waiting for a sender…", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Gap(4.dp)
                    Text(
                        "Visible as ${settings.deviceName}. Ask the sender to pick this computer or scan your QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        textAlign = TextAlign.Center,
                    )
                    Gap(14.dp)
                    SecondaryButton("My QR code", onClick = { controller.navigate(DesktopScreen.MyQr) }, icon = Icons.Rounded.QrCode2)
                }
            }
        }
        if (incoming.isNotEmpty()) {
            Gap(8.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                incoming.asReversed().forEach { t -> ReceiveCard(t, controller) }
            }
        }
    }
}

@Composable
private fun ReceiveCard(t: TransferInfo, controller: DesktopController) {
    val c = Theme.colors
    val title = t.items.singleOrNull()?.let { if (it.isText) "Text" else it.name } ?: "${t.items.size} items"
    val saved = t.received.filter { it.location != null }
    val single = saved.singleOrNull()
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (t.phase) {
                    TransferPhase.COMPLETED -> Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(28.dp))
                    TransferPhase.FAILED, TransferPhase.CANCELLED, TransferPhase.REJECTED -> Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(28.dp))
                    else -> Unit
                }
                if (t.phase.isFinished) Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (t.phase) {
                            TransferPhase.COMPLETED -> "Transfer complete"
                            TransferPhase.FAILED -> "Transfer failed"
                            TransferPhase.CANCELLED -> "Transfer cancelled"
                            TransferPhase.REJECTED -> "Declined"
                            else -> "Receiving from ${t.peerName}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                    )
                    Text("From ${t.peerName}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                if (t.phase.isFinished) IconButton(onClick = { controller.dismiss(t.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Format.bytes(t.totalBytes), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            if (t.phase == TransferPhase.TRANSFERRING) {
                Progress(t.fraction)
                Text(
                    buildList {
                        add("${(t.fraction * 100).toInt()}%")
                        add("${Format.bytes(t.bytesDone)} / ${Format.bytes(t.totalBytes)}")
                        if (t.bytesPerSecond > 0) add(Format.speed(t.bytesPerSecond))
                        if (t.etaSeconds >= 0) add("${Format.duration(t.etaSeconds)} left")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
            }
            if (t.isActive) TextButton(onClick = { controller.cancel(t.id) }) { Text("Cancel", color = c.danger) }
            if ((t.phase == TransferPhase.FAILED || t.phase == TransferPhase.CANCELLED) && !t.message.isNullOrBlank()) {
                Text(t.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.danger)
            }
            if (t.phase == TransferPhase.COMPLETED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (single != null) SecondaryButton("Open file", onClick = { Platform.openFile(File(single.location!!)) }, icon = Icons.Rounded.OpenInNew)
                    SecondaryButton(
                        "Open folder",
                        onClick = { single?.location?.let { Platform.revealFile(File(it)) } ?: Platform.openFolder(File(controller.graph.settings.state.value.downloadDir)) },
                        icon = Icons.Rounded.FolderOpen,
                    )
                    SecondaryButton("Details", onClick = { controller.navigate(DesktopScreen.Transfer(t.id)) }, icon = Icons.Rounded.Info)
                }
            }
        }
    }
}

/** Progress / result of one transfer (opened from Home, after QR/address sends, or when a trusted device starts sending). */
@Composable
fun TransferPage(controller: DesktopController, id: String) {
    val c = Theme.colors
    val transfers by controller.engine.transfers.collectAsState()
    val transfer = transfers.firstOrNull { it.id == id }
    val close = {
        transfer?.takeIf { it.phase.isFinished }?.let { controller.dismiss(it.id) }
        controller.selectTab(DesktopScreen.Home)
    }
    PageScaffold(
        if (transfer?.direction == Direction.SEND) "Sending" else "Receiving",
        onBack = close,
        maxWidth = 720.dp,
    ) {
        if (transfer == null) {
            EmptyState("This transfer is no longer available", "It was dismissed or already finished.", Icons.Rounded.Info, action = { PrimaryButton("Back to Home", onClick = { controller.selectTab(DesktopScreen.Home) }) })
            return@PageScaffold
        }
        if (transfer.phase == TransferPhase.WAITING_FOR_ACCEPT && transfer.pin != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Confirm this PIN matches on ${transfer.peerName}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    Gap(10.dp)
                    PinDigits(transfer.pin!!)
                }
            }
            Gap(12.dp)
        }
        TransferCard(
            transfer = transfer,
            canRetry = controller.canRetry(transfer.id),
            onCancel = { controller.cancel(transfer.id) },
            onDismiss = { controller.dismiss(transfer.id); controller.back() },
            onRetry = { controller.retry(transfer.id) },
            onOpenFolder = { Platform.openFolder(File(controller.graph.settings.state.value.downloadDir)) },
        )
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(if (transfer.phase.isFinished) "Done" else "Back to Home", onClick = close)
        }
    }
}

@Composable
fun MyQrPage(controller: DesktopController) {
    val c = Theme.colors
    val settings by controller.graph.settings.state.collectAsState()
    val port by controller.graph.lan.port.collectAsState()
    val addresses by controller.addresses.collectAsState()
    val link = if (port != null && addresses.isNotEmpty()) controller.connectLink() else null
    PageScaffold("My QR code", "Let another device connect straight to this computer.", onBack = controller::back, maxWidth = 640.dp) {
        Card(Modifier.fillMaxWidth(), padding = PaddingValues(24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    !settings.visible -> ErrorState(
                        "You're hidden",
                        "Turn on visibility so other devices can connect.",
                        Icons.Rounded.Visibility,
                        retryLabel = "Become visible",
                        onRetry = { controller.graph.settings.update { it.copy(visible = true) } },
                    )
                    link == null -> ErrorState("No connection", "Connect this computer to Wi-Fi, Ethernet or a hotspot to get a code.", Icons.Rounded.WifiOff, retryLabel = "Check again", onRetry = controller::refreshDevices)
                    else -> {
                        val qr = remember(link) { qrImage(link.toUri(), 420) }
                        Box(Modifier.clip(RoundedCornerShape(24.dp)).background(c.accentBrush).padding(10.dp)) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                                Image(qr, "ShareX QR code", modifier = Modifier.size(240.dp))
                            }
                        }
                        Gap(16.dp)
                        Text(settings.deviceName, style = MaterialTheme.typography.titleLarge, color = c.text)
                        link.hosts.take(3).forEach { Text("$it:${link.port}", style = MaterialTheme.typography.labelLarge, color = c.text) }
                        Gap(10.dp)
                        Text(
                            "On the other device tap Scan QR, pick files and they land in this computer's ShareX folder. The code pins this computer's identity, so nobody else on the network can pose as it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                            textAlign = TextAlign.Center,
                        )
                        Gap(10.dp)
                        Text("Key fingerprint", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                        Text(controller.graph.identity.displayFingerprint, style = MaterialTheme.typography.labelLarge, color = c.text)
                        Gap(12.dp)
                        SecondaryButton("Copy code", onClick = { Platform.copy(link.toUri()); controller.flash("Code copied") }, icon = Icons.Rounded.QrCode2)
                    }
                }
            }
        }
    }
}
