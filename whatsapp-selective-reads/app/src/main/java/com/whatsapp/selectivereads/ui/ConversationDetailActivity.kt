package com.whatsapp.selectivereads.ui

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.whatsapp.selectivereads.R
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.ConversationEntity
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ActivityConversationDetailBinding
import com.whatsapp.selectivereads.service.AudioPlayerManager
import com.whatsapp.selectivereads.service.ReplyHelper
import com.whatsapp.selectivereads.service.WhatsAppNotificationService
import com.whatsapp.selectivereads.ui.adapter.MessageBubbleAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var bubbleAdapter: MessageBubbleAdapter
    private val db by lazy { WhatsAppSelectiveReadsApp.instance.database }
    private val messageDao by lazy { db.messageDao() }
    private val conversationDao by lazy { db.conversationDao() }
    private val audioPlayer by lazy { AudioPlayerManager.getInstance() }

    private var conversationId: String = ""
    private var conversation: ConversationEntity? = null
    private var isAtBottom = true
    private var playingMessageId: Long = -1

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: run {
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupInputBar()
        setupActionBar()
        setupScrollToBottom()
        observeConversation()
        startAudioProgressUpdater()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
    }

    override fun onPause() {
        super.onPause()
        audioPlayer.pause()
    }

    private fun setupToolbar() {
        binding.chatToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnMore.setOnClickListener { showMoreOptions() }
        binding.btnVoiceCall.setOnClickListener {
            Snackbar.make(binding.root, "Voice call - opens WhatsApp", Snackbar.LENGTH_SHORT).show()
            openWhatsAppChat()
        }
        binding.btnVideoCall.setOnClickListener {
            Snackbar.make(binding.root, "Video call - opens WhatsApp", Snackbar.LENGTH_SHORT).show()
            openWhatsAppChat()
        }
    }

    private fun setupRecyclerView() {
        val isGroup = conversation?.isGroupChat ?: false
        bubbleAdapter = MessageBubbleAdapter(
            isGroupChat = isGroup,
            onMediaClick = { message -> openMediaViewer(message) },
            onLongClick = { message, anchor -> showMessageContextMenu(message, anchor) },
            onAudioPlay = { message -> handleAudioPlay(message) }
        )

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
            reverseLayout = false
        }

        binding.messagesRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = bubbleAdapter

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
                    val totalItems = layoutManager.itemCount
                    isAtBottom = lastVisible >= totalItems - 3
                    updateScrollToBottomButton(totalItems - lastVisible - 1)
                }
            })
        }
    }

    private fun setupInputBar() {
        // Toggle mic/send button based on input
        binding.inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                val iconRes = if (hasText) R.drawable.ic_send_white else R.drawable.ic_mic
                binding.btnSend.setImageResource(iconRes)

                // Animate button color
                if (hasText) {
                    binding.btnSend.imageTintList = android.content.res.ColorStateList.valueOf(
                        getColor(android.R.color.white)
                    )
                }
            }
        })

        // Enter key sends message
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendReply()
                true
            } else false
        }

        // Send/mic button
        binding.btnSend.setOnClickListener {
            if (!binding.inputMessage.text.isNullOrBlank()) {
                sendReply()
            } else {
                Snackbar.make(binding.root, "Hold to record voice message", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Emoji button - focus input and show keyboard
        binding.btnEmoji.setOnClickListener {
            binding.inputMessage.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.inputMessage, InputMethodManager.SHOW_IMPLICIT)
        }

        // Attachment button
        binding.btnAttach.setOnClickListener {
            val popup = PopupMenu(this, binding.btnAttach)
            popup.menu.add("Document")
            popup.menu.add("Camera")
            popup.menu.add("Gallery")
            popup.menu.add("Audio")
            popup.menu.add("Location")
            popup.menu.add("Contact")
            popup.setOnMenuItemClickListener { item ->
                Snackbar.make(binding.root, "${item.title} - opens WhatsApp", Snackbar.LENGTH_SHORT).show()
                openWhatsAppChat()
                true
            }
            popup.show()
        }

        // Camera button
        binding.btnCamera.setOnClickListener {
            Snackbar.make(binding.root, "Camera - opens WhatsApp", Snackbar.LENGTH_SHORT).show()
            openWhatsAppChat()
        }
    }

    private fun setupActionBar() {
        binding.btnMarkRead.setOnClickListener { markConversationAsRead() }
        binding.btnDismiss.setOnClickListener { dismissConversation() }
        binding.btnOpenWhatsApp.setOnClickListener { openWhatsAppChat() }
    }

    private fun setupScrollToBottom() {
        binding.scrollToBottomBtn.setOnClickListener {
            isAtBottom = true
            binding.messagesRecyclerView.smoothScrollToPosition(
                (binding.messagesRecyclerView.adapter?.itemCount ?: 1) - 1
            )
        }
    }

    private fun observeConversation() {
        conversationDao.getByIdLive(conversationId).observe(this) { conv ->
            conversation = conv ?: return@observe

            binding.chatTitle.text = conv.chatTitle
            binding.chatAvatar.setImageResource(
                if (conv.isGroupChat) R.drawable.ic_group else R.drawable.ic_person
            )
            binding.chatSubtitle.text = when {
                conv.unreadCount > 0 -> "${conv.unreadCount} new messages"
                conv.hasReplyAction -> "tap here for contact info"
                else -> "last seen recently"
            }

            binding.statusText.text = when (conv.status) {
                MessageStatus.PENDING -> "Read receipt NOT sent - tap to manage"
                MessageStatus.READ_SENT -> "Read receipt sent"
                MessageStatus.REPLIED -> "Replied - read receipt OFF"
                MessageStatus.DISMISSED -> "Dismissed - read receipt NOT sent"
                MessageStatus.ARCHIVED -> "Archived"
            }

            val isPending = conv.status == MessageStatus.PENDING
            binding.actionBar.visibility = if (isPending) View.VISIBLE else View.GONE

            binding.inputMessage.hint = if (conv.hasReplyAction) {
                "Type a message"
            } else {
                "Type a message"
            }

            // Re-create adapter with correct group setting
            val oldItems = (bubbleAdapter as? MessageBubbleAdapter)?.let { adapter ->
                // Keep existing items if any
                emptyList<MessageBubbleAdapter.ChatListItem>()
            }

            bubbleAdapter = MessageBubbleAdapter(
                isGroupChat = conv.isGroupChat,
                onMediaClick = { message -> openMediaViewer(message) },
                onLongClick = { message, anchor -> showMessageContextMenu(message, anchor) },
                onAudioPlay = { message -> handleAudioPlay(message) }
            )
            binding.messagesRecyclerView.adapter = bubbleAdapter
            observeMessages()
        }
    }

    private fun observeMessages() {
        messageDao.getMessagesForConversation(conversationId).observe(this) { messages ->
            val chatItems = buildChatList(messages)
            bubbleAdapter.submitChatList(chatItems)

            if (isAtBottom && chatItems.isNotEmpty()) {
                binding.messagesRecyclerView.post {
                    binding.messagesRecyclerView.scrollToPosition(chatItems.size - 1)
                }
            }
        }
    }

    private fun buildChatList(messages: List<Message>): List<MessageBubbleAdapter.ChatListItem> {
        val items = mutableListOf<MessageBubbleAdapter.ChatListItem>()
        var lastDate: Calendar? = null

        for (message in messages) {
            val msgDate = Calendar.getInstance().apply { timeInMillis = message.timestamp }

            if (lastDate == null ||
                msgDate.get(Calendar.YEAR) != lastDate.get(Calendar.YEAR) ||
                msgDate.get(Calendar.DAY_OF_YEAR) != lastDate.get(Calendar.DAY_OF_YEAR)) {
                items.add(MessageBubbleAdapter.ChatListItem.DateHeader(message.timestamp))
                lastDate = msgDate
            }

            items.add(MessageBubbleAdapter.ChatListItem.MessageItem(message))
        }

        return items
    }

    private fun updateScrollToBottomButton(unreadCount: Int) {
        if (unreadCount > 2) {
            binding.scrollToBottomBtn.visibility = View.VISIBLE
            binding.unreadCountBadge.text = unreadCount.toString()
            binding.unreadCountBadge.visibility = View.VISIBLE
        } else if (!isAtBottom) {
            binding.scrollToBottomBtn.visibility = View.VISIBLE
            binding.unreadCountBadge.visibility = View.GONE
        } else {
            binding.scrollToBottomBtn.visibility = View.GONE
        }
    }

    private fun handleAudioPlay(message: Message) {
        val filePath = message.mediaUri ?: return

        if (audioPlayer.isPlayingFile(filePath)) {
            audioPlayer.pause()
            playingMessageId = -1
            refreshAudioUI()
            return
        }

        audioPlayer.stop()
        playingMessageId = message.id

        audioPlayer.play(
            filePath = filePath,
            onProgress = { currentMs, totalMs ->
                runOnUiThread {
                    refreshAudioUI()
                }
            },
            onComplete = {
                runOnUiThread {
                    playingMessageId = -1
                    refreshAudioUI()
                }
            }
        )

        refreshAudioUI()
    }

    private fun startAudioProgressUpdater() {
        lifecycleScope.launch {
            while (isActive) {
                if (playingMessageId > 0) {
                    refreshAudioUI()
                }
                delay(200)
            }
        }
    }

    private fun refreshAudioUI() {
        val layoutManager = binding.messagesRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        for (i in firstVisible..lastVisible) {
            if (i < 0) continue
            val holder = binding.messagesRecyclerView.findViewHolderForAdapterPosition(i) as? MessageBubbleAdapter.MessageViewHolder
            holder?.let {
                // Force rebind for the playing item
                binding.messagesRecyclerView.adapter?.notifyItemChanged(i)
            }
        }
    }

    private fun sendReply() {
        val text = binding.inputMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        val conv = conversation ?: return

        binding.btnSend.isEnabled = false
        binding.sendProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val service = WhatsAppNotificationService.instance
                var replySuccess = false

                if (service != null && conv.hasReplyAction) {
                    replySuccess = ReplyHelper.sendReply(
                        service = service,
                        notificationKey = conv.notificationKey,
                        conversationId = conversationId,
                        replyText = text,
                        remoteInputResultKey = conv.remoteInputResultKey,
                        replyActionIndex = conv.replyActionIndex
                    )
                } else if (service == null) {
                    Snackbar.make(binding.root, "Notification listener not connected. Grant permission in Settings.", Snackbar.LENGTH_LONG).show()
                }

                val replyMsg = Message(
                    notificationKey = "${conv.notificationKey}:reply:${System.currentTimeMillis()}",
                    packageName = conv.packageName,
                    conversationId = conversationId,
                    senderName = "You",
                    messageText = text,
                    chatKey = conversationId.hashCode().toString(),
                    isGroupChat = conv.isGroupChat,
                    timestamp = System.currentTimeMillis(),
                    status = if (replySuccess) MessageStatus.REPLIED else MessageStatus.PENDING
                )
                messageDao.insert(replyMsg)

                if (replySuccess) {
                    conversationDao.updateStatus(conversationId, MessageStatus.REPLIED)
                }

                binding.inputMessage.text?.clear()

                isAtBottom = true
                binding.messagesRecyclerView.post {
                    binding.messagesRecyclerView.smoothScrollToPosition(
                        (binding.messagesRecyclerView.adapter?.itemCount ?: 1) - 1
                    )
                }

                if (replySuccess) {
                    Snackbar.make(binding.root, "Sent (read receipt OFF)", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Saved locally", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.btnSend.isEnabled = true
                binding.sendProgress.visibility = View.GONE
            }
        }
    }

    private fun showMessageContextMenu(message: Message, anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add(0, 1, 0, "Copy text")
        popup.menu.add(0, 2, 0, "Reply")

        if (message.hasMedia) {
            popup.menu.add(0, 3, 0, "Download media")
        }

        popup.menu.add(0, 4, 0, "Message info")
        popup.menu.add(0, 5, 0, "Mark as read")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.messageText))
                    Snackbar.make(binding.root, "Copied to clipboard", Snackbar.LENGTH_SHORT).show()
                    true
                }
                2 -> {
                    binding.inputMessage.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.inputMessage, InputMethodManager.SHOW_IMPLICIT)
                    true
                }
                3 -> {
                    openMediaViewer(message)
                    true
                }
                4 -> {
                    val timeFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
                    val info = "From: ${message.senderName}\nTime: ${timeFormat.format(java.util.Date(message.timestamp))}\nStatus: ${message.status}"
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Message Info")
                        .setMessage(info)
                        .setPositiveButton("OK", null)
                        .show()
                    true
                }
                5 -> {
                    lifecycleScope.launch {
                        messageDao.updateStatus(message.id, MessageStatus.READ_SENT)
                        Snackbar.make(binding.root, "Marked as read", Snackbar.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun markConversationAsRead() {
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversationId, MessageStatus.READ_SENT)
            conversationDao.updateStatus(conversationId, MessageStatus.READ_SENT)
            openWhatsAppChat()
            Snackbar.make(binding.root, "Read receipt sent", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun dismissConversation() {
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversationId, MessageStatus.DISMISSED)
            conversationDao.updateStatus(conversationId, MessageStatus.DISMISSED)
            Snackbar.make(binding.root, "Dismissed - no read receipt sent", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppChat() {
        val conv = conversation ?: return
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversationId, MessageStatus.READ_SENT)
            conversationDao.updateStatus(conversationId, MessageStatus.READ_SENT)
        }
        val intent = packageManager.getLaunchIntentForPackage(conv.packageName)
        if (intent != null) startActivity(intent)
    }

    private fun openMediaViewer(message: Message) {
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra(MediaViewerActivity.EXTRA_MEDIA_PATH, message.mediaUri)
            putExtra(MediaViewerActivity.EXTRA_MEDIA_TYPE, message.mediaType)
            putExtra(MediaViewerActivity.EXTRA_MESSAGE_TEXT, message.messageText)
            putExtra(MediaViewerActivity.EXTRA_SENDER_NAME, message.senderName)
            putExtra(MediaViewerActivity.EXTRA_TIMESTAMP, message.timestamp)
        }
        startActivity(intent)
    }

    private fun showMoreOptions() {
        val items = arrayOf(
            "Mark as Read & Open WhatsApp",
            "Dismiss (no receipt)",
            "Clear conversation history",
            "Contact info"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> markConversationAsRead()
                    1 -> dismissConversation()
                    2 -> clearConversation()
                    3 -> {
                        val conv = conversation ?: return@setItems
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(conv.chatTitle)
                            .setMessage("Chat type: ${if (conv.isGroupChat) "Group" else "Individual"}\nMessages: ${conv.unreadCount} unread")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }.show()
    }

    private fun clearConversation() {
        lifecycleScope.launch {
            val messages = messageDao.getMessagesForConversationSync(conversationId)
            messages.forEach { messageDao.updateStatus(it.id, MessageStatus.ARCHIVED) }
            Snackbar.make(binding.root, "History cleared", Snackbar.LENGTH_SHORT).show()
        }
    }
}
