# Taptix v2.3 — Phase N: Controlled Launch, Google Play Publication & Post-Launch Monitoring Guide

**Document Version:** 1.0  
**Date:** November 2, 2026  
**Application ID:** `com.example.taptix`  
**Target Release:** Taptix v2.3  
**Version Code:** `21`  
**Version Name:** `2.3`  
**Target SDK:** `35` (Android 15) | **Min SDK:** `26` (Android 8.0)  
**Role:** Senior Android Launch Director & Play Console Compliance Lead  

---

> [!IMPORTANT]
> **GO-LIVE SAFETY & PRE-LAUNCH STOP CONDITIONS**
> - **Pre-Flight Status:** This guide provides the complete blueprint and operational procedures for launching Taptix v2.3.
> - **Zero Premature Publishing:** Neither the APK nor the AAB has been uploaded or released to Google Play. Publication requires your explicit execution in the Google Play Console.
> - **Upload Key Integrity:** The original production keystore (`release.keystore`) is 100% preserved. The SHA-256 signing fingerprint (`8C:40:1F:EB:...`) is cryptographically verified.
> - **Honest Test Scope:** Automated unit testing (98/98 passed) and Android 15 (API 35) AVD virtual testing passed. **Physical smartphone on-road testing is cataloged as NOT RUN.**
> - **Brand Asset Disclosure:** Phase K approved 3D logo integration remains on hold pending artwork delivery; existing verified store assets in `play_store_assets/` are configured for the release candidate.

---

## 1. Mandatory Previous-Phase Audit Summary

| Audit Milestone | Status | Key Evidence / Verification |
| :--- | :---: | :--- |
| **Phase J: Release Candidate Audit** | **PASS** | `release_artifacts/Taptix-v2.3.apk` (9.23 MB); SHA-256 `8C2BF77C...`; V2 signature verified |
| **Phase L: Release Readiness Audit** | **PASS** | 98/98 unit tests passed; Section 10 financial exclusion (0 financial metrics); 0 shift scheduling |
| **Phase M: Store Listing & Asset Staging** | **PASS** | High-res icon (512×512), Feature graphic (1024×500), 6 genuine Android 15 screenshots staged |
| **Release Bundle (.aab) Build** | **PASS** | `release_artifacts/Taptix-v2.3.aab` (8.83 MB); SHA-256 `2D36DDE0...`; signed with original keystore |
| **Physical Hardware Testing** | **NOT RUN** | Real on-road smartphone testing was not conducted due to local host testing constraints |
| **Phase K Logo File Delivery** | **HOLD** | Master 3D tapping hand artwork was not provided in prompt; legacy neon-cab brand identity retained |

---

## 2. Google Play Console Step-by-Step Setup Guide

