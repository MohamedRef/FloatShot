# FloatShot

Kotlin Android floating screenshot utility, designed for Samsung Galaxy S24 Ultra.

## Current repository status

The repository has been initialized. The complete application source has **not** yet been committed here; the Kotlin source ZIP was provided in the ChatGPT conversation. An Android APK build has **not** been verified.

## Intended features

- Floating translucent capture control
- Short tap for a single screenshot
- Long press for verified scrolling screenshot
- Offline image processing and lossless PNG output

## Source import

Extract the `FloatShot-Kotlin-source.zip` archive delivered in the conversation and commit its contents to this repository. GitHub Actions in that archive runs `:app:testDebugUnitTest` and `:app:assembleDebug` and uploads a debug APK artifact.

The screenshot stitching algorithm has passed 10 standalone JVM tests, but real-device validation remains necessary.
