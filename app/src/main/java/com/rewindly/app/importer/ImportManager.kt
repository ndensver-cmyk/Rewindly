package com.rewindly.app.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.data.repository.RewindlyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream

data class ImportReport(
    val fileName: String?,
    val filesFound: Int,
    val read: Int,
    val inserted: Int,
    val duplicates: Int,
    val replaced: Int,
    val ignoredPodcasts: Int,
    val invalid: Int,
    val warnings: List<String>,
    val minTs: Long?,
    val maxTs: Long?
)

class ImportManager(private val context: Context, private val repo: RewindlyRepository) {

    suspend fun import(uri: Uri, displayName: String? = null, onProgress: (Int) -> Unit = {}): ImportReport = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = displayName
        var size: Long? = null
        if (uri.scheme == "file") size = uri.path?.let { java.io.File(it).length() }
        else resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = name ?: c.getString(0)
                size = if (c.isNull(1)) null else c.getLong(1)
            }
        }
        ZipValidator.validateSize(size)

        val stream = resolver.openInputStream(uri) ?: throw ImportException("No se pudo abrir el archivo.")
        BufferedInputStream(stream).use { input ->
            input.mark(16)
            val header = ByteArray(8)
            val n = input.read(header)
            input.reset()
            if (n <= 0) throw ImportException("El archivo está vacío.")

            val buffer = ArrayList<Pair<RawPlay, HistoryKind>>(CHUNK)
            var inserted = 0; var dup = 0; var replaced = 0
            var minTs: Long? = null; var maxTs: Long? = null
            val batchIds = mutableMapOf<HistoryKind, Long>()
            fun srcOf(kind: HistoryKind) = if (kind == HistoryKind.EXTENDED) Sources.ZIP_EXTENDED else Sources.ZIP_ACCOUNT

            fun flush() {
                if (buffer.isEmpty()) return
                // Se agrupa por tipo para conservar la fuente correcta
                buffer.groupBy { it.second }.forEach { (kind, list) ->
                    val src = srcOf(kind)
                    val id = batchIds.getOrPut(kind) { runBlocking { repo.newBatch(src, name) } }
                    val r = runBlocking { repo.insertPlays(list.map { it.first }, src, id) }
                    inserted += r.inserted; dup += r.duplicates; replaced += r.replaced
                }
                buffer.clear()
                onProgress(inserted + dup)
            }

            val collect: (RawPlay, HistoryKind) -> Unit = { p, k ->
                minTs = minOf(minTs ?: p.timestampUtc, p.timestampUtc)
                maxTs = maxOf(maxTs ?: p.timestampUtc, p.timestampUtc)
                buffer += p to k
                if (buffer.size >= CHUNK) flush()
            }

            val summary: ZipImporter.Summary = when {
                ZipValidator.isZip(header) -> ZipImporter().read(input, collect)
                ZipValidator.isJson(header) -> {
                    val kind = ZipValidator.classify(name ?: "") ?: HistoryKind.EXTENDED
                    val r = JsonParser().parse(input) { collect(it, kind) }
                    if (r.read == 0) throw ImportException("El JSON no contiene reproducciones de música reconocibles.")
                    ZipImporter.Summary(1, r.read, r.ignored, r.invalid, setOf(kind), r.warnings)
                }
                else -> throw ImportException("El archivo no es un ZIP ni un JSON válido.")
            }
            flush()

            val warnings = summary.warnings.toMutableList()
            if (summary.kinds == setOf(HistoryKind.ACCOUNT)) {
                warnings += "Importaste Account data: incluye solo el último año y no trae álbum, saltos ni modo aleatorio. Cuando llegue el historial extendido, impórtalo: Rewindly fusionará ambos sin duplicar."
            }
            if (summary.invalid > 0) warnings += "${summary.invalid} registros estaban incompletos y no se importaron."

            batchIds.forEach { (kind, id) ->
                repo.finishBatch(
                    com.rewindly.app.data.entities.ImportBatchEntity(
                        id = id, source = srcOf(kind), fileName = name, createdAt = System.currentTimeMillis(),
                        inserted = inserted, duplicates = dup, skipped = summary.invalid,
                        warnings = warnings.joinToString("\n").ifBlank { null }, minTs = minTs, maxTs = maxTs
                    )
                )
            }
            ImportReport(name, summary.filesFound, summary.read, inserted, dup, replaced, summary.ignored, summary.invalid, warnings, minTs, maxTs)
        }
    }

    companion object { private const val CHUNK = 2_000 }
}
