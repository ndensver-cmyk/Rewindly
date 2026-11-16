package com.rewindly.app.analytics

data class PersonalityInput(
    val plays: Int,
    val obsessionPct: Double,
    val discoveryPct: Double,
    val nostalgiaPct: Double,
    val nightPct: Double,
    val top5Pct: Double,
    val diversity: Double,
    val longestSessionMs: Long,
    val avgSessionMs: Long,
    val stabilityPct: Double,
    val maxRepeats: Int
)

/**
 * Perfiles descriptivos basados en reglas. No son diagnósticos psicológicos:
 * solo resumen hábitos de escucha del período.
 */
object PersonalityEngine {

    fun classify(i: PersonalityInput): Personality {
        if (i.plays < 10) return Personality(
            "STARTER", "El Comienzo", "Todavía hay pocos datos en este período para describir un perfil.",
            "Menos de 10 reproducciones", 0
        )
        // Cada perfil recibe una puntuación normalizada; gana la más alta
        val scores = listOf(
            Triple("REPEATER", i.obsessionPct / 35.0 + (i.maxRepeats / 8.0), "Tus 3 canciones principales suman el ${p(i.obsessionPct)} de tus reproducciones"),
            Triple("EXPLORER", i.discoveryPct / 30.0, "El ${p(i.discoveryPct)} de tus reproducciones fueron de artistas nuevos"),
            Triple("NOSTALGIC", i.nostalgiaPct / 15.0, "El ${p(i.nostalgiaPct)} de lo que escuchaste volvió tras meses sin sonar"),
            Triple("NOCTURNAL", i.nightPct / 55.0, "El ${p(i.nightPct)} de tu tiempo fue de noche o de madrugada"),
            Triple("SPECIALIST", i.top5Pct / 65.0, "Tus 5 artistas principales concentran el ${p(i.top5Pct)} del tiempo"),
            Triple("ECLECTIC", i.diversity / 0.85, "Índice de diversidad de ${"%.2f".format(i.diversity)} sobre 1"),
            Triple("MARATHONER", i.longestSessionMs / (4.0 * 3_600_000) + i.avgSessionMs / (2.0 * 3_600_000), "Tu sesión más larga duró ${StatsEngine.formatMinutes(i.longestSessionMs)}"),
            Triple("LOYAL", i.stabilityPct / 70.0, "El ${p(i.stabilityPct)} de tu top 10 de artistas se mantuvo respecto al período anterior")
        )
        val (key, _, reason) = scores.maxBy { it.second }
        return when (key) {
            "REPEATER" -> Personality(key, "El Repetidor", "Cuando una canción te atrapa, la escuchas una y otra vez.", reason, 1)
            "EXPLORER" -> Personality(key, "El Explorador", "Buscas sonidos nuevos y artistas que aún no conocías.", reason, 2)
            "NOSTALGIC" -> Personality(key, "El Nostálgico", "Te gusta volver a canciones que marcaron otra época.", reason, 3)
            "NOCTURNAL" -> Personality(key, "El Nocturno", "Tu música vive cuando cae la noche.", reason, 4)
            "SPECIALIST" -> Personality(key, "El Especialista", "Tienes pocos artistas, pero los conoces a fondo.", reason, 5)
            "ECLECTIC" -> Personality(key, "El Ecléctico", "Repartes tu escucha entre muchos artistas distintos.", reason, 6)
            "MARATHONER" -> Personality(key, "El Maratonista", "Encadenas sesiones largas sin soltar los audífonos.", reason, 7)
            else -> Personality("LOYAL", "El Incondicional", "Tus favoritos se mantienen firmes período tras período.", reason, 8)
        }
    }

    private fun p(d: Double) = "${"%.0f".format(d)} %"
}
