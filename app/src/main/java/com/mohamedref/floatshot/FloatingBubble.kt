package com.mohamedref.floatshot

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.roundToInt

/** A 44dp touch target around a user-adjustable 20–44dp semi-transparent visual bubble. */
internal class FloatingBubble(
    private val service: ScreenshotAccessibilityService,
    private val onTap: () -> Unit,
    private val onHold: () -> Unit
) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private val touchDp = 48
    private val touchPx get() = dp(touchDp)
    private val view = FrameLayout(service)
    private val icon = ImageView(service)
    private var added = false
    private var enabled = true
    private var params: WindowManager.LayoutParams? = null
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop * 1.4f
    private var downX = 0f
    private var downY = 0f
    private var initialX = 0
    private var initialY = 0
    private var downAt = 0L
    private var moved = false

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).roundToInt()
    private fun screenWidth(): Int = runCatching { manager.currentWindowMetrics.bounds.width() }
        .getOrDefault(service.resources.displayMetrics.widthPixels)
    private fun screenHeight(): Int = runCatching { manager.currentWindowMetrics.bounds.height() }
        .getOrDefault(service.resources.displayMetrics.heightPixels)

    fun show() {
        if (added) return
        icon.setImageResource(R.drawable.ic_bubble)
        icon.setBackgroundResource(R.drawable.bubble_background)
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.setPadding(dp(5), dp(5), dp(5), dp(5))
        icon.isClickable = false
        view.addView(icon)
        view.contentDescription = service.getString(R.string.bubble_description)
        view.setOnTouchListener(::onTouch)
        val prefs = SettingsStore.prefs(service)
        val onRight = prefs.getInt(SettingsStore.SIDE, 1) != 0
        val fraction = prefs.getFloat(SettingsStore.VERTICAL, 0.48f).coerceIn(0.07f, 0.88f)
        val spec = WindowManager.LayoutParams(
            touchPx, touchPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.LEFT // physical edges, unchanged by RTL
            it.x = if (onRight) (screenWidth() - touchPx) else 0
            it.y = ((screenHeight() - touchPx) * fraction).roundToInt()
            it.title = "FloatShot capture button"
        }
        params = spec
        refresh()
        try {
            manager.addView(view, spec)
            added = true
        } catch (_: Exception) {
            // A missing overlay token or a stopped accessibility service must not crash the app.
        }
    }

    fun refresh() {
        val size = dp(SettingsStore.diameter(service))
        val lp = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        icon.layoutParams = lp
        icon.alpha = SettingsStore.opacity(service) / 100f
        view.invalidate()
    }

    fun setVisible(visible: Boolean) {
        enabled = visible
        if (added) view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun reposition() {
        val layout = params ?: return
        val prefs = SettingsStore.prefs(service)
        layout.x = if (prefs.getInt(SettingsStore.SIDE, 1) != 0) screenWidth() - touchPx else 0
        val fraction = prefs.getFloat(SettingsStore.VERTICAL, 0.48f).coerceIn(0.07f, 0.88f)
        layout.y = ((screenHeight() - touchPx) * fraction).roundToInt()
        update()
    }

    private fun onTouch(target: View, event: MotionEvent): Boolean {
        if (!enabled) return true
        val spec = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                initialX = spec.x
                initialY = spec.y
                downAt = event.eventTime
                moved = false
                target.isPressed = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) moved = true
                if (moved) {
                    spec.x = (initialX + event.rawX - downX).roundToInt()
                        .coerceIn(0, (screenWidth() - touchPx).coerceAtLeast(0))
                    spec.y = (initialY + event.rawY - downY).roundToInt()
                        .coerceIn(0, (screenHeight() - touchPx).coerceAtLeast(0))
                    update()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                target.isPressed = false
                if (moved) {
                    val onRight = spec.x + touchPx / 2 > screenWidth() / 2
                    spec.x = if (onRight) (screenWidth() - touchPx).coerceAtLeast(0) else 0
                    update()
                    SettingsStore.prefs(service).edit()
                        .putInt(SettingsStore.SIDE, if (onRight) 1 else 0)
                        .putFloat(SettingsStore.VERTICAL,
                            spec.y.toFloat() / (screenHeight() - touchPx).coerceAtLeast(1))
                        .apply()
                } else if (event.eventTime - downAt >= ViewConfiguration.getLongPressTimeout()) {
                    onHold()
                } else {
                    onTap()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                target.isPressed = false
                return true
            }
        }
        return false
    }

    private fun update() {
        if (added) try {
            manager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) { /* detached by Android */ }
    }

    fun remove() {
        if (added) {
            try { manager.removeView(view) } catch (_: IllegalArgumentException) { /* already removed */ }
            added = false
        }
    }
}
