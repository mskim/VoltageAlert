# VoltageAlert (활선 접근 경보기)

A safety-critical Android application for Korean power company workers that provides real-time warnings when approaching dangerous high-voltage power lines.

## Project Status

✅ **Core Implementation Complete**

The app is fully implemented with all major features:
- ✅ Bluetooth LE sensor communication
- ✅ Multi-level voltage detection (220V, 380V, 154KV, 229KV, 345KV, 500KV, 765KV)
- ✅ Multi-modal alerts (visual, audio, haptic)
- ✅ Event logging with duplicate suppression
- ✅ Bilingual support (Korean/English)
- ✅ 23 voltage warning images converted and integrated
- ✅ Professional app launcher icon (Lightning Bolt design)
- ✅ Unit tests for core logic

## App Icon

Professional Lightning Bolt design for maximum visibility and instant recognition:
- ⚡ **Bold white lightning bolt** on vibrant red-orange background (#FF3D00)
- ⭕ **Yellow warning ring** for enhanced safety visibility
- 📱 **Adaptive icon support** (Android 8.0+) with separate foreground/background layers
- 🔄 **Round icon variants** for launchers that support circular icons
- 📐 **Complete density coverage** (mdpi through xxxhdpi - 48px to 192px)

The lightning bolt icon immediately communicates high-voltage danger and matches the app's safety-critical purpose.

## Features

### Automatic Connection (No User Action Required!)
- **Auto-scan on app start**: Finds device automatically within 5-8 seconds
- **Continuous rescanning**: If disconnected, rescans every 8 seconds until found
- **Background monitoring**: Keeps monitoring even when app is minimized
- **Smart filtering**: Only scans for "ESSYSTEM" device (fast!)

### Safety-Critical Alerts
- **Visual**: Color-coded display (GREEN = safe, RED = dangerous)
- **Full-screen alert**: Voltage-specific warning screens for dangerous levels
- **Audio**: Two-tone siren (1200Hz/800Hz) using USAGE_ALARM stream
- **Haptic**: Strong vibration pattern
- **Wake Lock**: Automatically turns screen on and shows alert on lock screen

### Voltage Detection
Monitors seven voltage levels:
- **Safe Voltage** (220V, 380V): GREEN display, NO alert
- **Dangerous Voltage** (154KV, 229KV, 345KV, 500KV, 765KV): RED display, FULL alert
- **Diagnostics**: Self-test OK/NG indicators

### Event Logging
- Maximum 99 log entries (FIFO)
- Duplicate suppression: Hides logs after 3 identical consecutive entries
- Format: `1. 2025/12/23 08:45:25 220V`
- Persistent storage using Room database

### Bluetooth Protocol
**ST9401-UP Device Protocol** (ASCII Text Format):
- **Device Name**: ESSYSTEM
- **MAC Address**: 30:ED:A0:D4:8D:92
- **Data Format**: ASCII text (e.g., "220V WARNING")
- **Service UUID**: `0000fff0-0000-1000-8000-00805f9b34fb`
- **Characteristic UUID**: `0000fff1-0000-1000-8000-00805f9b34fb`
- **Properties**: READ, NOTIFY
- **Auto-reconnect**: Continuous rescanning every 8 seconds if disconnected
- **Scan Time**: 5 seconds per scan
- **Wait Between Scans**: 3 seconds

## Architecture

### Technology Stack
- **Language**: Kotlin 1.9.22
- **Build**: Gradle 8.13 with Kotlin DSL
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

### Key Libraries
- **Room 2.6.1**: Event log database
- **Nordic BLE 2.7.1**: Bluetooth LE communication
- **Coroutines 1.7.3**: Asynchronous operations
- **Material 3**: Modern UI components

### Design Pattern
- **MVVM**: ViewModel + StateFlow/LiveData
- **Foreground Service**: Continuous Bluetooth monitoring
- **Repository Pattern**: Data access abstraction
- **Clean Architecture**: UI → ViewModel → Repository → Data Sources

## Project Structure

```
VoltageAlert/
├── app/
│   ├── src/main/
│   │   ├── java/com/voltagealert/
│   │   │   ├── models/           # Data models (VoltageLevel, VoltageReading)
│   │   │   ├── logging/          # Database, DAO, duplicate suppression
│   │   │   ├── bluetooth/        # BLE service, parser, permissions
│   │   │   ├── alert/            # Sound, haptic, coordinator
│   │   │   ├── ui/               # Activities, ViewModel, adapters
│   │   │   ├── utils/            # Helpers and utilities
│   │   │   └── testing/          # Mock Bluetooth device
│   │   └── res/
│   │       ├── layout/           # XML layouts
│   │       ├── values/           # English strings
│   │       ├── values-ko/        # Korean strings
│   │       ├── mipmap-*/         # App launcher icons (all densities)
│   │       └── drawable-xxhdpi/  # 23 voltage warning images (PNG)
│   └── src/test/                 # Unit tests
└── images/                       # Original BMP source images
```

## Testing

### Without Physical Hardware
Use the MockBluetoothDevice for testing:
1. Enable "Use Mock Sensor" in Settings (debug build)
2. Select test scenario:
   - **SAFE**: Only diagnostic readings
   - **DANGER**: Random dangerous voltages
   - **MIXED**: Mix of all levels
   - **DUPLICATE_TEST**: Tests suppression logic
   - **ALL_VOLTAGES**: Cycles through all levels

### Unit Tests
```bash
./gradlew test
```

Tests included:
- `DuplicateSuppressionFilterTest`: Validates 3-duplicate threshold
- `SensorDataParserTest`: Packet parsing and CRC validation

### Manual Testing Checklist
- [ ] Bluetooth permission request flow
- [ ] Connection to sensor (or mock)
- [ ] Voltage display updates in real-time
- [ ] Alert triggers on dangerous voltage
- [ ] Alert dismissible with button
- [ ] Log entries appear correctly
- [ ] Duplicate suppression works
- [ ] 99-entry limit enforced
- [ ] Language switching (Korean ↔ English)

## Building

### Debug Build
```bash
cd /Users/mskim/Development/Android/VoltageAlert
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Installation

### Via ADB
```bash
~/Library/Android/sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
```

### View Logs
```bash
~/Library/Android/sdk/platform-tools/adb logcat -d | grep VoltageAlert
```

## Permissions Required

- **BLUETOOTH_CONNECT** (API 31+): Connect to BLE devices
- **BLUETOOTH_SCAN** (API 31+): Scan for BLE devices
- **VIBRATE**: Haptic alerts
- **FOREGROUND_SERVICE**: Background monitoring
- **POST_NOTIFICATIONS** (API 33+): Alert notifications
- **WAKE_LOCK**: Turn screen on during alerts

## Configuration

### Bluetooth Device
**Configured for ST9401-UP Device:**
- Device name: `"ESSYSTEM"`
- Scan filter enabled (only scans for ESSYSTEM - fast!)

To change device name, modify `BluetoothScanner.kt`:
```kotlin
val scanFilters = listOf(
    ScanFilter.Builder()
        .setDeviceName("YOUR_DEVICE_NAME")
        .build()
)
```

### Alert Frequencies
Two-tone siren in `AlertSoundGenerator.kt`:
```kotlin
private const val TONE_1_FREQ = 1200.0  // Hz
private const val TONE_2_FREQ = 800.0   // Hz
```

### Vibration Pattern
In `HapticAlertManager.kt`:
```kotlin
private val VIBRATION_PATTERN = longArrayOf(
    0, 500, 200, 500, 200, 500, 800  // ms
)
```

## User Guide

### First Time Setup
1. Install APK on Android device (API 26+)
2. Launch app
3. Grant Bluetooth and Notification permissions
4. App automatically starts scanning for ESSYSTEM device

### Daily Use
1. **Launch app** - Auto-scanning starts immediately
2. **Bring ST9401-UP device near power source** - Device detects voltage and starts advertising
3. **Wait 5-8 seconds** - App finds and connects automatically
4. **Monitor voltage** - GREEN card = safe, RED card = dangerous
5. **If dangerous voltage detected** - Full-screen alert + sound + vibration
6. **Tap OK** to dismiss alert

### Device States
- **GREEN card (220V, 380V)**: Safe voltage detected, no alert
- **RED card (154KV+)**: Dangerous voltage! Alert triggered
- **"No voltage detected"**: Device not detecting any voltage (LCD black)
- **"Scanning..."**: Looking for ESSYSTEM device
- **"Connected"**: Monitoring voltage in real-time

### Troubleshooting
- **Device not connecting?** Make sure ST9401-UP is detecting voltage (LCD showing voltage, fast blinking LED)
- **App keeps rescanning?** Device stops advertising when not detecting voltage - bring near power source
- **Slow connection?** Device may take 5-8 seconds to be found after it starts advertising

## Known Limitations

1. **Settings Activity**: Not yet implemented (volume, preferences)
2. **Manual scan button**: Hidden (auto-scan is always active)
3. **Mock mode**: Hidden in production (testing only)
4. **Single device**: Only supports one ESSYSTEM device at a time
5. **Battery Optimization**: May need user whitelisting for reliable 24/7 background operation

## Next Steps

### High Priority
1. Implement SettingsActivity with PreferenceFragment
2. Add debug menu for mock Bluetooth toggle
3. Test on physical devices with various Android versions
4. Implement actual Bluetooth device discovery and pairing UI

### Medium Priority
1. Add battery optimization whitelist prompt
2. Implement notification for background alerts
3. Add log export functionality (CSV/JSON)
4. Improve error handling and user feedback
5. Add connection retry manual trigger

### Low Priority
1. Support for more voltage levels (if needed)
2. Customizable alert thresholds
3. Historical data visualization
4. Multi-device support

## License

Proprietary - Korean Power Company

## Contact

For issues or questions, contact the development team.

---

**⚠️ Safety Notice**: This is safety-critical software. Any modifications should be thoroughly tested before deployment to field personnel.
