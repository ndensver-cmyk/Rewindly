package com.rewindly.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewindly.app.analytics.NamedValue
import com.rewindly.app.analytics.RankedItem
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.ui.theme.Serif
import com.rewindly.app.ui.theme.mono

enum class Availability(val label: String) { REAL("REAL"), ESTIMATED("ESTIMADO"), UNAVAILABLE("N/D") }

private val Square = RoundedCornerShape(2.dp)

/** Línea fina de separación. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(mono.line))
}

/** Encabezado de pantalla editorial: índice en mono, titular serif con una palabra en cursiva. */
@Composable
fun ScreenHeader(index: String, kicker: String, title: String, italic: String? = null, lead: String? = null) {
    val m = mono
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index, style = MaterialTheme.typography.labelSmall, color = m.fg)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(18.dp).height(1.dp).background(m.faint))
            Spacer(Modifier.width(10.dp))
            Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = m.dim)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            buildAnnotatedString {
                append(title)
                italic?.let { append(" "); withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(it) } }
            },
            style = MaterialTheme.typography.displaySmall, color = m.fg
        )
        lead?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = m.dim)
        }
    }
}

/** Título de sección: filete superior y rótulo en mono. */
@Composable
fun SectionTitle(text: String, subtitle: String? = null, index: String? = null) {
    val m = mono
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp)) {
        Hairline()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = m.fg, modifier = Modifier.weight(1f))
            index?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = m.faint) }
        }
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = m.dim)
        }
    }
}

/** Bloque con borde fino, sin relleno. */
@Composable
fun RCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Square).border(1.dp, mono.line, Square).padding(16.dp),
        content = content
    )
}

/** Bloque invertido (tinta de fondo), para el dato protagonista. */
@Composable
fun InverseBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val m = mono
    Column(modifier.fillMaxWidth().clip(Square).background(m.fg).padding(20.dp), content = content)
}

@Composable
fun Badge(a: Availability) {
    val m = mono
    val color = when (a) { Availability.REAL -> m.fg; Availability.ESTIMATED -> m.dim; Availability.UNAVAILABLE -> m.faint }
    Text(
        a.label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 1.sp),
        textDecoration = if (a == Availability.UNAVAILABLE) TextDecoration.LineThrough else null,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.6f), Square).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

data class MetricData(val label: String, val value: String, val hint: String? = null, val availability: Availability = Availability.REAL)

/** Cuadrícula de 2 columnas separada por filetes, al estilo de una tabla impresa. */
@Composable
fun MetricGrid(items: List<MetricData>) {
    val m = mono
    Column(Modifier.fillMaxWidth().border(1.dp, m.line, Square)) {
        items.chunked(2).forEachIndexed { r, row ->
            if (r > 0) Hairline()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                MetricCell(row[0], Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(m.line))
                if (row.size > 1) MetricCell(row[1], Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCell(d: MetricData, modifier: Modifier) {
    val m = mono
    Column(modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(d.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = m.dim, modifier = Modifier.weight(1f), maxLines = 2)
            if (d.availability != Availability.REAL) { Spacer(Modifier.width(4.dp)); Badge(d.availability) }
        }
        Spacer(Modifier.height(8.dp))
        val numeric = d.value.firstOrNull()?.let { it.isDigit() || it == '+' || it == '-' || it == '—' } == true
        Text(
            d.value, fontFamily = Serif, fontSize = if (numeric) 30.sp else 21.sp, lineHeight = if (numeric) 30.sp else 23.sp,
            color = m.fg, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        d.hint?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = m.dim, maxLines = 3) }
    }
}

/** Ranking con número en mono y barra de 2 dp. */
@Composable
fun RankedList(items: List<RankedItem>, showTime: Boolean = true) {
    val m = mono
    val max = (items.maxOfOrNull { if (showTime) it.ms else it.plays.toLong() } ?: 1L).coerceAtLeast(1L)
    Column {
        items.forEachIndexed { i, it ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(i + 1), modifier = Modifier.width(34.dp), style = MaterialTheme.typography.labelMedium, color = if (i < 3) m.fg else m.faint)
                Column(Modifier.weight(1f)) {
                    Text(it.name, style = MaterialTheme.typography.titleSmall, color = m.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(it.subtitle, it.extra).joinToString(" · ").ifBlank { "${it.plays} reproducciones" },
                        style = MaterialTheme.typography.bodySmall, color = m.dim, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    val v = if (showTime) it.ms else it.plays.toLong()
                    Box(Modifier.fillMaxWidth().height(2.dp).background(m.line)) {
                        Box(Modifier.fillMaxWidth(v.toFloat() / max).fillMaxHeight().background(m.fg))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (showTime) StatsEngine.formatMinutes(it.ms) else "${it.plays}",
                    style = MaterialTheme.typography.labelMedium, color = m.fg
                )
            }
        }
    }
}

/** Barras verticales rectas, animadas al aparecer. La barra destacada va en tinta plena. */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, modifier: Modifier = Modifier, highlight: Int? = null) {
    val m = mono
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) { anim.snapTo(0f); anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / max) * anim.value
                drawRect(
                    color = if (highlight == null || i == highlight) m.fg else m.fg.copy(alpha = 0.32f),
                    topLeft = Offset(i * w + w * 0.18f, size.height - h),
                    size = Size(w * 0.64f, h.coerceAtLeast(1.5f))
                )
            }
            drawRect(m.faint, Offset(0f, size.height - 1f), Size(size.width, 1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 0.sp), color = m.dim, maxLines = 1) }
        }
    }
}

