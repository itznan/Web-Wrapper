package com.nan.webwrapper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches website favicons automatically
 */
class FaviconFetcher(private val context: Context) {

    companion object {
        private const val TAG = "FaviconFetcher"
        private const val FAVICON_SIZE = 256
        private const val TIMEOUT_MS = 5000
    }

    /**
     * Fetch favicon for a URL and save it
     * Returns the file path if successful, null otherwise
     */
    suspend fun fetchAndSaveFavicon(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val domain = extractDomain(url) ?: run {
                Log.e(TAG, "Failed to extract domain from: $url")
                return@withContext null
            }
            
            Log.d(TAG, "Fetching favicon for domain: $domain")
            val faviconUrls = getFaviconUrls(domain, url)
            
            // Try each favicon URL until one works
            var bitmap: Bitmap? = null
            for (faviconUrl in faviconUrls) {
                Log.d(TAG, "Trying favicon URL: $faviconUrl")
                bitmap = downloadFavicon(faviconUrl)
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    Log.d(TAG, "Successfully downloaded favicon from: $faviconUrl")
                    break
                } else {
                    bitmap?.recycle()
                    bitmap = null
                }
            }
            
            if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) {
                Log.e(TAG, "Failed to download favicon for $domain from all URLs")
                return@withContext null
            }

            // Resize and optimize
            val resizedBitmap = resizeBitmap(bitmap, FAVICON_SIZE)
            bitmap.recycle()

            // Save to internal storage
            val logoDir = File(context.filesDir, "history_logos")
            if (!logoDir.exists()) {
                logoDir.mkdirs()
            }

            val logoFile = File(logoDir, "favicon_${domain.hashCode()}.png")
            val outputStream = FileOutputStream(logoFile)
            resizedBitmap.compress(Bitmap.CompressFormat.PNG, 85, outputStream)
            outputStream.flush()
            outputStream.close()
            resizedBitmap.recycle()

            Log.d(TAG, "Favicon saved for $domain: ${logoFile.absolutePath}")
            logoFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching favicon for $url", e)
            e.printStackTrace()
            null
        }
    }

    private fun extractDomain(urlString: String): String? {
        return try {
            val url = URL(urlString)
            url.host ?: urlString
        } catch (e: Exception) {
            null
        }
    }

    private fun getFaviconUrls(domain: String, originalUrl: String): List<String> {
        val encodedOriginalUrl = try {
            URLEncoder.encode(originalUrl, "UTF-8")
        } catch (_: Exception) {
            originalUrl
        }

        val encodedDomainUrl = try {
            URLEncoder.encode("https://$domain", "UTF-8")
        } catch (_: Exception) {
            "https://$domain"
        }

        // Prefer endpoints that return PNG (BitmapFactory can decode reliably).
        return listOf(
            // Google favicon v2 (typically PNG)
            "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=$encodedOriginalUrl&size=$FAVICON_SIZE",
            // Google s2 service using full URL
            "https://www.google.com/s2/favicons?domain_url=$encodedOriginalUrl&sz=$FAVICON_SIZE",
            // Google s2 using domain URL
            "https://www.google.com/s2/favicons?domain_url=$encodedDomainUrl&sz=$FAVICON_SIZE",
            // DuckDuckGo (often ICO; keep as fallback)
            "https://icons.duckduckgo.com/ip3/$domain.ico",
            // Direct favicon.ico from domain (often ICO)
            "https://$domain/favicon.ico",
            "http://$domain/favicon.ico",
            // Try with www prefix
            "https://www.$domain/favicon.ico"
        )
    }

    private suspend fun downloadFavicon(faviconUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(faviconUrl)
            connection = url.openConnection() as? HttpURLConnection ?: run {
                Log.e(TAG, "Failed to create connection for $faviconUrl")
                return@withContext null
            }
            
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.d(TAG, "HTTP $responseCode for $faviconUrl")
                connection.disconnect()
                return@withContext null
            }

            val inputStream = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            connection.disconnect()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from $faviconUrl")
                return@withContext null
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading favicon from $faviconUrl: ${e.message}")
            connection?.disconnect()
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return if (NativeHelper.isNativeLibraryLoaded()) {
            val resizedBitmap = Bitmap.createBitmap(newWidth, newHeight, bitmap.config)
            if (NativeHelper.resizeImageNative(bitmap, resizedBitmap)) {
                bitmap.recycle()
                resizedBitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                    bitmap.recycle()
                }
            }
        } else {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                bitmap.recycle()
            }
        }
    }
}

