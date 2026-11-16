package com.rewindly.app.stories

import com.rewindly.app.analytics.NamedValue
import com.rewindly.app.analytics.RankedItem
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.analytics.StatsResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SceneKind { COVER, TIME, ARTIST, SONGS, HOURS, DISCOVERY, BEHAVIOR, PERSONALITY, SUMMARY }

data class StoryScene(
    val kind: SceneKind,
    val eyebrow: String,
    val title: String,
    val bigNumber: Long? = null,
    val bigUnit: String? = null,
    val bigText: String? = null,
    val body: String? = null,
    val items: List<RankedItem> = emptyList(),
    val bars: List<NamedValue> = emptyList(),
    val facts: List<Pair<String, String>> = emptyList(),
    val palette: Int = 0
)

object StoryGenerator {
    private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))

    fun periodLabel(days: Int) = when {
        days <= 7 -> "Tu semana en música"
        days <= 16 -> "Tu quincena en música"
        days <= 31 -> "Tu mes en música"
        days <= 366 -> "Tu año en música"
        else -> "Tu historia en música"
    }

    fun dateRange(s: StatsResult): String {
        val z = runCatching { ZoneId.of(s.zoneId) }.getOrDefault(ZoneId.systemDefault())
        val a = Instant.ofEpochMilli(s.periodStart).atZone(z).format(fmt)
        val b = Instant.ofEpochMilli(s.periodEnd - 1).atZone(z).format(fmt)
        return "$a — $b"
    }

    fun generate(s: StatsResult): List<StoryScene> {
        val pal = s.personality.colorIndex
        if (s.isEmpty) return listOf(
            StoryScene(SceneKind.COVER, dateRange(s), "Sin música en este período",
                body = "No hay reproducciones importadas en estas fechas. Importa un ZIP, activa el registro en vivo o sincroniza Last.fm.", palette = 0)
        )
        val scenes = mutableListOf<StoryScene>()
        scenes += StoryScene(SceneKind.COVER, dateRange(s), periodLabel(s.periodDays),
            body = "${s.plays} reproducciones · ${s.distinctArtists} artistas · ${s.distinctTracks} canciones", palette = pal)

        val minutes = s.totalMs / 60_000
        scenes += StoryScene(
            SceneKind.TIME, "TIEMPO ESCUCHANDO", "Le diste play a tu vida durante",
            bigNumber = minutes, bigUnit = "minutos",
            body = buildString {
                append("Eso es un promedio de ${StatsEngine.formatMinutes(s.dailyAvgMs)} al día.")
                s.bestDay?.let { append("\nTu día récord fue el ${it.label}, con ${StatsEngine.formatMinutes(it.value)}.") }
                if (s.estimatedPlaysPct > 0) append("\n(Incluye tiempo estimado en el ${"%.0f".format(s.estimatedPlaysPct)} % de reproducciones.)")
            },
            bars = s.dailyTotals.takeLast(31), palette = (pal + 1) % 9
        )

        s.topArtists.firstOrNull()?.let { a ->
            scenes += StoryScene(
                SceneKind.ARTIST, "ARTISTA PRINCIPAL", "El artista que dominó tu período",
                bigText = a.name,
                body = "${StatsEngine.formatMinutes(a.ms)} · ${a.plays} reproducciones · el ${"%.0f".format(if (s.totalMs > 0) a.ms * 100.0 / s.totalMs else 0.0)} % de tu tiempo",
                items = s.topArtists.take(5), palette = (pal + 2) % 9
            )
        }

        scenes += StoryScene(
            SceneKind.SONGS, "CANCIONES FAVORITAS", "Las canciones que marcaron el período",
            items = s.topTracks.take(5),
            body = s.maxConsecutiveRepeats?.let { "La pusiste en bucle: ${it.name}, ${it.extra}." },
            palette = (pal + 3) % 9
        )

        val hour = s.favoriteHour
        scenes += StoryScene(
            SceneKind.HOURS, "TU RELOJ MUSICAL", "Tu hora favorita",
            bigText = hour?.let { "%02d:00".format(it) },
            body = s.dayparts.maxByOrNull { it.value }?.let { "Tu momento del día: la ${it.label.lowercase()}." },
            bars = s.dayparts, palette = (pal + 4) % 9
        )

        scenes += StoryScene(
            SceneKind.DISCOVERY, "DESCUBRIMIENTO", "Abriste tus oídos a",
            bigNumber = s.newArtistsCount.toLong(), bigUnit = if (s.newArtistsCount == 1) "artista nuevo" else "artistas nuevos",
            body = buildString {
                append("${s.newTracksCount} canciones sonaron por primera vez.")
                if (s.returnedTracks.isNotEmpty()) append("\nY volviste a ${s.returnedTracks.first().name}: ${s.returnedTracks.first().extra?.lowercase()}.")
            },
            items = s.newArtists.take(3), palette = (pal + 5) % 9
        )

        scenes += StoryScene(
            SceneKind.BEHAVIOR, "TUS HÁBITOS", "Cómo escuchas",
            facts = listOfNotNull(
                "Sesiones" to s.sessions.count.toString(),
                "Sesión más larga" to StatsEngine.formatMinutes(s.sessions.longestMs),
                "Canciones por sesión" to "%.1f".format(s.sessions.avgTracks),
                s.skipRatePct?.let { "Saltos" to "${"%.0f".format(it)} %" },
                s.shufflePct?.let { "Modo aleatorio" to "${"%.0f".format(it)} %" },
                "Racha" to "${s.longestStreakDays} días seguidos"
            ),
            palette = (pal + 6) % 9
        )

        scenes += StoryScene(
            SceneKind.PERSONALITY, "PERSONALIDAD MUSICAL", "Este período fuiste",
            bigText = s.personality.title,
            body = s.personality.description + "\n\n" + s.personality.reason + ".",
            palette = pal
        )

        scenes += StoryScene(
            SceneKind.SUMMARY, dateRange(s), "Tu identidad musical",
            facts = listOfNotNull(
                "Minutos" to minutes.toString(),
                s.topArtists.firstOrNull()?.let { "Artista" to it.name },
                s.topTracks.firstOrNull()?.let { "Canción" to it.name },
                "Perfil" to s.personality.title,
                "Artistas" to s.distinctArtists.toString(),
                "Nuevos" to s.newArtistsCount.toString()
            ),
            palette = pal
        )
        return scenes
    }
}
