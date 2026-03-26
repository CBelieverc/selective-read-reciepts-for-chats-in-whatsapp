package com.whatsapp.selectivereads.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.whatsapp.selectivereads.WhatsAppSelectiveReadsApp
import com.whatsapp.selectivereads.databinding.ActivitySettingsBinding
import com.whatsapp.selectivereads.service.PreferencesManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        setupSettings()
    }

    private fun setupSettings() {
        binding.switchServiceEnabled.isChecked = prefs.isEnabled()
        binding.switchServiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.setEnabled(isChecked)
        }

        binding.switchInterceptGroups.isChecked = prefs.shouldInterceptGroups()
        binding.switchInterceptGroups.setOnCheckedChangeListener { _, isChecked ->
            prefs.setInterceptGroups(isChecked)
        }

        binding.switchAutoDismiss.isChecked = prefs.shouldAutoDismiss()
        binding.switchAutoDismiss.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAutoDismiss(isChecked)
        }

        binding.btnNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                WhatsAppSelectiveReadsApp.instance.database.messageDao().clearHistory()
            }
        }

        when (prefs.getRetentionDays()) {
            3 -> binding.radio3Days.isChecked = true
            7 -> binding.radio7Days.isChecked = true
            14 -> binding.radio14Days.isChecked = true
            30 -> binding.radio30Days.isChecked = true
        }

        binding.radioGroupRetention.setOnCheckedChangeListener { _, checkedId ->
            val days = when (checkedId) {
                com.whatsapp.selectivereads.R.id.radio_3_days -> 3
                com.whatsapp.selectivereads.R.id.radio_7_days -> 7
                com.whatsapp.selectivereads.R.id.radio_14_days -> 14
                com.whatsapp.selectivereads.R.id.radio_30_days -> 30
                else -> 7
            }
            prefs.setRetentionDays(days)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
