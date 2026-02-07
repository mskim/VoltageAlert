# VoltageAlert Installation Guide
## 활선 접근 경보기 설치 가이드

**Version:** 1.0.0
**Date:** February 2026
**For:** Android 8.0 and above

---

## 📱 English Version

### What is VoltageAlert?

VoltageAlert (활선 접근 경보기) is a workplace safety application designed for Korean power company workers. It provides real-time warnings when approaching dangerous high-voltage power lines through:

- 🔊 **Audio alerts** - Warning sounds
- 📳 **Haptic alerts** - Vibration patterns
- 🖼️ **Visual alerts** - Full-screen voltage warnings
- 📝 **Event logging** - Track all voltage detections

---

### System Requirements

**Minimum Requirements:**
- Android 8.0 (Oreo) or higher
- Bluetooth support
- 50 MB free storage
- Internet connection (for initial setup only)

**Recommended:**
- Android 12 or higher
- Samsung, LG, or other major brand devices
- Bluetooth 5.0+

---

### Installation Steps

#### Step 1: Enable Unknown Sources

Before installing VoltageAlert, you must allow installation from sources other than the Play Store.

**For Android 8-11:**
1. Open **Settings** (설정)
2. Tap **Security** or **Lock screen and security** (보안)
3. Find **Unknown sources** or **Install unknown apps** (알 수 없는 소스)
4. Enable the toggle switch
5. Confirm when prompted

**For Android 12+:**
1. Open **Settings** (설정)
2. Tap **Apps** (앱)
3. Tap **Special app access** (특별 액세스)
4. Tap **Install unknown apps** (알 수 없는 앱 설치)
5. Select your file manager or browser
6. Enable **Allow from this source** (이 소스 허용)

⚠️ **Security Note:** You can disable this after installation is complete.

---

#### Step 2: Download the APK File

Obtain the VoltageAlert APK file from your company IT department or supervisor via:

- 📧 **Email attachment:** `VoltageAlert-v1.0.0-release.apk`
- 💾 **USB drive:** Copy to your phone's Downloads folder
- ☁️ **Cloud storage:** Download from shared link (Google Drive, etc.)
- 📱 **Direct transfer:** Via Bluetooth or file sharing

**File Details:**
- **Filename:** VoltageAlert-v1.0.0-release.apk
- **Size:** Approximately 2.3 MB
- **Version:** 1.0.0

---

#### Step 3: Install the Application

1. **Locate the APK file:**
   - Open **My Files** or **Files** app
   - Navigate to **Downloads** folder
   - Find **VoltageAlert-v1.0.0-release.apk**

2. **Tap the APK file** to start installation

3. **Review permissions:**
   - The app will request permissions (see Permissions section below)
   - Tap **Install** (설치)

4. **Wait for installation:**
   - Installation takes 5-10 seconds
   - Do not interrupt the process

5. **Installation complete:**
   - Tap **Open** to launch immediately
   - Or find the app icon on your home screen

⚠️ **If you see a warning:**
- "This type of file can harm your device"
- Tap **OK** or **Install anyway** (무시하고 설치)
- This is normal for APK files not from Play Store

---

#### Step 4: Grant Permissions

When you first open VoltageAlert, it will request several permissions. **All permissions are required** for the app to function properly.

**Required Permissions:**

1. **📶 Nearby devices / Bluetooth** (근처 기기 / 블루투스)
   - **Why:** Connect to voltage sensor device
   - **Action:** Tap **Allow** (허용)

2. **📍 Location** (위치)
   - **Why:** Required for Bluetooth scanning on Android 12+
   - **Action:** Tap **Allow only while using the app** (앱 사용 중에만 허용)

3. **🔔 Notifications** (알림)
   - **Why:** Display voltage alerts even when app is in background
   - **Action:** Tap **Allow** (허용)

4. **📳 Vibration** (진동)
   - **Why:** Haptic alerts for dangerous voltage
   - **Action:** Automatically granted

⚠️ **Important:** If you deny any permission, the app may not work correctly. You can change permissions later in Android Settings → Apps → VoltageAlert → Permissions.

---

### First-Time Setup

After installation and granting permissions:

1. **Language Selection:**
   - The app will detect your system language
   - Switch between Korean/English in Settings if needed

2. **Bluetooth Setup:**
   - Ensure Bluetooth is enabled on your device
   - The app will guide you through sensor pairing

3. **Test Mode:**
   - For testing without a physical sensor
   - Go to Settings → Enable "Mock Mode"
   - This generates simulated voltage readings

---

### Using VoltageAlert

#### Main Screen

