# FloatShot installation and security

## Why Google Play Protect may block the APK

FloatShot intentionally declares an Android accessibility service for tap-triggered screenshots and scrolling gestures. Google Play Protect can block apps with sensitive accessibility capabilities that were obtained via browsers or file managers. This is an operating-system security decision, not a Gradle build error. Using GitHub Releases or changing the signature alone does not remove the warning. Do not disable Play Protect just to install the app.

Android warning guidance: https://developers.google.com/android/play-protect/warning-dev-guidance

## Developer testing on your Galaxy S24 Ultra

Use only your own trusted computer and official Android development tools.

1. Install Android Studio or the official Android SDK Platform Tools.
2. On the device, open Settings > About phone > Software information and tap Build number repeatedly to enable Developer options. Turn on USB debugging in Developer options.
3. Connect the unlocked device to your computer by USB and approve debugging only for your trusted computer.
4. Open FloatShot in Android Studio, select the device and choose Run, or use Android's official ADB tool to install a verified debug APK.

   ```sh
   adb devices
   adb install path/to/FloatShot-v0.2.0-preview.1.apk
   ```

5. If Play Protect or another security policy still blocks installation, stop. Do not disable system protections or try to evade the warning. Use a reviewed distribution method or request a mistaken-warning review.
6. Turn off USB debugging after testing if it is no longer needed.

Android Studio testing guide: https://developer.android.com/studio/run/device

## Preview APK and signing

The GitHub Preview Release contains a debug-signed APK and its SHA-256 checksum file. These files are experimental and have not been validated on a Galaxy S24 Ultra. CI debug signing keys may differ between runner instances. Installing an upgrade requires a consistent signing certificate and a higher versionCode; normal releases should use a securely stored, stable signing key.

## Accessibility disclosure and distribution

Before opening system Accessibility settings, FloatShot shows a prominent disclosure of its screen-capture, view-structure and gesture capabilities and asks for user acknowledgment. Consent does not grant permissions; the user must independently enable the service in Android settings. For public distribution, prepare the appropriate Google Play testing/release track and comply with Accessibility API policy. Do not claim the app is a disability accessibility tool unless it genuinely qualifies.

Google Play policy: https://support.google.com/googleplay/android-developer/answer/10964491

## Privacy

- Screenshots remain on the device and are not uploaded.
- The network permission is used to check official public GitHub release metadata.
- FloatShot does not request SMS or notifications access.
- The service is user-controlled and can be disabled from Android Accessibility settings.
- It does not bypass secured or DRM-protected screens.
