package com.whatsapp.selectivereads.ui.adapter

import android.view.LayoutInflater
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
    private val onMarkRead: (ConversationEntity) -> Unit,
    private val onDismiss: (ConversationEntity) -> Unit,
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

                if (conversation.isGroupChat) {
                    chatIcon.setImageResource(R.drawable.ic_group)
                } else {
                    chatIcon.setImageResource(R.drawable.ic_person)
                }

                unreadBadge.text = conversation.unreadCount.toString()
                unreadBadge.visibility = if (conversation.unreadCount > 0) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

                val isPending = conversation.status == MessageStatus.PENDING

                btnReply.visibility = if (isPending && conversation.hasReplyAction) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                btnReply.setOnClickListener { onReply(conversation) }

                btnMarkRead.isEnabled = isPending
                btnMarkRead.alpha = if (isPending) 1.0f else 0.4f
                btnMarkRead.setOnClickListener { onMarkRead(conversation) }

                btnDismiss.isEnabled = isPending
                btnDismiss.alpha = if (isPending) 1.0f else 0.4f
                btnDismiss.setOnClickListener { onDismiss(conversation) }

                root.setOnClickListener { onClick(conversation) }
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                else -> timeFormat.format(Date(timestamp))
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
