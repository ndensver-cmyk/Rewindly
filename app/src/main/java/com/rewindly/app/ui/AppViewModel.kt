package com.rewindly.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rewindly.app.analytics.StatsResult
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.data.repository.RewindlyRepository
import com.rewindly.app.importer.ImportManager
import com.rewindly.app.importer.ImportReport
import com.rewindly.app.lastfm.LastFmClient
import com.rewindly.app.stories.StoryExporter
import com.rewindly.app.stories.StoryGenerator
import com.rewindly.app.stories.StoryScene
import com.rewindly.app.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val processed: Int, val message: String) : ImportState
    data class Done(val report: ImportReport) : ImportState
    data class LastFmDone(val inserted: Int, val duplicates: Int, val read: Int) : ImportState
    data class Error(val message: String) : ImportState
}

data class StoryState(val reportId: Long?, val title: String, val scenes: List<StoryScene>, val stats: StatsResult)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = RewindlyRepository.get(app)
    val settings = repo.settings
    private val importer = ImportManager(app, repo)

    val playCount = repo.playCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val minTs = repo.minTs.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val maxTs = repo.maxTs.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val sourceCounts = repo.sourceCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reports = repo.reports.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val batches = repo.batches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentLive = repo.recentLive.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _import.asStateFlow()

    private val _story = MutableStateFlow<StoryState?>(null)
    val story: StateFlow<StoryState?> = _story.asStateFlow()

    private val _stats = MutableStateFlow<StatsResult?>(null)
    val stats: StateFlow<StatsResult?> = _stats.asStateFlow()
    private val _statsLoading = MutableStateFlow(false)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()
    private val _statsType = MutableStateFlow(ReportType.MONTHLY)
    val statsType: StateFlow<ReportType> = _statsType.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun importFile(uri: Uri) = viewModelScope.launch {
        _import.value = ImportState.Running(0, "Leyendo archivo…")
        _import.value = try {
            val r = importer.import(uri) { n -> _import.value = ImportState.Running(n, "Procesando reproducciones…") }
            refreshStats()
            ImportState.Done(r)
        } catch (e: Exception) {
            ImportState.Error(e.message ?: "No se pudo importar el archivo.")
        }
    }

    fun syncLastFm() = viewModelScope.launch {
        _import.value = ImportState.Running(0, "Conectando con Last.fm…")
        _import.value = try {
            val r = LastFmClient(repo).sync { p, t -> _import.value = ImportState.Running(p, "Descargando página $p de $t…") }
            refreshStats()
            ImportState.LastFmDone(r.inserted, r.duplicates, r.read)
        } catch (e: Exception) {
            ImportState.Error(e.message ?: "No se pudo sincronizar Last.fm.")
        }
    }

    fun resetImportState() { _import.value = ImportState.Idle }

    fun selectStatsType(type: ReportType) { _statsType.value = type; refreshStats() }

    fun refreshStats(customRange: Pair<Long, Long>? = null) = viewModelScope.launch {
        _statsLoading.value = true
        val range = customRange ?: rangeFor(_statsType.value)
        _stats.value = withContext(Dispatchers.Default) { repo.computeStats(range.first, range.second) }
        _statsLoading.value = false
    }

    private suspend fun rangeFor(type: ReportType): Pair<Long, Long> {
        val r = repo.dataRange()
        return Periods.range(type, settings.zone(), r?.first, r?.second)
    }

    /** Genera un informe, lo guarda en la biblioteca y abre sus historias. */
    fun generateReport(type: ReportType, custom: Pair<Long, Long>? = null) = viewModelScope.launch {
        if (playCount.value == 0) { _message.value = "Primero importa datos: no hay reproducciones para analizar."; return@launch }
        val range = custom ?: rangeFor(type)
        val stats = withContext(Dispatchers.Default) { repo.computeStats(range.first, range.second) }
        val title = "${type.label} · ${StoryGenerator.dateRange(stats)}"
        val id = if (stats.isEmpty) null else repo.saveReport(type.name, title, stats)
        _story.value = StoryState(id, title, StoryGenerator.generate(stats), stats)
    }

    fun openReport(id: Long) = viewModelScope.launch {
        val (r, s) = repo.loadReport(id) ?: return@launch
        _story.value = StoryState(id, r.title, StoryGenerator.generate(s), s)
    }

    fun deleteReport(id: Long) = viewModelScope.launch { repo.deleteReport(id) }

    fun closeStory() { _story.value = null }

    fun exportPng(index: Int) = viewModelScope.launch {
        val st = _story.value ?: return@launch
        val ctx = getApplication<Application>()
        val f = withContext(Dispatchers.IO) { StoryExporter.exportPng(ctx, st.scenes[index], "rewindly_${System.currentTimeMillis()}") }
        StoryExporter.share(ctx, f, "image/png")
    }

    fun exportPdf() = viewModelScope.launch {
        val st = _story.value ?: return@launch
        val ctx = getApplication<Application>()
        val f = withContext(Dispatchers.IO) { StoryExporter.exportPdf(ctx, st.scenes, "rewindly_${System.currentTimeMillis()}") }
        StoryExporter.share(ctx, f, "application/pdf")
    }

    fun deleteAll() = viewModelScope.launch {
        repo.deleteAllData(); _stats.value = null
        _message.value = "Se eliminó todo el historial importado y los informes."
    }

    fun deleteSource(source: String) = viewModelScope.launch {
        repo.deleteSource(source); refreshStats()
        _message.value = "Se eliminaron los datos de ${Sources.label(source)}."
    }

    fun updateSchedules() = Scheduler.apply(getApplication())
}
