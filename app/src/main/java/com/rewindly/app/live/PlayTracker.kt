package com.rewindly.app.live

import com.rewindly.app.importer.RawPlay

/**
 * Convierte los cambios de estado del reproductor (metadatos + play/pausa)
 * en reproducciones con tiempo realmente escuchado. Lógica pura, sin Android.
 */
class PlayTracker(
    private val clock: () -> Long,       // reloj monotónico (ms)
    private val wallClock: () -> Long,   // hora real UTC (ms)
    private val onCommit: (RawPlay) -> Unit
) {
    data class Track(val title: String, val artist: String, val album: String?, val durationMs: Long, val uri: String? = null)

    private var current: Track? = null
    private var playingSince: Long? = null
    private var accumulated = 0L
    private var startedManually: Boolean? = null

    val nowPlaying: Track? get() = current

    fun onMetadata(track: Track?) {
        if (track != null && current != null && sameTrack(track, current!!)) {
            // Mismo tema: solo completamos datos que faltaban (álbum, URI)
            current = current!!.copy(
                album = current!!.album ?: track.album,
                uri = current!!.uri ?: track.uri,
                durationMs = if (current!!.durationMs <= 0) track.durationMs else current!!.durationMs
            )
            return
        }
        val wasPlaying = playingSince != null
        commit(naturalCandidate = true)
        current = track?.takeIf { isMusic(it) }
        accumulated = 0
        playingSince = if (wasPlaying && current != null) clock() else null
    }

    fun onPlaybackState(playing: Boolean) {
        val now = clock()
        if (playing) {
            if (playingSince == null && current != null) playingSince = now
        } else {
            playingSince?.let { accumulated += now - it }
            playingSince = null
        }
    }

    /** Llamar cuando el reproductor desaparece o el servicio se detiene. */
    fun flush() {
        commit(naturalCandidate = false)
        current = null
        accumulated = 0
        playingSince = null
    }

    private fun commit(naturalCandidate: Boolean) {
        val t = current ?: return
        playingSince?.let { accumulated += clock() - it; playingSince = clock() }
        val played = accumulated
        if (played < MIN_COMMIT_MS) return
        val finished = t.durationMs > 0 && played >= t.durationMs - END_TOLERANCE_MS
        onCommit(
            RawPlay(
                trackName = t.title,
                artistName = t.artist,
                albumName = t.album,
                trackUri = t.uri,
                timestampUtc = wallClock(),
                msPlayed = if (t.durationMs > 0) played.coerceAtMost(t.durationMs + 5_000) else played,
                platform = "Android",
                reasonStart = startedManually?.let { if (it) "clickrow" else "trackdone" },
                reasonEnd = if (finished && naturalCandidate) "trackdone" else if (naturalCandidate) "fwdbtn" else "endplay",
                skipped = !finished && played < SKIP_THRESHOLD_MS,
                shuffle = null,
                offline = null,
                privateSession = null
            )
        )
        startedManually = finished.not()
        accumulated = 0
    }

    private fun sameTrack(a: Track, b: Track) =
        a.title.equals(b.title, true) && a.artist.equals(b.artist, true)

    private fun isMusic(t: Track): Boolean {
        if (t.title.isBlank() || t.artist.isBlank()) return false
        val title = t.title.lowercase()
        val artist = t.artist.lowercase()
        if (artist == "spotify" || title == "advertisement" || title == "anuncio" || title.startsWith("spotify ad")) return false
        if (t.durationMs in 1..15_000) return false
        return true
    }

    companion object {
        const val MIN_COMMIT_MS = 5_000L
        const val SKIP_THRESHOLD_MS = 30_000L
        const val END_TOLERANCE_MS = 6_000L
    }
}
