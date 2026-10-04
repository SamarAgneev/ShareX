package com.sharex.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Column
import com.sharex.app.ui.components.ShareXBottomBar
import com.sharex.app.ui.components.Tab
import com.sharex.app.ui.screens.ChatActions
import com.sharex.app.ui.screens.ChatScreen
import com.sharex.app.ui.screens.DeviceActions
import com.sharex.app.ui.screens.DeviceDetailActions
import com.sharex.app.ui.screens.DeviceDetailsScreen
import com.sharex.app.ui.screens.DevicesScreen
import com.sharex.app.ui.screens.HistoryScreen
import com.sharex.app.ui.screens.HomeActions
import com.sharex.app.ui.screens.HomeScreen
import com.sharex.app.ui.screens.IncomingRequestDialog
import com.sharex.app.ui.screens.PickKind
import com.sharex.app.ui.screens.ProfileActions
import com.sharex.app.ui.screens.ProfileScreen
import com.sharex.app.ui.screens.ProfileState
import com.sharex.app.ui.screens.ReceiveActions
import com.sharex.app.ui.screens.ReceiveQrScreen
import com.sharex.app.ui.screens.ReceiveScreen
import com.sharex.app.ui.screens.SendScreen
import com.sharex.app.ui.screens.TextComposeDialog
import com.sharex.app.ui.screens.TransferScreen
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.TransferPhase
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.sharex.app.ui.screens.OnboardingScreen
import com.sharex.app.ui.theme.ShareX
import com.sharex.app.ui.theme.ShareXTheme

@Composable
fun ShareXRoot(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    ShareXTheme(theme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ShareX.colors.background),
        ) {
            val onboarded by vm.settings.onboarded.collectAsStateWithLifecycle()
            if (!onboarded) {
                val name by vm.settings.deviceName.collectAsStateWithLifecycle()
                OnboardingScreen(initialName = name) { chosen ->
                    vm.settings.setDeviceName(chosen)
                    vm.settings.setOnboarded(true)
                    onRequestPermissions()
                }
            } else {
                MainContent(vm, onRequestPermissions)
            }
        }
    }
}

