package com.nan.webwrapper

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.nan.webwrapper.databinding.ActivityPatternLockBinding

class PatternLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatternLockBinding
    private lateinit var patternManager: PatternLockManager
    private var isSettingPattern = false
    private var firstPattern: List<Int>? = null
    private var isVerifying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatternLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patternManager = PatternLockManager(this)
        
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VERIFY
        isSettingPattern = mode == MODE_SET
        isVerifying = mode == MODE_VERIFY

        setupButtons()
        setupPatternLock()
        updateInstructions()
    }

    private fun setupButtons() {
        binding.cancelButton.visibility = if (isVerifying) android.view.View.VISIBLE else android.view.View.GONE
        binding.cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun setupPatternLock() {
        binding.patternLockView.onPatternComplete = { pattern ->
            if (pattern.size < 4) {
                setMessage(R.string.pattern_too_short, isError = true)
                binding.patternLockView.reset()
            } else {
                if (isSettingPattern) {
                    handleSetPattern(pattern)
                } else {
                    handleVerifyPattern(pattern)
                }
            }
        }
    }

    private fun handleSetPattern(pattern: List<Int>) {
        if (firstPattern == null) {
            // First pattern entry
            firstPattern = pattern
            setMessage(R.string.pattern_confirm, isError = false)
            binding.patternLockView.reset()
        } else {
            // Confirm pattern
            if (pattern == firstPattern) {
                patternManager.savePattern(pattern)
                setMessage(R.string.pattern_saved, isError = false)
                setResult(RESULT_OK)
                finish()
            } else {
                setMessage(R.string.pattern_mismatch, isError = true)
                firstPattern = null
                binding.patternLockView.reset()
            }
        }
    }

    private fun handleVerifyPattern(pattern: List<Int>) {
        val savedPattern = patternManager.getPattern()
        if (savedPattern != null && pattern == savedPattern) {
            setMessage(R.string.pattern_correct, isError = false)
            setResult(RESULT_OK)
            finish()
        } else {
            setMessage(R.string.pattern_incorrect, isError = true)
            binding.patternLockView.reset()
        }
    }

    private fun updateInstructions() {
        when {
            isSettingPattern && firstPattern == null -> {
                setMessage(R.string.draw_pattern, isError = false)
                binding.helperText.text = getString(R.string.pattern_helper_min_dots)
            }
            isSettingPattern && firstPattern != null -> {
                setMessage(R.string.pattern_confirm, isError = false)
                binding.helperText.text = getString(R.string.pattern_helper_confirm)
            }
            isVerifying -> {
                setMessage(R.string.enter_pattern, isError = false)
                binding.helperText.text = getString(R.string.pattern_helper_verify)
            }
        }
    }

    private fun setMessage(messageResId: Int, isError: Boolean) {
        binding.messageText.text = getString(messageResId)
        val colorAttr = if (isError) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOnSurface
        binding.messageText.setTextColor(MaterialColors.getColor(binding.messageText, colorAttr))
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_SET = "set"
        const val MODE_VERIFY = "verify"
    }
}

