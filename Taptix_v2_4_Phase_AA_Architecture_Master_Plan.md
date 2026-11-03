# Taptix v2.4 — Phase AA: Architecture, Compatibility Improvements & Development Master Plan

**Document Version:** 1.0.0  
**Target Release:** Taptix v2.4  
**Current Production Base:** Taptix v2.3 (`versionCode: 21`, `commit: f08353e`)  
**Active Planning Branch:** `phase-aa-architecture-master-plan`  
**Application ID:** `com.example.taptix`  
**Status:** APPROVED FOR ARCHITECTURAL BASELINE (Zero Source Modifications in Phase AA)

---

## 1. Executive Summary & Mission Scope

Taptix v2.3 is currently published and verified on GitHub Releases as a stable, free distribution. Phase AA establishes the complete technical architecture and phased engineering master plan for upgrading Taptix to version 2.4.

### Primary Engineering Objectives for v2.4:
1. **Automation Safety & Tri-State Discipline:** Maintain zero-speculative-tap execution, bounded single-retry policies, and fresh positive acceptance verification.
2. **Dynamic Partner UI Profile Architecture:** Transition hardcoded selectors into modular, version-aware partner configuration profiles that adapt cleanly without risking unintended clicks.
3. **Android Version Compatibility (API 26–36):** Formalize architectural boundaries across Android 8.0 through Android 16 Developer Preview, accounting for runtime permissions, foreground service types, and 16 KB page size compatibility.
4. **OEM Background & Battery Resilience:** Mitigate aggressive vendor background freezes (Xiaomi HyperOS, Samsung One UI 6, BBK/OnePlus OxygenOS) through targeted system settings intents and service lifecycle hardening.
5. **Driver Ergonomics & Immediate Kill-Switch:** Provide rapid overlay HUD controls and physical hardware volume key emergency stop triggers.
6. **Strict Production Release Protection:** The published v2.3 APK, tag, keystore, and historical backups remain completely unmodified and preserved throughout all development activities.

---

## 2. Preliminary Reviews & Release Protection Audit

| Review Item | Verified State | Audit Finding |
| :--- | :--- | :--- |
| **Phase Y Compatibility Assessment** | Completed on Android 15 (Pixel 8 AVD); Profiles A–F & H marked `BLOCKED` | **VERIFIED** — Accurate real-world matrix; zero false pass claims. |
| **Phase Z Reliability Report & Plan** | `Taptix_Phase_Z_Implementation_Plan.md` created; 127/127 tests passing | **VERIFIED** — Eliminates button disappearance false-positives. |
| **Phase Z Source Code Diff** | `git diff f08353e 5bdd9d5` (5 files changed, +105/-7 lines) | **VERIFIED** — Targeted safety & NaN boundary improvements. |
| **Production Git Tag `v2.3`** | `refs/tags/v2.3` -> `f08353e2cc3e258fad8bc0a944dba658d8da544d` | **INTACT** — Immutable release pointer untouched. |
| **Production Keystore** | `release.keystore` (2,734 B, SHA-256: `032C3D6B...`) | **INTACT** — Local isolation; zero secrets committed or uploaded. |
| **Release Artifact Checksum** | `67515AEA44E2C2C0864BE18EC4A060B13443B402A5D16DEED29F28050025C1F9` | **INTACT** — Bit-for-bit exact match across all release storage. |
| **Milestone Backups** | Phase S, Phase U, Phase V, Phase W, Phase X | **INTACT** — All 5 APK artifacts preserved. |

---

## 3. Phase Z Code Review & Safety Baseline

Inspection of the actual Kotlin code on `phase-z-reliability-improvements` confirms three core safety invariants:

