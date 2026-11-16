package com.rewindly.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rewindly.app.data.entities.ArtistFirstSeen
import com.rewindly.app.data.entities.ImportBatchEntity
import com.rewindly.app.data.entities.KeyLastSeen
import com.rewindly.app.data.entities.PlaybackEntity
import com.rewindly.app.data.entities.ReportEntity
import com.rewindly.app.data.entities.SourceCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PlaybackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: PlaybackEntity): Long

    @Query("SELECT * FROM playback WHERE timestampUtc >= :from AND timestampUtc < :to ORDER BY timestampUtc")
    suspend fun between(from: Long, to: Long): List<PlaybackEntity>

    @Query(
        "SELECT * FROM playback WHERE trackKey = :key AND timestampUtc BETWEEN :from AND :to LIMIT 5"
    )
    suspend fun near(key: String, from: Long, to: Long): List<PlaybackEntity>

    @Query("DELETE FROM playback WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT trackKey AS `key`, MAX(timestampUtc) AS lastTs FROM playback WHERE timestampUtc < :before GROUP BY trackKey")
    suspend fun trackLastSeenBefore(before: Long): List<KeyLastSeen>

    @Query("SELECT artistName, MIN(timestampUtc) AS firstTs FROM playback GROUP BY artistName")
    suspend fun artistFirstSeen(): List<ArtistFirstSeen>

    @Query("SELECT COUNT(*) FROM playback")
    fun countFlow(): Flow<Int>

    @Query("SELECT MIN(timestampUtc) FROM playback")
    suspend fun minTs(): Long?

    @Query("SELECT MAX(timestampUtc) FROM playback")
    suspend fun maxTs(): Long?

    @Query("SELECT MAX(timestampUtc) FROM playback")
    fun maxTsFlow(): Flow<Long?>

    @Query("SELECT MIN(timestampUtc) FROM playback")
    fun minTsFlow(): Flow<Long?>

    @Query("SELECT source, COUNT(*) AS count FROM playback GROUP BY source")
    fun countBySource(): Flow<List<SourceCount>>

    @Query("SELECT * FROM playback WHERE source = 'LIVE' ORDER BY timestampUtc DESC LIMIT 20")
    fun recentLive(): Flow<List<PlaybackEntity>>

    @Query("DELETE FROM playback")
    suspend fun deleteAll()

    @Query("DELETE FROM playback WHERE source = :source")
    suspend fun deleteSource(source: String)
}

@Dao
interface ImportBatchDao {
    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long

    @Update
    suspend fun update(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batch ORDER BY createdAt DESC")
    fun all(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batch WHERE source = :source ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastOf(source: String): ImportBatchEntity?

    @Query("DELETE FROM import_batch")
    suspend fun deleteAll()
}

@Dao
interface ReportDao {
    @Insert
    suspend fun insert(report: ReportEntity): Long

    @Query("SELECT * FROM report ORDER BY createdAt DESC")
    fun all(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM report WHERE id = :id")
    suspend fun byId(id: Long): ReportEntity?

    @Query("SELECT COUNT(*) FROM report WHERE type = :type AND periodStart = :start AND periodEnd = :end")
    suspend fun exists(type: String, start: Long, end: Long): Int

    @Query("DELETE FROM report WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM report")
    suspend fun deleteAll()
}
