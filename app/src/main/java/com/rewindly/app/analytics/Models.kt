package com.rewindly.app.analytics

import kotlinx.serialization.Serializable

/** Reproducción mínima usada por el motor estadístico (sin dependencias de Android). */
data class Play(
    val track: String,
    val artist: String,
    val album: String?,
    val trackKey: String,
    val ts: Long,
    val ms: Long,
    val skipped: Boolean? = null,
    val shuffle: Boolean? = null,
    val offline: Boolean? = null,
    val reasonStart: String? = null,
    val reasonEnd: String? = null,
    val platform: String? = null,
    val estimated: Boolean = false
)

@Serializable
data class RankedItem(
    val name: String,
    val subtitle: String? = null,
    val plays: Int = 0,
    val ms: Long = 0,
    val extra: String? = null
)

@Serializable
data class NamedValue(val label: String, val value: Long)

@Serializable
data class Comparison(
    val prevTotalMs: Long,
    val prevPlays: Int,
    val prevArtists: Int,
    val totalDeltaPct: Double?,
    val playsDeltaPct: Double?,
    val artistsDelta: Int
)

@Serializable
data class Personality(
    val key: String,
    val title: String,
    val description: String,
    val reason: String,
    val colorIndex: Int
)

@Serializable
data class SessionInfo(
    val count: Int,
    val avgMs: Long,
    val longestMs: Long,
    val longestStart: Long?,
    val avgTracks: Double,
    val maxTracks: Int
)

@Serializable
data class StatsResult(
    val periodStart: Long,
    val periodEnd: Long,
    val zoneId: String,
    // A. Tiempo
    val totalMs: Long,
    val plays: Int,
    val periodDays: Int,
    val activeDays: Int,
    val dailyAvgMs: Long,
    val weeklyAvgMs: Long,
    val bestDay: NamedValue?,
    val worstDay: NamedValue?,
    val bestWeek: NamedValue?,
    val bestMonth: NamedValue?,
    val favoriteHour: Int?,
    val hourDistribution: List<Long>,
    val weekdayDistribution: List<Long>,
    val dayparts: List<NamedValue>,
    val longestStreakDays: Int,
    val longestStreakStart: Long?,
    val yearlyTotals: List<NamedValue>,
    val monthlyTotals: List<NamedValue>,
    val dailyTotals: List<NamedValue>,
    // B. Canciones
    val distinctTracks: Int,
    val topTracks: List<RankedItem>,
    val topTracksByTime: List<RankedItem>,
    val topTrackSingleDay: RankedItem?,
    val topTrackSingleWeek: RankedItem?,
    val newTracksCount: Int,
    val newTracks: List<RankedItem>,
    val returnedTracks: List<RankedItem>,
    val disappearedTracks: List<RankedItem>,
    val mostSkippedTracks: List<RankedItem>,
    val oneTimeTracks: Int,
    val knownPlaysPct: Double,
    val maxConsecutiveRepeats: RankedItem?,
    // C. Artistas
    val distinctArtists: Int,
    val topArtists: List<RankedItem>,
    val growthArtist: RankedItem?,
    val lostArtist: RankedItem?,
    val newArtistsCount: Int,
    val newArtists: List<RankedItem>,
    val top5ConcentrationPct: Double,
    val weeklyTopArtists: List<RankedItem>,
    val stableArtists: List<String>,
    // D. Álbumes
    val distinctAlbums: Int,
    val topAlbums: List<RankedItem>,
    val topAlbumsByTime: List<RankedItem>,
    val newAlbumsCount: Int,
    val albumsTop5Pct: Double,
    val deepListenedAlbums: Int,
    // E. Comportamiento
    val skippedPlays: Int?,
    val skipRatePct: Double?,
    val avgMsPerPlay: Long,
    val sessions: SessionInfo,
    val shufflePct: Double?,
    val offlinePct: Double?,
    val manualStartPct: Double?,
    val naturalEndPct: Double?,
    val platforms: List<NamedValue>,
    // F. Análisis inteligentes
    val diversityIndex: Double,
    val discoveryRatePct: Double,
    val nostalgiaPct: Double,
    val obsessionPct: Double,
    val stabilityPct: Double,
    val routineScore: Double,
    val nightPct: Double,
    val seasonal: List<NamedValue>,
    val comparison: Comparison?,
    val personality: Personality,
    // Calidad de datos
    val estimatedPlaysPct: Double,
    val hasSkipData: Boolean,
    val hasShuffleData: Boolean,
    val hasReasonData: Boolean,
    val hasAlbumData: Boolean
) {
    val isEmpty: Boolean get() = plays == 0
}