### A. Acceptance Verification Safety (`AutoAcceptCore.kt`)
- **Positive Confirmation Mandate:** An offer is strictly marked as `CONFIRMED` only when fresh booking-specific confirmation keywords (`"BOOKING ACCEPTED"`, `"NAVIGATE"`, `"START TRIP"`, `"ARRIVING"`, `"ORDER DETAILS"`) are detected in the active partner application.
- **Button Disappearance Safeguard:** Single-parameter `AcceptVerifier.evaluate(acceptButtonStillVisible)` was explicitly hardened in Phase Z to return `NOT_CONFIRMED` when the button disappears without accompanying positive confirmation text, eliminating false-positive acceptance logs.
- **Cancellation & Expiry Halts:** Negative keywords (`"EXPIRED"`, `"CANCELLED"`, `"MISSED"`, `"TOO LATE"`) immediately halt automation and record the outcome as unconfirmed.
- **Decoupled Booking Lifecycle:** The state machine enforces five distinct, non-conflated phases:
  ```text
  [1. Detected] ──> [2. Filter Matched] ──> [3. Click Attempted] ──> [4. Verified Confirmed] ──> [5. Completed]
  ```

### B. Click Execution Discipline (`AcceptClickExecutor.kt`)
- **Direct Action Priority:** Executes `AccessibilityNodeInfo.ACTION_CLICK` on clickable target views, eliminating coordinate overlay dispatch when direct clicks succeed (`DirectClickResult.SUCCESS`).
- **Uncertain Dispatch Protection (`UNKNOWN`):** When `performAction(ACTION_CLICK)` returns `false` on a clickable node, the engine records `UNKNOWN`, executing **zero coordinate fallbacks** and **zero speculative retries** to prevent double-tap conflicts.
- **Bounded Fallback:** Coordinate fallback is permitted at most once per offer cycle, and only when non-dispatch of the direct action is reliably established (`FAILED_CONFIRMED_NOT_DISPATCHED`).
- **Target Freshness Validation:** Before fallback execution, the target node must be verified valid, enabled, visible to user, and within the expected foreground partner application.
- **Pre-Click Concurrency Protection:** Offers are fingerprinted via `DuplicateOfferGuard` using package name, fare, distance, and button text before click invocation with a 4000 ms sliding window cooldown.

### C. Booking Filter Mathematics (`OfferRuleEngine.kt` & `OfferParser.kt`)
- **NaN & Infinity Guards:** `OfferParser.validateFare()` and `validateDistance()` enforce positive, finite floating-point boundaries ($\le 100,000$ fare, $\le 500$ km distance).
- **Rate-per-Kilometer Math:** `OfferRuleEngine` explicitly guards against `tripDist.isNaN()` and `tripDist.isInfinite()`, ensuring zero division anomalies cannot bypass configured minimum fare-per-km thresholds.
- **Fallback on Incomplete Data:** Unparsed distance or fare data strictly triggers the driver's configured `UnparsedDataFallback` policy (`REJECT` vs `ALLOW`).

---

## 4. Comprehensive Android OS Compatibility Matrix (API 26–36)

Taptix v2.4 must maintain broad compatibility across the Android ecosystem while adapting to evolving platform privacy and security models.

| Profile | Android Version | API Level | Local Execution Status | Technical Platform Requirements & Architecture Adjustments |
| :---: | :--- | :---: | :---: | :--- |
| **A** | Android 8.0 / 8.1 Oreo | 26 / 27 | **BLOCKED** *(Image not installed)* | **Baseline API Floor.** Uses Notification Channels, JobIntentService compatibility, basic AccessibilityService lifecycle. Adaptive icons and vector drawables standard. |
| **B** | Android 10 Q | 29 | **BLOCKED** *(Image not installed)* | Scoped Storage introduction. Background activity launch restrictions (`canDrawOverlays` required for floating HUD). Mandatory dark theme support. |
| **C** | Android 11 R | 30 | **BLOCKED** *(Image not installed)* | Package visibility filtering (`<queries>` tag in manifest required to inspect commercial partner applications). One-time permissions support. |
| **D** | Android 12 / 12L S | 31 / 32 | **BLOCKED** *(Image not installed)* | Foreground service launch restrictions from background. Notification trampolines blocked. `FLAG_IMMUTABLE` / `FLAG_MUTABLE` required on all PendingIntents. |
| **E** | Android 13 Tiramisu | 33 | **BLOCKED** *(Image not installed)* | Runtime `POST_NOTIFICATIONS` permission required. Per-app language preferences. Battery saver and App Standby Buckets restrictions. |
| **F** | Android 14 Upside Down Cake | 34 | **BLOCKED** *(Image not installed)* | Explicit foreground service types required in manifest (`specialUse`, `location`). Strict broadcast receiver registration flags (`RECEIVER_NOT_EXPORTED`). |
| **G** | Android 15 Vanilla Ice Cream | 35 | **TESTED & VERIFIED** *(Pixel 8 AVD)* | Mandatory edge-to-edge system insets. Predictive back navigation. Strict 16 KB ELF page alignment readiness. Clean streamed installation and 0 crashes verified. |
| **H** | Android 16 Baklava | 36 | **BLOCKED** *(Image not installed)* | Developer preview / experimental. Early alignment for future SDK 36/37 compile targets and system photo picker sandboxing. |

