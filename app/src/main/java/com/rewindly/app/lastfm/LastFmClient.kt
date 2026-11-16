package com.rewindly.app.lastfm

import com.rewindly.app.data.entities.Sources
import com.rewindly.app.data.repository.RewindlyRepository
import com.rewindly.app.importer.RawPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LastFmException(message: String) : Exception(message)

/**
 * Lee el historial público de scrobbles de Last.fm (user.getRecentTracks).
 * Solo necesita el nombre de usuario de Last.fm y una API key gratuita del propio usuario:
 * Rewindly nunca ve ni guarda credenciales de Spotify.
 */
class LastFmClient(private val repo: RewindlyRepository) {

    data class SyncResult(val pages: Int, val read: Int, val inserted: Int, val duplicates: Int, val newestUts: Long)

    suspend fun sync(onProgress: (page: Int, total: Int) -> Unit = { _, _ -> }): SyncResult = withContext(Dispatchers.IO) {
        val s = repo.settings
        val user = s.lastFmUser
        val key = s.lastFmApiKey
        if (user.isBlank() || key.isBlank()) throw LastFmException("Configura tu usuario y tu API key de Last.fm.")
        val from = s.lastFmLastSyncUts
        var page = 1
        var totalPages = 1
        var read = 0; var inserted = 0; var dup = 0
        var newest = from
        val batchId = repo.newBatch(Sources.LASTFM, "last.fm/$user")

        while (page <= totalPages && page <= MAX_PAGES) {
            val obj = request(user, key, page, if (from > 0) from + 1 else null)
            val recent = obj.optJSONObject("recenttracks") ?: throw LastFmException("Respuesta inesperada de Last.fm.")
            totalPages = recent.optJSONObject("@attr")?.optString("totalPages")?.toIntOrNull() ?: 1
            val tracks = when (val t = recent.opt("track")) {
                is JSONArray -> t
                is JSONObject -> JSONArray().put(t)
                else -> JSONArray()
            }
            val plays = ArrayList<RawPlay>(tracks.length())
            for (i in 0 until tracks.length()) {
                val t = tracks.getJSONObject(i)
                if (t.optJSONObject("@attr")?.optString("nowplaying") == "true") continue
                val uts = t.optJSONObject("date")?.optString("uts")?.toLongOrNull() ?: continue
                val name = t.optString("name").ifBlank { null } ?: continue
                val artist = t.optJSONObject("artist")?.let { it.optString("#text").ifBlank { it.optString("name") } }?.ifBlank { null } ?: continue
                val album = t.optJSONObject("album")?.optString("#text")?.ifBlank { null }
                newest = maxOf(newest, uts)
                // Last.fm registra el inicio; estimamos el final con una duración media
                plays += RawPlay(
                    trackName = name, artistName = artist, albumName = album, trackUri = null,
                    timestampUtc = uts * 1000 + ESTIMATED_MS, msPlayed = ESTIMATED_MS, msEstimated = true
                )
            }
            read += plays.size
            val r = repo.insertPlays(plays, Sources.LASTFM, batchId)
            inserted += r.inserted; dup += r.duplicates
            onProgress(page, totalPages)
            page++
            delay(250) // respetar el límite de peticiones de Last.fm
        }
        if (newest > from) s.lastFmLastSyncUts = newest
        repo.finishBatch(
            com.rewindly.app.data.entities.ImportBatchEntity(
                id = batchId, source = Sources.LASTFM, fileName = "last.fm/$user",
                createdAt = System.currentTimeMillis(), inserted = inserted, duplicates = dup,
                warnings = "Last.fm no informa cuánto escuchaste cada canción: el tiempo se estima en 3:30 por reproducción."
            )
        )
        SyncResult(page - 1, read, inserted, dup, newest)
    }

    private fun request(user: String, key: String, page: Int, from: Long?): JSONObject {
        val q = buildString {
            append("method=user.getrecenttracks&format=json&limit=200")
            append("&user=").append(URLEncoder.encode(user, "UTF-8"))
            append("&api_key=").append(URLEncoder.encode(key, "UTF-8"))
            append("&page=").append(page)
            if (from != null) append("&from=").append(from)
        }
        var attempt = 0
        while (true) {
            attempt++
            val conn = (URL("https://ws.audioscrobbler.com/2.0/?$q").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "Rewindly/1.0 (Android)")
            }
            try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (json != null && json.has("error")) {
                    val msg = when (json.optInt("error")) {
                        6 -> "No existe ese usuario de Last.fm."
                        10, 26 -> "La API key de Last.fm no es válida."
                        17 -> "El perfil de Last.fm es privado. Activa el historial público en la configuración de privacidad de Last.fm."
                        29 -> "Last.fm limitó las peticiones. Intenta de nuevo en unos minutos."
                        else -> json.optString("message", "Error de Last.fm")
                    }
                    if (json.optInt("error") in setOf(8, 11, 16) && attempt < 3) { Thread.sleep(1500L * attempt); continue }
                    throw LastFmException(msg)
                }
                if (json == null) {
                    if (attempt < 3) { Thread.sleep(1500L * attempt); continue }
                    throw LastFmException("Last.fm no respondió correctamente (HTTP $code).")
                }
                return json
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        const val ESTIMATED_MS = 210_000L
        const val MAX_PAGES = 2_500
    }
}
