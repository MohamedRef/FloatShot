package com.mohamedref.floatshot

import android.content.Context
import android.content.SharedPreferences

internal object SettingsStore {
    const val FILE = "floatshot_settings"
    const val OPACITY = "opacity_percent"
    const val DIAMETER = "diameter_dp"
    const val SIDE = "bubble_side" // 0 = left, 1 = right
    const val VERTICAL = "vertical_fraction"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun opacity(context: Context): Int = prefs(context).getInt(OPACITY, 18).coerceIn(8, 70)
    fun diameter(context: Context): Int = prefs(context).getInt(DIAMETER, 26).coerceIn(20, 44)
}