*Rule Compliance: Only Profile G is marked as tested; Profiles A–F and H remain honestly documented as `BLOCKED` due to local host system-image availability.*

---

## 5. Taptix v2.4 Feature Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            TAPTIX v2.4 ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────────────┐   ┌────────────────────────┐   ┌──────────────┐  │
│  │  Dynamic UI Profiles  │   │  Automation Engine v2  │   │  Kill-Switch │  │
│  │  • Uber Driver        │   │  • Tri-State Dispatch  │   │  • Floating  │  │
│  │  • Ola Driver         │──>│  • Target Freshness    │<──│    HUD Tap   │  │
│  │  • Rapido Captain     │   │  • Bounded Fallback    │   │  • Volume    │  │
│  │  • inDrive / Namma    │   │  • Pre-Click Signature │   │    Key Event │  │
│  └───────────────────────┘   └───────────┬────────────┘   └──────────────┘  │
│                                          │                                  │
│                                          ▼                                  │
│                              ┌───────────────────────┐                      │
│                              │  AcceptVerifier v2    │                      │
│                              │  • Fresh Keywords     │                      │
│                              │  • Cancel Signal Trap │                      │
│                              │  • Activity Logger    │                      │
│                              └───────────────────────┘                      │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────┐   ┌────────────────────────┐   ┌──────────────┐  │
│  │  OEM Battery Defense  │   │  Smart Filter Core     │   │ Map Geofence │  │
│  │  • HyperOS / One UI   │   │  • Min / Max Fare      │   │ • On-Device  │  │
│  │  • ColorOS / OxygenOS │   │  • Rate / Km Math      │   │ • Inclusion  │  │
│  │  • App-Details Intent │   │  • Category Exclusions │   │ • Exclusion  │  │
│  └───────────────────────┘   └────────────────────────┘   └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### A. Dynamic Partner UI Profiles
1. **Decoupled Architecture:** Move hardcoded partner string literals and button selectors out of monolithic service files into modular declarative profile objects implementing `PartnerUiProfile`.
2. **Version-Aware Selectors:** Each partner profile defines prioritized view selectors (resource IDs, text regexes, content descriptions) and slide-to-accept bounding box coordinates.
3. **Graceful Degradation:** When a partner app updates its UI hierarchy to an unrecognized layout, the profile marks the screen as `UNRECOGNIZED_LAYOUT`, logging an informational event and safely executing **zero clicks**.
4. **Strict Allowlist Parity:** Preserves the 20 commercial rideshare/delivery packages defined in `PartnerPackages.SUPPORTED`.

### B. Automation Safety Engine & Emergency Stop
1. **Centralized Click Pipeline:** All click requests route through a single hardened executor maintaining the tri-state outcome model (`SUCCESS`, `FAILED_CONFIRMED_NOT_DISPATCHED`, `UNKNOWN`).
2. **Instant Kill-Switch:**
   - *Hardware Volume Key Trigger:* Double-pressing either volume key immediately unbinds automation clicking and pauses the foreground service.
   - *Overlay HUD Emergency Stop:* A prominent red "HALT" control on the floating HUD instantly stops active click sequences.
3. **Sanitized Diagnostic Ring Buffer:** Maintain an in-memory ring buffer (last 50 events) capturing anonymized automation timestamps, click methods, and outcomes for driver debugging without storing personal or passenger data.

