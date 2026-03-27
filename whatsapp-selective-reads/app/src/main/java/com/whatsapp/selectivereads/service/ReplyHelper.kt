package com.whatsapp.selectivereads.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.MessageStatus

object ReplyHelper {

    fun sendReply(
        service: NotificationListenerService,
        notificationKey: String,
        conversationId: String,
        replyText: String,
        remoteInputResultKey: String?,
        replyActionIndex: Int
    ): Boolean {
        return try {
            val sbn = service.activeNotifications.find { it.key == notificationKey }
                ?: return false

            val notification = sbn.notification
            val actions = notification.actions ?: return false

            if (replyActionIndex < 0 || replyActionIndex >= actions.size) return false

            val action = actions[replyActionIndex]
            val remoteInputs = action.remoteInputs ?: return false

            val remoteInput = if (remoteInputResultKey != null) {
                remoteInputs.find { it.resultKey == remoteInputResultKey }
            } else {
                remoteInputs.firstOrNull()
            } ?: return false

            val results = Bundle()
            results.putCharSequence(remoteInput.resultKey, replyText)

            val fillInIntent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)

            action.actionIntent.send(service, 0, fillInIntent)

            kotlinx.coroutines.runBlocking {
                val db = WhatsAppSelectiveReadsApp.instance.database
                db.conversationDao().updateStatus(conversationId, MessageStatus.REPLIED)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
