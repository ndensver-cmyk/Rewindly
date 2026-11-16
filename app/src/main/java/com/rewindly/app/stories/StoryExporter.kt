package com.rewindly.app.stories

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.rewindly.app.analytics.StatsEngine
import com.rewindly.app.ui.theme.StoryStyles
import com.rewindly.app.R
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream

/** Dibuja las escenas en alta resolución (1080×1920) y las comparte como PNG o PDF. */
object StoryExporter {
    private const val W = 1080
    private const val H = 1920

    fun exportPng(context: Context, scene: StoryScene, name: String): File {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(context, Canvas(bmp), scene)
        val file = File(dir(context), "$name.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun exportPdf(context: Context, scenes: List<StoryScene>, name: String): File {
        val doc = PdfDocument()
        scenes.forEachIndexed { i, s ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            draw(context, page.canvas, s)
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

    private class Faces(val serif: Typeface, val serifItalic: Typeface, val sans: Typeface, val mono: Typeface)

    private var faces: Faces? = null
    private fun faces(context: Context): Faces = faces ?: Faces(
        ResourcesCompat.getFont(context, R.font.instrument_serif) ?: Typeface.SERIF,
        ResourcesCompat.getFont(context, R.font.instrument_serif_italic) ?: Typeface.SERIF,
        ResourcesCompat.getFont(context, R.font.inter_tight) ?: Typeface.DEFAULT,
        ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    ).also { faces = it }

    private fun draw(context: Context, c: Canvas, s: StoryScene) {
        val f = faces(context)
        val style = StoryStyles[s.palette % StoryStyles.size]
        val bgc = style.bg.toArgb(); val fgc = style.fg.toArgb()
        c.drawColor(bgc)

        // Surcos de vinilo en la esquina inferior derecha
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgc; alpha = 22; this.style = Paint.Style.STROKE; strokeWidth = 2f }
        for (i in 1..16) c.drawCircle(W * 1.05f, H * 0.92f, i * 70f, groove)

        var y = 220f
        val left = 96f
        val width = W - 192
        fun text(t: String, size: Float, face: Typeface, alpha: Int = 255, maxLines: Int = 6, spacing: Float = 0f, lineMult: Float = 1f): Float {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = fgc; this.alpha = alpha; textSize = size; typeface = face; letterSpacing = spacing }
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines).setLineSpacing(0f, lineMult)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save(); c.translate(left, y); layout.draw(c); c.restore()
            y += layout.height + size * 0.4f
            return y
        }
        val rule = Paint().apply { color = fgc; alpha = 70 }
        text("REWINDLY", 30f, f.mono, 255, spacing = 0.2f)
        c.drawRect(left, y, left + width, y + 2f, rule); y += 60
        text(s.eyebrow.uppercase(), 30f, f.mono, 180, spacing = 0.12f)
        text(s.title, if (s.kind == SceneKind.COVER) 128f else 92f, f.serif, lineMult = 0.92f)
        y += 30
        s.bigNumber?.let { text("%,d".format(it).replace(',', '.'), 220f, f.serif, maxLines = 1); s.bigUnit?.let { u -> text(u.uppercase(), 34f, f.mono, 190, spacing = 0.15f) } }
        s.bigText?.let { text(it, 124f, f.serifItalic, maxLines = 3, lineMult = 0.92f) }
        s.items.forEachIndexed { i, it ->
            text("%02d   ".format(i + 1) + it.name, if (i == 0) 66f else 54f, f.serif, maxLines = 1); y -= 14
            text("       " + listOfNotNull(it.subtitle, "${it.plays} plays", StatsEngine.formatMinutes(it.ms)).joinToString(" · "), 30f, f.sans, 170, 1)
            y += 6
        }
        if (s.bars.isNotEmpty() && s.bars.size <= 7) {
            val max = (s.bars.maxOf { it.value }).coerceAtLeast(1)
            s.bars.forEach { b ->
                text("${b.label} · ${StatsEngine.formatMinutes(b.value)}", 36f, f.sans)
                val track = Paint().apply { color = fgc; alpha = 36 }
                val fill = Paint().apply { color = fgc }
                c.drawRect(RectF(left, y - 12, left + width, y + 4), track)
                c.drawRect(RectF(left, y - 12, left + width * (b.value.toFloat() / max), y + 4), fill)
                y += 56
            }
        }
        s.facts.forEach { (k, v) ->
            c.drawRect(left, y, left + width, y + 1.5f, rule); y += 26
            text(k.uppercase(), 26f, f.mono, 170, 1, spacing = 0.12f); y -= 8
            text(v, 64f, f.serif, maxLines = 2)
        }
        s.body?.let { y += 20; text(it, 40f, f.sans, 220, 8, lineMult = 1.1f) }

        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = fgc; alpha = 140; textSize = 24f; typeface = f.mono; letterSpacing = 0.1f }
        c.drawRect(left, H - 140f, left + width, H - 138.5f, rule)
        c.drawText("GENERADO LOCALMENTE CON REWINDLY · NO AFILIADO A SPOTIFY", left, H - 96f, footer)
    }
}
