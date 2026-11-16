package com.rewindly.app.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rewindly.app.analytics.RankedItem
import com.rewindly.app.analytics.StatsEngine.formatMinutes
import com.rewindly.app.analytics.StatsEngine.weekdayName
import com.rewindly.app.analytics.StatsResult
import com.rewindly.app.stories.StoryGenerator
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.ReportType
import com.rewindly.app.ui.components.Availability
import com.rewindly.app.ui.components.BarChart
import com.rewindly.app.ui.components.Badge
import com.rewindly.app.ui.components.ShareBar
import com.rewindly.app.ui.components.ScreenHeader
import com.rewindly.app.ui.components.SegmentRow
import com.rewindly.app.ui.components.Hairline
import com.rewindly.app.ui.components.MetricData
import com.rewindly.app.ui.components.MetricGrid
import com.rewindly.app.ui.components.RCard
import com.rewindly.app.ui.components.RankedList
import com.rewindly.app.ui.components.SectionTitle
import com.rewindly.app.ui.components.pctText
import com.rewindly.app.ui.theme.mono
import androidx.compose.foundation.layout.width

@Composable
fun StatisticsScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsState()
    val loading by vm.statsLoading.collectAsState()
    val type by vm.statsType.collectAsState()
    val count by vm.playCount.collectAsState()

    LaunchedEffect(count) { if (count > 0) vm.refreshStats() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader("03", "Estadísticas", "Lo que", "escuchas")
            SegmentRow(
                listOf(ReportType.WEEKLY, ReportType.BIWEEKLY, ReportType.MONTHLY, ReportType.YEARLY, ReportType.ALL), type,
                { when (it) { ReportType.WEEKLY -> "7 d"; ReportType.BIWEEKLY -> "15 d"; ReportType.MONTHLY -> "30 d"; ReportType.YEARLY -> "1 año"; else -> "Todo" } },
                { vm.selectStatsType(it) }, Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)
            )
            Hairline()
        }
        when {
            count == 0 -> Empty("Importa datos para ver tus estadísticas.")
            loading && stats == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = mono.fg, strokeWidth = 1.5.dp) }
            stats == null -> Empty("Calculando…")
            stats!!.isEmpty -> Empty("No hay reproducciones en este período (${StoryGenerator.dateRange(stats!!)}).")
            else -> StatsContent(stats!!, loading)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = mono.dim)
    }
}

