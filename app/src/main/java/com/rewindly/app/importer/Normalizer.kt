package com.rewindly.app.importer

import com.rewindly.app.data.entities.PlaybackEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Reproducción ya leída de cualquier fuente, antes de guardarse. */
data class RawPlay(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val trackUri: String?,
    val timestampUtc: Long,
    val msPlayed: Long,
    val platform: String? = null,
    val reasonStart: String? = null,
    val reasonEnd: String? = null,
    val shuffle: Boolean? = null,
    val skipped: Boolean? = null,
    val offline: Boolean? = null,
    val privateSession: Boolean? = null,
    val msEstimated: Boolean = false
)

object PlayNormalizer {
    private val accountFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun normalizeText(s: String): String = com.rewindly.app.analytics.StatsEngine.normalize(s)

    fun trackKey(track: String, artist: String) = normalizeText(track) + "|" + normalizeText(artist)

    /**
     * Huella para detectar duplicados. Usa minuto + segundos escuchados para que
     * "Account data" (precisión de minutos) y "Extended" (segundos) coincidan.
     */
    fun fingerprint(p: RawPlay): String {
        val minute = p.timestampUtc / 60_000L
        val secs = p.msPlayed / 1000L
        return trackKey(p.trackName, p.artistName) + "|" + minute + "|" + secs
    }

    /** "2024-03-01T18:22:31Z" o variantes ISO. */
    fun parseIso(ts: String): Long? = runCatching { Instant.parse(ts).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(ts.removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

    /** "2024-03-01 18:22" (formato de Account data, en UTC). */
    fun parseAccount(ts: String): Long? = runCatching {
        LocalDateTime.parse(ts.trim(), accountFormat).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull() ?: parseIso(ts)

    fun toEntity(p: RawPlay, source: String, batchId: Long) = PlaybackEntity(
        fingerprint = fingerprint(p),
        trackKey = trackKey(p.trackName, p.artistName),
        trackName = p.trackName.trim(),
        artistName = p.artistName.trim(),
        albumName = p.albumName?.trim()?.ifBlank { null },
        trackUri = p.trackUri,
        timestampUtc = p.timestampUtc,
        msPlayed = p.msPlayed.coerceAtLeast(0),
        platform = p.platform,
        reasonStart = p.reasonStart,
        reasonEnd = p.reasonEnd,
        shuffle = p.shuffle,
        skipped = p.skipped,
        offline = p.offline,
        privateSession = p.privateSession,
        source = source,
        msEstimated = p.msEstimated,
        batchId = batchId
    )
}
