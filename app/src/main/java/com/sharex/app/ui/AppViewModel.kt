package com.sharex.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharex.app.data.UriSendItem
import com.sharex.app.graph
import com.sharex.app.net.NetworkStatus
import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.Peer
import com.sharex.core.link.ConnectLink
import com.sharex.core.store.HistoryEntry
import com.sharex.core.store.KnownDevice
import com.sharex.core.transfer.ChatMessage
import com.sharex.core.transfer.ChatTimeline
import com.sharex.core.transfer.Decision
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.SendItem
import com.sharex.core.transfer.TextSendItem
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferPhase
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

/**
 * Navigation destinations. [Home], [Devices] and [Profile] are the bottom-bar tabs; everything else
 * is pushed on top of the single back stack held by [AppViewModel].
 */
sealed interface Screen {
    data object Home : Screen
    data object Devices : Screen
    data object Profile : Screen
    data class Device(val id: String) : Screen
    data class Chat(val id: String) : Screen

    /** [targetId] pre-selects the receiving device (used when starting from a device). */
    data class Send(val targetId: String? = null) : Screen
    data object Receive : Screen
    data class Transfer(val id: String) : Screen
    data object ReceiveQr : Screen
    data object History : Screen
}

data class SelectedItem(val key: String, val item: SendItem, val uri: Uri?) {
    val name: String get() = item.name
    val size: Long get() = item.size
    val mimeType: String? get() = item.mimeType
    val isText: Boolean get() = item is TextSendItem
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object PickFiles : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.graph
    val engine = graph.engine
    val settings = graph.settings
    val identity = graph.identity
    val nearbySupported: Boolean = graph.nearby != null
    val nearbyAvailable: StateFlow<Boolean> = graph.nearby?.available ?: MutableStateFlow(false)
    val lanPort: StateFlow<Int?> = graph.lan.port

