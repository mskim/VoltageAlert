# Firmware Change Request: BLE Broadcast Mode
# 펌웨어 변경 요청서: BLE 브로드캐스트 모드

**Date / 날짜:** 2026-02-11
**From / 요청자:** VoltageAlert Android App Development Team
**To / 수신자:** ESSYSTEM Sensor Firmware Team
**Priority / 우선순위:** HIGH / 높음 (Safety-Critical / 안전 관련)

---

## 1. Problem Statement / 문제점

### English

The current ESSYSTEM sensor firmware turns BLE off when no voltage is detected (to save battery). When the sensor detects a voltage, it turns BLE on and starts advertising. The Android app must then:

1. **Scan** and find the device (~100-500ms)
2. **GATT Connect** to the device (~1-3s)
3. **Service Discovery** (~1-2s)
4. **Enable Notifications** (~0.5s)
5. **Receive first data packet**

**Total time: 3-7 seconds delay before the worker is warned.**

This delay is a **safety risk** for workers near high-voltage power lines. The customer's biggest complaint is this reconnection delay.

### 한국어

현재 ESSYSTEM 센서 펌웨어는 전압이 감지되지 않으면 배터리 절약을 위해 BLE를 끕니다. 센서가 전압을 감지하면 BLE를 켜고 advertising을 시작합니다. 이때 Android 앱은 다음 단계를 거쳐야 합니다:

1. **스캔** - 디바이스 발견 (~100-500ms)
2. **GATT 연결** (~1-3초)
3. **서비스 검색** (~1-2초)
4. **알림(Notification) 활성화** (~0.5초)
5. **첫 번째 데이터 패킷 수신**

**총 지연 시간: 작업자에게 경고하기까지 3-7초 지연**

이 지연은 고압 송전선 근처 작업자들에게 **안전 위험**입니다. 고객의 가장 큰 불만이 이 재연결 지연 문제입니다.

---

## 2. Proposed Solution / 제안 솔루션

### English

**Broadcast Mode**: Embed voltage data directly inside the BLE advertisement packet's manufacturer-specific data field. The phone reads voltage from scan results instantly — no GATT connection, no service discovery, no notification setup needed.

**Key Point: Zero battery impact.** The BLE on/off behavior stays exactly the same:
- BLE OFF when no voltage detected (same as current)
- BLE ON when voltage detected (same as current)
- The ONLY change: add 5 bytes of manufacturer-specific data to the advertisement packet that is already being broadcast

### 한국어

**브로드캐스트 모드**: BLE advertisement 패킷의 manufacturer-specific data 필드에 전압 데이터를 직접 포함시킵니다. 폰은 스캔 결과에서 즉시 전압을 읽을 수 있습니다 — GATT 연결, 서비스 검색, 알림 설정이 필요 없습니다.

**핵심: 배터리 영향 없음.** BLE 켜기/끄기 동작은 현재와 완전히 동일합니다:
- 전압 미감지 시 BLE OFF (현재와 동일)
- 전압 감지 시 BLE ON (현재와 동일)
- **유일한 변경**: 이미 브로드캐스트 중인 advertisement 패킷에 5바이트의 manufacturer-specific data 추가

---

## 3. Speed Comparison / 속도 비교

```
CURRENT (slow / 현재 - 느림):
  Sensor detects voltage / 센서 전압 감지
  → BLE ON, start advertising
  → Phone scans, finds device (100-500ms)
  → Phone GATT connects (1-3s) ← BOTTLENECK / 병목
  → Service discovery (1-2s)   ← BOTTLENECK / 병목
  → Enable notifications (0.5s) ← BOTTLENECK / 병목
  → Receive data
  → TOTAL: 3-7 seconds / 총: 3-7초

BROADCAST MODE (fast / 브로드캐스트 모드 - 빠름):
  Sensor detects voltage / 센서 전압 감지
  → BLE ON, start advertising WITH voltage data embedded
  → Phone scans, sees advertisement WITH voltage data (100-500ms)
  → Phone reads voltage from scan result (instant)
  → Trigger alarm / 알람 발생
  → TOTAL: 100-500ms / 총: 100-500ms
```

---

## 4. Why Broadcast Mode (Not Always-On BLE) / 왜 브로드캐스트 모드인가 (상시 BLE 켜기가 아닌 이유)

