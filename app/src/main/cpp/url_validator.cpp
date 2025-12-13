#include <jni.h>
#include <string>
#include <regex>
#include <algorithm>
#include <cctype>
#include <android/log.h>

#define LOG_TAG "UrlValidator"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Fast URL validation using native regex
 */
JNIEXPORT jboolean JNICALL
Java_com_nan_webwrapper_NativeHelper_validateUrlNative(JNIEnv *env, jclass clazz, jstring url) {
    if (url == nullptr) {
        return JNI_FALSE;
    }

    const char* urlStr = env->GetStringUTFChars(url, nullptr);
    if (urlStr == nullptr) {
        return JNI_FALSE;
    }

    std::string urlString(urlStr);
    env->ReleaseStringUTFChars(url, urlStr);

    if (urlString.empty()) {
        return JNI_FALSE;
    }

    // Enhanced URL validation regex
    // Matches http://, https://, or protocol-relative URLs
    std::regex urlPattern(
        R"(^https?://[^\s/$.?#].[^\s]*$|^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]?\.[a-zA-Z]{2,}(/.*)?$)",
        std::regex_constants::icase
    );

    bool isValid = std::regex_match(urlString, urlPattern);
    
    // Also check for common protocols
    if (!isValid) {
        // Check if it starts with a protocol
        if (urlString.find("http://") == 0 || 
            urlString.find("https://") == 0 ||
            urlString.find("file://") == 0 ||
            urlString.find("ftp://") == 0) {
            isValid = true;
        }
    }

    LOGI("URL validation: %s -> %s", urlString.c_str(), isValid ? "valid" : "invalid");
    return isValid ? JNI_TRUE : JNI_FALSE;
}

/**
 * Extract domain from URL
 */
JNIEXPORT jstring JNICALL
Java_com_nan_webwrapper_NativeHelper_extractDomainNative(JNIEnv *env, jclass clazz, jstring url) {
    if (url == nullptr) {
        return nullptr;
    }

    const char* urlStr = env->GetStringUTFChars(url, nullptr);
    if (urlStr == nullptr) {
        return nullptr;
    }

    std::string urlString(urlStr);
    env->ReleaseStringUTFChars(url, urlStr);

    std::string domain;
    
    // Extract domain from URL
    size_t protocolEnd = urlString.find("://");
    if (protocolEnd != std::string::npos) {
        urlString = urlString.substr(protocolEnd + 3);
    }
    
    size_t pathStart = urlString.find('/');
    if (pathStart != std::string::npos) {
        domain = urlString.substr(0, pathStart);
    } else {
        domain = urlString;
    }
    
    // Remove port if present
    size_t portStart = domain.find(':');
    if (portStart != std::string::npos) {
        domain = domain.substr(0, portStart);
    }

    return env->NewStringUTF(domain.c_str());
}

/**
 * Normalize URL (add https:// if missing, lowercase domain, etc.)
 */
JNIEXPORT jstring JNICALL
Java_com_nan_webwrapper_NativeHelper_normalizeUrlNative(JNIEnv *env, jclass clazz, jstring url) {
    if (url == nullptr) {
        return nullptr;
    }

    const char* urlStr = env->GetStringUTFChars(url, nullptr);
    if (urlStr == nullptr) {
        return nullptr;
    }

    std::string urlString(urlStr);
    env->ReleaseStringUTFChars(url, urlStr);

    if (urlString.empty()) {
        return env->NewStringUTF("");
    }

    // Trim whitespace
    urlString.erase(0, urlString.find_first_not_of(" \t\n\r"));
    urlString.erase(urlString.find_last_not_of(" \t\n\r") + 1);

    // Add protocol if missing
    if (urlString.find("://") == std::string::npos) {
        urlString = "https://" + urlString;
    }

    // Convert to lowercase (domain part only)
    size_t protocolEnd = urlString.find("://");
    if (protocolEnd != std::string::npos) {
        size_t pathStart = urlString.find('/', protocolEnd + 3);
        if (pathStart != std::string::npos) {
            std::string domain = urlString.substr(protocolEnd + 3, pathStart - protocolEnd - 3);
            std::string path = urlString.substr(pathStart);
            
            // Lowercase domain
            std::transform(domain.begin(), domain.end(), domain.begin(), ::tolower);
            urlString = urlString.substr(0, protocolEnd + 3) + domain + path;
        } else {
            std::string domain = urlString.substr(protocolEnd + 3);
            std::transform(domain.begin(), domain.end(), domain.begin(), ::tolower);
            urlString = urlString.substr(0, protocolEnd + 3) + domain;
        }
    }

    LOGI("Normalized URL: %s", urlString.c_str());
    return env->NewStringUTF(urlString.c_str());
}

}

