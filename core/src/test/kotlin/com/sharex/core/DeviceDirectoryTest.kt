package com.sharex.core

import com.sharex.core.engine.DeviceDirectory
import com.sharex.core.engine.DeviceFilter
import com.sharex.core.engine.Peer
import com.sharex.core.engine.Route
import com.sharex.core.store.KnownDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceDirectoryTest {
    private fun known(id: String, name: String, trusted: Boolean = false, lastSeen: Long = 100) =
        KnownDevice(id, name, "LAPTOP", "ab12", trusted, listOf("10.0.0.2"), lastSeen)

    private fun peer(id: String, name: String, lastSeen: Long = 500) =
        Peer(DeviceInfo(id, name, DeviceType.PHONE), listOf(Route("lan", "Wi-Fi", "10.0.0.9:1", 0)), false, lastSeen)

    @Test
    fun knownDeviceBecomesOnlineWhenDiscovered() {
        val merged = DeviceDirectory.merge(listOf(known("a", "Laptop")), listOf(peer("a", "Laptop", 900)))
        val entry = merged.single()
        assertTrue(entry.online)
        assertTrue(entry.known)
        assertEquals(900, entry.lastSeen)
        assertEquals(listOf("Wi-Fi"), entry.connections)
    }

    @Test
    fun knownDeviceWithoutPeerIsOfflineAndKeepsLastSeen() {
        val entry = DeviceDirectory.merge(listOf(known("a", "Laptop", lastSeen = 42)), emptyList()).single()
        assertFalse(entry.online)
        assertEquals(42, entry.lastSeen)
        assertTrue(entry.connections.isEmpty())
    }

    @Test
    fun unknownPeerIsNearbyOnly() {
        val merged = DeviceDirectory.merge(listOf(known("a", "Laptop")), listOf(peer("b", "Phone")))
        assertEquals(listOf("a"), DeviceDirectory.mine(merged).map { it.id })
        assertEquals(listOf("b"), DeviceDirectory.nearby(merged).map { it.id })
    }

    @Test
    fun onlineDevicesSortFirst() {
        val merged = DeviceDirectory.merge(listOf(known("a", "Alpha", lastSeen = 999), known("b", "Beta", lastSeen = 1)), listOf(peer("b", "Beta", 5)))
        assertEquals(listOf("b", "a"), merged.map { it.id })
    }

    @Test
    fun filterAndSearch() {
        val merged = DeviceDirectory.merge(listOf(known("a", "Office-PC"), known("b", "Home-PC")), listOf(peer("a", "Office-PC")))
        assertEquals(listOf("a"), DeviceDirectory.apply(merged, DeviceFilter.ONLINE, "").map { it.id })
        assertEquals(listOf("b"), DeviceDirectory.apply(merged, DeviceFilter.OFFLINE, "").map { it.id })
        assertEquals(listOf("b"), DeviceDirectory.apply(merged, DeviceFilter.ALL, "  home ").map { it.id })
        assertTrue(DeviceDirectory.apply(merged, DeviceFilter.ONLINE, "home").isEmpty())
    }
}