Follow these sequential steps in the [Google Play Console](https://play.google.com/console):

### Step 2.1 — Access & App Creation
1. Log in to your Google Play Console developer account.
2. Click **Create app** (top-right).
3. **App details:**
   - **App name:** `Taptix`
   - **Default language:** `English (United States)` (or preferred primary locale)
   - **App or game:** Select `App`
   - **Free or paid:** Select `Free`
4. **Declarations:**
   - Check the box accepting the **Developer Program Policies**.
   - Check the box accepting **US export laws**.
5. Click **Create app**.

---

### Step 2.2 — Store Listing Setup (Main Store Listing)
Navigate to **Grow > Store presence > Main store listing**:

- **App name (30 chars max):** `Taptix: Driver Auto Accept`
- **Short description (80 chars max):**  
  `Hands-free, smart ride and delivery acceptance with custom fare and distance filters.`
- **Full description (4000 chars max):**  
  Copy and paste verbatim from [`play_store_assets/store_listing_metadata.txt`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets/store_listing_metadata.txt).
  > `[INPUT REQUIRED]` Update the placeholder fields at the bottom with your developer name, support email, and privacy policy URL before saving.

---

### Step 2.3 — Graphic Assets Upload
Navigate to **Store presence > Main store listing > Graphics**:

1. **App icon:**  
   Upload [`play_store_assets/app_icon_512x512.png`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets/app_icon_512x512.png) (512 × 512 px, 32-bit PNG).
2. **Feature graphic:**  
   Upload [`play_store_assets/feature_graphic_1024x500.png`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets/feature_graphic_1024x500.png) (1024 × 500 px, PNG).
3. **Phone screenshots (Minimum 2 required, 6 provided):**  
   Upload all 6 files from [`play_store_assets/screenshots/`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets/screenshots/):
   - `01_main_dashboard.png` (Master controls & active driver dock)
   - `02_booking_filters.png` (Fare, distance, and per-km rate thresholds)
   - `03_map_zones.png` (Interactive operating boundaries & geofence radius)
   - `04_operational_analytics.png` (Operational trip volume ledger)
   - `05_activity_history.png` (Rule-match audit trail)
   - `06_android15_startup.png` (Verified Android 15 launch)

---

### Step 2.4 — Policy & Content Declarations (Policy Center)
Navigate to **Policy and programs > App content**:

1. **Privacy Policy:**  
   > `[INPUT REQUIRED]` Enter your public HTTPS URL (e.g., hosted on GitHub Pages or your custom domain containing [`privacy_policy.html`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/app/src/main/assets/privacy_policy.html)).
2. **Ads:**  
   Select **No, my app does not contain ads**.
3. **App Access:**  
   Select **All functionality is available without special access** (Taptix requires no login credentials or paywalls).
4. **Content Rating (IARC Questionnaire):**  
   - Category: Select `Utility, Productivity, Communication, or Other`.
   - Violence, sexual content, profanity, drugs, gambling: Select **No** to all questions.
   - Resulting Rating: **Everyone / PEGI 3**.
5. **Target Audience and Content:**  
   - Target age group: Select **18 and over** (professional commercial drivers).
   - Could your app unintentionally appeal to children: Select **No**.
6. **Data Safety Form:**  
   - Does your app collect or share user data: Select **Yes** (On-device location for geofencing).
   - Is all user data encrypted in transit: Select **Yes**.
   - Do you provide a way for users to request data deletion: Select **Yes** (local data clear via app settings).
   - **Location:** Check `Approximate location` and `Precise location` -> Check `Collected: YES`, `Shared: NO`, `Ephemeral: YES` (processed in RAM only, never saved), Purpose: `App functionality`.
   - **Financial info:** Select **No** (Section 10 strictly enforced).
   - **Personal info / Messages / Photos / Contacts:** Select **No** to all.

---

### Step 2.5 — Special Sensitive Permissions Declarations
Under **App Content**, complete the sensitive permission declarations:

1. **Accessibility API Declaration:**
   - **Is this an Accessibility Tool?** Select **NO**.
   - **Explanation:** Enter the exact declaration script:
     > *"Taptix uses the AccessibilityService API strictly to provide hands-free, automated ride and delivery acceptance for commercial drivers. Operating a motor vehicle while inspecting small order alerts creates dangerous driver distractions. Taptix reads only the incoming booking alert button labels within driver partner applications (Uber, Ola, Rapido, inDrive) and simulates a single tap on the accept button only when trip criteria match the driver's pre-configured safety rules. Taptix does not read private messages, credentials, or passenger data, and does not record or transmit screen data off the device."*
   - **Demonstration Video URL:** `[INPUT REQUIRED]` Provide an unlisted YouTube video URL following the script in Section 3.
2. **Foreground Service Special Use Declaration (API 34+):**  
   - Permission: `FOREGROUND_SERVICE_SPECIAL_USE`
   - Subtype: `Floating control overlay for driver auto-clicker interaction`
   - Justification: *"Enables a persistent, interactive floating dock window that allows commercial drivers to monitor and pause automated acceptance controls over active navigation applications."*

---

## 3. Google Play Policy, Automation Legality & Review Video

### 3.1 Deterministic Automation vs. Autonomous AI
- **Google Play Automation Rule:** Autonomous AI agents that independently plan and execute tasks without human guardrails are prohibited under Accessibility API policies.
- **Taptix Compliance:** Taptix uses **deterministic, rule-based automation**. Every click is triggered strictly by user-defined, static parameters (e.g., minimum fare threshold, maximum pickup distance).

### 3.2 Third-Party Platform Terms Risk Disclosure
- **Play Console DDA Section 4.4 Risk:** Ride-hailing platforms (Uber, Ola, Rapido) have Terms of Service prohibiting automated scripts or bots. If platform owners submit a formal complaint to Google Play, the listing could be subject to policy inquiries or takedown.
- **Mitigation:** Distribute primarily via direct APK / GitHub Releases and Amazon Appstore, using Google Play as a secondary channel.

### 3.3 Mandatory Declaration Video Script (YouTube Unlisted)
Google reviewers require an unlisted YouTube video showing the complete user flow from a clean installation:

```
[0:00 - 0:15] CLEAN INSTALL & OPEN
Show device home screen. Open Taptix fresh. Show the initial setup dashboard.

[0:15 - 0:35] PROMINENT ACCESSIBILITY DISCLOSURE
Tap the Accessibility setup card. The in-app "Accessibility Service Disclosure" dialog 
must appear on screen BEFORE redirecting to settings. Clearly show the text explaining 
what it reads (button labels only) and what it does (single tap simulation).

[0:35 - 0:50] AFFIRMATIVE CONSENT & SETTINGS REDIRECT
Tap "Agree & Continue". Show transition into Android System Settings > Accessibility.
Locate Taptix and toggle the service to ON. Accept system dialog.

[0:50 - 1:15] HANDS-FREE AUTOMATION DEMONSTRATION
Return to Taptix. Open "Auto-Accept Simulator" (DemoActivity).
Configure a rule (e.g., Min Fare ₹200). Trigger a simulated booking offer.
Show Taptix automatically detect the offer and simulate the one-tap acceptance hands-free.
```

---

## 4. Release Artifact Verification & Google Play App Signing

### 4.1 Artifact Comparison & Cryptographic Verification

| Attribute | Production Release Bundle (.aab) | Standalone Release (.apk) | Status |
| :--- | :--- | :--- | :---: |
| **Path** | [`release_artifacts/Taptix-v2.3.aab`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/release_artifacts/Taptix-v2.3.aab) | [`release_artifacts/Taptix-v2.3.apk`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/release_artifacts/Taptix-v2.3.apk) | **Verified** |
| **File Size** | `8,837,922` bytes (8.42 MB) | `9,230,306` bytes (8.80 MB) | **Verified** |
| **Package ID** | `com.example.taptix` | `com.example.taptix` | **Match** |
| **Version Code**| `21` | `21` | **Match** |
| **Version Name**| `2.3` | `2.3` | **Match** |
| **SHA-256 Hash**| `2D36DDE005ADB2D351DE4B415E27208B8B7A91CAEA939B30C478CF928C2E523C` | `8C2BF77C32C174B48D62C9F4F79CD85AA44F86F695B4CBC31AE1E50F5914E988` | **Verified** |
| **Signer Fingerprint** | `8C:40:1F:EB:F2:9A:3E:B2:79:82:E3:77:A8:6D:A0:4E:9D:01:14:04:0F:CD:55:AD:1D:31:34:31:31:7C:89:BD` | `8C:40:1F:EB:F2:9A:3E:B2:79:82:E3:77:A8:6D:A0:4E:9D:01:14:04:0F:CD:55:AD:1D:31:34:31:31:7C:89:BD` | **100% Identical** |
| **Debuggable** | `false` | `false` | **PASS** |

### 4.2 Google Play App Signing Architecture Explained
- **The Upload Key (`release.keystore`):** The cryptographic keystore in your repository signs the `.aab` or `.apk` before you upload it to Google. Google Play authenticates this upload using the SHA-256 fingerprint (`8C:40:1F:EB:...`).
- **The Google Play App Signing Key:** When Google Play App Signing is active, Google ingests your `.aab`, splits it into optimized device-specific APKs, and signs them with Google's managed signing key for end-user device installations.
- **CRITICAL SECURITY RULE:** Never lose, overwrite, or re-generate `release.keystore`. If the upload key changes, Google Play will reject all future updates for `com.example.taptix`.

---

## 5. Controlled Testing Track Setup & Scenarios

Google Play mandates that personal developer accounts created after November 2023 must run a **closed testing track with at least 20 testers opted in for 14 consecutive days** prior to requesting production release access.

### 5.1 Track Selection Recommendation
1. **Internal Testing Track (Day 1):**  
   - Immediately available to up to 100 internal team testers.
   - Bypasses Google's manual review queue (available within minutes).
   - Use this to verify installation, splash screen, and the Accessibility permission flow.
2. **Closed Testing Track (Days 2–16):**  
   - Enroll 20+ independent testers.
   - Gather real-world feedback and monitor crash reports in Play Console.

---

### 5.2 10 Critical Test Scenarios for Testers

| # | Feature / Test Scenario | Expected Behavior |
| :-: | :--- | :--- |
| **1** | **Clean Installation & Launch** | App installs without Play Protect warnings; boots cleanly into MainActivity. |
| **2** | **Accessibility Disclosure Flow** | Clicking Enable displays the prominent in-app disclosure dialog; "Agree & Continue" navigates to Settings. |
| **3** | **Master Automation Toggle** | Toggling master switch enables background listener; persistent notification appears. |
| **4** | **Floating Dock Overlay** | Dock appears over partner apps; pause/resume buttons respond immediately. |
| **5** | **Fare Filter Threshold** | Simulator offers below minimum fare are rejected; offers meeting or exceeding are accepted. |
| **6** | **Pickup Distance Isolation** | Offers with pickup distance beyond max threshold are rejected without dispatching click. |
| **7** | **Map Zone Geofencing** | Rides outside the defined boundary polygon/radius are rejected. |
| **8** | **Operational Analytics Ledger** | Accepted/rejected counts increment accurately; strictly zero currency/earnings data shown. |
| **9** | **Background Survival** | App remains active when screen locks or during long driving sessions. |
| **10**| **Graceful Teardown** | Toggling master switch OFF cleanly tears down overlay dock and halts click listener. |

---

### 5.3 Tester Feedback & Bug Reporting Template

Testers should submit bug reports using this format:

```text
[TAPTIX v2.3 BUG REPORT]
Device Model: (e.g., Samsung Galaxy S23, Pixel 7)
Android OS Version: (e.g., Android 14, 15)
Driver Apps Installed: (e.g., Uber Driver v4.490, Ola Captain v5.2)
Battery Optimization Setting: (Unrestricted / Optimized)
Issue Description: (What happened vs what was expected)
Steps to Reproduce:
1. 
2. 
3. 
Screenshot / Logcat Attached: (Yes / No)
```

---

## 6. Staged Production Rollout Plan & Post-Launch Monitoring

When production access is approved by Google Play:

```
[ Day 1: 5% Rollout ] ──> [ Day 2: 10% Rollout ] ──> [ Day 3: 20% Rollout ] ──> [ Day 5: 50% Rollout ] ──> [ Day 7: 100% Full Release ]
```

### 6.1 Android Vitals SLA Thresholds (Kill-Switch Triggers)

Monitor **Quality > Android Vitals** in Play Console:

| Vital Metric | Google Bad Behavior Threshold | Taptix Rollback Trigger | Recommended Immediate Action |
| :--- | :---: | :---: | :--- |
| **Crash Rate (User-perceived)** | > 1.09% | **> 0.80%** | **Halt Rollout.** Inspect stack trace in Play Console; identify failing OEM skin. |
| **ANR Rate (Application Not Responding)** | > 0.47% | **> 0.35%** | **Halt Rollout.** Investigate accessibility event thread blocking or geofence loops. |
| **Excessive Wake Locks** | > 0.10% | **> 0.08%** | Verify background service sleep cycles. |
| **Policy Center Inquiries** | Any warning | **Immediate** | Respond within 72 hours with prominent disclosure and declaration video evidence. |

### 6.2 Emergency Rollback & Recovery Procedure
1. If a critical crash or freeze occurs during rollout, navigate to **Release > Production**.
2. Click **Halt rollout** immediately. Existing users stay on the current version, but no new users receive the update.
3. If an emergency hotfix is required:
   - Increment `versionCode` to `22` and update `versionName` to `"2.3.1"`.
   - Build hotfix bundle: `.\gradlew.bat bundleRelease`.
   - Upload new AAB directly to Production at 100% rollout to overwrite the failing build.

---

## 7. Launch Preparation Sign-Off

- [x] Production Release AAB compiled & signed: [`release_artifacts/Taptix-v2.3.aab`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/release_artifacts/Taptix-v2.3.aab)
- [x] Production Release APK compiled & signed: [`release_artifacts/Taptix-v2.3.apk`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/release_artifacts/Taptix-v2.3.apk)
- [x] Original signing keystore verified (`8C:40:1F:EB:...`).
- [x] 98/98 unit tests passed with 0 failures.
- [x] Section 10 financial exclusion and shift schedule absence strictly verified.
- [x] Genuine Android 15 store graphics and screenshots staged in `play_store_assets/`.
- [x] Google Play Console step-by-step guidance and declaration scripts prepared.
- [ ] Owner: Provide support contact email and public privacy policy URL.
- [ ] Owner: Record YouTube unlisted Accessibility declaration demonstration video.
- [ ] Owner: Upload AAB to Internal / Closed testing track in Google Play Console.
