package com.sharex.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.Peer
import com.sharex.core.link.ConnectLink
import com.sharex.core.store.HistoryEntry
import com.sharex.core.transfer.ChatMessage
import com.sharex.core.transfer.ChatTimeline
import com.sharex.core.transfer.Decision
import com.sharex.core.transfer.FileSendItem
import com.sharex.core.transfer.SendItem
import com.sharex.core.transfer.TextSendItem
import com.sharex.core.util.FileNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Destinations. Home / Devices / Profile are the sidebar tabs; the rest are pushed on one back stack. */
sealed interface DesktopScreen {
    data object Home : DesktopScreen
    data object Devices : DesktopScreen
    data object Profile : DesktopScreen
    data class Device(val id: String) : DesktopScreen
    data class Chat(val id: String) : DesktopScreen
    data class Send(val targetId: String? = null) : DesktopScreen
    data object Receive : DesktopScreen
    data object History : DesktopScreen
    data object MyQr : DesktopScreen
    data class Transfer(val id: String) : DesktopScreen
}

sealed interface DesktopDialog {
    /** [targetId] null: add the text to the Send selection; otherwise send straight to that device. */
    data class SendText(val targetId: String? = null) : DesktopDialog
    data object ChooseFolder : DesktopDialog
    data object ConnectByCode : DesktopDialog
}

/** Keyboard scrolling, routed to whichever page is on screen. */
enum class ScrollCommand { LINE_UP, LINE_DOWN, PAGE_UP, PAGE_DOWN, TOP, BOTTOM }

sealed interface DesktopEvent {
    /** Ask the window to open a file chooser. [targetId] null: add to the Send selection. */
    data class PickFiles(val targetId: String?) : DesktopEvent
}

data class DesktopItem(val key: String, val item: SendItem, val file: File?) {
    val name: String get() = item.name
    val size: Long get() = item.size
    val isText: Boolean get() = item is TextSendItem
}

class DesktopController(val graph: DesktopGraph) {
    val engine = graph.engine

    private val backStack = MutableStateFlow<List<DesktopScreen>>(listOf(DesktopScreen.Home))
    val screen: StateFlow<DesktopScreen> = backStack.map { it.last() }.stateIn(graph.scope, SharingStarted.Eagerly, DesktopScreen.Home)

    /** Which sidebar tab owns the current screen: the tab this stack was built on top of Home from. */
    val tab: StateFlow<DesktopScreen> = backStack.map { stack ->
        stack.getOrNull(1)?.takeIf { it == DesktopScreen.Devices || it == DesktopScreen.Profile } ?: DesktopScreen.Home
    }.stateIn(graph.scope, SharingStarted.Eagerly, DesktopScreen.Home)

    private val _selection = MutableStateFlow<List<DesktopItem>>(emptyList())
    val selection: StateFlow<List<DesktopItem>> = _selection.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _networkChecked = MutableStateFlow(false)
    val networkChecked: StateFlow<Boolean> = _networkChecked.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _dialog = MutableStateFlow<DesktopDialog?>(null)
    val dialog: StateFlow<DesktopDialog?> = _dialog.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _events = MutableSharedFlow<DesktopEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DesktopEvent> = _events.asSharedFlow()

    private val _scroll = MutableSharedFlow<ScrollCommand>(extraBufferCapacity = 16)
    val scrollCommands: SharedFlow<ScrollCommand> = _scroll.asSharedFlow()

