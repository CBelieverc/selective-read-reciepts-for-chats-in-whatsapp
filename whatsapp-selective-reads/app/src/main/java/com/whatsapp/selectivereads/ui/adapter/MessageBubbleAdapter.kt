package com.whatsapp.selectivereads.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.databinding.ItemMessageBubbleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageBubbleAdapter(
    private val onMediaClick: (Message) -> Unit
) : ListAdapter<Message, MessageBubbleAdapter.BubbleViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BubbleViewHolder {
        val binding = ItemMessageBubbleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BubbleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BubbleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BubbleViewHolder(
        private val binding: ItemMessageBubbleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(message: Message) {
            binding.apply {
                if (message.isGroupChat) {
                    senderLabel.visibility = View.VISIBLE
                    senderLabel.text = message.senderName
                } else {
                    senderLabel.visibility = View.GONE
                }

                messageText.text = message.messageText
                messageText.maxLines = Integer.MAX_VALUE
                messageTime.text = timeFormat.format(Date(message.timestamp))

                if (message.hasMedia && message.mediaType != null) {
                    mediaPreview.visibility = View.VISIBLE
                    mediaLabel.text = when {
                        message.mediaType.startsWith("image/") -> "\uD83D\uDDBC Image"
                        message.mediaType.startsWith("video/") -> "\uD83C\uDFA5 Video"
                        message.mediaType.startsWith("audio/") -> "\uD83C\uDFA4 Audio"
                        message.mediaType.startsWith("application/") -> "\uD83D\uDCC4 Document"
                        else -> "\uD83D\uDCCE Attachment"
                    }
                    mediaPreview.setOnClickListener { onMediaClick(message) }
                } else {
                    mediaPreview.visibility = View.GONE
                }

                replyIndicator.visibility = if (message.messageText.contains("You replied:", ignoreCase = true)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
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
