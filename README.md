# WebWrapper

A high-performance Android WebView wrapper application with native optimizations and advanced gesture controls.

## Features

### Core Functionality
- **Full-Screen WebView Experience**: Immersive browsing with configurable notch handling
- **Website History Management**: Save and organize frequently visited websites with custom names and logos
- **Native Performance Optimizations**: C++ powered URL validation, normalization, and image processing via JNI/NDK
- **Advanced Gesture Controls**:
  - Two-finger pull-down to refresh
  - Left-to-right then right-to-left swipe gesture to return home
- **Fullscreen Video Support**: Seamless video playback with custom view handling

### Settings & Customization
- **Per-Website Settings**:
  - Hardware acceleration toggle
  - High refresh rate support (for compatible devices)
  - Notch/cutout area handling
  - Cache management
- **Global Defaults**: Set default behaviors for new websites
- **Custom Website Identity**: Add custom names and logos for easy identification

### Performance Features
- **Native URL Processing**: Fast regex-based URL validation using C++
- **Native Image Resizing**: Hardware-accelerated bitmap operations
- **Efficient Cache Management**: Monitor and clear website-specific caches
- **Hardware Acceleration**: Optional GPU-accelerated rendering

## Architecture

### Native Layer (C++/JNI)
- `native-lib.cpp`: Core native library initialization
- `url_validator.cpp`: High-performance URL validation and normalization
- `image_processor.cpp`: Native bitmap processing and resizing
- `CMakeLists.txt`: NDK build configuration

### Android Layer (Kotlin)
#### Activities
- `MainActivity`: Home screen with website history
- `WebViewActivity`: Full-screen WebView with gesture support
- `AddWebsiteActivity`: Add new websites to history
- `SettingsActivity`: Global and per-website settings

#### Data & Storage
- `HistoryRepository`: Manages website history in SharedPreferences
- `SettingsRepository`: Stores per-website and global settings
- `CacheManager`: Cache size calculation and clearing utilities

#### UI Components
- `HistoryAdapter`: RecyclerView adapter for website list
- `SettingsAdapter`: RecyclerView adapter for settings items

#### Gesture Detection
- `TwoFingerPullDownDetector`: Custom two-finger pull-down gesture
- `SwipeHomeGestureDetector`: Custom swipe-to-home gesture

#### Models
- `HistoryEntry`: Website data model
- `WebsiteSettingsItem`: Settings data model

#### Native Bridge
- `NativeHelper`: JNI wrapper for C++ functions

## Technical Highlights

### Native Performance
```kotlin
// URL validation and normalization using native C++
val validUrl = NativeHelper.validateAndNormalizeUrl(userInput)

// Fast bitmap resizing with native code
NativeHelper.resizeImageNative(sourceBitmap, destinationBitmap)
```

### Gesture System
- **Two-Finger Pull Down**: Refresh the current page
- **Swipe Home**: Left-to-right then right-to-left swipe returns to home

### WebView Configuration
- JavaScript enabled with DOM storage
- Cookie support with third-party cookies
- Wide viewport and zoom support
- Custom WebChromeClient for fullscreen video
- Configurable hardware acceleration

### Display Features
- Immersive mode with system bar hiding
- Configurable display cutout (notch) handling
- High refresh rate support (Android 11+)
- Dynamic display mode selection

## Setup

### Prerequisites
- Android Studio Arctic Fox or newer
- Android NDK (for native components)
- Minimum SDK: 24 (Android 7.0)
- Target SDK: Latest

### Build
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on device or emulator

### Native Library
The native library is automatically built via CMake during the Gradle build process. No additional setup required.

## Usage

### Adding a Website
1. Launch the app
2. Tap the FAB (Floating Action Button) or enter a URL
3. The website is validated and normalized automatically
4. Optionally customize with a name and logo

### Customizing Websites
1. Long-press or tap edit on any website entry
2. Add a custom display name
3. Upload a logo image (automatically resized to 256x256)

### Configuring Settings
1. Open Settings from the main menu
2. Set global defaults for new websites
3. Configure per-website settings:
   - Hardware acceleration
   - High refresh rate
   - Notch handling
   - Clear cache

### Gestures
- **Reload**: Two-finger pull down on any webpage
- **Go Home**: Swipe left-to-right then right-to-left
- **Go Back**: Standard back button or gesture

## Architecture Decisions

### Why Native Code?
- **URL Validation**: C++ regex is significantly faster than Java Pattern for repeated validations
- **Image Processing**: Direct bitmap manipulation in native code reduces memory overhead
- **Performance Critical Path**: URL normalization happens frequently, native implementation provides measurable speedup

### Gesture System
Custom gesture detectors provide a unique UX without conflicting with standard WebView gestures:
- Pull-to-refresh alternatives that work with any web content
- Intuitive home navigation without UI clutter

### Settings Architecture
Three-tier settings system:
1. Global defaults
2. Per-website overrides
3. Runtime configuration

This allows fine-grained control while maintaining simplicity.

## Performance Optimization

### Memory Management
- Automatic bitmap recycling after resize operations
- Efficient logo caching at 256x256 resolution
- WebView cache monitoring and cleanup

### Rendering
- Optional hardware acceleration per website
- High refresh rate support for smooth scrolling
- Layer-type optimization based on settings

### Storage
- JSON-based SharedPreferences for lightweight data
- File-based logo storage with automatic cleanup
- Efficient cache size calculation

## License

This project is open source. See the repository for license details.

## Contributing

Contributions are welcome! Please follow standard Android development practices and ensure native code changes include appropriate JNI error handling.

## Acknowledgments

Built with:
- Android NDK for native performance
- Material Design 3 components
- AndroidX libraries
- Kotlin coroutines for async operations

---

**Developer**: [itznan](https://github.com/itznan)
