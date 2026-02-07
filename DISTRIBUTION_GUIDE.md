# VoltageAlert App Distribution Guide

**App:** VoltageAlert (활선 접근 경보기)
**Target Users:** Korean Power Company Workers
**Platform:** Android 8.0+ (API 26+)

---

## Distribution Options Overview

| Method | Best For | Cost | Approval Time | Pros | Cons |
|--------|----------|------|---------------|------|------|
| **Google Play Store** | Public/General Users | $25 one-time | 1-7 days | Wide reach, auto updates | Public listing, review required |
| **Private Play Store** | Company Employees | $25 one-time | 1-7 days | Controlled access, auto updates | Requires Play Console |
| **Enterprise MDM** | Corporate Deployment | Varies | Immediate | Full control, no approval | Requires MDM infrastructure |
| **Direct APK** | Quick Testing/Limited Users | Free | Immediate | Simple, fast | Manual updates, security warnings |
| **Korean App Stores** | Korean Market | Varies | 3-7 days | Local presence | Multiple submissions |
| **Beta Testing** | Pre-release Testing | Free | Immediate | Controlled testing | Limited users (max 2000) |

---

## Option 1: Google Play Store (공식 배포)

### For: Public Distribution or Workplace Apps

**Best for:** If you want the app available to all Korean power company workers across multiple companies.

### Steps to Publish:

#### 1. **Create Google Play Developer Account**
   - **URL:** https://play.google.com/console
   - **Cost:** $25 USD (one-time registration fee)
   - **Required Information:**
     - Developer name (personal or company)
     - Email address
     - Payment method (credit card)
     - Identity verification (government ID)

#### 2. **Prepare Release APK/AAB**
   ```bash
   cd /Users/mskim/Development/Android/VoltageAlert

   # Generate signed release build (AAB - Android App Bundle)
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
   ./gradlew bundleRelease

   # Or generate APK
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
   ./gradlew assembleRelease
   ```

#### 3. **Create Signing Key** (if not already done)
   ```bash
   # Generate keystore for signing releases
   keytool -genkey -v -keystore voltage-alert-release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias voltage-alert

   # Enter password and information when prompted
   # IMPORTANT: Store this file and password securely!
   ```

#### 4. **Configure Signing in build.gradle.kts**
   ```kotlin
   // app/build.gradle.kts
   android {
       signingConfigs {
           create("release") {
               storeFile = file("../voltage-alert-release-key.jks")
               storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "your_password"
               keyAlias = "voltage-alert"
               keyPassword = System.getenv("KEY_PASSWORD") ?: "your_password"
           }
       }

       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               isMinifyEnabled = true
               proguardFiles(
                   getDefaultProguardFile("proguard-android-optimize.txt"),
                   "proguard-rules.pro"
               )
           }
       }
   }
   ```

#### 5. **Required Assets for Play Store**
   - **App Icon:** 512×512 PNG (transparent background)
   - **Feature Graphic:** 1024×500 PNG
   - **Screenshots:** Minimum 2, recommended 4-8
     - Phone: 1080×1920 or higher
     - Tablet: 1920×1200 or higher (optional)
   - **Privacy Policy URL** (required if app collects data)
   - **App Description:**
     - Short description (80 characters max)
     - Full description (4000 characters max)
     - Korean and English versions

#### 6. **App Category & Content Rating**
   - **Category:** Business or Tools
   - **Content Rating:** Complete IARC questionnaire
   - **Target Audience:** Workplace/Professional users
   - **Age Rating:** 18+ (workplace safety app)

#### 7. **Review & Approval**
   - **Approval Time:** 1-7 days (usually 1-2 days)
   - **Common Rejection Reasons:**
     - Missing privacy policy
     - Bluetooth permission justification not clear
     - Screenshots not representative
     - App crashes on test devices

#### 8. **Ongoing Maintenance**
   - **Updates:** Can push updates anytime (review each time)
   - **Rollout:** Can do staged rollout (5%, 10%, 50%, 100%)
   - **Statistics:** Download counts, crash reports, user reviews

### Pros & Cons

**Pros:**
- ✅ Official Google distribution
- ✅ Automatic updates for users
- ✅ Play Protect security scanning
- ✅ Detailed statistics and crash reports
- ✅ User reviews and ratings
- ✅ Discoverable by search

**Cons:**
- ❌ $25 registration fee
- ❌ 1-7 day review process for each update
- ❌ Public listing (anyone can download)
- ❌ Must follow Play Store policies strictly
- ❌ Cannot distribute pre-release/test versions easily

---

## Option 2: Google Play - Private Distribution (비공개 배포)

