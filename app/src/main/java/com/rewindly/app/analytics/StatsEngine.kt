package com.rewindly.app.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor estadístico local. Todo se calcula con reglas matemáticas en el dispositivo.
 * Nada se envía a servidores ni a modelos de IA.
 */
object StatsEngine {

    private val es = Locale("es")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", es)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", es)
    private val shortDayFmt = DateTimeFormatter.ofPattern("d MMM", es)
    private const val DAY = 86_400_000L
    private const val NOSTALGIA_GAP = 90L * DAY
    private const val MIN_COUNTED_MS = 30_000L

    fun compute(
        period: List<Play>,
        previous: List<Play>,
        trackLastSeenBefore: Map<String, Long>,
        artistFirstSeen: Map<String, Long>,
        yearContext: List<Play>,
        start: Long,
        end: Long,
        zone: ZoneId,
        sessionGapMinutes: Int = 30
    ): StatsResult {
        val plays = period.sortedBy { it.ts }
        val totalMs = plays.sumOf { it.ms }
        val periodDays = max(1, ((end - start + DAY - 1) / DAY).toInt())
        val localDate: (Long) -> LocalDate = { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

        // ---------- A. Tiempo ----------
        val byDay = plays.groupBy { localDate(it.ts) }
        val dayTotals = byDay.mapValues { (_, l) -> l.sumOf { it.ms } }
        val allDays = generateSequence(localDate(start)) { it.plusDays(1) }
            .takeWhile { it.isBefore(localDate(end - 1).plusDays(1)) }.toList()
        val dailyTotals = allDays.map { NamedValue(it.format(shortDayFmt), dayTotals[it] ?: 0L) }
        val bestDay = dayTotals.maxByOrNull { it.value }?.let { NamedValue(it.key.format(dayFmt), it.value) }
        val worstDay = if (plays.isEmpty()) null else allDays.minByOrNull { dayTotals[it] ?: 0L }
            ?.let { NamedValue(it.format(dayFmt), dayTotals[it] ?: 0L) }
        val byWeek = plays.groupBy { localDate(it.ts).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val bestWeek = byWeek.maxByOrNull { e -> e.value.sumOf { it.ms } }
            ?.let { NamedValue("Semana del " + it.key.format(shortDayFmt), it.value.sumOf { p -> p.ms }) }
        val byMonth = plays.groupBy { localDate(it.ts).withDayOfMonth(1) }
        val bestMonth = byMonth.maxByOrNull { e -> e.value.sumOf { it.ms } }
            ?.let { NamedValue(it.key.format(monthFmt), it.value.sumOf { p -> p.ms }) }

        val hours = LongArray(24)
        val weekdays = LongArray(7)
        plays.forEach {
            val z = Instant.ofEpochMilli(it.ts).atZone(zone)
            hours[z.hour] += it.ms
            weekdays[z.dayOfWeek.value - 1] += it.ms
        }
        val favoriteHour = if (plays.isEmpty()) null else hours.indices.maxByOrNull { hours[it] }
        val morning = (6..11).sumOf { hours[it] }
        val afternoon = (12..18).sumOf { hours[it] }
        val night = (19..23).sumOf { hours[it] }
        val dawn = (0..5).sumOf { hours[it] }
        val dayparts = listOf(
            NamedValue("Mañana", morning), NamedValue("Tarde", afternoon),
            NamedValue("Noche", night), NamedValue("Madrugada", dawn)
        )

        var longest = 0; var current = 0; var streakStart: LocalDate? = null; var bestStreakStart: LocalDate? = null
        var prev: LocalDate? = null
        byDay.keys.sorted().forEach { d ->
            current = if (prev != null && prev!!.plusDays(1) == d) current + 1 else 1
            if (current == 1) streakStart = d
            if (current > longest) { longest = current; bestStreakStart = streakStart }
            prev = d
        }

        val yearly = yearContext.groupBy { localDate(it.ts).year }.toSortedMap()
            .map { NamedValue(it.key.toString(), it.value.sumOf { p -> p.ms }) }
        val monthly = yearContext.groupBy { localDate(it.ts).withDayOfMonth(1) }.toSortedMap()
            .map { NamedValue(it.key.format(monthFmt), it.value.sumOf { p -> p.ms }) }

        // ---------- B. Canciones ----------
        val byTrack = plays.groupBy { it.trackKey }
        fun trackItem(key: String, list: List<Play>, extra: String? = null) =
            RankedItem(list.first().track, list.first().artist, list.size, list.sumOf { it.ms }, extra)

        val topTracks = byTrack.entries.sortedWith(compareByDescending<Map.Entry<String, List<Play>>> { it.value.size }
            .thenByDescending { e -> e.value.sumOf { it.ms } }).take(100).map { trackItem(it.key, it.value) }
        val topTracksByTime = byTrack.entries.sortedByDescending { e -> e.value.sumOf { it.ms } }
            .take(100).map { trackItem(it.key, it.value) }
        val topSingleDay = byDay.flatMap { (d, l) -> l.groupBy { it.trackKey }.map { Triple(d, it.key, it.value) } }
            .maxByOrNull { it.third.size }?.let { trackItem(it.second, it.third, it.first.format(dayFmt)) }
        val topSingleWeek = byWeek.flatMap { (d, l) -> l.groupBy { it.trackKey }.map { Triple(d, it.key, it.value) } }
            .maxByOrNull { it.third.size }?.let { trackItem(it.second, it.third, "Semana del " + it.first.format(shortDayFmt)) }

        val newTrackKeys = byTrack.keys.filter { it !in trackLastSeenBefore }
        val newTracks = newTrackKeys.map { trackItem(it, byTrack.getValue(it)) }.sortedByDescending { it.plays }
        val returned = byTrack.filter { (k, l) ->
            val last = trackLastSeenBefore[k] ?: return@filter false
            l.first().ts - last >= NOSTALGIA_GAP
        }.map { (k, l) ->
            val days = (l.first().ts - trackLastSeenBefore.getValue(k)) / DAY
            trackItem(k, l, "Volvió tras $days días")
        }.sortedByDescending { it.plays }

        val prevByTrack = previous.groupBy { it.trackKey }
        val disappeared = prevByTrack.filter { it.key !in byTrack && it.value.size >= 3 }
            .map { trackItem(it.key, it.value, "Antes: ${it.value.size} reproducciones") }
            .sortedByDescending { it.plays }

        val hasSkip = plays.any { it.skipped != null }
        val skipCount = plays.count { isSkip(it, hasSkip) }
        val mostSkipped = byTrack.map { (k, l) -> k to l.count { isSkip(it, hasSkip) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(10)
            .map { (k, c) -> trackItem(k, byTrack.getValue(k), "$c saltos") }

        val oneTime = byTrack.count { it.value.size == 1 }
        val knownPlays = plays.count { it.trackKey in trackLastSeenBefore }
        val knownPct = pct(knownPlays, plays.size)

        var bestRun = 0; var bestRunKey: String? = null; var run = 0; var lastKey: String? = null
        plays.forEach {
            run = if (it.trackKey == lastKey) run + 1 else 1
            lastKey = it.trackKey
            if (run > bestRun) { bestRun = run; bestRunKey = it.trackKey }
        }
        val repeats = bestRunKey?.takeIf { bestRun >= 2 }?.let { trackItem(it, byTrack.getValue(it), "$bestRun veces seguidas") }

        // ---------- C. Artistas ----------
        val byArtist = plays.groupBy { normalize(it.artist) }
        fun artistItem(list: List<Play>, extra: String? = null) =
            RankedItem(list.first().artist, "${list.map { it.trackKey }.distinct().size} canciones", list.size, list.sumOf { it.ms }, extra)
        val topArtists = byArtist.values.sortedByDescending { l -> l.sumOf { it.ms } }.take(100).map { artistItem(it) }
        val prevByArtist = previous.groupBy { normalize(it.artist) }
        val growth = byArtist.map { (k, l) -> Triple(k, l, l.sumOf { it.ms } - (prevByArtist[k]?.sumOf { it.ms } ?: 0L)) }
            .filter { it.third > 0 && prevByArtist.isNotEmpty() }.maxByOrNull { it.third }
            ?.let { artistItem(it.second, "+" + formatMinutes(it.third) + " vs período anterior") }
        val lost = prevByArtist.map { (k, l) -> Triple(k, l, l.sumOf { it.ms } - (byArtist[k]?.sumOf { it.ms } ?: 0L)) }
            .filter { it.third > 0 }.maxByOrNull { it.third }
            ?.let { RankedItem(it.second.first().artist, null, it.second.size, it.second.sumOf { p -> p.ms }, "-" + formatMinutes(it.third)) }
        val newArtistKeys = byArtist.keys.filter { (artistFirstSeen[it] ?: Long.MAX_VALUE) >= start }
        val newArtists = newArtistKeys.map { artistItem(byArtist.getValue(it)) }.sortedByDescending { it.ms }
        val top5Pct = pctL(topArtists.take(5).sumOf { it.ms }, totalMs)
        val weekly = byWeek.toSortedMap().mapNotNull { (d, l) ->
            l.groupBy { normalize(it.artist) }.maxByOrNull { e -> e.value.sumOf { it.ms } }?.value
                ?.let { artistItem(it, "Semana del " + d.format(shortDayFmt)) }
        }
        val prevTop = prevByArtist.entries.sortedByDescending { e -> e.value.sumOf { it.ms } }.take(10).map { it.key }.toSet()
        val stable = topArtists.take(10).filter { normalize(it.name) in prevTop }.map { it.name }

        // ---------- D. Álbumes ----------
        val withAlbum = plays.filter { !it.album.isNullOrBlank() }
        val byAlbum = withAlbum.groupBy { normalize(it.album!!) + "|" + normalize(it.artist) }
        fun albumItem(l: List<Play>) = RankedItem(l.first().album!!, l.first().artist, l.size, l.sumOf { it.ms },
            "${l.map { it.trackKey }.distinct().size} canciones distintas")
        val topAlbums = byAlbum.values.sortedByDescending { it.size }.take(50).map { albumItem(it) }
        val topAlbumsByTime = byAlbum.values.sortedByDescending { l -> l.sumOf { it.ms } }.take(50).map { albumItem(it) }
        val prevAlbumKeys = previous.filter { !it.album.isNullOrBlank() }.map { normalize(it.album!!) + "|" + normalize(it.artist) }.toSet()
        val newAlbums = byAlbum.keys.count { it !in prevAlbumKeys }
        val albumsTop5 = pct(topAlbums.take(5).sumOf { it.plays }, withAlbum.size)
        // "Escucha profunda": 8 o más canciones distintas del mismo álbum (no prueba que se completara)
        val deepAlbums = byAlbum.values.count { l -> l.map { it.trackKey }.distinct().size >= 8 }

        // ---------- E. Comportamiento ----------
        val sessions = sessions(plays, sessionGapMinutes)
        val shuffleKnown = plays.filter { it.shuffle != null }
        val offlineKnown = plays.filter { it.offline != null }
        val reasonStartKnown = plays.filter { it.reasonStart != null }
        val reasonEndKnown = plays.filter { it.reasonEnd != null }
        val manual = reasonStartKnown.count { it.reasonStart in setOf("clickrow", "playbtn", "click", "fwdbtn", "backbtn", "remote") }
        val natural = reasonEndKnown.count { it.reasonEnd == "trackdone" }
        val platforms = plays.filter { it.platform != null }.groupBy { it.platform!! }
            .map { NamedValue(it.key, it.value.sumOf { p -> p.ms }) }.sortedByDescending { it.value }

        // ---------- F. Análisis inteligentes ----------
        val diversity = shannon(byArtist.values.map { l -> l.sumOf { it.ms }.toDouble() })
        val newArtistSet = newArtistKeys.toHashSet()
        val discovery = pct(plays.count { normalize(it.artist) in newArtistSet }, plays.size)
        val returnedKeys = returned.map { PlayKey(it.name, it.subtitle) }.toSet()
        val nostalgia = pct(plays.count { PlayKey(it.track, it.artist) in returnedKeys }, plays.size)
        val obsession = pct(topTracks.take(3).sumOf { it.plays }, plays.size)
        val stability = if (prevTop.isEmpty()) 0.0 else stable.size * 10.0
        val routine = routineScore(byDay, zone)
        val nightPct = pctL(night + dawn, totalMs)
        val seasonal = yearContext.groupBy { season(localDate(it.ts).monthValue) }
            .map { NamedValue(it.key, it.value.sumOf { p -> p.ms }) }
        val comparison = if (previous.isEmpty()) null else {
            val pt = previous.sumOf { it.ms }
            Comparison(
                prevTotalMs = pt, prevPlays = previous.size, prevArtists = prevByArtist.size,
                totalDeltaPct = if (pt > 0) (totalMs - pt) * 100.0 / pt else null,
                playsDeltaPct = if (previous.isNotEmpty()) (plays.size - previous.size) * 100.0 / previous.size else null,
                artistsDelta = byArtist.size - prevByArtist.size
            )
        }

        val partial = PersonalityInput(
            plays = plays.size, obsessionPct = obsession, discoveryPct = discovery, nostalgiaPct = nostalgia,
            nightPct = nightPct, top5Pct = top5Pct, diversity = diversity, longestSessionMs = sessions.longestMs,
            avgSessionMs = sessions.avgMs, stabilityPct = stability, maxRepeats = bestRun
        )

        return StatsResult(
            periodStart = start, periodEnd = end, zoneId = zone.id,
            totalMs = totalMs, plays = plays.size, periodDays = periodDays, activeDays = byDay.size,
            dailyAvgMs = totalMs / periodDays, weeklyAvgMs = (totalMs * 7) / periodDays,
            bestDay = bestDay, worstDay = worstDay, bestWeek = bestWeek, bestMonth = bestMonth,
            favoriteHour = favoriteHour, hourDistribution = hours.toList(), weekdayDistribution = weekdays.toList(),
            dayparts = dayparts, longestStreakDays = longest,
            longestStreakStart = bestStreakStart?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            yearlyTotals = yearly, monthlyTotals = monthly, dailyTotals = if (dailyTotals.size <= 120) dailyTotals else emptyList(),
            distinctTracks = byTrack.size, topTracks = topTracks, topTracksByTime = topTracksByTime,
            topTrackSingleDay = topSingleDay, topTrackSingleWeek = topSingleWeek,
            newTracksCount = newTrackKeys.size, newTracks = newTracks.take(25), returnedTracks = returned.take(25),
            disappearedTracks = disappeared.take(25), mostSkippedTracks = mostSkipped, oneTimeTracks = oneTime,
            knownPlaysPct = knownPct, maxConsecutiveRepeats = repeats,
            distinctArtists = byArtist.size, topArtists = topArtists, growthArtist = growth, lostArtist = lost,
            newArtistsCount = newArtistKeys.size, newArtists = newArtists.take(25), top5ConcentrationPct = top5Pct,
            weeklyTopArtists = weekly, stableArtists = stable,
            distinctAlbums = byAlbum.size, topAlbums = topAlbums, topAlbumsByTime = topAlbumsByTime,
            newAlbumsCount = newAlbums, albumsTop5Pct = albumsTop5, deepListenedAlbums = deepAlbums,
            skippedPlays = if (hasSkip || plays.isNotEmpty()) skipCount else null,
            skipRatePct = if (plays.isEmpty()) null else pct(skipCount, plays.size),
            avgMsPerPlay = if (plays.isEmpty()) 0 else totalMs / plays.size,
            sessions = sessions,
            shufflePct = if (shuffleKnown.isEmpty()) null else pct(shuffleKnown.count { it.shuffle == true }, shuffleKnown.size),
            offlinePct = if (offlineKnown.isEmpty()) null else pct(offlineKnown.count { it.offline == true }, offlineKnown.size),
            manualStartPct = if (reasonStartKnown.isEmpty()) null else pct(manual, reasonStartKnown.size),
            naturalEndPct = if (reasonEndKnown.isEmpty()) null else pct(natural, reasonEndKnown.size),
            platforms = platforms,
            diversityIndex = diversity, discoveryRatePct = discovery, nostalgiaPct = nostalgia,
            obsessionPct = obsession, stabilityPct = stability, routineScore = routine, nightPct = nightPct,
            seasonal = seasonal, comparison = comparison,
            personality = PersonalityEngine.classify(partial),
            estimatedPlaysPct = pct(plays.count { it.estimated }, plays.size),
            hasSkipData = hasSkip, hasShuffleData = shuffleKnown.isNotEmpty(),
            hasReasonData = reasonEndKnown.isNotEmpty(), hasAlbumData = withAlbum.isNotEmpty()
        )
    }

    private data class PlayKey(val track: String, val artist: String?)

    /** Si hay dato de "skipped" se usa; si no, menos de 30 s escuchados se considera salto. */
    private fun isSkip(p: Play, hasSkipData: Boolean): Boolean =
        if (p.estimated) false
        else if (hasSkipData && p.skipped != null) p.skipped || (p.reasonEnd == "fwdbtn" && p.ms < MIN_COUNTED_MS)
        else p.ms < MIN_COUNTED_MS

    fun sessions(plays: List<Play>, gapMinutes: Int): SessionInfo {
        if (plays.isEmpty()) return SessionInfo(0, 0, 0, null, 0.0, 0)
        val gap = gapMinutes * 60_000L
        val groups = mutableListOf<MutableList<Play>>()
        var lastEnd = Long.MIN_VALUE
        plays.sortedBy { it.ts }.forEach { p ->
            val startTs = p.ts - p.ms
            if (groups.isEmpty() || startTs - lastEnd > gap) groups += mutableListOf(p) else groups.last() += p
            lastEnd = p.ts
        }
        val durations = groups.map { g -> (g.last().ts - (g.first().ts - g.first().ms)).coerceAtLeast(g.sumOf { it.ms }) }
        val longestIdx = durations.indices.maxByOrNull { durations[it] } ?: 0
        return SessionInfo(
            count = groups.size,
            avgMs = durations.average().toLong(),
            longestMs = durations[longestIdx],
            longestStart = groups[longestIdx].first().let { it.ts - it.ms },
            avgTracks = groups.map { it.size }.average(),
            maxTracks = groups.maxOf { it.size }
        )
    }

    /** Entropía de Shannon normalizada (0 = un solo artista, 1 = reparto perfecto). */
    fun shannon(weights: List<Double>): Double {
        val total = weights.sum()
        if (weights.size <= 1 || total <= 0) return 0.0
        val h = -weights.filter { it > 0 }.sumOf { val p = it / total; p * ln(p) }
        return (h / ln(weights.size.toDouble())).coerceIn(0.0, 1.0)
    }

    /** Qué tan repetida es tu hora de escucha entre días (0–100). */
    private fun routineScore(byDay: Map<LocalDate, List<Play>>, zone: ZoneId): Double {
        if (byDay.size < 3) return 0.0
        val peaks = byDay.values.map { l ->
            l.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).hour / 3 }.maxByOrNull { e -> e.value.sumOf { it.ms } }!!.key
        }
        val mode = peaks.groupingBy { it }.eachCount().maxOf { it.value }
        return mode * 100.0 / peaks.size
    }

    private fun season(month: Int) = when (month) {
        12, 1, 2 -> "Dic–Feb"
        3, 4, 5 -> "Mar–May"
        6, 7, 8 -> "Jun–Ago"
        else -> "Sep–Nov"
    }

    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Misma normalización que el importador: minúsculas, sin tildes y espacios simples. */
    fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(marks, "").replace(spaces, " ")
    private fun pct(a: Int, b: Int) = if (b == 0) 0.0 else a * 100.0 / b
    private fun pctL(a: Long, b: Long) = if (b == 0L) 0.0 else a * 100.0 / b

    fun formatMinutes(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }

    fun weekdayName(index: Int): String =
        DayOfWeek.of(index + 1).getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }

    fun weekOfYear(date: LocalDate) = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun round1(d: Double) = (d * 10).roundToInt() / 10.0
}