| | Continuous BLE (rejected) / 상시 BLE (기각) | Broadcast Mode (proposed) / 브로드캐스트 모드 (제안) |
|---|---|---|
| Battery / 배터리 | 1/3 of current / 현재의 1/3 | **Same as current / 현재와 동일** |
| Detection speed / 감지 속도 | < 500ms | **100-500ms** |
| Firmware complexity / 펌웨어 복잡도 | High (connection mgmt, heartbeat) / 높음 | **Low (add data to existing ad) / 낮음** |
| Firmware change scope / 변경 범위 | Major rewrite / 대규모 재작성 | **Small addition / 소규모 추가** |
| Risk / 위험도 | High / 높음 | **Low / 낮음** |

---

## 5. Advertisement Packet Format / Advertisement 패킷 포맷

### BLE Advertisement Data Structure / BLE Advertisement 데이터 구조

The advertisement packet must contain the following AD structures:

```
AD Structure 1: Flags (3 bytes)
  Length: 0x02
  Type:   0x01 (Flags)
  Data:   0x06 (General Discoverable | BLE Only)

AD Structure 2: Complete Local Name (10 bytes)
  Length: 0x09
  Type:   0x09 (Complete Local Name)
  Data:   "ESSYSTEM" (8 bytes)

AD Structure 3: 16-bit Service UUID (4 bytes)
  Length: 0x03
  Type:   0x03 (Complete List of 16-bit Service UUIDs)
  Data:   0xF0, 0xFF  (0xFFF0 - for scanner filter compatibility)

AD Structure 4: Manufacturer Specific Data (5 bytes) ← NEW / 신규
  Length: 0x04
  Type:   0xFF (Manufacturer Specific Data)
  Data:   0xE5, 0x02  (Company ID: 0x02E5 = Espressif, little-endian)
          [VoltageCode] (1 byte - see table below)
```

**Total: 3 + 10 + 4 + 5 = 22 bytes** (within 31-byte BLE advertising limit)

### Voltage Code Table / 전압 코드 표

| VoltageCode / 전압코드 | Voltage / 전압 | Description / 설명 |
|---|---|---|
| `0x01` | 220V | Low voltage / 저압 |
| `0x02` | 380V | Low voltage / 저압 |
| `0x03` | 22.9KV | High voltage / 고압 |
| `0x04` | 154KV | High voltage / 고압 |
| `0x05` | 345KV | Ultra-high voltage / 초고압 |
| `0x06` | 500KV | Ultra-high voltage / 초고압 |
| `0x07` | 765KV | Ultra-high voltage / 초고압 |

> **Note / 참고:** These byte codes match the existing GATT characteristic data format used by the current firmware. The Android app already maps these codes to voltage levels.
>
> **참고:** 이 바이트 코드는 현재 펌웨어에서 사용하는 GATT characteristic 데이터 포맷과 동일합니다. Android 앱은 이미 이 코드를 전압 레벨에 매핑하고 있습니다.

---

## 6. Advertising Parameters / Advertising 매개변수

| Parameter / 매개변수 | Value / 값 | Reason / 이유 |
|---|---|---|
| Type | `ADV_IND` (connectable, scannable) | Backward compatibility - old app can still GATT connect / 하위 호환성 |
| Interval / 간격 | 100ms (`BLE_GAP_ADV_ITVL_MS(100)`) | Fast detection / 빠른 감지 |
| Duration / 지속 | As long as voltage detected / 전압 감지 중 계속 | Same as current behavior / 현재와 동일 |
| TX Power | Same as current / 현재와 동일 | No change needed / 변경 불필요 |

---

## 7. State Machine / 상태 머신

**No change from current behavior / 현재 동작과 변경 없음:**

```
┌─────────────────┐                      ┌─────────────────────────┐
│   IDLE STATE    │   Voltage Detected   │    ADVERTISING STATE    │
│   BLE = OFF     │ ──────────────────── │    BLE = ON             │
│   Battery Save  │                      │    Broadcasting voltage │
│                 │   Voltage Gone       │    data in adv packet   │
│                 │ ◄─────────────────── │    + GATT available     │
└─────────────────┘                      └─────────────────────────┘

Same as current / 현재와 동일:
- BLE OFF when idle → saves battery / 대기 시 BLE OFF → 배터리 절약
- BLE ON when voltage detected / 전압 감지 시 BLE ON
- ONLY CHANGE: advertisement packet now includes voltage code
  유일한 변경: advertisement 패킷에 전압 코드 포함
```

---

## 8. ESP32-S3 NimBLE Implementation / ESP32-S3 NimBLE 구현 코드

### Complete Implementation Example / 전체 구현 예시

