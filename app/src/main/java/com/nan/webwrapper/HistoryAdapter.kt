package com.nan.webwrapper

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nan.webwrapper.databinding.ItemHistoryBinding
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (HistoryEntry) -> Unit,
    private val onDelete: (HistoryEntry) -> Unit,
    private val onEdit: (HistoryEntry) -> Unit,
    private val onLongPress: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val allItems = mutableListOf<HistoryEntry>()
    private val visibleItems = mutableListOf<HistoryEntry>()
    private var currentQuery: String = ""
    private var currentTagFilter: String? = null

    fun setItems(items: List<HistoryEntry>) {
        allItems.clear()
        allItems.addAll(items)
        applyFilters()
    }

    fun filter(query: String) {
        currentQuery = query
        applyFilters()
    }

    fun setTagFilter(tag: String?) {
        currentTagFilter = tag?.trim()?.takeIf { it.isNotBlank() }
        applyFilters()
    }

    private fun applyFilters() {
        val text = currentQuery.trim().lowercase(Locale.getDefault())
        val tag = currentTagFilter?.lowercase(Locale.getDefault())

        visibleItems.clear()
        visibleItems.addAll(
            allItems.filter { entry ->
                val matchesText = text.isEmpty() || (entry.url.lowercase(Locale.getDefault()).contains(text)) ||
                    (entry.customName?.lowercase(Locale.getDefault())?.contains(text) == true)

                val entryTag = entry.tag?.lowercase(Locale.getDefault())
                val matchesTag = tag == null || entryTag == tag

                matchesText && matchesTag
            }
        )
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Set up circular outline for logo ImageView
        binding.logoImage.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        binding.logoImage.clipToOutline = true
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(visibleItems[position])
    }

    override fun getItemCount(): Int = visibleItems.size

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: HistoryEntry) {
            // Display custom name if available, otherwise show URL
            binding.urlText.text = entry.customName ?: entry.url
            binding.timeText.text = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
            ).format(Date(entry.timestamp))

            // Load and display logo if available
            binding.logoImage.visibility = if (entry.logoPath != null) {
                val logoFile = File(entry.logoPath)
                if (logoFile.exists()) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                        binding.logoImage.setImageBitmap(bitmap)
                        android.view.View.VISIBLE
                    } catch (e: Exception) {
                        android.view.View.GONE
                    }
                } else {
                    android.view.View.GONE
                }
            } else {
                android.view.View.GONE
            }

            binding.root.setOnClickListener { onClick(entry) }
            binding.root.setOnLongClickListener {
                onLongPress(entry)
                true
            }
            binding.editButton.setOnClickListener { onEdit(entry) }
            binding.deleteButton.setOnClickListener { onDelete(entry) }
        }
    }
}

