package com.sharex.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sharex.app.net.NetworkStatus
import com.sharex.app.ui.components.DeviceListItem
import com.sharex.app.ui.components.FilterChips
import com.sharex.app.ui.components.GradientButton
import com.sharex.app.ui.components.QRButton
import com.sharex.app.ui.components.SearchField
import com.sharex.app.ui.components.SectionHeader
import com.sharex.app.ui.components.ShareXCard
import com.sharex.app.ui.components.Wordmark
import com.sharex.app.ui.theme.ShareX
import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceEntry
import com.sharex.core.engine.DeviceFilter

@Composable
fun DevicesScreen(
    devices: List<DeviceEntry>,
    refreshing: Boolean,
    network: NetworkStatus,
    networkChecked: Boolean,
    nearbyAvailable: Boolean,
    onScanQr: () -> Unit,
    onRefresh: () -> Unit,
    deviceActions: DeviceActions,
) {
    val colors = ShareX.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(DeviceFilter.ALL.name) }
    val filter = DeviceFilter.valueOf(filterName)
    val visibleDevices = DeviceDirectory.apply(devices, filter, query)
    val mine = DeviceDirectory.mine(visibleDevices)
    val nearby = DeviceDirectory.nearby(visibleDevices)
    val offlineNetwork = networkChecked && !network.connected && !nearbyAvailable

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Wordmark(Modifier.padding(top = 4.dp, bottom = 4.dp)) }
        item { SearchField(query, { query = it }) }
        item { FilterChips(filter, { filterName = it.name }) }

        if (offlineNetwork) {
            item {
                ErrorState(
                    title = "Not connected to a network",
                    body = "Join the same Wi-Fi as the other device, or allow nearby devices to connect phones directly.",
                    icon = Icons.Rounded.WifiOff,
                    retryLabel = "Check again",
                    onRetry = onRefresh,
                )
            }
        }

        item { SectionHeader("My Devices") }
        if (mine.isEmpty()) {
            item {
                ShareXCard(Modifier.fillMaxWidth()) {
                    Column {
                        androidx.compose.material3.Text(
                            if (devices.none { it.known }) "Devices you exchange files with appear here. Trusted devices can send without a prompt."
                            else "No devices match this filter.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        } else {
            item {
                ShareXCard(Modifier.fillMaxWidth().animateContentSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    Column {
                        mine.forEachIndexed { index, entry ->
                            DeviceListItem(
                                entry = entry,
                                onClick = { deviceActions.onOpenDetails(entry.id) },
                                menu = deviceActions.menuFor(entry),
                            )
                            if (index < mine.lastIndex) HorizontalDivider(color = colors.outline.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Nearby Devices") {
                QRButton("Scan QR", Icons.Rounded.QrCodeScanner, onScanQr)
                QRButton(if (refreshing) "Searching" else "Refresh", Icons.Rounded.Refresh, onRefresh, enabled = !refreshing)
            }
        }
        when {
            nearby.isNotEmpty() -> item {
                ShareXCard(Modifier.fillMaxWidth().animateContentSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    Column {
                        nearby.forEachIndexed { index, entry ->
                            DeviceListItem(
                                entry = entry,
                                onClick = { deviceActions.onOpenDetails(entry.id) },
                                menu = deviceActions.menuFor(entry),
                                onConnect = { deviceActions.onOpenChat(entry.id) },
                            )
                            if (index < nearby.lastIndex) HorizontalDivider(color = colors.outline.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
            refreshing && devices.none { !it.known } -> item { LoadingState("Looking for nearby devices…", "Open ShareX on the other device and keep it visible.") }
            query.isNotBlank() || filter != DeviceFilter.ALL -> item {
                EmptyState("No matching devices", "Try a different search or filter.", Icons.Rounded.Search)
            }
            else -> item {
                EmptyState(
                    "No devices found",
                    "Make sure the other device has ShareX open and is visible, then refresh. You can also scan its QR code.",
                    Icons.Rounded.Search,
                    action = { GradientButton("Refresh", onClick = onRefresh, icon = Icons.Rounded.Refresh) },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
