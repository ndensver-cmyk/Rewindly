package com.rewindly.app.importer

import java.io.FilterInputStream
import java.io.InputStream

class ImportException(message: String) : Exception(message)

/** Límites de seguridad contra ZIP maliciosos o desproporcionados. */
object ZipLimits {
    const val MAX_COMPRESSED_BYTES = 1_000L * 1024 * 1024        // 1 GB de ZIP
    const val MAX_TOTAL_UNCOMPRESSED = 2_500L * 1024 * 1024      // 2.5 GB descomprimidos en total
    const val MAX_ENTRY_UNCOMPRESSED = 400L * 1024 * 1024        // 400 MB por JSON
    const val MAX_ENTRIES = 5_000
    const val MAX_RATIO = 200                                    // descompresión excesiva ("zip bomb")
    const val MAX_PLAYS = 3_000_000
}

object ZipValidator {
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun isZip(header: ByteArray): Boolean = header.size >= 4 && header.copyOf(4).contentEquals(ZIP_MAGIC)

    fun isJson(header: ByteArray): Boolean {
        val first = header.map { it.toInt().toChar() }.firstOrNull { !it.isWhitespace() && it != '\uFEFF' }
        return first == '[' || first == '{'
    }

    fun validateSize(size: Long?) {
        if (size != null && size > ZipLimits.MAX_COMPRESSED_BYTES) {
            throw ImportException("El archivo supera el tamaño máximo permitido (1 GB).")
        }
    }

    /** Solo nos interesan los JSON de historial de audio (y no los de vídeo ni podcasts). */
    fun classify(entryName: String): HistoryKind? {
        val name = entryName.substringAfterLast('/').lowercase()
        if (entryName.contains("..")) return null
        if (!name.endsWith(".json")) return null
        return when {
            name.startsWith("streaming_history_audio") -> HistoryKind.EXTENDED
            name.startsWith("endsong") -> HistoryKind.EXTENDED // formato antiguo
            name.startsWith("streaminghistory_music") -> HistoryKind.ACCOUNT
            name.startsWith("streaminghistory") && !name.contains("podcast") && !name.contains("video") -> HistoryKind.ACCOUNT
            else -> null
        }
    }
}

enum class HistoryKind { EXTENDED, ACCOUNT }

/** Cuenta los bytes descomprimidos y corta la lectura si se exceden los límites. */
class LimitedInputStream(
    input: InputStream,
    private val entryLimit: Long,
    private val onBytes: (Long) -> Unit
) : FilterInputStream(input) {
    private var count = 0L

    private fun add(n: Int) {
        if (n > 0) {
            count += n
            onBytes(n.toLong())
            if (count > entryLimit) throw ImportException("Un archivo del ZIP es demasiado grande.")
        }
    }

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) add(1)
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        add(r)
        return r
    }

    /** No cerramos el ZipInputStream subyacente al terminar cada entrada. */
    override fun close() {}
}
