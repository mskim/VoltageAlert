# Bluetooth Pairing Issue - Firmware Modification Request
**ST9401-UP Voltage Detection Device**

---

## ENGLISH VERSION

### Subject: Bluetooth Pairing Compatibility Issue - Firmware Modification Request for ST9401-UP

Dear [Manufacturer Name],

We are developing an Android application (VoltageAlert) to work with your ST9401-UP voltage detection device. During development and testing, we have encountered a **Bluetooth pairing authentication failure** that prevents successful connection between Android smartphones and the ST9401-UP device.

### Technical Problem Description

**Issue:** Android devices cannot successfully pair with ST9401-UP due to SSP (Secure Simple Pairing) authentication failure.

**Error Details:**
- Device: ST9401-UP (ESP32-based, Classic Bluetooth)
- Android Version: 12+ (API 31+)
- Error Code: `HCI_ERR_AUTH_FAILURE` (reason code 5)
- Bond State: Device enters BOND_BONDING state, then fails to BOND_NONE

**Root Cause Analysis:**
1. The ST9401-UP ESP32 firmware currently uses **SSP (Secure Simple Pairing)** as the default Bluetooth security mode
2. SSP with numeric comparison requires **both devices** to display and confirm a 6-digit pairing code
3. The ST9401-UP device **has no display** (wearable helmet/wrist device)
4. Android shows the 6-digit code and waits for user confirmation
5. The ST9401-UP cannot confirm the code, causing authentication timeout and failure
6. Modern Android security policies (API 26+) strictly enforce SSP for Classic Bluetooth

**Testing Performed:**
- Attempted pairing with multiple PIN codes: 1234, 9527, 0000, 1111, 0001
- All PIN attempts ignored because SSP takes priority over legacy PIN pairing
- Device successfully detected via Bluetooth Classic discovery
- Device name: "ST9401-UP"
- MAC Address: [varies by device]
- Service UUID: 00001101-0000-1000-8000-00805f9b34fb (SPP - Serial Port Profile)

### Requested Firmware Modifications

We respectfully request **one of the following firmware modifications** to enable successful Android pairing:

#### **OPTION A: Enable Legacy PIN-Based Pairing (Recommended)**

**Configuration Changes:**
```c
// ESP32 Arduino/ESP-IDF Configuration
esp_bt_sp_param_t param_type = ESP_BT_SP_IOCAP_MODE;
esp_bt_io_cap_t iocap = ESP_BT_IO_CAP_NONE; // Disable SSP numeric confirmation
esp_bt_gap_set_security_param(param_type, &iocap, sizeof(uint8_t));

// Set fixed PIN code
esp_bt_pin_type_t pin_type = ESP_BT_PIN_TYPE_FIXED;
esp_bt_pin_code_t pin_code = {'1', '2', '3', '4'}; // Default PIN: 1234
esp_bt_gap_set_pin(pin_type, 4, pin_code);
```

**Benefits:**
- Compatible with all Android versions (API 26+)
- No user interaction required on device side
- Industry-standard approach for industrial/medical devices
- User simply enters PIN on smartphone

**Recommended Default PIN:** `1234` (most common) or `9527` (ESP32 standard)

---

#### **OPTION B: Enable "Just Works" SSP Mode**

**Configuration Changes:**
```c
// Set I/O capability to "NoInputNoOutput" for Just Works SSP
esp_bt_io_cap_t iocap = ESP_BT_IO_CAP_NONE;
esp_bt_gap_set_security_param(ESP_BT_SP_IOCAP_MODE, &iocap, sizeof(uint8_t));

// Automatically accept pairing requests
// Implement in esp_bt_gap_cb event handler:
case ESP_BT_GAP_AUTH_CMPL_EVT:
    if (param->auth_cmpl.stat == ESP_BT_STATUS_SUCCESS) {
        // Auto-accept without user confirmation
    }
```

**Benefits:**
- Modern SSP security maintained
- Automatic pairing without user interaction
- No PIN required

**Trade-off:** Lower security (no man-in-the-middle protection)

---

#### **OPTION C: Add BLE (Bluetooth Low Energy) Support**

If feasible, adding BLE alongside Classic Bluetooth would provide:
- Better Android compatibility
- Lower power consumption
- Modern security options
- Wider device support

**Note:** ESP32-S3 has BLE 5.0 support; current Classic Bluetooth suggests original ESP32 chip

---

### Additional Information Needed

To assist with development, please provide:

1. **Current Bluetooth Configuration:**
   - Is SSP currently enabled? (Yes/No)
   - What is the I/O capability setting? (NoInputNoOutput / DisplayYesNo / KeyboardOnly / etc.)
   - Is there a fixed PIN code? If yes, what is it?

2. **Firmware Update Process:**
   - Can firmware be updated via OTA (Over-The-Air)?
   - Is there a configuration mode accessible via AT commands?
   - Do you provide firmware update tools for customers?

3. **Hardware Specifications:**
   - Exact ESP32 chip model (ESP32, ESP32-S2, ESP32-S3, ESP32-C3)?
   - Is there Bluetooth LE (BLE) support available?