### For: Company-Specific Deployment

**Best for:** If VoltageAlert is only for specific power company employees.

### Managed Google Play (Enterprise)

**Requirements:**
- Google Workspace account or Enterprise Mobility Management (EMM)
- Company email domain
- Play Console developer account

**Setup Process:**
1. Create private app in Play Console
2. Mark as "Internal App" or "Managed Google Play"
3. Distribute via company email domain whitelist
4. Employees sign in with company Google account to access

**Benefits:**
- App not visible in public Play Store
- Only authorized users can install
- Still get automatic updates
- No public reviews
- Controlled rollout

**Cost:** $25 Play Console registration (same as public)

---

## Option 3: Enterprise MDM Distribution (기업 배포)

### For: Large Organizations with IT Infrastructure

**Best for:** If power companies have Mobile Device Management (MDM) systems.

### Common MDM Solutions in Korea:
- **Samsung Knox** (popular for Samsung devices)
- **Google Workspace Mobile Management**
- **Microsoft Intune**
- **MobileIron**
- **AirWatch (VMware)**

### How it Works:
1. Company IT department uploads your signed APK to MDM
2. MDM pushes app to all employee devices automatically
3. Updates pushed centrally by IT
4. Can enforce installation and prevent uninstall

### Benefits:
- ✅ Zero user action required (automatic deployment)
- ✅ IT controls everything
- ✅ Can enforce app policies
- ✅ Works with company-owned devices
- ✅ No public listing needed

### Requirements:
- Company must have MDM infrastructure
- You provide signed APK to IT department
- May need to integrate with MDM SDK (optional)

**Cost:** None for developer (company pays for MDM)

---

## Option 4: Direct APK Distribution (직접 배포)

### For: Quick Deployment, Testing, or Small Groups

**Best for:** Testing with workers before official release, or small pilot programs.

### How to Distribute:

#### Method A: Email/File Transfer
```bash
# Build release APK
cd /Users/mskim/Development/Android/VoltageAlert
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew assembleRelease

# APK location
# app/build/outputs/apk/release/app-release.apk

# Rename for clarity
cp app/build/outputs/apk/release/app-release.apk \
   VoltageAlert-v1.0.0.apk

# Send via email, USB, or cloud storage
```

#### Method B: Self-Hosted Web Download
```html
<!-- Create simple download page -->
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>VoltageAlert 다운로드</title>
</head>
<body>
    <h1>활선 접근 경보기 (VoltageAlert)</h1>
    <h2>버전 1.0.0</h2>

    <h3>설치 방법:</h3>
    <ol>
        <li>아래 다운로드 버튼 클릭</li>
        <li>설정 → 보안 → "알 수 없는 소스" 허용</li>
        <li>다운로드한 APK 파일 실행</li>
        <li>설치 완료</li>
    </ol>

    <a href="VoltageAlert-v1.0.0.apk" download>
        <button style="font-size:20px; padding:20px;">
            다운로드 (Download)
        </button>
    </a>

    <h3>업데이트 내역:</h3>
    <ul>
        <li>블루투스 센서 연결</li>
        <li>실시간 전압 모니터링</li>
        <li>경보 알림 (소리, 진동, 화면)</li>
    </ul>
</body>
</html>
```

Host this on:
- Company intranet server
- Cloud storage (Google Drive, Dropbox) with shared link
- Simple web hosting (GitHub Pages, Netlify)

#### Method C: QR Code Distribution
```bash
# Generate QR code pointing to APK download URL
# Users scan QR code → download → install

# Use online QR generator:
# - https://www.qr-code-generator.com/
# - Input: Your APK download URL
# - Print QR code on posters at workplace
```

### Installation Instructions for Users:

**Korean (한국어):**
```
📱 VoltageAlert 설치 방법

1. 안드로이드 설정 열기
2. "보안" 또는 "생체 인식 및 보안" 선택
3. "알 수 없는 소스" 또는 "알 수 없는 앱 설치" 활성화
4. APK 파일 다운로드
5. 다운로드한 파일 클릭하여 설치
6. 설치 완료 후 앱 실행

⚠️ 보안 경고가 나타나면 "무시하고 설치" 선택
```

**English:**
```
📱 VoltageAlert Installation Instructions

1. Open Android Settings
2. Go to "Security" or "Biometrics and Security"
3. Enable "Unknown Sources" or "Install Unknown Apps"
4. Download the APK file
5. Tap the downloaded file to install
6. Launch the app after installation

⚠️ If you see a security warning, select "Install Anyway"
```

### Pros & Cons

