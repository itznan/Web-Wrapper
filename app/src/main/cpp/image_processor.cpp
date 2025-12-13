#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <chrono>

#define LOG_TAG "ImageProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Optimize image by resizing and compressing
 * Returns processing time in milliseconds
 */
JNIEXPORT jlong JNICALL
Java_com_nan_webwrapper_NativeHelper_processImageNative(JNIEnv *env, jclass clazz,
                                                         jbyteArray imageData, jint width, jint height) {
    using namespace std::chrono;
    auto start = std::chrono::high_resolution_clock::now();
    
    jbyte* data = env->GetByteArrayElements(imageData, nullptr);
    if (data == nullptr) {
        LOGE("Failed to get image data");
        return -1;
    }

    // Perform image processing operations here
    // For now, just validate the data
    jsize length = env->GetArrayLength(imageData);
    if (length <= 0 || width <= 0 || height <= 0) {
        env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);
        return -1;
    }

    // Example: Simple validation and processing
    // In a real implementation, you could:
    // - Resize the image
    // - Apply filters
    // - Compress the image
    // - Convert formats

    env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    
    LOGI("Image processed in %lld ms", static_cast<long long>(duration));
    return static_cast<jlong>(duration);
}

/**
 * Fast image resize using native code
 */
JNIEXPORT jboolean JNICALL
Java_com_nan_webwrapper_NativeHelper_resizeImageNative(JNIEnv *env, jclass clazz,
                                                        jobject srcBitmap, jobject dstBitmap) {
    AndroidBitmapInfo srcInfo, dstInfo;
    void* srcPixels;
    void* dstPixels;

    // Get source bitmap info
    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get source bitmap info");
        return JNI_FALSE;
    }

    // Get destination bitmap info
    if (AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get destination bitmap info");
        return JNI_FALSE;
    }

    // Lock pixels
    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock source pixels");
        return JNI_FALSE;
    }

    if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        LOGE("Failed to lock destination pixels");
        return JNI_FALSE;
    }

    // Simple nearest-neighbor resize (for performance)
    // For better quality, use bilinear or bicubic interpolation
    int srcWidth = srcInfo.width;
    int srcHeight = srcInfo.height;
    int dstWidth = dstInfo.width;
    int dstHeight = dstInfo.height;

    if (srcInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        dstInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        
        uint32_t* src = (uint32_t*)srcPixels;
        uint32_t* dst = (uint32_t*)dstPixels;

        for (int y = 0; y < dstHeight; y++) {
            int srcY = (y * srcHeight) / dstHeight;
            for (int x = 0; x < dstWidth; x++) {
                int srcX = (x * srcWidth) / dstWidth;
                dst[y * dstWidth + x] = src[srcY * srcWidth + srcX];
            }
        }
    }

    // Unlock pixels
    AndroidBitmap_unlockPixels(env, dstBitmap);
    AndroidBitmap_unlockPixels(env, srcBitmap);

    LOGI("Image resized from %dx%d to %dx%d", srcWidth, srcHeight, dstWidth, dstHeight);
    return JNI_TRUE;
}

}