```c
#include "host/ble_hs.h"
#include "host/ble_gap.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

/* ===== Manufacturer-Specific Data ===== */
#define ESPRESSIF_COMPANY_ID_LO  0xE5  /* 0x02E5 little-endian */
#define ESPRESSIF_COMPANY_ID_HI  0x02

/* Voltage codes - MUST match Android app VoltageLevel.byteCode */
#define VOLTAGE_CODE_220V   0x01
#define VOLTAGE_CODE_380V   0x02
#define VOLTAGE_CODE_22_9KV 0x03
#define VOLTAGE_CODE_154KV  0x04
#define VOLTAGE_CODE_345KV  0x05
#define VOLTAGE_CODE_500KV  0x06
#define VOLTAGE_CODE_765KV  0x07

/* Current voltage being advertised */
static uint8_t current_voltage_code = 0x00;

/* Manufacturer-specific data: [CompanyID_Lo][CompanyID_Hi][VoltageCode] */
static uint8_t manufacturer_data[3] = {
    ESPRESSIF_COMPANY_ID_LO,
    ESPRESSIF_COMPANY_ID_HI,
    0x00  /* voltage code - updated dynamically */
};

/* ===== Advertisement Data Setup ===== */

/**
 * Build and set advertisement data with voltage code.
 * 전압 코드를 포함한 advertisement 데이터를 구성하고 설정합니다.
 */
static void set_adv_data(uint8_t voltage_code) {
    struct ble_hs_adv_fields fields = {0};

    /* Flags: General Discoverable | BLE Only */
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;

    /* Complete Local Name: "ESSYSTEM" */
    const char *device_name = "ESSYSTEM";
    fields.name = (uint8_t *)device_name;
    fields.name_len = strlen(device_name);
    fields.name_is_complete = 1;

    /* 16-bit Service UUID: 0xFFF0 (for scanner filter compatibility) */
    static ble_uuid16_t svc_uuid = BLE_UUID16_INIT(0xFFF0);
    fields.uuids16 = &svc_uuid;
    fields.num_uuids16 = 1;
    fields.uuids16_is_complete = 1;

    /* Manufacturer Specific Data: [CompanyID][VoltageCode] */
    manufacturer_data[2] = voltage_code;
    fields.mfg_data = manufacturer_data;
    fields.mfg_data_len = sizeof(manufacturer_data);

    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "Failed to set adv data: rc=%d", rc);
    }
}

/* ===== Advertising Control ===== */

/**
 * Start BLE advertising with voltage data.
 * Called when voltage is detected.
 *
 * 전압 데이터를 포함하여 BLE advertising을 시작합니다.
 * 전압이 감지되면 호출됩니다.
 */
void start_voltage_advertising(uint8_t voltage_code) {
    struct ble_gap_adv_params adv_params = {0};

    /* Stop any existing advertising */
    ble_gap_adv_stop();

    /* Set advertisement data with voltage code */
    current_voltage_code = voltage_code;
    set_adv_data(voltage_code);

    /* ADV_IND: connectable + scannable (backward compatible with GATT) */
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;  /* connectable */
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;  /* general discoverable */

    /* 100ms interval for fast detection */
    adv_params.itvl_min = BLE_GAP_ADV_ITVL_MS(100);
    adv_params.itvl_max = BLE_GAP_ADV_ITVL_MS(100);

    int rc = ble_gap_adv_start(
        BLE_OWN_ADDR_PUBLIC,  /* own address type */
        NULL,                  /* directed address (NULL = undirected) */
        BLE_HS_FOREVER,        /* duration: forever until stopped */
        &adv_params,
        gap_event_handler,     /* existing GAP event handler */
        NULL
    );

    if (rc != 0) {
        ESP_LOGE(TAG, "Failed to start advertising: rc=%d", rc);
    } else {
        ESP_LOGI(TAG, "Advertising started with voltage code: 0x%02X", voltage_code);
    }
}

/**
 * Update voltage code in advertisement data without restarting advertising.
 * Call this when the detected voltage level changes while already advertising.
 *
 * Advertising을 재시작하지 않고 advertisement 데이터의 전압 코드를 업데이트합니다.
 * 이미 advertising 중에 감지된 전압 레벨이 변경될 때 호출합니다.
 */
void update_voltage_in_advertisement(uint8_t new_voltage_code) {
    if (current_voltage_code == new_voltage_code) {
        return;  /* No change needed */
    }

    current_voltage_code = new_voltage_code;

    /* Update the advertisement data - NimBLE handles this while advertising */
    set_adv_data(new_voltage_code);

    ESP_LOGI(TAG, "Updated advertisement voltage code: 0x%02X", new_voltage_code);
}

/**
 * Stop BLE advertising.
 * Called when voltage is no longer detected.
 *
 * BLE advertising을 중지합니다.
 * 전압이 더 이상 감지되지 않으면 호출됩니다.
 */
void stop_voltage_advertising(void) {
    ble_gap_adv_stop();
    current_voltage_code = 0x00;
    ESP_LOGI(TAG, "Advertising stopped - no voltage detected");
}

/* ===== Integration with Existing Code ===== */

/**
 * EXISTING: Your voltage detection callback.
 * CHANGE: Call start_voltage_advertising() instead of (or in addition to)
 *         just starting plain advertising.
 *
 * 기존: 전압 감지 콜백.
 * 변경: 기존 plain advertising 시작 대신 (또는 추가로)
 *       start_voltage_advertising()을 호출합니다.
 *
 * Example integration:
 */
void on_voltage_detected(int voltage_type) {
    uint8_t voltage_code;

    switch (voltage_type) {
        case 220:   voltage_code = VOLTAGE_CODE_220V;   break;
        case 380:   voltage_code = VOLTAGE_CODE_380V;   break;
        case 22900: voltage_code = VOLTAGE_CODE_22_9KV; break;
        case 154000: voltage_code = VOLTAGE_CODE_154KV;  break;
        case 345000: voltage_code = VOLTAGE_CODE_345KV;  break;
        case 500000: voltage_code = VOLTAGE_CODE_500KV;  break;
        case 765000: voltage_code = VOLTAGE_CODE_765KV;  break;
        default:    return;  /* Unknown voltage */
    }

    /* Start advertising with voltage data embedded */
    start_voltage_advertising(voltage_code);
}

void on_voltage_gone(void) {
    /* Stop advertising - same as current behavior */
    stop_voltage_advertising();
}
```

