package com.sharex.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sharex.app.data.AndroidReceiveStorage
import com.sharex.app.data.Settings
import com.sharex.app.net.AndroidNetworkPlatform
import com.sharex.app.net.NearbyTransport
import com.sharex.app.service.Notifications
import com.sharex.app.service.ShareXService
import com.sharex.core.DeviceType
import com.sharex.core.crypto.DeviceIdentity
import com.sharex.core.engine.ShareXEngine
import com.sharex.core.lan.LanTransport
import com.sharex.core.store.HistoryStore
import com.sharex.core.store.KnownDevices
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.ReceiveStorage
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Process-wide singletons. Created once in [ShareXApp]. */
class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = Settings(context)
    val identity: DeviceIdentity = DeviceIdentity.loadOrCreate(File(context.filesDir, "identity.bin"))
    val knownDevices = KnownDevices(File(context.filesDir, "devices.json"))
    val history = HistoryStore(File(context.filesDir, "history.json"))
    val network = AndroidNetworkPlatform(context)
    val notifications = Notifications(context)
    val lan = LanTransport(network, probeHosts = { knownDevices.recentHosts() })
    val nearby: NearbyTransport? = if (NearbyTransport.isSupported(context)) NearbyTransport(context) else null

    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val storage = AndroidReceiveStorage(context)

    val engine = ShareXEngine(identity, knownDevices, object : ShareXEngine.Platform {
        override fun deviceName(): String = settings.deviceName.value
        override fun deviceType(): DeviceType =
            if (context.resources.configuration.smallestScreenWidthDp >= 600) DeviceType.TABLET else DeviceType.PHONE
        override val storage: ReceiveStorage get() = this@AppGraph.storage
        override fun autoAcceptTrusted(): Boolean = settings.autoAcceptTrusted.value

        override fun onIncomingRequest(transfer: TransferInfo) {
            if (!_foreground.value) notifications.showIncomingRequest(transfer)
        }

        override fun onTransferFinished(transfer: TransferInfo) {
            val declinedIncoming = transfer.direction == Direction.RECEIVE && transfer.phase == TransferPhase.REJECTED
            if (!declinedIncoming) history.add(transfer)
            notifications.cancelIncomingRequest(transfer.id)
            if (!_foreground.value && !declinedIncoming) notifications.showFinished(transfer)
        }

        override fun log(message: String, error: Throwable?) {
            Log.w("ShareX", message, error)
        }
    })

    init {
        engine.addTransport(lan)
        nearby?.let(engine::addTransport)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                _foreground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                _foreground.value = false
            }
        })

        // Visibility: on while the app is open, and in the background too if the user allows it.
        scope.launch {
            combine(settings.visible, settings.backgroundVisible, foreground, settings.onboarded) { visible, background, fg, onboarded ->
                onboarded && visible && (fg || background)
            }.distinctUntilChanged().collect { engine.setVisible(it) }
        }

        // Keep a foreground service alive whenever we're visible or moving bytes.
        scope.launch {
            combine(engine.visible, engine.transfers.map { list -> list.any { it.isActive } }) { visible, active ->
                visible || active
            }.distinctUntilChanged().collect { needed ->
                if (needed) ShareXService.start(context) else ShareXService.stop(context)
            }
        }

        scope.launch {
            settings.deviceName.drop(1).collect {
                engine.refreshPresence()
                nearby?.restartAdvertising()
            }
        }
    }

    fun onPermissionsChanged() {
        nearby?.refresh()
    }
}
