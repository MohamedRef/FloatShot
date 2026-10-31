package com.mohamedref.floatshot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        status = findViewById(R.id.status_text)
        statusDot = findViewById(R.id.status_dot)
        settingsButton = findViewById(R.id.enable_button)
        settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val opacitySeek = findViewById<SeekBar>(R.id.opacity_seek)
        val sizeSeek = findViewById<SeekBar>(R.id.size_seek)
        val opacityText = findViewById<TextView>(R.id.opacity_value)
        val sizeText = findViewById<TextView>(R.id.size_value)

        fun displayOpacity(value: Int) {
            opacityText.text = getString(R.string.opacity_label) + " · $value%"
        }
        fun displaySize(value: Int) {
            sizeText.text = getString(R.string.size_label) + " · ${value}dp"
        }

        opacitySeek.progress = SettingsStore.opacity(this) - 8
        sizeSeek.progress = SettingsStore.diameter(this) - 20
        displayOpacity(opacitySeek.progress + 8)
        displaySize(sizeSeek.progress + 20)

        opacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val opacity = progress + 8
                    displayOpacity(opacity)
                    SettingsStore.prefs(this@MainActivity).edit()
                        .putInt(SettingsStore.OPACITY, opacity).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val diameter = progress + 20
                    displaySize(diameter)
                    SettingsStore.prefs(this@MainActivity).edit()
                        .putInt(SettingsStore.DIAMETER, diameter).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            info.resolveInfo.serviceInfo.packageName == packageName &&
                info.resolveInfo.serviceInfo.name == ScreenshotAccessibilityService::class.java.name
        }
        status.setText(if (enabled) R.string.status_on else R.string.status_off)
        statusDot.setBackgroundResource(if (enabled) R.drawable.dot_enabled else R.drawable.dot_disabled)
        settingsButton.setText(if (enabled) R.string.manage_accessibility else R.string.open_accessibility)
    }
}
