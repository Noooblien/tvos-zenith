# Security Architecture & Vulnerability Audit

## 1. Executive Summary

**tvOS Zenith** is an ultra-lightweight, ad-free Android TV launcher engineered for legacy hardware (5–10 years old, 1GB–2GB physical RAM, Android 7.0 Nougat to Android 14). Unlike stock Google TV / Android TV home interfaces that run background web-views, recommendation daemons, and video ad trackers, tvOS Zenith operates under a **Zero-Trust, Zero-Telemetry, Air-Gapped security architecture**.

---

## 2. OWASP Mobile Top 10 (2024) Compliance Matrix

| OWASP Risk | Category | Zenith Mitigation & Enforcement | Audit Status |
| :--- | :--- | :--- | :---: |
| **M1** | **Improper Platform Usage** | Strictly validates Intent categories against `CATEGORY_LEANBACK_LAUNCHER` and `CATEGORY_LAUNCHER`. Strips all transitive URI permission flags (`FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_GRANT_WRITE_URI_PERMISSION`). | **VERIFIED PASS** |
| **M2** | **Inadequate Supply Chain Security** | Zero third-party ad networks, zero analytics SDKs, zero web-tracking libraries. Uses pinned AndroidX first-party dependencies only. | **VERIFIED PASS** |
| **M3** | **Insecure Authentication** | No remote accounts, no cloud sync, no credentials stored. Local settings strictly sandboxed. | **VERIFIED PASS** |
| **M4** | **Insufficient Input/Output Validation** | All external package names and class names undergo strict regex validation before Intent generation, blocking metacharacters, path traversals, and reflection tricks. | **VERIFIED PASS** |
| **M5** | **Insecure Communication** | **Zero Internet Permission** (`android.permission.INTERNET` is completely omitted from `AndroidManifest.xml`). Mathematically prevents data exfiltration. | **VERIFIED PASS** |
| **M6** | **Inadequate Privacy Controls** | Zero device telemetry, zero user habit profiling, zero advertising IDs (AAID/GAID). Full air-gapped TV launcher. | **VERIFIED PASS** |
| **M7** | **Insufficient Binary Protections** | ProGuard / R8 code obfuscation enabled. Native stripping, class repackaging, and dead-code stripping enabled in release builds. | **VERIFIED PASS** |
| **M8** | **Security Misconfiguration** | `android:allowBackup="false"` (blocks adb backup extraction), `android:usesCleartextTraffic="false"`, non-exported broadcast receivers (`android:exported="false"`). | **VERIFIED PASS** |
| **M9** | **Insecure Data Storage** | Zero plaintext databases, zero world-readable shared preferences, zero external storage queries. | **VERIFIED PASS** |
| **M10**| **Insufficient Cryptography** | No weak cryptographic primitives used; completely stateless design eliminates cryptographic surface risk. | **VERIFIED PASS** |

---

## 3. STRIDE Threat Model

```
+---------------------------------------------------------------------------------------+
|                                  STRIDE THREAT MODEL                                  |
+---------------------------------------------------------------------------------------+
| Threat Category         | Attack Vector                     | Zenith Architectural Defense    |
+-------------------------+-----------------------------------+---------------------------------+
| Spoofing                | Fake app mimicking system apps    | Strict PackageManager package   |
|                         |                                   | signature & component check     |
+-------------------------+-----------------------------------+---------------------------------+
| Tampering               | Intent payload injection via D-pad| Explicit ComponentName binding, |
|                         | or malicious broadcast            | zero extras passed to targets   |
+-------------------------+-----------------------------------+---------------------------------+
| Repudiation             | Malicious background actions      | Air-gapped: no background tasks |
|                         |                                   | or unmonitored threads          |
+-------------------------+-----------------------------------+---------------------------------+
| Information Disclosure  | ADB backup extraction of history  | android:allowBackup="false",    |
|                         | or app launch metrics             | no persistent launch logs saved |
+-------------------------+-----------------------------------+---------------------------------+
| Denial of Service       | OOM attack via gigantic app icons | UltraLightIconCache caps bitmap |
|                         | or thousands of fake packages     | memory strictly to 12MB ceiling |
+-------------------------+-----------------------------------+---------------------------------+
| Elevation of Privilege  | Transitive URI permission hijack  | Bitwise masking out of all      |
|                         |                                   | FLAG_GRANT_*_URI_PERMISSION     |
+-------------------------+-----------------------------------+---------------------------------+
```

---

## 4. Hardened IPC & Intent Sanitization Architecture

```mermaid
flowchart TD
    User([D-Pad Center Click]) --> Adapter[AppDockAdapter]
    Adapter --> Sanitizer[IntentSanitizer.createSafeLaunchIntent]
    
    subgraph Security Boundary
        Sanitizer --> RegexCheck{Regex Validation\nPackage & Class}
        RegexCheck -- Fail --> Block[Drop & Log Security Warning]
        RegexCheck -- Pass --> PMCheck{PackageManager\nComponent Resolution}
        PMCheck -- Unexported / Missing --> Fallback[Fallback to Safe Leanback Intent]
        PMCheck -- Verified Exported --> Build[Construct Explicit Intent]
        Build --> FlagStrip[Bitwise Strip FLAG_GRANT_*_URI_PERMISSION]
        FlagStrip --> SafeIntent[Pristine Intent with NEW_TASK]
    end
    
    SafeIntent --> StartActivity[Activity.startActivity]
```

### Key Security Safeguards in `IntentSanitizer.kt`:
1. **Explicit Component Enforcement**:
   ```kotlin
   val safeIntent = Intent(Intent.ACTION_MAIN).apply {
       this.component = ComponentName(packageName, activityClassName)
       addCategory(Intent.CATEGORY_LAUNCHER)
       sanitizeFlags(this)
   }
   ```
2. **Privilege Neutralization**:
   ```kotlin
   var flags = intent.flags
   flags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION.inv()
   flags = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION.inv()
   flags = flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION.inv()
   flags = flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION.inv()
   intent.flags = flags
   ```

---

## 5. Security Audit Verification Commands

To verify the security posture of the binary on a local or connected Android TV device:

```bash
# 1. Verify zero INTERNET permission
aapt dump permissions app/build/outputs/apk/release/app-release-unsigned.apk | grep -i internet
# Expected output: (empty)

# 2. Verify all internal components are non-exported
aapt dump xmltree app/build/outputs/apk/release/app-release-unsigned.apk AndroidManifest.xml | grep -E "(receiver|service|provider)" -A 5

# 3. Verify memory ceiling under load
adb shell dumpsys meminfo org.zenith.tvos | grep "TOTAL PSS"
# Target: < 18,000 KB (18MB)
```

---

## 6. Vulnerability Disclosure Policy

If you discover a security vulnerability within **tvOS Zenith**, please submit a confidential advisory via GitHub Security Advisories or report directly to the core engineering team. Fixes are prioritized and deployed within 24 hours.
