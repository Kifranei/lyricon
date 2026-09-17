/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.line.effect.DEFAULT_WAVE_LIFT_EM
import io.github.proify.lyricon.lyric.view.line.effect.EMPHASIS_LIFT_EM
import io.github.proify.lyricon.lyric.view.line.effect.LyricEffectEngine
import io.github.proify.lyricon.lyric.view.line.effect.MIN_STRENGTH
import io.github.proify.lyricon.lyric.view.line.effect.SCALE_OVERFLOW_FRACTION
import io.github.proify.lyricon.lyric.view.line.effect.UnitTransform
import io.github.proify.lyricon.lyric.view.line.model.EmphasisGroup
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import kotlin.math.abs
import kotlin.math.max

internal class TextDrawer(private val effectEngine: LyricEffectEngine) {
    private var bgColors = intArrayOf(Color.GRAY)
    private var hlColors = intArrayOf(Color.WHITE)

    val isRainbowBg get() = bgColors.size > 1
    val isRainbowHl get() = hlColors.size > 1

    private val fontMetrics = Paint.FontMetrics()
    private var baselineOffset = 0f

    /** 文本自然高度（descent - ascent），用于计算空间受限时的动画强度。 */
    private var textHeight = 0f

    private val bgRainbowCache = RainbowGradientCache()
    private val hlRainbowCache = RainbowGradientCache()
    private var cachedAlphaMaskShader: LinearGradient? = null
    private var lastHighlightWidth = -1f
    private var cachedSolidShader: LinearGradient? = null
    private var lastSolidWidth = -1f
    private var lastSolidColor = 0

    /** 当前播放时间（毫秒）；由 [WordSyncRenderer] 在 update/seek 时更新，供时间驱动特效使用。 */
    var currentTimeMs: Long = 0

