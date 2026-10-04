package com.sharex.core.engine

import com.sharex.core.DeviceType
import com.sharex.core.store.KnownDevice

/**
 * One row in the device lists: a device we have exchanged keys with ([known]), a device that is
 * currently discoverable ([online]), or both.
 */
data class DeviceEntry(
    val id: String,
    val name: String,
    val type: DeviceType,
    val online: Boolean,
    val trusted: Boolean,
    val known: Boolean,
    /** Epoch millis of the last time the device was seen (discovered or completed a handshake); 0 when unknown. */
    val lastSeen: Long,
    /** Transport labels currently usable to reach the device (empty while offline). */
    val connections: List<String>,
    /** Hex fingerprint of the device's verified public key, when a handshake has happened. */
    val fingerprint: String?,
)

enum class DeviceFilter { ALL, ONLINE, OFFLINE }

/** Pure functions that turn engine/store state into the lists the UI shows. */
object DeviceDirectory {
    fun merge(known: List<KnownDevice>, peers: List<Peer>): List<DeviceEntry> {
        val peersById = peers.associateBy { it.id }
        val knownIds = known.mapTo(HashSet()) { it.id }
        val entries = ArrayList<DeviceEntry>(known.size + peers.size)
        for (device in known) {
            val peer = peersById[device.id]
            entries += DeviceEntry(
                id = device.id,
                name = peer?.device?.name ?: device.name,
                type = peer?.device?.type ?: device.deviceType,
                online = peer != null,
                trusted = device.trusted,
                known = true,
                lastSeen = maxOf(device.lastSeen, peer?.lastSeen ?: 0L),
                connections = peer?.transportLabels.orEmpty(),
                fingerprint = device.fingerprint,
            )
        }
        for (peer in peers) {
            if (peer.id in knownIds) continue
            entries += DeviceEntry(
                id = peer.id,
                name = peer.device.name,
                type = peer.device.type,
                online = true,
                trusted = false,
                known = false,
                lastSeen = peer.lastSeen,
                connections = peer.transportLabels,
                fingerprint = null,
            )
        }
        return entries.sortedWith(
            compareByDescending<DeviceEntry> { it.online }
                .thenByDescending { it.lastSeen }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id },
        )
    }

    /** Devices the user has paired with before. */
    fun mine(entries: List<DeviceEntry>): List<DeviceEntry> = entries.filter { it.known }

    /** Devices that are discoverable right now but have never completed a handshake with us. */
    fun nearby(entries: List<DeviceEntry>): List<DeviceEntry> = entries.filter { !it.known && it.online }

    /** Most recently active first, regardless of online state (used by Home → Recent). */
    fun recent(entries: List<DeviceEntry>): List<DeviceEntry> =
        entries.sortedWith(compareByDescending<DeviceEntry> { it.lastSeen }.thenBy { it.name.lowercase() }.thenBy { it.id })

    fun apply(entries: List<DeviceEntry>, filter: DeviceFilter, query: String): List<DeviceEntry> {
        val needle = query.trim().lowercase()
        return entries.filter { entry ->
            val passesFilter = when (filter) {
                DeviceFilter.ALL -> true
                DeviceFilter.ONLINE -> entry.online
                DeviceFilter.OFFLINE -> !entry.online
            }
            passesFilter && (needle.isEmpty() || entry.name.lowercase().contains(needle))
        }
    }
}
