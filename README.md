# FloatShot

A tiny, local-only floating screenshot utility for Android, built with Kotlin. Designed for Samsung Galaxy S24 Ultra (Android 14+) and compatible with Android 11+ (API 30+).

## What it does

- **Tap** the edge bubble: one regular screenshot.
- **Long press and release**: a verified scrolling screenshot (automatically swipes, captures, aligns, and stitches).
- **Drag** the 48dp transparent touch target to dock on the left/right edge.
- Change the visible circle size (20–44dp) and opacity (8–70%).
- Saves lossless PNG images in **Pictures/FloatShot**, visible in Gallery.
- English and Arabic system-language UI; RTL layout support.
- No ads, accounts, trackers, analytics, network calls, root, or broad storage permission.

## Long screenshot quality and limits

**This cannot honestly guarantee a flawless long screenshot in every app.** Samsung's proprietary Scroll Capture engine is not exposed as a public general-purpose API for arbitrary third-party apps. This implementation instead prioritizes correctness:

1. Detects the active app's scrollable view bounds using accessibility geometry; never reads node text.
2. On Android 14+ uses `AccessibilityService.takeScreenshotOfWindow()` when the window dimensions can be mapped exactly, bypassing the floating control. Otherwise hides the control and captures the display using `takeScreenshot()`.
3. Uses Android 15+ fractional accessibility scrolling when the target view supports it (53% of the visible viewport, no fling). Otherwise scrolls slowly using `dispatchGesture()`. Waits for the page to stop moving.
4. Takes **two settled captures** at least 520ms apart; tries again for ongoing motion.
5. Computes overlap at **1-pixel precision** from visual samples, not swipe distance. Checks match quality and rejects ambiguous repeated patterns.
6. Spools each verified segment to private temporary files and creates a **lossless PNG by streaming rows**, without allocating one enormous bitmap.
7. Does **not save a long image on failed alignment** (rather than silently duplicate or skip rows).

Fixed headers / bottom bars are included once, provided the app exposes its scrolling viewport correctly. Animated feeds, auto-scrolling apps, images loading mid-capture, repeated full-screen patterns, nested horizontal/narrow scrolling panes, and `FLAG_SECURE` screens may not work. Image height is capped at **26,000px** for gallery compatibility; reaching that limit saves a clearly labeled verified **partial** image. On a non-scrolling page it saves an ordinary screenshot and says so. It cannot override DRM or secure screen restrictions.

## Build

Android Studio (recent stable version with JDK 17) can import this project. Install Android SDK Platform 35, Android SDK Build Tools 35, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, and Gradle 8.11.1. It uses only the Android SDK and Kotlin standard library (no runtime dependencies).

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The GitHub Actions workflow sets up Gradle and SDK 35 and automatically produces a downloadable debug APK under **Actions → Android build and tests → Artifacts**. This source archive does not include an `android.jar`, Gradle wrapper JAR or a prebuilt APK; building requires Android tools and network access to download Gradle dependencies. Run `gradle wrapper --gradle-version 8.11.1` once if you want a project-local wrapper.

### Install on Galaxy S24 Ultra

1. Build the debug APK or download the GitHub Actions build artifact after publishing this project.
2. Install the APK (allow installing from that source when Android prompts).
3. Open FloatShot and select **Set up floating button**.
   If Android marks the self-built APK's accessibility permission as restricted, inspect the source first and decide whether to trust it. Android's official flow for trusted apps is **Settings → Apps → FloatShot → ⋮ → Allow restricted settings** (the location varies by One UI version). See [Android Help](https://support.google.com/android/answer/12623953). This grants powerful access; do not do this for unknown APKs.
4. Under Android **Settings → Accessibility → Installed apps**, enable **FloatShot floating capture**. On some One UI versions, the menu wording is slightly different. Android shows a broad accessibility-access disclosure; this permission is needed to take screenshots and automate swipes.
5. Leave FloatShot. Tap / hold / drag the bubble in any supported app.

If One UI suspends the service, set FloatShot battery usage to **Unrestricted** in app settings. This is optional and is not done automatically.

For real-device validation, use [Galaxy S24 Ultra QA matrix](docs/GALAXY_S24_ULTRA_QA.md).

## Accessibility and privacy

- The accessibility service declares `canTakeScreenshot`, `canPerformGestures`, and `canRetrieveWindowContent`. The third capability lets it determine *the rectangle and package name of the visible scrollable view* so the stitched image does not repeat app bars. The code never calls node text APIs (`getText`, `getContentDescription`) to inspect third-party app content.
- The button uses `TYPE_ACCESSIBILITY_OVERLAY`; no `SYSTEM_ALERT_WINDOW` approval needed.
- Images are processed locally, and the app has **no INTERNET permission**. No content is logged or uploaded.
- The service is controlled entirely by the Android Accessibility settings. Disabling it removes the floating bubble.
- Android 14+ window capture and Android 11+ display capture are implemented with documented platform APIs.

See official documentation: [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

## Tests

`OverlapMatcherTest` includes exact pixel displacement, final-page movement, stationary state, blank views, ambiguous repeated cards, unrelated navigation, and a full-header/footer seam verification. `StreamingPngTest` decodes the PNG using ImageIO and verifies dimensions and RGBA pixels.

Tests exercise the portable matching and encoding logic; **on-device integration tests on a Galaxy S24 Ultra are still necessary** for One UI, Chrome, Samsung Internet, WhatsApp and other apps. No claim of device certification or a verified installable APK is made here.

## GitHub

The canonical project repository is [MohamedRef/FloatShot](https://github.com/MohamedRef/FloatShot).

On every push to `main`, GitHub Actions runs Android unit tests and packages an installable debug APK in workflow artifacts. The app remains experimental until its full device behavior is confirmed on a Galaxy S24 Ultra.

## License

MIT; see [LICENSE](LICENSE).
