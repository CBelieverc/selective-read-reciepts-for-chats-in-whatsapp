package com.whatsapp.selectivereads.ui.adapter

import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.whatsapp.selectivereads.R
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ItemDateDividerBinding
import com.whatsapp.selectivereads.databinding.ItemMessageBubbleBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageBubbleAdapter(
    private val isGroupChat: Boolean,
    private val onMediaClick: (Message) -> Unit
) : ListAdapter<RecyclerView.Adapter<RecyclerView.ViewHolder>, MessageBubbleAdapter.BaseViewHolder>(AnyDiffCallback()) {

    private val items = mutableListOf<ChatListItem>()

    sealed class ChatListItem {
        data class DateHeader(val timestamp: Long) : ChatListItem()
        data class MessageItem(val message: Message) : ChatListItem()
    }

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_MESSAGE_IN = 1
        private const val VIEW_TYPE_MESSAGE_OUT = 2

        private val SENDER_COLORS = intArrayOf(
            R.color.wa_sender_1, R.color.wa_sender_2, R.color.wa_sender_3,
            R.color.wa_sender_4, R.color.wa_sender_5, R.color.wa_sender_6
        )
    }

    fun submitChatList(newItems: List<ChatListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is ChatListItem.DateHeader -> VIEW_TYPE_DATE
            is ChatListItem.MessageItem -> {
                if (item.message.senderName == "You") VIEW_TYPE_MESSAGE_OUT
                else VIEW_TYPE_MESSAGE_IN
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> {
                val binding = ItemDateDividerBinding.inflate(inflater, parent, false)
                DateViewHolder(binding)
            }
            else -> {
                val binding = ItemMessageBubbleBinding.inflate(inflater, parent, false)
                MessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatListItem.DateHeader -> (holder as DateViewHolder).bind(item)
            is ChatListItem.MessageItem -> {
                val prevItem = items.getOrNull(position - 1)
                val nextItem = items.getOrNull(position + 1)
                val isFirstInGroup = prevItem !is ChatListItem.MessageItem ||
                    prevItem.message.senderName != item.message.senderName
                val isLastInGroup = nextItem !is ChatListItem.MessageItem ||
                    nextItem.message.senderName != item.message.senderName
                (holder as MessageViewHolder).bind(item, isFirstInGroup, isLastInGroup)
            }
        }
    }

    abstract class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class DateViewHolder(
        private val binding: ItemDateDividerBinding
    ) : BaseViewHolder(binding.root) {

        fun bind(item: ChatListItem.DateHeader) {
            binding.dateText.text = formatDateHeader(item.timestamp)
        }

        private fun formatDateHeader(timestamp: Long): String {
            val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

            return when {
                msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "TODAY"

                msgCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "YESTERDAY"

                else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    inner class MessageViewHolder(
        private val binding: ItemMessageBubbleBinding
    ) : BaseViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(item: ChatListItem.MessageItem, isFirstInGroup: Boolean, isLastInGroup: Boolean) {
            val message = item.message
            val isSent = message.senderName == "You"

            binding.bubbleContainer.apply {
                // Set gravity based on sent/received
                gravity = if (isSent) Gravity.END else Gravity.START

                // Set bubble background based on position in group
                if (isSent) {
                    setBackgroundResource(
                        if (isLastInGroup) R.drawable.bubble_out else R.drawable.bubble_out_no_tail
                    )
                    (layoutParams as ViewGroup.MarginLayoutParams).apply {
                        marginStart = 64
                        marginEnd = 0
                    }
                } else {
                    setBackgroundResource(
                        when {
                            isFirstInGroup && isLastInGroup -> R.drawable.bubble_in
                            isFirstInGroup -> R.drawable.bubble_in_first
                            isLastInGroup -> R.drawable.bubble_in_last
                            else -> R.drawable.bubble_in_middle
                        }
                    )
                    (layoutParams as ViewGroup.MarginLayoutParams).apply {
                        marginStart = 0
                        marginEnd = 64
                    }
                }
            }

            // Sender name (only for group chats, received messages, first in group)
            if (isGroupChat && !isSent && isFirstInGroup) {
                binding.senderName.visibility = View.VISIBLE
                binding.senderName.text = message.senderName
                val colorIndex = (message.senderName.hashCode() and Int.MAX_VALUE) % SENDER_COLORS.size
                binding.senderName.setTextColor(itemView.context.getColor(SENDER_COLORS[colorIndex]))
            } else {
                binding.senderName.visibility = View.GONE
            }

            // Message text
            binding.messageText.text = message.messageText

            // Timestamp
            binding.messageTime.text = timeFormat.format(Date(message.timestamp))

            // Tick indicator (only for sent messages)
            if (isSent) {
                binding.tickIndicator.visibility = View.VISIBLE
                binding.tickIndicator.setImageResource(
                    when (message.status) {
                        MessageStatus.READ_SENT -> R.drawable.tick_read
                        MessageStatus.REPLIED -> R.drawable.tick_read
                        MessageStatus.DISMISSED -> R.drawable.tick_delivered
                        MessageStatus.PENDING -> R.drawable.tick_sent
                        MessageStatus.ARCHIVED -> R.drawable.tick_sent
                    }
                )
            } else {
                binding.tickIndicator.visibility = View.GONE
            }

            // Media handling
            if (message.hasMedia && message.mediaType != null) {
                binding.mediaContainer.visibility = View.VISIBLE
                binding.mediaFileVisibility(message)
                binding.mediaContainer.setOnClickListener { onMediaClick(message) }
            } else {
                binding.mediaContainer.visibility = View.GONE
            }
        }

        private fun ItemMessageBubbleBinding.mediaFileVisibility(message: Message) {
            when {
                message.mediaType!!.startsWith("image/") -> {
                    mediaThumbnail.visibility = View.VISIBLE
                    mediaFileInfo.visibility = View.GONE
                    mediaThumbnail.setImageResource(android.R.color.darker_gray)
                }
                else -> {
                    mediaThumbnail.visibility = View.GONE
                    mediaFileInfo.visibility = View.VISIBLE
                    mediaFileName.text = message.mediaType
                    mediaFileSize.text = "Tap to download"
                }
            }
        }
    }

    class AnyDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is ChatListItem.DateHeader && newItem is ChatListItem.DateHeader) {
                return oldItem.timestamp == newItem.timestamp
            }
            if (oldItem is ChatListItem.MessageItem && newItem is ChatListItem.MessageItem) {
                return oldItem.message.id == newItem.message.id
            }
            return false
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }
}
