package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import kotlin.math.abs
import kotlin.math.max

/**
 * 歌词行渲染控制器
 * 负责单行歌词的状态管理、动画驱动及复杂的着色器渲染逻辑。
 */
class Syllable(private val view: LyricLineView) {

    private val backgroundPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val renderDelegate: LineRenderDelegate =
        if (Build.VERSION.SDK_INT >= 29) HardwareRenderer() else SoftwareRenderer()

    private val textRenderer = LineTextRenderer()
    private val progressAnimator = ProgressAnimator()
    private val scrollController = ScrollController()

    var lastPosition = Long.MIN_VALUE
        private set

    var playListener: LyricPlayListener? = null

    private val rainbowColor = RainbowColor(
        background = intArrayOf(0),
        highlight = intArrayOf(0)
    )

    val isRainbowHighlight get() = rainbowColor.highlight.size > 1
    val isRainbowBackground get() = rainbowColor.background.size > 1

    var isGradientEnabled: Boolean = true
        set(value) {
            field = value
            renderDelegate.isGradientEnabled = value
            renderDelegate.invalidate()
        }

    var isScrollOnly: Boolean = false
        set(value) {
            field = value
            renderDelegate.isOnlyScrollMode = value
            renderDelegate.invalidate()
        }

    val textSize: Float get() = backgroundPaint.textSize
    val isStarted: Boolean get() = progressAnimator.hasStarted
    val isPlaying: Boolean get() = progressAnimator.isAnimating
    val isFinished: Boolean get() = progressAnimator.hasFinished

    init {
        updateLayoutMetrics()
    }

