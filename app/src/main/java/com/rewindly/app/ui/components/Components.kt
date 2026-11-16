package com.rewindly.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewindly.app.analytics.NamedValue
import com.rewindly.app.analytics.RankedItem
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.ui.theme.RColors

enum class Availability(val label: String, val color: Color) {
    REAL("Real", RColors.Teal),
    ESTIMATED("Estimado", RColors.Amber),
    UNAVAILABLE("No disponible", RColors.TextDim)
}

@Composable
fun SectionTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun RCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun GradientCard(colors: List<Color>, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(colors))) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun Badge(a: Availability) {
    Text(
        a.label.uppercase(), color = a.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(a.color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, availability: Availability = Availability.REAL) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 2)
                if (availability != Availability.REAL) Badge(availability)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3) }
        }
    }
}

/** Cuadrícula de 2 columnas de métricas. */
@Composable
fun MetricGrid(items: List<MetricData>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { MetricCard(it.label, it.value, Modifier.weight(1f), it.hint, it.availability) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

data class MetricData(val label: String, val value: String, val hint: String? = null, val availability: Availability = Availability.REAL)

@Composable
fun RankedList(items: List<RankedItem>, showTime: Boolean = true, accent: Color = RColors.Coral) {
    val max = (items.maxOfOrNull { if (showTime) it.ms else it.plays.toLong() } ?: 1L).coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, it ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Black, color = if (i < 3) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(it.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(it.subtitle, "${it.plays} plays", StatsEngine.formatMinutes(it.ms), it.extra).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    val v = if (showTime) it.ms else it.plays.toLong()
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                        Box(Modifier.fillMaxWidth(v.toFloat() / max).fillMaxHeight().clip(CircleShape).background(accent))
                    }
                }
            }
        }
    }
}

/** Gráfico de barras verticales animado (Canvas de Compose). */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, modifier: Modifier = Modifier, color: Color = RColors.Violet, highlight: Int? = null) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) { anim.snapTo(0f); anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / max) * anim.value
                drawRoundRect(
                    color = if (i == highlight) RColors.Coral else color.copy(alpha = 0.85f),
                    topLeft = Offset(i * w + w * 0.15f, size.height - h),
                    size = Size(w * 0.7f, h.coerceAtLeast(3f)),
                    cornerRadius = CornerRadius(w * 0.25f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

/** Gráfico circular (dona) animado. */
@Composable
fun DonutChart(parts: List<NamedValue>, colors: List<Color>, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(parts) { anim.snapTo(0f); anim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val total = parts.sumOf { it.value }.coerceAtLeast(1L)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(120.dp)) {
            var startAngle = -90f
            parts.forEachIndexed { i, p ->
                val sweep = 360f * p.value / total * anim.value
                drawArc(colors[i % colors.size], startAngle, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 34f))
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text("${p.label}  ${"%.0f".format(p.value * 100.0 / total)} %", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

fun pctText(v: Double?) = v?.let { "${"%.0f".format(it)} %" } ?: "—"
