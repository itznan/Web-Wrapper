package com.nan.webwrapper

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nan.webwrapper.databinding.ActivityWebviewBinding

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private lateinit var gestureDetector: TwoFingerPullDownDetector
    private lateinit var swipeHomeDetector: SwipeHomeGestureDetector
    private lateinit var settingsRepository: SettingsRepository
    private var currentUrl: String = ""
    private var hardwareAccelEnabled: Boolean = true
    private var highRefreshEnabled: Boolean = true
    
    // Fullscreen support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen: Boolean = false
    private var webChromeClient: WebChromeClient? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        currentUrl = url
        settingsRepository = SettingsRepository(this)
        gestureDetector = TwoFingerPullDownDetector(this) { binding.webView.reload() }
        swipeHomeDetector = SwipeHomeGestureDetector(this) {
            // Navigate back to home (MainActivity)
            finish()
        }

        resolveSettings()
        configureImmersiveMode()
        setupWebView()

        if (!url.isNullOrBlank()) {
            binding.webView.loadUrl(url)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    // Exit fullscreen first
                    exitFullscreen()
                } else if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun resolveSettings() {
        hardwareAccelEnabled = settingsRepository.shouldUseHardwareAccel(currentUrl)
        highRefreshEnabled = settingsRepository.shouldUseHighRefresh(currentUrl)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveMode()
        }
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Configure notch handling based on settings
        val ignoreNotch = if (currentUrl.isNotBlank()) {
            settingsRepository.shouldIgnoreNotch(currentUrl)
        } else {
            settingsRepository.getDefaultIgnoreNotch()
        }
        
        if (ignoreNotch) {
            // Extend into notch area - use LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val layoutParams = window.attributes
                layoutParams.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = layoutParams
            }
        } else {
            // Respect notch area - use LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val layoutParams = window.attributes
                layoutParams.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                window.attributes = layoutParams
            }
        }
        
        val controller = ViewCompat.getWindowInsetsController(window.decorView)
            ?: WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        // Hardware acceleration preference
        if (hardwareAccelEnabled) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            binding.webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        } else {
            binding.webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        }

        // High refresh preference (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val display = display
            val modes = display?.supportedModes
            if (highRefreshEnabled && modes != null && modes.isNotEmpty()) {
                val bestMode = modes.maxByOrNull { it.refreshRate }
                bestMode?.let {
                    val attrs = window.attributes
                    attrs.preferredDisplayModeId = it.modeId
                    window.attributes = attrs
                }
            } else {
                val attrs = window.attributes
                attrs.preferredDisplayModeId = 0
                window.attributes = attrs
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(true)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.isVisible = newProgress < 100
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    hideCustomView()
                    return
                }
                
                customView = view
                customViewCallback = callback
                isFullscreen = true

                // Hide system bars for true fullscreen
                val controller = ViewCompat.getWindowInsetsController(window.decorView)
                    ?: WindowInsetsControllerCompat(window, binding.root)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                // Add custom view to fullscreen container
                binding.fullscreenContainer.visibility = View.VISIBLE
                if (view != null) {
                    binding.fullscreenContainer.addView(view)
                }
                binding.webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }
        
        binding.webView.webChromeClient = webChromeClient

        binding.webView.setOnTouchListener { _, event ->
            // Check for swipe home gesture first
            if (swipeHomeDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            // Then check for two-finger pull down gesture
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun hideCustomView() {
        if (customView == null) return

        // Remove custom view safely
        try {
            binding.fullscreenContainer.removeView(customView)
        } catch (e: Exception) {
            // View might already be removed
        }
        binding.fullscreenContainer.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE

        // Call callback to notify WebView
        customViewCallback?.onCustomViewHidden()
        
        customView = null
        customViewCallback = null
        isFullscreen = false

        // Restore immersive mode
        configureImmersiveMode()
    }

    private fun exitFullscreen() {
        if (isFullscreen) {
            hideCustomView()
        }
    }

    override fun onDestroy() {
        exitFullscreen()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}

