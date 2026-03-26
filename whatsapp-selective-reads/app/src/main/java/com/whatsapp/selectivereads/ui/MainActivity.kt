package com.whatsapp.selectivereads.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.whatsapp.selectivereads.R
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.data.ConversationEntity
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ActivityMainBinding
import com.whatsapp.selectivereads.service.PreferencesManager
import com.whatsapp.selectivereads.service.WhatsAppNotificationService
import com.whatsapp.selectivereads.ui.adapter.ConversationAdapter
import com.whatsapp.selectivereads.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var prefs: PreferencesManager
    private val db by lazy { WhatsAppSelectiveReadsApp.instance.database }
    private val conversationDao by lazy { db.conversationDao() }
    private val messageDao by lazy { db.messageDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        setupTabs()
        setupFab()
        observeConversations()
        checkNotificationAccess()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupRecyclerView() {
        conversationAdapter = ConversationAdapter(
            onClick = { conversation -> openConversation(conversation) },
            onMarkRead = { conversation -> markConversationAsRead(conversation) },
            onDismiss = { conversation -> dismissConversation(conversation) },
            onReply = { conversation -> openConversationForReply(conversation) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.conversationAdapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            observeConversations()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> observePendingConversations()
                    1 -> observeAllConversations()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFab() {
        binding.fabMarkAllRead.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Mark all as read?")
                .setMessage("This will send read receipts for all pending conversations.")
                .setPositiveButton("Mark All Read") { _, _ -> markAllAsRead() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeConversations() {
        when (binding.tabLayout.selectedTabPosition) {
            0 -> observePendingConversations()
            1 -> observeAllConversations()
        }
    }

    private fun observePendingConversations() {
        conversationDao.getPendingConversations().observe(this) { conversations ->
            conversationAdapter.submitList(conversations)
            updateEmptyState(conversations)
        }
    }

    private fun observeAllConversations() {
        conversationDao.getAllConversations().observe(this) { conversations ->
            conversationAdapter.submitList(conversations)
            updateEmptyState(conversations)
        }
    }

    private fun updateEmptyState(conversations: List<ConversationEntity>?) {
        if (conversations.isNullOrEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.emptyText.text = if (binding.tabLayout.selectedTabPosition == 0) {
                getString(R.string.no_pending_messages)
            } else {
                getString(R.string.no_message_history)
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openConversation(conversation: ConversationEntity) {
        val intent = Intent(this, ConversationDetailActivity::class.java).apply {
            putExtra(ConversationDetailActivity.EXTRA_CONVERSATION_ID, conversation.id)
        }
        startActivity(intent)
    }

    private fun openConversationForReply(conversation: ConversationEntity) {
        openConversation(conversation)
    }

    private fun markConversationAsRead(conversation: ConversationEntity) {
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversation.id, MessageStatus.READ_SENT)
            conversationDao.updateStatus(conversation.id, MessageStatus.READ_SENT)
            openWhatsAppChat(conversation)
            Snackbar.make(binding.root, "Read receipt sent to ${conversation.chatTitle}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun dismissConversation(conversation: ConversationEntity) {
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversation.id, MessageStatus.DISMISSED)
            conversationDao.updateStatus(conversation.id, MessageStatus.DISMISSED)
            Snackbar.make(binding.root, "Dismissed - no read receipt sent", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppChat(conversation: ConversationEntity) {
        lifecycleScope.launch {
            messageDao.updateStatusByConversation(conversation.id, MessageStatus.READ_SENT)
            conversationDao.updateStatus(conversation.id, MessageStatus.READ_SENT)
        }

        val intent = packageManager.getLaunchIntentForPackage(conversation.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Snackbar.make(binding.root, "WhatsApp not installed", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun markAllAsRead() {
        lifecycleScope.launch {
            val pending = conversationDao.getPendingConversationsSync()
            pending.forEach { conversation ->
                messageDao.updateStatusByConversation(conversation.id, MessageStatus.READ_SENT)
                conversationDao.updateStatus(conversation.id, MessageStatus.READ_SENT)
            }
            Snackbar.make(binding.root, "${pending.size} conversations marked as read", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationAccess() {
        if (!isNotificationServiceEnabled()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Notification Access Required")
                .setMessage("This app needs notification access to intercept WhatsApp messages. Please enable it in Settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, WhatsAppNotificationService::class.java)
        return flat?.contains(componentName.flattenToString()) == true
    }

    private fun updateServiceStatus() {
        val enabled = isNotificationServiceEnabled() && prefs.isEnabled()
        binding.serviceStatus.text = if (enabled) {
            getString(R.string.service_active)
        } else {
            getString(R.string.service_inactive)
        }
        binding.serviceStatus.setTextColor(
            if (enabled) getColor(R.color.status_active) else getColor(R.color.status_inactive)
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_history -> {
                lifecycleScope.launch {
                    conversationDao.clearHistory()
                    messageDao.clearHistory()
                    Snackbar.make(binding.root, "History cleared", Snackbar.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_notification_settings -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
