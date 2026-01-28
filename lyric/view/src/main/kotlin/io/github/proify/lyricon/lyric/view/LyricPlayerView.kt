/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.content.Context
import android.util.AttributeSet
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
import io.github.proify.lyricon.lyric.view.LyricPlayerView.Companion.KEY_SONG_TITLE_LINE
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.model.RichLyricLineModel
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.getChildAtOrNull
import io.github.proify.lyricon.lyric.view.util.visibilityIfChanged
import io.github.proify.lyricon.lyric.view.util.visible

open class LyricPlayerView(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs), UpdatableColor {

    companion object {
        internal const val KEY_SONG_TITLE_LINE: String = "TitleLine"
        private const val MIN_GAP_DURATION: Long = 8 * 1000
    }

    private var textMode = false
    private val recycleTextLineView: LyricLineView by lazy {
        LyricLineView(context)
    }

    private fun updateTextLineViewStyle(config: RichLyricLineConfig) {
        val lyricLineConfig = LyricLineConfig(
            config.primary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle
        )
        recycleTextLineView.setStyle(lyricLineConfig)
    }

    var text: String? = null
        set(value) {
            field = value

            if (textMode.not()) {
                reset()
                textMode = true
            }

            if (value.isNullOrBlank()) {
                removeAllViews()
                return
            }
            if (!contains(recycleTextLineView)) {
                addView(recycleTextLineView, reusableLayoutParams)
                updateTextLineViewStyle(config)
            }

            recycleTextLineView.setLyric(LyricLine(text = value, end = Long.MAX_VALUE / 10))
            recycleTextLineView.post {
                recycleTextLineView.startMarquee()
            }
        }

    var song: Song? = null
        set(value) {
            reset()
            textMode = false

            if (value != null) {
                val newSong = fillGapAtStart(value)

                var previous: RichLyricLineModel? = null
                lineModels = newSong.lyrics?.map {
                    val model = RichLyricLineModel(it)

                    model.previous = previous
                    previous?.next = model
                    previous = model

                    model
                }

                timingNavigator = TimingNavigator(lineModels?.toTypedArray() ?: emptyArray())
                field = value
            } else {
                field = null
                lineModels = null
                timingNavigator = emptyTimingNavigator()
            }
        }

    private var lineModels: List<RichLyricLineModel>? = null
    private val activeLines = mutableListOf<IRichLyricLine>()

    private var config: RichLyricLineConfig = RichLyricLineConfig()
    private var isDisplayTranslation: Boolean = false
    private var enableRelativeProgress = false
    private var enableRelativeProgressHighlight = false

    private val myLayoutTransition = LayoutTransitionX().apply {
        setAnimateParentHierarchy(false)
//        addTransitionListener(object : LayoutTransition.TransitionListener {
//            override fun endTransition(
//                p0: LayoutTransition?,
//                p1: ViewGroup?,
//                p2: View?,
//                p3: Int
//            ) {
//                forEach { view ->
//                    if (view is RichLyricLineView) {
//                        view.reLayout()
//                    } else if (view is LyricLineView) {
//                        view.reLayout()
//                    }
//                }
//            }
//
//            override fun startTransition(
//                p0: LayoutTransition?,
//                p1: ViewGroup?,
//                p2: View?,
//                p3: Int
//            ) {
//            }
//
//        })
    }

    private val tempViewsToRemove = mutableListOf<RichLyricLineView>()
    private val tempViewsToAdd = mutableListOf<IRichLyricLine>()

