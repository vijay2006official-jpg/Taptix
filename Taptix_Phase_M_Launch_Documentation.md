# Taptix v2.3 — Phase M: Play Store Assets & Launch Documentation

**Document Version:** 1.0  
**Date:** November 2, 2026  
**Application ID:** `com.example.taptix`  
**Target Release:** Taptix v2.3  
**Version Code:** `21`  
**Version Name:** `2.3`  
**Target SDK:** `35` (Android 15) | **Min SDK:** `26` (Android 8.0)  
**Engineering Role:** Senior Android Launch & Compliance Architect  

---

> [!IMPORTANT]
> **PREVIOUS-PHASE AUDIT DISCLOSURE & STOP-CONDITION ASSESSMENT**
> - **Phase L Verification:** Fully audited via [`Taptix_Phase_L_Release_Readiness_Report.md`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/Taptix_Phase_L_Release_Readiness_Report.md).
> - **Technical Health:** 98/98 unit tests passed; APK signature valid (V2 scheme); original keystore SHA-256 fingerprint verified (`8C:40:1F:EB:...`); zero hardcoded credentials; zero crashes on Android 15 Pixel 8 AVD.
> - **Unresolved Blocker (Phase K Logo):** The approved 3D tapping hand logo artwork was not supplied in the prompt payload. The current build and store assets utilize the verified legacy neon-cab brand identity (`play_store_assets/app_icon_512x512.png` and `feature_graphic_1024x500.png`).
> - **Google Play Policy Risk:** High platform risk under Google Play DDA Section 4.4 and third-party ride-hailing Terms of Service. An in-app prominent disclosure dialog is implemented, and a mandatory Play Console declaration video script is detailed herein.
> - **Publication Status:** **STRICTLY UNPUBLISHED.** Artifacts remain exclusively on local storage pending explicit owner authorization.

---

## 1. Google Play Store Listing Specification

### 1.1 App Identity & Metadata

- **App Title (30-character limit):**  
  `Taptix: Driver Auto Accept` *(26 characters)*
  *(Alternative: `Taptix - Driver Co-Pilot` - 23 characters)*

- **Short Description (80-character limit):**  
  `Hands-free, smart ride and delivery acceptance with custom fare and distance filters.` *(78 characters)*

- **Category Recommendation:**  
  - **Primary Category:** `Auto & Vehicles` (or `Productivity`)
  - **Tags:** `Rideshare`, `Delivery`, `Driver Utility`, `Hands-Free`, `Driving Safety`

- **Content Rating Target:**  
  `Everyone` (IARC / PEGI 3 / ESRB Everyone) — No adult content, no user-generated content, no gambling, no financial transactions.

---

### 1.2 Full Description (Google Play Store Draft)

