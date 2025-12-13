package com.nan.webwrapper

import android.graphics.Bitmap

/**
 * JNI wrapper for native C++ functions
 * Provides high-performance operations using NDK
 */
object NativeHelper {

    init {
        try {
            System.loadLibrary("webwrapper")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("NativeHelper", "Failed to load native library", e)
        }
    }

    /**
     * Get the version of the native library
     */
    external fun getNativeVersion(): String

    /**
     * Validate URL using fast native regex
     */
    external fun validateUrlNative(url: String): Boolean

    /**
     * Extract domain from URL
     */
    external fun extractDomainNative(url: String): String?

    /**
     * Normalize URL (add protocol, lowercase domain, etc.)
     */
    external fun normalizeUrlNative(url: String): String?

    /**
     * Process image using native code (for future use)
     */
    external fun processImageNative(imageData: ByteArray, width: Int, height: Int): Long

    /**
     * Resize image using native code for better performance
     */
    external fun resizeImageNative(srcBitmap: Bitmap, dstBitmap: Bitmap): Boolean

    /**
     * Convenience method to validate and normalize URL
     */
    fun validateAndNormalizeUrl(url: String): String? {
        val normalized = normalizeUrlNative(url) ?: return null
        return if (validateUrlNative(normalized)) normalized else null
    }

    /**
     * Check if native library is loaded
     */
    fun isNativeLibraryLoaded(): Boolean {
        return try {
            getNativeVersion()
            true
        } catch (e: Exception) {
            false
        }
    }
}