**Pros:**
- ✅ **Free** (no Play Store fees)
- ✅ **Immediate** distribution
- ✅ **Full control** over releases
- ✅ **No approval process**
- ✅ Works for testing/pilots

**Cons:**
- ❌ **Security warnings** during install (not from Play Store)
- ❌ **Manual updates** (users must download new APK each time)
- ❌ **No automatic updates**
- ❌ **No crash reporting** (unless you add Firebase)
- ❌ Requires enabling "Unknown Sources"

---

## Option 5: Korean App Stores (한국 앱마켓)

### For: Korean Market Presence

Popular alternatives to Google Play in Korea:

### 1. **ONE Store (원스토어)**
   - **URL:** https://www.onestore.co.kr
   - **Market Share:** #2 in Korea (after Google Play)
   - **Users:** ~10 million Korean users
   - **Pros:** Strong Korean presence, popular with Korean companies
   - **Cons:** Requires separate registration and submission
   - **Review Time:** 3-5 days

### 2. **Galaxy Store (삼성)**
   - **URL:** https://seller.samsungapps.com
   - **Market Share:** Samsung device users only
   - **Users:** All Samsung Galaxy users in Korea
   - **Pros:** Pre-installed on Samsung devices (huge in Korea)
   - **Cons:** Samsung devices only
   - **Review Time:** 3-7 days

### 3. **Naver App Square (네이버)**
   - **URL:** https://section.cafe.naver.com/appstore
   - **Market Share:** Small but growing
   - **Users:** Naver ecosystem users
   - **Pros:** Integration with Naver services
   - **Cons:** Smaller user base

### Multi-Store Strategy

**Recommended:** Submit to both Google Play AND ONE Store for maximum reach in Korea.

---

## Option 6: Beta Testing Distribution (베타 테스트)

### For: Pre-Release Testing with Real Users

### Google Play Internal Testing
- **Max Users:** 100 testers
- **Approval:** Instant (no review)
- **Access:** Via email invitation
- **Best for:** Development team testing

### Google Play Closed Testing
- **Max Users:** Up to 2000 testers
- **Approval:** Instant (no review)
- **Access:** Via email or link
- **Best for:** Pilot program with selected power company workers

### Google Play Open Testing
- **Max Users:** Unlimited
- **Approval:** Requires review (1-2 days)
- **Access:** Public opt-in link
- **Best for:** Public beta before full release

### Setup Beta Testing:
1. Go to Play Console → Testing → Internal/Closed/Open Testing
2. Upload APK/AAB
3. Add tester email addresses or create opt-in link
4. Share link with testers
5. Testers click link → Install beta version

**Benefits:**
- Get real user feedback before public release
- Test with actual power company workers
- Collect crash reports and analytics
- Iterate quickly without affecting production users

---

## Recommended Distribution Strategy

### For VoltageAlert (Power Company Safety App):

### **Phase 1: Beta Testing (1-2 months)**
1. ✅ **Direct APK Distribution** to 5-10 early adopters
   - Quick iteration
   - Get immediate feedback on Bluetooth connectivity
   - Fix critical bugs

2. ✅ **Google Play Closed Beta** to 50-100 workers
   - Pilot program at one power company
   - Test in real field conditions
   - Collect usage data and crash reports

### **Phase 2: Pilot Deployment (2-3 months)**
1. ✅ **Google Play Internal App** for specific power company
   - Distribute to all workers at pilot company
   - Monitor performance and safety effectiveness
   - Refine based on field feedback

### **Phase 3: Production (Ongoing)**

**Option A: Single Company**
- ✅ **Enterprise MDM** if company has MDM system
- ✅ **Google Play Private App** if no MDM

**Option B: Multiple Companies**
- ✅ **Google Play Store** (public) for all Korean power companies
- ✅ **ONE Store** for additional reach
- ✅ **Galaxy Store** for Samsung device users

---

## Legal Requirements (Korean Market)

### 1. **Business Registration**
If selling the app or distributing commercially:
- Company business registration (사업자등록증)
- Personal: Individual business registration

### 2. **Privacy Policy** (Required)
Must include:
- What data is collected (Bluetooth, location, logs)
- How data is used (voltage monitoring, safety alerts)
- Data storage and security
- User rights (access, deletion)
- Contact information

**Template:** https://app-privacy-policy-generator.firebaseapp.com/

### 3. **App Permissions Justification**
Must explain why each permission is needed:
- `BLUETOOTH_CONNECT`: "Connect to voltage sensor device"
- `BLUETOOTH_SCAN`: "Scan for nearby voltage sensors"
- `ACCESS_FINE_LOCATION`: "Required for Bluetooth scanning on Android 12+"
- `VIBRATE`: "Alert user of dangerous voltage"
- `FOREGROUND_SERVICE`: "Continuous voltage monitoring"