@Composable
private fun StatsContent(s: StatsResult, loading: Boolean) {
    var topN by remember { mutableIntStateOf(10) }
    var trackMode by remember { mutableStateOf(0) }
    val timeAvail = if (s.estimatedPlaysPct > 0.5) Availability.ESTIMATED else Availability.REAL

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(StoryGenerator.dateRange(s).uppercase(), style = MaterialTheme.typography.labelMedium, color = mono.dim)
            Spacer(Modifier.height(10.dp))
            Text(formatMinutes(s.totalMs), style = MaterialTheme.typography.displayLarge, color = mono.fg)
            Text("de música en ${s.plays} reproducciones · ${s.distinctArtists} artistas", style = MaterialTheme.typography.bodyMedium, color = mono.dim)
            if (loading) Text("ACTUALIZANDO…", style = MaterialTheme.typography.labelSmall, color = mono.fg)
            if (s.estimatedPlaysPct > 0.5) Text(
                "El ${"%.0f".format(s.estimatedPlaysPct)} % de las reproducciones viene de Last.fm, con tiempo estimado.",
                style = MaterialTheme.typography.bodySmall, color = mono.dim
            )
        }

        // ---------- A. Tiempo ----------
        item {
            SectionTitle("Tiempo", "Cuánto y cuándo escuchas")
            MetricGrid(listOfNotNull(
                MetricData("Tiempo total", formatMinutes(s.totalMs), "${s.plays} reproducciones", timeAvail),
                MetricData("Promedio diario", formatMinutes(s.dailyAvgMs), "${s.activeDays} de ${s.periodDays} días con música", timeAvail),
                MetricData("Promedio semanal", formatMinutes(s.weeklyAvgMs), availability = timeAvail),
                s.bestDay?.let { MetricData("Día con más escucha", formatMinutes(it.value), it.label, timeAvail) },
                s.worstDay?.let { MetricData("Día con menos escucha", formatMinutes(it.value), it.label, timeAvail) },
                s.bestWeek?.let { MetricData("Semana más musical", formatMinutes(it.value), it.label, timeAvail) },
                s.bestMonth?.let { MetricData("Mes más musical", formatMinutes(it.value), it.label, timeAvail) },
                s.favoriteHour?.let { MetricData("Hora favorita", "%02d:00".format(it)) },
                MetricData("Racha más larga", "${s.longestStreakDays} días", "Días seguidos con música"),
                s.bestDay?.let { MetricData("Récord diario", formatMinutes(it.value), availability = timeAvail) }
            ))
        }
        item {
            SectionTitle("Ritmo", "Cuándo suena tu música")
            RCard { Text("POR HORA", style = MaterialTheme.typography.labelSmall, color = mono.dim); Spacer(Modifier.height(10.dp)); BarChart(s.hourDistribution, (0..23).map { if (it % 3 == 0) "$it" else "" }, highlight = s.favoriteHour) }
            Spacer(Modifier.height(10.dp))
            RCard { Text("FRANJAS DEL DÍA", style = MaterialTheme.typography.labelSmall, color = mono.dim); Spacer(Modifier.height(10.dp)); ShareBar(s.dayparts) }
            Spacer(Modifier.height(10.dp))
            RCard {
                Text("POR DÍA DE LA SEMANA", style = MaterialTheme.typography.labelSmall, color = mono.dim)
                Spacer(Modifier.height(6.dp))
                val best = s.weekdayDistribution.indices.maxByOrNull { s.weekdayDistribution[it] }
                BarChart(s.weekdayDistribution, (0..6).map { weekdayName(it).take(3) }, highlight = best)
            }
        }
        if (s.yearlyTotals.size > 1 || s.monthlyTotals.size > 1) item {
            SectionTitle("Evolución", "Tiempo acumulado por mes (últimos 12 meses)")
            RCard {
                val m = s.monthlyTotals.takeLast(12)
                BarChart(m.map { it.value }, m.map { it.label.take(3) })
                if (s.yearlyTotals.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    s.yearlyTotals.forEach { Text("${it.label}: ${formatMinutes(it.value)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // ---------- B. Canciones ----------
        item {
            SectionTitle("Canciones", "${s.distinctTracks} canciones distintas")
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                SegmentRow(listOf(10, 25, 50, 100), topN, { "Top $it" }, { topN = it })
                Spacer(Modifier.width(18.dp))
                SegmentRow(listOf(0, 1), trackMode, { if (it == 0) "Plays" else "Tiempo" }, { trackMode = it })
            }
            Spacer(Modifier.height(8.dp))
            RCard { RankedList((if (trackMode == 0) s.topTracks else s.topTracksByTime).take(topN), showTime = trackMode == 1) }
        }
        item {
            Spacer(Modifier.height(10.dp))
            MetricGrid(listOfNotNull(
                s.topTracks.firstOrNull()?.let { MetricData("Más repetida", it.name, "${it.plays} plays · ${it.subtitle}") },
                s.topTracksByTime.firstOrNull()?.let { MetricData("Más tiempo acumulado", it.name, formatMinutes(it.ms), timeAvail) },
                s.topTrackSingleDay?.let { MetricData("Récord en un solo día", it.name, "${it.plays} veces · ${it.extra}") },
                s.topTrackSingleWeek?.let { MetricData("Récord en una semana", it.name, "${it.plays} veces · ${it.extra}") },
                MetricData("Canciones nuevas", "${s.newTracksCount}", "Nunca antes escuchadas"),
                MetricData("Escuchadas una vez", "${s.oneTimeTracks}"),
                MetricData("Conocidas vs. nuevas", pctText(s.knownPlaysPct), "de las reproducciones eran canciones que ya conocías"),
                s.maxConsecutiveRepeats?.let { MetricData("En bucle", it.name, it.extra) }
            ))
        }
        listBlock("Regresaron después de meses", s.returnedTracks)
        listBlock("Desaparecieron de tus hábitos", s.disappearedTracks)
        listBlock("Canciones nuevas destacadas", s.newTracks)
        if (s.mostSkippedTracks.isNotEmpty()) listBlock(
            "Más saltadas", s.mostSkippedTracks,
            if (s.hasSkipData) null else "Sin dato oficial de saltos: se cuentan reproducciones de menos de 30 s."
        )

        // ---------- C. Artistas ----------
        item {
            SectionTitle("Artistas", "${s.distinctArtists} artistas distintos")
            RCard { RankedList(s.topArtists.take(topN)) }
            Spacer(Modifier.height(10.dp))
            MetricGrid(listOfNotNull(
                s.topArtists.firstOrNull()?.let { MetricData("Más dominante", it.name, formatMinutes(it.ms), timeAvail) },
                s.growthArtist?.let { MetricData("Mayor crecimiento", it.name, it.extra) },
                s.lostArtist?.let { MetricData("Lo perdiste de vista", it.name, it.extra) },
                MetricData("Artistas nuevos", "${s.newArtistsCount}", "Descubiertos en este período"),
                MetricData("Concentración top 5", pctText(s.top5ConcentrationPct), "del tiempo en tus 5 artistas principales"),
                MetricData("Se mantienen en tu top", if (s.stableArtists.isEmpty()) "—" else s.stableArtists.take(3).joinToString(", "), "vs. período anterior")
            ))
        }
        if (s.weeklyTopArtists.size > 1) listBlock("Artista favorito de cada semana", s.weeklyTopArtists)
        listBlock("Descubrimientos", s.newArtists)

        // ---------- D. Álbumes ----------
        item {
            SectionTitle("Álbumes", if (s.hasAlbumData) "${s.distinctAlbums} álbumes distintos" else "Tu exportación no incluye álbumes")
            if (s.hasAlbumData) {
                RCard { RankedList(s.topAlbums.take(minOf(topN, 50)), showTime = false) }
                Spacer(Modifier.height(10.dp))
                MetricGrid(listOfNotNull(
                    s.topAlbums.firstOrNull()?.let { MetricData("Más escuchado", it.name, it.subtitle) },
                    s.topAlbumsByTime.firstOrNull()?.let { MetricData("Más tiempo acumulado", it.name, formatMinutes(it.ms), timeAvail) },
                    MetricData("Álbumes nuevos", "${s.newAlbumsCount}", "vs. período anterior"),
                    MetricData("Escucha profunda", "${s.deepListenedAlbums}", "Álbumes con 8+ canciones distintas (no prueba que estén completos)", Availability.ESTIMATED),
                    MetricData("Concentración top 5", pctText(s.albumsTop5Pct), "de reproducciones en tus 5 álbumes favoritos")
                ))
            }
        }

        // ---------- E. Comportamiento ----------
        item {
            SectionTitle("Comportamiento")
            MetricGrid(listOfNotNull(
                MetricData("Saltos", pctText(s.skipRatePct), "${s.skippedPlays ?: 0} reproducciones", if (s.hasSkipData) Availability.REAL else Availability.ESTIMATED),
                MetricData("Tiempo medio por canción", formatMinutes(s.avgMsPerPlay).let { if (s.avgMsPerPlay < 60_000) "${s.avgMsPerPlay / 1000} s" else it }, availability = timeAvail),
                MetricData("Sesiones", "${s.sessions.count}", "Separadas por pausas de más de 30 min"),
                MetricData("Duración media de sesión", formatMinutes(s.sessions.avgMs)),
                MetricData("Sesión más larga", formatMinutes(s.sessions.longestMs), "${s.sessions.maxTracks} canciones como máximo"),
                MetricData("Canciones por sesión", "%.1f".format(s.sessions.avgTracks)),
                MetricData("Modo aleatorio", pctText(s.shufflePct), availability = if (s.shufflePct == null) Availability.UNAVAILABLE else Availability.REAL),
                MetricData("Sin conexión", pctText(s.offlinePct), availability = if (s.offlinePct == null) Availability.UNAVAILABLE else Availability.REAL),
                MetricData("Iniciadas manualmente", pctText(s.manualStartPct), availability = if (s.manualStartPct == null) Availability.UNAVAILABLE else Availability.REAL),
                MetricData("Terminaron naturalmente", pctText(s.naturalEndPct), "No prueba que se escuchara entera", if (s.naturalEndPct == null) Availability.UNAVAILABLE else Availability.REAL)
            ))
            if (s.platforms.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                RCard { Text("DISPOSITIVOS", style = MaterialTheme.typography.labelSmall, color = mono.dim); Spacer(Modifier.height(10.dp)); ShareBar(s.platforms.take(5)) }
            }
        }

        // ---------- F. Análisis inteligentes ----------
        item {
            SectionTitle("Análisis inteligentes", "Reglas matemáticas locales, sin IA externa")
            RCard {
                Text("PERSONALIDAD MUSICAL", style = MaterialTheme.typography.labelSmall, color = mono.dim)
                Text(s.personality.title, style = MaterialTheme.typography.headlineLarge, color = mono.fg)
                Text(s.personality.description)
                Text(s.personality.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Perfil descriptivo de hábitos, no un diagnóstico de personalidad.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            MetricGrid(listOfNotNull(
                MetricData("Diversidad", "%.2f".format(s.diversityIndex), "0 = un solo artista · 1 = reparto total"),
                MetricData("Dependencia", pctText(s.top5ConcentrationPct), "Concentración en 5 artistas"),
                MetricData("Descubrimiento", pctText(s.discoveryRatePct), "de plays de artistas nuevos"),
                MetricData("Nostalgia", pctText(s.nostalgiaPct), "de plays que volvieron tras 90+ días"),
                MetricData("Obsesión", pctText(s.obsessionPct), "de plays en tus 3 canciones top"),
                MetricData("Estabilidad", pctText(s.stabilityPct), "del top 10 se mantiene"),
                MetricData("Rutina", pctText(s.routineScore), "de días con la misma franja horaria pico"),
                MetricData("Nocturnidad", pctText(s.nightPct), "del tiempo entre 19:00 y 06:00"),
                s.comparison?.let { c ->
                    MetricData("Vs. período anterior", c.totalDeltaPct?.let { (if (it >= 0) "+" else "") + "%.0f %%".format(it) } ?: "—",
                        "Antes: ${formatMinutes(c.prevTotalMs)} · artistas ${if (c.artistsDelta >= 0) "+" else ""}${c.artistsDelta}")
                }
            ))
            if (s.seasonal.size > 1) {
                Spacer(Modifier.height(10.dp))
                RCard {
                    Text("CAMBIOS DE TEMPORADA", style = MaterialTheme.typography.labelSmall, color = mono.dim)
                    Spacer(Modifier.height(10.dp))
                    ShareBar(s.seasonal)
                }
            }
        }

        // ---------- Precisión ----------
        item {
            SectionTitle("Precisión de los datos", "Qué es real, qué es estimado y qué no existe en la exportación")
            RCard {
                listOf(
                    "Tiempo escuchado" to timeAvail,
                    "Canciones y artistas más escuchados" to Availability.REAL,
                    "Hora favorita" to Availability.REAL,
                    "Canciones saltadas" to if (s.hasSkipData) Availability.REAL else Availability.ESTIMATED,
                    "Sesiones musicales" to Availability.ESTIMATED,
                    "Géneros musicales" to Availability.UNAVAILABLE,
                    "Estado de ánimo" to Availability.UNAVAILABLE,
                    "Popularidad de canciones" to Availability.UNAVAILABLE,
                    "Edad de una canción" to Availability.UNAVAILABLE
                ).forEach { (k, a) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = mono.fg); Badge(a)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Las horas se calculan en la zona horaria ${s.zoneId}. Una reproducción de 3 minutos no prueba que la canción se haya escuchado completa.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.listBlock(title: String, items: List<RankedItem>, note: String? = null) {
    if (items.isEmpty()) return
    item {
        SectionTitle(title, note)
        RCard { RankedList(items.take(10), showTime = false) }
    }
}
