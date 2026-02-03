/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import androidx.annotation.CallSuper
import androidx.core.view.contains
import androidx.core.view.forEach
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.lyricMetadataOf
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.model.RichLyricLineModel
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.getChildAtOrNull
import io.github.proify.lyricon.lyric.view.util.visibilityIfChanged
import io.github.proify.lyricon.lyric.view.util.visible

open class LyricPlayerView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes), UpdatableColor {

    companion object {
        internal const val KEY_SONG_TITLE_LINE: String = "TitleLine"
        private const val MIN_GAP_DURATION: Long = 8 * 1000
    }

    private var textMode = false
    private var config = RichLyricLineConfig()

    var isDisplayTranslation = false
        private set
    var isDisplayRoma = false
        private set

    private var enableRelativeProgress = false
    private var enableRelativeProgressHighlight = false
    private var enteringInterludeMode = false

    var song: Song? = null
        set(value) {
            reset()
            textMode = false
            if (value != null) {
                val newSong = fillGapAtStart(value)
                var previous: RichLyricLineModel? = null
                lineModels = newSong.lyrics?.map {
                    RichLyricLineModel(it).apply {
                        this.previous = previous
                        previous?.next = this
                        previous = this
                    }
                }
                timingNavigator = TimingNavigator(lineModels?.toTypedArray() ?: emptyArray())
            } else {
                lineModels = null
                timingNavigator = emptyTimingNavigator()
            }
            field = value
        }

    var text: String? = null
        set(value) {
            field = value
            if (!textMode) {
                reset(); textMode = true
            }
            if (value.isNullOrBlank()) {
                removeAllViews(); return
            }

            if (!contains(recycleTextLineView)) {
                addView(recycleTextLineView, reusableLayoutParams)
                updateTextLineViewStyle(config)
            }
            recycleTextLineView.setLyric(LyricLine(text = value, end = Long.MAX_VALUE / 10))
            recycleTextLineView.post { recycleTextLineView.startMarquee() }
        }

    private var lineModels: List<RichLyricLineModel>? = null
    private val activeLines = mutableListOf<IRichLyricLine>()
    private val recycleTextLineView by lazy { LyricLineView(context) }
    private val reusableLayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val tempViewsToRemove = mutableListOf<RichLyricLineView>()
    private val tempViewsToAdd = mutableListOf<IRichLyricLine>()
    private val tempFindActiveLines = mutableListOf<RichLyricLineModel>()

    private var timingNavigator: TimingNavigator<RichLyricLineModel> = emptyTimingNavigator()
    private var interludeState: InterludeState? = null

    private var layoutTransitionX: LayoutTransitionX? = null

    /**
     * 由autoAddView方法设置
     * @see [autoAddView]
     */
    private fun updateLayoutTransitionX(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransitionX = LayoutTransitionX(config).apply {
            //setAnimateParentHierarchy(false)
        }
        layoutTransition = null
    }

    private val mainLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private val secondaryLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            view.visible = true; updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    init {
        orientation = VERTICAL
        updateLayoutTransitionX()
        gravity = Gravity.CENTER_VERTICAL
    }

    // --- 公开 API ---

    private var _transitionConfig: String? = null
    fun setTransitionConfig(config: String?) {
        if (_transitionConfig == config) return
        _transitionConfig = config
        updateLayoutTransitionX(config)

        forEach { if (it is RichLyricLineView) it.setTransitionConfig(config) }

        Log.d("LyricPlayerView", "setTransitionConfig: $config")
    }

    fun setStyle(config: RichLyricLineConfig) = apply {
        this.config = config
        updateTextLineViewStyle(config)
        forEach { if (it is RichLyricLineView) it.setStyle(config) }
        updateViewsVisibility()
    }

    fun getStyle() = config

    fun updateDisplayTranslation(
        displayTranslation: Boolean = isDisplayTranslation,
        displayRoma: Boolean = isDisplayRoma
    ) {
        isDisplayTranslation = displayTranslation
        isDisplayRoma = displayRoma
        forEach {
            if (it is RichLyricLineView) {
                it.displayTranslation = displayTranslation
                it.displayRoma = displayRoma
                it.notifyLineChanged()
            }
        }
    }

    fun seekTo(position: Long) = updatePosition(position, true)

    fun setPosition(position: Long) = updatePosition(position)

    fun reset() {
        removeAllViews()
        activeLines.clear()
        if (enteringInterludeMode) exitInterludeMode()
    }

    override fun removeAllViews() {
        layoutTransition = null // 移除时禁用动画防止闪烁
        super.removeAllViews()
    }

