package com.nan.webwrapper

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.nan.webwrapper.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: HistoryRepository
    private lateinit var faviconFetcher: FaviconFetcher
    private val adapter = HistoryAdapter(
        onClick = { openWebView(it.url) },
        onDelete = { entry ->
            repository.deleteEntry(entry)
            loadHistory()
        },
        onEdit = { entry ->
            showEditDialog(entry)
        },
        onLongPress = { entry ->
            showChangeTagDialog(entry)
        }
    )

    private fun getKnownTags(history: List<HistoryEntry> = repository.getHistory()): List<String> {
        return history.mapNotNull { it.tag?.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
    }

    private fun showChangeTagDialog(entry: HistoryEntry) {
        val tags = getKnownTags()
        val options = ArrayList<String>(tags.size + 2).apply {
            add(getString(R.string.tag_none))
            addAll(tags)
            add(getString(R.string.new_tag))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_tag)
            .setItems(options.toTypedArray()) { _, which ->
                when (val selected = options[which]) {
                    getString(R.string.tag_none) -> {
                        repository.updateEntry(entry, entry.customName, entry.logoPath, "")
                        loadHistory()
                    }
                    getString(R.string.new_tag) -> {
                        showNewTagDialog(entry)
                    }
                    else -> {
                        repository.updateEntry(entry, entry.customName, entry.logoPath, selected)
                        loadHistory()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewTagDialog(entry: HistoryEntry) {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(entry.tag.orEmpty())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_tag)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newTag = input.text?.toString().orEmpty().trim()
                repository.updateEntry(entry, entry.customName, entry.logoPath, newTag)
                loadHistory()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateTagChips(history: List<HistoryEntry>) {
        val tags = history.mapNotNull { it.tag?.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }

        val chipGroup = binding.tagChipGroup
        val currentSelection = chipGroup.checkedChipId
        chipGroup.removeAllViews()

        fun addChip(label: String, tagValue: String?) {
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isCheckedIconVisible = false
            }
            chip.setOnCheckedChangeListener { button, isChecked ->
                if (!isChecked) return@setOnCheckedChangeListener
                // Ensure single selection behavior
                for (i in 0 until chipGroup.childCount) {
                    val child = chipGroup.getChildAt(i)
                    if (child is Chip && child != button) {
                        child.isChecked = false
                    }
                }

                adapter.setTagFilter(tagValue)
                adapter.filter(binding.searchEditText.text?.toString().orEmpty())
                binding.emptyView.isVisible = adapter.itemCount == 0
            }
            chipGroup.addView(chip)
        }

        addChip(getString(R.string.tag_all), null)
        tags.forEach { tag ->
            addChip(tag, tag)
        }

        // Restore selection if possible; otherwise default to All.
        val restored = (0 until chipGroup.childCount)
            .mapNotNull { chipGroup.getChildAt(it) as? Chip }
            .firstOrNull { it.id == currentSelection }
        if (restored == null) {
            (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
        }
    }

    private val addWebsiteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        loadHistory()
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            currentEditingDialogLogoUri = it
            currentEditingDialogImageView?.let { imageView ->
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = android.view.View.VISIBLE
                } catch (e: Exception) {
                    Snackbar.make(binding.root, R.string.error_loading_image, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var currentEditingEntry: HistoryEntry? = null
    private var currentEditingDialogLogoUri: Uri? = null
    private var currentEditingDialogImageView: ImageView? = null

    private lateinit var patternManager: PatternLockManager
    private val patternLockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // Pattern verification failed or cancelled - exit app
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        patternManager = PatternLockManager(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = HistoryRepository(this)
        faviconFetcher = FaviconFetcher(this)
        
        // Check if pattern lock is enabled and verify pattern
        checkPatternLock()

        setupToolbar()
        setupList()
        setupSearch()
        setupTagFilter()
        setupFab()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
        if (adapter.itemCount == 0) {
            navigateToAddWebsite()
        }
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.action_clear -> {
                    repository.clear()
                    loadHistory()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupList() {
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
                binding.emptyView.isVisible = adapter.itemCount == 0
            }
        })
    }

    private fun setupTagFilter() {
        // Default selection: All
        adapter.setTagFilter(null)
    }

    private fun setupFab() {
        binding.addFab.setOnClickListener { navigateToAddWebsite() }
        binding.githubButton.setOnClickListener { openGitHub() }
    }

    private fun checkPatternLock() {
        if (patternManager.isPatternLockEnabled() && patternManager.hasPattern()) {
            // Show pattern lock screen
            val intent = Intent(this, PatternLockActivity::class.java).apply {
                putExtra(PatternLockActivity.EXTRA_MODE, PatternLockActivity.MODE_VERIFY)
            }
            patternLockLauncher.launch(intent)
        }
    }

    private fun openGitHub() {
        val url = "https://github.com/itznan"
        openWebView(url)
    }

    private fun navigateToAddWebsite() {
        val intent = Intent(this, AddWebsiteActivity::class.java)
        addWebsiteLauncher.launch(intent)
    }

    private fun loadHistory() {
        val history = repository.getHistory()
        adapter.setItems(history)
        binding.emptyView.isVisible = history.isEmpty()
        updateTagChips(history)
    }

    private fun openWebView(url: String) {
        if (url.isBlank()) {
            Snackbar.make(binding.root, R.string.error_invalid_url, Snackbar.LENGTH_SHORT).show()
            return
        }
        
        // Use native URL validation and normalization for better performance
        val normalizedUrl = if (NativeHelper.isNativeLibraryLoaded()) {
            NativeHelper.validateAndNormalizeUrl(url)
        } else {
            // Fallback to simple validation
            val trimmed = url.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
        
        if (normalizedUrl == null) {
            Snackbar.make(binding.root, R.string.error_invalid_url, Snackbar.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_URL, normalizedUrl)
        }
        startActivity(intent)
    }

    private fun showEditDialog(entry: HistoryEntry) {
        currentEditingEntry = entry
        currentEditingDialogLogoUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_history, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.nameEditText)
        val tagEditText = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tagEditText)
        val logoImageView = dialogView.findViewById<ImageView>(R.id.logoImageView)
        val pickLogoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.pickLogoButton)
        
        currentEditingDialogImageView = logoImageView
        
        nameEditText.setText(entry.customName ?: entry.url)
        tagEditText.setText(entry.tag.orEmpty())

        val tagAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            getKnownTags()
        )
        tagEditText.setAdapter(tagAdapter)
        
        // Load existing logo if available
        entry.logoPath?.let { path ->
            val logoFile = File(path)
            if (logoFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                    logoImageView.setImageBitmap(bitmap)
                    logoImageView.visibility = android.view.View.VISIBLE
                } catch (e: Exception) {
                    logoImageView.visibility = android.view.View.GONE
                }
            }
        }

        logoImageView.setOnClickListener {
            pickImage()
        }
        
        pickLogoButton.setOnClickListener {
            pickImage()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_history)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = nameEditText.text.toString().trim()
                val newTag = tagEditText.text?.toString().orEmpty().trim()
                val logoPath = if (currentEditingDialogLogoUri != null) {
                    // Save new logo
                    currentEditingDialogLogoUri?.let { uri ->
                        saveLogoFromUri(uri, entry)
                    } ?: entry.logoPath
                } else {
                    entry.logoPath // Keep existing logo path
                }
                
                // Update entry with new name and logo path
                repository.updateEntry(entry, newName.takeIf { it.isNotEmpty() && it != entry.url }, logoPath, newTag)
                loadHistory()
                currentEditingEntry = null
                currentEditingDialogLogoUri = null
                currentEditingDialogImageView = null
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                currentEditingEntry = null
                currentEditingDialogLogoUri = null
                currentEditingDialogImageView = null
            }
            .setNeutralButton(R.string.remove_logo) { _, _ ->
                repository.updateEntry(entry, entry.customName, null, entry.tag)
                loadHistory()
                currentEditingEntry = null
                currentEditingDialogLogoUri = null
                currentEditingDialogImageView = null
            }
            .show()
    }

    private fun pickImage() {
        imagePickerLauncher.launch("image/*")
    }

    private fun saveLogoFromUri(uri: Uri, entry: HistoryEntry): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Snackbar.make(binding.root, R.string.error_loading_image, Snackbar.LENGTH_SHORT).show()
                return null
            }

            // Optimize logo size (max 256x256 for performance)
            val maxSize = 256
            val scaledBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                val scale = minOf(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
                val newWidth = (originalBitmap.width * scale).toInt()
                val newHeight = (originalBitmap.height * scale).toInt()
                
                // Use native resize if available for better performance
                if (NativeHelper.isNativeLibraryLoaded()) {
                    val resizedBitmap = Bitmap.createBitmap(newWidth, newHeight, originalBitmap.config)
                    if (NativeHelper.resizeImageNative(originalBitmap, resizedBitmap)) {
                        originalBitmap.recycle()
                        resizedBitmap
                    } else {
                        // Fallback to Android resize
                        Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true).also {
                            originalBitmap.recycle()
                        }
                    }
                } else {
                    // Fallback to Android resize
                    Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true).also {
                        originalBitmap.recycle()
                    }
                }
            } else {
                originalBitmap
            }

            // Save bitmap to internal storage
            val logoDir = File(filesDir, "history_logos")
            if (!logoDir.exists()) {
                logoDir.mkdirs()
            }
            
            val logoFile = File(logoDir, "${entry.url.hashCode()}_${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(logoFile)
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 85, outputStream)
            outputStream.flush()
            outputStream.close()
            scaledBitmap.recycle()

            // Delete old logo if exists
            entry.logoPath?.let { oldPath ->
                File(oldPath).delete()
            }

            logoFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error saving logo", e)
            Snackbar.make(binding.root, R.string.error_loading_image, Snackbar.LENGTH_SHORT).show()
            null
        }
    }
}

