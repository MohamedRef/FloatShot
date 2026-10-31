package com.mohamedref.floatshot

import android.content.Context
import android.content.SharedPreferences

internal object SettingsStore {
    const val FILE = "floatshot_settings"
    const val OPACITY = "opacity_percent"
    const val DIAMETER = "diameter_dp"
    const val SIDE = "bubble_side" // 0 = left, 1 = right
    const val VERTICAL = "vertical_fraction"
    const val ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
    const val AUTO_CHECK_UPDATES = "auto_check_updates"
    const val LAST_AUTO_UPDATE_CHECK = "last_auto_update_check"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun opacity(context: Context): Int = prefs(context).getInt(OPACITY, 18).coerceIn(8, 70)
    fun diameter(context: Context): Int = prefs(context).getInt(DIAMETER, 26).coerceIn(20, 44)
    fun autoUpdateChecks(context: Context): Boolean =
        prefs(context).getBoolean(AUTO_CHECK_UPDATES, true)
}