@Composable
private fun MainContent(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // Null: composing for the Send screen's selection. Otherwise: send straight to that device.
    var textTarget by remember { mutableStateOf<String?>(null) }
    var composingText by remember { mutableStateOf(false) }
    var pickTarget by remember { mutableStateOf<String?>(null) }

    val screen by vm.screen.collectAsStateWithLifecycle()
    val deviceName by vm.settings.deviceName.collectAsStateWithLifecycle()
    val visibleSetting by vm.settings.visible.collectAsStateWithLifecycle()
    val backgroundVisible by vm.settings.backgroundVisible.collectAsStateWithLifecycle()
    val autoAccept by vm.settings.autoAcceptTrusted.collectAsStateWithLifecycle()
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val networkChecked by vm.networkChecked.collectAsStateWithLifecycle()
    val nearbyAvailable by vm.nearbyAvailable.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val requests by vm.pendingRequests.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val lanPort by vm.lanPort.collectAsStateWithLifecycle()
    val selfType = vm.engine.self.type

    val onPicked: (List<Uri>) -> Unit = { uris ->
        val target = pickTarget
        pickTarget = null
        if (target != null) vm.sendUrisTo(target, uris) else vm.addUris(uris)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onPicked)
    val appPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onPicked)
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), onPicked)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), onPicked)
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), onPicked)

    fun pick(kind: PickKind, target: String? = null) {
        pickTarget = target
        when (kind) {
            PickKind.FILES -> filePicker.launch(arrayOf("*/*"))
            PickKind.APPS -> appPicker.launch(arrayOf("application/vnd.android.package-archive"))
            PickKind.PHOTOS -> imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            PickKind.VIDEOS -> videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }
    fun pickMedia(target: String?) {
        pickTarget = target
        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    // The Google code scanner brings its own camera UI and permission handling, so no CAMERA permission is needed.
    val scanQr: () -> Unit = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        val activity = context as? Activity
        val scanner = if (activity != null) GmsBarcodeScanning.getClient(activity, options) else GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(vm::onQrScanned) }
            .addOnFailureListener { vm.message("QR scanner unavailable: ${it.message}") }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
                UiEvent.PickFiles -> pick(PickKind.FILES)
            }
        }
    }

    BackHandler(enabled = screen != Screen.Home) { vm.back() }

    val deviceActions = DeviceActions(
        onOpenChat = vm::openChat,
        onOpenDetails = vm::openDevice,
        onSendFiles = { id -> pick(PickKind.FILES, id) },
        onTrust = vm::setTrusted,
        onRemove = vm::forgetDevice,
    )

    val tab = when (screen) {
        Screen.Home -> Tab.HOME
        Screen.Devices -> Tab.DEVICES
        Screen.Profile -> Tab.PROFILE
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { (fadeIn() + slideInHorizontally { it / 12 }) togetherWith fadeOut() },
            label = "screens",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { current ->
            when (current) {
                Screen.Home -> HomeScreen(
                    deviceName = deviceName,
                    deviceType = selfType,
                    visible = visibleSetting,
                    network = network,
                    networkChecked = networkChecked,
                    nearbySupported = vm.nearbySupported,
                    nearbyAvailable = nearbyAvailable,
                    refreshing = refreshing,
                    devices = devices,
                    activeTransfers = transfers.filter { it.isActive && it.phase != TransferPhase.AWAITING_DECISION },
                    actions = HomeActions(
                        onSend = { vm.navigate(Screen.Send()) },
                        onReceive = { vm.navigate(Screen.Receive) },
                        onScanQr = scanQr,
                        onShowQr = { vm.navigate(Screen.ReceiveQr) },
                        onToggleVisible = vm::setVisible,
                        onRefresh = vm::refreshDevices,
                        onOpenHistory = { vm.navigate(Screen.History) },
                        onOpenTransfer = { vm.navigate(Screen.Transfer(it)) },
                        onGrantNearby = onRequestPermissions,
                        device = deviceActions,
                    ),
                )
                Screen.Devices -> DevicesScreen(
                    devices = devices,
                    refreshing = refreshing,
                    network = network,
                    networkChecked = networkChecked,
                    nearbyAvailable = nearbyAvailable,
                    onScanQr = scanQr,
                    onRefresh = vm::refreshDevices,
                    deviceActions = deviceActions,
                )
                Screen.Profile -> ProfileScreen(
                    state = ProfileState(
                        deviceName = deviceName,
                        deviceType = selfType,
                        fingerprint = vm.identity.displayFingerprint,
                        visible = visibleSetting,
                        backgroundVisible = backgroundVisible,
                        autoAcceptTrusted = autoAccept,
                        theme = theme,
                        nearbySupported = vm.nearbySupported,
                        nearbyAvailable = nearbyAvailable,
                        freeStorageBytes = freeStorageBytes(),
                    ),
                    actions = ProfileActions(
                        onRename = vm.settings::setDeviceName,
                        onVisible = vm::setVisible,
                        onBackgroundVisible = vm.settings::setBackgroundVisible,
                        onAutoAccept = vm.settings::setAutoAcceptTrusted,
                        onTheme = vm.settings::setTheme,
                        onGrantNearby = onRequestPermissions,
                        onCopyFingerprint = { copyToClipboard(context, vm.identity.displayFingerprint) },
                        onOpenReceived = { openDownloads(context) },
                    ),
                )
                is Screen.Device -> DeviceDetailsScreen(
                    entry = devices.firstOrNull { it.id == current.id },
                    refreshing = refreshing,
                    actions = DeviceDetailActions(
                        onBack = vm::back,
                        onSendFiles = { pick(PickKind.FILES, current.id) },
                        onSendMedia = { pickMedia(current.id) },
                        onSendText = { textTarget = current.id; composingText = true },
                        onHistory = { vm.openChat(current.id) },
                        onTrust = { vm.setTrusted(current.id, it) },
                        onReconnect = vm::refreshDevices,
                        onRemove = {
                            vm.forgetDevice(current.id)
                            vm.back()
                        },
                    ),
                )
                is Screen.Chat -> {
                    val messages by remember(current.id) { vm.chatFor(current.id) }.collectAsStateWithLifecycle()
                    ChatScreen(
                        entry = devices.firstOrNull { it.id == current.id },
                        messages = messages,
                        actions = ChatActions(
                            onBack = vm::back,
                            onOpenDetails = { vm.openDevice(current.id) },
                            onAttach = { pick(PickKind.FILES, current.id) },
                            onGallery = { pickMedia(current.id) },
                            onSendText = { vm.sendTextTo(current.id, it) },
                            onCancel = vm::cancel,
                            onRetry = vm::retry,
                            canRetry = vm::canRetry,
                            onRefresh = vm::refreshDevices,
                        ),
                    )
                }
                is Screen.Send -> SendScreen(
                    targetId = current.targetId,
                    devices = devices.filter { it.online || it.id == current.targetId },
                    selection = selection,
                    resolving = resolving,
                    refreshing = refreshing,
                    onBack = vm::back,
                    onPick = { kind -> pick(kind) },
                    onCompose = { textTarget = null; composingText = true },
                    onRemove = vm::removeItem,
                    onSendToDevice = vm::sendSelectionTo,
                    onScanQr = scanQr,
                    onSendToAddress = vm::sendToAddress,
                    onRefresh = vm::refreshDevices,
                )
                Screen.Receive -> ReceiveScreen(
                    deviceName = deviceName,
                    deviceType = selfType,
                    visible = visibleSetting,
                    network = network,
                    networkChecked = networkChecked,
                    nearbyAvailable = nearbyAvailable,
                    transfers = transfers.filter { it.direction == Direction.RECEIVE && it.phase != TransferPhase.AWAITING_DECISION },
                    actions = ReceiveActions(
                        onBack = vm::back,
                        onMakeVisible = { vm.setVisible(true) },
                        onShowQr = { vm.navigate(Screen.ReceiveQr) },
                        onOpenTransfer = { vm.navigate(Screen.Transfer(it)) },
                        onCancel = vm::cancel,
                        onDismiss = vm::dismissTransfer,
                        onRefresh = vm::refreshDevices,
                    ),
                )
                is Screen.Transfer -> {
                    val transfer = transfers.firstOrNull { it.id == current.id }
                    TransferScreen(
                        transfer = transfer,
                        selfType = selfType,
                        canRetry = vm.canRetry(current.id),
                        onClose = { vm.finishTransfer(transfer) },
                        onCancel = { vm.cancel(current.id) },
                        onRetry = { vm.retry(current.id) },
                    )
                }
                Screen.ReceiveQr -> ReceiveQrScreen(
                    link = if (lanPort != null && addresses.isNotEmpty()) vm.connectLink() else null,
                    visible = visibleSetting,
                    fingerprint = vm.identity.displayFingerprint,
                    onBack = vm::back,
                    onMakeVisible = { vm.setVisible(true) },
                )
                Screen.History -> HistoryScreen(
                    entries = history,
                    knownDeviceIds = devices.mapTo(HashSet()) { it.id },
                    onBack = vm::back,
                    onClear = vm::clearHistory,
                    onRemove = vm::removeHistory,
                    onOpenConversation = vm::openChat,
                )
            }
        }
        if (tab != null) ShareXBottomBar(tab, onSelect = { selected ->
            vm.selectTab(
                when (selected) {
                    Tab.HOME -> Screen.Home
                    Tab.DEVICES -> Screen.Devices
                    Tab.PROFILE -> Screen.Profile
                },
            )
        })
    }

    requests.firstOrNull()?.let { request ->
        IncomingRequestDialog(request) { decision -> vm.respond(request.id, decision) }
    }

    if (composingText) {
        TextComposeDialog(onDismiss = { composingText = false }, onSend = {
            composingText = false
            val target = textTarget
            textTarget = null
            if (target != null) vm.sendTextTo(target, it) else vm.addText(it)
        })
    }

    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = if (tab != null) 80.dp else 0.dp, start = 16.dp, end = 16.dp, top = 16.dp), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar) { data ->
            Snackbar(
                data,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                containerColor = ShareX.colors.surfaceHigh,
                contentColor = ShareX.colors.text,
                actionColor = ShareX.colors.accent,
            )
        }
    }
}
