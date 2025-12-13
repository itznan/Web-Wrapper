package com.nan.webwrapper

import android.content.Context

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getIgnoreNotchForWebsite(url: String): Boolean? = getBooleanForWebsite(url, KEY_IGNORE_NOTCH_PREFIX)
    fun setIgnoreNotchForWebsite(url: String, ignore: Boolean) = setBooleanForWebsite(url, KEY_IGNORE_NOTCH_PREFIX, ignore)

    fun getHardwareAccelForWebsite(url: String): Boolean? = getBooleanForWebsite(url, KEY_HW_ACCEL_PREFIX)
    fun setHardwareAccelForWebsite(url: String, enabled: Boolean) = setBooleanForWebsite(url, KEY_HW_ACCEL_PREFIX, enabled)

    fun getHighRefreshForWebsite(url: String): Boolean? = getBooleanForWebsite(url, KEY_HIGH_REFRESH_PREFIX)
    fun setHighRefreshForWebsite(url: String, enabled: Boolean) = setBooleanForWebsite(url, KEY_HIGH_REFRESH_PREFIX, enabled)

    fun getDefaultIgnoreNotch(): Boolean {
        return prefs.getBoolean(KEY_DEFAULT_IGNORE_NOTCH, false)
    }

    fun setDefaultIgnoreNotch(ignore: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_IGNORE_NOTCH, ignore).apply()
    }

    fun getDefaultHardwareAccel(): Boolean {
        return prefs.getBoolean(KEY_DEFAULT_HW_ACCEL, true)
    }

    fun setDefaultHardwareAccel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_HW_ACCEL, enabled).apply()
    }

    fun getDefaultHighRefresh(): Boolean {
        return prefs.getBoolean(KEY_DEFAULT_HIGH_REFRESH, true)
    }

    fun setDefaultHighRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_HIGH_REFRESH, enabled).apply()
    }

    fun shouldIgnoreNotch(url: String): Boolean {
        val websiteSetting = getIgnoreNotchForWebsite(url)
        return websiteSetting ?: getDefaultIgnoreNotch()
    }

    fun shouldUseHardwareAccel(url: String): Boolean {
        val websiteSetting = getHardwareAccelForWebsite(url)
        return websiteSetting ?: getDefaultHardwareAccel()
    }

    fun shouldUseHighRefresh(url: String): Boolean {
        val websiteSetting = getHighRefreshForWebsite(url)
        return websiteSetting ?: getDefaultHighRefresh()
    }

    fun removeWebsiteSetting(url: String) {
        val notchKey = getWebsiteKey(url, KEY_IGNORE_NOTCH_PREFIX)
        val hwKey = getWebsiteKey(url, KEY_HW_ACCEL_PREFIX)
        val refreshKey = getWebsiteKey(url, KEY_HIGH_REFRESH_PREFIX)
        prefs.edit()
            .remove(notchKey)
            .remove(hwKey)
            .remove(refreshKey)
            .apply()
    }

    private fun getBooleanForWebsite(url: String, prefix: String): Boolean? {
        val key = getWebsiteKey(url, prefix)
        if (!prefs.contains(key)) return null
        return prefs.getBoolean(key, false)
    }

    private fun setBooleanForWebsite(url: String, prefix: String, value: Boolean) {
        val key = getWebsiteKey(url, prefix)
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun getWebsiteKey(url: String, prefix: String): String {
        return "${prefix}_${url.hashCode()}"
    }

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val KEY_DEFAULT_IGNORE_NOTCH = "default_ignore_notch"
        private const val KEY_DEFAULT_HW_ACCEL = "default_hw_accel"
        private const val KEY_DEFAULT_HIGH_REFRESH = "default_high_refresh"
        private const val KEY_IGNORE_NOTCH_PREFIX = "ignore_notch"
        private const val KEY_HW_ACCEL_PREFIX = "hw_accel"
        private const val KEY_HIGH_REFRESH_PREFIX = "high_refresh"
    }
}

