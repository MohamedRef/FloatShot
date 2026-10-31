package com.mohamedref.floatshot

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** Activated explicitly by the user in Android Accessibility settings. */
class ScreenshotAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var bubble: FloatingBubble? = null
    private var capture: ScreenshotEngine? = null
    private var settings: SharedPreferences? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsStore.prefs(this).also {
            it.registerOnSharedPreferenceChangeListener(this)
        }
        capture = ScreenshotEngine(this)
        bubble = FloatingBubble(
            service = this,
            onTap = { capture?.start(long = false) },
            onHold = { capture?.start(long = true) }
        ).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == SettingsStore.OPACITY || key == SettingsStore.DIAMETER) bubble?.refresh()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubble?.reposition()
    }

    internal fun setBubbleVisible(visible: Boolean) {
        bubble?.setVisible(visible)
    }

    internal fun notifyResult(stringId: Int) {
        Toast.makeText(this, getString(stringId), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        capture?.close()
        capture = null
        bubble?.remove()
        bubble = null
        settings?.unregisterOnSharedPreferenceChangeListener(this)
        settings = null
        super.onDestroy()
    }
}
