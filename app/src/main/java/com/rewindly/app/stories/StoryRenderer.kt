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
import com.rewindly.app.ui.theme.StoryStyles
import com.rewindly.app.ui.theme.Serif
import com.rewindly.app.ui.theme.MonoFont
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
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

    val fg = StoryStyles[scenes[pager.currentPage.coerceIn(0, scenes.lastIndex)].palette % StoryStyles.size].fg
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
                    Box(Modifier.weight(1f).height(2.dp).background(fg.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(fg))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("REWINDLY", color = fg, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp), modifier = Modifier.weight(1f))
                IconButton(onClick = { onExportPng(pager.currentPage) }) { Icon(Icons.Default.Image, "Exportar imagen", tint = fg) }
                IconButton(onClick = onExportPdf) { Icon(Icons.Default.PictureAsPdf, "Exportar PDF", tint = fg) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = fg) }
            }
        }
    }
}

private val LocalStoryFg = staticCompositionLocalOf { Color.White }

@Composable
fun SceneView(scene: StoryScene, visible: Boolean) {
    val style = StoryStyles[scene.palette % StoryStyles.size]
    val fg = style.fg
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "phase")

    CompositionLocalProvider(LocalStoryFg provides fg) {
    Box(
        Modifier.fillMaxSize().background(style.bg).background(
            Brush.radialGradient(
                listOf(fg.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(200f + 700f * phase, 300f + 1200f * phase), radius = 1100f
            )
        )
    ) {
        Grooves(fg, phase)
        Particles(fg)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 72.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Reveal(visible, 0) { Text(scene.eyebrow.uppercase(), color = fg.copy(0.7f), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp)) }
            Spacer(Modifier.height(10.dp))
            Reveal(visible, 150) {
                Text(scene.title, color = fg, fontFamily = Serif, fontSize = if (scene.kind == SceneKind.COVER) 58.sp else 38.sp,
                    lineHeight = if (scene.kind == SceneKind.COVER) 56.sp else 38.sp, letterSpacing = (-0.8).sp)
            }
            Spacer(Modifier.height(24.dp))
            scene.bigNumber?.let { n ->
                Reveal(visible, 300) {
                    Column {
                        AnimatedCounter(n, visible)
                        scene.bigUnit?.let { Text(it.uppercase(), color = fg.copy(0.75f), fontFamily = MonoFont, fontSize = 14.sp, letterSpacing = 2.sp) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            scene.bigText?.let {
                Reveal(visible, 300) {
                    Text(it, color = fg, fontFamily = Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 54.sp, lineHeight = 54.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
            }
            if (scene.items.isNotEmpty()) {
                scene.items.forEachIndexed { i, item ->
                    Reveal(visible, 420 + i * 120) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(i + 1), color = fg.copy(if (i == 0) 1f else 0.55f), fontFamily = MonoFont, fontSize = 14.sp, modifier = Modifier.width(30.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = fg, fontFamily = Serif, fontSize = if (i == 0) 30.sp else 24.sp, lineHeight = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(item.subtitle, "${item.plays} plays", StatsEngine.formatMinutes(item.ms)).joinToString(" · "),
                                    color = fg.copy(0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
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
                                Column(Modifier.weight(1f).border(1.dp, fg.copy(0.25f)).padding(14.dp)) {
                                    Text(k.uppercase(), color = fg.copy(0.65f), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(6.dp))
                                    Text(v, color = fg, fontFamily = Serif, fontSize = 24.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            scene.body?.let {
                Reveal(visible, 700) { Text(it, color = fg.copy(0.85f), fontSize = 16.sp, lineHeight = 23.sp) }
            }
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
    val fg = LocalStoryFg.current
    Text("%,d".format(value).replace(',', '.'), color = fg, fontFamily = Serif, fontSize = 96.sp, lineHeight = 92.sp, letterSpacing = (-2).sp)
}

@Composable
private fun GrowingBars(bars: List<com.rewindly.app.analytics.NamedValue>, visible: Boolean, showLabels: Boolean) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) { anim.snapTo(0f); anim.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) } }
    val max = (bars.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    val fg = LocalStoryFg.current
    if (showLabels) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bars.forEach { b ->
                Column {
                    Row { Text(b.label, color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(StatsEngine.formatMinutes(b.value), color = fg.copy(0.75f), fontFamily = MonoFont, fontSize = 13.sp) }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(fg.copy(0.12f))) {
                        Box(Modifier.fillMaxWidth((b.value.toFloat() / max) * anim.value).fillMaxHeight().background(fg))
                    }
                }
            }
        }
    } else {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width / bars.size
            bars.forEachIndexed { i, b ->
                val h = size.height * (b.value.toFloat() / max) * anim.value
                drawRect(
                    color = fg.copy(alpha = 0.85f),
                    topLeft = Offset(i * w + w * 0.15f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h.coerceAtLeast(2f))
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
                val side = 2f + s * 6f
                drawRect(
                    color = tint.copy(alpha = 0.06f + s * 0.14f),
                    topLeft = Offset(xx * size.width, yy * size.height),
                    size = androidx.compose.ui.geometry.Size(side, side)
                )
            }
        }
    }
}

/** Surcos de vinilo: círculos concéntricos finos que respiran lentamente en una esquina. */
@Composable
private fun Grooves(fg: Color, phase: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width * 1.05f, size.height * 0.92f)
        for (i in 1..14) {
            drawCircle(
                color = fg.copy(alpha = 0.05f + (i % 3) * 0.015f),
                radius = i * 46f + phase * 30f,
                center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )
        }
    }
}
