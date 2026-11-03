# Taptix v2.3 — Phase Z Implementation Plan

**Document Version:** 1.0.0  
**Target Branch:** `phase-z-reliability-improvements`  
**Base Commit:** `f08353e` (Taptix v2.3 Release Tag)  
**Package ID:** `com.example.taptix`  
**Status:** PROPOSED & READY FOR IMPLEMENTATION

---

## 1. Executive Summary & Purpose

Phase Y conducted an independent compatibility and reliability assessment of the published Taptix v2.3 release. The assessment verified that:
1. **The core architecture is rock solid on Android 15 (API 35)**: Zero startup crashes, zero ANRs, clean streamed installation, and fluid Compose UI rendering on Pixel 8.
2. **Algorithmic rules and deduplication passed 127/127 unit tests**: Multi-factor filtering (fare, distance, rate/km, inclusions, exclusions) and atomic pre-click registration operate reliably.
3. **Older Android SDK system images (API 26–34) are not installed locally on this host machine**: Marked truthfully as `BLOCKED`.
4. **Key operational edge cases were identified**:
   - Single-argument legacy `AcceptVerifier.evaluate(acceptButtonStillVisible)` could incorrectly mark button disappearance as `CONFIRMED` if invoked without positive confirmation keywords.
   - Missing OEM battery optimization shortcuts for modern Android 14/15 custom skins (HyperOS, OxygenOS 14, One UI 6).
   - Incomplete booking data handling under edge-case unparsed strings.

The mission of Phase Z is to address these verified items in a controlled development branch (`phase-z-reliability-improvements`), preparing a stable foundation for Taptix v2.4 while leaving the published v2.3 APK, tag, and keystore 100% untouched.

---

## 2. Phase Y Findings Review & Defect Classification

| Finding ID | Observed Behavior | Type | Severity | Affected Module |
| :--- | :--- | :--- | :--- | :--- |
| **Y-01** | `AcceptVerifier.evaluate(acceptButtonStillVisible: Boolean?)` single-parameter overload returns `CONFIRMED` when `acceptButtonStillVisible == false`, treating button disappearance alone as acceptance. | Confirmed Safety Defect | **MEDIUM** | `AutoAcceptCore.kt`, `AutoClickService.kt` |
| **Y-02** | Modern Android 14/15 OEM battery killers (Xiaomi HyperOS, OnePlus OxygenOS, Samsung One UI) require explicit manufacturer-specific background guidance to avoid background service pauses. | Usability & Reliability Limitation | **MEDIUM** | `BatteryGuidanceDialog.kt`, `AppSettings.kt` |
| **Y-03** | Rate-per-km filtering with unparsed distance or zero trip distance must consistently adhere to `fallbackOnUnparsedData` without silently bypassing minimum rate thresholds. | Boundary Robustness | **LOW** | `OfferRuleEngine.kt` |
| **Y-04** | System images for Android 8.0–14 (API 26–34) not present in local developer environment. | Environmental Limitation | **BLOCKED** | Host Android SDK |

---

## 3. Detailed Fix Plan for Phase Z

### Fix 1: Eliminate Button-Disappearance Acceptance False Positives
- **Problem:** `AcceptVerifier.evaluate(acceptButtonStillVisible: Boolean?)` maps `false` to `AcceptOutcome.CONFIRMED`. The release guidelines mandate: *"Do not treat disappearance of the Accept button as proof of acceptance. Require a fresh, booking-specific positive confirmation before marking an offer accepted."*
- **Solution:**
  1. Standardize all verification calls on the tri-parameter `AcceptVerifier.evaluate(acceptButtonStillVisible, positiveConfirmationPresent, negativeOrCancelledPresent)`.
  2. Deprecate or update the single-parameter fallback so that `false` alone returns `NOT_CONFIRMED` unless accompanied by positive confirmation signals.
  3. Update legacy unit tests in `AutoAcceptIntegrationTest` and `ComprehensiveRegressionTest` to assert that button disappearance without positive confirmation returns `NOT_CONFIRMED`.

### Fix 2: Harden Incomplete Booking Data Handling in `OfferRuleEngine`
- **Problem:** When an incoming offer contains an unparseable distance string, rate-per-km filtering must strictly respect the driver's configured `rules.fallbackOnUnparsedData` (`REJECT` vs `ALLOW`).
- **Solution:**
  1. Verify in `OfferRuleEngine.kt` that if `minRatePerKmEnabled` is active and trip distance is unparseable (`null` or `0.0`), the engine immediately evaluates `fallbackOnUnparsedData`.
  2. If `UnparsedDataFallback.REJECT`, reject with an explicit diagnostic message: `"Rejected because trip distance could not be parsed for rate-per-km evaluation"`.

### Fix 3: Expand Modern OEM Battery Optimization Guidance
- **Problem:** Devices running Xiaomi HyperOS, Samsung One UI 6, and OnePlus OxygenOS 14 enforce aggressive background freezes if the driver does not configure "No restrictions" and "Autostart".
- **Solution:**
  1. Update `BatteryGuidanceDialog.kt` to include explicit step-by-step instructions for Xiaomi HyperOS/MIUI, Samsung One UI, and BBK/OnePlus devices.
  2. Provide direct intents to open the device's system Application Details settings page (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) so drivers can grant battery exemptions in one tap.

---

## 4. Regression Risks & Mitigation Protocols

```
┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
│           REGRESSION RISK            │     │          MITIGATION PROTOCOL         │
├──────────────────────────────────────┤     ├──────────────────────────────────────┤
│ 1. Breaking existing 127 JVM unit    │ ──> │ Run ':app:testDebugUnitTest' after   │
│    tests by changing test signatures │     │ every atomic change. Keep 127/127.   │
├──────────────────────────────────────┤     ├──────────────────────────────────────┤
│ 2. Accidentally modifying published  │ ──> │ Work strictly on isolated branch     │
│    production v2.3 release artifacts │     │ 'phase-z-reliability-improvements'.  │
├──────────────────────────────────────┤     ├──────────────────────────────────────┤
│ 3. Introducing runtime crashes on    │ ──> │ Validate on Android Studio Pixel 8   │
│    Android 15 (API 35) AVD           │     │ emulator using logcat error traps.   │
└──────────────────────────────────────┘     └──────────────────────────────────────┘
```

---

## 5. Acceptance Verification Gate for Phase Z

To conclude Phase Z with a **`PASS`** verdict, the following criteria must be satisfied:
1. `git -C Taptix status` confirms all work is committed on `phase-z-reliability-improvements`.
2. Release tag `v2.3` and commit `f08353e` remain 100% unaltered.
3. 127/127 (or higher) JVM unit tests pass with zero failures and zero skipped tests.
4. `:app:lintVitalRelease` reports zero fatal errors.
5. Android Studio AVD execution confirms clean startup, Compose UI rendering, and zero fatal crashes.
6. The stable v2.3 APKs in `release_artifacts/` remain bit-for-bit identical to their recorded checksums.
