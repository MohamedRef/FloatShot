package com.mohamedref.floatshot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.Executors
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var settingsButton: Button
    private lateinit var updateStatus: TextView
    private lateinit var updateCheckButton: Button
    private lateinit var updateOpenButton: Button
    private lateinit var updateToggle: Switch
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private var updateInProgress = false

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

        updateStatus = findViewById(R.id.update_status)
        updateCheckButton = findViewById(R.id.check_updates_button)
        updateOpenButton = findViewById(R.id.open_update_button)
        updateToggle = findViewById(R.id.auto_update_switch)
        val currentVersion = installedVersion()
        findViewById<TextView>(R.id.update_installed_version).text =
            getString(R.string.updates_installed_version, currentVersion)
        updateToggle.isChecked = SettingsStore.autoUpdateChecks(this)
        updateToggle.setOnCheckedChangeListener { _, enabled ->
            SettingsStore.prefs(this).edit()
                .putBoolean(SettingsStore.AUTO_CHECK_UPDATES, enabled).apply()
            if (enabled) {
                SettingsStore.prefs(this).edit().remove(SettingsStore.LAST_AUTO_UPDATE_CHECK).apply()
                autoCheckUpdatesIfDue()
            }
        }
        updateCheckButton.setOnClickListener { checkForUpdates(automatic = false) }
        updateOpenButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASE_PAGE)))
            } catch (_: ActivityNotFoundException) {
                updateStatus.setText(R.string.updates_no_browser)
            }
        }
    }

    private fun installedVersion(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"

    private fun autoCheckUpdatesIfDue() {
        if (!SettingsStore.autoUpdateChecks(this) || updateInProgress) return
        val now = System.currentTimeMillis()
        val last = SettingsStore.prefs(this).getLong(SettingsStore.LAST_AUTO_UPDATE_CHECK, 0L)
        if (last > 0L && now >= last && now - last < 24L * 60L * 60L * 1000L) return
        checkForUpdates(automatic = true)
    }

    private fun checkForUpdates(automatic: Boolean) {
        if (updateInProgress) return
        updateInProgress = true
        if (automatic) {
            SettingsStore.prefs(this).edit()
                .putLong(SettingsStore.LAST_AUTO_UPDATE_CHECK, System.currentTimeMillis()).apply()
        }
        updateStatus.setText(R.string.updates_checking)
        updateCheckButton.isEnabled = false
        updateOpenButton.visibility = View.GONE
        val installed = installedVersion()
        updateExecutor.execute {
            val result = UpdateChecker.check(installed)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateInProgress = false
                updateCheckButton.isEnabled = true
                if (!automatic && result !is UpdateResult.Failed) {
                    SettingsStore.prefs(this).edit()
                        .putLong(SettingsStore.LAST_AUTO_UPDATE_CHECK, System.currentTimeMillis()).apply()
                }
                when (result) {
                    is UpdateResult.Available -> {
                        updateStatus.text = getString(R.string.updates_available, result.version)
                        updateOpenButton.visibility = View.VISIBLE
                    }
                    UpdateResult.UpToDate ->
                        updateStatus.text = getString(R.string.updates_up_to_date, installed)
                    UpdateResult.NoPublishedRelease ->
                        updateStatus.setText(R.string.updates_no_releases)
                    UpdateResult.InvalidVersion ->
                        updateStatus.setText(R.string.updates_invalid_version)
                    UpdateResult.Failed ->
                        updateStatus.setText(R.string.updates_failed)
                }
            }
        }
    }

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        super.onDestroy()
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
        autoCheckUpdatesIfDue()
    }
}
