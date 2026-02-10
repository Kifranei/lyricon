/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.widget.TextView
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.DefaultMarqueeConfig
import io.github.proify.lyricon.lyric.view.DefaultSyllableConfig
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import java.io.File
import kotlin.math.min

/**
 * 高级歌词文本渲染控件，支持逐字染色、跑马灯及间奏监听。
 *
 */
class SuperText(context: Context) : LyricPlayerView(context) {

    companion object {
        const val VIEW_TAG: String = "lyricon:text_view"
        private const val TAG = "SuperText"
        private const val DEBUG = false
        private const val MAX_FONT_WEIGHT: Int = 1000
    }

    /**
     * 关联的系统 TextView，用于同步默认的字体、字号等样式。
     */
    var linkedTextView: TextView? = null

    /**
     * 歌词事件监听器
     */
    var eventListener: EventListener? = null

    private var currentStatusColor = StatusColor()
    private var currentLyricStyle: LyricStyle? = null
    private val syllableConfigCache = DefaultSyllableConfig()

    /**
     * 事件监听接口
     */
    interface EventListener {
        /** 进入间奏模式 */
        fun enteringInterludeMode(duration: Long)

        /** 退出间奏模式 */
        fun exitInterludeMode()
    }

    init {
        tag = VIEW_TAG
    }

    // --- 生命周期/重写方法 ---

    override fun enteringInterludeMode(duration: Long) {
        super.enteringInterludeMode(duration)
        eventListener?.enteringInterludeMode(duration)
    }

    override fun exitInterludeMode() {
        super.exitInterludeMode()
        eventListener?.exitInterludeMode()
    }

    // --- 公开 API ---

    /**
     * 应用全量歌词样式。
     *
     * @param style 歌词样式配置
     */
    fun applyStyle(style: LyricStyle) {
        this.currentLyricStyle = style
        val textStyle = style.packageStyle.text

        // 应用过渡配置
        setTransitionConfig(textStyle.transitionConfig)

        // 更新容器布局（Margin/Padding）
        updateContainerLayout(textStyle)

        // 配置核心样式
        val config = getStyle().apply {
            val resolvedTypeface = resolveTypeface(textStyle)
            val fontSize = if (textStyle.textSize > 0) {
                textStyle.textSize.sp
            } else {
                linkedTextView?.textSize ?: 14f.sp
            }

            primary.apply {
                this.textColor = resolvePrimaryColor(textStyle)
                this.textSize = fontSize
                this.typeface = resolvedTypeface
                enableRelativeProgress = textStyle.relativeProgress
                enableRelativeProgressHighlight = textStyle.relativeProgressHighlight
            }

            secondary.apply {
                this.textColor = primary.textColor
                this.textSize = fontSize * 0.76f
                this.typeface = resolvedTypeface
            }

            this.marquee = buildMarqueeConfig(textStyle)
            this.syllable = buildSyllableConfig(textStyle)

            this.gradientProgressStyle = textStyle.gradientProgressStyle
            scaleInMultiLine = textStyle.scaleInMultiLine
            fadingEdgeLength = textStyle.fadingEdgeLength.coerceAtLeast(0).dp
            placeholderFormat = textStyle.placeholderFormat ?: TextStyle.Defaults.PLACEHOLDER_FORMAT
        }

        setStyle(config)
    }

    /**
     * 更新状态栏颜色环境，同步刷新文本及染色颜色。
     */
    fun setStatusBarColor(color: StatusColor) {
        this.currentStatusColor = color
        refreshVisualColors()
    }

    // --- 内部逻辑私有方法 ---

    /**
     * 仅刷新颜色相关的配置，不触发布局变更。
     */
    private fun refreshVisualColors() {
        val textStyle = currentLyricStyle?.packageStyle?.text ?: return

        val primaryColor = resolvePrimaryColor(textStyle)
        val syllableConfig = buildSyllableConfig(textStyle)

        updateColor(
            primaryColor,
            syllableConfig.backgroundColor,
            syllableConfig.highlightColor
        )

        if (DEBUG) {
            Log.d(TAG, "Color refreshed: Primary=$primaryColor")
        }
    }

    private fun updateContainerLayout(textStyle: TextStyle) {
        val margins = textStyle.margins
        val paddings = textStyle.paddings

        val params = (layoutParams as? MarginLayoutParams)
            ?: MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        params.setMargins(
            margins.left.dp,
            margins.top.dp,
            margins.right.dp,
            margins.bottom.dp
        )
        layoutParams = params

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    private fun buildMarqueeConfig(textStyle: TextStyle) = DefaultMarqueeConfig().apply {
        scrollSpeed = textStyle.marqueeSpeed
        ghostSpacing = textStyle.marqueeGhostSpacing
        initialDelay = textStyle.marqueeInitialDelay
        loopDelay = textStyle.marqueeLoopDelay
        repeatCount = if (textStyle.marqueeRepeatUnlimited) -1 else textStyle.marqueeRepeatCount
        stopAtEnd = textStyle.marqueeStopAtEnd
    }

    private fun buildSyllableConfig(textStyle: TextStyle) =
        syllableConfigCache.apply {
            val customColor = textStyle.color(currentStatusColor.lightMode)
            val isCustomEnabled = textStyle.enableCustomTextColor && customColor != null

            backgroundColor = when {
                isCustomEnabled && customColor.background != 0 -> customColor.background
                else -> currentStatusColor.translucentColor
            }

            highlightColor = when {
                isCustomEnabled && customColor.highlight != 0 -> customColor.highlight
                else -> currentStatusColor.color
            }
        }

    private fun resolvePrimaryColor(textStyle: TextStyle): Int {
        val customColor = textStyle.color(currentStatusColor.lightMode)
        return if (textStyle.enableCustomTextColor && customColor?.normal != 0) {
            customColor?.normal ?: currentStatusColor.color
        } else {
            currentStatusColor.color
        }
    }

    private fun resolveTypeface(textStyle: TextStyle): Typeface {
        // 1. 获取基础字体
        val baseTypeface = textStyle.typeFace?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists()) {
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } else null
        } ?: linkedTextView?.typeface ?: Typeface.DEFAULT

        // 2. 处理粗细和斜体
        return if (textStyle.fontWeight > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(
                baseTypeface,
                min(MAX_FONT_WEIGHT, textStyle.fontWeight),
                textStyle.typeFaceItalic
            )
        } else {
            val styleFlag = when {
                textStyle.typeFaceBold && textStyle.typeFaceItalic -> Typeface.BOLD_ITALIC
                textStyle.typeFaceBold -> Typeface.BOLD
                textStyle.typeFaceItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(baseTypeface, styleFlag)
        }
    }
}