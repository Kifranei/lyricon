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
import android.view.Gravity
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
    var currentLyricStyle: LyricStyle
) : ScreenStateMonitor.ScreenStateListener {
    companion object {
        const val TAG = "StatusBarViewController"
        private const val MAX_CLIP_RELAX_DEPTH = 12
    }

    val context: Context = statusBarView.context.applicationContext
    val visibilityController: ViewVisibilityController = ViewVisibilityController(statusBarView)
    val lyricView: StatusBarLyric by lazy { createLyricView(currentLyricStyle) }
    private var hdrSurfaceProbeView: HdrSurfaceProbeView? = null
    private var hdrOverlayProbeController: HdrOverlayProbeController? = null

    private val clockId: Int by lazy { ResourceMapper.getIdByName(context, "clock") }
    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null
    private var colorMonitorView: View? = null
    private var coverColorPaletteResult: ColorPaletteResult? = null
    private var systemStatusBarColor: SystemStatusBarColor? = null
    private var lastStatusColorLogFingerprint: String? = null

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        applyVisibilityRulesNow()
    }

    // --- 生命周期与初始化 ---
    fun onCreate() {
        statusBarView.addOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.addOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.addListener(this)
        lyricView.onPlayingChanged = { _ -> }

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
        updateHdrProbeState(
            hdrEnabled = false,
            ratio = 1.0f,
            localProbeEnabled = false,
            surfaceProbeEnabled = false,
            overlayProbeEnabled = false
        )
        removeSurfaceProbeView()
        removeOverlayProbeView()
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

    fun updateHdrProbeState(
        hdrEnabled: Boolean,
        ratio: Float,
        localProbeEnabled: Boolean,
        surfaceProbeEnabled: Boolean,
        overlayProbeEnabled: Boolean
    ) {
        lyricView.setHdrLocalProbe(
            enabled = hdrEnabled && localProbeEnabled,
            ratio = ratio
        )

        val shouldShowSurfaceProbe = hdrEnabled && surfaceProbeEnabled
        if (shouldShowSurfaceProbe) {
            val probeView = ensureSurfaceProbeLocation()
            probeView?.setProbeEnabled(true, ratio)
            YLog.info(
                TAG,
                "HDR probe state: hdr=$hdrEnabled ratio=$ratio local=$localProbeEnabled " +
                        "surface=$surfaceProbeEnabled overlay=$overlayProbeEnabled " +
                        "lyricAttached=${lyricView.isAttachedToWindow} " +
                        "probeAttached=${probeView?.isAttachedToWindow}"
            )
        } else {
            hdrSurfaceProbeView?.setProbeEnabled(false, ratio)
            removeSurfaceProbeView()
        }

        val shouldShowOverlayProbe = hdrEnabled && overlayProbeEnabled
        if (shouldShowOverlayProbe) {
            ensureOverlayProbeLocation(ratio)
        } else {
            removeOverlayProbeView()
        }

        YLog.info(
            TAG,
            "HDR overlay probe state: hdr=$hdrEnabled ratio=$ratio overlay=$overlayProbeEnabled " +
                    "lyricAttached=${lyricView.isAttachedToWindow} " +
                    "controller=${hdrOverlayProbeController != null}"
        )
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
        ensureSurfaceProbeLocationIfVisible()
        ensureOverlayProbeLocationIfVisible()

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

    private fun ensureSurfaceProbeLocationIfVisible() {
        if (hdrSurfaceProbeView?.isVisible == true) {
            ensureSurfaceProbeLocation()
        }
    }

    private fun ensureSurfaceProbeLocation(): HdrSurfaceProbeView? {
        if (!lyricView.isAttachedToWindow) {
            YLog.warning(TAG, "HDR Surface probe skipped: lyric view is not attached")
            return null
        }

        val probeView = hdrSurfaceProbeView ?: HdrSurfaceProbeView(statusBarView.context).apply {
            visibility = View.GONE
            hdrSurfaceProbeView = this
        }

        val currentParent = probeView.parent as? ViewGroup
        if (currentParent !== lyricView) {
            currentParent?.removeView(probeView)
            lyricView.addView(
                probeView,
                createSurfaceProbeLayoutParams()
            )
            YLog.info(TAG, "HDR Surface probe injected inside lyric view")
        } else {
            probeView.layoutParams = createSurfaceProbeLayoutParams()
        }
        return probeView
    }

    private fun removeSurfaceProbeView() {
        val probeView = hdrSurfaceProbeView ?: return
        (probeView.parent as? ViewGroup)?.removeView(probeView)
    }

    private fun ensureOverlayProbeLocationIfVisible() {
        if (hdrOverlayProbeController != null) {
            hdrOverlayProbeController?.updateLocation(lyricView)
        }
    }

    private fun ensureOverlayProbeLocation(ratio: Float) {
        val controller = hdrOverlayProbeController ?: HdrOverlayProbeController(statusBarView.context).also {
            hdrOverlayProbeController = it
        }
        controller.show(lyricView, ratio)
    }

    private fun removeOverlayProbeView() {
        hdrOverlayProbeController?.hide()
        hdrOverlayProbeController = null
    }

    private fun createSurfaceProbeLayoutParams(): ViewGroup.LayoutParams {
        val width = 36.dp
        val height = 14.dp
        return LinearLayout.LayoutParams(width, height).apply {
            gravity = Gravity.CENTER_VERTICAL
            leftMargin = 4.dp
        }
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
