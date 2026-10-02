package com.sharex.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sharex.app.graph
import com.sharex.core.transfer.Decision

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        val transferId = intent.getStringExtra(EXTRA_ID)
        when (intent.action) {
            ACTION_ACCEPT -> transferId?.let {
                graph.engine.respond(it, Decision.ACCEPT)
                graph.notifications.cancelIncomingRequest(it)
            }
            ACTION_DECLINE -> transferId?.let {
                graph.engine.respond(it, Decision.DECLINE)
                graph.notifications.cancelIncomingRequest(it)
            }
            ACTION_CANCEL -> transferId?.let(graph.engine::cancel)
            ACTION_HIDE -> graph.settings.setVisible(false)
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.sharex.app.ACCEPT"
        const val ACTION_DECLINE = "com.sharex.app.DECLINE"
        const val ACTION_CANCEL = "com.sharex.app.CANCEL"
        const val ACTION_HIDE = "com.sharex.app.HIDE"
        private const val EXTRA_ID = "transfer_id"

        fun intent(context: Context, action: String, transferId: String?): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, transferId)
            return PendingIntent.getBroadcast(
                context,
                (action + transferId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