### Integration Notes / 통합 참고사항

**What to keep (unchanged) / 유지할 것 (변경 없음):**
- Existing GATT service (UUID: `0000FFF0`) and characteristic (UUID: `0000FFF1`)
- Existing GATT notification/read data format
- BLE on/off logic based on voltage detection

**What to add / 추가할 것:**
- Manufacturer-specific data field in advertisement packet (3 bytes of data)
- `set_adv_data()` function to build advertisement data
- Call `start_voltage_advertising(voltage_code)` instead of plain `ble_gap_adv_start()`
- Call `update_voltage_in_advertisement(new_code)` when voltage level changes

**What NOT to change / 변경하지 말 것:**
- Battery management (BLE on/off timing stays the same)
- GATT service/characteristic setup
- Connection handling for existing apps

---

## 9. Backward Compatibility / 하위 호환성

| Feature / 기능 | Old Android App / 기존 앱 | New Android App / 새 앱 |
|---|---|---|
| Scanning / 스캔 | Works (device name "ESSYSTEM" unchanged) | Works (same scan filter) |
| GATT Connection | Works (`ADV_IND` is connectable) | Not needed for voltage alerts |
| Service/Characteristic | Works (unchanged) | Available as fallback |
| Voltage data source | GATT notification (FFF1) | Advertisement manufacturer data |
| Detection speed / 감지 속도 | 3-7 seconds | **100-500ms** |

**Both old and new Android apps work simultaneously with the updated firmware.**
**기존 앱과 새 앱 모두 업데이트된 펌웨어와 동시에 작동합니다.**

---

## 10. Power Analysis / 전력 분석

### Current Firmware / 현재 펌웨어:
```
Idle: BLE OFF → ~10μA (deep sleep)
Active: BLE ON → advertising at current interval → ~10-15mA
Duty cycle: depends on voltage detection frequency
```

### After Change / 변경 후:
```
Idle: BLE OFF → ~10μA (deep sleep)  ← SAME / 동일
Active: BLE ON → advertising at 100ms interval → ~10-15mA  ← SAME / 동일
Duty cycle: depends on voltage detection frequency  ← SAME / 동일
```

**The advertisement packet is only 3 bytes longer. This has negligible impact on transmission power/time.**
**Advertisement 패킷이 3바이트만 길어집니다. 이는 전송 전력/시간에 무시할 수 있는 영향입니다.**

---

## 11. Testing Procedure / 테스트 절차

### Using nRF Connect App / nRF Connect 앱 사용

