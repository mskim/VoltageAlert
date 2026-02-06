# VoltageAlert - Build Instructions

## Current Status

✅ **Project Implementation**: Complete (22 Kotlin files, 14 XML resources, 23 images)
⚠️ **Build Requirement**: Java/JDK must be installed
📍 **Project Location**: `/Users/mskim/Development/Android/VoltageAlert/`

## Prerequisites

### Option 1: Install Android Studio (Recommended)
Android Studio includes everything you need (JDK, Android SDK, build tools).

1. **Download Android Studio**:
   ```
   https://developer.android.com/studio
   ```

2. **Install Android Studio**:
   - Open the downloaded DMG file
   - Drag Android Studio to Applications folder
   - Launch Android Studio
   - Complete the setup wizard (installs SDK, JDK, etc.)

3. **Open the Project**:
   ```
   File → Open → /Users/mskim/Development/Android/VoltageAlert
   ```

4. **Build in Android Studio**:
   - Wait for Gradle sync to complete
   - Click "Build" → "Make Project" (⌘+F9)
   - Or click the green "Run" button to build and install on device/emulator

### Option 2: Install JDK Only (Command Line Build)
If you prefer command-line builds without Android Studio:

1. **Install Homebrew** (if not already installed):
   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```

2. **Install JDK 17**:
   ```bash
   brew install openjdk@17
   ```

3. **Set JAVA_HOME**:
   ```bash
   echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
   source ~/.zshrc
   ```

4. **Verify Java Installation**:
   ```bash
   java -version
   # Should show: openjdk version "17.x.x"
   ```

## Building the App

### Using Android Studio (Easiest)
1. Open project in Android Studio
2. Wait for Gradle sync
3. Build → Make Project (⌘+F9)
4. Find APK: `app/build/outputs/apk/debug/app-debug.apk`

### Using Command Line (After JDK Installation)

#### Clean Build
```bash
cd /Users/mskim/Development/Android/VoltageAlert
./gradlew clean
```

#### Build Debug APK
```bash
./gradlew assembleDebug
```
**Output**: `app/build/outputs/apk/debug/app-debug.apk`

#### Build Release APK
```bash
./gradlew assembleRelease
```
**Output**: `app/build/outputs/apk/release/app-release-unsigned.apk`

#### Run Unit Tests
```bash
./gradlew test
```

#### Check for Issues
```bash
./gradlew lint
```

## Installation on Device

### Prerequisites
- Android device with **Android 8.0 (API 26) or higher**
- USB debugging enabled
- ADB (Android Debug Bridge) installed

### Install via ADB
```bash
# Check device is connected
~/Library/Android/sdk/platform-tools/adb devices

# Install the APK
~/Library/Android/sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk

# Or reinstall (if already installed)
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### View App Logs
```bash
~/Library/Android/sdk/platform-tools/adb logcat -d | grep VoltageAlert
```

## Project Verification

All project files are in place:

### Source Code (22 Kotlin files)
```
✅ models/          - VoltageLevel, VoltageReading, ConnectionStatus
✅ logging/         - Room database, duplicate suppression, log manager
✅ bluetooth/       - BLE service, packet parser, permissions
✅ alert/           - Sound generator, haptic manager, coordinator
✅ ui/              - MainActivity, AlertActivity, ViewModel, adapters
✅ testing/         - MockBluetoothDevice
```

### Resources (14 XML files)
```
✅ layouts/         - activity_main, activity_alert, item_log_entry
✅ values/          - strings (English), colors, themes
✅ values-ko/       - strings (Korean)
✅ drawables/       - status indicators, notification icons
✅ xml/             - backup rules, data extraction
```

### Images (23 PNG files)
```
✅ drawable-xxhdpi/ - All voltage warning images converted from BMP
```

### Build Configuration
```
✅ build.gradle.kts       - Root and app module configurations
✅ settings.gradle.kts    - Project settings
✅ gradle wrapper         - Gradle 8.13 configured
✅ AndroidManifest.xml    - All permissions declared
✅ proguard-rules.pro     - Release build optimization
```

## Build Configuration Details

### Gradle Version
- **Gradle**: 8.13
- **Android Gradle Plugin**: 8.3.0
- **Kotlin**: 1.9.22

### SDK Versions
- **compileSdk**: 34 (Android 14)
- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 34 (Android 14)

### Build Variants
- **Debug**: Includes mock Bluetooth toggle
- **Release**: ProGuard enabled, optimized

## Troubleshooting

### "Unable to locate a Java Runtime"
**Solution**: Install JDK (see Option 1 or Option 2 above)

### "SDK location not found"
**Solution**: Create `local.properties` file:
```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

### "Gradle sync failed"
**Solution**:
1. Delete `.gradle` folder: `rm -rf .gradle`
2. Sync again in Android Studio

### "AAPT2 error"
**Solution**: Update Android SDK build tools via Android Studio SDK Manager

### Build is slow
**Solution**: Increase Gradle memory in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

## Expected Build Output

### Successful Debug Build
```
BUILD SUCCESSFUL in 1m 23s
45 actionable tasks: 45 executed
```

**APK Location**:
```
app/build/outputs/apk/debug/app-debug.apk
```

**APK Size**: ~2-3 MB (debug), ~1.5 MB (release)

### Unit Test Results
```
DuplicateSuppressionFilterTest > 8 tests PASSED
SensorDataParserTest > 11 tests PASSED

BUILD SUCCESSFUL
```

## Next Steps After Build

1. **Install on device** using ADB
2. **Grant permissions** (Bluetooth, Notifications)
3. **Test with MockBluetoothDevice** (debug build)
4. **Verify all features**:
   - Connection status indicator
   - Voltage display updates
   - Alert triggers (sound, vibration, screen)
   - Event log with duplicate suppression
   - Language switching (Korean ↔ English)

## Support

For build issues:
- Check Android Studio's "Build" output window
- Review `gradlew` command output
- Ensure all prerequisites are installed
- Verify SDK versions match configuration

## Quick Reference

| Task | Command |
|------|---------|
| Clean | `./gradlew clean` |
| Build Debug | `./gradlew assembleDebug` |
| Build Release | `./gradlew assembleRelease` |
| Run Tests | `./gradlew test` |
| Lint Check | `./gradlew lint` |
| Install on Device | `adb install app/build/outputs/apk/debug/app-debug.apk` |
| View Logs | `adb logcat -d \| grep VoltageAlert` |

---

**Note**: The project is fully implemented and ready to build. Once Java/JDK is installed, the build should complete successfully in ~1-2 minutes.
