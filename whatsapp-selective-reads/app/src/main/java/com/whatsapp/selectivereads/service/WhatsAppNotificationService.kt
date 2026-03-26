package com.whatsapp.selectivereads.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.ConversationEntity
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class WhatsAppNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesManager

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val ACTION_SEND_REPLY = "com.whatsapp.selectivereads.ACTION_SEND_REPLY"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)

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
        val conversationId = "${packageName}:${chatKey}"

        val senderName = if (isGroupChat) {
            val parts = title.split(":")
            if (parts.size >= 2) parts[0].trim() else title
        } else {
            title
        }

        val replyAction = extractReplyAction(notification)
        val hasReplyAction = replyAction != null
        val replyActionIndex = replyAction?.first ?: -1
        val remoteInputResultKey = replyAction?.second?.resultKey

        scope.launch {
            val db = WhatsAppSelectiveReadsApp.instance.database
            val messageDao = db.messageDao()
            val conversationDao = db.conversationDao()

            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyle(notification)
            val mediaBitmap = notification.getLargeIcon()?.loadDrawable(this@WhatsAppNotificationService)
                ?.let { drawable ->
                    val bitmap = drawable as? Bitmap
                    bitmap
                }

            if (messagingStyle != null) {
                val messages = messagingStyle.messages
                if (messages != null && messages.isNotEmpty()) {
                    for (msg in messages) {
                        val msgText = msg.text?.toString() ?: continue
                        val msgSender = msg.senderPerson?.name?.toString() ?: senderName
                        val msgKey = "${conversationId}:${msg.timestamp}"

                        val existing = messageDao.findByNotificationKey(msgKey)
                        if (existing != null) continue

                        val mediaData = msg.data?.let { uri ->
                            val mimeType = msg.dataMimeType
                            if (mimeType != null && uri.isNotEmpty()) {
                                saveMediaToInternal(uri, mimeType, conversationId, msg.timestamp)
                            } else null
                        }

                        val message = Message(
                            notificationKey = msgKey,
                            packageName = packageName,
                            conversationId = conversationId,
                            senderName = msgSender.toString(),
                            messageText = msgText,
                            chatKey = chatKey,
                            isGroupChat = isGroupChat,
                            timestamp = msg.timestamp,
                            hasMedia = mediaData != null,
                            mediaType = mediaData?.first,
                            mediaUri = mediaData?.second
                        )
                        messageDao.insert(message)
                    }
                } else {
                    handleSingleMessage(
                        messageDao, notificationKey, packageName, conversationId,
                        senderName, text, chatKey, isGroupChat, sbn.postTime, extras
                    )
                }
            } else {
                handleSingleMessage(
                    messageDao, notificationKey, packageName, conversationId,
                    senderName, text, chatKey, isGroupChat, sbn.postTime, extras
                )
            }

            val unreadCount = messageDao.getMessagesForConversationSync(conversationId)
                .count { it.status == MessageStatus.PENDING }

            val conversation = ConversationEntity(
                id = conversationId,
                packageName = packageName,
                chatTitle = if (isGroupChat) title else senderName,
                isGroupChat = isGroupChat,
                lastMessagePreview = text,
                lastMessageTimestamp = sbn.postTime,
                unreadCount = unreadCount,
                notificationKey = notificationKey,
                hasReplyAction = hasReplyAction,
                replyActionIndex = replyActionIndex,
                remoteInputResultKey = remoteInputResultKey
            )
            conversationDao.insertOrUpdate(conversation)
        }

        if (prefs.shouldAutoDismiss()) {
            cancelNotification(sbn.key)
        }
    }

    private suspend fun handleSingleMessage(
        dao: com.whatsapp.selectivereads.data.MessageDao,
        notificationKey: String,
        packageName: String,
        conversationId: String,
        senderName: String,
        text: String,
        chatKey: String,
        isGroupChat: Boolean,
        postTime: Long,
        extras: Bundle
    ) {
        val existing = dao.findByNotificationKey(notificationKey)
        if (existing != null && existing.status == MessageStatus.PENDING) {
            dao.update(existing.copy(messageText = text, timestamp = postTime))
            return
        }

        val message = Message(
            notificationKey = notificationKey,
            packageName = packageName,
            conversationId = conversationId,
            senderName = senderName,
            messageText = text,
            chatKey = chatKey,
            isGroupChat = isGroupChat,
            timestamp = postTime
        )
        dao.insert(message)
    }

    private fun extractReplyAction(notification: Notification): Pair<Int, android.app.RemoteInput?>? {
        val actions = notification.actions ?: return null
        for ((index, action) in actions.withIndex()) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null) {
                for (ri in remoteInputs) {
                    val resultKey = ri.resultKey ?: continue
                    if (resultKey.contains("reply", ignoreCase = true) ||
                        resultKey.contains("key_text_reply", ignoreCase = true)) {
                        return Pair(index, ri)
                    }
                }
            }
        }
        return null
    }

    private fun saveMediaToInternal(
        dataUri: String,
        mimeType: String,
        conversationId: String,
        timestamp: Long
    ): Pair<String, String>? {
        return try {
            val ext = when {
                mimeType.startsWith("image/") -> ".jpg"
                mimeType.startsWith("video/") -> ".mp4"
                mimeType.startsWith("audio/") -> ".ogg"
                else -> ".bin"
            }
            val mediaDir = File(filesDir, "media/${conversationId.hashCode()}")
            mediaDir.mkdirs()
            val file = File(mediaDir, "${timestamp}${ext}")
            val bytes = android.util.Base64.decode(dataUri, android.util.Base64.DEFAULT)
            FileOutputStream(file).use { it.write(bytes) }
            Pair(mimeType, file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {}

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
