package com.whatsapp.selectivereads.service

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("selective_reads_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun shouldInterceptGroups(): Boolean = prefs.getBoolean(KEY_INTERCEPT_GROUPS, true)

    fun setInterceptGroups(intercept: Boolean) {
        prefs.edit().putBoolean(KEY_INTERCEPT_GROUPS, intercept).apply()
    }

    fun shouldAutoDismiss(): Boolean = prefs.getBoolean(KEY_AUTO_DISMISS_ENABLED, false)

    fun setAutoDismiss(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DISMISS_ENABLED, enabled).apply()
    }

    fun getRetentionDays(): Int = prefs.getInt(KEY_RETENTION_DAYS, 7)

    fun setRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_RETENTION_DAYS, days).apply()
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    companion object {
        private const val KEY_ENABLED = "service_enabled"
        private const val KEY_INTERCEPT_GROUPS = "intercept_groups"
        private const val KEY_AUTO_DISMISS_ENABLED = "auto_dismiss_enabled"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