4. **Documentation:**
   - Bluetooth pairing procedure documentation
   - Serial communication protocol specification (packet format, CRC)
   - Android app integration guide (if available)

---

### Impact on Project

This issue is **blocking production deployment** of our Android application. We have implemented all available workarounds on the Android side, but the firmware-level SSP configuration prevents successful pairing.

**Current Workaround:** Mock mode for development/testing (simulated data)
**Required for Production:** Real device connectivity

---

### Timeline

We would appreciate:
- **Initial response:** Within 1 week
- **Firmware solution:** Within 2-4 weeks (if possible)
- **Testing period:** 1 week for validation

---

### Contact Information

**Project:** VoltageAlert (활선 접근 경보기) - High Voltage Proximity Warning System
**Platform:** Android 8.0+ (API 26+)
**Development Language:** Kotlin
**Bluetooth Library:** Nordic BLE Library + Android BluetoothAdapter

For technical questions, please contact:
- **Developer:** [Your Name]
- **Email:** [Your Email]
- **Phone:** [Your Phone]

Thank you for your attention to this matter. We look forward to your response and continued collaboration.

Best regards,
[Your Name]
[Your Company/Organization]

---
---

## 한국어 버전

### 제목: 블루투스 페어링 호환성 문제 - ST9401-UP 펌웨어 수정 요청

안녕하세요,

저희는 귀사의 ST9401-UP 전압 감지 장치와 연동되는 안드로이드 애플리케이션(VoltageAlert)을 개발 중입니다. 개발 및 테스트 과정에서 **블루투스 페어링 인증 실패** 문제가 발생하여 안드로이드 스마트폰과 ST9401-UP 장치 간 연결이 불가능한 상황입니다.

### 기술적 문제 상황

**문제점:** 안드로이드 기기가 ST9401-UP와 SSP(Secure Simple Pairing) 인증 실패로 페어링할 수 없습니다.

**오류 상세 정보:**
- 장치: ST9401-UP (ESP32 기반, 클래식 블루투스)
- 안드로이드 버전: 12+ (API 31+)
- 오류 코드: `HCI_ERR_AUTH_FAILURE` (reason code 5)
- Bond 상태: BOND_BONDING 진입 후 BOND_NONE으로 실패

**원인 분석:**
1. ST9401-UP ESP32 펌웨어가 현재 **SSP(Secure Simple Pairing)**를 기본 블루투스 보안 모드로 사용
2. SSP 숫자 비교 방식은 **양쪽 기기 모두**에서 6자리 페어링 코드를 표시하고 확인해야 함
3. ST9401-UP 장치는 **디스플레이가 없음** (헬멧/손목 착용 장치)
4. 안드로이드는 6자리 코드를 표시하고 사용자 확인을 대기
5. ST9401-UP는 코드를 확인할 수 없어 인증 타임아웃 및 실패 발생
6. 최신 안드로이드 보안 정책(API 26+)은 클래식 블루투스에 대해 SSP를 엄격히 적용

**수행한 테스트:**
- 여러 PIN 코드로 페어링 시도: 1234, 9527, 0000, 1111, 0001
- SSP가 레거시 PIN 페어링보다 우선하여 모든 PIN 시도가 무시됨
- 블루투스 클래식 검색으로 장치 발견 성공
- 장치 이름: "ST9401-UP"
- MAC 주소: [장치마다 다름]
- 서비스 UUID: 00001101-0000-1000-8000-00805f9b34fb (SPP - Serial Port Profile)

### 펌웨어 수정 요청 사항

성공적인 안드로이드 페어링을 위해 **다음 중 하나의 펌웨어 수정**을 요청드립니다:

#### **옵션 A: 레거시 PIN 기반 페어링 활성화 (권장)**

**설정 변경:**
```c
// ESP32 Arduino/ESP-IDF 설정
esp_bt_sp_param_t param_type = ESP_BT_SP_IOCAP_MODE;
esp_bt_io_cap_t iocap = ESP_BT_IO_CAP_NONE; // SSP 숫자 확인 비활성화
esp_bt_gap_set_security_param(param_type, &iocap, sizeof(uint8_t));

// 고정 PIN 코드 설정
esp_bt_pin_type_t pin_type = ESP_BT_PIN_TYPE_FIXED;
esp_bt_pin_code_t pin_code = {'1', '2', '3', '4'}; // 기본 PIN: 1234
esp_bt_gap_set_pin(pin_type, 4, pin_code);
```

**장점:**
- 모든 안드로이드 버전과 호환 (API 26+)
- 장치 측 사용자 조작 불필요
- 산업용/의료용 장치의 표준 방식
- 사용자가 스마트폰에서 PIN만 입력

**권장 기본 PIN:** `1234` (가장 일반적) 또는 `9527` (ESP32 표준)

---

#### **옵션 B: "Just Works" SSP 모드 활성화**

