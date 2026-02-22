/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.graphics.withScale
import androidx.core.view.forEach
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.lyricMetadataOf
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.visibleIfChanged

@SuppressLint("ViewConstructor")
class RichLyricLineView(
    context: Context,
    var displayTranslation: Boolean = false,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
    var displayRoma: Boolean = false
) : LinearLayout(context), UpdatableColor {

    companion object {
        private val EMPTY_LYRIC_LINE = LyricLine()
    }

    val main = LyricLineView(context)
    val secondary = LyricLineView(context).apply { visibleIfChanged = false }

    var alwaysShowSecondary = false

    var renderScale = 1.0f
        private set

    private var animationTransition: Boolean = false
    private var pendingLine: IRichLyricLine? = null
    private var pendingPosition: Long? = null

    fun beginAnimationTransition() {
        animationTransition = true
    }

    fun endAnimationTransition() {
        animationTransition = false
        if (pendingLine != null) {
            updateAllLines()
            pendingPosition?.let { setPosition(it) }
        }
        pendingLine = null
        pendingPosition = null
    }

    private fun updateLayoutTransitionX(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        val layoutTransitionX = LayoutTransitionX(config).apply {
            setAnimateParentHierarchy(true)
        }
        layoutTransition = layoutTransitionX
    }

    var line: IRichLyricLine? = null
        set(value) {
            field = value
            if (animationTransition) {
                pendingLine = value
            } else {
                updateAllLines()
            }
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(main, lp)
        addView(secondary, lp)
        updateLayoutTransitionX()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (renderScale != 1.0f && renderScale > 0) {
            val originalWidth = MeasureSpec.getSize(widthMeasureSpec)
            val mode = MeasureSpec.getMode(widthMeasureSpec)

            val compensatedWidth = (originalWidth / renderScale).toInt()
            val newWidthSpec = MeasureSpec.makeMeasureSpec(compensatedWidth, mode)

            super.onMeasure(newWidthSpec, heightMeasureSpec)
            setMeasuredDimension(originalWidth, measuredHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (renderScale != 1.0f) {
            canvas.withScale(renderScale, renderScale, 0f, height / 2f) {
                super.dispatchDraw(this)
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }

    fun setRenderScale(scale: Float) {
        if (renderScale != scale) {
            renderScale = scale
            invalidate()
        }
    }

    fun setTransitionConfig(config: String?) {
        updateLayoutTransitionX(config)
    }

    fun notifyLineChanged() = updateAllLines()

    fun seekTo(position: Long) {
        if (animationTransition) {
            pendingPosition = position
            return
        }
        main.seekTo(position)
        secondary.seekTo(position)
    }

    fun setPosition(position: Long) {
        if (animationTransition) {
            pendingPosition = position
            return
        }
        main.setPosition(position)
        secondary.setPosition(position)
    }

    fun tryStartMarquee() {
        if (main.isMarqueeMode()) main.startMarquee()
        if (secondary.isMarqueeMode()) secondary.startMarquee()
    }

    fun setStyle(config: RichLyricLineConfig) {
        setMainStyle(
            config.primary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle,
            config.fadingEdgeLength
        )
        setSecondaryStyle(
            config.secondary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle,
            config.fadingEdgeLength
        )
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        forEach { if (it is UpdatableColor) it.updateColor(primary, background, highlight) }
    }

    fun setMainLyricPlayListener(listener: LyricPlayListener?) {
        main.syllable.playListener = listener
    }

    fun setSecondaryLyricPlayListener(listener: LyricPlayListener?) {
        secondary.syllable.playListener = listener
    }

    // --- 内部逻辑处理 ---

    private fun updateAllLines() {
        setMainLine(line)
        setSecondaryLine(line)
    }

    private fun setMainLine(source: IRichLyricLine?) {
        if (source == null) {
            main.setLyric(EMPTY_LYRIC_LINE)
            return
        }

        // 仅在启用相对进度且非标题行时，尝试为整行生成一个虚拟的 Word 节点
        val shouldGenerate = enableRelativeProgress && !source.isTitleLine()
        val processedWords = if (shouldGenerate) {
            calculateRelativeProgressWords(source, source.text, source.words)
        } else source.words

        val isGenerated = processedWords !== source.words

        main.setLyric(
            LyricLine(
                begin = source.begin,
                end = source.end,
                duration = source.duration,
                isAlignedRight = source.isAlignedRight,
                metadata = source.metadata,
                text = source.text,
                words = processedWords
            )
        )

        // 如果是生成的 Word，根据配置决定是否显示逐字高亮效果
        main.syllable.isScrollOnly = isGenerated && !enableRelativeProgressHighlight
    }

    private fun setSecondaryLine(source: IRichLyricLine?) {
        alwaysShowSecondary = false

        if (source == null) {
            secondary.apply { setLyric(null); visibleIfChanged = false }
            return
        }

        var isGenerated = false
        val newLine = LyricLine().apply {
            begin = source.begin
            end = source.end
            isAlignedRight = source.isAlignedRight

            when {
                // 1. 优先展示副行歌词
                !source.secondary.isNullOrBlank() || !source.secondaryWords.isNullOrEmpty() -> {
                    text = source.secondary
                    words = calculateRelativeProgressWords(
                        source,
                        source.secondary,
                        source.secondaryWords
                    )
                    isGenerated = words !== source.secondaryWords
                }
                // 2. 其次展示翻译
                displayTranslation && (!source.translation.isNullOrBlank() || !source.translationWords.isNullOrEmpty()) -> {
                    text = source.translation
                    words = calculateRelativeProgressWords(
                        source,
                        source.translation,
                        source.translationWords
                    )
                    metadata = lyricMetadataOf("translation" to "true")
                    isGenerated = words !== source.translationWords
                }

                displayRoma -> {
                    text = source.roma
                    metadata = lyricMetadataOf("roma" to "true")
                }
            }
        }

        val hasContent = newLine.text?.isNotBlank() == true || !newLine.words.isNullOrEmpty()
        alwaysShowSecondary = hasContent
                && (newLine.words?.isEmpty() == true
                || newLine.metadata?.getBoolean("translation") == true
                || newLine.metadata?.getBoolean("roma") == true)

        secondary.visibleIfChanged = alwaysShowSecondary

        secondary.setLyric(newLine)
        secondary.syllable.isScrollOnly = isGenerated && !enableRelativeProgressHighlight
    }

    /**
     * 当没有逐字信息但有起止时间时，构造一个包含全文本的虚拟 Word 节点以实现平滑滚动。
     */
    fun calculateRelativeProgressWords(
        timing: ILyricTiming,
        text: String?,
        words: List<LyricWord>?
    ): List<LyricWord>? {
        return if (words.isNullOrEmpty() && !text.isNullOrBlank() && timing.begin < timing.end && timing.begin >= 0) {
            listOf(
                LyricWord(
                    text = text,
                    begin = timing.begin,
                    end = timing.end,
                    duration = timing.end - timing.begin
                )
            )
        } else words
    }

    private fun setMainStyle(
        cfg: MainTextConfig,
        mar: MarqueeConfig,
        syl: SyllableConfig,
        grad: Boolean,
        fadingEdgeLength: Int
    ) {
        val notifyNeeded = (cfg.enableRelativeProgress != enableRelativeProgress) ||
                (cfg.enableRelativeProgressHighlight != enableRelativeProgressHighlight)

        enableRelativeProgress = cfg.enableRelativeProgress
        enableRelativeProgressHighlight = cfg.enableRelativeProgressHighlight

        main.setStyle(LyricLineConfig(cfg, mar, syl, grad, fadingEdgeLength))
        if (notifyNeeded) notifyLineChanged()
    }

    private fun setSecondaryStyle(
        cfg: SecondaryTextConfig,
        mar: MarqueeConfig,
        syl: SyllableConfig,
        grad: Boolean,
        fadingEdgeLength: Int
    ) {
        secondary.setStyle(LyricLineConfig(cfg, mar, syl, grad, fadingEdgeLength))
    }
}