---
name: Automation & Click Safety Issue
about: Report booking detection, filtering, or click dispatch issues with partner applications
title: "[AUTOMATION]: "
labels: ["automation", "triage"]
assignees: []
---

### Problem Summary
<!-- Brief description of the automation failure or unexpected behavior -->

### Target Partner Application Information
- **Partner App Name:** <!-- e.g., Uber Driver, Ola Driver, Rapido Captain, inDrive, Namma Yatri, Porter, etc. -->
- **Partner App Version:** <!-- e.g., v4.450.10001 (Found in Android App Info) -->

### Taptix Configuration & Setup
- **Taptix Version:** v2.3
- **Accessibility Service Enabled in Android Settings:** [ ] Yes / [ ] No
- **Floating HUD Overlay Active:** [ ] Yes / [ ] No
- **Active Automation Filters:**
  - Minimum Fare Threshold: <!-- e.g., ₹150 -->
  - Maximum Pickup Distance: <!-- e.g., 3.0 km -->
  - Rate per km Threshold: <!-- e.g., ₹15/km -->
  - Target Area / Geofence Mode: <!-- e.g., All Areas, Included Zone Only, Excluded Zone -->

### Observed Automation Behavior
<!-- Mark all that apply -->
- [ ] Offer card appeared on screen but was completely ignored by Taptix
- [ ] Offer met all criteria but the Accept button was not clicked
- [ ] Offer did NOT meet criteria but was accepted anyway (filtering bug)
- [ ] Repeated clicks dispatched for a single offer (duplicate-action bug)
- [ ] Direct click failed and coordinate fallback clicked the wrong area
- [ ] Accessibility service disconnected unexpectedly during driving shift

### Steps to Reproduce
1. Start Taptix service.
2. Open partner driver application.
3. Receive incoming booking offer.
4. Note Taptix behavior and HUD status.

### Expected Behavior
<!-- What should Taptix have done according to your filters? -->

### Actual Behavior
<!-- What did Taptix actually do? -->

### Privacy Notice & Sanitized Logs
<!--
PRIVACY RULE:
Strictly REDACT all customer/passenger names, pickup/drop addresses, phone numbers, and driver license details.
Only provide accessibility event tags or redacted view hierarchy information.
-->
