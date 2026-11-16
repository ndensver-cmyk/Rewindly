package com.rewindly.app.analytics

import com.rewindly.app.importer.PlayNormalizer
import com.rewindly.app.importer.RawPlay
import com.rewindly.app.live.PlayTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatsEngineTest {
    private val zone = ZoneId.of("America/Bogota")
    private fun ts(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun play(track: String, artist: String, t: Long, ms: Long = 200_000, skipped: Boolean? = false) =
        Play(track, artist, "Album $artist", PlayNormalizer.trackKey(track, artist), t, ms, skipped = skipped)

    @Test fun totalsTopsAndSessions() {
        val plays = listOf(
            play("A", "Uno", ts(2, 22, 0)), play("A", "Uno", ts(2, 22, 4)), play("A", "Uno", ts(2, 22, 8)),
            play("B", "Dos", ts(3, 9, 0)), play("C", "Uno", ts(4, 23, 30), ms = 10_000, skipped = true)
        )
        val start = ts(1, 0); val end = ts(8, 0)
        val s = StatsEngine.compute(plays, emptyList(), emptyMap(), emptyMap(), plays, start, end, zone, 30)
        assertEquals(5, s.plays)
        assertEquals(810_000L, s.totalMs)
        assertEquals("A", s.topTracks.first().name)
        assertEquals("Uno", s.topArtists.first().name)
        assertEquals(3, s.sessions.count)
        assertEquals(3, s.longestStreakDays)
        assertEquals(22, s.favoriteHour)
        assertEquals(1, s.skippedPlays)
        assertEquals(2, s.newArtistsCount)
        assertNotNull(s.maxConsecutiveRepeats)
        assertTrue(s.nightPct > 50)
    }

    @Test fun nostalgiaDetectsReturns() {
        val p = listOf(play("Vieja", "Banda", ts(5, 12)))
        val last = mapOf(PlayNormalizer.trackKey("Vieja", "Banda") to ts(5, 12) - 200L * 86_400_000)
        val s = StatsEngine.compute(p, emptyList(), last, mapOf("banda" to 0L), p, ts(1, 0), ts(8, 0), zone)
        assertEquals(1, s.returnedTracks.size)
        assertEquals(0, s.newTracksCount)
    }

    @Test fun fingerprintMatchesAccountAndExtended() {
        val ext = RawPlay("Canción", "Artísta", "X", null, PlayNormalizer.parseIso("2024-03-01T18:22:31Z")!!, 187_345)
        val acc = RawPlay("cancion", "artista", null, null, PlayNormalizer.parseAccount("2024-03-01 18:22")!!, 187_345)
        assertEquals(PlayNormalizer.fingerprint(ext), PlayNormalizer.fingerprint(acc))
    }

    @Test fun shannonBounds() {
        assertEquals(0.0, StatsEngine.shannon(listOf(10.0)), 0.0001)
        assertEquals(1.0, StatsEngine.shannon(listOf(5.0, 5.0, 5.0)), 0.0001)
    }

    @Test fun trackerMeasuresListening() {
        var now = 0L
        val out = mutableListOf<RawPlay>()
        val t = PlayTracker({ now }, { 1_000_000L + now }, { out += it })
        t.onMetadata(PlayTracker.Track("Uno", "Artista", "Álbum", 180_000))
        t.onPlaybackState(true)
        now += 60_000
        t.onPlaybackState(false)
        now += 30_000            // en pausa: no cuenta
        t.onPlaybackState(true)
        now += 120_000
        t.onMetadata(PlayTracker.Track("Dos", "Artista", null, 200_000))
        now += 10_000
        t.onMetadata(PlayTracker.Track("Tres", "Artista", null, 200_000)) // saltada a los 10 s
        t.flush()
        assertEquals(2, out.size)
        assertEquals(180_000L, out[0].msPlayed)
        assertEquals("trackdone", out[0].reasonEnd)
        assertFalse(out[0].skipped!!)
        assertTrue(out[1].skipped!!)
    }

    @Test fun trackerIgnoresAds() {
        var now = 0L
        val out = mutableListOf<RawPlay>()
        val t = PlayTracker({ now }, { now }, { out += it })
        t.onMetadata(PlayTracker.Track("Advertisement", "Spotify", null, 30_000))
        t.onPlaybackState(true); now += 30_000; t.flush()
        assertTrue(out.isEmpty())
    }
}