1. **Install nRF Connect** on an Android/iOS phone
   nRF Connect를 Android/iOS 폰에 설치합니다

2. **Start scanning** in nRF Connect
   nRF Connect에서 스캔을 시작합니다

3. **Trigger voltage detection** on the sensor
   센서에서 전압 감지를 트리거합니다

4. **Verify advertisement data** in nRF Connect:
   nRF Connect에서 advertisement 데이터를 확인합니다:

   - Device name should show "ESSYSTEM"
     디바이스 이름이 "ESSYSTEM"으로 표시되어야 합니다
   - Look for "Manufacturer Specific Data" section
     "Manufacturer Specific Data" 섹션을 확인합니다
   - Company ID should show "0x02E5" (Espressif)
     Company ID가 "0x02E5" (Espressif)로 표시되어야 합니다
   - Data byte should match the voltage code (e.g., `0x01` for 220V)
     데이터 바이트가 전압 코드와 일치해야 합니다 (예: 220V의 경우 `0x01`)

5. **Verify GATT still works** (backward compatibility):
   GATT가 여전히 작동하는지 확인합니다 (하위 호환성):

   - Tap "CONNECT" in nRF Connect
     nRF Connect에서 "CONNECT"를 탭합니다
   - Verify service UUID `0000FFF0` appears
     서비스 UUID `0000FFF0`이 표시되는지 확인합니다
   - Verify characteristic `0000FFF1` has READ and NOTIFY
     특성 `0000FFF1`에 READ와 NOTIFY가 있는지 확인합니다
   - Enable notifications and verify voltage data is received
     알림을 활성화하고 전압 데이터가 수신되는지 확인합니다

6. **Test voltage change**: While advertising, change the detected voltage level
   **전압 변경 테스트**: advertising 중에 감지된 전압 레벨을 변경합니다

   - Verify the manufacturer-specific data byte updates in real-time
     manufacturer-specific data 바이트가 실시간으로 업데이트되는지 확인합니다

7. **Test BLE off**: Remove voltage source
   **BLE 끄기 테스트**: 전압 소스를 제거합니다

   - Verify device disappears from nRF Connect scan results
     nRF Connect 스캔 결과에서 디바이스가 사라지는지 확인합니다
   - Confirms BLE turns off properly (battery savings preserved)
     BLE가 정상적으로 꺼지는지 확인합니다 (배터리 절약 유지)

### Expected nRF Connect Display / 예상 nRF Connect 표시:

```
ESSYSTEM                           -45 dBm
├── Flags: 0x06
│   General Discoverable | BLE Only
├── Complete Local Name: ESSYSTEM
├── Complete List of 16-bit Service UUIDs: 0xFFF0
└── Manufacturer Specific Data:
    Company: Espressif (0x02E5)
    Data: 0x01                    ← Voltage code (220V)
```

---

## 12. Summary of Required Changes / 필요한 변경 사항 요약

### For Firmware Team / 펌웨어 팀:

1. **Add** manufacturer-specific data to the advertisement packet (3 bytes: Company ID + Voltage Code)
   Advertisement 패킷에 manufacturer-specific data 추가 (3바이트: Company ID + 전압 코드)

2. **Add** `set_adv_data()` function to build advertisement data with voltage code
   전압 코드를 포함한 advertisement 데이터를 구성하는 `set_adv_data()` 함수 추가

3. **Modify** the voltage detection callback to call `start_voltage_advertising(voltage_code)`
   전압 감지 콜백을 `start_voltage_advertising(voltage_code)` 호출로 수정

4. **Keep** all existing GATT service/characteristic code unchanged
   기존 GATT 서비스/특성 코드는 모두 변경 없이 유지

5. **Keep** BLE on/off timing unchanged (battery savings preserved)
   BLE 켜기/끄기 타이밍 변경 없이 유지 (배터리 절약 유지)

### Estimated Effort / 예상 작업량:
- **Code changes**: ~50 lines of C code (add to existing firmware)
- **Testing**: 1-2 hours with nRF Connect verification
- **Risk**: Low (additive change, no existing behavior modified)

---

## 13. Contact / 연락처

For questions about the Android app integration, please contact the development team.
Android 앱 통합에 관한 질문은 개발 팀에 연락해 주세요.

**Android app will be updated to support broadcast mode as soon as the firmware update is delivered.**
**펌웨어 업데이트가 제공되는 즉시 Android 앱을 브로드캐스트 모드를 지원하도록 업데이트할 예정입니다.**
