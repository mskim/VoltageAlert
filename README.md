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
- ✅ Unit tests for core logic

## Features

### Safety-Critical Alerts
- **Visual**: Full-screen alert activity with voltage-specific images
- **Audio**: Two-tone siren (1200Hz/800Hz) using USAGE_ALARM stream
- **Haptic**: Strong vibration pattern
- **Wake Lock**: Automatically turns screen on and shows alert on lock screen

### Voltage Detection
Monitors seven voltage levels:
- **Low Voltage**: 220V, 380V
- **High Voltage**: 154KV, 229KV, 345KV, 500KV, 765KV
- **Diagnostics**: Self-test OK/NG indicators

### Event Logging
- Maximum 99 log entries (FIFO)
- Duplicate suppression: Hides logs after 3 identical consecutive entries
- Format: `1. 2025/12/23 08:45:25 220V`
- Persistent storage using Room database

### Bluetooth Protocol
Custom 10-byte packet protocol:
```
[0xAA][VoltageCode][SeqHi][SeqLo][CRC8][Padding...][0x55]
```
- Service UUID: `0000ffe0-0000-1000-8000-00805f9b34fb`
- Characteristic UUID: `0000ffe1-0000-1000-8000-00805f9b34fb`
- Auto-reconnect with exponential backoff
- CRC8 validation

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
Default device name filter: `"VoltSensor-"`

To change, modify `BluetoothService.kt`:
```kotlin
private const val DEVICE_NAME_PREFIX = "YourDeviceName-"
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

## Known Limitations

1. **Settings Activity**: Placeholder implementation (preferences not functional yet)
2. **Mock Bluetooth Toggle**: Debug menu not yet implemented in MainActivity
3. **Launcher Icons**: Using placeholder adaptive icons
4. **Network Issues**: No handling for sensor firmware updates
5. **Battery Optimization**: May need user whitelisting for reliable background operation

## Next Steps

### High Priority
1. Implement SettingsActivity with PreferenceFragment
2. Add debug menu for mock Bluetooth toggle
3. Create proper launcher icons
4. Test on physical devices with various Android versions
5. Implement actual Bluetooth device discovery and pairing UI

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
