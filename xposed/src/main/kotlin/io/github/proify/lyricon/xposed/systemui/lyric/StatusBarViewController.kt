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
import io.github.proify.lyricon.app.bridge.AppBridge.LyricGesturePrefs
import io.github.proify.lyricon.colorextractor.palette.ColorExtractor
import io.github.proify.lyricon.colorextractor.palette.ColorPaletteResult
import io.github.proify.lyricon.common.util.ResourceMapper
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.ClockViewFinder
import io.github.proify.lyricon.xposed.systemui.hook.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.hook.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController.isPlaying
import io.github.proify.lyricon.xposed.systemui.lyric.control.LyricControlPopup
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

    // --- 手势控制状态 (随偏好热更新) ---
    private var gestureEnabled: Boolean = LyricGesturePrefs.DEFAULT_ENABLED
    private var swipeLeftAction: Int = LyricGesturePrefs.DEFAULT_SWIPE_LEFT
    private var swipeRightAction: Int = LyricGesturePrefs.DEFAULT_SWIPE_RIGHT
    private var tapAction: Int = LyricGesturePrefs.DEFAULT_TAP
    private var longPressAction: Int = LyricGesturePrefs.DEFAULT_LONG_PRESS

    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null
    private var colorMonitorView: View? = null
    private var coverColorPaletteResult: ColorPaletteResult? = null
    private var systemStatusBarColor: SystemStatusBarColor? = null
    private var lastStatusColorLogFingerprint: String? = null

    // --- 临时隐藏歌词（由 ACTION_TOGGLE_CLOCK 手势驱动）---
    private var userShowClock = false

    // 歌词隐藏期间的恢复通道：歌词视图为 GONE 收不到触摸，
    // 需要另挂监听在状态栏上，观察时钟区域的点击来恢复。
    private var restoreTouchObserver: View.OnTouchListener? = null
    private var wrappedOriginalTouchListener: View.OnTouchListener? = null
    private var restoreGestureDetector: GestureDetector? = null
    private var listenerInfoField: java.lang.reflect.Field? = null
    private var onTouchListenerField: java.lang.reflect.Field? = null

    private val colorChangeListener = object : OnColorChangeListener {

        private var colorFingerprint: String? = null
        override fun onColorChanged(color: Int, darkIntensity: Float) {
            val colorFingerprint = color.toString() + darkIntensity
            if (colorFingerprint == this.colorFingerprint) return
            this.colorFingerprint = colorFingerprint

            updateStatusColor(SystemStatusBarColor(color, darkIntensity))
        }
    }

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        applyVisibilityRulesNow()
        healAfterHierarchyChange()
        StatusBarColorMonitor.refresh()
    }

    /**
     * 亮暗色切换等场景下 SystemUI 会重建部分状态栏子视图：
     * 时钟实例被替换会导致取色监听断流，歌词视图可能被移出父容器。
     * 在全局布局回调里做轻量自愈（均有同状态早退，开销可忽略）。
     */
    private fun healAfterHierarchyChange() {
        val clock = getClockView()
        if (clock != null && clock !== colorMonitorView) {
            colorMonitorView?.let { StatusBarColorMonitor.unbindClockView(it) }
            colorMonitorView = clock
            StatusBarColorMonitor.bindClockView(clock)
            StatusBarColorMonitor.refresh()
            YLog.info(TAG, "Clock view changed, color monitor re-registered")
        }

        if (!lyricView.isAttachedToWindow && statusBarView.isAttachedToWindow) {
            checkLyricViewExists()
        }
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

        // 手势控制:读取偏好并绑定回调,手势动作可配置
        refreshGestureConfig()
        lyricView.gestureListener = { gesture -> onLyricGesture(gesture) }

        StatusBarColorMonitor.bindStatusBar(statusBarView)
        colorMonitorView = getClockView()
        StatusBarColorMonitor.bindClockView(colorMonitorView)
        StatusBarColorMonitor.addListener(colorChangeListener)

        statusBarView.doOnAttach { checkLyricViewExists() }
        YLog.info(tag = TAG, "Lyric view created for $statusBarView")
    }

    fun onDestroy() {
        statusBarView.removeOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.removeOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.removeListener(this)
        lyricView.onPlayingChanged = null
        lyricView.gestureListener = null
        uninstallRestoreObserver()
        lyricView.setOnClickListener(null)
        LyricControlPopup.dismissIfOwnedBy(lyricView)
        StatusBarColorMonitor.removeListener(colorChangeListener)
        colorMonitorView?.let { StatusBarColorMonitor.unbindClockView(it) }
        colorMonitorView = null
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
        refreshGestureConfig()

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
        logAnchorContainer(anchorParent, anchor, targetIndex)
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

    private fun getClockView(): View? = ClockViewFinder.find(statusBarView)

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

    // --- 手势控制 ---

    /**
     * 从偏好刷新手势配置,并同步视图的手势开关与点击行为。
     *
     * 手势关闭时保留旧版"单击打开控制面板"的行为(通过点击监听器委托)。
     */
    private fun refreshGestureConfig() {
        gestureEnabled = LyricPrefs.gestureEnabled
        swipeLeftAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_SWIPE_LEFT,
            LyricGesturePrefs.DEFAULT_SWIPE_LEFT
        )
        swipeRightAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_SWIPE_RIGHT,
            LyricGesturePrefs.DEFAULT_SWIPE_RIGHT
        )
        tapAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_TAP,
            LyricGesturePrefs.DEFAULT_TAP
        )
        longPressAction = LyricPrefs.gestureAction(
            LyricGesturePrefs.KEY_LONG_PRESS,
            LyricGesturePrefs.DEFAULT_LONG_PRESS
        )

        lyricView.gestureEnabled = gestureEnabled
        lyricView.hapticEnabled = LyricPrefs.gestureHapticEnabled
        if (gestureEnabled) {
            lyricView.setOnClickListener(null)
        } else {
            lyricView.setOnClickListener { v ->
                LyricControlPopup.show(v)
            }
        }
        // setOnClickListener(null) 会关闭 clickable,这里恢复以保持手势模式下的点击语义(无障碍)
        lyricView.isClickable = true
    }

    /**
     * 手势回调入口:根据当前配置将手势映射为动作并执行
     */
    private fun onLyricGesture(gesture: StatusBarLyric.GestureType) {
        if (!gestureEnabled) return

        val action = when (gesture) {
            StatusBarLyric.GestureType.SWIPE_LEFT -> swipeLeftAction
            StatusBarLyric.GestureType.SWIPE_RIGHT -> swipeRightAction
            StatusBarLyric.GestureType.TAP -> tapAction
            StatusBarLyric.GestureType.LONG_PRESS -> longPressAction
        }

        when (action) {
            LyricGesturePrefs.ACTION_NONE -> Unit
            LyricGesturePrefs.ACTION_TOGGLE_PLAY -> PlaybackControl.togglePlay()
            LyricGesturePrefs.ACTION_PREVIOUS -> PlaybackControl.previous()
            LyricGesturePrefs.ACTION_NEXT -> PlaybackControl.next()
            LyricGesturePrefs.ACTION_OPEN_CONTROL -> LyricControlPopup.show(lyricView)
            LyricGesturePrefs.ACTION_TOGGLE_CLOCK -> setUserShowClock(!userShowClock)
            else -> YLog.warning(TAG, "Unknown gesture action: $action")
        }
    }

    /**
     * 记录锚点容器类型与插入后的实际子视图顺序。
     *
     * 插入位置靠 [ViewGroup.addView] 的 index 决定，这只对按索引排布的容器
     * （LinearLayout 等）成立；若容器是 ConstraintLayout / 自定义排版容器，
     * 子视图顺序由约束或自定义 onLayout 决定，index 不影响视觉先后。
     * 出现"插入顺序设置无效"时，先看这条日志里的容器类型。
     */
    private fun logAnchorContainer(parent: ViewGroup, anchor: String, targetIndex: Int) {
        val order = (0 until parent.childCount).joinToString(", ") { i ->
            val child = parent.getChildAt(i)
            val name = runCatching {
                if (child.id != View.NO_ID) child.resources.getResourceEntryName(child.id) else null
            }.getOrNull() ?: child.javaClass.simpleName
            "$i:$name"
        }
        YLog.info(
            TAG,
            "Anchor container: ${parent.javaClass.name} anchor=$anchor " +
                    "insertedAt=$targetIndex children=[$order]"
        )
    }

    /**
     * 临时以时钟替代歌词显示。
     *
     * 由 [LyricGesturePrefs.ACTION_TOGGLE_CLOCK] 手势触发，只在本次播放内生效，
     * 停止播放时由 [lyricView].onPlayingChanged 复位。
     */
    private fun setUserShowClock(show: Boolean) {
        if (userShowClock == show) return
        userShowClock = show
        lyricView.userHideLyric = show
        applyVisibilityRulesNow()
        if (show) {
            // 歌词一旦隐藏就不再接收触摸，恢复只能靠状态栏上的观察者。
            // 观察者装不上就没有恢复出口，此时放弃隐藏而不是把用户锁死。
            if (!ensureRestoreObserverInstalled()) {
                userShowClock = false
                lyricView.userHideLyric = false
                applyVisibilityRulesNow()
                YLog.warning(TAG, "Hide lyric aborted: no restore channel available")
                return
            }
        } else {
            uninstallRestoreObserver()
            // 恢复歌词时重刷翻译显示配置，避免副行残留为原文/空行
            LyricViewController.refreshTranslationDisplay()
        }
        YLog.info(TAG, "Gesture clock toggle: showClock=$show")
    }

    // --- 隐藏期间的恢复通道 ---
    //
    // 歌词被临时隐藏后视图为 GONE，挂在它上面的手势监听收不到任何事件，
    // 因此必须在状态栏本身上观察触摸。接入方式是包装 touchView 上已有的
    // OnTouchListener 并原样转发：部分 ROM（HyperOS 等）的状态栏下拉手势依赖
    // 挂在该视图上的监听，直接 setOnTouchListener 会把它顶掉导致状态栏拉不下来。
    // 观察者自身从不消费事件，且只在隐藏期间安装。

    private fun ensureRestoreObserverInstalled(): Boolean {
        val current = readStatusBarTouchListener().getOrElse {
            YLog.error(TAG, "Cannot inspect status bar touch listener, lyric restore unavailable", it)
            return false
        }
        val observer = restoreTouchObserver ?: createRestoreObserver().also {
            restoreTouchObserver = it
        }
        if (current === observer) return true

        wrappedOriginalTouchListener = current
        touchView.setOnTouchListener(observer)
        YLog.info(TAG, "Lyric restore observer installed, wrapped=${current?.javaClass?.name}")
        return true
    }

    private fun uninstallRestoreObserver() {
        val observer = restoreTouchObserver ?: return
        val current = readStatusBarTouchListener().getOrNull()
        if (current === observer) {
            touchView.setOnTouchListener(wrappedOriginalTouchListener)
            // 仅在确认还原后清空：观察者若仍在链上，被包装的原监听不能丢
            wrappedOriginalTouchListener = null
            YLog.info(TAG, "Lyric restore observer uninstalled")
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

    private fun createRestoreObserver(): View.OnTouchListener {
        val mainHandler = Handler(context.mainLooper)
        restoreGestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    restoreLyric("tap")
                    return false
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    restoreLyric("double-tap")
                    return false
                }

                override fun onLongPress(e: MotionEvent) {
                    restoreLyric("long-press")
                }
            },
            mainHandler
        )
        return View.OnTouchListener { view, event ->
            // 先取本次转发目标：双击/长按会在本次派发内同步恢复歌词并卸载观察者，
            // 那时 wrappedOriginalTouchListener 已被清空，系统监听会漏掉这个事件
            val wrapped = wrappedOriginalTouchListener
            if (userShowClock && isTouchInRestoreArea(event)) {
                restoreGestureDetector?.onTouchEvent(event)
            }
            // 永不消费：交还给被包装的系统监听，没有则返回 false，
            // 让状态栏自身的 onTouchEvent（下拉手势）照常执行
            wrapped?.onTouch(view, event) ?: false
        }
    }

    private fun restoreLyric(source: String) {
        if (!userShowClock) return
        YLog.info(TAG, "Restore lyric by $source on clock area")
        setUserShowClock(false)
    }

    /**
     * 恢复手势的有效区域：优先取时钟视图（隐藏歌词后它占据原歌词位置）。
     * 时钟不可用时退回整条状态栏，保证任何情况下都有恢复出口。
     */
    private fun isTouchInRestoreArea(event: MotionEvent): Boolean {
        val clock = getClockView()
        if (clock != null && clock.isShown && clock.width > 0) {
            return isTouchInside(clock, event)
        }
        return true
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
