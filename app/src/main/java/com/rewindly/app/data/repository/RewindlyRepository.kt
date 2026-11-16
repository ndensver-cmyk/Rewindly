package com.rewindly.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.rewindly.app.analytics.Play
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.analytics.StatsResult
import com.rewindly.app.data.database.RewindlyDatabase
import com.rewindly.app.data.entities.ImportBatchEntity
import com.rewindly.app.data.entities.PlaybackEntity
import com.rewindly.app.data.entities.ReportEntity
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.importer.PlayNormalizer
import com.rewindly.app.importer.RawPlay
import kotlinx.serialization.json.Json

class RewindlyRepository private constructor(context: Context) {
    val db = RewindlyDatabase.get(context)
    val settings = Settings(context)
    private val playbackDao = db.playbackDao()
    private val batchDao = db.importBatchDao()
    private val reportDao = db.reportDao()

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val playCount = playbackDao.countFlow()
    val minTs = playbackDao.minTsFlow()
    val maxTs = playbackDao.maxTsFlow()
    val sourceCounts = playbackDao.countBySource()
    val batches = batchDao.all()
    val reports = reportDao.all()
    val recentLive = playbackDao.recentLive()

    data class InsertResult(var inserted: Int = 0, var duplicates: Int = 0, var replaced: Int = 0)

    suspend fun newBatch(source: String, fileName: String?): Long =
        batchDao.insert(ImportBatchEntity(source = source, fileName = fileName, createdAt = System.currentTimeMillis()))

    suspend fun finishBatch(batch: ImportBatchEntity) = batchDao.update(batch)

    /**
     * Inserta reproducciones evitando duplicados:
     * 1) misma huella → se ignora;
     * 2) misma canción con ±3 min de diferencia desde otra fuente → se conserva la fuente más precisa.
     */
    suspend fun insertPlays(plays: List<RawPlay>, source: String, batchId: Long): InsertResult {
        val res = InsertResult()
        if (plays.isEmpty()) return res
        val myPriority = Sources.priority(source)
        db.withTransaction {
            val toInsert = ArrayList<PlaybackEntity>(plays.size)
            for (p in plays) {
                val e = PlayNormalizer.toEntity(p, source, batchId)
                val near = playbackDao.near(e.trackKey, e.timestampUtc - WINDOW, e.timestampUtc + WINDOW)
                    .filter { it.source != source }
                if (near.isNotEmpty()) {
                    val stronger = near.any { Sources.priority(it.source) >= myPriority }
                    if (stronger) { res.duplicates++; continue }
                    playbackDao.deleteIds(near.map { it.id })
                    res.replaced += near.size
                }
                toInsert += e
            }
            val ids = playbackDao.insertAll(toInsert)
            ids.forEach { if (it == -1L) res.duplicates++ else res.inserted++ }
        }
        return res
    }

    suspend fun lastBatch(source: String) = batchDao.lastOf(source)

    /** Calcula estadísticas para un período usando el historial anterior como referencia. */
    suspend fun computeStats(start: Long, end: Long): StatsResult {
        val zone = settings.zone()
        val length = end - start
        val period = playbackDao.between(start, end).map { it.toPlay() }
        val previous = playbackDao.between(start - length, start).map { it.toPlay() }
        val lastSeen = playbackDao.trackLastSeenBefore(start).associate { it.key to it.lastTs }
        val artistFirst = playbackDao.artistFirstSeen()
            .groupBy { PlayNormalizer.normalizeText(it.artistName) }
            .mapValues { (_, l) -> l.minOf { it.firstTs } }
        val monthlyWindowStart = end - 365L * DAY
        val yearPlays = if (length < 360L * DAY) playbackDao.between(monthlyWindowStart, end).map { it.toPlay() } else period
        return StatsEngine.compute(
            period = period,
            previous = previous,
            trackLastSeenBefore = lastSeen,
            artistFirstSeen = artistFirst,
            yearContext = yearPlays,
            start = start,
            end = end,
            zone = zone,
            sessionGapMinutes = settings.sessionGapMinutes
        )
    }

    suspend fun saveReport(type: String, title: String, stats: StatsResult): Long =
        reportDao.insert(
            ReportEntity(
                type = type, title = title,
                periodStart = stats.periodStart, periodEnd = stats.periodEnd,
                createdAt = System.currentTimeMillis(),
                statsJson = json.encodeToString(StatsResult.serializer(), stats),
                personality = stats.personality.title
            )
        )

    suspend fun reportExists(type: String, start: Long, end: Long) = reportDao.exists(type, start, end) > 0

    suspend fun loadReport(id: Long): Pair<ReportEntity, StatsResult>? {
        val r = reportDao.byId(id) ?: return null
        return r to json.decodeFromString(StatsResult.serializer(), r.statsJson)
    }

    suspend fun deleteReport(id: Long) = reportDao.delete(id)

    suspend fun dataRange(): Pair<Long, Long>? {
        val min = playbackDao.minTs() ?: return null
        val max = playbackDao.maxTs() ?: return null
        return min to max
    }

    suspend fun deleteAllData() {
        db.withTransaction {
            playbackDao.deleteAll()
            batchDao.deleteAll()
            reportDao.deleteAll()
        }
        settings.lastFmLastSyncUts = 0
    }

    suspend fun deleteSource(source: String) {
        playbackDao.deleteSource(source)
        if (source == Sources.LASTFM) settings.lastFmLastSyncUts = 0
    }

    companion object {
        private const val WINDOW = 3 * 60_000L
        const val DAY = 86_400_000L
        @Volatile private var instance: RewindlyRepository? = null
        fun get(context: Context): RewindlyRepository = instance ?: synchronized(this) {
            instance ?: RewindlyRepository(context.applicationContext).also { instance = it }
        }
    }
}

fun PlaybackEntity.toPlay() = Play(
    track = trackName, artist = artistName, album = albumName, trackKey = trackKey,
    ts = timestampUtc, ms = msPlayed, skipped = skipped, shuffle = shuffle, offline = offline,
    reasonStart = reasonStart, reasonEnd = reasonEnd, platform = platform, estimated = msEstimated
)