**설정 변경:**
```c
// I/O 기능을 "NoInputNoOutput"으로 설정하여 Just Works SSP 사용
esp_bt_io_cap_t iocap = ESP_BT_IO_CAP_NONE;
esp_bt_gap_set_security_param(ESP_BT_SP_IOCAP_MODE, &iocap, sizeof(uint8_t));

// 페어링 요청 자동 수락
// esp_bt_gap_cb 이벤트 핸들러에서 구현:
case ESP_BT_GAP_AUTH_CMPL_EVT:
    if (param->auth_cmpl.stat == ESP_BT_STATUS_SUCCESS) {
        // 사용자 확인 없이 자동 수락
    }
```

**장점:**
- 최신 SSP 보안 유지
- 사용자 조작 없이 자동 페어링
- PIN 불필요

**단점:** 보안 수준 낮음 (중간자 공격 방어 없음)

---

#### **옵션 C: BLE (Bluetooth Low Energy) 지원 추가**

가능한 경우, 클래식 블루투스와 함께 BLE 추가 시:
- 안드로이드 호환성 향상
- 저전력 소비
- 최신 보안 옵션
- 더 넓은 장치 지원

**참고:** ESP32-S3는 BLE 5.0 지원; 현재 클래식 블루투스 사용은 오리지널 ESP32 칩 사용을 시사

---

### 추가 필요 정보

개발 지원을 위해 다음 정보를 제공해 주시기 바랍니다:

1. **현재 블루투스 설정:**
   - SSP가 현재 활성화되어 있습니까? (예/아니오)
   - I/O 기능 설정은 무엇입니까? (NoInputNoOutput / DisplayYesNo / KeyboardOnly 등)
   - 고정 PIN 코드가 있습니까? 있다면 무엇입니까?

2. **펌웨어 업데이트 절차:**
   - OTA(무선) 펌웨어 업데이트가 가능합니까?
   - AT 명령어로 접근 가능한 설정 모드가 있습니까?
   - 고객용 펌웨어 업데이트 도구를 제공합니까?

3. **하드웨어 사양:**
   - 정확한 ESP32 칩 모델은 무엇입니까? (ESP32, ESP32-S2, ESP32-S3, ESP32-C3?)
   - 블루투스 LE(BLE) 지원이 가능합니까?

4. **문서:**
   - 블루투스 페어링 절차 문서
   - 시리얼 통신 프로토콜 사양 (패킷 형식, CRC)
   - 안드로이드 앱 통합 가이드 (있는 경우)

---

### 프로젝트 영향

이 문제로 인해 안드로이드 애플리케이션의 **상용화 배포가 중단**되었습니다. 안드로이드 측에서 가능한 모든 우회 방법을 구현했으나, 펌웨어 수준의 SSP 설정이 페어링 성공을 막고 있습니다.

**현재 임시방편:** 개발/테스트용 Mock 모드 (시뮬레이션 데이터)
**상용화를 위한 필수사항:** 실제 장치 연결

---

### 일정

다음과 같이 요청드립니다:
- **초기 응답:** 1주일 이내
- **펌웨어 솔루션:** 2-4주 이내 (가능한 경우)
- **테스트 기간:** 검증을 위한 1주일

---

### 연락처 정보

**프로젝트:** VoltageAlert (활선 접근 경보기) - 고압 전선 접근 경보 시스템
**플랫폼:** Android 8.0+ (API 26+)
**개발 언어:** Kotlin
**블루투스 라이브러리:** Nordic BLE Library + Android BluetoothAdapter

기술 문의사항은 다음으로 연락 주시기 바랍니다:
- **개발자:** [귀하의 이름]
- **이메일:** [귀하의 이메일]
- **전화:** [귀하의 전화번호]

이 문제에 관심을 가져주셔서 감사합니다. 귀사의 회신과 지속적인 협력을 기대합니다.

감사합니다.
[귀하의 이름]
[귀하의 회사/조직]

---

## Technical Reference - For Engineering Team

### Relevant Android Logs (Evidence)

```
Need BLUETOOTH PRIVILEGED permission: Neither user 10398 nor current process has android.permission.BLUETOOTH_PRIVILEGED.
Bond state changed: bt_status:failure reason:HCI_ERR_AUTH_FAILURE
Disconnected: xx:xx:xx:xx:da:e7 classic reason:AUTHENTICATION_FAILURE
```

### ESP32 Documentation References

- [ESP32 Classic Bluetooth SPP (IDFGH-484)](https://github.com/espressif/esp-idf/issues/2774)
- [ESP-IDF Bluetooth Classic API](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/bluetooth/esp_gap_bt.html)
- [ESP32 Security Configuration](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/bluetooth/esp_bt_main.html)

### Recommended Firmware Configuration File

```c
// bluetooth_config.h - Recommended configuration for ST9401-UP

#define USE_LEGACY_PAIRING  true
#define DEFAULT_PIN_CODE    "1234"
#define SSP_MODE            ESP_BT_IO_CAP_NONE
#define AUTO_ACCEPT_PAIRING true
#define DEVICE_NAME         "ST9401-UP"
#define SPP_SERVICE_UUID    "00001101-0000-1000-8000-00805f9b34fb"
```

---

**Document Version:** 1.0
**Date:** February 7, 2026
**Author:** VoltageAlert Development Team
