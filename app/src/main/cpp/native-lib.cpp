#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "WebWrapperNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_nan_webwrapper_NativeHelper_getNativeVersion(JNIEnv *env, jclass clazz) {
    std::string version = "1.0.0";
    LOGI("Native library version: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

}

