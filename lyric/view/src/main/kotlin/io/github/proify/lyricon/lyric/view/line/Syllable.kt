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
import android.os.SystemClock
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
    companion object {
        private const val SUSTAIN_EFFECT_MIN_DURATION_MS = 420L
        private const val SUSTAIN_EFFECT_TRIGGER_DELAY_MS = 260L
        private const val SUSTAIN_EFFECT_TRIGGER_MAX_RATIO = 0.42f
        private const val SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP = 3.4f
        private const val SUSTAIN_EFFECT_MAX_GLOW_ALPHA = 160
        private const val SUSTAIN_EFFECT_RELEASE_DURATION_MS = 120L

    }

    private data class SustainEffectState(
        val startX: Float,
        val endX: Float,
        val liftOffsetPx: Float,
        val glowRadiusPx: Float,
        val glowAlpha: Int,
        val intensity: Float,
        val isReleasePhase: Boolean = false
    )

    private val backgroundPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val renderDelegate: LineRenderDelegate =
        if (Build.VERSION.SDK_INT >= 29) HardwareRenderer() else SoftwareRenderer()

    private val textRenderer = LineTextRenderer()
    private val progressAnimator = ProgressAnimator()
    private val scrollController = ScrollController()
    private val charAnimator = CharAnimator()

    var lastPosition = Long.MIN_VALUE
        private set

    var playListener: LyricPlayListener? = null
    private var activeSustainWord: WordModel? = null
    private var activeSustainIntensity = 0f
    private var activeSustainPeakIntensity = 0f
    private var releaseSustainWord: WordModel? = null
    private var releaseStartRealtimeMs = 0L
    private var releaseSeedIntensity = 0f

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

    var isSustainLiftEnabled: Boolean = false
        set(value) {
            field = value
            renderDelegate.invalidate()
        }

    var isSustainGlowEnabled: Boolean = false
        set(value) {
            field = value
            renderDelegate.invalidate()
        }

    var isCharFloatAnimationEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                charAnimator.reset()
            }
        }

    var isSharpenEnabled: Boolean = false
        set(value) {
            field = value
            renderDelegate.invalidate()
        }

    var sharpenIntensity: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 2.0f)
            renderDelegate.invalidate()
        }

    val textSize: Float get() = backgroundPaint.textSize
    val isStarted: Boolean get() = progressAnimator.hasStarted
    val isPlaying: Boolean get() = progressAnimator.isAnimating
    val isFinished: Boolean get() = progressAnimator.hasFinished
    val isCharAnimActive: Boolean get() = charAnimator.hasActiveAnimation

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

        if (!rainbowColor.background.contentEquals(background) || !rainbowColor.highlight.contentEquals(highlight)) {
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
        charAnimator.reset()
        lastPosition = Long.MIN_VALUE
        resetSustainState()
        renderDelegate.onHighlightUpdate(0f)
        renderDelegate.onSustainEffectUpdate(emptyList())
    }

    fun seek(position: Long) {
        // 重置字符动画，确保进度条拖动时动画状态干净
        charAnimator.reset()
        
        val currentWord = view.lyric.wordTimingNavigator.first(position)
        val targetWidth = calculateCurrentWidth(position)
        progressAnimator.jumpTo(targetWidth)
        scrollController.update(targetWidth, view)
        renderDelegate.onHighlightUpdate(targetWidth)
        resetSustainState()
        renderDelegate.onSustainEffectUpdate(buildSustainEffects(currentWord, position))
        
        // 清除字符动画状态
        renderDelegate.onCharAnimUpdate(null, null)
        
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
        val targetWidth = if (currentWord != null) currentWord.endPosition else calculateCurrentWidth(position)

        if (currentWord != null && progressAnimator.currentWidth == 0f) {
            currentWord.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }

        // targetWidth发生变化或animator未运行（例如seek后），需要重新启动动画
        if (targetWidth != progressAnimator.targetWidth || !progressAnimator.isAnimating) {
            val duration = if (currentWord != null) {
                (currentWord.end - position).coerceAtLeast(0)
            } else 0L

            if (duration > 0) {
                progressAnimator.start(targetWidth, duration)
            } else {
                progressAnimator.jumpTo(targetWidth)
            }
        }
        renderDelegate.onSustainEffectUpdate(buildSustainEffects(currentWord, position))
        lastPosition = position
    }

    fun onFrameUpdate(nanoTime: Long): Boolean {
        val progressUpdated = progressAnimator.step(nanoTime)
        if (progressUpdated) {
            scrollController.update(progressAnimator.currentWidth, view)
            renderDelegate.onHighlightUpdate(progressAnimator.currentWidth)
        }

        // 更新字符动画（仅在启用时）
        var charAnimUpdated = false
        if (isCharFloatAnimationEnabled) {
            // 即使 lastPosition 对应的 word 为 null，也需要让下沉动画自然完成
            val currentWord = view.lyric.wordTimingNavigator.first(lastPosition)
            charAnimUpdated = charAnimator.update(currentWord, lastPosition)
        }

        if (progressUpdated || releaseSustainWord != null || charAnimUpdated) {
            renderDelegate.onSustainEffectUpdate(
                buildSustainEffects(
                    view.lyric.wordTimingNavigator.first(lastPosition),
                    lastPosition
                )
            )
            // 传递字符动画状态给渲染器（当前和上一个）
            if (isCharFloatAnimationEnabled) {
                renderDelegate.onCharAnimUpdate(
                    charAnimator.getActiveAnimation(),
                    charAnimator.getPreviousAnimation()
                )
            } else {
                renderDelegate.onCharAnimUpdate(null, null)
            }
            
            if (progressUpdated) {
                notifyProgressUpdate()
            }
            return progressUpdated || releaseSustainWord != null || charAnimUpdated
        }

        return progressUpdated
    }

    fun draw(canvas: Canvas) {
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
        renderDelegate.draw(canvas, view.scrollXOffset)
    }

    private fun updateLayoutMetrics() {
        textRenderer.updateMetrics(backgroundPaint)
        renderDelegate.onLayout(view.measuredWidth, view.measuredHeight, view.isOverflow())
    }

    private fun resetSustainState() {
        activeSustainWord = null
        activeSustainIntensity = 0f
        activeSustainPeakIntensity = 0f
        releaseSustainWord = null
        releaseStartRealtimeMs = 0L
        releaseSeedIntensity = 0f
    }

    private fun resolveReleaseSeedIntensity(fallback: Float): Float {
        val peakBased = activeSustainPeakIntensity.takeIf { it > 0f } ?: fallback
        return max(0.68f, max(peakBased * 0.9f, fallback)).coerceIn(0f, 1f)
    }

    private fun beginSustainRelease(word: WordModel, seedIntensity: Float) {
        releaseSustainWord = word
        releaseStartRealtimeMs = SystemClock.elapsedRealtime()
        releaseSeedIntensity = seedIntensity.coerceIn(0f, 1f)
    }

    private fun buildSustainEffects(word: WordModel?, position: Long): List<SustainEffectState> {
        if (!isSustainGlowEnabled) {
            resetSustainState()
            return emptyList()
        }
        if (position == Long.MIN_VALUE) return emptyList()

        val activeEffect = buildActiveSustainEffect(word, position)
        if (activeEffect != null && word != null) {
            if (activeSustainWord != null && activeSustainWord != word) {
                releaseSustainWord = null
                releaseStartRealtimeMs = 0L
                releaseSeedIntensity = 0f
                activeSustainPeakIntensity = 0f
            }
            activeSustainWord = word
            activeSustainIntensity = activeEffect.intensity
            activeSustainPeakIntensity = max(activeSustainPeakIntensity, activeEffect.intensity)
            if (releaseSustainWord == word) {
                releaseSustainWord = null
            }
        } else {
            activeSustainWord?.let {
                beginSustainRelease(
                    it,
                    resolveReleaseSeedIntensity(activeSustainIntensity.takeIf { s -> s > 0f } ?: 1f)
                )
            }
            activeSustainWord = null
            activeSustainIntensity = 0f
            activeSustainPeakIntensity = 0f
        }

        val releaseEffect = buildReleaseSustainEffect()
        if (releaseEffect == null && activeEffect == null) return emptyList()

        // 优先使用活跃效果，避免重影
        return when {
            activeEffect != null -> listOf(activeEffect)
            releaseEffect != null -> listOf(releaseEffect)
            else -> emptyList()
        }
    }

    private fun buildActiveSustainEffect(word: WordModel?, position: Long): SustainEffectState? {
        if (!isSustainGlowEnabled) return null
        word ?: return null
        if (word.duration < SUSTAIN_EFFECT_MIN_DURATION_MS) return null

        val wordWidth = (word.endPosition - word.startPosition).coerceAtLeast(0f)
        if (wordWidth <= 0f) return null

        val elapsedInWord = (position - word.begin).coerceIn(0L, word.duration)
        val triggerDelayMs = minOf(
            SUSTAIN_EFFECT_TRIGGER_DELAY_MS,
            (word.duration * SUSTAIN_EFFECT_TRIGGER_MAX_RATIO).toLong().coerceAtLeast(1L)
        )
        if (elapsedInWord < triggerDelayMs) return null

        val effectiveDuration = (word.duration - triggerDelayMs).coerceAtLeast(1L)
        val progress = ((elapsedInWord - triggerDelayMs).toFloat() / effectiveDuration).coerceIn(0f, 1f)
        if (progress <= 0f || progress >= 1f) return null

        val edgeFade = when {
            progress < 0.18f -> (progress / 0.18f)
            progress > 0.82f -> ((1f - progress) / 0.18f)
            else -> 1f
        }.coerceIn(0f, 1f)
        val intensity = (0.5f + edgeFade * 0.5f).coerceIn(0f, 1f)
        if (intensity <= 0f) return null

        val density = view.resources.displayMetrics.density
        val glowRadius = density * SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP * (0.64f + intensity * 0.32f)
        val glowAlpha = (SUSTAIN_EFFECT_MAX_GLOW_ALPHA * (0.34f + intensity * 0.56f))
            .toInt()
            .coerceIn(0, 255)

        return SustainEffectState(
            startX = word.startPosition,
            endX = word.endPosition,
            liftOffsetPx = 0f,
            glowRadiusPx = glowRadius,
            glowAlpha = glowAlpha,
            intensity = intensity,
            isReleasePhase = false
        )
    }

    private fun buildReleaseSustainEffect(): SustainEffectState? {
        if (!isSustainGlowEnabled) return null
        val word = releaseSustainWord ?: return null
        if (releaseStartRealtimeMs <= 0L) return null

        val elapsed = (SystemClock.elapsedRealtime() - releaseStartRealtimeMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / SUSTAIN_EFFECT_RELEASE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        if (progress >= 1f) {
            releaseSustainWord = null
            releaseStartRealtimeMs = 0L
            releaseSeedIntensity = 0f
            return null
        }

        val tailProgress = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val easedTail = tailProgress * tailProgress * (3f - 2f * tailProgress)
        val falloff = 1f - easedTail
        val intensity = (releaseSeedIntensity * falloff).coerceIn(0f, 1f)
        if (intensity <= 0.02f) return null

        val density = view.resources.displayMetrics.density
        val glowRadius = density * SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP * (0.42f + intensity * 0.34f)
        val glowAlpha = (SUSTAIN_EFFECT_MAX_GLOW_ALPHA * (0.16f + intensity * 0.38f))
            .toInt()
            .coerceIn(0, 255)

        return SustainEffectState(
            startX = word.startPosition,
            endX = word.endPosition,
            liftOffsetPx = 0f,
            glowRadiusPx = glowRadius,
            glowAlpha = glowAlpha,
            intensity = intensity,
            isReleasePhase = true
        )
    }

    // 根据当前时间精确计算单词内的高亮宽度
    private fun calculateCurrentWidth(
        pos: Long,
        word: WordModel? = view.lyric.wordTimingNavigator.first(pos)
    ): Float = when {
        word != null -> {
            val progress = ((pos - word.begin).toFloat() / word.duration).coerceIn(0f, 1f)
            word.startPosition + (word.endPosition - word.startPosition) * progress
        }
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
        fun onSustainEffectUpdate(effects: List<SustainEffectState>)
        fun onCharAnimUpdate(currentAnim: CharAnimator.CharAnimState?, previousAnim: CharAnimator.CharAnimState?)
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
        private var sustainEffects: List<SustainEffectState> = emptyList()
        private var currentCharAnim: CharAnimator.CharAnimState? = null
        private var previousCharAnim: CharAnimator.CharAnimState? = null
        
        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            this@SoftwareRenderer.width = width; this@SoftwareRenderer.height =
                height; this@SoftwareRenderer.overflow = overflow
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            this@SoftwareRenderer.highlightWidth = highlightWidth
        }

        override fun onSustainEffectUpdate(effects: List<SustainEffectState>) {
            sustainEffects = effects
        }

        override fun onCharAnimUpdate(currentAnim: CharAnimator.CharAnimState?, previousAnim: CharAnimator.CharAnimState?) {
            currentCharAnim = currentAnim
            previousCharAnim = previousAnim
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
                sustainEffects,
                currentCharAnim,
                previousCharAnim,
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
        private var sustainEffects: List<SustainEffectState> = emptyList()
        private var currentCharAnim: CharAnimator.CharAnimState? = null
        private var previousCharAnim: CharAnimator.CharAnimState? = null
        private var isDirty = true
        override fun invalidate() {
            isDirty = true
        }

        override fun onLayout(width: Int, height: Int, overflow: Boolean) {
            if (this@HardwareRenderer.width != width || this@HardwareRenderer.height != height || this@HardwareRenderer.overflow != overflow) {
                this@HardwareRenderer.width = width; this@HardwareRenderer.height =
                    height; this@HardwareRenderer.overflow = overflow
                // RenderNode 位置不变，因为 clipToBounds = false 允许内容超出边界
                renderNode.setPosition(0, 0, this@HardwareRenderer.width, this@HardwareRenderer.height)
                isDirty = true
            }
        }

        override fun onHighlightUpdate(highlightWidth: Float) {
            if (abs(this@HardwareRenderer.highlightWidth - highlightWidth) > 0.1f) {
                this@HardwareRenderer.highlightWidth = highlightWidth; isDirty = true
            }
        }

        override fun onSustainEffectUpdate(effects: List<SustainEffectState>) {
            if (sustainEffects != effects) {
                sustainEffects = effects
                isDirty = true
            }
        }

        override fun onCharAnimUpdate(currentAnim: CharAnimator.CharAnimState?, previousAnim: CharAnimator.CharAnimState?) {
            if (currentCharAnim != currentAnim || previousCharAnim != previousAnim) {
                currentCharAnim = currentAnim
                previousCharAnim = previousAnim
                isDirty = true
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
                    sustainEffects,
                    currentCharAnim,
                    previousCharAnim,
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
        private val sustainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

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
            sustainEffects: List<SustainEffectState>,
            currentCharAnim: CharAnimator.CharAnimState?,
            previousCharAnim: CharAnimator.CharAnimState?,
            useGradient: Boolean, scrollOnly: Boolean, bgPaint: TextPaint,
            hlPaint: TextPaint, normPaint: TextPaint
        ) {
            val y = (viewHeight / 2f) + baselineOffset
            canvas.withSave {
                val leftBearingPadding = model.textLeftBearingPadding
                val xOffset =
                    if (isOverflow) scrollX else if (model.isAlignedRight) viewWidth - model.width else 0f
                translate(xOffset + leftBearingPadding, 0f)

                if (scrollOnly) {
                    canvas.drawText(model.wordText, 0f, y, normPaint)
                    return@withSave
                }

                val sustainRanges = sustainEffects
                    .filterNot { it.isReleasePhase }
                    .mapNotNull {
                        val start = it.startX.coerceAtLeast(0f)
                        val end = it.endX.coerceAtMost(model.width)
                        if (end > start) start to end else null
                    }
                    .sortedBy { it.first }
                val hasSustain = sustainRanges.isNotEmpty()

                // 1. 绘制背景层 (可能是静止的彩虹)
                if (isRainbowBackground) {
                    bgPaint.shader = getOrCreateRainbowShader(model.width, rainbowColor.background)
                } else {
                    bgPaint.shader = null
                }

                val allExclusions = mutableListOf<Pair<Float, Float>>()
                if (hasSustain && useGradient) {
                    allExclusions.addAll(sustainRanges)
                }
                
                currentCharAnim?.let { anim ->
                    val word = anim.word
                    val isActive = anim.phase != CharAnimator.AnimPhase.IDLE && !anim.isFinished
                    if (anim.charIndex in word.chars.indices && isActive) {
                        val charStart = word.charStartPositions[anim.charIndex]
                        val charEnd = word.charEndPositions[anim.charIndex]
                        allExclusions.add(charStart to charEnd)
                    }
                }
                
                previousCharAnim?.let { anim ->
                    val word = anim.word
                    val isActive = anim.phase != CharAnimator.AnimPhase.IDLE && !anim.isFinished
                    if (anim.charIndex in word.chars.indices && isActive) {
                        val charStart = word.charStartPositions[anim.charIndex]
                        val charEnd = word.charEndPositions[anim.charIndex]
                        allExclusions.add(charStart to charEnd)
                    }
                }

                val backgroundLeft = if (useGradient) 0f else highlightWidth
                
                drawTextWithCharacterOffset(
                    canvas = canvas,
                    model = model,
                    y = y,
                    paint = bgPaint,
                    clipLeft = backgroundLeft,
                    clipRight = model.width,
                    exclusions = allExclusions,
                    currentCharAnim = currentCharAnim,
                    previousCharAnim = previousCharAnim,
                    viewHeight = viewHeight
                )

                // 2. 绘制高亮层
                if (highlightWidth > 0f) {
                    canvas.withSave {
                        clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())
                        
                        if (useGradient) {
                            val baseShader = if (isRainbowHighlight) {
                                getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                            } else {
                                LinearGradient(
                                    0f, 0f, model.width, 0f,
                                    hlPaint.color, hlPaint.color,
                                    Shader.TileMode.CLAMP
                                )
                            }
                            val maskShader = getOrCreateAlphaMaskShader(model.width, highlightWidth)
                            hlPaint.shader = ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                        } else {
                            if (isRainbowHighlight) {
                                hlPaint.shader = getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                            } else {
                                hlPaint.shader = null
                            }
                        }
                        
                        drawTextWithCharacterOffset(
                            canvas = canvas,
                            model = model,
                            y = y,
                            paint = hlPaint,
                            clipLeft = 0f,
                            clipRight = highlightWidth,
                            exclusions = allExclusions,
                            currentCharAnim = currentCharAnim,
                            previousCharAnim = previousCharAnim,
                            viewHeight = viewHeight
                        )
                    }
                }

                sustainEffects.forEach { effect ->
                    drawSustainEffect(
                        canvas = canvas,
                        model = model,
                        y = y,
                        viewHeight = viewHeight,
                        effect = effect,
                        highlightPaint = hlPaint
                    )
                }
                
                // 3. 绘制字符动画（先绘制上一个字符的下沉动画，再绘制当前字符的上浮动画）
                // 跳过已完成的动画（isFinished），已完成字符由静态层渲染
                previousCharAnim?.let { anim ->
                    if (!anim.isFinished) {
                        drawCharAnimation(
                            canvas = canvas,
                            model = model,
                            y = y,
                            viewHeight = viewHeight,
                            charAnim = anim,
                            highlightWidth = highlightWidth,
                            highlightPaint = hlPaint,
                            backgroundPaint = bgPaint
                        )
                    }
                }
                
                currentCharAnim?.let { anim ->
                    if (!anim.isFinished) {
                        drawCharAnimation(
                            canvas = canvas,
                            model = model,
                            y = y,
                            viewHeight = viewHeight,
                            charAnim = anim,
                            highlightWidth = highlightWidth,
                            highlightPaint = hlPaint,
                            backgroundPaint = bgPaint
                        )
                    }
                }
            }
        }

        private fun drawTextWithCharacterOffset(
            canvas: Canvas,
            model: LyricModel,
            y: Float,
            paint: TextPaint,
            clipLeft: Float,
            clipRight: Float,
            exclusions: List<Pair<Float, Float>>,
            currentCharAnim: CharAnimator.CharAnimState?,
            previousCharAnim: CharAnimator.CharAnimState?,
            viewHeight: Int
        ) {
            val safeLeft = minOf(clipLeft, model.textLeftBearing)
            val safeRight = clipRight.coerceAtLeast(safeLeft)
            if (safeRight <= safeLeft) return
            
            data class AnimCharInfo(
                val word: WordModel,
                val charIndex: Int,
                val charStart: Float,
                val charEnd: Float,
                val pushDistance: Float
            )
            
            val animChars = mutableListOf<AnimCharInfo>()
            
            currentCharAnim?.let { anim ->
                val word = anim.word
                val isActive = anim.phase != CharAnimator.AnimPhase.IDLE && !anim.isFinished
                if (anim.charIndex in word.chars.indices && isActive) {
                    val charStart = word.charStartPositions[anim.charIndex]
                    val charEnd = word.charEndPositions[anim.charIndex]
                    val charWidth = charEnd - charStart
                    val pushDistance = charWidth * (anim.currentScale - 1f) / 2f
                    
                    animChars.add(AnimCharInfo(
                        word = word,
                        charIndex = anim.charIndex,
                        charStart = charStart,
                        charEnd = charEnd,
                        pushDistance = pushDistance
                    ))
                }
            }
            
            previousCharAnim?.let { anim ->
                val word = anim.word
                val isActive = anim.phase != CharAnimator.AnimPhase.IDLE && !anim.isFinished
                if (anim.charIndex in word.chars.indices && isActive) {
                    val charStart = word.charStartPositions[anim.charIndex]
                    val charEnd = word.charEndPositions[anim.charIndex]
                    val charWidth = charEnd - charStart
                    val pushDistance = charWidth * (anim.currentScale - 1f) / 2f
                    
                    animChars.add(AnimCharInfo(
                        word = word,
                        charIndex = anim.charIndex,
                        charStart = charStart,
                        charEnd = charEnd,
                        pushDistance = pushDistance
                    ))
                }
            }
            
            if (animChars.isEmpty()) {
                drawTextWithOptionalExclusion(canvas, model.wordText, y, paint, safeLeft, safeRight, exclusions, viewHeight, model.textLeftBearing)
                return
            }
            
            canvas.withSave {
                clipRect(safeLeft, 0f, safeRight, viewHeight.toFloat())
                
                model.words.forEach { word ->
                    for (i in word.chars.indices) {
                        val charStart = word.charStartPositions[i]
                        val charEnd = word.charEndPositions[i]
                        
                        val isExcluded = exclusions.any { ex -> charStart >= ex.first && charEnd <= ex.second }
                        if (isExcluded) continue
                        
                        var charOffset = 0f
                        
                        for (animInfo in animChars) {
                            if (animInfo.word != word) continue
                            if (i > animInfo.charIndex) {
                                charOffset += animInfo.pushDistance
                            }
                        }
                        
                        val adjustedStart = charStart + charOffset
                        val adjustedEnd = charEnd + charOffset
                        
                        if (adjustedEnd >= safeLeft && adjustedStart <= safeRight) {
                            val charText = word.chars[i].toString()
                            drawText(charText, charStart + charOffset, y, paint)
                        }
                    }
                }
            }
        }


        private fun drawTextWithOptionalExclusion(
            canvas: Canvas,
            text: String,
            y: Float,
            paint: TextPaint,
            clipLeft: Float,
            clipRight: Float,
            exclusions: List<Pair<Float, Float>>,
            viewHeight: Int,
            leftBearing: Float = 0f
        ) {
            val safeLeft = minOf(clipLeft, leftBearing)
            val safeRight = clipRight.coerceAtLeast(safeLeft)
            if (safeRight <= safeLeft) return

            val clippedExclusions = exclusions
                .mapNotNull { (start, end) ->
                    if (end <= start || end <= safeLeft || start >= safeRight) null
                    else start.coerceIn(safeLeft, safeRight) to end.coerceIn(safeLeft, safeRight)
                }
                .sortedBy { it.first }

            if (clippedExclusions.isEmpty()) {
                canvas.withSave {
                    clipRect(safeLeft, 0f, safeRight, viewHeight.toFloat())
                    drawText(text, 0f, y, paint)
                }
                return
            }

            var cursor = safeLeft
            clippedExclusions.forEach { (start, end) ->
                if (start > cursor) {
                    canvas.withSave {
                        clipRect(cursor, 0f, start, viewHeight.toFloat())
                        drawText(text, 0f, y, paint)
                    }
                }
                cursor = max(cursor, end)
            }
            if (cursor < safeRight) {
                canvas.withSave {
                    clipRect(cursor, 0f, safeRight, viewHeight.toFloat())
                    drawText(text, 0f, y, paint)
                }
            }
        }

        private fun drawSustainEffect(
            canvas: Canvas,
            model: LyricModel,
            y: Float,
            viewHeight: Int,
            effect: SustainEffectState?,
            highlightPaint: TextPaint
        ) {
            effect ?: return
            val clipStart = effect.startX.coerceAtLeast(0f)
            val clipEnd = effect.endX.coerceAtMost(model.width)
            if (clipEnd <= clipStart) return

            val baseColor = (rainbowColor.highlight.firstOrNull() ?: highlightPaint.color) and 0x00FFFFFF
            val t = 0.22f
            val glowRgb = (
                ((Color.red(baseColor) + (255 - Color.red(baseColor)) * t).toInt().coerceIn(0, 255) shl 16) or
                ((Color.green(baseColor) + (255 - Color.green(baseColor)) * t).toInt().coerceIn(0, 255) shl 8) or
                ((Color.blue(baseColor) + (255 - Color.blue(baseColor)) * t).toInt().coerceIn(0, 255))
            )
            val density = view.resources.displayMetrics.density
            val outerStroke = (effect.glowRadiusPx * 0.3f).coerceAtLeast(density * 0.38f)
            val innerStroke = (effect.glowRadiusPx * 0.18f).coerceAtLeast(density * 0.28f)
            val outerAlpha = (effect.glowAlpha * 0.28f * effect.intensity).toInt().coerceIn(0, 255)
            val innerAlpha = (effect.glowAlpha * 0.46f).toInt().coerceIn(0, 255)
            val coreAlpha = if (effect.isReleasePhase) {
                (160f * effect.intensity).toInt().coerceIn(0, 255)
            } else {
                0xFF
            }
            val coreColor = (coreAlpha shl 24) or baseColor
            val drawGlow = isSustainGlowEnabled && effect.glowAlpha > 0

            sustainPaint.set(highlightPaint)
            sustainPaint.shader = null
            canvas.withSave {
                clipRect(clipStart, 0f, clipEnd, viewHeight.toFloat())

                if (drawGlow) {
                    // 外层光晕（降低过曝，优先可读性）。
                    sustainPaint.style = Paint.Style.STROKE
                    sustainPaint.strokeWidth = outerStroke
                    sustainPaint.color = (outerAlpha shl 24) or glowRgb
                    drawText(model.wordText, 0f, y, sustainPaint)

                    // 内层高亮描边。
                    sustainPaint.style = Paint.Style.STROKE
                    sustainPaint.strokeWidth = innerStroke
                    sustainPaint.color = (innerAlpha shl 24) or glowRgb
                    drawText(model.wordText, 0f, y, sustainPaint)
                }

                // 核心字形。
                sustainPaint.style = Paint.Style.FILL
                sustainPaint.strokeWidth = 0f
                sustainPaint.color = coreColor
                drawText(model.wordText, 0f, y, sustainPaint)
            }
        }

        /**
         * 绘制字符动画
         * 上浮阶段：从静态层排除，动画层施加偏移/缩放
         * 悬浮阶段：从静态层排除，动画层施加偏移/缩放（含呼吸脉动）
         * 下沉阶段：从静态层排除，动画层施加偏移/缩放（由 CharAnimator 提前结束避免过冲）
         * 已完成（isFinished）的字符不应调用此方法，由静态层渲染
         *
         * 关键：动画层统一使用 clipRect + 单色 paint 渲染。
         * 不使用 ComposeShader，因为 canvas 的 translate+scale 变换会使
         * ComposeShader 的坐标偏移，导致渐变遮罩位置错误。
         */
        private fun drawCharAnimation(
            canvas: Canvas,
            model: LyricModel,
            y: Float,
            viewHeight: Int,
            charAnim: CharAnimator.CharAnimState,
            highlightWidth: Float,
            highlightPaint: TextPaint,
            backgroundPaint: TextPaint
        ) {
            if (charAnim.isFinished) return
            
            val word = charAnim.word
            val charIndex = charAnim.charIndex
            
            if (charIndex !in word.chars.indices) return
            
            val charStart = word.charStartPositions[charIndex]
            val charEnd = word.charEndPositions[charIndex]
            if (charEnd <= charStart) return
            
            val charText = word.chars[charIndex].toString()
            val charWidth = charEnd - charStart

            val isInHighlight = charStart < highlightWidth
            val crossesHighlight = isInHighlight && charEnd > highlightWidth

            val charCenterX = (charStart + charEnd) / 2f
            val density = view.resources.displayMetrics.density
            val floatOffsetPx = charAnim.currentOffset * density

            if (crossesHighlight) {
                val clipLeft = minOf(0f, model.textLeftBearing)
                canvas.withSave {
                    clipRect(clipLeft, 0f, highlightWidth, viewHeight.toFloat())
                    translate(0f, y)
                    translate(0f, -floatOffsetPx)
                    scale(charAnim.currentScale, charAnim.currentScale, charCenterX, 0f)
                    val hlP = TextPaint(highlightPaint)
                    hlP.shader = null
                    if (isRainbowHighlight) {
                        hlP.shader = getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                    }
                    drawText(charText, charStart, 0f, hlP)
                }
                canvas.withSave {
                    clipRect(highlightWidth, 0f, model.width, viewHeight.toFloat())
                    translate(0f, y)
                    translate(0f, -floatOffsetPx)
                    scale(charAnim.currentScale, charAnim.currentScale, charCenterX, 0f)
                    val bgP = TextPaint(backgroundPaint)
                    bgP.shader = null
                    if (isRainbowBackground) {
                        bgP.shader = getOrCreateRainbowShader(model.width, rainbowColor.background)
                    }
                    drawText(charText, charStart, 0f, bgP)
                }
            } else {
                canvas.withSave {
                    translate(0f, y)
                    translate(0f, -floatOffsetPx)
                    scale(charAnim.currentScale, charAnim.currentScale, charCenterX, 0f)

                    val basePaint = if (isInHighlight) TextPaint(highlightPaint) else TextPaint(backgroundPaint)
                    basePaint.shader = null
                    if (isInHighlight && isRainbowHighlight) {
                        basePaint.shader = getOrCreateRainbowShader(model.width, rainbowColor.highlight)
                    } else if (!isInHighlight && isRainbowBackground) {
                        basePaint.shader = getOrCreateRainbowShader(model.width, rainbowColor.background)
                    }

                    drawText(charText, charStart, 0f, basePaint)
                }
            }
        }

        private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
            val inverseRatio = 1f - ratio
            val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
            val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
            val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
            val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
            return Color.argb(a, r, g, b)
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
        private fun getOrCreateAlphaMaskShader(totalWidth: Float, highlightWidth: Float): Shader {
            val edgePosition = max(highlightWidth / totalWidth, minEdgePosition)

            if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
                // 使用从不透明到透明的渐变
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
    }
}
