/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import io.github.proify.android.extensions.crc32
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.isLandScape
import io.github.proify.android.extensions.setColorAlpha
import io.github.proify.android.extensions.toBitmap
import io.github.proify.lyricon.colorextractor.palette.ColorExtractor
import io.github.proify.lyricon.colorextractor.palette.ColorPaletteResult
import io.github.proify.lyricon.common.util.ResourceMapper
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.ClockColorMonitor
import io.github.proify.lyricon.xposed.systemui.hook.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController.isPlaying
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityController
import java.io.File
import java.util.Locale
import kotlin.math.min

/**
 * 状态栏歌词视图控制器：负责歌词视图的注入、位置锚定及显隐逻辑
 */
@SuppressLint("DiscouragedApi")
class StatusBarViewController(
    val statusBarView: ViewGroup,
    var currentLyricStyle: LyricStyle,
    // Keep gesture observation on the actual status-bar layout. Touch events do not bubble
    // from its children to the root view used for wide lyric layout injection.
    private val touchView: ViewGroup = statusBarView
) : ScreenStateMonitor.ScreenStateListener {
    companion object {
        const val TAG = "StatusBarViewController"
        private const val MAX_CLIP_RELAX_DEPTH = 12
    }

    val context: Context = statusBarView.context.applicationContext
    val visibilityController: ViewVisibilityController = ViewVisibilityController(statusBarView)
    val lyricView: StatusBarLyric by lazy { createLyricView(currentLyricStyle) }

    private val clockId: Int by lazy { ResourceMapper.getIdByName(context, "clock") }
    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null
    private var colorMonitorView: View? = null
    private var coverColorPaletteResult: ColorPaletteResult? = null
    private var systemStatusBarColor: SystemStatusBarColor? = null
    private var lastStatusColorLogFingerprint: String? = null

    // --- 双击临时隐藏歌词 ---
    private var userShowClock = false
    private var doubleTapSwitchEnabled = false
    private var lyricDoubleTapDetector: GestureDetector? = null
    private var clockDoubleTapDetector: GestureDetector? = null
    private var doubleTapTouchObserver: View.OnTouchListener? = null
    private var wrappedOriginalTouchListener: View.OnTouchListener? = null
    private var listenerInfoField: java.lang.reflect.Field? = null
    private var onTouchListenerField: java.lang.reflect.Field? = null

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        applyVisibilityRulesNow()
        healAfterHierarchyChange()
    }

    private val onColorChangeListener = object : OnColorChangeListener {

        private var colorFingerprint: String? = null
        override fun onColorChanged(color: Int, darkIntensity: Float) {
            val colorFingerprint = color.toString() + darkIntensity
            if (colorFingerprint == this.colorFingerprint) return
            this.colorFingerprint = colorFingerprint

            updateStatusColor(SystemStatusBarColor(color, darkIntensity))
        }
    }

    /**
     * 亮暗色切换等场景下 SystemUI 会重建部分状态栏子视图：
     * 时钟实例被替换会导致取色监听断流，歌词视图可能被移出父容器。
     * 在全局布局回调里做轻量自愈（均有同状态早退，开销可忽略）。
     */
    private fun healAfterHierarchyChange() {
        val clock = getClockView()
        if (clock != null && clock !== colorMonitorView) {
            colorMonitorView?.let { ClockColorMonitor.setListener(it, null) }
            colorMonitorView = clock
            ClockColorMonitor.setListener(clock, onColorChangeListener)
            updateStatusColor(clock.currentSystemStatusBarColor())
            YLog.info(TAG, "Clock view changed, color monitor re-registered")
        }

        if (!lyricView.isAttachedToWindow && statusBarView.isAttachedToWindow) {
            checkLyricViewExists()
        }

        ensureDoubleTapObserverInstalled()
    }

    // --- 生命周期与初始化 ---
    fun onCreate() {
        statusBarView.addOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.addOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.addListener(this)
        lyricView.onPlayingChanged = { playing ->
            // 双击隐藏只在本次播放内生效，停止播放即复位
            if (!playing) setUserShowClock(false)
        }

        colorMonitorView = getClockView()?.also {
            ClockColorMonitor.setListener(it, onColorChangeListener)
            updateStatusColor(it.currentSystemStatusBarColor())
        }

        statusBarView.doOnAttach { checkLyricViewExists() }
        YLog.info(tag = TAG, "Lyric view created for $statusBarView")
    }

    fun onDestroy() {
        statusBarView.removeOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.removeOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.removeListener(this)
        lyricView.onPlayingChanged = null
        uninstallDoubleTapObserver()
        colorMonitorView?.let { ClockColorMonitor.setListener(it, null) }
        YLog.info(tag = TAG, "Lyric view destroyed for $statusBarView")
    }

    // --- 核心业务逻辑 ---

    /**
     * 更新状态栏颜色，内部决定最终颜色
     */
    internal fun updateStatusColor(systemStatusBarColor: SystemStatusBarColor) {
        this.systemStatusBarColor = systemStatusBarColor

        val textStyle = currentLyricStyle.packageStyle.text
        var colorSource = "system"
        val statusColor = lyricView.currentStatusColor.apply {
            darkIntensity = systemStatusBarColor.darkIntensity

            val coverPalette = coverColorPaletteResult
            when {
                coverPalette != null
                        && textStyle.enableExtractCoverTextColor
                        && textStyle.enableExtractCoverTextGradient -> {
                    val themeColors = coverPalette
                        .let { if (isLightMode) it.lightModeColors else it.darkModeColors }

                    val gradient = themeColors.swatches
                    colorSource = "cover-gradient"
                    color = gradient
                    translucentColor = gradient.map {
                        it.setColorAlpha(0.75f)
                    }.toIntArray()
                }

                coverPalette != null
                        && textStyle.enableExtractCoverTextColor -> {
                    val themeColors = coverPalette
                        .let { if (isLightMode) it.lightModeColors else it.darkModeColors }

                    val primary = themeColors.primary
                    colorSource = "cover"
                    color = intArrayOf(primary)
                    translucentColor = intArrayOf(primary.setColorAlpha(0.75f))
                }

                else -> {
                    color = intArrayOf(systemStatusBarColor.color)
                    translucentColor =
                        intArrayOf(systemStatusBarColor.color.setColorAlpha(0.5f))
                }
            }
        }
        lyricView.setStatusBarColor(statusColor)
        logStatusColorApplied(colorSource, statusColor, systemStatusBarColor, textStyle)
    }

    /**
     * 更新歌词样式及位置，若锚点或顺序变化则重新注入视图
     */
    fun updateLyricStyle(lyricStyle: LyricStyle) {
        this.currentLyricStyle = lyricStyle
        val basicStyle = lyricStyle.basicStyle

        doubleTapSwitchEnabled = basicStyle.doubleTapSwitchClock
        if (doubleTapSwitchEnabled) {
            ensureDoubleTapObserverInstalled()
        } else {
            setUserShowClock(false)
            uninstallDoubleTapObserver()
        }

        val needUpdateLocation = lastAnchor != basicStyle.anchor
                || lastInsertionOrder != basicStyle.insertionOrder
                || !lyricView.isAttachedToWindow

        if (needUpdateLocation) {
            YLog.info(
                TAG,
                "Lyric location changed: ${basicStyle.anchor}, order ${basicStyle.insertionOrder}"
            )
            updateLocation(basicStyle)
        }
        lyricView.updateStyle(lyricStyle)
        logLyricWidthState("style-applied", basicStyle)
        lyricView.post { logLyricWidthState("post-style", basicStyle) }

        systemStatusBarColor?.let { updateStatusColor(it) }
    }

    fun updateCoverThemeColors(coverFile: File?) {
        coverColorPaletteResult = null
        try {
            val bitmap = coverFile?.toBitmap() ?: run {
                applyCurrentStatusColor()
                return
            }
            ColorExtractor.extractAsync(
                bitmap = bitmap,
                cacheKey = {
                    coverFile.crc32().toString()
                }) {
                coverColorPaletteResult = it
                YLog.info(
                    TAG,
                    "Cover palette extracted: result=${it != null} " +
                            "light=${it?.lightModeColors?.swatches.describeColors()} " +
                            "dark=${it?.darkModeColors?.swatches.describeColors()}"
                )
                applyCurrentStatusColor()
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
        relaxAncestorClipping(anchorParent)

        // 标记内部移除，避免触发冗余的 detach 逻辑
        internalRemoveLyricViewFlag = true

        (lyricView.parent as? ViewGroup)?.removeView(lyricView)

        val anchorIndex = anchorParent.indexOfChild(anchorView)

        val lp = createLyricLayoutParams(baseStyle)

        // 执行插入：在前或在后
        val targetIndex =
            if (baseStyle.insertionOrder == BasicStyle.INSERTION_ORDER_AFTER) anchorIndex + 1
            else anchorIndex
        anchorParent.addView(lyricView, targetIndex, lp)

        lyricView.updateVisibility()
        lastAnchor = anchor
        lastInsertionOrder = baseStyle.insertionOrder
        internalRemoveLyricViewFlag = false

        YLog.info(TAG, "Lyric injected: anchor $anchor, index $targetIndex")
        logLyricWidthState("injected", baseStyle, anchorParent)
    }

    private fun createLyricLayoutParams(baseStyle: BasicStyle): ViewGroup.LayoutParams {
        val requestedWidth = calculateRequestedLyricWidth(baseStyle)
        return when (val current = lyricView.layoutParams) {
            is ViewGroup.MarginLayoutParams -> current.apply {
                width = requestedWidth
                if (height == 0) height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            is ViewGroup.LayoutParams -> current.apply {
                width = requestedWidth
                if (height == 0) height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            else -> ViewGroup.MarginLayoutParams(
                requestedWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun calculateRequestedLyricWidth(baseStyle: BasicStyle): Int {
        val requested = baseStyle.getAutoWidth(
            context.isLandScape(),
            isOplusCapsuleShowing = OplusCapsuleHooker.isShowing
        ).dp
        val screenWidth = statusBarView.resources.displayMetrics.widthPixels
        return if (screenWidth > 0 && requested > 0) min(requested, screenWidth) else requested
    }

    private fun relaxAncestorClipping(start: ViewGroup) {
        var current: ViewGroup? = start
        var changed = false
        var depth = 0
        while (current != null && depth < MAX_CLIP_RELAX_DEPTH) {
            if (current.clipChildren || current.clipToPadding) changed = true
            current.clipChildren = false
            current.clipToPadding = false
            if (current === statusBarView) break
            current = current.parent as? ViewGroup
            depth++
        }
        if (changed) {
            YLog.info(TAG, "Lyric ancestor clipping relaxed from ${start.javaClass.name}")
        }
    }

    private fun logLyricWidthState(
        stage: String,
        baseStyle: BasicStyle,
        parent: ViewGroup? = lyricView.parent as? ViewGroup
    ) {
        val lp = lyricView.layoutParams
        YLog.info(
            TAG,
            "Lyric width $stage: requested=${calculateRequestedLyricWidth(baseStyle)} " +
                    "lpWidth=${lp?.width} lpHeight=${lp?.height} " +
                    "measured=${lyricView.measuredWidth} width=${lyricView.width} " +
                    "parent=${parent?.javaClass?.name} parentWidth=${parent?.width} " +
                    "statusWidth=${statusBarView.width} lpClass=${lp?.javaClass?.name}"
        )
    }

    fun checkLyricViewExists() {
        if (lyricView.isAttachedToWindow) return
        lastAnchor = ""
        lastInsertionOrder = -1
        updateLyricStyle(currentLyricStyle)
    }

    // --- 辅助方法 ---

    private fun getClockView(): View? = statusBarView.findViewById(clockId)

    private fun applyCurrentStatusColor() {
        updateStatusColor(
            systemStatusBarColor
                ?: colorMonitorView?.currentSystemStatusBarColor()
                ?: SystemStatusBarColor(color = Color.BLACK, darkIntensity = 0f)
        )
    }

    private fun View.currentSystemStatusBarColor(): SystemStatusBarColor {
        val color = (this as? TextView)?.currentTextColor ?: Color.BLACK
        return SystemStatusBarColor(
            color = color,
            darkIntensity = ColorUtils.calculateLuminance(color).toFloat()
        )
    }

    private fun logStatusColorApplied(
        colorSource: String,
        statusColor: io.github.proify.lyricon.statusbarlyric.StatusColor,
        systemStatusBarColor: SystemStatusBarColor,
        textStyle: io.github.proify.lyricon.lyric.style.TextStyle
    ) {
        val customColor = textStyle.color(statusColor.isLightMode)
        val visualSource = if (textStyle.enableCustomTextColor) "custom" else colorSource
        val fingerprint = listOf(
            visualSource,
            colorSource,
            statusColor.isLightMode,
            statusColor.color.contentHashCode(),
            statusColor.translucentColor.contentHashCode(),
            textStyle.enableCustomTextColor,
            customColor?.normal?.contentHashCode(),
            customColor?.background?.contentHashCode(),
            customColor?.highlight?.contentHashCode()
        ).joinToString("|")

        if (fingerprint == lastStatusColorLogFingerprint) return
        lastStatusColorLogFingerprint = fingerprint

        YLog.info(
            TAG,
            "Status color applied: visual=$visualSource statusSource=$colorSource " +
                    "lightMode=${statusColor.isLightMode} " +
                    "system=${systemStatusBarColor.color.toColorHex()} " +
                    "darkIntensity=${systemStatusBarColor.darkIntensity} " +
                    "status=${statusColor.color.describeColors()} " +
                    "translucent=${statusColor.translucentColor.describeColors()} " +
                    "customNormal=${customColor?.normal.describeColors()} " +
                    "customBg=${customColor?.background.describeColors()} " +
                    "customHighlight=${customColor?.highlight.describeColors()}"
        )
    }

    private fun IntArray?.describeColors(): String =
        this?.let { "size=${it.size} first=${it.firstOrNull()?.toColorHex() ?: "none"}" }
            ?: "null"

    private fun Int.toColorHex(): String =
        String.format(Locale.US, "#%08X", this)

    private var wasPlayingBeforeVisibilityUpdate: Boolean = false

    fun computeShouldApplyPlayingRules(): Boolean {
        if (userShowClock) return false
        return isPlaying && when {
            lyricView.isDisabledVisible -> !lyricView.isHideOnLockScreen()
            lyricView.isVisible -> true
            else -> false
        }
    }

    private fun applyVisibilityRulesNow() {
        val isPlaying = computeShouldApplyPlayingRules()
        fun apply() {
            visibilityController.applyVisibilityRules(
                rules = currentLyricStyle.basicStyle.visibilityRules,
                isPlaying = isPlaying
            )
        }

        if (!isPlaying) {
            // 仅在之前是播放状态时才更新（避免重复更新非播放状态的隐藏逻辑）
            if (wasPlayingBeforeVisibilityUpdate) {
                apply()
                wasPlayingBeforeVisibilityUpdate = false
            }
        } else {
            apply()
            wasPlayingBeforeVisibilityUpdate = true
        }
    }

    private fun createLyricView(style: LyricStyle) =
        StatusBarLyric(context, style, getClockView() as? TextView)

    // --- 双击临时隐藏歌词 ---
    //
    // 触摸接入方式：包装 statusBarView 上已有的 OnTouchListener 并原样转发。
    // 部分 ROM（HyperOS 等）的状态栏下拉手势依赖挂在该视图上的监听，
    // 直接 setOnTouchListener 会把它顶掉，导致状态栏拉不下来；
    // 因此只有成功读到现有监听（可为 null）时才安装，观察者自身从不消费事件。

    private fun ensureDoubleTapObserverInstalled() {
        if (!doubleTapSwitchEnabled) return
        val current = readStatusBarTouchListener().getOrElse {
            YLog.error(TAG, "Cannot inspect status bar touch listener, double-tap unavailable", it)
            return
        }
        val observer = doubleTapTouchObserver ?: createDoubleTapObserver().also {
            doubleTapTouchObserver = it
        }
        if (current === observer) return

        // current 可能是系统自己的手势监听（也可能为 null），包装转发而不是替换
        wrappedOriginalTouchListener = current
        touchView.setOnTouchListener(observer)
        YLog.info(TAG, "Double-tap touch observer installed, wrapped=${current?.javaClass?.name}")
    }

    private fun uninstallDoubleTapObserver() {
        val observer = doubleTapTouchObserver ?: return
        val current = readStatusBarTouchListener().getOrNull()
        if (current === observer) {
            touchView.setOnTouchListener(wrappedOriginalTouchListener)
            // 仅在确认还原后清空：观察者若仍在链上，被包装的原监听不能丢
            wrappedOriginalTouchListener = null
        }
    }

    private fun readStatusBarTouchListener(): Result<View.OnTouchListener?> = runCatching {
        val infoField = listenerInfoField
            ?: View::class.java.getDeclaredField("mListenerInfo")
                .apply { isAccessible = true }
                .also { listenerInfoField = it }
        val listenerInfo = infoField.get(touchView) ?: return@runCatching null
        val touchField = onTouchListenerField
            ?: listenerInfo.javaClass.getDeclaredField("mOnTouchListener")
                .apply { isAccessible = true }
                .also { onTouchListenerField = it }
        touchField.get(listenerInfo) as? View.OnTouchListener
    }

    private fun createDoubleTapObserver(): View.OnTouchListener {
        val mainHandler = Handler(context.mainLooper)
        lyricDoubleTapDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (doubleTapSwitchEnabled && isPlaying) setUserShowClock(true)
                    return true
                }
            },
            mainHandler
        )
        clockDoubleTapDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (doubleTapSwitchEnabled && isPlaying) setUserShowClock(false)
                    return true
                }
            },
            mainHandler
        )
        return View.OnTouchListener { view, event ->
            observeDoubleTapEvent(event)
            // 永不消费：交还给被包装的系统监听，没有则返回 false，
            // 让状态栏自身的 onTouchEvent（下拉手势）照常执行
            wrappedOriginalTouchListener?.onTouch(view, event) ?: false
        }
    }

    private fun observeDoubleTapEvent(event: MotionEvent) {
        if (!doubleTapSwitchEnabled || !isPlaying) return
        if (!userShowClock) {
            if (lyricView.isShown && isTouchInside(lyricView, event)) {
                lyricDoubleTapDetector?.onTouchEvent(event)
            }
        } else {
            val clock = getClockView()
            if (clock != null && clock.isShown && isTouchInside(clock, event)) {
                clockDoubleTapDetector?.onTouchEvent(event)
            }
        }
    }

    private fun setUserShowClock(show: Boolean) {
        if (userShowClock == show) return
        userShowClock = show
        lyricView.userHideLyric = show
        applyVisibilityRulesNow()
        if (!show) {
            // 恢复歌词时重刷翻译显示配置，避免副行残留为原文/空行
            LyricViewController.refreshTranslationDisplay()
        }
        YLog.info(TAG, "User double-tap switch: showClock=$show")
    }

    private fun isTouchInside(view: View, event: MotionEvent): Boolean {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        return event.rawX in left..(left + width) && event.rawY in top..(top + height)
    }

    fun highlightView(idName: String?) {
        YLog.info(TAG, "Highlighting view id:$idName")

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
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {}
    }

    override fun onScreenOn() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
    }

    override fun onScreenOff() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = true
    }

    override fun onScreenUnlocked() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
    }

    fun onDisableStateChanged(shouldHide: Boolean) {
        lyricView.isDisabledVisible = shouldHide
    }

    override fun equals(other: Any?): Boolean =
        (this === other) ||
                (other is StatusBarViewController && statusBarView == other.statusBarView)

    override fun hashCode(): Int = 31 * 17 + statusBarView.hashCode()

    data class SystemStatusBarColor(val color: Int, val darkIntensity: Float)
}
