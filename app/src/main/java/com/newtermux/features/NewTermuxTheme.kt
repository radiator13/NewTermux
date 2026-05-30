package com.newtermux.features

import android.content.Context

/**
 * Manages the user-chosen accent color for NewTermux UI elements.
 */
object NewTermuxTheme {

    private const val PREFS_NAME = "newtermux_theme"
    private const val KEY_ACCENT_COLOR = "accent_color"
    const val DEFAULT_COLOR: Int = 0xFFBB86FC.toInt() // Purple

    @JvmField
    val COLORS = intArrayOf(
        0xFFBB86FC.toInt(), // Purple
        0xFF2196F3.toInt(), // Blue
        0xFF4CAF50.toInt(), // Green
        0xFFFF9800.toInt(), // Orange
        0xFFF44336.toInt(), // Red
        0xFF00BCD4.toInt(), // Teal
        0xFFE91E63.toInt(), // Pink
        0xFFFFC107.toInt(), // Gold
        0xFFFFFFFF.toInt(), // White
    )

    @JvmField
    val COLOR_NAMES = arrayOf(
        "Purple", "Blue", "Green", "Orange", "Red", "Teal", "Pink", "Gold", "White"
    )

    @JvmStatic
    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCENT_COLOR, DEFAULT_COLOR)
    }

    @JvmStatic
    fun setAccentColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACCENT_COLOR, color).apply()
    }

    @JvmStatic
    fun getColorName(color: Int): String {
        for (i in COLORS.indices) {
            if (COLORS[i] == color) return COLOR_NAMES[i]
        }
        return String.format("#%06X", color and 0xFFFFFF)
    }

    /** Returns true if the current accent_color doesn't match any of the 9 presets. */
    @JvmStatic
    fun isCustomAccentActive(context: Context): Boolean {
        val cur = getAccentColor(context)
        for (c in COLORS) if (c == cur) return false
        return true
    }
}