    private val tempFindActiveLines: MutableList<RichLyricLineModel> = mutableListOf()
    private val reusableLayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val mainLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) {
            updateViewsVisibility()
        }

        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private val secondaryLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            view.visible = true
            updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) {
            updateViewsVisibility()
        }

        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private var timingNavigator: TimingNavigator<RichLyricLineModel> = emptyTimingNavigator()
    private var interludeState: InterludeState? = null

    init {
        orientation = VERTICAL
        layoutTransition = myLayoutTransition
        gravity = Gravity.CENTER_VERTICAL
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    fun reset() {
        removeAllViews()
        activeLines.clear()

        if (enteringInterludeMode) exitInterludeMode()
    }

    override fun removeAllViews() {
        setLayoutTransition(null)
        super.removeAllViews()
    }

    private fun createDoubleLineView(line: IRichLyricLine): RichLyricLineView {
        val view = RichLyricLineView(
            context,
            displayTranslation = isDisplayTranslation,
            enableRelativeProgress = enableRelativeProgress,
            enableRelativeProgressHighlight = enableRelativeProgressHighlight
        ).apply {
            this.line = line
            setStyle(config)
            setMainLyricPlayListener(mainLyricPlayListener)
            setSecondaryLyricPlayListener(secondaryLyricPlayListener)
        }
        return view
    }

    /**
     * 更新歌词行视图的可见性及字体大小。
     * 采用状态预计算逻辑，确保字号缩放基于全局可见组件总数。
     */
    internal fun updateViewsVisibility() {
        val totalChildCount = childCount
        if (totalChildCount == 0) return

        // --- 1. 预计算阶段：判定各行的显示组件数量 ---

        // 第一行 (V0) 状态
        val v0 = getChildAtOrNull(0) as? RichLyricLineView ?: return
        val v0HasSecondary = v0.secondary.isVisible

        // 规则：若有副歌词、主歌词播完且有后继行，则隐藏主歌词为下一行留白
        val v0HideMain = v0HasSecondary && v0.main.isPlayFinished() && totalChildCount > 1
        val v0MainVisible = if (v0HideMain) GONE else VISIBLE
        val v0Count = (if (v0HideMain) 0 else 1) + (if (v0HasSecondary) 1 else 0)

        // 第二行 (V1) 状态
        var v1Visible = false
        var v1Count = 0
        if (totalChildCount > 1) {
            val v1 = getChildAtOrNull(1) as? RichLyricLineView
            // 只有当第一行空间占用较小（仅 1 个组件）时，第二行才允许显示
            if (v1 != null && v0Count <= 1) {
                v1Visible = true
                v1Count = 1 + (if (v1.secondary.isVisible) 1 else 0)
            }
        }

        // --- 2. 样式计算阶段：计算全局缩放 ---

        // 全局可见组件总数用于判断是否应用“多行模式”缩放比
        val totalVisibleComponents = v0Count + v1Count
        val needsScale = totalVisibleComponents > 1
        val scale = if (needsScale) config.textSizeRatioInMultiLineMode.coerceIn(0.1f, 2f) else 1f

        // --- 3. 应用阶段：同步 UI 状态 ---

        for (i in 0 until totalChildCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue

            when (i) {
                0 -> {
                    view.visibilityIfChanged = VISIBLE
                    applyLineStyle(
                        view = view,
                        mainVisible = v0MainVisible,
                        mainSize = config.primary.textSize * scale,
                        secondarySize = config.secondary.textSize * scale
                    )
                }

                1 -> {
                    if (v1Visible) {
                        view.visibilityIfChanged = VISIBLE
                        // 若第一行主歌词仍在，则第二行反转字号作为“预告行”
                        val isV0Active = v0MainVisible == VISIBLE
                        val pSize =
                            if (isV0Active) config.secondary.textSize else config.primary.textSize
                        val sSize =
                            if (isV0Active) config.primary.textSize else config.secondary.textSize

                        applyLineStyle(
                            view = view,
                            mainVisible = VISIBLE,
                            mainSize = pSize * scale,
                            secondarySize = sSize * scale
                        )
                    } else {
                        view.visibilityIfChanged = GONE
                    }
                }

                else -> {
                    // 第三行及以后强制隐藏
                    view.visibilityIfChanged = GONE
                }
            }
        }

        requestLayout()
        invalidate()
    }

    /**
     * 应用具体的可见性和字号，仅在数值变化时触发系统调用。
     */
    private fun applyLineStyle(
        view: RichLyricLineView,
        mainVisible: Int,
        mainSize: Float,
        secondarySize: Float
    ) {
        view.main.visibilityIfChanged = mainVisible

        // 检查 textSize 避免无效的 requestLayout()
        if (view.main.textSize != mainSize) {
            view.main.setTextSize(mainSize)
        }
        if (view.secondary.textSize != secondarySize) {
            view.secondary.setTextSize(secondarySize)
        }
    }

    fun setDisplayTranslation(displayTranslation: Boolean) {
        isDisplayTranslation = displayTranslation
        this.forEach { view ->
            if (view is RichLyricLineView) {
                view.displayTranslation = displayTranslation
                view.notifyLineChanged()
            }
        }
    }

    fun seekTo(position: Long) {
        updatePosition(position, true)
    }

    fun setPosition(position: Long) {
        updatePosition(position)
    }

    private fun updatePosition(position: Long, seekTo: Boolean = false) {
        if (textMode) return

        tempFindActiveLines.clear()
        timingNavigator.forEachAtOrPrevious(position) {
            tempFindActiveLines.add(it)
        }
        val matches = tempFindActiveLines
        updateActiveViews(matches)

        forEach { view ->
            if (view is RichLyricLineView)
                if (seekTo) view.seekTo(position) else view.setPosition(position)
        }

        handleInterlude(position, matches)
    }

    private fun handleInterlude(
        position: Long,
        matches: List<RichLyricLineModel>
    ) {
        val resolvedState = resolveInterludeState(position, matches)

        if (interludeState == resolvedState) return

        if (interludeState != null && resolvedState == null) {
            interludeState = null
            exitInterludeMode()
            return
        }

        if (resolvedState != null) {
            interludeState = resolvedState
            enteringInterludeMode(resolvedState.end - resolvedState.start)
        }
    }

    private fun resolveInterludeState(
        position: Long,
        matches: List<RichLyricLineModel>
    ): InterludeState? {

        // 1. 若已有间奏，优先校验是否仍然命中
        interludeState?.let { state ->
            if (position > state.start && position < state.end) return state
        }

        // 2. 尝试基于当前歌词重新构建间奏
        if (matches.isEmpty()) return null

        val current = matches.last()
        val next = current.next ?: return null

        val gapDuration = next.begin - current.end
        if (gapDuration <= MIN_GAP_DURATION) return null

        if (position <= current.end || position >= next.begin) return null

        return InterludeState(
            start = current.end,
            end = next.begin
        )
    }

    private var enteringInterludeMode: Boolean = false

    @CallSuper
    protected open fun enteringInterludeMode(duration: Long) {
        if (!enteringInterludeMode) enteringInterludeMode = true
    }

    @CallSuper
    protected open fun exitInterludeMode() {
        if (enteringInterludeMode) enteringInterludeMode = false
    }

    private fun updateActiveViews(matches: List<IRichLyricLine>) {
        tempViewsToRemove.clear()
        tempViewsToAdd.clear()

        // 找出需要移除的视图
        val currentSize = childCount
        for (i in 0 until currentSize) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue
            val line = view.line
            if (line != null && line !in matches) {
                tempViewsToRemove.add(view)
            }
        }

        // 找出需要添加的视图
        val matchesSize = matches.size
        for (i in 0 until matchesSize) {
            val line = matches[i]
            if (line !in activeLines) {
                tempViewsToAdd.add(line)
            }
        }

        // 如果没有变化,直接返回
        if (tempViewsToRemove.isEmpty() && tempViewsToAdd.isEmpty()) return

        // 优化:单个视图替换的情况
        val isSingleViewSwap = activeLines.size == 1
                && tempViewsToRemove.size == 1
                && tempViewsToAdd.size == 1

        if (isSingleViewSwap) {
            run {
                val recycleView = getChildAtOrNull(0) as? RichLyricLineView ?: return@run
                val newLine = tempViewsToAdd[0]

                newLine.let { activeLines[0] = it }
                recycleView.line = newLine

                recycleView.tryStartMarquee()
            }
        } else {
            // 批量处理移除
            val removeSize = tempViewsToRemove.size
            for (i in 0 until removeSize) {
                val view = tempViewsToRemove[i]
                removeView(view)
                activeLines.remove(view.line)
            }

            // 批量处理添加
            val addSize = tempViewsToAdd.size
            for (i in 0 until addSize) {
                val line = tempViewsToAdd[i]
                activeLines.add(line)

                val view = createDoubleLineView(line)
                autoAddView(view)

                view.tryStartMarquee()
            }
        }

        updateViewsVisibility()
    }

    fun autoAddView(view: RichLyricLineView) {
        if (layoutTransition == null && isNotEmpty()) {
            setLayoutTransition(myLayoutTransition)
        }
        addView(view, reusableLayoutParams)
    }

    override fun updateColor(primaryColor: Int, backgroundColor: Int, highlightColor: Int) {
        val needUpdate =
            primaryColor != config.primary.textColor
                    || primaryColor != config.secondary.textColor
                    || backgroundColor != config.syllable.backgroundColor
                    || highlightColor != config.syllable.highlightColor

        if (!needUpdate) {
            return
        }

        config.apply {
            primary.apply {
                textColor = primaryColor
            }
            secondary.apply {
                textColor = primaryColor
            }
            syllable.apply {
                this.highlightColor = highlightColor
                this.backgroundColor = backgroundColor
            }
        }

        forEach {
            if (it is UpdatableColor)
                it.updateColor(primaryColor, backgroundColor, highlightColor)
        }
    }

    fun setStyle(config: RichLyricLineConfig): LyricPlayerView = apply {
        this.config = config
        updateTextLineViewStyle(config)

        forEach { view ->
            if (view is RichLyricLineView) view.setStyle(config)
        }
    }

    fun getStyle(): RichLyricLineConfig = config

    fun fillGapAtStart(origin: Song): Song {
        val song = origin.deepCopy()
        val songTitle = getSongTitle(song) ?: return song
        val lyrics: MutableList<RichLyricLine> = song.lyrics?.toMutableList() ?: mutableListOf()

        if (lyrics.isEmpty()) {
            val duration = if (song.duration > 0) song.duration else Long.MAX_VALUE
            lyrics.add(
                createLyricTitleLine(
                    begin = 0,
                    end = duration,
                    duration = duration,
                    text = songTitle
                )
            )
        } else {
            val first = lyrics.first()
            if (first.begin > 0) {
                lyrics.add(
                    0,
                    createLyricTitleLine(
                        begin = 0,
                        end = first.begin,
                        duration = first.begin,
                        songTitle
                    )
                )
            }
        }

        song.lyrics = lyrics
        return song
    }

    @Suppress("SameParameterValue")
    private fun createLyricTitleLine(
        begin: Long,
        end: Long,
        duration: Long,
        text: String
    ) = RichLyricLine(
        begin = begin,
        end = end,
        duration = duration,
        text = text
    ).apply {
        metadata = lyricMetadataOf(KEY_SONG_TITLE_LINE to "true")
    }

    private fun getSongTitle(song: Song): String? {
        val hasName = song.name?.isNotBlank() ?: false
        val hasArtist = song.artist?.isNotBlank() ?: false

        return when {
            hasName && hasArtist -> "${song.name} - ${song.artist}"
            hasName -> song.name
            else -> null
        }
    }

    private fun emptyTimingNavigator() = TimingNavigator<RichLyricLineModel>(emptyArray())

    private data class InterludeState(
        val start: Long,
        val end: Long
    )
}

fun IRichLyricLine.isTitleLine(): Boolean =
    metadata?.getBoolean(KEY_SONG_TITLE_LINE, false) == true