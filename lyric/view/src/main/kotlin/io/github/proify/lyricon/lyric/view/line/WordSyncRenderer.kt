/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import kotlin.math.max

internal class WordSyncRenderer(private val view: LyricLineView) : LineRenderer {
    val bgPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val hlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val progressAnimator = ProgressAnimator()
    private val scrollStepper = ScrollStepper()
    private val textDrawer = TextDrawer()
    private var sustainEffects: List<SustainEffectState> = emptyList()
    private var activeSustainWord: WordModel? = null
    private var activeSustainIntensity = 0f
    private var activeSustainPeakIntensity = 0f
    private var releaseSustainWord: WordModel? = null
    private var releaseStartRealtimeMs = 0L
    private var releaseSeedIntensity = 0f

    var isScrollOnly = false

    var isCharMotionEnabled = true

    var sustainGlowEnabled: Boolean
        get() = textDrawer.sustainGlowEnabled
        set(value) {
            textDrawer.sustainGlowEnabled = value
        }

    var cjkMotionLiftFactor: Float
        get() = textDrawer.cjkLiftFactor
        set(value) {
            textDrawer.cjkLiftFactor = value
        }

    var cjkMotionWaveFactor: Float
        get() = textDrawer.cjkWaveFactor
        set(value) {
            textDrawer.cjkWaveFactor = value
        }

    var latinMotionLiftFactor: Float
        get() = textDrawer.latinLiftFactor
        set(value) {
            textDrawer.latinLiftFactor = value
        }

    var latinMotionWaveFactor: Float
        get() = textDrawer.latinWaveFactor
        set(value) {
            textDrawer.latinWaveFactor = value
        }

    var isGradientEnabled = true
        set(value) {
            if (field != value) {
                field = value
                textDrawer.clearShaderCache()
            }
        }

    var playListener: LyricPlayListener? = null
        set(value) {
            field = value
            _playListener = value ?: NoOpPlayListener
        }

    private var _playListener: LyricPlayListener = NoOpPlayListener

    var lastPosition = Long.MIN_VALUE
        private set

    override val isPlaying get() = progressAnimator.isAnimating
    override val isFinished get() = progressAnimator.hasFinished
    override val isStarted get() = progressAnimator.hasStarted

    fun setTextSize(size: Float) {
        bgPaint.textSize = size
        hlPaint.textSize = size
        textDrawer.updateMetrics(bgPaint)
    }