### C. OEM Background Resilience & Battery Defense
1. **Manufacturer Intent Dispatcher:** Enhance `BatteryGuidanceDialog` with programmatic intent shortcuts directing drivers straight to vendor-specific battery screens:
   - Xiaomi / Poco: `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`
   - Samsung: `com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity`
   - Huawei / Honor: `com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity`
   - Standard Fallback: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `package:com.example.taptix`
2. **Doze Mode Awareness:** Foreground notification with high priority channel ensures the Android kernel does not freeze the accessibility event dispatcher during active shifts.

### D. Map & Target Area Geofencing
1. **Strictly On-Device Haversine Calculation:** Geofence evaluation operates 100% locally on device without external GPS tracking or cloud telemetry.
2. **Zone Polygons & Radius Circles:** Support center-point radius geofencing with driver-configurable inclusion (accept only inside) and exclusion (reject inside) rules.
3. **Numerical Safety:** Coordinates and distances bounded to valid physical limits ($\text{latitude} \in [-90, 90]$, $\text{longitude} \in [-180, 180]$, $\text{radius} \le 100\text{ km}$).

### E. Security, Privacy & Third-Party Policy Compliance
1. **Zero Cloud Telemetry:** No user analytics SDKs (Firebase, Mixpanel, Adjust, etc.) will ever be integrated.
2. **No Anti-Bot Bypass:** Taptix interacts strictly through sanctioned Android Accessibility APIs as an assistive utility. It does not tamper with partner application binaries, inject memory hooks, or bypass CAPTCHAs.
3. **Privacy First:** Driver location coordinates, passenger names, trip addresses, and fares are processed strictly in ephemeral RAM for filter math and are never stored off-device.

---

## 6. Phased Implementation Roadmap for Taptix v2.4

```text
Phase AA ──> Phase AB ──> Phase AC ──> Phase AD ──> Phase AE ──> Phase AF
(Plan)       (Profiles)   (Safety)     (OEM/Diag)   (Harness)    (Release Gate)
```

| Phase | Title | Primary Deliverable | Source Safety Rule |
| :---: | :--- | :--- | :--- |
| **Phase AA** | Architecture Master Plan | This comprehensive technical master plan & baseline audit | **Zero source modifications** |
| **Phase AB** | Dynamic Partner UI Profiles | Decoupled `PartnerUiProfile` architecture for 20 commercial partner apps | Branch `phase-ab-partner-profiles` |
| **Phase AC** | Automation Safety & Kill-Switch | Centralized click executor, volume-key emergency halt, diagnostic ring buffer | Branch `phase-ac-automation-safety` |
| **Phase AD** | OEM Background Resilience | Vendor-specific battery intents & Doze mode lifecycle hardening | Branch `phase-ad-oem-resilience` |
| **Phase AE** | Mock Partner Harness & E2E Tests | Synthetic mock driver apps & Android Studio AVD verification | Kept strictly outside production allowlist |
| **Phase AF** | Final Release Gate & v2.4 APK | Release APK generation, 127+ unit tests, apksigner check, release notes | Signed with original production keystore |

---

## 7. Phase AA Conclusion & Sign-Off

```
╔═════════════════════════════════════════════════════════════════════════════╗
║                      PHASE AA MASTER PLAN: COMPLETE (PASS)                  ║
╠═════════════════════════════════════════════════════════════════════════════╣
║  • Published v2.3 Release     : 100% Preserved (Tag v2.3, Hashes & Keystore)║
║  • Planning Branch            : phase-aa-architecture-master-plan           ║
║  • Source Code Modifications  : 0 files modified (Strict Read-Only)         ║
║  • Preliminary Reviews        : Phase Y and Phase Z audits verified         ║
║  • Android Support Target     : API 26 through API 36 (Baseline API 35)     ║
║  • Architectural Blueprint    : Modular profiles, kill-switch, OEM defense  ║
║  • Implementation Phasing     : Defined across Phases AB through AF         ║
╚═════════════════════════════════════════════════════════════════════════════╝
```

*Phase AA is complete. The technical architecture and master plan for Taptix v2.4 are fully documented and established without modifying any production code. Awaiting your instruction.*