    override fun updateColor(primary: Int, background: Int, highlight: Int) {
        val needsUpdate = primary != config.primary.textColor ||
                highlight != config.syllable.highlightColor
        if (!needsUpdate) return

        config.apply {
            this.primary.textColor = primary
            secondary.textColor = primary
            syllable.highlightColor = highlight
            syllable.backgroundColor = background
        }
        forEach {
            if (it is UpdatableColor) it.updateColor(
                primary,
                background,
                highlight
            )
        }
    }

    // --- 核心更新逻辑 ---

    private fun updatePosition(position: Long, seekTo: Boolean = false) {
        if (textMode) return

        tempFindActiveLines.clear()
        timingNavigator.forEachAtOrPrevious(position) { tempFindActiveLines.add(it) }

        val matches = tempFindActiveLines
        updateActiveViews(matches)

        forEach { view ->
            if (view is RichLyricLineView) {
                if (seekTo) view.seekTo(position) else view.setPosition(position)
            }
        }
        handleInterlude(position, matches)
    }

    private fun updateActiveViews(matches: List<IRichLyricLine>) {
        tempViewsToRemove.clear()
        tempViewsToAdd.clear()

        // 1. 识别需移除项
        for (i in 0 until childCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue
            if (view.line !in matches) tempViewsToRemove.add(view)
        }

        // 2. 识别需添加项
        matches.forEach { if (it !in activeLines) tempViewsToAdd.add(it) }

        if (tempViewsToRemove.isEmpty() && tempViewsToAdd.isEmpty()) return

        // 3. 单行变化直接复用 View
        if (activeLines.size == 1 && tempViewsToRemove.size == 1 && tempViewsToAdd.size == 1) {
            val recycleView = getChildAtOrNull(0) as? RichLyricLineView
            val newLine = tempViewsToAdd[0]
            if (recycleView != null) {
                activeLines[0] = newLine
                recycleView.line = newLine
                recycleView.tryStartMarquee()
            }
        } else {
            tempViewsToRemove.forEach { removeView(it); activeLines.remove(it.line) }
            tempViewsToAdd.forEach { line ->
                activeLines.add(line)
                createDoubleLineView(line).also { autoAddView(it); it.tryStartMarquee() }
            }
        }
        updateViewsVisibility()
    }

    fun updateViewsVisibility() {
        val totalChildCount = childCount
        if (totalChildCount == 0) return

        val v0 = getChildAtOrNull(0) as? RichLyricLineView ?: return
        val v0HasSec = v0.secondary.isVisible

        // 1. 判定 V0 内部状态
        val v0HideMain = v0HasSec && v0.main.isPlayFinished() && totalChildCount > 1
        val v0MainVisible = if (v0HideMain) GONE else VISIBLE
        val v0InternalLineCount = (if (v0HideMain) 0 else 1) + (if (v0HasSec) 1 else 0)

        // 2. 判定 V1 是否可见
        var v1Visible = false
        var v1InternalLineCount = 0
        if (totalChildCount > 1 && v0InternalLineCount <= 1) {
            v1Visible = true
            val v1 = getChildAtOrNull(1) as? RichLyricLineView
            v1InternalLineCount = 1 + (if (v1?.secondary?.isVisible == true) 1 else 0)
        }

        // 3. 确定缩放比例：根据总行数决定
        val totalLines = v0InternalLineCount + v1InternalLineCount
        val targetScale = if (totalLines > 1) {
            config.scaleInMultiLineMode.coerceIn(0.1f, 2f)
        } else 1.0f

        // 4. 确定是否处于"多视图吸附"模式
        val isMultiViewMode = totalChildCount > 1 && v1Visible && targetScale != 1.0f

        for (i in 0 until totalChildCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue

            when (i) {
                0 -> {
                    view.visibilityIfChanged = VISIBLE
                    applyCenterScaleStyle(
                        view,
                        v0MainVisible,
                        config.primary.textSize,
                        config.secondary.textSize,
                        targetScale
                    )
                }

                1 -> {
                    if (v1Visible) {
                        view.visibilityIfChanged = VISIBLE
                        val isV0Active = v0MainVisible == VISIBLE
                        val pSize =
                            if (isV0Active) config.secondary.textSize else config.primary.textSize
                        val sSize =
                            if (isV0Active) config.primary.textSize else config.secondary.textSize
                        applyCenterScaleStyle(view, VISIBLE, pSize, sSize, targetScale)
                    } else {
                        view.visibilityIfChanged = GONE
                        view.translationY = 0f
                    }
                }

                else -> {
                    view.visibilityIfChanged = GONE
                    view.translationY = 0f
                }
            }

            // --- 中心吸附计算 (基于 View 容器判断) ---
            if (isMultiViewMode && view.isVisible && view.height > 0) {
                // 计算单侧缩进量（基于原始高度，因为缩放在 Canvas 层）
                val offset = (view.height * (1f - targetScale)) / 2f
                // i=0 向下吸附，i=1 向上吸附
                view.translationY = if (i == 0) offset else -offset
            } else if (!v1Visible || targetScale == 1.0f) {
                // 如果切回单 View 模式或比例恢复 1.0，必须清空位移
                view.translationY = 0f
            }
        }

        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewsVisibility()
    }