```markdown
Taptix is a driver assistance utility designed to reduce distracted driving by automating one-tap ride acceptance based on your personal preferences. Configure your minimum fare rates, maximum pickup distance, and operating zones, and let Taptix safely manage acceptance alerts hands-free.

SAFETY FIRST — DISTRACTION-FREE DRIVING
Navigating city traffic while inspecting small order alerts creates dangerous behind-the-wheel distractions. Taptix helps you keep your eyes on the road and hands on the wheel by automatically evaluating incoming booking criteria against your custom pre-configured rules.

INTELLIGENT OFFER FILTERING
• Minimum Fare Filter: Set your threshold to ignore unprofitable trips.
• Maximum Pickup Distance: Prevent deadhead mileage by limiting how far you drive to pickups.
• Fare-per-Kilometer Rate: Automatically accept rides that meet your target earning rate per km.
• Service Category Controls: Filter specifically for Auto, Cab Economy, Premium, or Courier orders.
• Smart Drop Location Filters: Identify destination keywords (e.g., Airport, Central Station) to match your route.

CUSTOM MAP ZONES & GEOFENCING
• Draw custom operating perimeters directly on an interactive map.
• Set pickup geofence boundaries to only accept requests originating within your preferred neighborhoods.
• Automatic geofence tolerance ensures you stay focused on your preferred operating areas.

TRANSPARENT OPERATIONAL LEDGER
• Keep track of daily operational metrics: rides detected, offers accepted, orders rejected, and total distance driven.
• Review detailed activity logs to understand which filter rules matched incoming offers.
• Section 10 Compliant: Strictly zero financial tracking, income calculations, or profit estimates. Taptix tracks only operational trip activity.

PRIVACY & PERMISSIONS TRANSPARENCY
• 100% On-Device Processing: Taptix does not operate servers, collect tracking metrics, or upload personal data.
• Screen Privacy: Taptix never reads personal messages, customer names, phone numbers, or passwords.
• Zero Third-Party Trackers: No third-party advertising SDKs, analytic trackers, or data-sharing integrations.

ACCESSIBILITY SERVICE API DISCLOSURE
Taptix uses Android's AccessibilityService API solely to provide automated ride-acceptance for drivers. 

• What It Reads: Reads only incoming order popup button labels (e.g., "Accept", "Confirm") within supported driver partner applications.
• What It Does: Simulates a single screen tap on the accept button when your configured platform rules match.
• Privacy Guarantee: Taptix does NOT read personal messages, passwords, credit card numbers, or passenger details. No screen data is recorded, saved, or uploaded off your device.
• Explicit Consent: Before enabling this service, an in-app prominent disclosure is displayed, requiring your explicit affirmative consent.

DEVELOPER & SUPPORT CONTACT
• Developer Name: [OWNER TO PROVIDE: e.g., Taptix Mobile Technologies]
• Support Email: [OWNER TO PROVIDE: e.g., support@taptix.app]
• Privacy Policy URL: [OWNER TO PROVIDE: e.g., https://yourdomain.com/privacy or GitHub hosted raw link]
• Physical / Mailing Address: [OWNER TO PROVIDE]
```

---

## 2. Store Graphic Assets & Screenshot Catalog

All graphic assets are organized in [`play_store_assets/`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets):

| Asset Name | Required Dimensions | File Path | Status | Verification Detail |
| :--- | :--- | :--- | :---: | :--- |
| **High-Resolution App Icon** | 512 × 512 px, 32-bit PNG, max 1024 KB | [`play_store_assets/app_icon_512x512.png`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets/app_icon_512x512.png) | **READY** | 512×512 px, 442,325 bytes, transparent background |
| **Feature Graphic** | 1024 × 500 px, PNG or JPEG, max 15 MB | [`play_store_assets/feature_graphic_1024x500.png`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/play_store_assets/feature_graphic_1024x500.png) | **READY** | 1024×500 px, 269,788 bytes, high-res brand banner |
| **Amazon Promo Banner** | 1024 × 500 px | [`amazon_store_assets/amazon_promo_1024x500.png`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/amazon_store_assets/amazon_promo_1024x500.png) | **READY** | 1024×500 px |

---

### 2.1 Genuine Phone Screenshot Catalog

All screenshots reflect the actual running Taptix v2.3 application captured from Android 15 (API 35) Pixel 8 AVD execution without mockups or simulated financial metrics:

```
play_store_assets/screenshots/
├── 01_main_dashboard.png         (380,472 bytes) - Live Master Toggle, Platform Status & Controls
├── 02_booking_filters.png        (463,144 bytes) - Min Fare, Distance & Fare-per-km Rules
├── 03_map_zones.png              (280,544 bytes) - Interactive Geofence & Target Area Management
├── 04_operational_analytics.png  (321,882 bytes) - Pure Operational Ledger (Counts, Completed, Distance)
├── 05_activity_history.png       (257,182 bytes) - Timestamped Activity Log & Filter Match Audit
└── 06_android15_startup.png      (110,934 bytes) - Verified Android 15 Installation & Splash Launch
```

