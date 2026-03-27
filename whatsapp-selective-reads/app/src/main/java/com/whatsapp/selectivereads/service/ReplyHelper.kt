package com.whatsapp.selectivereads.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Log
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReplyHelper {

    private const val TAG = "ReplyHelper"

    suspend fun sendReply(
        service: NotificationListenerService,
        notificationKey: String,
        conversationId: String,
        replyText: String,
        remoteInputResultKey: String?,
        replyActionIndex: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val allNotifications = service.activeNotifications
                Log.d(TAG, "Looking for notification: $notificationKey among ${allNotifications.size} active notifications")

                // Try exact key match first
                var sbn = allNotifications.find { it.key == notificationKey }

                // Fallback: find any WhatsApp notification with reply action
                if (sbn == null) {
                    Log.d(TAG, "Exact key not found, searching for any WhatsApp notification with reply action...")
                    val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
                    sbn = allNotifications.firstOrNull { sbn ->
                        sbn.packageName in whatsappPackages &&
                        sbn.notification.actions?.any { action ->
                            action.remoteInputs?.isNotEmpty() == true
                        } == true
                    }
                }

                if (sbn == null) {
                    Log.e(TAG, "No WhatsApp notification found with reply action")
                    return@withContext false
                }

                Log.d(TAG, "Found notification: ${sbn.key} (package: ${sbn.packageName})")

                val notification = sbn.notification
                val actions = notification.actions
                if (actions.isNullOrEmpty()) {
                    Log.e(TAG, "Notification has no actions")
                    return@withContext false
                }

                Log.d(TAG, "Found ${actions.size} actions")

                // Find the reply action (any action with RemoteInputs)
                val replyAction = actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
                if (replyAction == null) {
                    Log.e(TAG, "No reply action found in notification")
                    return@withContext false
                }

                val remoteInputs = replyAction.remoteInputs ?: return@withContext false
                Log.d(TAG, "Found reply action with ${remoteInputs.size} RemoteInputs")

                val remoteInput = if (remoteInputResultKey != null) {
                    remoteInputs.find { it.resultKey == remoteInputResultKey }
                } else {
                    remoteInputs.firstOrNull()
                }
                if (remoteInput == null) {
                    Log.e(TAG, "RemoteInput not found. Available: ${remoteInputs.map { it.resultKey }}")
                    return@withContext false
                }

                Log.d(TAG, "Sending reply via RemoteInput: ${remoteInput.resultKey}")

                val results = Bundle()
                results.putCharSequence(remoteInput.resultKey, replyText)

                val fillInIntent = Intent()
                RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)

                replyAction.actionIntent.send(service, 0, fillInIntent)
                Log.d(TAG, "Reply sent successfully!")

                val db = WhatsAppSelectiveReadsApp.instance.database
                db.conversationDao().updateStatus(conversationId, MessageStatus.REPLIED)

                true
            } catch (e: Exception) {
                Log.e(TAG, "sendReply failed: ${e.message}", e)
                e.printStackTrace()
                false
            }
        }
    }
}
