package com.whatsapp.selectivereads.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.whatsapp.selectivereads.R
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val onMarkRead: (Message) -> Unit,
    private val onDismiss: (Message) -> Unit,
    private val onOpenChat: (Message) -> Unit
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(message: Message) {
            binding.apply {
                senderName.text = message.senderName
                messageText.text = message.messageText
                messageTime.text = formatTime(message.timestamp)

                if (message.isGroupChat) {
                    chatTypeIndicator.setImageResource(R.drawable.ic_group)
                    chatTypeIndicator.contentDescription = "Group chat"
                } else {
                    chatTypeIndicator.setImageResource(R.drawable.ic_person)
                    chatTypeIndicator.contentDescription = "Individual chat"
                }

                statusBadge.text = when (message.status) {
                    MessageStatus.PENDING -> "Pending"
                    MessageStatus.READ_SENT -> "Read"
                    MessageStatus.DISMISSED -> "Dismissed"
                    MessageStatus.ARCHIVED -> "Archived"
                }

                statusBadge.setBackgroundResource(
                    when (message.status) {
                        MessageStatus.PENDING -> R.drawable.badge_pending
                        MessageStatus.READ_SENT -> R.drawable.badge_read
                        MessageStatus.DISMISSED -> R.drawable.badge_dismissed
                        MessageStatus.ARCHIVED -> R.drawable.badge_archived
                    }
                )

                val isPending = message.status == MessageStatus.PENDING

                btnMarkRead.isEnabled = isPending
                btnMarkRead.alpha = if (isPending) 1.0f else 0.4f
                btnMarkRead.setOnClickListener { onMarkRead(message) }

                btnDismiss.isEnabled = isPending
                btnDismiss.alpha = if (isPending) 1.0f else 0.4f
                btnDismiss.setOnClickListener { onDismiss(message) }

                btnOpenChat.setOnClickListener { onOpenChat(message) }

                root.setOnClickListener { onOpenChat(message) }
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> timeFormat.format(Date(timestamp))
                else -> dateFormat.format(Date(timestamp))
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
