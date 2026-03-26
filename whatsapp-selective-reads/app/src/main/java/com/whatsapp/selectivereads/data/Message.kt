package com.whatsapp.selectivereads.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notificationKey: String,
    val packageName: String = "com.whatsapp",
    val senderName: String,
    val messageText: String,
    val chatKey: String,
    val isGroupChat: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.PENDING,
    val notificationId: Int = 0,
    val notificationTag: String? = null
)

enum class MessageStatus {
    PENDING,
    READ_SENT,
    DISMISSED,
    ARCHIVED
}
