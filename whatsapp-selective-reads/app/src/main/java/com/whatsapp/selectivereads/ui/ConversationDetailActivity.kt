package com.whatsapp.selectivereads.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.whatsapp.selectivereads.service.ReplyHelper
import com.whatsapp.selectivereads.service.WhatsAppNotificationService
import com.whatsapp.selectivereads.ui.adapter.MessageBubbleAdapter
import kotlinx.coroutines.launch
import java.util.Calendar

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var bubbleAdapter: MessageBubbleAdapter
    private val db by lazy { WhatsAppSelectiveReadsApp.instance.database }
    private val messageDao by lazy { db.messageDao() }
    private val conversationDao by lazy { db.conversationDao() }

    private var conversationId: String = ""
    private var conversation: ConversationEntity? = null
    private var isAtBottom = true

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
        observeMessages()
    }

    private fun setupToolbar() {
        binding.chatToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnMore.setOnClickListener {
            showMoreOptions()
        }
    }

    private fun setupRecyclerView() {
        val isGroup = conversation?.isGroupChat ?: false
        bubbleAdapter = MessageBubbleAdapter(
            isGroupChat = isGroup,
            onMediaClick = { message -> openMediaViewer(message) }
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
        // Toggle between mic and send button based on text input
        binding.inputMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.setImageResource(
                    if (hasText) R.drawable.ic_send_white else R.drawable.ic_mic
                )
            }
        })

        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendReply()
                true
            } else false
        }

        binding.btnSend.setOnClickListener {
            if (!binding.inputMessage.text.isNullOrBlank()) {
                sendReply()
            }
        }

        binding.btnEmoji.setOnClickListener {
            // Toggle keyboard emoji panel
            binding.inputMessage.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.inputMessage, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupActionBar() {
        binding.btnMarkRead.setOnClickListener { markConversationAsRead() }
        binding.btnDismiss.setOnClickListener { dismissConversation() }
        binding.btnOpenWhatsApp.setOnClickListener { openWhatsAppChat() }
    }

    private fun setupScrollToBottom() {
        binding.scrollToBottomBtn.setOnClickListener {
            binding.messagesRecyclerView.smoothScrollToPosition(
                (binding.messagesRecyclerView.adapter?.itemCount ?: 1) - 1
            )
        }
    }

    private fun observeConversation() {
        conversationDao.getByIdLive(conversationId).observe(this) { conv ->
            conversation = conv ?: return@observe

            // Update toolbar
            binding.chatTitle.text = conv.chatTitle
            binding.chatAvatar.setImageResource(
                if (conv.isGroupChat) R.drawable.ic_group else R.drawable.ic_person
            )
            binding.chatSubtitle.text = when {
                conv.unreadCount > 0 -> "${conv.unreadCount} new messages"
                conv.hasReplyAction -> "tap here for contact info"
                else -> "last seen recently"
            }

            // Update status bar
            binding.statusText.text = when (conv.status) {
                MessageStatus.PENDING -> "Read receipt NOT sent - messages pending"
                MessageStatus.READ_SENT -> "Read receipt sent"
                MessageStatus.REPLIED -> "Replied - read receipt OFF"
                MessageStatus.DISMISSED -> "Dismissed - read receipt NOT sent"
                MessageStatus.ARCHIVED -> "Archived"
            }

            // Show/hide action bar
            val isPending = conv.status == MessageStatus.PENDING
            binding.actionBar.visibility = if (isPending) View.VISIBLE else View.GONE

            // Input bar state
            if (conv.hasReplyAction) {
                binding.inputMessage.isEnabled = true
                binding.inputMessage.hint = "Type a message"
                binding.btnSend.isEnabled = true
            } else {
                binding.inputMessage.isEnabled = true
                binding.inputMessage.hint = "Reply (sends via WhatsApp notification)"
                binding.btnSend.isEnabled = true
            }

            // Re-create adapter with correct group setting
            bubbleAdapter = MessageBubbleAdapter(
                isGroupChat = conv.isGroupChat,
                onMediaClick = { message -> openMediaViewer(message) }
            )
            binding.messagesRecyclerView.adapter = bubbleAdapter
            observeMessages()
        }
    }

    private fun observeMessages() {
        messageDao.getMessagesForConversation(conversationId).observe(this) { messages ->
            val chatItems = buildChatList(messages)
            bubbleAdapter.submitChatList(chatItems)

            if (isAtBottom && messages.isNotEmpty()) {
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

    private fun sendReply() {
        val text = binding.inputMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        val conv = conversation ?: return

        binding.btnSend.isEnabled = false
        binding.sendProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val service = getSystemService(NOTIFICATION_SERVICE) as? WhatsAppNotificationService
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
                hideKeyboard()

                // Scroll to bottom
                isAtBottom = true
                binding.messagesRecyclerView.post {
                    binding.messagesRecyclerView.smoothScrollToPosition(
                        (binding.messagesRecyclerView.adapter?.itemCount ?: 1) - 1
                    )
                }

                if (replySuccess) {
                    Snackbar.make(binding.root, "Sent (read receipt OFF)", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Saved locally - notification may have expired", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.btnSend.isEnabled = true
                binding.sendProgress.visibility = View.GONE
            }
        }
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
            "Clear conversation history"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> markConversationAsRead()
                    1 -> dismissConversation()
                    2 -> clearConversation()
                }
            }.show()
    }

    private fun clearConversation() {
        lifecycleScope.launch {
            val messages = messageDao.getMessagesForConversationSync(conversationId)
            messages.forEach { messageDao.updateStatus(it.id, MessageStatus.ARCHIVED) }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
