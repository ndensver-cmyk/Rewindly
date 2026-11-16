package com.rewindly.app.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.ui.theme.StoryPalettes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Visor de historias: deslizar horizontalmente o tocar los lados para avanzar. */
@Composable
fun StoryViewer(
    scenes: List<StoryScene>,
    onClose: () -> Unit,
    onExportPng: (Int) -> Unit,
    onExportPdf: () -> Unit
) {
    val pager = rememberPagerState { scenes.size }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }

    // Avance automático con barra de progreso, como una presentación
    LaunchedEffect(pager.currentPage, paused) {
        if (paused) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(7000, easing = LinearEasing))
        if (pager.currentPage < scenes.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            SceneView(scenes[page], visible = page == pager.currentPage)
        }
        // Zonas táctiles: izquierda = anterior, derecha = siguiente, mantener = pausa
        Row(Modifier.fillMaxSize().padding(top = 90.dp, bottom = 90.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                    onPress = { paused = true; tryAwaitRelease(); paused = false }
                )
            })
            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(scenes.lastIndex)) } },
                    onPress = { paused = true; tryAwaitRelease(); paused = false }
                )
            })
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                scenes.indices.forEach { i ->
                    val f = when {
                        i < pager.currentPage -> 1f
                        i == pager.currentPage -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("REWINDLY", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onExportPng(pager.currentPage) }) { Icon(Icons.Default.Image, "Exportar imagen", tint = Color.White) }
                IconButton(onClick = onExportPdf) { Icon(Icons.Default.PictureAsPdf, "Exportar PDF", tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
            }
        }
    }
}

@Composable
fun SceneView(scene: StoryScene, visible: Boolean) {
    val colors = StoryPalettes[scene.palette % StoryPalettes.size]
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "phase")

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(colors[0], lerp(colors[1], colors[0], phase * 0.5f), lerp(colors[2], colors[1], phase)),
                start = Offset(0f, 0f), end = Offset(800f * (1 + phase), 2200f)
            )
        )
    ) {
        Particles(colors[2])
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 72.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Reveal(visible, 0) { Text(scene.eyebrow.uppercase(), color = Color.White.copy(0.75f), style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.height(10.dp))
            Reveal(visible, 150) {
                Text(scene.title, color = Color.White, fontSize = if (scene.kind == SceneKind.COVER) 44.sp else 30.sp,
                    fontWeight = FontWeight.Black, lineHeight = if (scene.kind == SceneKind.COVER) 48.sp else 34.sp)
            }
            Spacer(Modifier.height(24.dp))
            scene.bigNumber?.let { n ->
                Reveal(visible, 300) {
                    Column {
                        AnimatedCounter(n, visible)
                        scene.bigUnit?.let { Text(it, color = Color.White.copy(0.85f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            scene.bigText?.let {
                Reveal(visible, 300) {
                    Text(it, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black, lineHeight = 50.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
            }
            if (scene.items.isNotEmpty()) {
                scene.items.forEachIndexed { i, item ->
                    Reveal(visible, 420 + i * 120) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.16f)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(item.subtitle, "${item.plays} plays", StatsEngine.formatMinutes(item.ms)).joinToString(" · "),
                                    color = Color.White.copy(0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (scene.bars.isNotEmpty()) {
                Reveal(visible, 450) { GrowingBars(scene.bars, visible, showLabels = scene.bars.size <= 7) }
                Spacer(Modifier.height(16.dp))
            }
            if (scene.facts.isNotEmpty()) {
                scene.facts.chunked(2).forEachIndexed { row, pair ->
                    Reveal(visible, 350 + row * 140) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { (k, v) ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.12f)).padding(14.dp)) {
                                    Text(k.uppercase(), color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelSmall)
                                    Text(v, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            scene.body?.let {
                Reveal(visible, 700) { Text(it, color = Color.White.copy(0.9f), fontSize = 17.sp, lineHeight = 24.sp) }
            }
        }
    }
}

/** Aparición cinematográfica: desvanecido + desplazamiento con retardo escalonado. */
@Composable
private fun Reveal(visible: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(delayMs.toLong()); show = true } else show = false
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(650)) + slideInVertically(tween(650, easing = FastOutSlowInEasing)) { it / 3 }
    ) { content() }
}

@Composable
private fun AnimatedCounter(target: Long, visible: Boolean) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible, target) {
        if (visible) { anim.snapTo(0f); anim.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }
    }
    val value = (target * anim.value).toLong()
    Text("%,d".format(value).replace(',', '.'), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black, lineHeight = 76.sp)
}

@Composable
private fun GrowingBars(bars: List<com.rewindly.app.analytics.NamedValue>, visible: Boolean, showLabels: Boolean) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) { anim.snapTo(0f); anim.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) } }
    val max = (bars.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    if (showLabels) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bars.forEach { b ->
                Column {
                    Row { Text(b.label, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(StatsEngine.formatMinutes(b.value), color = Color.White.copy(0.8f)) }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Color.White.copy(0.12f))) {
                        Box(Modifier.fillMaxWidth((b.value.toFloat() / max) * anim.value).fillMaxHeight().clip(CircleShape).background(Color.White))
                    }
                }
            }
        }
    } else {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width / bars.size
            bars.forEachIndexed { i, b ->
                val h = size.height * (b.value.toFloat() / max) * anim.value
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(i * w + w * 0.15f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h.coerceAtLeast(2f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.3f)
                )
            }
        }
    }
}

/** Partículas discretas que flotan lentamente. */
@Composable
private fun Particles(tint: Color) {
    val seeds = remember { List(26) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    val t = rememberInfiniteTransition(label = "particles")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "time")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            seeds.forEach { (x, y, s) ->
                val yy = ((y - time * (0.2f + s * 0.3f)) % 1f + 1f) % 1f
                val xx = x + 0.03f * sin((time * 2 * PI + s * 10).toFloat())
                drawCircle(
                    color = tint.copy(alpha = 0.10f + s * 0.18f),
                    radius = 3f + s * 9f,
                    center = Offset(xx * size.width, yy * size.height)
                )
            }
        }
    }
}