The main screen displays:
- **Connection Status:** Shows if sensor is connected
- **Current Voltage:** Real-time voltage reading
- **Event Log:** History of voltage detections (max 99 entries)
- **Scan Button:** Search for voltage sensor device

#### Connecting to Sensor

1. **Power on your ST9401-UP voltage sensor**
2. Tap **Scan** button in the app
3. **Wait for device discovery** (5-10 seconds)
4. App will show pairing instructions
5. **Enter PIN when prompted:**
   - Try: **1234** (most common)
   - Or: **9527** (ESP32 default)
   - Or: **0000**, **1111**, **0001**
6. Tap **Pair** in Android dialog
7. Connection status will show **"Connected"**

#### When Alert Triggers

When dangerous voltage is detected:

1. **📱 Screen:** Full-screen warning with voltage level
2. **🔊 Sound:** Two-tone siren (1200Hz/800Hz alternating)
3. **📳 Vibration:** Strong haptic pattern
4. **⏰ Duration:** Continues until you dismiss

**To dismiss alert:**
- Tap **OK** button on alert screen
- Move away from high-voltage area

---

### Settings

Access settings via **⚙️ icon** on main screen:

**Available Settings:**
- **Language:** Switch between Korean (한국어) and English
- **Mock Mode:** Enable for testing without sensor (개발자 모드)
- **Volume:** Adjust alert sound volume
- **Vibration:** Enable/disable haptic alerts
- **Clear Logs:** Delete all event history

---

### Troubleshooting

#### App Won't Install

**Problem:** "App not installed" error

**Solutions:**
1. Check free storage (need at least 50 MB)
2. Uninstall any previous version first
3. Ensure "Unknown sources" is enabled
4. Restart your phone and try again

---

#### Bluetooth Won't Connect

**Problem:** Can't find or connect to sensor

**Solutions:**
1. Ensure sensor is powered on
2. Check Bluetooth is enabled on phone
3. Try unpairing and pairing again:
   - Settings → Bluetooth → Forget device
   - Re-scan in VoltageAlert app
4. Try different PIN codes: 1234, 9527, 0000
5. Ensure location permission is granted

---

#### No Sound During Alert

**Problem:** Alert shows but no sound plays

**Solutions:**
1. Check phone volume (must be at least 50%)
2. Disable Do Not Disturb mode
3. Check VoltageAlert settings → Volume
4. Test with Mock Mode to verify

---

#### App Crashes or Closes

**Problem:** App unexpectedly closes

**Solutions:**
1. Restart your phone
2. Clear app cache: Settings → Apps → VoltageAlert → Storage → Clear Cache
3. Reinstall the app
4. Contact IT support if problem persists

---

### Uninstallation

To remove VoltageAlert:

1. Open **Settings** (설정)
2. Tap **Apps** (앱)
3. Find and tap **VoltageAlert**
4. Tap **Uninstall** (제거)
5. Confirm when prompted

---

### Support & Contact

**For Technical Issues:**
- Contact your company IT department
- Email: [Your support email]
- Phone: [Your support phone]

**App Version:** Check Settings → About to see your version

**Updates:** New versions will be distributed by your company IT department

---

### Privacy & Security

**Data Collection:**
- VoltageAlert only collects voltage readings and timestamps
- All data is stored locally on your device
- No data is sent to external servers
- Bluetooth connection is encrypted

**Permissions Usage:**
- Bluetooth: Only for sensor connection
- Location: Required by Android for Bluetooth scanning (not used for tracking)
- Notifications: Only for voltage alerts

---

### Legal

**Disclaimer:**
This app is a supplementary safety tool. It does NOT replace standard safety equipment, procedures, or training. Always follow your company's safety protocols and wear appropriate protective equipment when working near high-voltage lines.

**Copyright © 2026 VoltageAlert**
All rights reserved.

---
---

## 🇰🇷 한국어 버전

### VoltageAlert란 무엇인가요?

VoltageAlert (활선 접근 경보기)는 한국 전력 회사 직원을 위해 설계된 작업장 안전 애플리케이션입니다. 고압 전선에 접근할 때 실시간 경고를 제공합니다:

- 🔊 **음성 경고** - 경고음
- 📳 **진동 경고** - 진동 패턴
- 🖼️ **시각 경고** - 전체 화면 전압 경고
- 📝 **이벤트 로그** - 모든 전압 감지 기록

---

### 시스템 요구사항

**최소 요구사항:**
- Android 8.0 (Oreo) 이상
- 블루투스 지원
- 50 MB 이상 저장 공간
- 인터넷 연결 (초기 설정 시에만)

**권장 사양:**
- Android 12 이상
- 삼성, LG 등 주요 브랜드 기기
- 블루투스 5.0+