    private val backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val screen: StateFlow<Screen> = backStack.map { it.last() }.stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Home)
    val canGoBack: StateFlow<Boolean> = backStack.map { it.size > 1 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _selection = MutableStateFlow<List<SelectedItem>>(emptyList())
    val selection: StateFlow<List<SelectedItem>> = _selection.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _network = MutableStateFlow(NetworkStatus(null, null))
    val network: StateFlow<NetworkStatus> = _network.asStateFlow()

    /** False until the first network probe has finished, so screens can show a loading state instead of a false error. */
    private val _networkChecked = MutableStateFlow(false)
    val networkChecked: StateFlow<Boolean> = _networkChecked.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val peers: StateFlow<List<Peer>> = engine.peers
    val transfers: StateFlow<List<TransferInfo>> = engine.transfers
    val history: StateFlow<List<HistoryEntry>> = graph.history.entries
    val knownDevices: StateFlow<List<KnownDevice>> = graph.knownDevices.devices

    /** Known + currently discoverable devices, with real online state and last-seen times. */
    val devices: StateFlow<List<DeviceEntry>> = combine(knownDevices, peers) { known, nearby ->
        DeviceDirectory.merge(known, nearby)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingRequests: StateFlow<List<TransferInfo>> = transfers
        .map { list -> list.filter { it.phase == TransferPhase.AWAITING_DECISION } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pendingLink: ConnectLink? = null
    private val sentItems = mutableMapOf<String, Pair<Peer?, List<SelectedItem>>>()
    private val autoOpened = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            while (isActive) {
                val (status, addresses) = withContext(Dispatchers.IO) { graph.network.describe() to graph.network.shareableAddresses() }
                _network.value = status
                _addresses.value = addresses
                _networkChecked.value = true
                delay(3_000)
            }
        }
        // Jump to the progress screen when a trusted device starts sending without a prompt.
        viewModelScope.launch {
            transfers.collect { list ->
                val current = screen.value
                list.filter { it.direction == Direction.RECEIVE && it.phase == TransferPhase.TRANSFERRING && it.id !in autoOpened }
                    .forEach { transfer ->
                        autoOpened += transfer.id
                        val staysPut = current is Screen.Transfer || current == Screen.Receive || current == Screen.History ||
                            (current is Screen.Chat && current.id == transfer.peer?.id)
                        if (!staysPut) navigate(Screen.Transfer(transfer.id))
                    }
            }
        }
    }

    init {
        // Active discovery (queries + subnet sweep) only runs while scanning, so keep it on for every screen that
        // lists devices, but only while the app is in the foreground.
        viewModelScope.launch {
            combine(screen, graph.foreground) { current, foreground -> foreground && shouldScan(current) }
                .collect { engine.setScanning(it) }
        }
    }

    private fun shouldScan(screen: Screen): Boolean = when (screen) {
        Screen.Home, Screen.Devices, is Screen.Device, is Screen.Chat, is Screen.Send, Screen.Receive -> true
        else -> false
    }

    // Navigation ---------------------------------------------------------------------------------

    fun navigate(target: Screen) {
        backStack.update { stack -> if (stack.last() == target) stack else stack + target }
    }

    fun back() {
        backStack.update { stack -> if (stack.size > 1) stack.dropLast(1) else stack }
    }

    fun home() {
        backStack.value = listOf(Screen.Home)
    }

    /** Switches tab on the one back stack: Home is the root, the other tabs sit directly on top of it. */
    fun selectTab(tab: Screen) {
        backStack.value = if (tab == Screen.Home) listOf(Screen.Home) else listOf(Screen.Home, tab)
    }

    fun openDevice(id: String) = navigate(Screen.Device(id))

    fun openChat(id: String) = navigate(Screen.Chat(id))

    fun deviceEntry(id: String): DeviceEntry? = devices.value.firstOrNull { it.id == id }

    fun chatFor(id: String): StateFlow<List<ChatMessage>> =
        combine(history, transfers, devices) { entries, live, list ->
            ChatTimeline.build(id, list.firstOrNull { it.id == id }?.name, entries, live)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Discovery ----------------------------------------------------------------------------------

    /** Restarts discovery and re-announces this device. The spinner shows for as long as the sweep runs. */
    fun refreshDevices() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            engine.setScanning(false)
            engine.setScanning(true)
            engine.refreshPresence()
            delay(2_500)
            engine.setScanning(graph.foreground.value && shouldScan(screen.value))
            _refreshing.value = false
        }
    }

    // Selection ----------------------------------------------------------------------------------

    fun addUris(uris: List<Uri>, openSend: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = resolve(uris)
            if (resolved.isEmpty()) return@launch
            _selection.update { current -> (current + resolved).distinctBy { it.key } }
            afterSelection(openSend)
        }
    }

    private suspend fun resolve(uris: List<Uri>): List<SelectedItem> {
        val context = getApplication<Application>()
        _resolving.value = true
        val resolved = withContext(Dispatchers.IO) {
            uris.distinct().mapNotNull { uri -> UriSendItem.resolve(context, uri)?.let { SelectedItem(uri.toString(), it, uri) } }
        }
        _resolving.value = false
        if (resolved.isEmpty()) message("Couldn't read the selected files")
        else if (resolved.size < uris.distinct().size) message("Some files couldn't be read and were skipped")
        return resolved
    }

    fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _selection.update { it + SelectedItem("text:${System.nanoTime()}", TextSendItem(trimmed), null) }
        afterSelection(true)
    }

    fun removeItem(key: String) {
        _selection.update { list -> list.filterNot { it.key == key } }
    }

    fun clearSelection() {
        _selection.value = emptyList()
    }

    private fun afterSelection(openSend: Boolean) {
        val link = pendingLink
        if (link != null) {
            pendingLink = null
            sendToLink(link)
        } else if (openSend && screen.value !is Screen.Send) {
            navigate(Screen.Send())
        }
    }

    // Sending ------------------------------------------------------------------------------------

    /** Validates the target and starts the real send for the current selection; opens the progress screen. */
    fun sendSelectionTo(deviceId: String) {
        val items = _selection.value
        if (items.isEmpty()) {
            message("Pick something to send first")
            return
        }
        val peer = onlinePeer(deviceId) ?: return
        val id = engine.send(peer, items.map { it.item })
        sentItems[id] = peer to items
        navigate(Screen.Transfer(id))
    }

    /** Sends picked files straight to [deviceId] and keeps the user in that device's conversation. */
    fun sendUrisTo(deviceId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val peer = onlinePeer(deviceId) ?: return
        viewModelScope.launch {
            val items = resolve(uris)
            if (items.isEmpty()) return@launch
            startDirect(peer, items)
        }
    }

    fun sendTextTo(deviceId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val peer = onlinePeer(deviceId) ?: return
        startDirect(peer, listOf(SelectedItem("text:${System.nanoTime()}", TextSendItem(trimmed), null)))
    }

    private fun startDirect(peer: Peer, items: List<SelectedItem>) {
        val latest = peers.value.firstOrNull { it.id == peer.id } ?: peer
        val id = engine.send(latest, items.map { it.item })
        sentItems[id] = latest to items
        if (screen.value != Screen.Chat(peer.id)) navigate(Screen.Chat(peer.id))
    }

    private fun onlinePeer(deviceId: String): Peer? {
        val peer = peers.value.firstOrNull { it.id == deviceId }
        if (peer == null) {
            val name = deviceEntry(deviceId)?.name ?: "That device"
            message("$name is offline. Make sure it's nearby with ShareX open, then refresh.")
        }
        return peer
    }

    fun onQrScanned(raw: String) {
        val link = ConnectLink.parse(raw)
        if (link == null) {
            message("That isn't a ShareX QR code")
            return
        }
        if (link.id == identity.deviceId) {
            message("That's this device's own code")
            return
        }
        if (_selection.value.isEmpty()) {
            pendingLink = link
            message("Choose what to send to ${link.name}")
            _events.tryEmit(UiEvent.PickFiles)
        } else {
            sendToLink(link)
        }
    }

    private fun sendToLink(link: ConnectLink) {
        val items = _selection.value
        if (items.isEmpty()) return
        val id = engine.send(link, items.map { it.item })
        sentItems[id] = null to items
        navigate(Screen.Transfer(id))
    }

    fun sendToAddress(address: String) {
        val items = _selection.value
        if (items.isEmpty() || address.isBlank()) return
        val id = engine.sendToAddress(address.trim(), items.map { it.item })
        sentItems[id] = null to items
        navigate(Screen.Transfer(id))
    }

    fun canRetry(transferId: String): Boolean = sentItems[transferId]?.first != null

    /** Re-sends a failed transfer. From the progress screen the screen is swapped; from a conversation nothing navigates. */
    fun retry(transferId: String) {
        val (peer, items) = sentItems[transferId] ?: return
        if (peer == null) return
        val latest = peers.value.firstOrNull { it.id == peer.id }
        if (latest == null) {
            message("${peer.device.name} is offline. Make sure it's nearby, then try again.")
            return
        }
        val id = engine.send(latest, items.map { it.item })
        sentItems[id] = latest to items
        if (screen.value is Screen.Transfer) backStack.update { stack -> stack.dropLast(1) + Screen.Transfer(id) }
    }

    fun finishTransfer(transfer: TransferInfo?) {
        if (transfer != null) {
            if (transfer.direction == Direction.SEND && transfer.phase == TransferPhase.COMPLETED) clearSelection()
            if (transfer.phase.isFinished) engine.dismiss(transfer.id)
        }
        val stack = backStack.value
        backStack.value = if (transfer?.direction == Direction.SEND && transfer.phase != TransferPhase.COMPLETED) {
            stack.dropLast(1).ifEmpty { listOf(Screen.Home) }
        } else {
            listOf(Screen.Home)
        }
    }

    // Receiving ----------------------------------------------------------------------------------

    fun respond(transferId: String, decision: Decision) {
        engine.respond(transferId, decision)
        graph.notifications.cancelIncomingRequest(transferId)
        if (decision != Decision.DECLINE) {
            autoOpened += transferId
            if (screen.value != Screen.Receive) navigate(Screen.Transfer(transferId))
        }
    }

    fun openTransfer(transferId: String) {
        if (engine.transfer(transferId) != null) navigate(Screen.Transfer(transferId)) else navigate(Screen.History)
    }

    fun cancel(transferId: String) = engine.cancel(transferId)

    fun dismissTransfer(transferId: String) = engine.dismiss(transferId)

    // Settings & devices ---------------------------------------------------------------------------

    fun setVisible(visible: Boolean) = settings.setVisible(visible)

    fun setTrusted(id: String, trusted: Boolean) = graph.knownDevices.setTrusted(id, trusted)

    fun forgetDevice(id: String) = graph.knownDevices.remove(id)

    fun clearHistory() = graph.history.clear()

    fun removeHistory(id: String) = graph.history.remove(id)

    fun onPermissionsChanged() = graph.onPermissionsChanged()

    fun message(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }

    fun connectLink(): ConnectLink? {
        val port = lanPort.value ?: return null
        val hosts = addresses.value.take(4)
        if (hosts.isEmpty()) return null
        val self = engine.self
        return ConnectLink(self.id, self.name, self.type, hosts, port)
    }
}
