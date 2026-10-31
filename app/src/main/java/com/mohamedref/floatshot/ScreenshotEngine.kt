package com.mohamedref.floatshot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative screenshot state machine: capture -> settle -> visually align -> spool strips ->
 * lossless PNG. Unknown alignment aborts instead of silently generating a broken long shot.
 */
internal class ScreenshotEngine(private val service: ScreenshotAccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var active = false
    private var closed = false
    private var session: LongSession? = null

    private data class Frame(val bitmap: Bitmap, val xOffset: Int, val yOffset: Int, val window: Boolean)
    private class LongSession(val target: ScreenTarget.Target, val folder: File) {
        val tiles = ArrayList<ImageSaver.Tile>()
        var profile: ColumnFrame? = null
        var region: Rect? = null
        var firstWindow: Boolean? = null
        var steps = 0
        var height = 0
        var partial = false
        fun cleanup() { folder.listFiles()?.forEach { it.delete() }; folder.delete() }
    }

    fun start(long: Boolean) {
        if (closed) return
        if (active) {
            service.notifyResult(R.string.busy)
            return
        }
        active = true
        service.setBubbleVisible(false)
        handler.postDelayed({
            if (closed) return@postDelayed
            if (long) startLong() else startSingle()
        }, 190)
    }

    private fun startSingle(message: Int = R.string.saved_image) {
        capture(target = null) { result ->
            if (result == null) { finish(R.string.capture_failed); return@capture }
            io.execute {
                val success = try { ImageSaver.saveRegular(service, result.bitmap) } finally {
                    result.bitmap.recycle()
                }
                handler.post { finish(if (success) message else R.string.capture_failed) }
            }
        }
    }

    private fun startLong() {
        val metrics = service.resources.displayMetrics
        val screen = runCatching {
            service.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
        }.getOrDefault(Rect(0, 0, metrics.widthPixels, metrics.heightPixels))
        val width = screen.width()
        val height = screen.height()
        val target = ScreenTarget.discover(service, width, height) ?: ScreenTarget.Target(
            Rect(0, height / 10, width, (height * 0.90).toInt()),
            -1, Rect(0, 0, width, height), ""
        )
        val folder = File(service.cacheDir, "long_${SystemClock.uptimeMillis()}")
        val s = LongSession(target, folder)
        session = s
        capture(target) { frame ->
            if (frame == null) { abort(R.string.capture_failed); return@capture }
            io.execute {
                val region = viewport(s.target, frame)
                if (region == null) {
                    frame.bitmap.recycle()
                    handler.post { abort(R.string.long_alignment_failed) }
                    return@execute
                }
                val first = runCatching {
                    val profile = makeProfile(frame.bitmap, region)
                    val tile = ImageSaver.writeTile(s.folder, frame.bitmap, 0, region.bottom)
                    s.profile = profile
                    s.region = region
                    s.firstWindow = frame.window
                    s.tiles.add(tile)
                    s.height = tile.height
                }.isSuccess
                frame.bitmap.recycle()
                handler.post {
                    if (!first) abort(R.string.capture_failed)
                    else swipe(s)
                }
            }
        }
    }

    private fun swipe(s: LongSession) {
        if (closed || session !== s) return
        if (ScreenTarget.scrollPrecisely(service, s.target)) {
            // Uses Android 15's fractional scroll action when a view explicitly supports it.
            // No fling means more overlap, lower chance of skipped content.
            handler.postDelayed({ checkSettled(s, null, 0) }, 600)
            return
        }
        val area = s.target.viewportOnScreen
        val startY = (area.top + area.height() * 0.78f)
        val endY = (area.top + area.height() * 0.26f)
        val x = area.centerX().toFloat()
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, 850)
        ).build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                handler.postDelayed({ checkSettled(s, null, 0) }, 600)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (session === s) abort(R.string.long_alignment_failed)
            }
        }, handler)
        if (!accepted && session === s) {
            // Do not present a fake long shot if the destination refuses injected gestures.
            abort(R.string.long_alignment_failed)
        }
    }

    /** Checks two screenshots at least ~450ms apart, so a fling cannot create a torn seam. */
    private fun checkSettled(s: LongSession, prior: Frame?, tries: Int) {
        if (closed || session !== s) { prior?.bitmap?.recycle(); return }
        capture(if (s.firstWindow == false) null else s.target) { fresh ->
            if (fresh == null) {
                prior?.bitmap?.recycle()
                abort(R.string.capture_failed)
                return@capture
            }
            if (fresh.window != s.firstWindow ||
                (prior != null && (fresh.bitmap.width != prior.bitmap.width ||
                    fresh.bitmap.height != prior.bitmap.height ||
                    fresh.xOffset != prior.xOffset || fresh.yOffset != prior.yOffset))) {
                prior?.bitmap?.recycle()
                fresh.bitmap.recycle()
                abort(R.string.long_alignment_failed)
                return@capture
            }
            if (prior == null) {
                handler.postDelayed({ checkSettled(s, fresh, 0) }, 520)
                return@capture
            }
            io.execute {
                val stable = runCatching {
                    val region = s.region ?: error("Missing region")
                    val a = makeProfile(prior.bitmap, region)
                    val b = makeProfile(fresh.bitmap, region)
                    OverlapMatcher.isStationary(a, b)
                }.getOrDefault(false)
                prior.bitmap.recycle()
                handler.post {
                    if (closed || session !== s) { fresh.bitmap.recycle(); return@post }
                    if (stable) {
                        consume(s, fresh)
                    } else if (tries < 3) {
                        handler.postDelayed({ checkSettled(s, fresh, tries + 1) }, 520)
                    } else {
                        fresh.bitmap.recycle()
                        abort(R.string.long_moving_content)
                    }
                }
            }
        }
    }

    private fun consume(s: LongSession, frame: Frame) {
        io.execute {
            val outcome = runCatching {
                val region = s.region ?: error("Missing viewport")
                val prev = s.profile ?: error("Missing previous frame")
                val after = makeProfile(frame.bitmap, region)
                when (val match = OverlapMatcher.align(prev, after)) {
                    is Alignment.Scrolled -> {
                        // Prevent a dimension overflow / gigantic gallery-unreadable images.
                        val nextHeight = s.height + match.pixels + frame.bitmap.height - region.bottom
                        if (nextHeight > MAX_HEIGHT || s.steps >= MAX_STEPS) {
                            s.partial = true
                            Completion.END
                        } else {
                            val tile = ImageSaver.writeTile(s.folder, frame.bitmap,
                                region.bottom - match.pixels, region.bottom)
                            s.tiles.add(tile)
                            s.height += tile.height
                            s.profile = after
                            s.steps++
                            Completion.AGAIN
                        }
                    }
                    Alignment.Stationary -> Completion.END
                    Alignment.Uncertain -> Completion.ERROR
                }
            }.getOrDefault(Completion.ERROR)
            handler.post {
                when (outcome) {
                    Completion.AGAIN -> { frame.bitmap.recycle(); swipe(s) }
                    Completion.END -> completeLong(s, frame)
                    Completion.ERROR -> { frame.bitmap.recycle(); abort(R.string.long_alignment_failed) }
                }
            }
        }
    }

    private enum class Completion { AGAIN, END, ERROR }

    private fun completeLong(s: LongSession, last: Frame) {
        if (s.steps == 0) {
            // A page that cannot scroll should still produce the requested image, honestly labeled.
            io.execute {
                val success = try { ImageSaver.saveRegular(service, last.bitmap) }
                    finally { last.bitmap.recycle(); s.cleanup() }
                handler.post { finish(if (success) R.string.long_not_available else R.string.capture_failed) }
            }
            return
        }
        io.execute {
            val success = runCatching {
                val bottom = s.region!!.bottom
                if (!s.partial && bottom < last.bitmap.height) {
                    s.tiles.add(ImageSaver.writeTile(s.folder, last.bitmap, bottom, last.bitmap.height))
                }
                ImageSaver.saveLong(service, s.tiles)
            }.getOrDefault(false)
            last.bitmap.recycle()
            s.cleanup()
            handler.post {
                finish(if (!success) R.string.capture_failed
                    else if (s.partial) R.string.long_partial_saved else R.string.saved_image)
            }
        }
    }

    private fun viewport(target: ScreenTarget.Target, frame: Frame): Rect? {
        val region = Rect(target.viewportOnScreen).apply { offset(-frame.xOffset, -frame.yOffset) }
        val valid = region.intersect(0, 0, frame.bitmap.width, frame.bitmap.height)
        if (!valid || region.width() < frame.bitmap.width * 0.82 ||
            region.height() < 250 || region.bottom > frame.bitmap.height) return null
        return region
    }

    private fun makeProfile(bitmap: Bitmap, rect: Rect): ColumnFrame {
        val cols = 72
        return ColumnFrame.sample(rect.height(), cols) { y, column ->
            val x = rect.left + ((column + 1L) * (rect.width() - 1) / (cols + 1)).toInt()
            bitmap.getPixel(x, rect.top + y)
        }
    }

    private fun abort(message: Int) {
        val s = session
        session = null
        if (s != null) io.execute { s.cleanup() }
        finish(message)
    }

    private fun finish(message: Int) {
        if (closed) return
        active = false
        session = null
        service.setBubbleVisible(true)
        service.notifyResult(message)
    }

    private fun capture(target: ScreenTarget.Target?, attempts: Int = 0, reply: (Frame?) -> Unit) {
        if (closed) { reply(null); return }
        val window = Build.VERSION.SDK_INT >= 34 && target != null && target.windowId >= 0
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                if (closed) {
                    screenshot.hardwareBuffer.close()
                    return
                }
                val bitmap = runCatching {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        buffer.close()
                    }
                }.getOrNull()
                if (bitmap == null) { reply(null); return }
                // Only use per-window screenshots when their dimensions map exactly to screen
                // bounds; otherwise fall back to full display and hide the overlay.
                if (window && target != null &&
                    bitmap.width == target.rootBounds.width() &&
                    bitmap.height == target.rootBounds.height()) {
                    reply(Frame(bitmap, target.rootBounds.left, target.rootBounds.top, true))
                } else if (window) {
                    bitmap.recycle()
                    captureDisplay(attempts, reply)
                } else {
                    reply(Frame(bitmap, 0, 0, false))
                }
            }

            override fun onFailure(errorCode: Int) {
                if (closed) return
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && attempts < 5) {
                    handler.postDelayed({ capture(target, attempts + 1, reply) }, 420)
                } else if (window && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW) {
                    captureDisplay(attempts, reply)
                } else {
                    // In particular, never retry a FLAG_SECURE failure via another method.
                    reply(null)
                }
            }
        }
        try {
            if (window && target != null) {
                service.takeScreenshotOfWindow(target.windowId, service.mainExecutor, callback)
            } else {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
            }
        } catch (_: Exception) { reply(null) }
    }

    private fun captureDisplay(attempts: Int, reply: (Frame?) -> Unit) {
        capture(null, attempts, reply)
    }

    fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        session?.let { s -> io.execute { s.cleanup() } }
        session = null
        io.shutdown()
    }

    private companion object {
        const val MAX_HEIGHT = 26_000 // broadly viewable on-device; lossless streaming avoids OOM
        const val MAX_STEPS = 25
    }
}
