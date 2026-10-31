# Galaxy S24 Ultra manual QA (not yet performed)

The Android screenshot and accessibility integration must be verified on real hardware.
This checklist is intentionally a **plan**, not evidence of completed device tests.

## Before testing

- Build and install the debug APK on an S24 Ultra running One UI 6.1+.
- Enable FloatShot's accessibility service via system Settings.
- Open a long test article in Chrome and Samsung Internet.
- Hide the keyboard and stop any content that autoplays.
- Capture several pages and inspect the generated full-resolution PNG at 100% zoom.

## Required cases

| Case | How to test | Correct result |
|---|---|---|
| Single screenshot | Tap the edge bubble | Full screenshot, no bubble visible |
| Long article | Long-press while at top of article | No repeated lines or missing paragraphs at seams |
| Mid-article start | Start long capture from the middle | Preserves exactly the visible starting content |
| End-of-page | Capture a short page | Stops after the actual last content; no duplicate final viewport |
| 20-40px remainder | Stop near the bottom of the page | Appends only the remaining pixels, not a whole extra screen |
| Sticky top bar | Scroll a long settings page with toolbar | Toolbar appears once; no ghost toolbar within the image |
| Bottom tab bar | Scroll a view with bottom navigation | Bottom navigation appears once at end |
| Repeating list items | Repeat same-looking rows/cards | Exact seam or explicit failure; never guess an ambiguous join |
| Lazy-loaded feed | Try a feed that loads images while scrolling | Waits for stability or stops without saving a broken image |
| Video / animation | Scroll while content is playing | Refuses unstable captures rather than producing a torn seam |
| RTL Arabic page | Screenshot an Arabic article | Correct content order, no clipped text |
| Nested scroll panel | Test a narrow sub-pane | Capture only if the active vertical region is confidently identified |
| Secure screen | Try a DRM/secure app | No bypass; appropriate failure indication |
| Rotation | Rotate phone mid-capture | Fails safely, no corrupted PNG |
| Bubble movement | Drag to each edge, rotate, restart service | Dock position and opacity remain consistent |
| Gallery compatibility | Open long PNG in Samsung Gallery | Full-resolution image decodes without cropping or OOM |
| Accessibility off | Disable service | Bubble disappears, no background capture |
| Offline | Flight mode, then capture | Same function, no network dependency |

## Pass criteria

**Don't ship a production release** unless long captures pass several times in both Chrome and Samsung Internet with zero visual seams, and at least one end-of-page and sticky-header test passes. Any unsupported app must produce a clear failure instead of a "successful" broken image.

## Known fundamental limits

No general-purpose third-party app can access Samsung's proprietary long-capture implementation for every app. Certain animations and repeated perfectly identical full-screen patterns are mathematically unresolvable from visual matching alone. `FLAG_SECURE` must be respected. Height is capped to keep output broadly compatible with Android gallery viewers.
