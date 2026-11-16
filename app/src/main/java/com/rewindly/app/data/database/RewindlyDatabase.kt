package com.rewindly.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rewindly.app.data.dao.ImportBatchDao
import com.rewindly.app.data.dao.PlaybackDao
import com.rewindly.app.data.dao.ReportDao
import com.rewindly.app.data.entities.ImportBatchEntity
import com.rewindly.app.data.entities.PlaybackEntity
import com.rewindly.app.data.entities.ReportEntity

@Database(
    entities = [PlaybackEntity::class, ImportBatchEntity::class, ReportEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RewindlyDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile private var instance: RewindlyDatabase? = null

        fun get(context: Context): RewindlyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RewindlyDatabase::class.java,
                "rewindly.db"
            ).build().also { instance = it }
        }
    }
}
