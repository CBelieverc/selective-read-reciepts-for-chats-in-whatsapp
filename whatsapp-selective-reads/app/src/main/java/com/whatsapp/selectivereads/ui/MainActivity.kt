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
import com.whatsapp.selectivereads.data.Message
import com.whatsapp.selectivereads.data.MessageStatus
import com.whatsapp.selectivereads.databinding.ActivityMainBinding
import com.whatsapp.selectivereads.service.PreferencesManager
import com.whatsapp.selectivereads.service.WhatsAppNotificationService
import com.whatsapp.selectivereads.ui.adapter.MessageAdapter
import com.whatsapp.selectivereads.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var prefs: PreferencesManager
    private val dao by lazy { WhatsAppSelectiveReadsApp.instance.database.messageDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        setupTabs()
        setupFab()
        observeMessages()
        checkNotificationAccess()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            onMarkRead = { message -> markAsRead(message) },
            onDismiss = { message -> dismissMessage(message) },
            onOpenChat = { message -> openWhatsAppChat(message) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            observeMessages()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> observePendingMessages()
                    1 -> observeAllChats()
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
                .setMessage("This will send read receipts for all pending messages.")
                .setPositiveButton("Mark All Read") { _, _ -> markAllAsRead() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeMessages() {
        when (binding.tabLayout.selectedTabPosition) {
            0 -> observePendingMessages()
            1 -> observeAllChats()
        }
    }

    private fun observePendingMessages() {
        dao.getPendingMessages().observe(this) { messages ->
            adapter.submitList(messages)
            updateEmptyState(messages)
        }
    }

    private fun observeAllChats() {
        dao.getRecentHistory().observe(this) { messages ->
            adapter.submitList(messages)
            updateEmptyState(messages)
        }
    }

    private fun updateEmptyState(messages: List<Message>?) {
        if (messages.isNullOrEmpty()) {
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

    private fun markAsRead(message: Message) {
        lifecycleScope.launch {
            dao.updateStatus(message.id, MessageStatus.READ_SENT)
            openWhatsAppChat(message)
            Snackbar.make(binding.root, "Read receipt sent to ${message.senderName}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun dismissMessage(message: Message) {
        lifecycleScope.launch {
            dao.updateStatus(message.id, MessageStatus.DISMISSED)
            Snackbar.make(binding.root, "Dismissed - no read receipt sent", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppChat(message: Message) {
        lifecycleScope.launch {
            dao.updateStatus(message.id, MessageStatus.READ_SENT)
        }

        val intent = packageManager.getLaunchIntentForPackage(message.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Snackbar.make(binding.root, "WhatsApp not installed", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun markAllAsRead() {
        lifecycleScope.launch {
            val pending = dao.getPendingMessagesSync()
            pending.forEach { message ->
                dao.updateStatus(message.id, MessageStatus.READ_SENT)
            }
            Snackbar.make(binding.root, "${pending.size} messages marked as read", Snackbar.LENGTH_SHORT).show()
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
                    dao.clearHistory()
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
