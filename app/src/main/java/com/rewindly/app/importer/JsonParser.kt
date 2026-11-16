package com.rewindly.app.importer

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Lector en streaming: no carga el JSON completo en memoria.
 * Reconoce el formato extendido (ts, master_metadata_*) y el de Account data (endTime, trackName...).
 * Los campos sensibles (ip_addr, username, user_agent, conn_country) se leen y se descartan sin guardarse.
 */
class JsonParser {

    data class Result(var read: Int = 0, var ignored: Int = 0, var invalid: Int = 0, val warnings: MutableSet<String> = mutableSetOf())

    fun parse(input: InputStream, onPlay: (RawPlay) -> Unit): Result {
        val result = Result()
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = true
        try {
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> readArray(reader, result, onPlay)
                JsonToken.BEGIN_OBJECT -> {
                    // Algunos formatos envuelven el arreglo en un objeto
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName()
                        if (reader.peek() == JsonToken.BEGIN_ARRAY) readArray(reader, result, onPlay) else reader.skipValue()
                    }
                    reader.endObject()
                }
                else -> result.warnings += "Un archivo JSON no tiene el formato esperado."
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            result.warnings += "Un archivo JSON está incompleto o dañado; se importó lo que se pudo leer."
        }
        return result
    }

    private fun readArray(reader: JsonReader, result: Result, onPlay: (RawPlay) -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); result.invalid++; continue }
            val play = readObject(reader, result)
            if (play != null) {
                result.read++
                if (result.read > ZipLimits.MAX_PLAYS) throw ImportException("Demasiadas reproducciones en un solo archivo.")
                onPlay(play)
            }
        }
        reader.endArray()
    }

    private fun readObject(reader: JsonReader, result: Result): RawPlay? {
        var ts: Long? = null
        var ms: Long? = null
        var track: String? = null
        var artist: String? = null
        var album: String? = null
        var uri: String? = null
        var platform: String? = null
        var reasonStart: String? = null
        var reasonEnd: String? = null
        var shuffle: Boolean? = null
        var skipped: Boolean? = null
        var offline: Boolean? = null
        var incognito: Boolean? = null
        var isEpisode = false

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); continue }
            when (name) {
                "ts" -> ts = PlayNormalizer.parseIso(reader.nextString())
                "endTime" -> ts = PlayNormalizer.parseAccount(reader.nextString())
                "ms_played", "msPlayed" -> ms = readLong(reader)
                "master_metadata_track_name", "trackName" -> track = reader.nextString()
                "master_metadata_album_artist_name", "artistName" -> artist = reader.nextString()
                "master_metadata_album_album_name", "albumName" -> album = reader.nextString()
                "spotify_track_uri" -> uri = reader.nextString()
                "platform" -> platform = simplifyPlatform(reader.nextString())
                "reason_start" -> reasonStart = reader.nextString()
                "reason_end" -> reasonEnd = reader.nextString()
                "shuffle" -> shuffle = readBool(reader)
                "skipped" -> skipped = readBool(reader)
                "offline" -> offline = readBool(reader)
                "incognito_mode" -> incognito = readBool(reader)
                "episode_name", "spotify_episode_uri", "episode_show_name", "audiobook_title" -> {
                    reader.skipValue(); isEpisode = true
                }
                // ip_addr, username, user_agent_decrypted, conn_country, offline_timestamp, etc.
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (track.isNullOrBlank() || artist.isNullOrBlank()) {
            if (isEpisode) result.ignored++ else result.invalid++
            return null
        }
        if (ts == null || ms == null) { result.invalid++; return null }
        if (ms < 0 || ms > 24L * 3600_000) { result.invalid++; return null }
        if (skipped == null && reasonEnd == null && platform == null) result.warnings += "Algunos archivos no incluyen saltos ni motivos de reproducción (formato básico)."
        return RawPlay(
            trackName = track, artistName = artist, albumName = album, trackUri = uri,
            timestampUtc = ts, msPlayed = ms, platform = platform,
            reasonStart = reasonStart, reasonEnd = reasonEnd, shuffle = shuffle,
            skipped = skipped, offline = offline, privateSession = incognito
        )
    }

    private fun readLong(reader: JsonReader): Long? = when (reader.peek()) {
        JsonToken.NUMBER -> reader.nextLong()
        JsonToken.STRING -> reader.nextString().toLongOrNull()
        else -> { reader.skipValue(); null }
    }

    private fun readBool(reader: JsonReader): Boolean? = when (reader.peek()) {
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.STRING -> reader.nextString().toBooleanStrictOrNull()
        JsonToken.NUMBER -> reader.nextInt() != 0
        else -> { reader.skipValue(); null }
    }

    /** Solo guardamos el tipo de plataforma, no el agente completo del dispositivo. */
    private fun simplifyPlatform(raw: String): String {
        val p = raw.lowercase()
        return when {
            "android" in p -> "Android"
            "ios" in p || "iphone" in p || "ipad" in p -> "iOS"
            "windows" in p -> "Windows"
            "os x" in p || "osx" in p || "mac" in p -> "macOS"
            "linux" in p -> "Linux"
            "web" in p || "browser" in p -> "Web"
            "cast" in p -> "Chromecast"
            "tv" in p -> "TV"
            "car" in p || "auto" in p -> "Auto"
            "speaker" in p || "sonos" in p || "echo" in p -> "Altavoz"
            "playstation" in p || "xbox" in p -> "Consola"
            else -> "Otro"
        }
    }
}
