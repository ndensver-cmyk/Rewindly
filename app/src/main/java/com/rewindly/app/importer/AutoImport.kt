package com.rewindly.app.importer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.rewindly.app.data.repository.RewindlyRepository

/**
 * Importación automática: busca exportaciones de Spotify que el usuario ya descargó
 * (en Descargas o en una carpeta elegida) y las importa sin pasos manuales.
 * Solo lee archivos cuyo nombre coincide con los de la exportación oficial.
 */
object AutoImport {

    data class Candidate(val uri: Uri, val name: String, val size: Long, val modified: Long) {
        val key get() = "$name|$size|$modified"
    }

    data class Result(val imported: List<ImportReport>, val errors: List<String>)

    /** Reglas de nombre de la exportación oficial (my_spotify_data.zip, Streaming_History_*.json…). */
    fun looksLikeSpotifyExport(name: String): Boolean {
        val n = name.lowercase()
        if (n.endsWith(".crdownload") || n.endsWith(".part") || n.endsWith(".tmp")) return false
        if (n.endsWith(".zip")) return n.contains("spotify")
        if (n.endsWith(".json")) return n.startsWith("streaming_history") || n.startsWith("streaminghistory")
        return false
    }

    fun hasDownloadsAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean {
        val s = RewindlyRepository.get(context).settings
        return (s.scanDownloads && hasDownloadsAccess(context)) || s.watchedTreeUri != null
    }

    fun findCandidates(context: Context): List<Candidate> {
        val s = RewindlyRepository.get(context).settings
        val now = System.currentTimeMillis()
        val out = mutableListOf<Candidate>()

        if (s.scanDownloads && hasDownloadsAccess(context)) {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            runCatching {
                dir.walkTopDown().maxDepth(3)
                    .filter { it.isFile && looksLikeSpotifyExport(it.name) }
                    .forEach { out += Candidate(Uri.fromFile(it), it.name, it.length(), it.lastModified()) }
            }
        }
        s.watchedTreeUri?.let { tree -> runCatching { listTree(context, Uri.parse(tree), out) } }

        val done = s.processedFiles
        // Ignora archivos que se siguen escribiendo (descarga en curso)
        return out.filter { it.key !in done && now - it.modified > 15_000 }.distinctBy { it.key }
    }

    private fun listTree(context: Context, tree: Uri, out: MutableList<Candidate>) {
        val resolver = context.contentResolver
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            resolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1) ?: continue
                    val mime = c.getString(4)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 2) walk(id, depth + 1); continue }
                    if (!looksLikeSpotifyExport(name)) continue
                    out += Candidate(
                        DocumentsContract.buildDocumentUriUsingTree(tree, id), name,
                        if (c.isNull(2)) 0 else c.getLong(2), if (c.isNull(3)) 0 else c.getLong(3)
                    )
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
    }

    /** Importa todo lo nuevo. Cada archivo se marca como procesado para no repetirlo. */
    suspend fun run(context: Context): Result {
        val repo = RewindlyRepository.get(context)
        val s = repo.settings
        val manager = ImportManager(context, repo)
        val ok = mutableListOf<ImportReport>(); val errors = mutableListOf<String>()
        // Primero los archivos más antiguos (account data suele llegar antes que el extendido)
        for (c in findCandidates(context).sortedBy { it.modified }) {
            try { ok += manager.import(c.uri, c.name) } catch (e: Exception) { errors += "${c.name}: ${e.message}" }
            s.processedFiles = s.processedFiles + c.key
        }
        s.lastAutoScanAt = System.currentTimeMillis()
        return Result(ok, errors)
    }

    fun treeLabel(uri: String?): String? = uri?.let {
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)).substringAfter(':').ifBlank { "Almacenamiento" } }.getOrNull()
    }
}
