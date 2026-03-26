package com.whatsapp.selectivereads.service

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WhatsAppNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesManager

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val ACTION_MARK_READ = "com.whatsapp.selectivereads.ACTION_MARK_READ"
        const val ACTION_DISMISS = "com.whatsapp.selectivereads.ACTION_DISMISS"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val EXTRA_CHAT_KEY = "chat_key"

        const val PREFS_NAME = "selective_reads_prefs"
        const val KEY_ENABLED = "service_enabled"
        const val KEY_INTERCEPT_GROUPS = "intercept_groups"
        const val KEY_AUTO_DISMISS_ENABLED = "auto_dismiss_enabled"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.isEnabled()) return

        val packageName = sbn.packageName
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val isGroupChat = subText != null || title.contains(":")

        if (isGroupChat && !prefs.shouldInterceptGroups()) return

        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val notificationKey = sbn.key
        val chatKey = extractChatKey(sbn)

        scope.launch {
            val dao = WhatsAppSelectiveReadsApp.instance.database.messageDao()

            val existing = dao.findByNotificationKey(notificationKey)
            if (existing != null && existing.status == MessageStatus.PENDING) {
                val updated = existing.copy(
                    messageText = text,
                    timestamp = sbn.postTime
                )
                dao.update(updated)
                return@launch
            }

            val senderName = if (isGroupChat) {
                val parts = title.split(":")
                if (parts.size >= 2) parts[0].trim() else title
            } else {
                title
            }

            val message = Message(
                notificationKey = notificationKey,
                packageName = packageName,
                senderName = senderName,
                messageText = text,
                chatKey = chatKey,
                isGroupChat = isGroupChat,
                timestamp = sbn.postTime,
                notificationId = sbn.id,
                notificationTag = sbn.tag
            )

            dao.insert(message)
        }

        if (prefs.shouldAutoDismiss()) {
            cancelNotification(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        // Notification was removed externally - don't change message status
    }

    private fun extractChatKey(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras?.getString(Notification.EXTRA_TITLE) ?: "unknown"
        val subText = extras?.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        return "${sbn.packageName}:${title}:${subText}".hashCode().toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
