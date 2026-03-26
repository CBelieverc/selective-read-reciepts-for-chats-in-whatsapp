package com.whatsapp.selectivereads.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notificationKey: String,
    val packageName: String = "com.whatsapp",
    val conversationId: String,
    val senderName: String,
    val messageText: String,
    val chatKey: String,
    val isGroupChat: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.PENDING,
    val notificationId: Int = 0,
    val notificationTag: String? = null,
    val hasMedia: Boolean = false,
    val mediaType: String? = null,
    val mediaUri: String? = null,
    val mediaBitmapPath: String? = null,
    val isMuted: Boolean = false,
    val audioDurationMs: Long = 0,
    val quotedMessageText: String? = null
)

enum class MessageStatus {
    PENDING,
    READ_SENT,
    DISMISSED,
    ARCHIVED,
    REPLIED
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val packageName: String = "com.whatsapp",
    val chatTitle: String,
    val isGroupChat: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val status: MessageStatus = MessageStatus.PENDING,
    val notificationKey: String = "",
    val hasReplyAction: Boolean = false,
    val replyActionIndex: Int = -1,
    val remoteInputResultKey: String? = null
)
