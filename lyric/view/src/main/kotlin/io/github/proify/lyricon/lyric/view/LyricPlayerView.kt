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
import android.view.ViewTreeObserver
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
import io.github.proify.lyricon.lyric.view.util.visibleIfChanged
import java.util.concurrent.CopyOnWriteArraySet

open class LyricPlayerView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes), UpdatableColor {

    companion object {
        internal const val KEY_SONG_TITLE_LINE: String = "TitleLine"
        private const val MIN_GAP_DURATION: Long = 8 * 1000
    }

    // ---------- 私有常量 / 状态 ----------
    private var isTextMode = false
    private var styleConfig = RichLyricLineConfig() // 原名 config

    private var isEnableRelativeProgress = false
    private var isEnableRelativeProgressHighlight = false
    private var isEnteringInterludeMode = false

    // data models
    private var lineModelList: List<RichLyricLineModel>? = null
    private var timingNavigator: TimingNavigator<RichLyricLineModel> = emptyTimingNavigator()
    private var currentInterludeState: InterludeState? = null // 原名 interludeState

    // 视图缓存与临时集合（按用途分组）
    private val activeLyricLines = mutableListOf<IRichLyricLine>()
    private val textRecycleLineView by lazy { LyricLineView(context) }
    private val defaultLayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val viewsToRemoveTemp = mutableListOf<RichLyricLineView>()
    private val viewsToAddTemp = mutableListOf<IRichLyricLine>()
    private val tempFoundActiveLines = mutableListOf<RichLyricLineModel>()

    // 布局动画处理器
    private var layoutTransitionHandler: LayoutTransitionX? = null

    val lyricCountChangeListeners = CopyOnWriteArraySet<LyricCountChangeListener>()

    private val mainPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private val secondaryPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            view.visibleIfChanged = true; updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    // 视图树监听器
    private val viewTreeObserverListener =
        ViewTreeObserver.OnGlobalLayoutListener {
            updateViewsVisibility()
        }

    init {
        orientation = VERTICAL
        updateLayoutTransitionHandler()
        gravity = Gravity.CENTER_VERTICAL
    }

    // --- 公开 API  ---
    var isDisplayTranslation = false
        private set
    var isDisplayRoma = false
        private set

    var song: Song? = null
        set(value) {
            isTextMode = false
            if (value != null) {
                val curFirstLine = activeLyricLines.firstOrNull()
                val isExitingPlaceholder =
                    curFirstLine.isTitleLine() && getSongTitle(value) == curFirstLine?.text

                if (!isExitingPlaceholder) {
                    // 直接更新TimingNavigator即可
                    reset()
                }

                val newSong = fillGapAtStart(value)
                var previous: RichLyricLineModel? = null
                lineModelList = newSong.lyrics?.map {
                    RichLyricLineModel(it).apply {
                        this.previous = previous
                        previous?.next = this
                        previous = this
                    }
                }
                timingNavigator = TimingNavigator(lineModelList?.toTypedArray() ?: emptyArray())
            } else {
                reset()
                lineModelList = null
                timingNavigator = emptyTimingNavigator()
            }

            field = value
        }

    var text: String? = null
        set(value) {
            field = value
            if (!isTextMode) {
                reset(); isTextMode = true
            }
            if (value.isNullOrBlank()) {
                removeAllViews()
                return
            }

            if (!contains(textRecycleLineView)) {
                addView(textRecycleLineView, defaultLayoutParams)
                updateTextLineViewStyle(styleConfig)
            }
            textRecycleLineView.setLyric(LyricLine(text = value, end = Long.MAX_VALUE / 10))
            textRecycleLineView.post { textRecycleLineView.startMarquee() }
        }

    // ---------- 私有方法 ----------

    /**
     * 由 [autoAddView] 方法在合适时设置布局过渡器
     */
    private fun updateLayoutTransitionHandler(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransitionHandler = LayoutTransitionX(config)
        layoutTransition = null
    }

    fun setStyle(config: RichLyricLineConfig) = apply {
        this.styleConfig = config
        updateTextLineViewStyle(config)
        forEach { if (it is RichLyricLineView) it.setStyle(config) }
        updateViewsVisibility()
    }

    fun getStyle() = styleConfig

    fun setTransitionConfig(config: String?) {
        if (_transitionConfig == config) return
        _transitionConfig = config
        updateLayoutTransitionHandler(config)

        forEach { if (it is RichLyricLineView) it.setTransitionConfig(config) }

        Log.d("LyricPlayerView", "setTransitionConfig: $config")
    }

