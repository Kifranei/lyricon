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
import android.os.SystemClock
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
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.visibilityIfChanged

/**
 * 状态栏歌词容器视图
 *
 * @property initialStyle 初始歌词样式
 * @property linkedTextView 关联的 TextView，用于同步某些状态
 */
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

    // --- 状态属性 ---

    var currentStatusColor: StatusColor = StatusColor(Color.BLACK, false, Color.GRAY)
        private set

    var sleepMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Log.d(TAG, "休眠模式：$value")
            if (value) {
                pendingDataWhileAsleep = PendingData()
            } else {
                pendingDataWhileAsleep?.let { seekTo(it.position) }
                pendingDataWhileAsleep = null
            }
        }

    private var currentStyle: LyricStyle = initialStyle
    private var isPlaying: Boolean = false
    private var isOplusCapsuleShowing: Boolean = false
    private var lastLogoGravity: Int = -114
    private var pendingDataWhileAsleep: PendingData? = null
    private var hasLyricContent: Boolean = false
    private var noLyricSinceUptimeMs: Long? = null
    private var noLyricTimeoutReached: Boolean = false
    private var noLyricTimeoutToken: Int = 0
    private var noLyricTimeoutRunnable: Runnable? = null

    // --- 辅助组件 ---

    private val keyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    /**
     * 单次布局过渡动画处理器
     */
    private val singleLayoutTransition: LayoutTransition = LayoutTransitionX().apply {
        addTransitionListener(object : LayoutTransition.TransitionListener {
            override fun startTransition(p0: LayoutTransition, p1: ViewGroup, p2: View, p3: Int) {}
            override fun endTransition(p0: LayoutTransition?, p1: ViewGroup?, p2: View?, p3: Int) {
                disableTransitionType(LayoutTransition.CHANGING)
                layoutTransition = null
            }
        })
    }

    private val textHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) = updateVisibility()
        override fun onChildViewRemoved(parent: View?, child: View?) = updateVisibility()
    }

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        layoutTransition = null

        addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        })

        updateLogoLocation()
        applyInitialStyle(initialStyle)
        textView.setOnHierarchyChangeListener(textHierarchyChangeListener)
    }

    // --- 公开 API ---

    /**
     * 更新歌词样式
     */
    fun updateStyle(style: LyricStyle) {
        triggerSingleTransition()
        currentStyle = style
        logoView.applyStyle(style)
        updateLogoLocation()
        textView.applyStyle(style)
        updateLayoutConfig(style)
        refreshLyricContentState()
        updateNoLyricState(hasLyricContent)
        requestLayout()
    }

    /**
     * 设置状态栏颜色适配
     */
    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        logoView.setStatusBarColor(color)
        textView.setStatusBarColor(color)
    }

    /**
     * 设置播放状态并更新可见性
     */
    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        updateNoLyricState(hasLyricContent)
        updateVisibility()
    }

    /**
     * 更新视图可见性逻辑
     */
    fun updateVisibility() {
        val isLocked = keyguardManager.isKeyguardLocked
        val hideOnLockScreen = currentStyle.basicStyle.hideOnLockScreen && isLocked
        val shouldShow =
            isPlaying && !hideOnLockScreen && textView.isNotEmpty() && !noLyricTimeoutReached

        visibilityIfChanged = if (shouldShow) VISIBLE else GONE
    }

    /**
     * 设置当前歌曲
     */
    fun setSong(song: Song?) {
        if (song != null && isBlacklistedSong(song)) {
            textView.song = null
            hasLyricContent = false
        } else {
            textView.song = song
            hasLyricContent = !(song?.lyrics.isNullOrEmpty())
        }
        updateNoLyricState(hasLyricContent)
    }

    /**
     * 直接更新文本内容
     */
    fun updateText(text: String?) {
        if (isBlacklistedText(text)) {
            textView.text = null
            hasLyricContent = false
        } else {
            textView.text = text
            hasLyricContent = !text.isNullOrBlank()
        }
        updateNoLyricState(hasLyricContent)
    }

    /**
     * 跳转至指定进度
     */
    fun seekTo(position: Long) {
        textView.seekTo(position)
    }

    /**
     * 更新当前进度
     */
    fun updatePosition(position: Long) {
        if (sleepMode) {
            pendingDataWhileAsleep?.position = position
            return
        }
        textView.setPosition(position)
    }

    /**
     * 更新翻译/罗马音显示配置
     */
    fun updateDisplayTranslation(
        displayTranslation: Boolean = textView.isDisplayTranslation,
        displayRoma: Boolean = textView.isDisplayRoma
    ) {
        textView.updateDisplayTranslation(displayTranslation, displayRoma)
    }

    /**
     * 设置 ColorOS 胶囊状态
     */
    fun setOplusCapsuleVisibility(visible: Boolean) {
        isOplusCapsuleShowing = visible
        triggerSingleTransition()
        updateWidthInternal(currentStyle)
        logoView.oplusCapsuleShowing = visible
    }

    // --- 内部私有方法 ---

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
        ensureMarginLayoutParams().width = calculateTargetWidth(style.basicStyle).dp
        requestLayout()
    }

    private fun calculateTargetWidth(basicStyle: BasicStyle): Float {
        return if (isOplusCapsuleShowing) basicStyle.widthInColorOSCapsuleMode else basicStyle.width
    }

    private fun ensureMarginLayoutParams(): MarginLayoutParams {
        val lp = layoutParams as? MarginLayoutParams
            ?: MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        if (layoutParams == null) layoutParams = lp
        return lp
    }

    private fun triggerSingleTransition() {
        singleLayoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        layoutTransition = singleLayoutTransition
    }

    private fun refreshLyricContentState() {
        val currentText = textView.text
        if (!currentText.isNullOrBlank()) {
            if (isBlacklistedText(currentText)) {
                textView.text = null
                hasLyricContent = false
                return
            }
            hasLyricContent = true
            return
        }

        val currentSong = textView.song
        if (currentSong != null && isBlacklistedSong(currentSong)) {
            textView.song = null
            hasLyricContent = false
            return
        }
        hasLyricContent = !(currentSong?.lyrics.isNullOrEmpty())
    }

    private fun isBlacklistedText(text: String?): Boolean {
        val normalizedText = text?.trim().orEmpty()
        if (normalizedText.isEmpty()) return false
        val patterns = currentStyle.basicStyle.lyricTextBlacklist
        if (patterns.isEmpty()) return false
        return patterns.any { pattern ->
            val p = pattern.trim()
            p.isNotEmpty() && normalizedText.contains(p, ignoreCase = true)
        }
    }

    private fun isBlacklistedSong(song: Song): Boolean {
        val lines = song.lyrics ?: return false
        if (lines.size != 1) return false
        return isBlacklistedLine(lines.first())
    }

    private fun isBlacklistedLine(line: RichLyricLine): Boolean {
        if (!isBlacklistedText(line.text)) return false
        return line.secondary.isNullOrBlank() &&
            line.translation.isNullOrBlank() &&
            line.roma.isNullOrBlank()
    }

    private fun updateNoLyricState(hasLyric: Boolean) {
        val timeoutSeconds = currentStyle.basicStyle.hideWhenNoLyricAfterSeconds

        if (!isPlaying || timeoutSeconds <= 0 || hasLyric) {
            noLyricTimeoutReached = false
            clearNoLyricTimeout()
            updateVisibility()
            return
        }

        if (noLyricSinceUptimeMs == null) {
            noLyricSinceUptimeMs = SystemClock.uptimeMillis()
        }

        val deadlineMs = noLyricSinceUptimeMs!! + timeoutSeconds * 1000L
        val remainingMs = deadlineMs - SystemClock.uptimeMillis()

        if (remainingMs <= 0L) {
            noLyricTimeoutReached = true
            clearNoLyricTimeout()
            updateVisibility()
            return
        }

        scheduleNoLyricTimeout(remainingMs)
        updateVisibility()
    }

    private fun scheduleNoLyricTimeout(delayMs: Long) {
        val token = ++noLyricTimeoutToken
        noLyricTimeoutRunnable?.let { removeCallbacks(it) }
        noLyricTimeoutRunnable = Runnable {
            if (token != noLyricTimeoutToken) return@Runnable
            noLyricTimeoutReached = true
            updateVisibility()
        }
        postDelayed(noLyricTimeoutRunnable, delayMs)
    }

    private fun clearNoLyricTimeout() {
        noLyricSinceUptimeMs = null
        noLyricTimeoutToken++
        noLyricTimeoutRunnable?.let { removeCallbacks(it) }
        noLyricTimeoutRunnable = null
    }

    /**
     * 休眠期间暂存的数据结构
     */
    private class PendingData(var position: Long = 0)
}
