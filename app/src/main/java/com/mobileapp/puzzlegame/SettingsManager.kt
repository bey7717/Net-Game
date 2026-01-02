package com.mobileapp.puzzlegame

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

object SettingsManager {
    private const val PREFS_NAME = "net_game_settings"
    private const val KEY_GAME_WIDTH = "game_width"
    private const val KEY_GAME_HEIGHT = "game_height"
    private const val KEY_WRAPPING = "wrapping"
    private const val KEY_UNIQUE = "unique"
    private const val KEY_CURVE_GAP = "curve_gap"
    private const val KEY_THICK = "thick"
    private const val KEY_THICK_FILL = "thick_fill"
    private const val KEY_COLOR_INDEX = "color_index"
    private const val KEY_SAVED_GAME_STRING = "saved_game_string"

    val COLORS = listOf(
        "#0080FF", // Blue (default)
        "#FF0000", // Red
        "#00FF00", // Green
        "#FFFF00", // Yellow
        "#FF00FF", // Magenta
        "#00FFFF", // Cyan
        "#FF8000", // Orange
        "#8000FF", // Purple
        "#FF0080"  // Pink
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGameWidth(context: Context): Int {
        return getPrefs(context).getInt(KEY_GAME_WIDTH, DEFAULT_WIDTH)
    }

    fun setGameWidth(context: Context, width: Int) {
        getPrefs(context).edit { putInt(KEY_GAME_WIDTH, width) }
    }

    fun getGameHeight(context: Context): Int {
        return getPrefs(context).getInt(KEY_GAME_HEIGHT, DEFAULT_HEIGHT)
    }

    fun setGameHeight(context: Context, height: Int) {
        getPrefs(context).edit { putInt(KEY_GAME_HEIGHT, height) }
    }

    fun isWrapping(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WRAPPING, false)
    }

    fun setWrapping(context: Context, wrapping: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_WRAPPING, wrapping) }
    }

    fun isUnique(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_UNIQUE, true)
    }

    fun setUnique(context: Context, unique: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_UNIQUE, unique) }
    }

    fun getCurveGap(context: Context): Float {
        return getPrefs(context).getFloat(KEY_CURVE_GAP, 0.1f)
    }

    fun setCurveGap(context: Context, gap: Float) {
        getPrefs(context).edit { putFloat(KEY_CURVE_GAP, gap) }
    }

    fun getThick(context: Context): Float {
        return getPrefs(context).getFloat(KEY_THICK, 0.1f)
    }

    fun setThick(context: Context, thick: Float) {
        getPrefs(context).edit { putFloat(KEY_THICK, thick) }
    }

    fun getThickFill(context: Context): Float {
        return getPrefs(context).getFloat(KEY_THICK_FILL, 0.7f)
    }

    fun setThickFill(context: Context, thickFill: Float) {
        getPrefs(context).edit { putFloat(KEY_THICK_FILL, thickFill) }
    }

    fun getColorIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_COLOR_INDEX, 0)
    }

    fun setColorIndex(context: Context, index: Int) {
        getPrefs(context).edit { putInt(KEY_COLOR_INDEX, index.coerceIn(0, COLORS.size - 1)) }
    }

    fun getColor(context: Context): Int {
        val index = getColorIndex(context)
        return COLORS[index].toColorInt()
    }

    fun setSavedGameString(context: Context, gameString: String) {
        getPrefs(context).edit {
            putString(KEY_SAVED_GAME_STRING, gameString)
        }
    }

    fun getSavedGameString(context: Context): String? {
        return getPrefs(context).getString(KEY_SAVED_GAME_STRING, null)
    }
}

