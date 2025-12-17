package com.nan.webwrapper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.nan.webwrapper.databinding.ActivitySettingsBinding
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var historyRepository: HistoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cacheManager: CacheManager
    private lateinit var adapter: SettingsAdapter
    private lateinit var patternManager: PatternLockManager
    private val handler = Handler(Looper.getMainLooper())

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportToUri(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromUri(it) }
    }
    
    private val patternLockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            updatePatternLockUI()
        }
    }

    private fun setupBackup() {
        binding.exportButton.setOnClickListener {
            exportLauncher.launch("webwrapper-backup.json")
        }

        binding.importButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_data))
                .setMessage("Import will replace your current saved sites and settings. Continue?")
                .setPositiveButton(getString(R.string.import_data)) { _, _ ->
                    importLauncher.launch(arrayOf("application/json"))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun exportToUri(uri: Uri) {
        try {
            val backup = buildBackupJson()
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(backup.toString(2).toByteArray(Charsets.UTF_8))
                out.flush()
            }
            Snackbar.make(binding.root, getString(R.string.export_data), Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("Unable to read file")
            val obj = JSONObject(text)

            val version = obj.optInt("version", 0)
            if (version != 1) {
                throw IllegalArgumentException("Unsupported backup version: $version")
            }

            restoreBackupJson(obj)
            loadWebsiteSettings()
            updatePatternLockUI()
            Snackbar.make(binding.root, getString(R.string.import_data), Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Import failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun buildBackupJson(): JSONObject {
        val historyPrefs = getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val patternPrefs = getSharedPreferences(PATTERN_PREFS_NAME, Context.MODE_PRIVATE)

        val settingsAll = JSONObject()
        for ((key, value) in settingsPrefs.all) {
            when (value) {
                is Boolean -> settingsAll.put(key, value)
                is Int -> settingsAll.put(key, value)
                is Long -> settingsAll.put(key, value)
                is Float -> settingsAll.put(key, value.toDouble())
                is String -> settingsAll.put(key, value)
            }
        }

        val patternAll = JSONObject()
        for ((key, value) in patternPrefs.all) {
            when (value) {
                is Boolean -> patternAll.put(key, value)
                is String -> patternAll.put(key, value)
            }
        }

        val result = JSONObject()
        result.put("version", 1)
        result.put("exportedAt", System.currentTimeMillis())
        result.put("historyJson", historyPrefs.getString(HISTORY_KEY, "[]") ?: "[]")
        result.put("settingsPrefs", settingsAll)
        result.put("patternPrefs", patternAll)
        return result
    }

    private fun restoreBackupJson(obj: JSONObject) {
        val historyPrefs = getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val patternPrefs = getSharedPreferences(PATTERN_PREFS_NAME, Context.MODE_PRIVATE)

        val historyJson = obj.optString("historyJson", "[]")
        // Basic validation: must be a JSON array string
        JSONArray(historyJson)

        historyPrefs.edit().putString(HISTORY_KEY, historyJson).apply()

        val settingsObj = obj.optJSONObject("settingsPrefs") ?: JSONObject()
        settingsPrefs.edit().clear().apply()
        val settingsEditor = settingsPrefs.edit()
        val settingsKeys = settingsObj.keys()
        while (settingsKeys.hasNext()) {
            val key = settingsKeys.next()
            val value = settingsObj.get(key)
            when (value) {
                is Boolean -> settingsEditor.putBoolean(key, value)
                is Int -> settingsEditor.putInt(key, value)
                is Long -> settingsEditor.putLong(key, value)
                is Double -> settingsEditor.putFloat(key, value.toFloat())
                is String -> settingsEditor.putString(key, value)
            }
        }
        settingsEditor.apply()

        val patternObj = obj.optJSONObject("patternPrefs") ?: JSONObject()
        patternPrefs.edit().clear().apply()
        val patternEditor = patternPrefs.edit()
        val patternKeys = patternObj.keys()
        while (patternKeys.hasNext()) {
            val key = patternKeys.next()
            val value = patternObj.get(key)
            when (value) {
                is Boolean -> patternEditor.putBoolean(key, value)
                is String -> patternEditor.putString(key, value)
            }
        }
        patternEditor.apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyRepository = HistoryRepository(this)
        settingsRepository = SettingsRepository(this)
        cacheManager = CacheManager(this)
        patternManager = PatternLockManager(this)

        setupToolbar()
        setupRecyclerView()
        setupDefaultNotchToggle()
        setupBackup()
        setupPatternLock()
        loadWebsiteSettings()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = SettingsAdapter(
            cacheManager = cacheManager,
            settingsRepository = settingsRepository,
            onClearCache = { url ->
                clearCacheForWebsite(url)
            },
            onToggleNotch = { url, ignore ->
                settingsRepository.setIgnoreNotchForWebsite(url, ignore)
                loadWebsiteSettings()
            },
            onToggleHardware = { url, enabled ->
                settingsRepository.setHardwareAccelForWebsite(url, enabled)
                loadWebsiteSettings()
            },
            onToggleRefresh = { url, enabled ->
                settingsRepository.setHighRefreshForWebsite(url, enabled)
                loadWebsiteSettings()
            }
        )

        binding.websitesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.websitesRecyclerView.adapter = adapter
    }

    private fun setupDefaultNotchToggle() {
        binding.defaultIgnoreNotchSwitch.isChecked = settingsRepository.getDefaultIgnoreNotch()
        binding.defaultIgnoreNotchSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setDefaultIgnoreNotch(isChecked)
            Snackbar.make(
                binding.root,
                if (isChecked) "Default ignore notch enabled" else "Default ignore notch disabled",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        binding.defaultHardwareAccelSwitch.isChecked = settingsRepository.getDefaultHardwareAccel()
        binding.defaultHardwareAccelSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setDefaultHardwareAccel(isChecked)
            Snackbar.make(
                binding.root,
                if (isChecked) "Hardware acceleration enabled by default" else "Hardware acceleration disabled by default",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        binding.defaultHighRefreshSwitch.isChecked = settingsRepository.getDefaultHighRefresh()
        binding.defaultHighRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setDefaultHighRefresh(isChecked)
            Snackbar.make(
                binding.root,
                if (isChecked) "High refresh rate enabled by default" else "High refresh rate disabled by default",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupPatternLock() {
        updatePatternLockUI()
        
        binding.patternLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Enable pattern lock - need to set pattern first
                if (!patternManager.hasPattern()) {
                    // Open pattern setup
                    val intent = Intent(this, PatternLockActivity::class.java).apply {
                        putExtra(PatternLockActivity.EXTRA_MODE, PatternLockActivity.MODE_SET)
                    }
                    patternLockLauncher.launch(intent)
                } else {
                    patternManager.setPatternLockEnabled(true)
                    updatePatternLockUI()
                    Snackbar.make(binding.root, getString(R.string.pattern_lock) + " enabled", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                // Disable pattern lock
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.disable_pattern_lock))
                    .setMessage("Are you sure you want to disable pattern lock?")
                    .setPositiveButton("Disable") { _, _ ->
                        patternManager.setPatternLockEnabled(false)
                        updatePatternLockUI()
                        Snackbar.make(binding.root, getString(R.string.pattern_lock) + " disabled", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.patternLockSwitch.isChecked = true
                    }
                    .show()
            }
        }
        
        binding.patternLockButton.setOnClickListener {
            val intent = Intent(this, PatternLockActivity::class.java).apply {
                putExtra(PatternLockActivity.EXTRA_MODE, PatternLockActivity.MODE_SET)
            }
            patternLockLauncher.launch(intent)
        }
    }

    private fun updatePatternLockUI() {
        val isEnabled = patternManager.isPatternLockEnabled()
        val hasPattern = patternManager.hasPattern()
        
        binding.patternLockSwitch.isChecked = isEnabled
        binding.patternLockButton.visibility = if (isEnabled && hasPattern) {
            binding.patternLockButton.text = getString(R.string.change_pattern)
            android.view.View.VISIBLE
        } else if (isEnabled && !hasPattern) {
            binding.patternLockButton.text = getString(R.string.set_pattern)
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun loadWebsiteSettings() {
        val history = historyRepository.getHistory()
        val items = mutableListOf<WebsiteSettingsItem>()

        history.forEach { entry ->
            val ignoreNotch = settingsRepository.getIgnoreNotchForWebsite(entry.url)
                ?: settingsRepository.getDefaultIgnoreNotch()
            val hardware = settingsRepository.getHardwareAccelForWebsite(entry.url)
                ?: settingsRepository.getDefaultHardwareAccel()
            val highRefresh = settingsRepository.getHighRefreshForWebsite(entry.url)
                ?: settingsRepository.getDefaultHighRefresh()

            items.add(
                WebsiteSettingsItem(
                    url = entry.url,
                    cacheSize = 0L, // Will be updated asynchronously
                    ignoreNotch = ignoreNotch,
                    hardwareAccel = hardware,
                    highRefresh = highRefresh
                )
            )

            // Load cache size asynchronously
            cacheManager.getCacheSizeForWebsite(entry.url) { size ->
                val index = items.indexOfFirst { it.url == entry.url }
                if (index >= 0) {
                    items[index] = items[index].copy(cacheSize = size)
                    handler.post {
                        adapter.submitList(items.toList())
                    }
                }
            }
        }

        adapter.submitList(items)
    }

    private fun clearCacheForWebsite(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache")
            .setMessage("Clear cache for $url?")
            .setPositiveButton("Clear") { _, _ ->
                cacheManager.clearCacheForWebsite(url)
                Snackbar.make(
                    binding.root,
                    "Cache cleared for $url",
                    Snackbar.LENGTH_SHORT
                ).show()
                // Reload cache sizes
                loadWebsiteSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val HISTORY_PREFS_NAME = "history_prefs"
        private const val HISTORY_KEY = "history_list"
        private const val SETTINGS_PREFS_NAME = "settings_prefs"
        private const val PATTERN_PREFS_NAME = "pattern_lock_prefs"
    }
}

