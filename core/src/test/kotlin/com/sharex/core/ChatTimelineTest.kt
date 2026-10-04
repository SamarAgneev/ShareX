package com.sharex.core

import com.sharex.core.store.HistoryEntry
import com.sharex.core.store.HistoryItem
import com.sharex.core.transfer.ChatState
import com.sharex.core.transfer.ChatTimeline
import com.sharex.core.transfer.Direction
import com.sharex.core.transfer.TransferInfo
import com.sharex.core.transfer.TransferItemInfo
import com.sharex.core.transfer.TransferPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTimelineTest {
    private val laptop = DeviceInfo("dev-1", "Laptop", DeviceType.LAPTOP)

    private fun entry(id: String, time: Long, peerId: String?, name: String = "Laptop", status: String = "COMPLETED", send: Boolean = true) =
        HistoryEntry(
            id = id,
            direction = if (send) "SEND" else "RECEIVE",
            peerName = name,
            peerType = "LAPTOP",
            items = listOf(HistoryItem("a.pdf", 10, "application/pdf", null, null)),
            totalBytes = 10,
            status = status,
            time = time,
            peerId = peerId,
        )

    private fun live(id: String, phase: TransferPhase, done: Long = 5, started: Long = 50) = TransferInfo(
        id = id,
        direction = Direction.SEND,
        peer = laptop,
        items = listOf(TransferItemInfo(0, "movie.mkv", 100, "video/x-matroska", false)),
        totalBytes = 100,
        bytesDone = done,
        bytesPerSecond = 5,
        phase = phase,
        startedAt = started,
    )

    @Test
    fun onlyMessagesForTheDeviceAreIncludedAndOrderedByTime() {
        val history = listOf(entry("2", 20, "dev-1"), entry("1", 10, "dev-1"), entry("x", 15, "other", name = "Phone"))
        assertEquals(listOf("1", "2"), ChatTimeline.build("dev-1", "Laptop", history, emptyList()).map { it.id })
    }

    @Test
    fun legacyEntriesWithoutPeerIdMatchByName() {
        val history = listOf(entry("1", 10, null, name = "Laptop"), entry("2", 11, null, name = "Phone"))
        assertEquals(listOf("1"), ChatTimeline.build("dev-1", "Laptop", history, emptyList()).map { it.id })
    }

    @Test
    fun liveTransferShowsProgress() {
        val message = ChatTimeline.build("dev-1", "Laptop", emptyList(), listOf(live("t", TransferPhase.TRANSFERRING))).single()
        assertEquals(ChatState.IN_PROGRESS, message.state)
        assertEquals(0.05f, message.fraction)
        assertTrue(message.live)
        assertTrue(message.outgoing)
    }

    @Test
    fun finishedTransferPrefersPersistedCopyButActiveOneWins() {
        val history = listOf(entry("t", 50, "dev-1", status = "FAILED"))
        val finished = ChatTimeline.build("dev-1", "Laptop", history, listOf(live("t", TransferPhase.COMPLETED))).single()
        assertEquals(ChatState.FAILED, finished.state)
        assertFalse(finished.live)
        val active = ChatTimeline.build("dev-1", "Laptop", history, listOf(live("t", TransferPhase.TRANSFERRING))).single()
        assertEquals(ChatState.IN_PROGRESS, active.state)
    }

    @Test
    fun rejectedAndCancelledMapToTheirOwnStates() {
        val history = listOf(entry("1", 1, "dev-1", status = "REJECTED"), entry("2", 2, "dev-1", status = "CANCELLED"), entry("3", 3, "dev-1", status = "FAILED"))
        assertEquals(
            listOf(ChatState.DECLINED, ChatState.CANCELLED, ChatState.FAILED),
            ChatTimeline.build("dev-1", "Laptop", history, emptyList()).map { it.state },
        )
    }
}