    private fun applyCenterScaleStyle(
        view: RichLyricLineView,
        mainVis: Int,
        pBase: Float,
        sBase: Float,
        scale: Float
    ) {
        view.main.visibilityIfChanged = mainVis
        if (view.main.textSize != pBase) view.main.setTextSize(pBase)
        if (view.secondary.textSize != sBase) view.secondary.setTextSize(sBase)
        view.setRenderScale(scale)
    }

    private fun createDoubleLineView(line: IRichLyricLine) = RichLyricLineView(
        context,
        displayTranslation = isDisplayTranslation,
        displayRoma = isDisplayRoma,
        enableRelativeProgress = enableRelativeProgress,
        enableRelativeProgressHighlight = enableRelativeProgressHighlight,
    ).apply {
        this.line = line
        setStyle(config)
        setMainLyricPlayListener(mainLyricPlayListener)
        setSecondaryLyricPlayListener(secondaryLyricPlayListener)
        setTransitionConfig(_transitionConfig)
    }

    private fun autoAddView(view: RichLyricLineView) {
        if (layoutTransition == null && isNotEmpty()) layoutTransition = layoutTransitionX
        addView(view, reusableLayoutParams)
    }

    private fun updateTextLineViewStyle(config: RichLyricLineConfig) {
        recycleTextLineView.setStyle(
            LyricLineConfig(
                config.primary,
                config.marquee,
                config.syllable,
                config.gradientProgressStyle,
                config.fadingEdgeLength
            )
        )
    }

    // --- 间奏处理逻辑 ---

    private fun handleInterlude(position: Long, matches: List<RichLyricLineModel>) {
        val resolved = resolveInterludeState(position, matches)
        if (interludeState == resolved) return

        if (interludeState != null && resolved == null) {
            interludeState = null
            exitInterludeMode()
        } else if (resolved != null) {
            interludeState = resolved
            enteringInterludeMode(resolved.end - resolved.start)
        }
    }

    private fun resolveInterludeState(
        pos: Long,
        matches: List<RichLyricLineModel>
    ): InterludeState? {
        interludeState?.let { if (pos in (it.start + 1) until it.end) return it }

        if (matches.isEmpty()) return null
        val current = matches.last()
        val next = current.next ?: return null

        if (next.begin - current.end <= MIN_GAP_DURATION) return null
        if (pos <= current.end || pos >= next.begin) return null

        return InterludeState(current.end, next.begin)
    }

    @CallSuper
    protected open fun enteringInterludeMode(duration: Long) {
        enteringInterludeMode = true
    }

    @CallSuper
    protected open fun exitInterludeMode() {
        enteringInterludeMode = false
    }

    // --- 数据填充辅助 ---

    fun fillGapAtStart(origin: Song): Song {
        val song = origin.deepCopy()
        val title = getSongTitle(song) ?: return song
        val lyrics = song.lyrics?.toMutableList() ?: mutableListOf()

        if (lyrics.isEmpty()) {
            val d = if (song.duration > 0) song.duration else Long.MAX_VALUE
            lyrics.add(createLyricTitleLine(d, d, title))
        } else {
            val first = lyrics.first()
            if (first.begin > 0) lyrics.add(
                0,
                createLyricTitleLine(first.begin, first.begin, title)
            )
        }
        song.lyrics = lyrics
        return song
    }

    private fun createLyricTitleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text).apply {
            metadata = lyricMetadataOf(KEY_SONG_TITLE_LINE to "true")
        }

    private fun getSongTitle(song: Song) = when {
        !song.name.isNullOrBlank() && !song.artist.isNullOrBlank() -> "${song.name} - ${song.artist}"
        !song.name.isNullOrBlank() -> song.name
        else -> null
    }

    private fun emptyTimingNavigator() = TimingNavigator<RichLyricLineModel>(emptyArray())

    private data class InterludeState(val start: Long, val end: Long)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }
}

fun IRichLyricLine.isTitleLine(): Boolean =
    metadata?.getBoolean(LyricPlayerView.KEY_SONG_TITLE_LINE, false) == true