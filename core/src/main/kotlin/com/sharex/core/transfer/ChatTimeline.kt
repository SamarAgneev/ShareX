package com.sharex.core.transfer

import com.sharex.core.store.HistoryEntry

enum class ChatState { IN_PROGRESS, COMPLETED, FAILED, DECLINED, CANCELLED }

data class ChatItem(
    val name: String,
    val size: Long,
    val mime: String?,
    val location: String?,
    val text: String?,
) {
    val isText: Boolean get() = text != null
}

/** One bubble in a device conversation: a single transfer, in either direction. */
data class ChatMessage(
    val id: String,
    val outgoing: Boolean,
    val time: Long,
    val items: List<ChatItem>,
    val totalBytes: Long,
    val state: ChatState,
    val fraction: Float,
    val bytesDone: Long,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
    val detail: String?,
    /** True while the transfer still exists in the engine, so it can be cancelled. */
    val live: Boolean,
)

/** Builds a device conversation out of the persisted history and the transfers the engine is running. */
object ChatTimeline {
    fun build(
        deviceId: String,
        deviceName: String?,
        history: List<HistoryEntry>,
        transfers: List<TransferInfo>,
    ): List<ChatMessage> {
        val messages = LinkedHashMap<String, ChatMessage>()
        for (entry in history) {
            val matches = entry.peerId == deviceId || (entry.peerId == null && deviceName != null && entry.peerName == deviceName)
            if (matches) messages[entry.id] = entry.toMessage()
        }
        for (transfer in transfers) {
            if (transfer.peer?.id != deviceId) continue
            // A finished transfer that already reached history keeps its persisted copy.
            if (transfer.phase.isFinished && messages.containsKey(transfer.id)) continue
            messages[transfer.id] = transfer.toMessage()
        }
        return messages.values.sortedWith(compareBy<ChatMessage> { it.time }.thenBy { it.id })
    }

    private fun HistoryEntry.toMessage() = ChatMessage(
        id = id,
        outgoing = isSend,
        time = time,
        items = items.map { ChatItem(it.name, it.size, it.mime, it.location, it.text) },
        totalBytes = totalBytes,
        state = when (status) {
            TransferPhase.COMPLETED.name -> ChatState.COMPLETED
            TransferPhase.REJECTED.name -> ChatState.DECLINED
            TransferPhase.CANCELLED.name -> ChatState.CANCELLED
            else -> ChatState.FAILED
        },
        fraction = if (isSuccess) 1f else 0f,
        bytesDone = if (isSuccess) totalBytes else 0L,
        bytesPerSecond = 0L,
        etaSeconds = -1L,
        detail = message,
        live = false,
    )

    private fun TransferInfo.toMessage(): ChatMessage {
        val receivedByIndex = received.associateBy { it.index }
        return ChatMessage(
            id = id,
            outgoing = direction == Direction.SEND,
            time = startedAt,
            items = items.map { item ->
                val saved = receivedByIndex[item.index]
                ChatItem(item.name, item.size, item.mimeType, saved?.location, item.text ?: saved?.text)
            },
            totalBytes = totalBytes,
            state = when (phase) {
                TransferPhase.COMPLETED -> ChatState.COMPLETED
                TransferPhase.REJECTED -> ChatState.DECLINED
                TransferPhase.CANCELLED -> ChatState.CANCELLED
                TransferPhase.FAILED -> ChatState.FAILED
                else -> ChatState.IN_PROGRESS
            },
            fraction = fraction,
            bytesDone = bytesDone,
            bytesPerSecond = bytesPerSecond,
            etaSeconds = etaSeconds,
            detail = message,
            live = true,
        )
    }
}
