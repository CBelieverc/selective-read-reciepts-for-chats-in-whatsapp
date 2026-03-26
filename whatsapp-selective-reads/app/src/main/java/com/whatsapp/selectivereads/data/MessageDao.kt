package com.whatsapp.selectivereads.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingMessages(): LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY timestamp DESC")
    suspend fun getPendingMessagesSync(): List<Message>

    @Query("SELECT * FROM messages WHERE chatKey = :chatKey AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingMessagesByChat(chatKey: String): LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE status != 'PENDING' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): LiveData<List<Message>>

    @Query("SELECT DISTINCT chatKey, senderName, isGroupChat FROM messages WHERE status = 'PENDING' ORDER BY MAX(timestamp) DESC")
    fun getPendingChats(): LiveData<List<ChatSummary>>

    @Query("SELECT * FROM messages WHERE notificationKey = :notificationKey LIMIT 1")
    suspend fun findByNotificationKey(notificationKey: String): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Update
    suspend fun update(message: Message)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: Long, status: MessageStatus)

    @Query("UPDATE messages SET status = :status WHERE chatKey = :chatKey AND status = 'PENDING'")
    suspend fun updateStatusByChat(chatKey: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM messages WHERE status != 'PENDING'")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'PENDING'")
    fun getPendingCount(): LiveData<Int>
}

data class ChatSummary(
    val chatKey: String,
    val senderName: String,
    val isGroupChat: Boolean
)
