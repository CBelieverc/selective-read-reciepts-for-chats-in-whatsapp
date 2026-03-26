package com.whatsapp.selectivereads.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationSync(conversationId: String): List<Message>

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingMessages(): LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY timestamp DESC")
    suspend fun getPendingMessagesSync(): List<Message>

    @Query("SELECT * FROM messages WHERE status != 'PENDING' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE notificationKey = :notificationKey LIMIT 1")
    suspend fun findByNotificationKey(notificationKey: String): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Update
    suspend fun update(message: Message)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: Long, status: MessageStatus)

    @Query("UPDATE messages SET status = :status WHERE conversationId = :conversationId AND status = 'PENDING'")
    suspend fun updateStatusByConversation(conversationId: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM messages WHERE status != 'PENDING'")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'PENDING'")
    fun getPendingCount(): LiveData<Int>
}

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE status = 'PENDING' ORDER BY lastMessageTimestamp DESC")
    fun getPendingConversations(): LiveData<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE status = 'PENDING' ORDER BY lastMessageTimestamp DESC")
    suspend fun getPendingConversationsSync(): List<ConversationEntity>

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC LIMIT :limit")
    fun getAllConversations(limit: Int = 50): LiveData<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getByIdLive(id: String): LiveData<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Query("UPDATE conversations SET status = :status WHERE id = :conversationId")
    suspend fun updateStatus(conversationId: String, status: MessageStatus)

    @Query("UPDATE conversations SET status = 'READ_SENT'")
    suspend fun markAllAsRead()

    @Query("DELETE FROM conversations WHERE status != 'PENDING'")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM conversations WHERE status = 'PENDING'")
    fun getPendingCount(): LiveData<Int>
}
