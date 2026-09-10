# Current status: Android 1.1 trial (versionCode 2)
See HANDOVER section 16 for SMS implementation and verification limits. The original 1.0 notes below are historical. Use scripts/build-android.ps1 -PreviousApk <old-apk> for update compatibility verification.

# Android trial build

Date: 2026-09-10. User requested an installable Android APK for Samsung S24 Ultra.
Capacitor 8 embeds the built React assets; no server.url and no production-site wrapper.
Existing Firestore account storage and integer money logic remain in use.

## Build
Node 22+, Java 21, Android SDK platform 36 and build-tools 36.0.0.
Set JAVA_HOME and ANDROID_HOME for the shell, then:
```powershell
npm ci
npm run android:sync
cd android
.\gradlew.bat assembleDebug
```
APK: android/app/build/outputs/apk/debug/app-debug.apk.
This is a debug-signed trial, not the final release signing identity.
Keep the same signing identity for subsequent in-place updates. Do not commit keys.

## What is implemented
- Android application shell, Arabic label and RTL.
- Local bundled assets, including PDF reader; PWA service worker disabled for Android builds.
- Existing Firebase email/password authentication and persistence.
- Native system document picker for saving JSON/CSV, requiring no storage permission.
- Safe-area CSS compatibility with Capacitor system bars.
- Independent screen publication, concurrent historical reads, per-user in-flight read coalescing.

## Limits before a release
- Google login requires a native authentication integration and Firebase Android/OAuth registration.
  The trial explains this and offers email/password; web Google login remains unchanged.
  Do not create a different account to access existing data.
- (Outdated for 1.0 — since 1.1) READ_SMS is declared and on-demand SMS import exists; owner approved it
  as top priority (OVERRIDES §16). RECEIVE_SMS local collection is now implemented for review (OVERRIDES §17, HANDOVER §21); physical validation remains pending.
  Never import OTP/personal messages as transactions.
- Firestore is still the primary store; this is not a complete local-first/offline rewrite.
- Physical S24 Ultra validation is outstanding: login, cold start, imports, save picker,
  keyboard/insets, back gesture and account persistence.
- Existing backup coverage limitations remain (see HANDOVER); native saving does not expand coverage.
- APK installation does not itself resolve network latency. No phone speed percentage is claimed.
