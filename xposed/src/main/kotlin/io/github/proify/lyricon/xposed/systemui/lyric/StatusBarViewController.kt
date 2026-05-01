/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.setColorAlpha
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.colorextractor.palette.ColorExtractor
import io.github.proify.lyricon.colorextractor.palette.ColorPaletteResult
import io.github.proify.lyricon.common.util.ResourceMapper
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.VisibilityRule
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.ClockColorMonitor
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityController
import java.io.File
import kotlin.math.max

/**
 * 状态栏歌词视图控制器：负责歌词视图的注入、位置锚定及显隐逻辑
 */
@SuppressLint("DiscouragedApi")
class StatusBarViewController(
    val statusBarView: ViewGroup,
    var currentLyricStyle: LyricStyle
) : ScreenStateMonitor.ScreenStateListener {
    private companion object {
        const val TAG = "StatusBarViewController"
    }

    val context: Context = statusBarView.context.applicationContext
    val visibilityController = ViewVisibilityController(statusBarView)
    val lyricView: StatusBarLyric by lazy { createLyricView(currentLyricStyle) }

    private val clockId: Int by lazy { ResourceMapper.getIdByName(context, "clock") }
    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null
    private var userShowClock = false
    private var doubleTapSwitchEnabled = false
    private var clockView: TextView? = null
    private var lyricDoubleTapDetector: GestureDetector? = null
    private var clockDoubleTapDetector: GestureDetector? = null
    private var statusBarTouchListener: View.OnTouchListener? = null
    private var colorMonitorView: View? = null
    private var coverColorPaletteResult: ColorPaletteResult? = null
    private var systemStatusBarColor: SystemStatusBarColor? = null
    private var isClockAutoHiddenByDynamicWidth = false
    private var originalClockVisibilityBeforeDynamicHide: Int? = null
    private var pullDownFreezeArmed = false
    private var pullDownStartY = 0f
    private var isDynamicWidthFrozenByPullDown = false
    private val pullDownFreezeSlopPx = 8.dp.toFloat()

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (clockView == null) {
            setupDoubleTapHandlers()
        }
        applyVisibilityRulesNow()
    }

    // --- 生命周期与初始化 ---
    fun onCreate() {
        statusBarView.addOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.addOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.addListener(this)
        lyricView.onPlayingChanged = { playing ->
            if (!playing) {
                setUserShowClock(false)
                setDynamicWidthFrozenForPullDown(false)
            }
            updateDynamicWidthClockVisibility()
            LyricViewController.notifyLyricVisibilityChanged()
        }
        setupDoubleTapHandlers()

        val onColorChangeListener = object : OnColorChangeListener {

            private var colorFingerprint: String? = null
            override fun onColorChanged(color: Int, darkIntensity: Float) {
                val colorFingerprint = color.toString() + darkIntensity
                if (colorFingerprint == this.colorFingerprint) return
                this.colorFingerprint = colorFingerprint

                updateStatusColor(SystemStatusBarColor(color, darkIntensity))
            }
        }

        colorMonitorView = getClockView()?.also {
            ClockColorMonitor.setListener(it, onColorChangeListener)
        }

        statusBarView.doOnAttach { checkLyricViewExists() }
        YLog.info(TAG, "Lyric view created for $statusBarView")
    }

    fun onDestroy() {
        statusBarView.removeOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.removeOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.removeListener(this)
        lyricView.onPlayingChanged = null
        statusBarTouchListener?.let { statusBarView.setOnTouchListener(null) }
        statusBarTouchListener = null
        lyricDoubleTapDetector = null
        clockDoubleTapDetector = null
        colorMonitorView?.let { ClockColorMonitor.setListener(it, null) }
        setDynamicWidthFrozenForPullDown(false)
        restoreClockVisibilityFromDynamicWidth()
        LyricViewController.notifyLyricVisibilityChanged()
        YLog.info(TAG, "Lyric view destroyed for $statusBarView")
    }

    // --- 核心业务逻辑 ---

    /**
     * 更新状态栏颜色，内部决定最终颜色
     */
    private fun updateStatusColor(systemStatusBarColor: SystemStatusBarColor) {
        this.systemStatusBarColor = systemStatusBarColor

        val textStyle = currentLyricStyle.packageStyle.text
        lyricView.apply {
            currentStatusColor.apply {
                this.darkIntensity = systemStatusBarColor.darkIntensity

                val coverColorPaletteResult = coverColorPaletteResult
                when {
                    coverColorPaletteResult != null
                            && textStyle.enableExtractCoverTextColor
                            && textStyle.enableExtractCoverTextGradient -> {
                        val themeColors = coverColorPaletteResult
                            .let { if (isLightMode) it.lightModeColors else it.darkModeColors }

                        val gradient = themeColors.swatches

                        this.color = gradient
                        this.translucentColor = gradient.map {
                            it.setColorAlpha(0.75f)
                        }.toIntArray()
                    }

                    coverColorPaletteResult != null
                            && textStyle.enableExtractCoverTextColor -> {
                        val themeColors = coverColorPaletteResult
                            .let { if (isLightMode) it.lightModeColors else it.darkModeColors }

                        val primary = themeColors.primary

                        this.color = intArrayOf(primary)
                        this.translucentColor = intArrayOf(primary.setColorAlpha(0.75f))
                    }

                    else -> {
                        this.color = intArrayOf(systemStatusBarColor.color)
                        this.translucentColor =
                            intArrayOf(systemStatusBarColor.color.setColorAlpha(0.5f))
                    }
                }
            }
            setStatusBarColor(currentStatusColor)
        }
    }

    /**
     * 更新歌词样式及位置，若锚点或顺序变化则重新注入视图
     */
    fun updateLyricStyle(lyricStyle: LyricStyle) {
        this.currentLyricStyle = lyricStyle
        val basicStyle = lyricStyle.basicStyle
        doubleTapSwitchEnabled = basicStyle.doubleTapSwitchClock
        if (!doubleTapSwitchEnabled) {
            setUserShowClock(false)
        }
        if (!lyricStyle.basicStyle.dynamicWidthEnabled) {
            setDynamicWidthFrozenForPullDown(false)
        }

        val needUpdateLocation = lastAnchor != basicStyle.anchor
                || lastInsertionOrder != basicStyle.insertionOrder
                || !lyricView.isAttachedToWindow

        if (needUpdateLocation) {
            YLog.info(TAG, "Lyric location changed: ${basicStyle.anchor}, order ${basicStyle.insertionOrder}")
            updateLocation(basicStyle)
        }
        lyricView.updateStyle(lyricStyle)

        systemStatusBarColor?.let { updateStatusColor(it) }
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    fun updateCoverThemeColors(coverFile: File?) {
        coverColorPaletteResult = null
        try {
            val bitmap = coverFile?.toBitmap() ?: return
            ColorExtractor.extractAsync(bitmap) {
                coverColorPaletteResult = it
                systemStatusBarColor?.let { updateStatusColor(it) }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            YLog.error(TAG, "Failed to extract cover theme colors", e)
        }
    }

    /**
     * 处理视图注入逻辑：根据 BasicStyle 寻找锚点并插入歌词视图
     */
    private fun updateLocation(baseStyle: BasicStyle) {
        val anchor = baseStyle.anchor
        val anchorId = context.resources.getIdentifier(anchor, "id", context.packageName)
        val anchorView = statusBarView.findViewById<View>(anchorId) ?: return run {
            YLog.error(TAG, "Lyric anchor view $anchor not found")
        }

        val anchorParent = anchorView.parent as? ViewGroup ?: return run {
            YLog.error(TAG, "Lyric anchor parent not found")
        }

        // 标记内部移除，避免触发冗余的 detach 逻辑
        internalRemoveLyricViewFlag = true

        (lyricView.parent as? ViewGroup)?.removeView(lyricView)

        val anchorIndex = anchorParent.indexOfChild(anchorView)
        val lp = lyricView.layoutParams ?: ViewGroup.LayoutParams(
            baseStyle.width.dp,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 执行插入：在前或在后
        val targetIndex =
            if (baseStyle.insertionOrder == BasicStyle.INSERTION_ORDER_AFTER) anchorIndex + 1 else anchorIndex
        anchorParent.addView(lyricView, targetIndex, lp)

        lyricView.updateVisibility()
        lastAnchor = anchor
        lastInsertionOrder = baseStyle.insertionOrder
        internalRemoveLyricViewFlag = false

        YLog.info(TAG, "Lyric injected: anchor $anchor, index $targetIndex")
    }

    fun checkLyricViewExists() {
        if (lyricView.isAttachedToWindow) return
        lastAnchor = ""
        lastInsertionOrder = -1
        updateLyricStyle(currentLyricStyle)
    }

    // --- 辅助方法 ---

    private fun getClockView(): View? = statusBarView.findViewById(clockId)

    private fun hasManualClockHideRule(rules: List<VisibilityRule>?): Boolean {
        if (rules.isNullOrEmpty()) return false
        return rules.any { it.id == "clock" && it.mode == VisibilityRule.MODE_HIDE_WHEN_PLAYING }
    }

    private fun hideClockForDynamicWidth() {
        val clockView = getClockView() ?: return
        if (isClockAutoHiddenByDynamicWidth) return
        originalClockVisibilityBeforeDynamicHide = clockView.visibility
        clockView.visibility = View.GONE
        isClockAutoHiddenByDynamicWidth = true
    }

    private fun restoreClockVisibilityFromDynamicWidth() {
        val clockView = getClockView()
        if (clockView == null) {
            isClockAutoHiddenByDynamicWidth = false
            originalClockVisibilityBeforeDynamicHide = null
            return
        }
        if (isClockAutoHiddenByDynamicWidth) {
            clockView.visibility = originalClockVisibilityBeforeDynamicHide ?: View.VISIBLE
            originalClockVisibilityBeforeDynamicHide = null
            isClockAutoHiddenByDynamicWidth = false
        }
    }

    private fun updateDynamicWidthClockVisibility() {
        val basicStyle = currentLyricStyle.basicStyle
        if (hasManualClockHideRule(basicStyle.visibilityRules)) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        // 用户手动切到时钟时，强制恢复时钟，避免双击后因动态宽度仍被自动隐藏
        if (userShowClock) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        if (!basicStyle.dynamicWidthEnabled
            || !basicStyle.dynamicWidthAutoHideClock
            || basicStyle.anchor != "clock"
            || !LyricViewController.isPlaying
            || lyricView.visibility != View.VISIBLE
        ) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        val maxWidthPx = basicStyle.width.dp
        if (maxWidthPx <= 0) {
            restoreClockVisibilityFromDynamicWidth()
            return
        }

        var contentWidth = lyricView.width
        contentWidth = max(contentWidth, lyricView.measuredWidth)
        contentWidth = max(contentWidth, lyricView.textView.width)
        contentWidth = max(contentWidth, lyricView.textView.measuredWidth)

        if (contentWidth > maxWidthPx) {
            hideClockForDynamicWidth()
        } else {
            restoreClockVisibilityFromDynamicWidth()
        }
    }

    private fun computeShouldApplyPlayingRules(): Boolean {
        if (userShowClock) return false
        return LyricViewController.isPlaying && when {
            lyricView.isDisabledVisible -> !lyricView.isHideOnLockScreen()
            lyricView.isVisible -> true
            else -> false
        }
    }

    private fun applyVisibilityRulesNow() {
        visibilityController.applyVisibilityRules(
            rules = currentLyricStyle.basicStyle.visibilityRules,
            isPlaying = computeShouldApplyPlayingRules()
        )
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    private fun createLyricView(style: LyricStyle) =
        StatusBarLyric(context, style, getClockView() as? TextView)

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDoubleTapHandlers() {
        clockView = getClockView() as? TextView

        if (lyricDoubleTapDetector == null) {
            lyricDoubleTapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (!doubleTapSwitchEnabled || !LyricViewController.isPlaying) return false
                        setUserShowClock(true)
                        return true
                    }
                }
            )
        }

        if (clockDoubleTapDetector == null) {
            clockDoubleTapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (!doubleTapSwitchEnabled || !LyricViewController.isPlaying) return false
                        setUserShowClock(false)
                        return true
                    }
                }
            )
        }

        if (statusBarTouchListener == null) {
            statusBarTouchListener = View.OnTouchListener { _, event ->
                handleDynamicWidthFreezeTouch(event)
                if (!doubleTapSwitchEnabled || !LyricViewController.isPlaying) return@OnTouchListener false

                if (userShowClock) {
                    // 时钟模式下允许整块区域双击切回歌词，避免 clock 被自动隐藏时无响应
                    clockDoubleTapDetector?.onTouchEvent(event)
                    return@OnTouchListener false
                }

                if (isTouchInside(lyricView, event)) {
                    lyricDoubleTapDetector?.onTouchEvent(event)
                } else {
                    clockView?.let { clock ->
                        if (isTouchInside(clock, event)) {
                            clockDoubleTapDetector?.onTouchEvent(event)
                        }
                    }
                }
                false
            }
            statusBarView.setOnTouchListener(statusBarTouchListener)
        }
    }

    private fun setUserShowClock(show: Boolean) {
        if (userShowClock == show) return
        userShowClock = show
        lyricView.setUserHideLyric(show)
        lyricView.updateVisibility()
        if (!show) {
            // 恢复歌词时重绑一次翻译显示，避免只剩原文或副行状态错乱
            LyricViewController.refreshLyricTranslationDisplayConfig()
        }
        applyVisibilityRulesNow()
    }

    private fun shouldFreezeDynamicWidthDuringPullDown(): Boolean {
        val basicStyle = currentLyricStyle.basicStyle
        return basicStyle.dynamicWidthEnabled
                && LyricViewController.isPlaying
                && lyricView.isAttachedToWindow
                && lyricView.visibility == View.VISIBLE
    }

    private fun setDynamicWidthFrozenForPullDown(frozen: Boolean) {
        if (isDynamicWidthFrozenByPullDown == frozen) return
        isDynamicWidthFrozenByPullDown = frozen
        lyricView.setDynamicWidthFrozen(frozen)
        if (!frozen) {
            updateDynamicWidthClockVisibility()
        }
    }

    private fun handleDynamicWidthFreezeTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullDownStartY = event.rawY
                pullDownFreezeArmed = shouldFreezeDynamicWidthDuringPullDown()
                if (!pullDownFreezeArmed) {
                    setDynamicWidthFrozenForPullDown(false)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!pullDownFreezeArmed || isDynamicWidthFrozenByPullDown) return
                if (event.rawY - pullDownStartY > pullDownFreezeSlopPx) {
                    setDynamicWidthFrozenForPullDown(true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pullDownFreezeArmed = false
                setDynamicWidthFrozenForPullDown(false)
            }
        }
    }

    private fun isTouchInside(view: View, event: MotionEvent): Boolean {
        if (!view.isShown) return false
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + width
        val bottom = top + height
        return event.rawX in left..right && event.rawY in top..bottom
    }

    fun highlightView(idName: String?) {
        lastHighlightView?.background = null
        if (idName.isNullOrBlank()) return

        val id = ResourceMapper.getIdByName(context, idName)
        statusBarView.findViewById<View>(id)?.let { view ->
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#FF3582FF".toColorInt())
                cornerRadius = 20.dp.toFloat()
            }
            lastHighlightView = view
        } ?: YLog.error(TAG, "Highlight target $idName not found")
    }

    private val lyricAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            YLog.info(TAG, "LyricView attached")
            applyVisibilityRulesNow()
        }

        override fun onViewDetachedFromWindow(v: View) {
            YLog.info(TAG, "LyricView detached")
            if (!internalRemoveLyricViewFlag) {
                checkLyricViewExists()
            } else {
                YLog.info(TAG, "LyricView detached by internal flag")
            }
        }
    }

    private val statusBarAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            setupDoubleTapHandlers()
        }
        override fun onViewDetachedFromWindow(v: View) {
            setDynamicWidthFrozenForPullDown(false)
            restoreClockVisibilityFromDynamicWidth()
            LyricViewController.notifyLyricVisibilityChanged()
        }
    }

    override fun onScreenOn() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    override fun onScreenOff() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = true
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    override fun onScreenUnlocked() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    fun onDisableStateChanged(shouldHide: Boolean) {
        lyricView.isDisabledVisible = shouldHide
        updateDynamicWidthClockVisibility()
        LyricViewController.notifyLyricVisibilityChanged()
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is StatusBarViewController && statusBarView === other.statusBarView)

    override fun hashCode(): Int = 31 * 17 + statusBarView.hashCode()

    data class SystemStatusBarColor(val color: Int, val darkIntensity: Float)
}