---

### 설치 단계

#### 1단계: 알 수 없는 소스 허용

VoltageAlert를 설치하기 전에 Play 스토어 이외의 소스에서 설치를 허용해야 합니다.

**Android 8-11:**
1. **설정** 열기
2. **보안** 또는 **잠금화면 및 보안** 탭
3. **알 수 없는 소스** 또는 **알 수 없는 앱 설치** 찾기
4. 토글 스위치 활성화
5. 메시지가 나타나면 확인

**Android 12+:**
1. **설정** 열기
2. **앱** 탭
3. **특별 액세스** 탭
4. **알 수 없는 앱 설치** 탭
5. 파일 관리자 또는 브라우저 선택
6. **이 소스 허용** 활성화

⚠️ **보안 참고:** 설치 완료 후 이 설정을 비활성화할 수 있습니다.

---

#### 2단계: APK 파일 다운로드

회사 IT 부서 또는 관리자로부터 VoltageAlert APK 파일을 받으세요:

- 📧 **이메일 첨부:** `VoltageAlert-v1.0.0-release.apk`
- 💾 **USB 드라이브:** 휴대폰의 다운로드 폴더로 복사
- ☁️ **클라우드 저장소:** 공유 링크에서 다운로드 (Google 드라이브 등)
- 📱 **직접 전송:** 블루투스 또는 파일 공유

**파일 정보:**
- **파일명:** VoltageAlert-v1.0.0-release.apk
- **크기:** 약 2.3 MB
- **버전:** 1.0.0

---

#### 3단계: 애플리케이션 설치

1. **APK 파일 찾기:**
   - **내 파일** 또는 **파일** 앱 열기
   - **다운로드** 폴더로 이동
   - **VoltageAlert-v1.0.0-release.apk** 찾기

2. **APK 파일을 탭**하여 설치 시작

3. **권한 검토:**
   - 앱이 권한을 요청합니다 (아래 권한 섹션 참조)
   - **설치** 탭

4. **설치 대기:**
   - 설치는 5-10초가 걸립니다
   - 프로세스를 중단하지 마세요

5. **설치 완료:**
   - **열기**를 탭하여 즉시 실행
   - 또는 홈 화면에서 앱 아이콘 찾기

⚠️ **경고가 표시되면:**
- "이 유형의 파일은 기기를 손상시킬 수 있습니다"
- **확인** 또는 **무시하고 설치** 탭
- Play 스토어가 아닌 APK 파일의 정상적인 경고입니다

---

#### 4단계: 권한 부여

VoltageAlert를 처음 열면 여러 권한을 요청합니다. 앱이 제대로 작동하려면 **모든 권한이 필요**합니다.

**필수 권한:**

1. **📶 근처 기기 / 블루투스**
   - **이유:** 전압 센서 장치에 연결
   - **조치:** **허용** 탭

2. **📍 위치**
   - **이유:** Android 12+에서 블루투스 스캔에 필요
   - **조치:** **앱 사용 중에만 허용** 탭

3. **🔔 알림**
   - **이유:** 앱이 백그라운드에 있을 때도 전압 경고 표시
   - **조치:** **허용** 탭

4. **📳 진동**
   - **이유:** 위험한 전압에 대한 진동 경고
   - **조치:** 자동으로 부여됨

⚠️ **중요:** 권한을 거부하면 앱이 제대로 작동하지 않을 수 있습니다. 나중에 Android 설정 → 앱 → VoltageAlert → 권한에서 권한을 변경할 수 있습니다.

---

### 첫 설정

설치 및 권한 부여 후:

1. **언어 선택:**
   - 앱이 시스템 언어를 자동 감지합니다
   - 필요시 설정에서 한국어/영어 전환

2. **블루투스 설정:**
   - 기기에서 블루투스가 활성화되어 있는지 확인
   - 앱이 센서 페어링을 안내합니다

3. **테스트 모드:**
   - 물리적 센서 없이 테스트하기 위해
   - 설정 → "Mock 모드" 활성화
   - 시뮬레이션된 전압 판독값을 생성합니다

---

### VoltageAlert 사용하기

#### 메인 화면

메인 화면에 표시되는 내용:
- **연결 상태:** 센서 연결 여부 표시
- **현재 전압:** 실시간 전압 판독값
- **이벤트 로그:** 전압 감지 기록 (최대 99개 항목)
- **스캔 버튼:** 전압 센서 장치 검색

#### 센서 연결

