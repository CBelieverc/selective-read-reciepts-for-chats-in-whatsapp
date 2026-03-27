package com.whatsapp.selectivereads.ui.adapter

import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import com.whatsapp.selectivereads.R
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ItemDateDividerBinding
import com.whatsapp.selectivereads.databinding.ItemMessageBubbleBinding
import com.whatsapp.selectivereads.service.AudioPlayerManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageBubbleAdapter(
    private val isGroupChat: Boolean,
    private val onMediaClick: (Message) -> Unit,
    private val onLongClick: (Message, View) -> Unit,
    private val onAudioPlay: (Message) -> Unit
) : RecyclerView.Adapter<MessageBubbleAdapter.BaseViewHolder>() {

    private val items = mutableListOf<ChatListItem>()
    private val audioPlayer = AudioPlayerManager.getInstance()

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

            // Bubble alignment and background
            binding.bubbleContainer.apply {
                gravity = if (isSent) Gravity.END else Gravity.START

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

            // Sender name for groups
            if (isGroupChat && !isSent && isFirstInGroup) {
                binding.senderName.visibility = View.VISIBLE
                binding.senderName.text = message.senderName
                val colorIndex = (message.senderName.hashCode() and Int.MAX_VALUE) % SENDER_COLORS.size
                binding.senderName.setTextColor(itemView.context.getColor(SENDER_COLORS[colorIndex]))
            } else {
                binding.senderName.visibility = View.GONE
            }

            // Quoted message
            if (message.quotedMessageText != null && message.quotedMessageText.isNotEmpty()) {
                binding.quotedContainer.visibility = View.VISIBLE
                binding.quotedText.text = message.quotedMessageText
            } else {
                binding.quotedContainer.visibility = View.GONE
            }

            // Message text
            binding.messageText.text = message.messageText
            binding.messageText.visibility = if (message.messageText.isEmpty()) View.GONE else View.VISIBLE

            // Timestamp
            binding.messageTime.text = timeFormat.format(Date(message.timestamp))

            // Tick indicator
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
            setupMedia(message)

            // Long press for context menu
            binding.bubbleRoot.setOnLongClickListener { v ->
                onLongClick(message, v)
                true
            }
        }

        private fun setupMedia(message: Message) {
            // Reset visibility
            binding.mediaImage.visibility = View.GONE
            binding.audioContainer.visibility = View.GONE
            binding.fileContainer.visibility = View.GONE

            if (!message.hasMedia || message.mediaType == null) return

            when {
                message.mediaType.startsWith("image/") -> {
                    binding.mediaImage.visibility = View.VISIBLE
                    binding.mediaImage.setOnClickListener { onMediaClick(message) }

                    val filePath = message.mediaUri
                    if (filePath != null) {
                        val bitmap = BitmapFactory.decodeFile(filePath)
                        if (bitmap != null) {
                            binding.mediaImage.setImageBitmap(bitmap)
                        } else {
                            binding.mediaImage.setImageResource(android.R.color.darker_gray)
                        }
                    } else {
                        binding.mediaImage.setImageResource(android.R.color.darker_gray)
                    }
                }

                message.mediaType.startsWith("audio/") -> {
                    binding.audioContainer.visibility = View.VISIBLE
                    setupAudioPlayer(message)
                }

                message.mediaType.startsWith("video/") -> {
                    binding.fileContainer.visibility = View.VISIBLE
                    binding.fileContainer.setOnClickListener { onMediaClick(message) }
                    binding.fileName.text = "\uD83C\uDFA5 Video message"
                    binding.fileInfo.text = "Tap to view"
                }

                else -> {
                    binding.fileContainer.visibility = View.VISIBLE
                    binding.fileContainer.setOnClickListener { onMediaClick(message) }
                    val label = when {
                        message.mediaType.startsWith("application/pdf") -> "\uD83D\uDCC4 PDF Document"
                        else -> "\uD83D\uDCCE Document"
                    }
                    binding.fileName.text = label
                    binding.fileInfo.text = "Tap to download"
                }
            }
        }

        private fun setupAudioPlayer(message: Message) {
            val filePath = message.mediaUri ?: return
            val durationText = formatDuration(message.audioDurationMs)
            binding.audioDuration.text = durationText

            val isCurrentlyPlaying = audioPlayer.isPlayingFile(filePath)

            binding.btnAudioPlay.setImageResource(
                if (isCurrentlyPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            binding.btnAudioPlay.setOnClickListener {
                onAudioPlay(message)
            }

            binding.audioSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && audioPlayer.isPlayingFile(filePath)) {
                        val duration = audioPlayer.getDuration()
                        audioPlayer.seekTo((progress / 100f * duration).toInt())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            if (isCurrentlyPlaying) {
                val current = audioPlayer.getCurrentPosition()
                val total = audioPlayer.getDuration()
                binding.audioCurrentTime.text = formatDuration(current.toLong())
                if (total > 0) {
                    binding.audioSeekbar.progress = (current * 100 / total)
                }
            } else {
                binding.audioCurrentTime.text = "0:00"
                binding.audioSeekbar.progress = 0
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}
