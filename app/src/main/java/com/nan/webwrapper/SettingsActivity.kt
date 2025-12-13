package com.nan.webwrapper

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.nan.webwrapper.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var historyRepository: HistoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cacheManager: CacheManager
    private lateinit var adapter: SettingsAdapter
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyRepository = HistoryRepository(this)
        settingsRepository = SettingsRepository(this)
        cacheManager = CacheManager(this)

        setupToolbar()
        setupRecyclerView()
        setupDefaultNotchToggle()
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
}

