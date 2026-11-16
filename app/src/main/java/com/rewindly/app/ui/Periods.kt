package com.rewindly.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ReportType(val label: String, val days: Int?) {
    WEEKLY("Semanal", 7),
    BIWEEKLY("Quincenal", 15),
    MONTHLY("Mensual", 30),
    YEARLY("Anual", 365),
    ALL("Todo el historial", null),
    CUSTOM("Personalizado", null)
}

object Periods {
    private const val DAY = 86_400_000L

    /**
     * El período termina en el último día con datos (o hoy, si hay datos de hoy):
     * Rewindly nunca inventa reproducciones que todavía no se han importado.
     */
    fun range(type: ReportType, zone: ZoneId, dataMin: Long?, dataMax: Long?, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val anchor = minOf(now, dataMax ?: now)
        val endDate = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate().plusDays(1)
        val end = endDate.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (type) {
            ReportType.ALL -> {
                val start = Instant.ofEpochMilli(dataMin ?: (end - 365 * DAY)).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
            else -> endDate.minusDays((type.days ?: 30).toLong()).atStartOfDay(zone).toInstant().toEpochMilli() to end
        }
    }

    /** DateRangePicker entrega medianoche UTC; lo convertimos a la zona del usuario. */
    fun customRange(startUtcMidnight: Long, endUtcMidnight: Long, zone: ZoneId): Pair<Long, Long> {
        val s = Instant.ofEpochMilli(startUtcMidnight).atZone(ZoneId.of("UTC")).toLocalDate()
        val e = Instant.ofEpochMilli(endUtcMidnight).atZone(ZoneId.of("UTC")).toLocalDate().plusDays(1)
        return s.atStartOfDay(zone).toInstant().toEpochMilli() to e.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun lastCompletedWeek(zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Pair<Long, Long> {
        val end = today.atStartOfDay(zone).toInstant().toEpochMilli()
        return end - 7 * DAY to end
    }
}
