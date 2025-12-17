package com.nan.webwrapper

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nan.webwrapper.databinding.ActivityWebviewBinding
import org.json.JSONObject

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private lateinit var gestureDetector: TwoFingerPullDownDetector
    private lateinit var swipeHomeDetector: SwipeHomeGestureDetector
    private lateinit var twoFingerSwipeRightDetector: TwoFingerSwipeRightDetector
    private lateinit var settingsRepository: SettingsRepository
    private var currentUrl: String = ""
    private var hardwareAccelEnabled: Boolean = true
    private var highRefreshEnabled: Boolean = true

    private var isColorsInverted: Boolean = false
    
    // Fullscreen support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen: Boolean = false
    private var webChromeClient: WebChromeClient? = null
    
    // File upload support
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    
    // Permission handling
    private var pendingPermissionRequest: PermissionRequest? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handlePermissionResult(isGranted)
    }
    
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handlePermissionResult(isGranted)
    }

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
        twoFingerSwipeRightDetector = TwoFingerSwipeRightDetector(this) { showQuickControls() }

        resolveSettings()
        configureImmersiveMode()
        setupQuickControls()
        setupFileUpload()
        setupWebView()

        // Restore WebView state if available
        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else if (!url.isNullOrBlank()) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save WebView state to preserve scroll position and page content
        binding.webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // WebView state is restored in onCreate
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Activity won't be recreated, but we should reconfigure immersive mode
        configureImmersiveMode()
    }

    private fun resolveSettings() {
        hardwareAccelEnabled = settingsRepository.shouldUseHardwareAccel(currentUrl)
        highRefreshEnabled = settingsRepository.shouldUseHighRefresh(currentUrl)
    }

    private fun setupQuickControls() {
        binding.quickControlsCloseButton.setOnClickListener { hideQuickControls() }
        binding.quickControlsScrim.setOnClickListener { hideQuickControls() }

        binding.copyLinkButton.setOnClickListener {
            val urlToCopy = currentUrl.ifBlank { binding.webView.url.orEmpty() }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("URL", urlToCopy))
            Toast.makeText(this, getString(R.string.copy_current_link), Toast.LENGTH_SHORT).show()
        }

        binding.invertColorsButton.setOnClickListener {
            toggleInvertColors()
        }
    }

    private fun toggleInvertColors() {
        isColorsInverted = !isColorsInverted
        applyInvertFilterIfNeeded()
    }

    private fun applyInvertFilterIfNeeded() {
        if (isColorsInverted) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(invertMatrix) }
            binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            binding.invertColorsButton.text = getString(R.string.invert_colors) + ": ON"
        } else {
            binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            binding.invertColorsButton.text = getString(R.string.invert_colors)
        }
    }

    private fun showQuickControls() {
        if (binding.quickControlsScrim.isVisible) return
        binding.quickControlsScrim.visibility = View.VISIBLE
    }

    private fun hideQuickControls() {
        if (!binding.quickControlsScrim.isVisible) return
        binding.quickControlsScrim.visibility = View.GONE
    }

    // File upload launchers
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        handleFileUploadResult(uri)
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        handleFileUploadResult(uri)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        handleFileUploadResult(uri)
    }

    private fun setupFileUpload() {
        // File upload is handled in WebChromeClient.onShowFileChooser
    }

    private fun handleFileUploadResult(uri: Uri?) {
        fileUploadCallback?.onReceiveValue(
            if (uri != null) arrayOf(uri) else null
        )
        fileUploadCallback = null
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

        // Hardware acceleration - Always enable for maximum performance
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        // Always use hardware acceleration for WebView
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
        
        // Enable JavaScript and DOM storage
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        
        // Performance optimizations
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.loadsImagesAutomatically = true
        webSettings.blockNetworkImage = false
        webSettings.blockNetworkLoads = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false // Hide zoom controls UI
        webSettings.setSupportMultipleWindows(false)
        
        // Enable hardware acceleration and performance features
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        // Enable all performance features
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        // Keep these disabled for security/stability; they don't improve network performance.
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.allowUniversalAccessFromFileURLs = false
        
        // Configure WebView for high performance
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false)
        }
        
        // Set WebView to use hardware acceleration for maximum performance
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Increase memory limits (API 19+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Enable hardware acceleration
            binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Configure memory limits (API 26+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webSettings.safeBrowsingEnabled = true
        }

        // Improves perceived scroll/render smoothness on some devices
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            webSettings.offscreenPreRaster = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    currentUrl = url
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    currentUrl = url
                }
                // Inject Web Share polyfill after page loads
                injectWebSharePolyfill()
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.isVisible = newProgress < 100
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Cancel previous callback if any
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                // Determine file types to accept
                val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")

                // Launch file picker based on accepted types
                when {
                    acceptTypes.any { it.contains("image") } -> {
                        imagePickerLauncher.launch("image/*")
                    }
                    acceptTypes.any { it.contains("video") } -> {
                        videoPickerLauncher.launch("video/*")
                    }
                    else -> {
                        // Generic file picker for documents and other files
                        filePickerLauncher.launch("*/*")
                    }
                }

                return true
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
            
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                
                pendingPermissionRequest = request
                val resources = request.resources
                
                // Check which permissions are requested
                val needsCamera = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                val needsMicrophone = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                
                when {
                    needsCamera && needsMicrophone -> {
                        // Request both permissions
                        val cameraGranted = ContextCompat.checkSelfPermission(
                            this@WebViewActivity,
                            android.Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        val micGranted = ContextCompat.checkSelfPermission(
                            this@WebViewActivity,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        when {
                            cameraGranted && micGranted -> {
                                request.grant(request.resources)
                            }
                            cameraGranted -> {
                                microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                            micGranted -> {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                            else -> {
                                // Request camera first, then mic
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                    }
                    needsCamera -> {
                        if (ContextCompat.checkSelfPermission(
                                this@WebViewActivity,
                                android.Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            request.grant(request.resources)
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                    needsMicrophone -> {
                        if (ContextCompat.checkSelfPermission(
                                this@WebViewActivity,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            request.grant(request.resources)
                        } else {
                            microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    else -> {
                        // Grant other permissions automatically
                        request.grant(request.resources)
                    }
                }
            }
        }
        
        // Add JavaScript interface for Web Share API
        binding.webView.addJavascriptInterface(WebShareInterface(), "AndroidWebShare")
        
        binding.webView.webChromeClient = webChromeClient

        binding.webView.setOnTouchListener { _, event ->
            // If the panel is open, don't let touches leak into the WebView.
            if (binding.quickControlsScrim.isVisible) {
                return@setOnTouchListener true
            }

            // Check for swipe home gesture first
            if (swipeHomeDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            
            // Check for two-finger pull down gesture
            // Only consume if it's actually a pull down (not zoom)
            val isPullDown = gestureDetector.onTouchEvent(event)
            if (isPullDown) {
                return@setOnTouchListener true
            }

            // Two-finger swipe left-to-right opens quick controls.
            if (twoFingerSwipeRightDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            
            // Let WebView handle all other gestures (including zoom)
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

    override fun onPause() {
        super.onPause()
        // Pause WebView to reduce CPU usage when not visible
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        // Resume WebView when activity becomes visible
        binding.webView.onResume()
    }

    override fun onDestroy() {
        exitFullscreen()
        // Clean up WebView to free memory
        binding.webView.destroy()
        super.onDestroy()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        // Clear WebView cache when system is low on memory
        binding.webView.clearCache(true)
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Clear cache based on memory pressure level
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                binding.webView.clearCache(true)
            }
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        pendingPermissionRequest?.let { request ->
            if (isGranted) {
                // Check if we still need the other permission
                val needsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                val needsMicrophone = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                
                val cameraGranted = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                val micGranted = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                
                if ((needsCamera && !cameraGranted) || (needsMicrophone && !micGranted)) {
                    // Still need another permission
                    if (needsCamera && !cameraGranted) {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    } else if (needsMicrophone && !micGranted) {
                        microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    // All required permissions granted
                    request.grant(request.resources)
                    pendingPermissionRequest = null
                }
            } else {
                // Permission denied
                request.deny()
                pendingPermissionRequest = null
            }
        }
    }
    
    private fun injectWebSharePolyfill() {
        val sharePolyfill = """
            (function() {
                if (!navigator.share) {
                    navigator.share = function(data) {
                        return new Promise(function(resolve, reject) {
                            try {
                                var shareData = {
                                    title: data.title || '',
                                    text: data.text || '',
                                    url: data.url || ''
                                };
                                
                                AndroidWebShare.share(
                                    shareData.title,
                                    shareData.text,
                                    shareData.url
                                );
                                resolve();
                            } catch (error) {
                                reject(error);
                            }
                        });
                    };
                }
            })();
        """.trimIndent()
        
        binding.webView.evaluateJavascript(sharePolyfill, null)
    }
    
    inner class WebShareInterface {
        @JavascriptInterface
        fun share(title: String?, text: String?, url: String?) {
            runOnUiThread {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    val shareText = buildString {
                        if (!title.isNullOrBlank()) {
                            append(title)
                            if (!text.isNullOrBlank() || !url.isNullOrBlank()) {
                                append("\n")
                            }
                        }
                        if (!text.isNullOrBlank()) {
                            append(text)
                            if (!url.isNullOrBlank()) {
                                append("\n")
                            }
                        }
                        if (!url.isNullOrBlank()) {
                            append(url)
                        }
                    }
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    if (!title.isNullOrBlank()) {
                        putExtra(Intent.EXTRA_SUBJECT, title)
                    }
                }
                
                try {
                    startActivity(Intent.createChooser(shareIntent, "Share"))
                } catch (e: Exception) {
                    // Handle error silently
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}

