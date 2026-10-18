package io.github.proify.lyricon.app.history

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Shared raster renderer for the interactive wall and exported image. Mosaic geometry is ported from Halcyon. */
object PosterWallRenderer {
    fun render(context: Context, entries: List<RankedHistory>, title: String, summary: String): Bitmap {
        require(entries.isNotEmpty())
        val geometry = PosterWallGeometry(entries.size.coerceAtMost(96), cell = 124f, gap = 10f)
        val scale = min(1800f / max(geometry.width, 1f), 3200f / max(geometry.height, 1f))
        val margin = 54f
        val width = ceil(geometry.width * scale + margin * 2).toInt()
        val height = ceil(geometry.height * scale + 250).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(12, 14, 20))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD); paint.textSize = 46f
        canvas.drawText(title, margin, 72f, paint)
        paint.textSize = 24f; paint.typeface = Typeface.DEFAULT; paint.color = Color.rgb(172, 178, 194)
        canvas.drawText(ellipsize(summary, paint, width - margin * 2), margin, 118f, paint)
        canvas.save(); canvas.translate(margin, 154f); canvas.scale(scale, scale)
        entries.take(96).forEachIndexed { index, ranked ->
            val placement = geometry.rect(index)
            val rect = RectF(placement.x, placement.y, placement.right, placement.bottom)
            val path = Path().apply { addRoundRect(rect, 13f, 13f, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(path)
            val hue = ((ranked.label + ranked.subtitle).hashCode().toLong() and 0x7fffffff).rem(360).toFloat()
            paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                Color.HSVToColor(floatArrayOf(hue, .5f, .45f)), Color.HSVToColor(floatArrayOf((hue + 35) % 360, .55f, .15f)), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint); paint.shader = null
            val coverFile = ranked.entry.cover?.let { File(context.filesDir, "listening-covers/$it.jpg") }
            val cover = coverFile?.takeIf { it.isFile }?.let { file ->
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
            if (cover != null) {
                try {
                    val ratio = max(rect.width() / cover.width, rect.height() / cover.height)
                    val w = rect.width() / ratio; val h = rect.height() / ratio
                    val source = Rect(((cover.width - w) / 2).toInt(), ((cover.height - h) / 2).toInt(),
                        ((cover.width + w) / 2).toInt(), ((cover.height + h) / 2).toInt())
                    canvas.drawBitmap(cover, source, rect, paint)
                } finally { cover.recycle() }
            } else {
                paint.color = Color.argb(65, 255, 255, 255); paint.textSize = min(rect.width(), rect.height()) * .48f
                paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
                canvas.drawText(ranked.label.take(1).uppercase(), rect.centerX(), rect.centerY() + paint.textSize * .35f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(45, 0, 0, 0), Color.argb(230, 0, 0, 0)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint); paint.shader = null
            paint.color = Color.WHITE; paint.textSize = if (rect.width() < 140) 14f else 21f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(ellipsize(ranked.label, paint, rect.width() - 24), rect.left + 12, rect.bottom - 32, paint)
            paint.color = Color.argb(200, 255, 255, 255); paint.textSize = 11f; paint.typeface = Typeface.DEFAULT
            canvas.drawText(ellipsize(ranked.subtitle, paint, rect.width() - 24), rect.left + 12, rect.bottom - 13, paint)
            canvas.restore()
        }
        canvas.restore()
        paint.shader = null; paint.textSize = 22f; paint.color = Color.rgb(135, 145, 165); paint.typeface = Typeface.DEFAULT
        val footer = context.getSharedPreferences("listening_history", Context.MODE_PRIVATE)
            .getString("poster_footer_text", "").orEmpty().trim().ifEmpty { "LYRICON" }
        canvas.drawText(ellipsize("$footer  /  ${entries.take(96).size}", paint, width - margin * 2), margin, height - 32f, paint)
        return bitmap
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        val length = paint.breakText(text, true, (width - paint.measureText("…")).coerceAtLeast(0f), null)
        return text.take(length) + "…"
    }

    fun png(bitmap: Bitmap, output: OutputStream) {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG export failed" }
    }
}
