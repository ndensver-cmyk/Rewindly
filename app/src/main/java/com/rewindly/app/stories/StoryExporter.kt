package com.rewindly.app.stories

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.ui.theme.StoryPalettes
import java.io.File
import java.io.FileOutputStream

/** Dibuja las escenas en alta resolución (1080×1920) y las comparte como PNG o PDF. */
object StoryExporter {
    private const val W = 1080
    private const val H = 1920

    fun exportPng(context: Context, scene: StoryScene, name: String): File {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), scene)
        val file = File(dir(context), "$name.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun exportPdf(context: Context, scenes: List<StoryScene>, name: String): File {
        val doc = PdfDocument()
        scenes.forEachIndexed { i, s ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            draw(page.canvas, s)
            doc.finishPage(page)
        }
        val file = File(dir(context), "$name.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir informe").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun dir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun draw(c: Canvas, s: StoryScene) {
        val colors = StoryPalettes[s.palette % StoryPalettes.size].map { it.toArgb() }
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, W * 0.8f, H.toFloat(), colors.toIntArray(), null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[2]; alpha = 50 }
        listOf(120f to 300f, 900f to 520f, 760f to 1500f, 200f to 1700f, 980f to 1100f).forEachIndexed { i, (x, y) -> c.drawCircle(x, y, 14f + i * 6, dot) }

        val white = 0xFFFFFFFF.toInt()
        var y = 260f
        val left = 90f
        val width = W - 180
        fun text(t: String, size: Float, bold: Boolean, alpha: Int = 255, maxLines: Int = 6): Float {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white; this.alpha = alpha; textSize = size
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save(); c.translate(left, y); layout.draw(c); c.restore()
            y += layout.height + size * 0.45f
            return y
        }
        text("REWINDLY", 30f, true, 200)
        y += 30
        text(s.eyebrow.uppercase(), 32f, true, 190)
        text(s.title, if (s.kind == SceneKind.COVER) 96f else 68f, true)
        y += 30
        s.bigNumber?.let { text("%,d".format(it).replace(',', '.'), 170f, true, maxLines = 1); s.bigUnit?.let { u -> text(u, 52f, false, 220) } }
        s.bigText?.let { text(it, 104f, true, maxLines = 3) }
        s.items.forEachIndexed { i, it ->
            text("${i + 1}.  ${it.name}", 50f, true, maxLines = 1)
            y -= 12
            text("     " + listOfNotNull(it.subtitle, "${it.plays} plays", StatsEngine.formatMinutes(it.ms)).joinToString(" · "), 34f, false, 190, 1)
        }
        if (s.bars.isNotEmpty() && s.bars.size <= 7) {
            val max = (s.bars.maxOf { it.value }).coerceAtLeast(1)
            s.bars.forEach { b ->
                text("${b.label} · ${StatsEngine.formatMinutes(b.value)}", 38f, true)
                val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; alpha = 40 }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white }
                c.drawRoundRect(RectF(left, y - 10, left + width, y + 18), 14f, 14f, track)
                c.drawRoundRect(RectF(left, y - 10, left + width * (b.value.toFloat() / max), y + 18), 14f, 14f, fill)
                y += 60
            }
        }
        s.facts.forEach { (k, v) ->
            text(k.uppercase(), 30f, true, 180, 1); y -= 10
            text(v, 56f, true, maxLines = 2)
        }
        s.body?.let { y += 20; text(it, 42f, false, 230, 8) }

        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = white; alpha = 150; textSize = 28f }
        c.drawText("Generado localmente con Rewindly · No afiliado a Spotify", left, H - 90f, footer)
    }
}
