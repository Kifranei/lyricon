/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.contains
import androidx.core.view.isNotEmpty
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.visibleIfChanged

@SuppressLint("ViewConstructor")
class StatusBarLyric(
    context: Context,
    initialStyle: LyricStyle,
    linkedTextView: TextView?
) : LinearLayout(context) {

    companion object {
        const val VIEW_TAG: String = "lyricon:lyric_view"
        private const val TAG = "StatusBarLyric"
    }

    val logoView: SuperLogo = SuperLogo(context).apply {
        this.linkedTextView = linkedTextView
    }

    val textView: SuperText = SuperText(context).apply {
        this.linkedTextView = linkedTextView
        eventListener = object : SuperText.EventListener {
            override fun enteringInterludeMode(duration: Long) {
                logoView.syncProgress(0, duration)
            }

            override fun exitInterludeMode() {
                logoView.clearProgress()
            }
        }
    }

    // --- 对外状态 ---

    var currentStatusColor: StatusColor = StatusColor(Color.BLACK, false, Color.GRAY)
        private set

    var isSleepMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Log.d(TAG, "休眠模式：$value")
            if (value) {
                pendingSleepData = PendingData()
            } else {
                pendingSleepData?.let { seekTo(it.position) }
                pendingSleepData = null
            }
        }

    // --- 样式 / 播放状态 ---

    private var currentStyle: LyricStyle = initialStyle
    private var isPlaying: Boolean = false
    private var isOplusCapsuleShowing: Boolean = false

    // 上一次 Logo gravity，用于避免重复重排
    private var lastLogoGravity: Int = -114

    // 休眠期间缓存的进度数据
    private var pendingSleepData: PendingData? = null

    // --- 歌词内容与超时状态 ---

    private var hasLyricContent: Boolean = false
    private var lyricTimedOut: Boolean = false

    // 主线程调度器
    private val mainHandler: Handler = Handler(context.mainLooper)

    // 当前生效的超时 Runnable
    private var lyricTimeoutTask: Runnable? = null

    // --- 系统 / 辅助组件 ---

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    /**
     * 单次布局变更动画
     * 用于样式或尺寸突变时的过渡
     */
    private val singleLayoutTransition: LayoutTransition = LayoutTransitionX().apply {
        addTransitionListener(object : LayoutTransition.TransitionListener {

            override fun startTransition(
                transition: LayoutTransition?, container: ViewGroup?,
                view: View?, transitionType: Int
            ) = Unit

            override fun endTransition(
                transition: LayoutTransition?, container: ViewGroup?,
                view: View?, transitionType: Int
            ) {
                disableTransitionType(LayoutTransition.CHANGING)
                layoutTransition = null
            }
        })
    }

    // TextView 子视图结构变化监听，用于刷新可见性
    private val textHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) = updateVisibility()
        override fun onChildViewRemoved(parent: View?, child: View?) = updateVisibility()
    }

    // 歌词行数变化监听，用于重置超时逻辑
    private val lyricCountChangeListener =
        object : LyricPlayerView.LyricCountChangeListener {
            override fun onLyricCountChanged(newCount: Int, removeCount: Int) {
                refreshLyricTimeoutState()
            }
        }

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        layoutTransition = null

        addView(
            textView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            }
        )

        updateLogoLocation()
        applyInitialStyle(initialStyle)

        textView.setOnHierarchyChangeListener(textHierarchyChangeListener)
        textView.lyricCountChangeListeners += lyricCountChangeListener
    }

    // --- 公开 API ---

    fun updateStyle(style: LyricStyle) {
        triggerSingleTransition()
        currentStyle = style
        logoView.applyStyle(style)
        updateLogoLocation()
        textView.applyStyle(style)
        updateLayoutConfig(style)

        refreshLyricTimeoutState()
        requestLayout()
    }

    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        logoView.setStatusBarColor(color)
        textView.setStatusBarColor(color)
    }

    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        refreshLyricTimeoutState()
        updateVisibility()
    }

    fun updateVisibility() {
        val isLocked = keyguardManager.isKeyguardLocked
        val hideOnLockScreen = currentStyle.basicStyle.hideOnLockScreen && isLocked
        val shouldShow =
            isPlaying && !hideOnLockScreen && textView.isNotEmpty() && !lyricTimedOut

        visibleIfChanged = shouldShow
    }

    fun setSong(song: Song?) {
        textView.song = song
        hasLyricContent = !song?.lyrics.isNullOrEmpty()
        refreshLyricTimeoutState()
    }

    fun setText(text: String?) {
        textView.text = text
        hasLyricContent = !text.isNullOrBlank()
        refreshLyricTimeoutState()
    }

    fun seekTo(position: Long) {
        if (isSleepMode) {
            pendingSleepData?.position = position
            return
        }
        textView.seekTo(position)
    }

    fun setPosition(position: Long) {
        if (isSleepMode) {
            pendingSleepData?.position = position
            return
        }
        textView.setPosition(position)
    }

    fun updateDisplayTranslation(
        displayTranslation: Boolean = textView.isDisplayTranslation,
        displayRoma: Boolean = textView.isDisplayRoma
    ) {
        textView.updateDisplayTranslation(displayTranslation, displayRoma)
    }

    fun setOplusCapsuleVisibility(visible: Boolean) {
        isOplusCapsuleShowing = visible
        triggerSingleTransition()
        updateWidthInternal(currentStyle)
        logoView.oplusCapsuleShowing = visible
    }

    // --- 内部逻辑 ---

    private fun applyInitialStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        updateLayoutConfig(style)
    }

    private fun updateLogoLocation() {
        val logoStyle = currentStyle.packageStyle.logo
        val gravity = logoStyle.gravity
        if (gravity == lastLogoGravity) return
        lastLogoGravity = gravity

        if (contains(logoView)) removeView(logoView)
        val textIndex = indexOfChild(textView).coerceAtLeast(0)

        when (gravity) {
            LogoStyle.GRAVITY_START -> addView(logoView, textIndex)
            LogoStyle.GRAVITY_END -> addView(logoView, textIndex + 1)
            else -> addView(logoView, textIndex)
        }
    }

    private fun updateLayoutConfig(style: LyricStyle) {
        val basic = style.basicStyle
        val margins = basic.margins
        val paddings = basic.paddings

        ensureMarginLayoutParams().apply {
            width = calculateTargetWidth(basic).dp
            leftMargin = margins.left.dp
            topMargin = margins.top.dp
            rightMargin = margins.right.dp
            bottomMargin = margins.bottom.dp
        }

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    private fun updateWidthInternal(style: LyricStyle) {
        ensureMarginLayoutParams().width =
            calculateTargetWidth(style.basicStyle).dp
        requestLayout()
    }

    private fun calculateTargetWidth(basicStyle: BasicStyle): Float {
        return if (isOplusCapsuleShowing) {
            basicStyle.widthInColorOSCapsuleMode
        } else {
            basicStyle.width
        }
    }

    private fun ensureMarginLayoutParams(): MarginLayoutParams {
        val lp = layoutParams as? MarginLayoutParams
            ?: MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        if (layoutParams == null) layoutParams = lp
        return lp
    }

    private fun triggerSingleTransition() {
        singleLayoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        layoutTransition = singleLayoutTransition
    }

    private fun refreshLyricTimeoutState() {
        resetLyricTimeout()

        val hideWhenNoLyricAfterSeconds =
            currentStyle.basicStyle.hideWhenNoLyricAfterSeconds
        val hideWhenNoUpdateAfterSeconds =
            currentStyle.basicStyle.hideWhenNoUpdateAfterSeconds

        val hideWhenNoLyric = hideWhenNoLyricAfterSeconds > 0
        val hideWhenNoUpdate = hideWhenNoUpdateAfterSeconds > 0

        val timeout = when {
            hideWhenNoLyric && !hasLyricContent -> hideWhenNoLyricAfterSeconds
            hideWhenNoUpdate && hasLyricContent -> hideWhenNoUpdateAfterSeconds
            else -> -1
        }

        if (timeout > 0) {
            val task = Runnable {
                lyricTimedOut = true
                updateVisibility()
            }
            lyricTimeoutTask = task
            mainHandler.postDelayed(task, timeout * 1000L)
        }

        updateVisibility()
    }

    private fun resetLyricTimeout() {
        lyricTimedOut = false
        lyricTimeoutTask?.let { mainHandler.removeCallbacks(it) }
        lyricTimeoutTask = null
    }

    private class PendingData(var position: Long = 0)
}