    private var _transitionConfig: String? = null

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
        activeLyricLines.clear()
        if (isEnteringInterludeMode) exitInterludeMode()
    }

    override fun removeAllViews() {
        layoutTransition = null // 移除时禁用动画防止闪烁
        super.removeAllViews()
    }

    override fun updateColor(primary: Int, background: Int, highlight: Int) {
        val needsUpdate = primary != styleConfig.primary.textColor ||
                highlight != styleConfig.syllable.highlightColor
        if (!needsUpdate) return

        styleConfig.apply {
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

    /**
     * 更新播放时间位置，并同步各行视图进度。主入口。
     */
    private fun updatePosition(position: Long, seekTo: Boolean = false) {
        if (isTextMode) return

        tempFoundActiveLines.clear()
        timingNavigator.forEachAtOrPrevious(position) { tempFoundActiveLines.add(it) }

        val matches = tempFoundActiveLines
        updateActiveViews(matches)

        // 同步所有子视图的进度或 seek 操作
        forEach { view ->
            if (view is RichLyricLineView) {
                if (seekTo) view.seekTo(position) else view.setPosition(position)
            }
        }
        handleInterlude(position, matches)
    }

    /**
     * 根据匹配结果调整要添加与移除的行视图：复用单行，批量添加/移除。
     */
    private fun updateActiveViews(matches: List<IRichLyricLine>) {
        viewsToRemoveTemp.clear()
        viewsToAddTemp.clear()

        // 1. 识别需移除项
        for (i in 0 until childCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue
            if (view.line !in matches) viewsToRemoveTemp.add(view)
        }

        // 2. 识别需添加项
        matches.forEach { if (it !in activeLyricLines) viewsToAddTemp.add(it) }

        if (viewsToRemoveTemp.isEmpty() && viewsToAddTemp.isEmpty()) return

        // 3. 单行变化直接复用 View（减少重建）
        if (activeLyricLines.size == 1 && viewsToRemoveTemp.size == 1 && viewsToAddTemp.size == 1) {
            val recycleView = getChildAtOrNull(0) as? RichLyricLineView
            val newLine = viewsToAddTemp[0]
            if (recycleView != null) {
                activeLyricLines[0] = newLine
                recycleView.line = newLine
                recycleView.tryStartMarquee()
            }
        } else {
            viewsToRemoveTemp.forEach { removeView(it); activeLyricLines.remove(it.line) }
            viewsToAddTemp.forEach { line ->
                activeLyricLines.add(line)
                createDoubleLineView(line).also { autoAddView(it); it.tryStartMarquee() }
            }
        }

        if (lyricCountChangeListeners.isNotEmpty()) {
            lyricCountChangeListeners.forEach {
                it.onLyricCountChanged(viewsToAddTemp.size, viewsToRemoveTemp.size)
            }
        }

        updateViewsVisibility()
    }

    /**
     * 根据当前可见行与播放状态判断最终每个 RichLyricLineView 的可见性与缩放。
     * 逻辑分四个阶段：状态决策、样式初始配置、硬性保留规则(最多2个槽位)、布局动画缩放。
     */
    fun updateViewsVisibility() {
        val totalChildCount = childCount
        if (totalChildCount == 0) return

        val v0 = getChildAtOrNull(0) as? RichLyricLineView ?: return
        val v1 = getChildAtOrNull(1) as? RichLyricLineView // 可能为 null

        // --- Phase 1: 状态决策 ---
        val v0MainFinished = v0.main.isPlayFinished()
        val v0HasSecContent = v0.secondary.lyric.let {
            it.text.isNotBlank() || it.words.isNotEmpty()
        }

        // 是否进入“换行过渡模式”：v0 主句已结束且存在 v1
        val isTransitionMode = v0MainFinished && v1 != null

        // --- Phase 2: 样式与初步可见性配置 ---
        val pSize = styleConfig.primary.textSize
        val sSize = styleConfig.secondary.textSize

        // 配置 v0
        if (isTransitionMode) {
            // 过渡：v0 主歌词变小，副歌词隐藏
            v0.main.visibilityIfChanged = VISIBLE
            v0.main.setTextSize(sSize)
            v0.secondary.visibilityIfChanged = GONE
        } else {
            // 聚焦当前：v0 主歌词正常显示，副歌词按条件显示
            v0.main.visibilityIfChanged = VISIBLE
            v0.main.setTextSize(pSize)

            v0.secondary.visibilityIfChanged =
                if (v0.alwaysShowSecondary
                    || (v0HasSecContent && v0.secondary.isPlayStarted())
                ) VISIBLE else GONE

            v0.secondary.setTextSize(sSize)
        }

        // 配置 v1
        if (v1 != null) {
            if (isTransitionMode) {
                // 过渡：v1 主歌词作为新焦点，显示为大字
                v1.main.visibilityIfChanged = VISIBLE
                v1.main.setTextSize(pSize)
                v1.secondary.visibilityIfChanged = GONE
            } else {
                // 预读：v1 主歌词小字
                v1.main.visibilityIfChanged = VISIBLE
                v1.main.setTextSize(sSize)
                v1.secondary.visibilityIfChanged = GONE
            }
        }

        // --- Phase 3: 最终裁决（最多显示 2 个槽位） ---
        var slotsRemaining = 2

        forEach { view ->
            if (view is RichLyricLineView) {
                var mainVis = view.main.visibility
                var secVis = view.secondary.visibility

                // 检查 Main
                if (view.main.isVisible) {
                    if (slotsRemaining > 0) {
                        slotsRemaining--
                    } else {
                        mainVis = GONE
                    }
                }
                // 检查 Secondary
                if (view.secondary.isVisible) {
                    if (slotsRemaining > 0) {
                        slotsRemaining--
                    } else {
                        secVis = GONE
                    }
                }

                // 容器整体可见性判定
                val allGone = mainVis != VISIBLE && secVis != VISIBLE
                if (!allGone) {
                    view.main.visibilityIfChanged = mainVis
                    view.secondary.visibilityIfChanged = secVis
                }
                view.visibilityIfChanged = if (allGone) GONE else VISIBLE
            }
        }

        // --- Phase 4: 布局动画与缩放（基于最终可见性） ---
        val finalVisibleLines = (2 - slotsRemaining) // 实际占用的槽位
        val targetScale = if (finalVisibleLines > 1) {
            styleConfig.scaleInMultiLine.coerceIn(0.1f, 2f)
        } else 1.0f

        val isMultiViewMode =
            totalChildCount > 1 && v1?.visibility == VISIBLE && targetScale != 1.0f

        for (i in 0 until totalChildCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue

            // 应用缩放
            view.setRenderScale(targetScale)

            // 吸附位移：上下吸附以保持视觉中心
            if (isMultiViewMode && view.isVisible && view.height > 0) {
                val offset = (view.height * (1f - targetScale)) / 2f
                view.translationY = if (i == 0) offset else -offset
            } else {
                view.translationY = 0f
            }
        }

        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewsVisibility()
    }

    /**
     * 创建一个双行歌词视图并完成必要的监听与样式设置。
     */
    private fun createDoubleLineView(line: IRichLyricLine) = RichLyricLineView(
        context,
        displayTranslation = isDisplayTranslation,
        displayRoma = isDisplayRoma,
        enableRelativeProgress = isEnableRelativeProgress,
        enableRelativeProgressHighlight = isEnableRelativeProgressHighlight,
    ).apply {
        this.line = line
        setStyle(styleConfig)
        setMainLyricPlayListener(mainPlayListener)
        setSecondaryLyricPlayListener(secondaryPlayListener)
        setTransitionConfig(_transitionConfig)
    }

    /**
     * 将新创建的视图添加到容器中，必要时启用布局过渡动画。
     */
    private fun autoAddView(view: RichLyricLineView) {
        if (layoutTransition == null && isNotEmpty()) layoutTransition = layoutTransitionHandler
        addView(view, defaultLayoutParams)
    }

    private fun updateTextLineViewStyle(config: RichLyricLineConfig) {
        textRecycleLineView.setStyle(
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
        if (currentInterludeState == resolved) return

        if (currentInterludeState != null && resolved == null) {
            currentInterludeState = null
            exitInterludeMode()
        } else if (resolved != null) {
            currentInterludeState = resolved
            enteringInterludeMode(resolved.end - resolved.start)
        }
    }

    private fun resolveInterludeState(
        pos: Long,
        matches: List<RichLyricLineModel>
    ): InterludeState? {
        currentInterludeState?.let { if (pos in (it.start + 1) until it.end) return it }

        if (matches.isEmpty()) return null
        val current = matches.last()
        val next = current.next ?: return null

        if (next.begin - current.end <= MIN_GAP_DURATION) return null
        if (pos <= current.end || pos >= next.begin) return null

        return InterludeState(current.end, next.begin)
    }

    @CallSuper
    protected open fun enteringInterludeMode(duration: Long) {
        isEnteringInterludeMode = true
    }

    @CallSuper
    protected open fun exitInterludeMode() {
        isEnteringInterludeMode = false
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
        viewTreeObserver.removeOnGlobalLayoutListener(viewTreeObserverListener)
        reset()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(viewTreeObserverListener)
    }

    interface LyricCountChangeListener {
        fun onLyricCountChanged(newCount: Int, removeCount: Int)
    }
}

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(LyricPlayerView.KEY_SONG_TITLE_LINE, false) == true