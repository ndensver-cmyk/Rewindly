package com.rewindly.app.data.repository

import android.content.Context
import java.time.ZoneId

/** Preferencias simples guardadas en almacenamiento privado. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("rewindly_settings", Context.MODE_PRIVATE)

    var zoneId: String
        get() = prefs.getString("zone", ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id
        set(v) = prefs.edit().putString("zone", v).apply()

    var sessionGapMinutes: Int
        get() = prefs.getInt("session_gap", 30)
        set(v) = prefs.edit().putInt("session_gap", v).apply()

    var weeklyAutoReport: Boolean
        get() = prefs.getBoolean("weekly_auto", true)
        set(v) = prefs.edit().putBoolean("weekly_auto", v).apply()

    var exportReminder: Boolean
        get() = prefs.getBoolean("export_reminder", true)
        set(v) = prefs.edit().putBoolean("export_reminder", v).apply()

    var liveTrackingEnabled: Boolean
        get() = prefs.getBoolean("live_enabled", true)
        set(v) = prefs.edit().putBoolean("live_enabled", v).apply()

    var lastFmUser: String
        get() = prefs.getString("lastfm_user", "") ?: ""
        set(v) = prefs.edit().putString("lastfm_user", v.trim()).apply()

    var lastFmApiKey: String
        get() = prefs.getString("lastfm_key", "") ?: ""
        set(v) = prefs.edit().putString("lastfm_key", v.trim()).apply()

    var lastFmLastSyncUts: Long
        get() = prefs.getLong("lastfm_last_uts", 0L)
        set(v) = prefs.edit().putLong("lastfm_last_uts", v).apply()

    var lastFmAutoSync: Boolean
        get() = prefs.getBoolean("lastfm_auto", false)
        set(v) = prefs.edit().putBoolean("lastfm_auto", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) = prefs.edit().putBoolean("onboarding_done", v).apply()

    fun zone(): ZoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
}
