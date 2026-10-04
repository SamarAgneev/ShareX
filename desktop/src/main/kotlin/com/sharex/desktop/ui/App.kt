package com.sharex.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sharex.desktop.Platform
import java.io.File
import androidx.compose.ui.window.WindowState
import com.sharex.core.transfer.TransferPhase
import com.sharex.desktop.DesktopController
import com.sharex.desktop.DesktopDialog
import com.sharex.desktop.DesktopEvent
import com.sharex.desktop.DesktopScreen
import com.sharex.desktop.WindowsFrame

@Composable
fun FrameWindowScope.App(controller: DesktopController, state: WindowState, onClose: () -> Unit, onQuit: () -> Unit) {
    val settings by controller.graph.settings.state.collectAsState()
    val screen by controller.screen.collectAsState()
    val tab by controller.tab.collectAsState()
    val transfers by controller.engine.transfers.collectAsState()
    val message by controller.message.collectAsState()
    val dialog by controller.dialog.collectAsState()
    var keepOnTop by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.events.collect { event ->
            when (event) {
                is DesktopEvent.PickFiles -> {
                    val files = chooseFiles(window)
                    if (files.isNotEmpty()) {
                        if (event.targetId != null) controller.sendFilesTo(event.targetId, files) else controller.addFiles(files)
                    }
                }
            }
        }
    }

    ShareXDesktopTheme(settings.theme) {
        val c = Theme.colors
        LaunchedEffect(c.isDark) { WindowsFrame.style(window, c.outline.toArgb() and 0xFFFFFF) }
        val menu = listOf(
            MenuAction("Transfer history", Icons.Rounded.History) { controller.navigate(DesktopScreen.History) },
            MenuAction("Open received folder", Icons.Rounded.FolderOpen) { Platform.openFolder(File(settings.downloadDir)) },
            MenuAction(if (keepOnTop) "Stop keeping on top" else "Keep on top", Icons.Rounded.PushPin) {
                keepOnTop = !keepOnTop
                window.isAlwaysOnTop = keepOnTop
            },
            MenuAction("Quit ShareX", Icons.Rounded.PowerSettingsNew, destructive = true, onClick = onQuit),
        )
        WindowChrome(state, onClose, menu) {
            FileDropArea(onDrop = controller::onFilesDropped, modifier = Modifier.fillMaxSize()) { dragging ->
            CompositionLocalProvider(LocalScrollCommands provides controller.scrollCommands, LocalDragging provides dragging) {
            Box(Modifier.fillMaxSize().background(c.background)) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        tab = tab,
                        onTab = controller::selectTab,
                        deviceName = settings.deviceName,
                        visible = settings.visible,
                        activeCount = transfers.count { it.isActive && it.phase != TransferPhase.AWAITING_DECISION },
                        onVisible = { value -> controller.graph.settings.update { it.copy(visible = value) } },
                    )
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screens",
                        ) { current ->
                            when (current) {
                                DesktopScreen.Home -> HomePage(controller)
                                DesktopScreen.Devices -> DevicesPage(controller)
                                DesktopScreen.Profile -> ProfilePage(controller)
                                is DesktopScreen.Device -> DeviceDetailsPage(controller, current.id, window)
                                is DesktopScreen.Chat -> ChatPage(controller, current.id, window)
                                is DesktopScreen.Send -> SendPage(controller, current.targetId, window)
                                DesktopScreen.Receive -> ReceivePage(controller)
                                DesktopScreen.History -> HistoryPage(controller)
                                DesktopScreen.MyQr -> MyQrPage(controller)
                                is DesktopScreen.Transfer -> TransferPage(controller, current.id)
                            }
                        }
                    }
                }

                when (val d = dialog) {
                    is DesktopDialog.SendText -> SendTextDialog(
                        onDismiss = { controller.showDialog(null) },
                        confirmLabel = if (d.targetId == null) "Add" else "Send",
                        onAdd = {
                            controller.showDialog(null)
                            if (d.targetId == null) controller.addText(it) else controller.sendTextTo(d.targetId, it)
                        },
                    )
                    DesktopDialog.ChooseFolder -> ChooseFolderDialog(
                        current = settings.downloadDir,
                        window = window,
                        onDismiss = { controller.showDialog(null) },
                        onSelect = { path ->
                            controller.graph.settings.update { it.copy(downloadDir = path) }
                            controller.showDialog(null)
                        },
                    )
                    DesktopDialog.ConnectByCode -> ConnectByCodeDialog(
                        window = window,
                        onDismiss = { controller.showDialog(null) },
                        onDecodeFile = controller::decodeQr,
                        onDecodeImage = controller::decodeQr,
                        onCode = {
                            controller.showDialog(null)
                            controller.onCodeEntered(it)
                        },
                        onError = controller::flash,
                    )
                    null -> Unit
                }

                transfers.firstOrNull { it.phase == TransferPhase.AWAITING_DECISION }?.let { request ->
                    IncomingRequestOverlay(request, onRespond = { controller.respond(request.id, it) })
                }

                DropOverlay(visible = dragging, targetName = controller.dropTargetName(), onSendPage = screen is DesktopScreen.Send)

                AnimatedVisibility(
                    visible = message != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                ) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh).padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(message.orEmpty(), color = c.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun Sidebar(
    tab: DesktopScreen,
    onTab: (DesktopScreen) -> Unit,
    deviceName: String,
    visible: Boolean,
    activeCount: Int,
    onVisible: (Boolean) -> Unit,
) {
    val c = Theme.colors
    Column(Modifier.width(236.dp).fillMaxHeight().background(c.sidebar).padding(horizontal = 14.dp, vertical = 20.dp)) {
        Wordmark(Modifier.padding(start = 8.dp, bottom = 26.dp))
        NavItem("Home", Icons.Rounded.Home, tab == DesktopScreen.Home, badge = activeCount.takeIf { it > 0 }) { onTab(DesktopScreen.Home) }
        NavItem("Devices", Icons.Rounded.People, tab == DesktopScreen.Devices) { onTab(DesktopScreen.Devices) }
        NavItem("Profile", Icons.Rounded.Person, tab == DesktopScreen.Profile) { onTab(DesktopScreen.Profile) }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(deviceName, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(visible, 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (visible) "Visible" else "Hidden", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Switch(
                    checked = visible,
                    onCheckedChange = onVisible,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                        uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.outline, uncheckedThumbColor = c.textMuted,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, badge: Int? = null, onClick: () -> Unit) {
    val c = Theme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accentContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = if (selected) c.accent else c.textMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) c.accent else c.textMuted, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(Modifier.clip(CircleShape).background(c.accent).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text(badge.toString(), color = c.onAccent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Full-window overlay shown while files are dragged over the app. */
@Composable
private fun DropOverlay(visible: Boolean, targetName: String?, onSendPage: Boolean) {
    val c = Theme.colors
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.86f)).padding(18.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRoundRect(
                            color = c.accent,
                            cornerRadius = CornerRadius(26.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                        )
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.accent.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBubble(Icons.Rounded.CloudUpload, size = 84.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (targetName != null) "Drop to send to $targetName" else if (onSendPage) "Drop to add to your selection" else "Drop to send",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Files and folders · end-to-end encrypted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                    )
                }
            }
        }
    }
}
