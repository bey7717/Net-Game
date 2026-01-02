package com.mobileapp.puzzlegame

import android.content.Context
import android.content.SharedPreferences

object GameHistoryManager {
    private const val PREFS_NAME = "net_game_history"
    private const val KEY_HISTORY_COUNT = "history_count"
    private const val KEY_HISTORY_PREFIX = "history_item_"
    private const val MAX_HISTORY_SIZE = 100

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveGame(context: Context, item: GameHistoryItem) {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_HISTORY_COUNT, 0)
        
        // Shift existing items down
        for (i in (MAX_HISTORY_SIZE - 1) downTo 1) {
            val existing = prefs.getString("$KEY_HISTORY_PREFIX${i - 1}", null)
            if (existing != null) {
                prefs.edit().putString("$KEY_HISTORY_PREFIX$i", existing).apply()
            }
        }
        
        // Save new item at position 0
        val itemString = "${item.size}|${item.moves}|${item.timeSeconds}"
        val editor = prefs.edit()
        editor.putString("$KEY_HISTORY_PREFIX", itemString)
        editor.putInt(KEY_HISTORY_COUNT, (count + 1).coerceAtMost(MAX_HISTORY_SIZE))
        editor.apply()
    }

    fun getHistory(context: Context): List<GameHistoryItem> {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_HISTORY_COUNT, 0)
        val history = mutableListOf<GameHistoryItem>()
        
        for (i in 0 until count.coerceAtMost(MAX_HISTORY_SIZE)) {
            val itemString = prefs.getString("$KEY_HISTORY_PREFIX$i", null) ?: continue
            val parts = itemString.split("|")
            if (parts.size == 3) {
                try {
                    history.add(
                        GameHistoryItem(
                            size = parts[0],
                            moves = parts[1].toInt(),
                            timeSeconds = parts[2].toLong()
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid entries
                }
            }
        }
        
        return history
    }

    fun clearHistory(context: Context) {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_HISTORY_COUNT, 0)
        val editor = prefs.edit()
        for (i in 0 until count.coerceAtMost(MAX_HISTORY_SIZE)) {
            editor.remove("$KEY_HISTORY_PREFIX$i")
        }
        editor.remove(KEY_HISTORY_COUNT).apply()
    }
}

