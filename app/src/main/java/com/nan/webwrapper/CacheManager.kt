package com.nan.webwrapper

import android.content.Context
import android.webkit.WebView
import java.io.File
import kotlin.math.roundToInt

class CacheManager(private val context: Context) {

    fun getCacheSizeForWebsite(url: String, callback: (Long) -> Unit) {
        val domain = extractDomain(url)
        if (domain == null) {
            callback(0L)
            return
        }

        var totalSize = 0L

        // Get WebView cache directory size
        val cacheDir = context.cacheDir
        val webViewCacheDir = File(cacheDir, "org.chromium.android_webview")
        totalSize += getDirectorySize(webViewCacheDir)

        // Get WebView data directory size
        val dataDir = context.getDir("webview", Context.MODE_PRIVATE)
        totalSize += getDirectorySize(dataDir)

        // Get application cache
        val appCacheDir = context.cacheDir
        totalSize += getDirectorySize(appCacheDir)

        callback(totalSize)
    }

    fun clearCacheForWebsite(url: String) {
        try {
            // Clear WebView cache
            val webView = WebView(context)
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.destroy()

            // Clear cookies for this specific domain
            val cookieManager = android.webkit.CookieManager.getInstance()
            val domain = extractDomain(url)
            if (domain != null) {
                val cookies = cookieManager.getCookie(url)
                if (cookies != null) {
                    // Remove cookies for this specific domain
                    cookieManager.setCookie(url, "")
                    cookieManager.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllCache() {
        try {
            val webView = WebView(context)
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.destroy()

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractDomain(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }

    private fun getDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0L
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
            bytes < 1024 * 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0)).roundToInt()} MB"
            else -> "${(bytes / (1024.0 * 1024.0 * 1024.0)).roundToInt()} GB"
        }
    }
}