    fun setTypeface(tf: Typeface?) {
        bgPaint.typeface = tf
        hlPaint.typeface = tf
        textDrawer.updateMetrics(bgPaint)
    }

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgPaint.color = background[0]
        if (highlight.isNotEmpty()) hlPaint.color = highlight[0]
        textDrawer.setColors(background, highlight)
        textDrawer.clearShaderCache()
    }

    fun updateLayout(model: LyricModel, state: LineState, viewWidth: Int, viewHeight: Int) {
        textDrawer.updateMetrics(bgPaint)
        if (progressAnimator.hasFinished) {
            progressAnimator.jumpTo(model.width)
        }
        updateScrollState(model, state, viewWidth)
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val target = targetWidth(posMs, model)
        progressAnimator.jumpTo(target)
        updateScrollState(model, state, viewWidth)
        resetSustainState()
        sustainEffects = buildSustainEffects(model.wordTimingNavigator.first(posMs), posMs)
        lastPosition = posMs
        notifyProgress(model)
    }

    override fun update(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            seek(model, state, posMs, viewWidth, viewHeight)
            return
        }

        val word = model.wordTimingNavigator.first(posMs)
        val target = targetWidth(posMs, model, word)

        if (word != null && progressAnimator.currentWidth == 0f) {
            word.previous?.let { progressAnimator.jumpTo(it.endPosition) }
        }
        if (target != progressAnimator.targetWidth) {
            progressAnimator.animateTo(target, word?.duration ?: 0)
        }
        sustainEffects = buildSustainEffects(word, posMs)
        lastPosition = posMs
    }

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int
    ): Boolean {
        val progressUpdated = progressAnimator.step(deltaNanos)
        if (progressUpdated) {
            updateScrollState(model, state, viewWidth)
            notifyProgress(model)
        }
        if (progressUpdated || releaseSustainWord != null) {
            sustainEffects = buildSustainEffects(model.wordTimingNavigator.first(lastPosition), lastPosition)
            return progressUpdated || releaseSustainWord != null
        }
        return progressUpdated
    }

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        textDrawer.draw(
            canvas, model, viewWidth, viewHeight,
            state.scrollOffset, model.width > viewWidth,
            progressAnimator.currentWidth,
            sustainEffects, isGradientEnabled, isScrollOnly, isCharMotionEnabled,
            bgPaint, hlPaint, paint
        )
    }

    override fun reset(state: LineState) {
        progressAnimator.reset()
        state.reset()
        lastPosition = Long.MIN_VALUE
        resetSustainState()
        sustainEffects = emptyList()
        textDrawer.clearShaderCache()
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
        if (!sustainGlowEnabled) {
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

        return buildList(2) {
            releaseEffect?.let { add(it) }
            activeEffect?.let { add(it) }
        }
    }

    private fun buildActiveSustainEffect(word: WordModel?, position: Long): SustainEffectState? {
        if (!sustainGlowEnabled) return null
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
            progress < 0.18f -> progress / 0.18f
            progress > 0.82f -> (1f - progress) / 0.18f
            else -> 1f
        }.coerceIn(0f, 1f)
        if (edgeFade <= 0f) return null

        val density = view.resources.displayMetrics.density
        val glowRadius = density * SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP * (0.64f + edgeFade * 0.32f)
        val glowAlpha = (SUSTAIN_EFFECT_MAX_GLOW_ALPHA * (0.34f + edgeFade * 0.56f))
            .toInt()
            .coerceIn(0, 255)

        return SustainEffectState(
            startX = word.startPosition,
            endX = word.endPosition,
            glowRadiusPx = glowRadius,
            glowAlpha = glowAlpha,
            intensity = edgeFade
        )
    }

    private fun buildReleaseSustainEffect(): SustainEffectState? {
        if (!sustainGlowEnabled) return null
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
            glowRadiusPx = glowRadius,
            glowAlpha = glowAlpha,
            intensity = intensity
        )
    }

    private fun updateScrollState(model: LyricModel, state: LineState, viewWidth: Int) {
        val offset = scrollStepper.compute(
            progressAnimator.currentWidth, model.width,
            viewWidth.toFloat(), progressAnimator.hasFinished, state.isScrollFinished
        )
        state.scrollOffset = offset
        if (progressAnimator.hasFinished) {
            state.isScrollFinished = true
        }
    }

    private fun targetWidth(posMs: Long, model: LyricModel, word: WordModel? = null): Float {
        val w = word ?: model.wordTimingNavigator.first(posMs)
        return when {
            w != null -> w.endPosition
            posMs >= model.end -> model.width
            posMs <= model.begin -> 0f
            else -> progressAnimator.currentWidth
        }
    }

    private fun notifyProgress(model: LyricModel) {
        val current = progressAnimator.currentWidth
        val total = model.width

        if (!progressAnimator.hasStarted && current > 0f) {
            progressAnimator.hasStarted = true
            _playListener.onPlayStarted(view)
        }
        if (!progressAnimator.hasFinished && current >= total) {
            progressAnimator.hasFinished = true
            _playListener.onPlayEnded(view)
        }
        _playListener.onPlayProgress(view, total, current)
    }

    companion object {
        private const val SUSTAIN_EFFECT_MIN_DURATION_MS = 420L
        private const val SUSTAIN_EFFECT_TRIGGER_DELAY_MS = 180L
        private const val SUSTAIN_EFFECT_TRIGGER_MAX_RATIO = 0.35f
        private const val SUSTAIN_EFFECT_MAX_GLOW_RADIUS_DP = 3.4f
        private const val SUSTAIN_EFFECT_MAX_GLOW_ALPHA = 160
        private const val SUSTAIN_EFFECT_RELEASE_DURATION_MS = 170L

        private val NoOpPlayListener = object : LyricPlayListener {
            override fun onPlayStarted(view: LyricLineView) {}
            override fun onPlayEnded(view: LyricLineView) {}
            override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
        }
    }
}
