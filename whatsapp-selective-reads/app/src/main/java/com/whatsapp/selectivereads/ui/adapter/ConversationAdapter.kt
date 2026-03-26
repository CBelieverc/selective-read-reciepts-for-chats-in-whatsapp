package com.whatsapp.selectivereads.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.whatsapp.selectivereads.R
import com.whatsapp.selectivereads.data.ConversationEntity
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit,
    private val onReply: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(
        private val binding: ItemConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(conversation: ConversationEntity) {
            binding.apply {
                chatTitle.text = conversation.chatTitle
                lastMessage.text = conversation.lastMessagePreview
                messageTime.text = formatTime(conversation.lastMessageTimestamp)

                // Avatar
                chatAvatar.setImageResource(
                    if (conversation.isGroupChat) R.drawable.ic_group
                    else R.drawable.ic_person
                )

                // Unread badge
                if (conversation.unreadCount > 0) {
                    unreadBadge.visibility = View.VISIBLE
                    unreadBadge.text = conversation.unreadCount.toString()
                    chatTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                    lastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    unreadBadge.visibility = View.GONE
                    chatTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                    lastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                // Reply button
                val isPending = conversation.status == MessageStatus.PENDING
                if (isPending && conversation.hasReplyAction) {
                    btnReply.visibility = View.VISIBLE
                    btnReply.setOnClickListener { onReply(conversation) }
                } else {
                    btnReply.visibility = View.GONE
                }

                // Status indicator on last message
                if (isPending) {
                    lastMessage.setTextColor(itemView.context.getColor(R.color.accent))
                } else {
                    lastMessage.setTextColor(itemView.context.getColor(R.color.wa_msg_time))
                }

                root.setOnClickListener { onClick(conversation) }
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "now"
                diff < 86400_000 -> timeFormat.format(Date(timestamp))
                diff < 172800_000 -> "Yesterday"
                else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<ConversationEntity>() {
        override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean {
            return oldItem == newItem
        }
    }
}