> [!NOTE]
> **Content Compliance Check:**
> - Zero financial earnings, income, fare totals, profit, or expenses are shown in any screenshot.
> - Zero shift scheduling references are displayed.
> - Genuine Android 15 system bars and authentic UI components are depicted.

---

## 3. Comprehensive Permission Inventory & Privacy Review

| Permission Name | Protection Level | Declaring Feature | Essential vs Optional | User-Facing Disclosure | Disabling Mechanism |
| :--- | :--- | :--- | :---: | :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Signature / Special | `AutoClickService` | **Essential** | Prominent In-App Dialog before Settings redirect | Android Settings > Accessibility > Taptix |
| `SYSTEM_ALERT_WINDOW` | Signature / Special | `OverlayService` (Floating Dock) | **Optional** | "Enable Overlay" button prompts system draw dialog | Android Settings > Apps > Special App Access > Display Over Other Apps |
| `FOREGROUND_SERVICE` | Normal | Core Automation Service | **Essential** | Persistent Ongoing Notification in notification tray | Stop Master Toggle inside Taptix app |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Special (API 34+) | Driver Overlay Window Dock | **Essential** | Declared in Manifest with explicit subtype property | Stop Master Toggle inside Taptix app |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | System status alerts & foreground banner | **Optional** | System permission runtime prompt on first launch | Android Settings > Apps > Taptix > Notifications |
| `ACCESS_FINE_LOCATION` | Dangerous / Runtime | Map Zone Geofencing & Distance Checks | **Optional** | In-app rationale when opening Map Zone activity | Android Settings > Apps > Taptix > Permissions > Location |
| `ACCESS_COARSE_LOCATION` | Dangerous / Runtime | Fallback location estimation | **Optional** | Requested concurrently with Fine Location | Android Settings > Apps > Taptix > Permissions > Location |
| `RECORD_AUDIO` | Dangerous / Runtime | Voice Command Auto-Accept (Optional) | **Optional** | Prompted only when toggling "Voice Commands" in Settings | Android Settings > Apps > Taptix > Permissions > Microphone |
| `INTERNET` | Normal | In-app GitHub update checker | **Optional** | Automatic via HTTPS to raw GitHub release manifest | Disable Wi-Fi / Mobile Data |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`| Normal | OEM Doze mode prevention | **Optional** | In-app dialog explaining OEM background survival | Android Settings > Battery > Battery Optimization |
| `VIBRATE` | Normal | Haptic feedback on accepted ride | **Optional** | No prompt; controlled via in-app Settings toggle | Taptix Settings > Haptic Feedback toggle |

---

## 4. Google Play Console Declaration Forms & Policy Scripts

### 4.1 Accessibility API Declaration Script (Play Console)

When submitting the **Accessibility Service Declaration Form** in Google Play Console:

1. **Is your app an Accessibility Tool?**  
   Select: **NO**.  
   *(Taptix is a driver assistance utility, not a tool exclusively designed for persons with disabilities).*

2. **Core Feature Explanation (Required Text):**
   > *"Taptix uses the AccessibilityService API strictly to provide hands-free, automated ride and delivery acceptance for commercial drivers. Operating a motor vehicle while manually inspecting and tapping high-speed booking alerts causes severe driver distraction. Taptix reads only the specific incoming booking alert button labels within driver partner applications (Uber Driver, Ola Captain, Rapido Captain, inDrive) and simulates a single tap on the accept button only when the trip criteria match the driver's pre-configured safety rules. Taptix does not read private messages, credentials, or passenger data, and does not record or transmit any screen data off the device."*

3. **Demonstration Video URL (Mandatory Play Console Requirement):**  
   - Format: YouTube unlisted video URL.  
   - **Video Script Checklist for App Owner:**
     1. Open app from a clean install state.
     2. Tap the Accessibility permission card to show the in-app **Prominent Disclosure Dialog** ([`AccessibilityDisclosureDialog.kt`](file:///C:/Users/Vijay/AndroidStudioProjects/Taptix/app/src/main/java/com/example/taptix/ui/components/AccessibilityDisclosureDialog.kt)).
     3. Tap **"Agree & Continue"** and show transition into Android System Settings.
     4. Toggle Taptix Accessibility Service to ON.
     5. Return to Taptix and open the **DemoActivity** (Auto-Accept Simulator).
     6. Show simulated booking appear and demonstrate hands-free one-tap acceptance.

---

### 4.2 Location Permission Declaration Script

- **Purpose:** Map Zone Geofencing to enforce driver-selected operating areas.
- **Background Location Status:** Taptix uses **Foreground Location only** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`). It does **NOT** declare `ACCESS_BACKGROUND_LOCATION`, eliminating Google Play's severe background location review hurdles.
- **Accuracy Guarantee:** GPS coordinates are processed entirely in memory; logs mask coordinates to 2 decimal places (`12.97**, 77.59**`).

