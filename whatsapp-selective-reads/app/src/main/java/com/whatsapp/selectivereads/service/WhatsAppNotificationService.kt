package com.whatsapp.selectivereads.service

import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
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
import android.util.Base64 as AndroidBase64

class WhatsAppNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesManager

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val ACTION_SEND_REPLY = "com.whatsapp.selectivereads.ACTION_SEND_REPLY"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"

        var instance: WhatsAppNotificationService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
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
        val conversationId = "${packageName}:${chatKey}"

        val chatTitle = if (isGroupChat) title else title

        val replyAction = extractReplyAction(notification)
        val hasReplyAction = replyAction != null
        val replyActionIndex = replyAction?.first ?: -1
        val remoteInputResultKey = replyAction?.second?.resultKey

        scope.launch {
            val db = WhatsAppSelectiveReadsApp.instance.database
            val messageDao = db.messageDao()
            val conversationDao = db.conversationDao()

            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

            if (messagingStyle != null) {
                val messages = messagingStyle.messages
                val conversationUser = messagingStyle.conversationTitle?.toString() ?: chatTitle

                if (messages.isNotEmpty()) {
                    for (msg in messages) {
                        val msgText = msg.text?.toString() ?: ""
                        val msgPerson = msg.person
                        val msgSender = if (msgPerson != null) {
                            msgPerson.name?.toString() ?: chatTitle
                        } else {
                            if (isGroupChat) {
                                val parts = title.split(":")
                                if (parts.size >= 2) parts[0].trim() else title
                            } else {
                                chatTitle
                            }
                        }

                        val msgKey = "${conversationId}:${msg.timestamp}"

                        val existing = messageDao.findByNotificationKey(msgKey)
                        if (existing != null) continue

                        var hasMedia = false
                        var mediaType: String? = null
                        var mediaUri: String? = null
                        var audioDuration: Long = 0

                        val msgData = msg.dataUri
                        val msgDataMimeType = msg.dataMimeType
                        if (msgData != null && msgDataMimeType != null) {
                            hasMedia = true
                            mediaType = msgDataMimeType

                            // For simplicity, we just use the URI or try to save it if it's content
                            mediaUri = msgData.toString()

                            if (msgDataMimeType.startsWith("audio/")) {
                                audioDuration = extractAudioDuration(msgText)
                            }
                        }

                        if (msgText.isEmpty() && !hasMedia) continue

                        val message = Message(
                            notificationKey = msgKey,
                            packageName = packageName,
                            conversationId = conversationId,
                            senderName = msgSender,
                            messageText = if (msgText.isEmpty() && hasMedia) getMediaLabel(mediaType) else msgText,
                            chatKey = chatKey,
                            isGroupChat = isGroupChat,
                            timestamp = msg.timestamp,
                            hasMedia = hasMedia,
                            mediaType = mediaType,
                            mediaUri = mediaUri,
                            audioDurationMs = audioDuration
                        )
                        messageDao.insert(message)
                    }
                } else {
                    handleSingleMessage(
                        messageDao, notificationKey, packageName, conversationId,
                        chatTitle, text, chatKey, isGroupChat, sbn.postTime
                    )
                }
            } else {
                handleSingleMessage(
                    messageDao, notificationKey, packageName, conversationId,
                    chatTitle, text, chatKey, isGroupChat, sbn.postTime
                )
            }

            val unreadCount = try {
                messageDao.getMessagesForConversationSync(conversationId)
                    .count { it.status == MessageStatus.PENDING }
            } catch (e: Exception) { 0 }

            val lastMsgText = text

            val existingConv = conversationDao.getById(conversationId)
            val conversation = ConversationEntity(
                id = conversationId,
                packageName = packageName,
                chatTitle = chatTitle,
                isGroupChat = isGroupChat,
                lastMessagePreview = lastMsgText,
                lastMessageTimestamp = sbn.postTime,
                unreadCount = unreadCount,
                notificationKey = notificationKey,
                hasReplyAction = hasReplyAction || (existingConv?.hasReplyAction == true),
                replyActionIndex = if (hasReplyAction) replyActionIndex else (existingConv?.replyActionIndex ?: -1),
                remoteInputResultKey = remoteInputResultKey ?: existingConv?.remoteInputResultKey
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
        postTime: Long
    ) {
        val existing = dao.findByNotificationKey(notificationKey)
        if (existing != null) {
            dao.update(existing.copy(messageText = text, timestamp = postTime))
            return
        }

        if (text.isEmpty()) return

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
        data: String,
        mimeType: String,
        conversationId: String,
        timestamp: Long
    ): String? {
        return try {
            val ext = when {
                mimeType.startsWith("image/") -> ".jpg"
                mimeType.startsWith("video/") -> ".mp4"
                mimeType.startsWith("audio/") -> ".ogg"
                mimeType.startsWith("application/pdf") -> ".pdf"
                mimeType.startsWith("application/") -> ".bin"
                else -> ".bin"
            }
            val mediaDir = File(filesDir, "media/${conversationId.hashCode()}")
            mediaDir.mkdirs()
            val file = File(mediaDir, "${timestamp}${ext}")
            try {
                val bytes = AndroidBase64.decode(data, AndroidBase64.DEFAULT)
                FileOutputStream(file).use { it.write(bytes) }
            } catch (e: Exception) {
                FileOutputStream(file).use { it.write(data.toByteArray()) }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractAudioDuration(text: String): Long {
        val durationPattern = Regex("(\\d+)[\"']?\\s*(?:sec|second|min|minute)?", RegexOption.IGNORE_CASE)
        val match = durationPattern.find(text)
        return try {
            val value = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            if (text.contains("min", ignoreCase = true)) value * 60_000 else value * 1000
        } catch (e: Exception) { 0L }
    }

    private fun getMediaLabel(mediaType: String?): String {
        return when {
            mediaType == null -> "\uD83D\uDCCE Attachment"
            mediaType.startsWith("image/") -> "\uD83D\uDDBC Photo"
            mediaType.startsWith("video/") -> "\uD83C\uDFA5 Video"
            mediaType.startsWith("audio/") -> "\uD83C\uDFA4 Audio"
            mediaType.startsWith("application/pdf") -> "\uD83D\uDCC4 PDF"
            else -> "\uD83D\uDCCE Document"
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
        instance = null
        scope.cancel()
    }
}