### 4. **Safety & Liability**
Consider:
- Disclaimer about sensor accuracy
- Not a substitute for other safety measures
- User agreement acknowledging risks
- Liability waiver (consult with lawyer)

---

## Cost Comparison

| Method | One-time | Monthly | Yearly | Total (1st Year) |
|--------|----------|---------|--------|------------------|
| **Google Play** | $25 | $0 | $0 | $25 |
| **Private Play** | $25 | $0 | $0 | $25 |
| **Enterprise MDM** | $0 | Varies | Varies | Company pays |
| **Direct APK** | $0 | $0 | $0 | **$0 (Free)** |
| **ONE Store** | ₩50,000 | $0 | $0 | ~$40 |
| **Galaxy Store** | $0 | $0 | $0 | $0 |

---

## App Updates - Best Practices

### Version Numbering
```
versionCode: 1, 2, 3, 4... (increment by 1 each release)
versionName: "1.0.0", "1.0.1", "1.1.0", "2.0.0"

Format: MAJOR.MINOR.PATCH
- MAJOR: Breaking changes, major features
- MINOR: New features, backwards compatible
- PATCH: Bug fixes, minor changes
```

### Release Notes Template
```
버전 1.0.1 (2026-02-15)
━━━━━━━━━━━━━━━━━━━━━━

✨ 새로운 기능
• ESP32 센서 자동 연결
• 배터리 잔량 표시

🐛 버그 수정
• 블루투스 재연결 오류 수정
• 로그 99개 제한 수정

⚡ 성능 개선
• 앱 시작 속도 30% 향상
• 메모리 사용량 감소
```

### Update Distribution Timeline

**Critical Bugs:**
- Fix: Same day
- Release: 1-2 days (Play Store review)

**Regular Updates:**
- Monthly or bi-monthly
- Batch bug fixes and minor features

**Major Updates:**
- Quarterly (every 3 months)
- New features, redesigns

---

## Getting Started Checklist

### Before Distribution:

- [ ] **Signing Key Created** and stored securely
- [ ] **Release Build** compiles without errors
- [ ] **Tested on multiple devices** (Samsung, LG, etc.)
- [ ] **Bluetooth pairing tested** with real sensor (or Mock mode)
- [ ] **App icon** 512×512 PNG created
- [ ] **Screenshots** taken (at least 4)
- [ ] **Privacy Policy** written and hosted
- [ ] **App description** written (Korean & English)
- [ ] **Version number** set correctly (1.0.0)

### Choose Distribution Method:

**Quick Start (Recommended for Testing):**
1. Build signed APK
2. Email to 5-10 testers
3. Get feedback
4. Fix issues
5. Repeat

**Production (Recommended):**
1. Google Play Console registration ($25)
2. Create app listing
3. Upload signed AAB/APK
4. Submit for review
5. Launch as Internal or Closed Beta first
6. Promote to Production after testing

---

## Support & Maintenance

### User Support Channels:
- Email: your-support@email.com
- Phone: Your contact number
- Kakaotalk: Your business Kakaotalk
- Website: FAQ and troubleshooting guide

### Crash Reporting:
```kotlin
// Add Firebase Crashlytics to build.gradle.kts
dependencies {
    implementation("com.google.firebase:firebase-crashlytics-ktx:18.6.0")
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.0")
}
```

### Analytics:
- Firebase Analytics (free)
- Google Play Console statistics
- Track: Installs, active users, crash-free rate

---

## Summary - Quick Decision Guide

**Choose Google Play Store if:**
- ✅ Want automatic updates
- ✅ Need wide distribution
- ✅ Have $25 for registration
- ✅ Can wait 1-7 days for approval

**Choose Direct APK if:**
- ✅ Need immediate distribution
- ✅ Small pilot group (< 50 users)
- ✅ Want $0 cost
- ✅ Testing/development phase

**Choose Enterprise MDM if:**
- ✅ Company has IT infrastructure
- ✅ Need automatic deployment
- ✅ Corporate-owned devices
- ✅ Centralized management

**Choose Multiple Stores if:**
- ✅ Maximum reach in Korea
- ✅ Professional appearance
- ✅ Multiple power companies
- ✅ Long-term product

---

**Next Steps:**
1. Decide on distribution method
2. Prepare required assets
3. Create signing key
4. Build release version
5. Test thoroughly
6. Distribute!

**Need help with specific distribution method? Let me know!**
