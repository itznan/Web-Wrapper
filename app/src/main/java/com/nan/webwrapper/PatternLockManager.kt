package com.nan.webwrapper

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class PatternLockManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun savePattern(pattern: List<Int>) {
        val patternString = pattern.joinToString(",")
        prefs.edit().putString(KEY_PATTERN, patternString).apply()
    }
    
    fun getPattern(): List<Int>? {
        val patternString = prefs.getString(KEY_PATTERN, null) ?: return null
        return try {
            patternString.split(",").map { it.toInt() }
        } catch (e: Exception) {
            null
        }
    }
    
    fun hasPattern(): Boolean {
        return getPattern() != null
    }
    
    fun clearPattern() {
        prefs.edit().remove(KEY_PATTERN).apply()
    }
    
    fun isPatternLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_PATTERN_LOCK_ENABLED, false)
    }
    
    fun setPatternLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PATTERN_LOCK_ENABLED, enabled).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "pattern_lock_prefs"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_PATTERN_LOCK_ENABLED = "pattern_lock_enabled"
    }
}