1. **ST9401-UP 전압 센서 전원 켜기**
2. 앱에서 **스캔** 버튼 탭
3. **장치 검색 대기** (5-10초)
4. 앱이 페어링 지침을 표시합니다
5. **메시지가 나타나면 PIN 입력:**
   - 시도: **1234** (가장 일반적)
   - 또는: **9527** (ESP32 기본값)
   - 또는: **0000**, **1111**, **0001**
6. Android 대화 상자에서 **페어링** 탭
7. 연결 상태가 **"연결됨"**으로 표시됩니다

#### 경고가 트리거될 때

위험한 전압이 감지되면:

1. **📱 화면:** 전압 레벨이 있는 전체 화면 경고
2. **🔊 소리:** 2톤 사이렌 (1200Hz/800Hz 교대)
3. **📳 진동:** 강한 진동 패턴
4. **⏰ 지속 시간:** 해제할 때까지 계속됩니다

**경고 해제:**
- 경고 화면에서 **확인** 버튼 탭
- 고압 지역에서 이동

---

### 설정

메인 화면의 **⚙️ 아이콘**을 통해 설정에 액세스:

**사용 가능한 설정:**
- **언어:** 한국어와 영어 전환
- **Mock 모드:** 센서 없이 테스트하기 위해 활성화 (개발자 모드)
- **볼륨:** 경고 소리 볼륨 조정
- **진동:** 진동 경고 활성화/비활성화
- **로그 지우기:** 모든 이벤트 기록 삭제

---

### 문제 해결

#### 앱이 설치되지 않음

**문제:** "앱이 설치되지 않았습니다" 오류

**해결 방법:**
1. 여유 저장 공간 확인 (최소 50 MB 필요)
2. 이전 버전이 있으면 먼저 제거
3. "알 수 없는 소스"가 활성화되어 있는지 확인
4. 휴대폰을 재시작하고 다시 시도

---

#### 블루투스가 연결되지 않음

**문제:** 센서를 찾거나 연결할 수 없음

**해결 방법:**
1. 센서의 전원이 켜져 있는지 확인
2. 휴대폰에서 블루투스가 활성화되어 있는지 확인
3. 페어링 해제 후 다시 페어링 시도:
   - 설정 → 블루투스 → 장치 삭제
   - VoltageAlert 앱에서 다시 스캔
4. 다른 PIN 코드 시도: 1234, 9527, 0000
5. 위치 권한이 부여되었는지 확인

---

#### 경고 중 소리 없음

**문제:** 경고가 표시되지만 소리가 재생되지 않음

**해결 방법:**
1. 휴대폰 볼륨 확인 (최소 50% 이상이어야 함)
2. 방해 금지 모드 비활성화
3. VoltageAlert 설정 → 볼륨 확인
4. Mock 모드로 테스트하여 확인

---

#### 앱이 충돌하거나 닫힘

**문제:** 앱이 예기치 않게 닫힘

**해결 방법:**
1. 휴대폰 재시작
2. 앱 캐시 지우기: 설정 → 앱 → VoltageAlert → 저장소 → 캐시 지우기
3. 앱 재설치
4. 문제가 지속되면 IT 지원팀에 문의

---

### 제거

VoltageAlert를 제거하려면:

1. **설정** 열기
2. **앱** 탭
3. **VoltageAlert** 찾아서 탭
4. **제거** 탭
5. 메시지가 나타나면 확인

---

### 지원 및 문의

**기술 문제:**
- 회사 IT 부서에 문의
- 이메일: [귀하의 지원 이메일]
- 전화: [귀하의 지원 전화]

**앱 버전:** 설정 → 정보에서 버전 확인

**업데이트:** 새 버전은 회사 IT 부서에서 배포됩니다

---

### 개인정보 보호 및 보안

**데이터 수집:**
- VoltageAlert는 전압 판독값과 타임스탬프만 수집합니다
- 모든 데이터는 기기에 로컬로 저장됩니다
- 외부 서버로 데이터가 전송되지 않습니다
- 블루투스 연결은 암호화됩니다

**권한 사용:**
- 블루투스: 센서 연결만을 위해
- 위치: Android의 블루투스 스캔에 필요 (추적에 사용되지 않음)
- 알림: 전압 경고만을 위해

---

### 법적 고지

**면책 조항:**
이 앱은 보조 안전 도구입니다. 표준 안전 장비, 절차 또는 교육을 대체하지 않습니다. 고압 전선 근처에서 작업할 때는 항상 회사의 안전 프로토콜을 따르고 적절한 보호 장비를 착용하십시오.

**Copyright © 2026 VoltageAlert**
모든 권리 보유.

---

## Document Information

**Document Version:** 1.0
**Last Updated:** February 7, 2026
**App Version:** 1.0.0
**Platform:** Android 8.0+

For the latest version of this guide, contact your IT department.