    /** HDR 亮度比率；大于 1 且系统支持时高亮走扩展色域，使其亮过 SDR 白点。 */
    var hdrHighlightRatio: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            hlRainbowCache.clear()
            cachedSolidShader = null
            lastSolidWidth = -1f
        }

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgColors = background
        if (highlight.isNotEmpty()) hlColors = highlight
    }

    fun updateMetrics(paint: TextPaint) {
        paint.getFontMetrics(fontMetrics)
        baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
        textHeight = fontMetrics.descent - fontMetrics.ascent
    }

    fun clearShaderCache() {
        bgRainbowCache.clear()
        hlRainbowCache.clear()
        cachedAlphaMaskShader = null
        lastHighlightWidth = -1f
        cachedSolidShader = null
        lastSolidWidth = -1f
    }

    fun draw(
        canvas: Canvas,
        model: LyricModel,
        viewWidth: Int,
        viewHeight: Int,
        scrollX: Float,
        isOverflow: Boolean,
        highlightWidth: Float,
        useGradient: Boolean,
        scrollOnly: Boolean,
        charMotionEnabled: Boolean,
        bgPaint: TextPaint,
        hlPaint: TextPaint,
        normPaint: TextPaint
    ) {
        val y = (viewHeight / 2f) + baselineOffset
        canvas.withSave {
            val xOffset = when {
                isOverflow -> scrollX
                model.isAlignedRight -> viewWidth - model.width
                else -> 0f
            }
            translate(xOffset, 0f)

            if (scrollOnly) {
                canvas.drawText(model.wordText, 0f, y, normPaint)
                return@withSave
            }

            if (isRainbowBg) {
                bgPaint.shader = getOrCreateRainbowShader(model.width, bgColors)
            } else {
                bgPaint.shader = null
            }

            if (charMotionEnabled) {
                val bgClipStart = if (useGradient) 0f else highlightWidth
                drawAnimatedUnits(
                    canvas,
                    model,
                    highlightWidth,
                    bgClipStart,
                    Float.MAX_VALUE,
                    viewHeight,
                    y,
                    bgPaint,
                    renderGlow = true
                )
            } else if (!useGradient) {
                canvas.withSave {
                    canvas.clipRect(highlightWidth, 0f, Float.MAX_VALUE, viewHeight.toFloat())
                    canvas.drawText(model.wordText, 0f, y, bgPaint)
                }
            } else {
                canvas.drawText(model.wordText, 0f, y, bgPaint)
            }

            if (highlightWidth > 0f) {
                canvas.withSave {
                    canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())

                    if (useGradient) {
                        val baseShader = if (isRainbowHl) {
                            getOrCreateRainbowShader(model.width, hlColors, hdr = true)
                        } else {
                            getOrCreateSolidGradientShader(model.width, hlColors.first())
                        }
                        val maskShader = getOrCreateAlphaMaskShader(model.width, highlightWidth)
                        hlPaint.shader = ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                    } else {
                        if (isRainbowHl) {
                            hlPaint.shader = getOrCreateRainbowShader(model.width, hlColors, hdr = true)
                        } else {
                            hlPaint.shader = null
                            applyHighlightColor(hlPaint)
                        }
                    }
                    if (charMotionEnabled) {
                        // 渐变模式下背景 pass 已完整绘制辉光，高亮 pass 关闭辉光以
                        // 减半 shadowLayer 绘制开销（视觉一致：光晕不受高亮裁剪）。
                        drawAnimatedUnits(
                            canvas,
                            model,
                            highlightWidth,
                            0f,
                            highlightWidth,
                            viewHeight,
                            y,
                            hlPaint,
                            renderGlow = !useGradient
                        )
                    } else {
                        canvas.drawText(model.wordText, 0f, y, hlPaint)
                    }
                }
            }
        }
    }

    /**
     * 逐单元绘制：每个单元经 [LyricEffectEngine] 求变换后绘制。
     *
     * 为降低 RenderThread 负载，把「位移变换（dx/dy）相同、水平连续、完全落在裁剪区内」的
     * 相邻单元合并为一次 clip + 一次 drawText；带非平凡变换（透明度/缩放）或被裁剪边界
     * 穿过的单元单独绘制，保证与逐单元绘制完全一致。
     *
     * @param clipStart 裁剪区左边界（含）
     * @param clipEnd 裁剪区右边界（不含）
     * @param renderGlow 是否渲染辉光（阴影开销大，仅在需要的 pass 开启）
     */
    private fun drawAnimatedUnits(
        canvas: Canvas,
        model: LyricModel,
        highlightWidth: Float,
        clipStart: Float,
        clipEnd: Float,
        viewHeight: Int,
        baselineY: Float,
        paint: TextPaint,
        renderGlow: Boolean
    ) {
        effectEngine.beginFrame(
            model.width, highlightWidth, paint.textSize, currentTimeMs,
            computeStrength(paint.textSize, viewHeight)
        )

        var segTextStart = 0
        var segTextEnd = 0
        var segX0 = 0f
        var segX1 = 0f
        var segDx = 0f
        var segDy = 0f
        var hasSegment = false

        fun flush() {
            if (!hasSegment) return
            hasSegment = false
            canvas.withSave {
                // 合并段整体带位移（dx/dy），裁剪区按位移后的范围与 pass 边界相交，
                // 避免位移字形被原始边界切边。
                val left = (segX0 + segDx).coerceAtLeast(clipStart)
                val right = (segX1 + segDx).coerceAtMost(clipEnd)
                if (left < right) {
                    clipRect(left, 0f, right, viewHeight.toFloat())
                    drawText(model.wordText, segTextStart, segTextEnd, segX0 + segDx, baselineY + segDy, paint)
                }
            }
        }

        // 返回 false 表示后续单元都在裁剪区右侧之外，可提前结束本 pass。
        fun addUnit(
            textStart: Int,
            textLength: Int,
            x0: Float,
            x1: Float,
            isCjk: Boolean,
            beginMs: Long,
            endMs: Long,
            emphasisGroup: EmphasisGroup?,
            emphasisCharIndex: Int
        ): Boolean {
            if (x0 >= clipEnd) {
                flush()
                return false
            }
            if (x1 <= clipStart) {
                flush()
                return true
            }

            val transform = effectEngine.prepareUnit(
                textStart, textLength, x0, x1, isCjk, beginMs, endMs,
                emphasisGroup, emphasisCharIndex
            )
            val visibleLeft = x0.coerceAtLeast(clipStart)
            val visibleRight = x1.coerceAtMost(clipEnd)
            val fullyVisible = visibleLeft == x0 && visibleRight == x1
            val segmentable = transform.alpha == 1f && transform.scale == 1f &&
                    transform.glowRadius == 0f
            if (!fullyVisible || !segmentable) {
                flush()
                drawUnit(
                    canvas, model.wordText, textStart, textLength, x0, x1,
                    clipStart, clipEnd, viewHeight, baselineY, paint, transform,
                    renderGlow
                )
                return true
            }
            if (hasSegment && transform.dx == segDx && transform.dy == segDy && x0 == segX1) {
                segTextEnd = textStart + textLength
                segX1 = x1
            } else {
                flush()
                hasSegment = true
                segTextStart = textStart
                segTextEnd = textStart + textLength
                segX0 = x0
                segX1 = x1
                segDx = transform.dx
                segDy = transform.dy
            }
            return true
        }

        outer@ for (word in model.words) {
            val isCjk = word.isCjk
            val group = word.emphasisGroup
            if (isCjk || group != null) {
                // 逐字绘制：CJK 词固有策略；参与强调辉光的非 CJK 词也逐字（含错峰）。
                // 空白字符不参与错峰计数（源项目仅对 trim 后的字符建动画元素）。
                var animCharIndex = 0
                for (i in word.chars.indices) {
                    val isSpace = word.chars[i].isWhitespace()
                    val charGroup = if (isSpace) null else group
                    val charGroupIndex = word.emphasisCharOffset + (if (isSpace) 0 else animCharIndex)
                    if (!isSpace) animCharIndex++
                    if (!addUnit(
                            word.textOffset + i, 1,
                            word.charStartPositions[i], word.charEndPositions[i],
                            isCjk, word.begin, word.end,
                            charGroup, charGroupIndex
                        )
                    ) break@outer
                }
            } else {
                if (!addUnit(
                        word.textOffset, word.text.length,
                        word.startPosition, word.endPosition,
                        isCjk, word.begin, word.end, null, 0
                    )
                ) break@outer
            }
        }
        flush()
    }

    /**
     * 计算空间受限时的动画强度。
     *
     * 视图高度不足「完整动画所需高度」（文本自然高度 + 上浮余量 + 缩放溢出）时，
     * 按比例收缩特效幅度（下限 [MIN_STRENGTH]），避免字形被挤出视图边界；
     * 高度充足时保持全强度，动画与 [io.github.proify.lyricon.lyric.view.line.LyricLineView]
     * 预留的动画高度对应。
     */
    private fun computeStrength(textSize: Float, viewHeight: Int): Float {
        if (textHeight <= 0f || viewHeight <= 0) return 1f
        // 与 LyricLineView.onMeasure 的动画高度预留一致：WaveLift 上浮 + 强调上浮
        // + 强调缩放（1.12 上限）的对称溢出。
        val fullHeight = textHeight +
                textSize * (DEFAULT_WAVE_LIFT_EM + EMPHASIS_LIFT_EM) +
                textHeight * SCALE_OVERFLOW_FRACTION
        return (viewHeight / fullHeight).coerceIn(MIN_STRENGTH, 1f)
    }

    /**
     * 单个单元（或被裁剪边界截断的单元）带完整变换绘制：clip → scale → alpha → drawText。
     * 辉光单元左右扩展裁剪区以容纳光晕，并通过 shadowLayer 渲染白色辉光。
     *
     * 裁剪区按**变换后**的字形范围与 pass 边界（[clipStart], [clipEnd]）相交：
     * 缩放（围绕字符中心放大）与水平位移会改变字形实际范围，若仍按原始
     * [x0, x1] 裁剪，放大/位移的字形会被切边，呈现异常变形。
     *
     * @param renderGlow 是否渲染辉光；false 时忽略 [UnitTransform.glowRadius]（如渐变模式下
     * 高亮 pass 的重复辉光），节省每字一次带阴影的 drawText。
     */
    private fun drawUnit(
        canvas: Canvas,
        wordText: String,
        textStart: Int,
        textLength: Int,
        x0: Float,
        x1: Float,
        clipStart: Float,
        clipEnd: Float,
        viewHeight: Int,
        baselineY: Float,
        paint: TextPaint,
        transform: UnitTransform,
        renderGlow: Boolean
    ) {
        val drawX = x0 + transform.dx
        val drawY = baselineY + transform.dy
        val glow = if (renderGlow) transform.glowRadius else 0f

        // 缩放围绕字符中心对称放大：每侧溢出 (scale - 1) * 字宽 / 2。
        val scaleOverflow = if (transform.scale > 1f) (x1 - x0) * (transform.scale - 1f) / 2f else 0f
        val left = (x0 + transform.dx - scaleOverflow).coerceAtLeast(clipStart)
        val right = (x1 + transform.dx + scaleOverflow).coerceAtMost(clipEnd)

        canvas.withSave {
            clipRect(left - glow, 0f, right + glow, viewHeight.toFloat())
            if (transform.scale != 1f) {
                // 围绕「位移后的字符中心」缩放：与源项目「scale 后整体平移」语义一致，
                // 位移不被缩放放大。
                val centerX = (x0 + x1) / 2f
                scale(transform.scale, transform.scale, centerX + transform.dx, drawY)
            }
            val previousAlpha = paint.alpha
            if (transform.alpha != 1f) {
                paint.alpha = (previousAlpha * transform.alpha).toInt()
            }
            var shadowApplied = false
            try {
                if (glow > 0f) {
                    paint.setShadowLayer(
                        glow, 0f, 0f,
                        Color.argb((transform.glowAlpha * 255f).coerceIn(0f, 1f).toInt(), 255, 255, 255)
                    )
                    shadowApplied = true
                }
                drawText(wordText, textStart, textStart + textLength, drawX, drawY, paint)
            } finally {
                if (shadowApplied) paint.clearShadowLayer()
                paint.alpha = previousAlpha
            }
        }
    }

    private fun getOrCreateRainbowShader(
        totalWidth: Float,
        colors: IntArray,
        hdr: Boolean = false
    ): Shader = if (hdr) {
        hlRainbowCache.getOrCreate(totalWidth, colors, hdrHighlightRatio)
    } else {
        bgRainbowCache.getOrCreate(totalWidth, colors)
    }

    /** 无 shader 的高亮直绘路径：HDR 生效时把颜色打包为扩展色域，失败回退 SDR。 */
    @SuppressLint("NewApi")
    private fun applyHighlightColor(paint: TextPaint) {
        val color = hlColors.first()
        if (!RainbowGradientCache.isHdrActive(hdrHighlightRatio)) {
            paint.color = color
            return
        }
        runCatching {
            paint.setColor(HdrColor.packHighlightColor(color, hdrHighlightRatio))
        }.onFailure { paint.color = color }
    }

    private fun getOrCreateAlphaMaskShader(totalWidth: Float, highlightWidth: Float): Shader {
        val edgePosition = max(highlightWidth / totalWidth, 0.9f)
        if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
            cachedAlphaMaskShader = LinearGradient(
                0f, 0f, highlightWidth, 0f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, edgePosition, 1f),
                Shader.TileMode.CLAMP
            )
            lastHighlightWidth = highlightWidth
        }
        return cachedAlphaMaskShader!!
    }

    /** 纯色基底的渐变缓存：只依赖宽度与颜色，变化极少，无需每帧重建。 */
    private fun getOrCreateSolidGradientShader(width: Float, color: Int): Shader {
        if (cachedSolidShader == null || lastSolidWidth != width || lastSolidColor != color) {
            cachedSolidShader = createSolidHdrGradient(width, color) ?: LinearGradient(
                0f, 0f, width, 0f,
                color, color,
                Shader.TileMode.CLAMP
            )
            lastSolidWidth = width
            lastSolidColor = color
        }
        return cachedSolidShader!!
    }

    /** 纯色高亮的 HDR 变体：两端同色的扩展色域渐变；不支持时返回 null 由调用方回退。 */
    private fun createSolidHdrGradient(width: Float, color: Int): LinearGradient? {
        if (!RainbowGradientCache.isHdrActive(hdrHighlightRatio)) return null
        return runCatching {
            val packed = HdrColor.packHighlightColor(color, hdrHighlightRatio)
            LinearGradient(
                0f, 0f, width, 0f,
                longArrayOf(packed, packed), null,
                Shader.TileMode.CLAMP
            )
        }.getOrNull()
    }

}