/** Reparto proporcional: una barra segmentada en tonos de gris y su leyenda. */
@Composable
fun ShareBar(parts: List<NamedValue>, modifier: Modifier = Modifier) {
    val m = mono
    val anim = remember { Animatable(0f) }
    LaunchedEffect(parts) { anim.snapTo(0f); anim.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val total = parts.sumOf { it.value }.coerceAtLeast(1L)
    val shades = listOf(1f, 0.62f, 0.38f, 0.2f, 0.1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            var x = 0f
            parts.forEachIndexed { i, p ->
                val w = size.width * p.value / total * anim.value
                drawRect(m.fg.copy(alpha = shades[i % shades.size]), Offset(x, 0f), Size((w - 2f).coerceAtLeast(0f), size.height))
                x += w
            }
        }
        Spacer(Modifier.height(10.dp))
        parts.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(m.fg.copy(alpha = shades[i % shades.size])))
                Spacer(Modifier.width(10.dp))
                Text(p.label, style = MaterialTheme.typography.bodyMedium, color = m.fg, modifier = Modifier.weight(1f))
                Text("%.0f %%".format(p.value * 100.0 / total), style = MaterialTheme.typography.labelMedium, color = m.dim)
            }
        }
    }
}

/** Botón principal: rectángulo de tinta plena. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val m = mono
    Row(
        modifier.heightIn(min = 48.dp).clip(Square).background(if (enabled) m.fg else m.line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        icon?.let { Icon(it, null, tint = if (enabled) m.bg else m.faint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) m.bg else m.faint)
    }
}

/** Botón secundario: contorno fino. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val m = mono
    Row(
        modifier.heightIn(min = 44.dp).clip(Square).border(BorderStroke(1.dp, if (enabled) m.fg.copy(alpha = 0.5f) else m.line), Square)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        icon?.let { Icon(it, null, tint = if (enabled) m.fg else m.faint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) m.fg else m.faint)
    }
}

/** Enlace de texto en mono con flecha. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val m = mono
    Text(
        "$text  →", style = MaterialTheme.typography.labelMedium, color = m.fg,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}

/** Fila con interruptor monocromo. */
@Composable
fun ToggleRow(title: String, sub: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val m = mono
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) m.fg else m.faint)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = m.dim) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            value, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = m.bg, checkedTrackColor = m.fg, checkedBorderColor = m.fg,
                uncheckedThumbColor = m.dim, uncheckedTrackColor = m.bg, uncheckedBorderColor = m.faint
            )
        )
    }
}

/** Indicador de estado: cuadrado lleno (activo) o vacío (inactivo). */
@Composable
fun StatusDot(active: Boolean) {
    val m = mono
    Box(Modifier.size(8.dp).then(if (active) Modifier.background(m.fg) else Modifier.border(1.dp, m.faint)))
}

/** Pestañas de período en mono, subrayado de la activa. */
@Composable
fun <T> SegmentRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val m = mono
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Column(Modifier.clickable { onSelect(o) }.padding(vertical = 6.dp)) {
                Text(label(o).uppercase(), style = MaterialTheme.typography.labelMedium, color = if (sel) m.fg else m.faint)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.height(1.5.dp).width(if (sel) 22.dp else 0.dp).background(m.fg))
            }
        }
    }
}

fun Modifier.bottomRule(color: Color) = drawBehind { drawRect(color, Offset(0f, size.height - 1f), Size(size.width, 1f)) }

fun pctText(v: Double?) = v?.let { "${"%.0f".format(it)} %" } ?: "—"

