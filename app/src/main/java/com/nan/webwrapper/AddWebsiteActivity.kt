package com.nan.webwrapper

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.nan.webwrapper.databinding.ActivityAddWebsiteBinding
import com.google.android.material.snackbar.Snackbar

class AddWebsiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddWebsiteBinding
    private lateinit var repository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWebsiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = HistoryRepository(this)

        binding.topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.openButton.setOnClickListener { handleOpen() }
    }

    private fun handleOpen() {
        val urlInput = binding.urlEditText.text?.toString().orEmpty().trim()
        
        // Use native URL validation and normalization for better performance
        val validUrl = if (NativeHelper.isNativeLibraryLoaded()) {
            NativeHelper.validateAndNormalizeUrl(urlInput)
        } else {
            // Fallback to Java validation
            val normalized = normalizeUrl(urlInput)
            if (normalized != null && Patterns.WEB_URL.matcher(normalized).matches()) {
                normalized
            } else {
                null
            }
        }
        
        if (validUrl == null) {
            showError(getString(R.string.error_invalid_url))
            return
        }

        repository.addEntry(validUrl)
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_URL, validUrl)
        }
        startActivity(intent)
        setResult(RESULT_OK)
        finish()
    }

    private fun normalizeUrl(input: String): String? {
        if (input.isBlank()) return null
        return if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}

