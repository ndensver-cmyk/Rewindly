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
        get() = prefs.getBoolean("lastfm_auto", true)
        set(v) = prefs.edit().putBoolean("lastfm_auto", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) = prefs.edit().putBoolean("onboarding_done", v).apply()

    /** "dark", "light" o "system". Ambos temas son en blanco y negro. */
    var themeMode: String
        get() = prefs.getString("theme_mode", "dark") ?: "dark"
        set(v) = prefs.edit().putString("theme_mode", v).apply()

    // ---------- Automatización ----------
    /** Buscar ZIP/JSON de Spotify en Descargas (requiere acceso a archivos). */
    var scanDownloads: Boolean
        get() = prefs.getBoolean("scan_downloads", false)
        set(v) = prefs.edit().putBoolean("scan_downloads", v).apply()

    /** Carpeta elegida por el usuario (Storage Access Framework) para vigilar. */
    var watchedTreeUri: String?
        get() = prefs.getString("watched_tree", null)
        set(v) = prefs.edit().putString("watched_tree", v).apply()

    var processedFiles: Set<String>
        get() = prefs.getStringSet("processed_files", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("processed_files", v).apply()

    var lastAutoScanAt: Long
        get() = prefs.getLong("last_auto_scan", 0L)
        set(v) = prefs.edit().putLong("last_auto_scan", v).apply()

    var lastFmSyncedAt: Long
        get() = prefs.getLong("lastfm_synced_at", 0L)
        set(v) = prefs.edit().putLong("lastfm_synced_at", v).apply()

    /** Fecha en que el usuario pidió sus datos a Spotify (0 = no registrada). */
    var spotifyRequestedAt: Long
        get() = prefs.getLong("spotify_requested_at", 0L)
        set(v) = prefs.edit().putLong("spotify_requested_at", v).apply()

    var requestNotifiedStage: Int
        get() = prefs.getInt("request_notified_stage", 0)
        set(v) = prefs.edit().putInt("request_notified_stage", v).apply()

    var monthlyAutoReport: Boolean
        get() = prefs.getBoolean("monthly_auto", true)
        set(v) = prefs.edit().putBoolean("monthly_auto", v).apply()

    fun zone(): ZoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
}
