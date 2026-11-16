package com.rewindly.app.importer

import java.io.InputStream
import java.util.zip.ZipInputStream

/** Recorre el ZIP en streaming sin extraerlo al disco (evita "zip slip"). */
class ZipImporter(private val parser: JsonParser = JsonParser()) {

    data class Summary(
        val filesFound: Int,
        val read: Int,
        val ignored: Int,
        val invalid: Int,
        val kinds: Set<HistoryKind>,
        val warnings: Set<String>
    )

    fun read(input: InputStream, onPlay: (RawPlay, HistoryKind) -> Unit): Summary {
        var entries = 0
        var files = 0
        var total = 0L
        var read = 0; var ignored = 0; var invalid = 0
        val kinds = mutableSetOf<HistoryKind>()
        val warnings = mutableSetOf<String>()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > ZipLimits.MAX_ENTRIES) throw ImportException("El ZIP contiene demasiados archivos.")
                if (entry.isDirectory) continue
                val kind = ZipValidator.classify(entry.name) ?: continue
                if (entry.compressedSize > 0 && entry.size > 0 && entry.size / entry.compressedSize > ZipLimits.MAX_RATIO) {
                    throw ImportException("El ZIP tiene una compresión sospechosa y se rechazó por seguridad.")
                }
                files++
                kinds += kind
                val limited = LimitedInputStream(zip, ZipLimits.MAX_ENTRY_UNCOMPRESSED) { n ->
                    total += n
                    if (total > ZipLimits.MAX_TOTAL_UNCOMPRESSED) throw ImportException("El contenido descomprimido es demasiado grande.")
                }
                val r = parser.parse(limited) { onPlay(it, kind) }
                read += r.read; ignored += r.ignored; invalid += r.invalid
                warnings += r.warnings
            }
        }
        if (files == 0) throw ImportException(
            "No encontré archivos de historial de audio en el ZIP. Busca archivos llamados Streaming_History_Audio_*.json o StreamingHistory_music_*.json."
        )
        return Summary(files, read, ignored, invalid, kinds, warnings)
    }
}