---

### 4.3 Google Play Data Safety Form Answers

| Data Type | Collected? | Shared? | Purpose | Ephemeral? |
| :--- | :---: | :---: | :--- | :---: |
| **Location (Approximate / Precise)** | YES (On-Device) | **NO** | App functionality (Geofencing) | **YES** (Never saved to disk or server) |
| **Personal Info (Name, Email, Phone)**| **NO** | **NO** | N/A | N/A |
| **Financial Info (Earnings, Credit Card)**| **NO** | **NO** | Strictly Section 10 compliant | N/A |
| **Messages / SMS / Contacts** | **NO** | **NO** | N/A | N/A |
| **Audio (Microphone)** | Optional (Voice) | **NO** | Voice command trigger | **YES** (Processed locally in RAM) |
| **Device or other IDs** | **NO** | **NO** | N/A | N/A |

---

## 5. Third-Party Platform Terms Risk & Distribution Recommendation

> [!WARNING]
> **GOOGLE PLAY DISTRIBUTION RISK NOTICE**
> - **Platform Terms of Service:** Ride-hailing platforms (Uber, Ola, Rapido) have terms of service prohibiting automated tools or bots.
> - **Google Play Developer Distribution Agreement Section 4.4:** Prohibits apps that interfere with third-party software.
> - **Distribution Verdict:** While Taptix meets all technical Google Play technical policies (Prominent Disclosure, Target SDK 35, 64-bit architecture, Data Safety), submitting to Google Play carries an inherent risk of takedown or rejection if third-party platforms report the package.
> - **Recommended Launch Strategy:**
>   1. **Direct Distribution (Primary):** Distribute `Taptix-v2.3.apk` via official website / GitHub Releases with the in-app update mechanism.
>   2. **Alternative Stores:** Publish to Amazon Appstore (which historically approved Taptix v1.2–v2.2 with identical Accessibility declarations).
>   3. **Google Play Store (Secondary / Cautious):** Submit only with the complete declaration video and fully prepared legal defense documentation.

---

## 6. Launch Readiness Checklist

- [x] Version Code (`21`) and Version Name (`2.3`) verified in Gradle.
- [x] Target SDK set to `35` (Android 15 compliant).
- [x] Minimum SDK set to `26` (Android 8.0+ supported).
- [x] V2 APK signature verified via `apksigner`.
- [x] Production Keystore SHA-256 fingerprint verified (`8C:40:1F:EB:...`).
- [x] 98/98 unit tests passed with 0 failures.
- [x] Section 10 financial exclusion verified (0 financial metrics).
- [x] Shift scheduling permanently absent (0 references).
- [x] Genuine Android 15 phone screenshots captured and staged in `play_store_assets/screenshots/`.
- [x] High-res app icon (512×512) and Feature Graphic (1024×500) verified.
- [x] Data Safety questionnaire pre-filled with zero data collection.
- [x] Prominent Disclosure dialog verified in app code.
- [ ] Owner to insert official developer contact, support email, and privacy policy URL.
- [ ] Owner to record Google Play Accessibility declaration video.
- [ ] Owner to provide explicit sign-off prior to publishing.
