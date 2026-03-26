package com.whatsapp.selectivereads.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.ConversationEntity
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ActivityConversationDetailBinding
import com.whatsapp.selectivereads.service.ReplyHelper
import com.whatsapp.selectivereads.service.WhatsAppNotificationService
import com.whatsapp.selectivereads.ui.adapter.MessageBubbleAdapter
import kotlinx.coroutines.launch

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var bubbleAdapter: MessageBubbleAdapter
    private val db by lazy { WhatsAppSelectiveReadsApp.instance.database }
    private val messageDao by lazy { db.messageDao() }
    private val conversationDao by lazy { db.conversationDao() }

    private var conversationId: String = ""
    private var conversation: ConversationEntity? = null

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
        setupReplyBar()
        setupActionButtons()
        observeConversation()
        observeMessages()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        bubbleAdapter = MessageBubbleAdapter(
            onMediaClick = { message -> openMediaViewer(message) }
        )

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ConversationDetailActivity).apply {
                stackFromEnd = true
            }
            adapter = bubbleAdapter
        }
    }

    private fun setupReplyBar() {
        binding.replySendButton.setOnClickListener { sendReply() }

        binding.replyInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendReply()
                true
            } else {
                false
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnMarkRead.setOnClickListener {
            markConversationAsRead()
        }

        binding.btnDismiss.setOnClickListener {
            dismissConversation()
        }

        binding.btnOpenWhatsApp.setOnClickListener {
            openWhatsAppChat()
        }
    }

    private fun observeConversation() {
        conversationDao.getByIdLive(conversationId).observe(this) { conv ->
            conversation = conv ?: return@observe

            supportActionBar?.title = conv.chatTitle
            binding.chatTypeIcon.setImageResource(
                if (conv.isGroupChat) com.whatsapp.selectivereads.R.drawable.ic_group
                else com.whatsapp.selectivereads.R.drawable.ic_person
            )

            val isPending = conv.status == MessageStatus.PENDING
            binding.actionBar.visibility = if (isPending) View.VISIBLE else View.GONE

            if (conv.hasReplyAction) {
                binding.replyBar.visibility = View.VISIBLE
            } else {
                binding.replyBar.visibility = View.VISIBLE
                binding.replyInput.hint = "Reply not available for this notification"
                binding.replySendButton.isEnabled = false
                binding.replySendButton.alpha = 0.4f
            }

            binding.statusText.text = when (conv.status) {
                MessageStatus.PENDING -> "Pending - read receipt not sent"
                MessageStatus.READ_SENT -> "Read receipt sent"
                MessageStatus.REPLIED -> "Replied"
                MessageStatus.DISMISSED -> "Dismissed"
                MessageStatus.ARCHIVED -> "Archived"
            }
        }
    }

    private fun observeMessages() {
        messageDao.getMessagesForConversation(conversationId).observe(this) { messages ->
            bubbleAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun sendReply() {
        val text = binding.replyInput.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        val conv = conversation ?: return

        if (!conv.hasReplyAction) {
            Snackbar.make(binding.root, "Reply not available for this notification", Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.replySendButton.isEnabled = false
        binding.replyProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val service = getSystemService(NOTIFICATION_SERVICE) as? WhatsAppNotificationService
                if (service != null) {
                    val success = ReplyHelper.sendReply(
                        service = service,
                        notificationKey = conv.notificationKey,
                        conversationId = conversationId,
                        replyText = text,
                        remoteInputResultKey = conv.remoteInputResultKey,
                        replyActionIndex = conv.replyActionIndex
                    )

                    if (success) {
                        val replyMsg = Message(
                            notificationKey = "${conv.notificationKey}:reply:${System.currentTimeMillis()}",
                            packageName = conv.packageName,
                            conversationId = conversationId,
                            senderName = "You",
                            messageText = "You replied: $text",
                            chatKey = conversationId.hashCode().toString(),
                            isGroupChat = conv.isGroupChat,
                            timestamp = System.currentTimeMillis(),
                            status = MessageStatus.REPLIED
                        )
                        messageDao.insert(replyMsg)

                        binding.replyInput.text?.clear()
                        hideKeyboard()
                        Snackbar.make(binding.root, "Reply sent (read receipt OFF)", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(binding.root, "Failed to send reply - notification may have expired", Snackbar.LENGTH_LONG).show()
                    }
                } else {
                    Snackbar.make(binding.root, "Notification service not available", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.replySendButton.isEnabled = true
                binding.replyProgressBar.visibility = View.GONE
            }
        }
    }

    private fun markConversationAsRead() {
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversationId, MessageStatus.READ_SENT)
            conversationDao.updateStatus(conversationId, MessageStatus.READ_SENT)
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

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