    private data class RainbowColor(
        var background: IntArray,
        var highlight: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RainbowColor) return false
            return background.contentEquals(other.background) && highlight.contentEquals(other.highlight)
        }

        override fun hashCode(): Int =
            31 * background.contentHashCode() + highlight.contentHashCode()
    }

    fun setColor(background: IntArray, highlight: IntArray) {
        if (background.isEmpty() || highlight.isEmpty()) return
        if (!rainbowColor.background.contentEquals(background) || !rainbowColor.highlight.contentEquals(
                highlight
            )
        ) {
            backgroundPaint.color = background[0]
            highlightPaint.color = highlight[0]
            rainbowColor.background = background
            rainbowColor.highlight = highlight
            textRenderer.clearShaderCache()
            renderDelegate.invalidate()
        }
    }

    fun setTextSize(size: Float) {
        if (backgroundPaint.textSize != size) {
            backgroundPaint.textSize = size
            highlightPaint.textSize = size
            reLayout()
        }
    }

    fun reLayout() {
        textRenderer.updateMetrics(backgroundPaint)
        if (isFinished) progressAnimator.jumpTo(view.lyricWidth)
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
        scrollController.update(progressAnimator.currentWidth, view)
        renderDelegate.invalidate()
        view.invalidate()
    }

    fun setTypeface(typeface: Typeface?) {
        if (backgroundPaint.typeface != typeface) {
            backgroundPaint.typeface = typeface
            highlightPaint.typeface = typeface
            textRenderer.updateMetrics(backgroundPaint)
            renderDelegate.invalidate()
        }
    }

    fun reset() {
        progressAnimator.reset()
        scrollController.reset(view)
        lastPosition = Long.MIN_VALUE
        renderDelegate.onHighlightUpdate(0f)
    }

    fun seek(position: Long) {
        val targetWidth = calculateTargetWidth(position)
        progressAnimator.jumpTo(targetWidth)
        scrollController.update(targetWidth, view)
        renderDelegate.onHighlightUpdate(targetWidth)
        lastPosition = position
        notifyProgressUpdate()
    }

    fun updateProgress(position: Long) {
        if (lastPosition != Long.MIN_VALUE && position < lastPosition) {
            seek(position)
            return
        }
        val model = view.lyric
        val currentWord = model.wordTimingNavigator.first(position)
        val targetWidth = calculateTargetWidth(position, currentWord)

        if (currentWord != null && progressAnimator.currentWidth == 0f) {
            currentWord.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }
        if (targetWidth != progressAnimator.targetWidth) {
            progressAnimator.start(targetWidth, currentWord?.duration ?: 0)
        }
        lastPosition = position
    }

    fun onFrameUpdate(nanoTime: Long): Boolean {
        if (progressAnimator.step(nanoTime)) {
            scrollController.update(progressAnimator.currentWidth, view)
            renderDelegate.onHighlightUpdate(progressAnimator.currentWidth)
            notifyProgressUpdate()
            return true
        }
        return false
    }

    fun draw(canvas: Canvas) {
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
        renderDelegate.draw(canvas, view.scrollXOffset)
    }

    private fun updateLayoutMetrics() {
        textRenderer.updateMetrics(backgroundPaint)
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
    }

    private fun calculateTargetWidth(
        pos: Long,
        word: WordModel? = view.lyric.wordTimingNavigator.first(pos)
    ): Float = when {
        word != null -> word.endPosition
        pos >= view.lyric.end -> view.lyricWidth
        pos <= view.lyric.begin -> 0f
        else -> progressAnimator.currentWidth
    }

    private fun notifyProgressUpdate() {
        val current = progressAnimator.currentWidth
        val total = view.lyricWidth
        if (!progressAnimator.hasStarted && current > 0f) {
            progressAnimator.hasStarted = true
            playListener?.onPlayStarted(view)
        }
        if (!progressAnimator.hasFinished && current >= total) {
            progressAnimator.hasFinished = true
            playListener?.onPlayEnded(view)
        }
        playListener?.onPlayProgress(view, total, current)
    }

    // --- 内部组件 ---

    private class ProgressAnimator {
        var currentWidth = 0f
        var targetWidth = 0f
        var isAnimating = false
        var hasStarted = false
        var hasFinished = false
        private var startWidth = 0f
        private var startTimeNano = 0L
        private var durationNano = 1L

        fun reset() {
            currentWidth = 0f; targetWidth = 0f; isAnimating = false
            hasStarted = false; hasFinished = false
        }

        fun jumpTo(width: Float) {
            currentWidth = width; targetWidth = width; isAnimating = false
        }

        fun start(target: Float, durationMs: Long) {
            startWidth = currentWidth
            targetWidth = target
            durationNano = max(1L, durationMs) * 1_000_000L
            startTimeNano = System.nanoTime()
            isAnimating = true
        }

        fun step(now: Long): Boolean {
            if (!isAnimating) return false
            val elapsed = (now - startTimeNano).coerceAtLeast(0L)
            if (elapsed >= durationNano) {
                currentWidth = targetWidth
                isAnimating = false
                return true
            }
            val progress = elapsed.toFloat() / durationNano
            currentWidth = startWidth + (targetWidth - startWidth) * progress
            return true
        }
    }

    private class ScrollController {
        fun reset(v: LyricLineView) {
            v.scrollXOffset = 0f
            v.isScrollFinished = false
        }

        fun update(currentX: Float, v: LyricLineView) {
            val lyricW = v.lyricWidth
            val viewW = v.measuredWidth.toFloat()
            if (lyricW <= viewW) {
                v.scrollXOffset = 0f
                return
            }
            val minScroll = -(lyricW - viewW)
            if (v.isPlayFinished()) {
                v.scrollXOffset = minScroll
                v.isScrollFinished = true
                return
            }
            val halfWidth = viewW / 2f
            if (currentX > halfWidth) {
                v.scrollXOffset = (halfWidth - currentX).coerceIn(minScroll, 0f)
                v.isScrollFinished = v.scrollXOffset <= minScroll
            } else {
                v.scrollXOffset = 0f
            }
        }
    }

    private interface LineRenderDelegate {
        var isGradientEnabled: Boolean
        var isOnlyScrollMode: Boolean
        fun onLayout(width: Int, height: Int, overflow: Boolean)
        fun onHighlightUpdate(highlightWidth: Float)
        fun invalidate()
        fun draw(canvas: Canvas, scrollX: Float)
    }

    private inner class SoftwareRenderer : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f
        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            this@SoftwareRenderer.width = width; this@SoftwareRenderer.height =
                height; this@SoftwareRenderer.overflow = overflow
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            this@SoftwareRenderer.highlightWidth = highlightWidth
        }
        override fun invalidate() {}
        override fun draw(canvas: Canvas, scrollX: Float) {
            textRenderer.draw(
                canvas,
                view.lyric,
                width,
                height,
                scrollX,
                overflow,
                highlightWidth,
                isGradientEnabled,
                isOnlyScrollMode,
                backgroundPaint,
                highlightPaint,
                view.textPaint
            )
        }
    }

    @RequiresApi(29)
    private inner class HardwareRenderer : LineRenderDelegate {
        override var isGradientEnabled = true
        override var isOnlyScrollMode = false
        private val renderNode = RenderNode("LyricLine").apply { clipToBounds = false }
        private var width = 0
        private var height = 0
        private var overflow = false
        private var highlightWidth = 0f
        private var isDirty = true
        override fun invalidate() {
            isDirty = true
        }

        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            if (this@HardwareRenderer.width != width || this@HardwareRenderer.height != height || this@HardwareRenderer.overflow != overflow) {
                this@HardwareRenderer.width = width; this@HardwareRenderer.height =
                    height; this@HardwareRenderer.overflow = overflow
                renderNode.setPosition(
                    0, 0, this@HardwareRenderer.width,
                    this@HardwareRenderer.height
                )
                isDirty = true
            }
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            if (abs(this@HardwareRenderer.highlightWidth - highlightWidth) > 0.1f) {
                this@HardwareRenderer.highlightWidth = highlightWidth; isDirty = true
            }
        }
        override fun draw(canvas: Canvas, scrollX: Float) {
            if (isDirty) {
                val rc = renderNode.beginRecording(width, height)
                textRenderer.draw(
                    rc,
                    view.lyric,
                    width,
                    height,
                    scrollX,
                    overflow,
                    highlightWidth,
                    isGradientEnabled,
                    isOnlyScrollMode,
                    backgroundPaint,
                    highlightPaint,
                    view.textPaint
                )
                renderNode.endRecording()
                isDirty = false
            }
            canvas.drawRenderNode(renderNode)
        }
    }

    /**
     * 文本渲染器
     * 优化点：使用 ComposeShader 解决彩虹色随进度挤压的问题。
     */
    private inner class LineTextRenderer {
        private val minEdgePosition = 0.9f
        private val fontMetrics = Paint.FontMetrics()
        private var baselineOffset = 0f

        private var cachedRainbowShader: LinearGradient? = null
        private var cachedAlphaMaskShader: LinearGradient? = null
        private var lastTotalWidth = -1f
        private var lastHighlightWidth = -1f
        private var lastColorsHash = 0

        fun updateMetrics(paint: TextPaint) {
            paint.getFontMetrics(fontMetrics)
            baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
        }

        fun clearShaderCache() {
            cachedRainbowShader = null
            cachedAlphaMaskShader = null
            lastTotalWidth = -1f
        }

        fun draw(
            canvas: Canvas, model: LyricModel, viewWidth: Int, viewHeight: Int,
            scrollX: Float, isOverflow: Boolean, highlightWidth: Float,
            useGradient: Boolean, scrollOnly: Boolean, bgPaint: TextPaint,
            hlPaint: TextPaint, normPaint: TextPaint
        ) {
            val y = (viewHeight / 2f) + baselineOffset
            canvas.withSave {
                val xOffset =
                    if (isOverflow) scrollX else if (model.isAlignedRight) viewWidth - model.width else 0f
                translate(xOffset, 0f)

                if (scrollOnly) {
                    canvas.drawText(model.wordText, 0f, y, normPaint)
                    return@withSave
                }

                // 1. 绘制背景层 (可能是静止的彩虹)
                if (isRainbowBackground) {
                    bgPaint.shader = getOrCreateRainbowShader(model.width, rainbowColor.background)
                } else {
                    bgPaint.shader = null
                }

                // 非羽化模式背景需要裁剪掉高亮区
                if (!useGradient) {
                    canvas.withSave {
                        canvas.clipRect(highlightWidth, 0f, Float.MAX_VALUE, viewHeight.toFloat())
                        canvas.drawText(model.wordText, 0f, y, bgPaint)
                    }
                } else {
                    canvas.drawText(model.wordText, 0f, y, bgPaint)
                }

                // 2. 绘制高亮层
                if (highlightWidth > 0f) {
                    canvas.withSave {
                        // 裁剪高亮区域
                        canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())

                        if (useGradient) {
                            // 羽化模式：通过 ComposeShader 结合【固定比例彩虹】+【随进度移动的透明遮罩】
                            val baseShader = if (isRainbowHighlight) {
                                getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                            } else {
                                // 单色高亮转为 Shader 方便混合
                                LinearGradient(
                                    0f,
                                    0f,
                                    model.width,
                                    0f,
                                    hlPaint.color,
                                    hlPaint.color,
                                    Shader.TileMode.CLAMP
                                )
                            }

                            val maskShader = getOrCreateAlphaMaskShader(highlightWidth)
                            hlPaint.shader =
                                ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                        } else {
                            // 非羽化模式：直接裁剪，颜色位置天然正确
                            if (isRainbowHighlight) {
                                hlPaint.shader =
                                    getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                            } else {
                                hlPaint.shader = null
                            }
                        }
                        canvas.drawText(model.wordText, 0f, y, hlPaint)
                    }
                }
            }
        }

        /**
         * 获取或创建彩虹着色器。
         * 关键：宽度固定为 totalWidth，确保颜色分布在整行歌词上是恒定的。
         */
        private fun getOrCreateRainbowShader(totalWidth: Float, colors: IntArray): Shader {
            val colorsHash = colors.contentHashCode()
            if (cachedRainbowShader == null || lastTotalWidth != totalWidth || lastColorsHash != colorsHash) {
                cachedRainbowShader = LinearGradient(
                    0f, 0f, totalWidth, 0f,
                    colors, null, Shader.TileMode.CLAMP
                )
                lastTotalWidth = totalWidth
                lastColorsHash = colorsHash
            }
            return cachedRainbowShader!!
        }

        /**
         * 获取或创建透明度遮罩。
         * 关键：它负责高亮边缘 90% -> 100% 的淡出效果。
         */
        private fun getOrCreateAlphaMaskShader(highlightWidth: Float): Shader {
            if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
                // 使用从不透明到透明的渐变
                cachedAlphaMaskShader = LinearGradient(
                    0f, 0f, highlightWidth, 0f,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, minEdgePosition, 1f),
                    Shader.TileMode.CLAMP
                )
                lastHighlightWidth = highlightWidth
            }
            return cachedAlphaMaskShader!!
        }
    }
}