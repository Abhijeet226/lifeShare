# LifeShare Blood Bank - Comprehensive Engineering & Build Log

**Project**: LifeShare Android App & MongoDB Atlas Cloud Backend  
**Platform**: Android (Java, Material 3, API 24 - 34) & Node.js REST API (Express, Mongoose, MongoDB Atlas)  
**Date**: August 18–19, 2026  
**Author / Engineering Lead**: Abhijeet Pradhan & Antigravity IDE  

---

## 📑 Table of Contents
1. [Architectural Overview & Cloud Infrastructure](#1-architectural-overview--cloud-infrastructure)
2. [Complete Summary of Features & Edits](#2-complete-summary-of-features--edits)
3. [REST API Endpoints (Backend)](#3-rest-api-endpoints-backend)
4. [Android Components & UI Layouts](#4-android-components--ui-layouts)
5. [Build, Dependencies & Compilation Details](#5-build-dependencies--compilation-details)
6. [Commands Used During Development](#6-commands-used-during-development)

---

## 1. Architectural Overview & Cloud Infrastructure

### MongoDB Atlas Cloud Database
- **Cluster**: `cluster0.jdamg.mongodb.net`
- **Database Name**: `Lifeshare`
- **Collections**:
  - `users`: Registered voluntary blood donors with Odisha city, blood group, donor ID, hashed passwords, availability status, and privacy settings.
  - `emergencies`: Active hospital & voluntary emergency SOS blood requests across Odisha with `postedBy` tracking.
  - `bloodbanks`: 24x7 verified hospital and Red Cross blood centers across Odisha with GPS coordinates.
  - `otps`: 6-digit email OTPs with 10-minute expiry for password recovery.

### Server Endpoints & Connectivity
- **Physical Device Host IP**: `http://172.28.183.190:5000/api`
- **Android Emulator Host IP**: `http://10.0.2.2:5000/api`
- **Local Host IP**: `http://localhost:5000/api`

---

## 2. Complete Summary of Features & Edits

### 🪪 1. Digital Donor Pass & Real Odisha Donor ID
- **Unique Format**: `OD-LS-XXXXXX` generated algorithmically from unique credentials.
- **Dynamic QR Generator**: Implemented `QrUtils.java` using ZXing `3.5.3` with pure `#000000` black modules on crisp `#FFFFFF` white container.
- **Pass Display**: Embedded in `fragment_profile.xml` and tap-to-enlarge high-contrast modal `dialog_enlarged_qr_pass.xml`.

### 🔐 2. Biometric Lock (Fingerprint & Face Unlock)
- **Library**: `androidx.biometric:biometric:1.2.0-alpha05`.
- **BiometricHelper**: Wrapped `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
- **Interactive Verification**: Toggling on Biometric Unlock in Security Hub prompts the hardware sensor to confirm setup before persisting.
- **App Launch Gatekeeper**: Prompts for fingerprint/face before showing donor records.

### 🔔 3. Location & Blood-Group Matched Push Notifications
- **Area & Blood Compatibility**: Evaluates recipient vs donor blood compatibility (e.g. `O-` universal, `O+` to positive groups, `A+`, `B+`, `AB+`) and matches user's Odisha city.
- **First-Time Mandatory Permission Gate**: `SplashActivity` prompts Android 13+ `POST_NOTIFICATIONS` with `dialog_notification_permission.xml`.
- **Background Sync**: `LogInActivity` polls MongoDB Atlas every 25s for new emergency requests and issues heads-up alerts with direct *Call Coordinator* and *View SOS* actions.

### 📞 4. Dialer-Style Donor Search & Details Modal
- **Dialer Expandable Cards**: Tapping a donor card expands an icon-only action tray (Call, SMS, WhatsApp, Profile).
- **Donor Profile Modal**: Tapping blood badge opens `dialog_donor_details.xml` showing full verified donor information and contact icons.

### 🚨 5. SOS Emergency Hub & Own Broadcast Deletion
- **Fixed Full-Width Bottom Bar**: `fragment_emergency.xml` features a pinned `Broadcast Emergency SOS` button with independently scrolling request list.
- **Owner Broadcast Deletion**: Identifies requests posted by the active user and provides a `Delete SOS` button backed by `DELETE /api/emergency/:id`.

### 🎨 6. AI-Generated 3D App Icon & Animated Splash Screen
- **App Icon**: 3D vector glowing crimson blood drop with embedded pulse heartbeat line (`app_logo.png`, `ic_launcher.png`, `ic_launcher_round.png`).
- **Animated Splash**: Elevated 3D logo with pulsing glow ripple and smooth typography fade-in.

### 🚪 7. Exit Confirmation Dialog & Subpage Polish
- **Pill-Shaped Exit Modal**: `dialog_exit_app.xml` with "Stay" and "Exit" pill buttons.
- **Clean Subpages**: Removed top back-arrow toolbars from Edit Profile, Security Hub, and Feedback screens for uncluttered edge-to-edge UI.
- **Modern Logout Icon**: Clean door-exit vector icon `ic_logout_modern.xml`.

---

## 3. REST API Endpoints (Backend)

| Method | Route | Description |
| :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Register new donor with Odisha city, DOB, gender |
| `POST` | `/api/auth/login` | Email/password authentication returning JWT token |
| `POST` | `/api/auth/google` | Google Sign-In verification & automatic profile sync |
| `POST` | `/api/auth/forgot-password` | Generate & email 6-digit verification OTP |
| `POST` | `/api/auth/verify-reset-otp` | Verify OTP & set new password |
| `PUT` | `/api/users/profile` | Update donor profile (name, gender, city, availability) |
| `PUT` | `/api/users/change-password` | Update password directly in MongoDB Atlas |
| `GET` | `/api/donors` | Search donors filtered by blood group and Odisha city |
| `GET` | `/api/emergency/list` | Retrieve all active emergency SOS requests |
| `POST` | `/api/emergency/create` | Broadcast new emergency SOS with `postedBy` tracking |
| `DELETE` | `/api/emergency/:id` | Resolve and delete emergency SOS from MongoDB Atlas |
| `GET` | `/api/bloodbanks` | Fetch 24x7 blood centers and Red Cross hubs in Odisha |

---

## 4. Android Components & UI Layouts

```
app/src/main/
├── java/com/example/abhijeet/bloodbank/
│   ├── ApiClient.java               // OkHttp REST client with dynamic host detection
│   ├── BiometricHelper.java         // AndroidX BiometricPrompt wrapper
│   ├── DataManager.java             // Central repository & session management
│   ├── EmergencyRequest.java        // Model with postedBy owner mapping
│   ├── NotificationHelper.java      // High-priority channels & blood compatibility matching
│   ├── QrUtils.java                 // ZXing QR bitmap generator
│   ├── UserProfile.java             // Model with Odisha Donor ID & privacy flags
│   ├── SplashActivity.java          // Animated splash with mandatory notif gate
│   ├── LogInActivity.java           // Bottom navigation, biometric unlock & 25s SOS sync
│   ├── MainActivity.java            // Landing & authentication entry
│   ├── SignUpActivity.java          // Split Name, DOB, Gender, Odisha cities, +91 prefix
│   ├── UpdatePassword.java          // Security & Privacy Hub
│   ├── UpdateProfile.java           // Profile editor
│   ├── FeedbackActivity.java        // User feedback composer
│   └── ui/
│       ├── HomeFragment.java        // Dashboard, quick actions, nearby blood banks
│       ├── SearchFragment.java      // Dialer-style expandable donor cards & modal
│       ├── EmergencyFragment.java   // SOS hub with fixed bottom bar & delete action
│       └── ProfileFragment.java     // Digital Donor pass, QR enlarge modal, settings
└── res/
    ├── anim/                        // splash_pulse.xml, splash_fade_in.xml
    ├── drawable/                    // app_logo.png, bg_grouped_container.xml, ic_logout_modern.xml
    ├── layout/                      // activity_splash.xml, dialog_donor_details.xml,
    │                                // dialog_exit_app.xml, dialog_notification_permission.xml,
    │                                // dialog_enlarged_qr_pass.xml, item_emergency_request.xml,
    │                                // list_layout.xml, fragment_emergency.xml, fragment_search.xml
    └── values/                      // colors.xml, strings.xml, styles.xml
```

---

## 5. Build, Dependencies & Compilation Details

### Gradle Dependencies (`app/build.gradle`)
```groovy
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'com.google.android.gms:play-services-auth:21.0.0'
    implementation 'com.google.zxing:core:3.5.3'
    implementation 'androidx.biometric:biometric:1.2.0-alpha05'
    implementation 'com.squareup.picasso:picasso:2.8'
    implementation 'jp.wasabeef:picasso-transformations:2.4.0'
}
```

### Build Output
- **Target Task**: `assembleDebug`
- **Output Artifact**: `app/build/outputs/apk/debug/app-debug.apk`
- **File Size**: `9.26 MB`
- **Status**: `BUILD SUCCESSFUL`

---

## 6. Commands Used During Development

### Backend Server Commands
```bash
# Start backend server on local machine
node server.js

# Test MongoDB Atlas connection
node test_connection.js

# Seed 21 Odisha voluntary donors & 5 hospital SOS requests
node seed_db.js

# Test live emergency SOS endpoint
node -e "const http = require('http'); http.get('http://localhost:5000/api/emergency/list', res => { let data = ''; res.on('data', c => data += c); res.on('end', () => console.log(data)); });"
```

### Android Build Commands
```pwsh
# Compile and build debug APK
.\gradlew.bat assembleDebug

# Clean build cache (if needed)
.\gradlew.bat clean assembleDebug
```
