package com.nan.webwrapper

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nan.webwrapper.databinding.ItemWebsiteSettingsBinding

class SettingsAdapter(
    private val cacheManager: CacheManager,
    private val settingsRepository: SettingsRepository,
    private val onClearCache: (String) -> Unit,
    private val onToggleNotch: (String, Boolean) -> Unit,
    private val onToggleHardware: (String, Boolean) -> Unit,
    private val onToggleRefresh: (String, Boolean) -> Unit
) : ListAdapter<WebsiteSettingsItem, SettingsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWebsiteSettingsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemWebsiteSettingsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WebsiteSettingsItem) {
            binding.websiteUrl.text = item.url
            binding.cacheSize.text = cacheManager.formatSize(item.cacheSize)
            binding.ignoreNotchSwitch.isChecked = item.ignoreNotch
            binding.hardwareAccelSwitch.isChecked = item.hardwareAccel
            binding.highRefreshSwitch.isChecked = item.highRefresh

            binding.clearCacheButton.setOnClickListener {
                onClearCache(item.url)
            }

            binding.ignoreNotchSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleNotch(item.url, isChecked)
            }

            binding.hardwareAccelSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleHardware(item.url, isChecked)
            }

            binding.highRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleRefresh(item.url, isChecked)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WebsiteSettingsItem>() {
        override fun areItemsTheSame(
            oldItem: WebsiteSettingsItem,
            newItem: WebsiteSettingsItem
        ): Boolean = oldItem.url == newItem.url

        override fun areContentsTheSame(
            oldItem: WebsiteSettingsItem,
            newItem: WebsiteSettingsItem
        ): Boolean = oldItem == newItem
    }
}

