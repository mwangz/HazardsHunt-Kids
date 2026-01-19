package com.example.`hazardshuntkids`

import android.content.Context

object ApiKeyManager {
    private const val PREFS_NAME = "hazard_prefs"
    private const val KEY_API = "api_key"

    fun save(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API, apiKey).apply()
    }

    fun get(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API, null)
    }

    fun hasKey(context: Context): Boolean {
        return get(context) != null
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_API).apply()
    }
}
