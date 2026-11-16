package com.rewindly.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Origen de cada reproducción. El orden define la prioridad al resolver duplicados entre fuentes. */
object Sources {
    const val ZIP_EXTENDED = "ZIP_EXTENDED"   // Extended Streaming History (más completo)
    const val ZIP_ACCOUNT = "ZIP_ACCOUNT"     // Account data (último año, menos campos)
    const val LIVE = "LIVE"                   // Registro en vivo en el dispositivo
    const val LASTFM = "LASTFM"               // Scrobbles de Last.fm

    fun priority(source: String): Int = when (source) {
        ZIP_EXTENDED -> 4
        ZIP_ACCOUNT -> 3
        LIVE -> 2
        LASTFM -> 1
        else -> 0
    }

    fun label(source: String): String = when (source) {
        ZIP_EXTENDED -> "Historial extendido (ZIP)"
        ZIP_ACCOUNT -> "Datos de la cuenta (ZIP)"
        LIVE -> "Registro en vivo"
        LASTFM -> "Last.fm"
        else -> source
    }
}

@Entity(
    tableName = "playback",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["timestampUtc"]),
        Index(value = ["artistName"]),
        Index(value = ["trackKey"])
    ]
)
data class PlaybackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    /** Clave normalizada "canción|artista" para búsquedas rápidas. */
    val trackKey: String,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val trackUri: String?,
    /** Momento en que terminó la reproducción, en milisegundos UTC. */
    val timestampUtc: Long,
    val msPlayed: Long,
    val platform: String?,
    val reasonStart: String?,
    val reasonEnd: String?,
    val shuffle: Boolean?,
    val skipped: Boolean?,
    val offline: Boolean?,
    val privateSession: Boolean?,
    val source: String,
    /** true cuando la duración escuchada es una estimación (por ejemplo, Last.fm). */
    val msEstimated: Boolean = false,
    val batchId: Long
)

@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val fileName: String?,
    val createdAt: Long,
    val inserted: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val warnings: String? = null,
    val minTs: Long? = null,
    val maxTs: Long? = null
)

@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val periodStart: Long,
    val periodEnd: Long,
    val createdAt: Long,
    /** StatsResult serializado en JSON: el informe se conserva aunque cambien los datos. */
    val statsJson: String,
    val personality: String
)

/** Fila ligera para consultas agregadas. */
data class KeyLastSeen(val key: String, val lastTs: Long)
data class ArtistFirstSeen(val artistName: String, val firstTs: Long)
data class SourceCount(val source: String, val count: Int)