    /**
     * Window-level key handling. Only receives keys the focused control did not use, so typing in a text box is never
     * affected: PgUp/PgDn/Up/Down/Home/End scroll, Esc or Alt+Left goes back (or closes a dialog).
     */
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val scrollable = _dialog.value == null
        return when {
            event.key == Key.Escape || (event.key == Key.DirectionLeft && event.isAltPressed) -> when {
                _dialog.value != null -> { _dialog.value = null; true }
                backStack.value.size > 1 -> { back(); true }
                else -> false
            }
            !scrollable -> false
            event.key == Key.PageUp -> _scroll.tryEmit(ScrollCommand.PAGE_UP)
            event.key == Key.PageDown || event.key == Key.Spacebar -> _scroll.tryEmit(ScrollCommand.PAGE_DOWN)
            event.key == Key.MoveHome || (event.key == Key.DirectionUp && event.isCtrlPressed) -> _scroll.tryEmit(ScrollCommand.TOP)
            event.key == Key.MoveEnd || (event.key == Key.DirectionDown && event.isCtrlPressed) -> _scroll.tryEmit(ScrollCommand.BOTTOM)
            event.key == Key.DirectionUp -> _scroll.tryEmit(ScrollCommand.LINE_UP)
            event.key == Key.DirectionDown -> _scroll.tryEmit(ScrollCommand.LINE_DOWN)
            else -> false
        }
    }

    /** Files dropped anywhere on the window: into the open conversation/device if online, otherwise onto the Send list. */
    fun onFilesDropped(files: List<File>) {
        if (files.isEmpty()) return
        val current = screen.value
        val targetId = (current as? DesktopScreen.Chat)?.id ?: (current as? DesktopScreen.Device)?.id
        if (targetId != null) sendFilesTo(targetId, files) else addFiles(files)
    }

    /** Short description of what a drop would do right now, for the drop overlay. */
    fun dropTargetName(): String? {
        val current = screen.value
        val id = (current as? DesktopScreen.Chat)?.id ?: (current as? DesktopScreen.Device)?.id ?: return null
        return devices.value.firstOrNull { it.id == id }?.name
    }

    /** Known + currently discoverable devices with real online state and last-seen times. */
    val devices: StateFlow<List<DeviceEntry>> = combine(graph.knownDevices.devices, engine.peers) { known, peers ->
        DeviceDirectory.merge(known, peers)
    }.stateIn(graph.scope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryEntry>> get() = graph.history.entries

    private val retryTargets = mutableMapOf<String, Pair<Peer, List<DesktopItem>>>()
    private var pendingLink: ConnectLink? = null

    init {
        graph.scope.launch {
            while (isActive) {
                _addresses.value = runCatching { graph.network.shareableAddresses() }.getOrDefault(emptyList())
                _networkChecked.value = true
                delay(4_000)
            }
        }
        // A trusted device that starts sending without a prompt: show its progress unless we're already looking at transfers.
        graph.scope.launch {
            val opened = mutableSetOf<String>()
            engine.transfers.collect { list ->
                val current = screen.value
                list.filter { it.direction == com.sharex.core.transfer.Direction.RECEIVE && it.phase == com.sharex.core.transfer.TransferPhase.TRANSFERRING && opened.add(it.id) }
                    .forEach { t ->
                        if (current == DesktopScreen.Home || current == DesktopScreen.MyQr) navigate(DesktopScreen.Transfer(t.id))
                    }
            }
        }
        // Discovery queries only run while scanning: keep them on for every screen that lists devices.
        graph.scope.launch {
            screen.collect { engine.setScanning(shouldScan(it)) }
        }
    }

    private fun shouldScan(screen: DesktopScreen) = when (screen) {
        DesktopScreen.Home, DesktopScreen.Devices, is DesktopScreen.Device, is DesktopScreen.Chat, is DesktopScreen.Send, DesktopScreen.Receive -> true
        else -> false
    }

    // Navigation ---------------------------------------------------------------------------------

    fun navigate(target: DesktopScreen) = backStack.update { if (it.last() == target) it else it + target }

    fun back() = backStack.update { if (it.size > 1) it.dropLast(1) else it }

    fun selectTab(tab: DesktopScreen) {
        backStack.value = if (tab == DesktopScreen.Home) listOf(DesktopScreen.Home) else listOf(DesktopScreen.Home, tab)
    }

    fun showDialog(dialog: DesktopDialog?) {
        _dialog.value = dialog
    }

    fun chatFor(id: String): StateFlow<List<ChatMessage>> =
        combine(history, engine.transfers, devices) { entries, live, list ->
            ChatTimeline.build(id, list.firstOrNull { it.id == id }?.name, entries, live)
        }.stateIn(graph.scope, SharingStarted.Lazily, emptyList())

    fun requestPick(targetId: String?) {
        _events.tryEmit(DesktopEvent.PickFiles(targetId))
    }

    // Discovery ----------------------------------------------------------------------------------

    fun refreshDevices() {
        if (_refreshing.value) return
        graph.scope.launch {
            _refreshing.value = true
            engine.setScanning(false)
            engine.setScanning(true)
            engine.refreshPresence()
            delay(2_500)
            engine.setScanning(shouldScan(screen.value))
            _refreshing.value = false
        }
    }

    // Selection ----------------------------------------------------------------------------------

    private fun toItems(files: List<File>): List<DesktopItem> {
        val expanded = files.flatMap { file ->
            if (file.isDirectory) file.walkTopDown().filter { it.isFile && !it.isHidden }.take(5_000).toList() else listOf(file)
        }.filter { it.isFile && it.canRead() }
        if (expanded.isEmpty()) flash("Nothing readable to add")
        return expanded.map { DesktopItem(it.absolutePath, FileSendItem(it, FileNames.mimeFromName(it.name)), it) }
    }

    fun addFiles(files: List<File>) {
        val items = toItems(files)
        if (items.isEmpty()) return
        _selection.update { (it + items).distinctBy { item -> item.key } }
        val link = pendingLink
        if (link != null) {
            pendingLink = null
            sendToLink(link)
        } else if (screen.value !is DesktopScreen.Send) {
            navigate(DesktopScreen.Send())
        }
    }

    fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _selection.update { it + DesktopItem("text:${System.nanoTime()}", TextSendItem(trimmed), null) }
        val link = pendingLink
        if (link != null) {
            pendingLink = null
            sendToLink(link)
        } else if (screen.value !is DesktopScreen.Send) {
            navigate(DesktopScreen.Send())
        }
    }

    fun remove(key: String) = _selection.update { list -> list.filterNot { it.key == key } }

    fun clearSelection() {
        _selection.value = emptyList()
    }

    // Sending ------------------------------------------------------------------------------------

    private fun onlinePeer(deviceId: String): Peer? {
        val peer = engine.peers.value.firstOrNull { it.id == deviceId }
        if (peer == null) {
            val name = devices.value.firstOrNull { it.id == deviceId }?.name ?: "That device"
            flash("$name is offline. Make sure it's nearby with ShareX open, then refresh.")
        }
        return peer
    }

    /** Sends the Send-screen selection to an online device and opens its conversation. */
    fun sendSelectionTo(deviceId: String) {
        val items = _selection.value
        if (items.isEmpty()) {
            flash("Add files first")
            return
        }
        val peer = onlinePeer(deviceId) ?: return
        startDirect(peer, items)
        _selection.value = emptyList()
    }

    fun sendFilesTo(deviceId: String, files: List<File>) {
        val peer = onlinePeer(deviceId) ?: return
        val items = toItems(files)
        if (items.isNotEmpty()) startDirect(peer, items)
    }

    fun sendTextTo(deviceId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val peer = onlinePeer(deviceId) ?: return
        startDirect(peer, listOf(DesktopItem("text:${System.nanoTime()}", TextSendItem(trimmed), null)))
    }

    private fun startDirect(peer: Peer, items: List<DesktopItem>) {
        val latest = engine.peers.value.firstOrNull { it.id == peer.id } ?: peer
        val id = engine.send(latest, items.map { it.item })
        retryTargets[id] = latest to items
        if (screen.value != DesktopScreen.Chat(peer.id)) navigate(DesktopScreen.Chat(peer.id))
    }

    fun sendToAddress(address: String) {
        val items = _selection.value
        if (items.isEmpty()) {
            flash("Add files first")
            return
        }
        val link = ConnectLink.parse(address)
        val id = if (link != null) engine.send(link, items.map { it.item }) else engine.sendToAddress(address.trim(), items.map { it.item })
        _selection.value = emptyList()
        navigate(DesktopScreen.Transfer(id))
    }

    private fun sendToLink(link: ConnectLink) {
        val items = _selection.value
        if (items.isEmpty()) return
        val id = engine.send(link, items.map { it.item })
        _selection.value = emptyList()
        navigate(DesktopScreen.Transfer(id))
    }

    fun canRetry(id: String) = retryTargets.containsKey(id)

    fun retry(id: String) {
        val (peer, items) = retryTargets[id] ?: return
        val latest = engine.peers.value.firstOrNull { it.id == peer.id }
        if (latest == null) {
            flash("${peer.device.name} is offline. Make sure it's nearby, then try again.")
            return
        }
        engine.dismiss(id)
        val newId = engine.send(latest, items.map { it.item })
        retryTargets[newId] = latest to items
    }

    // QR / connect code --------------------------------------------------------------------------

    /** Handles a decoded QR payload or a pasted `sharex://` link, mirroring the phone's scan flow. */
    fun onCodeEntered(raw: String) {
        val link = ConnectLink.parse(raw.trim())
        if (link == null) {
            flash("That isn't a ShareX code")
            return
        }
        if (link.id == graph.identity.deviceId) {
            flash("That's this computer's own code")
            return
        }
        if (_selection.value.isEmpty()) {
            pendingLink = link
            flash("Choose what to send to ${link.name}")
            requestPick(null)
        } else {
            sendToLink(link)
        }
    }

    /** Decodes a QR code from a screenshot or photo file (Windows has no camera scanner in ShareX). */
    fun decodeQr(file: File): String? = runCatching {
        val image: BufferedImage = ImageIO.read(file) ?: return null
        decodeQr(image)
    }.getOrNull()

    fun decodeQr(image: BufferedImage): String? = runCatching {
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))).text
    }.getOrNull()

    // Receiving ----------------------------------------------------------------------------------

    fun respond(id: String, decision: Decision) = engine.respond(id, decision)

    fun cancel(id: String) = engine.cancel(id)

    fun dismiss(id: String) = engine.dismiss(id)

    fun setTrusted(id: String, trusted: Boolean) = graph.knownDevices.setTrusted(id, trusted)

    fun forgetDevice(id: String) = graph.knownDevices.remove(id)

    fun connectLink(): ConnectLink? {
        val port = graph.lan.port.value ?: return null
        val hosts = _addresses.value.take(4).ifEmpty { return null }
        val self = engine.self
        return ConnectLink(self.id, self.name, self.type, hosts, port)
    }

    fun flash(text: String) {
        _message.value = text
        graph.scope.launch {
            delay(3_500)
            if (_message.value == text) _message.value = null
        }
    }
}
