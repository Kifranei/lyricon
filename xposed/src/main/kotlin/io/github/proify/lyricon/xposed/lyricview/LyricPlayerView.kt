/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.lyricview

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.setColorAlpha
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.DefaultMarqueeConfig
import io.github.proify.lyricon.lyric.view.DefaultSyllableConfig
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.xposed.util.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.util.StatusColor
import java.io.File
import kotlin.math.min

class LyricPlayerView(context: Context) : LyricPlayerView(context),
    StatusBarColorMonitor.OnColorChangeListener {

    companion object {
        const val VIEW_TAG: String = "lyricon:text_view"
        private const val FONT_WEIGHT_MAX: Int = 1000
    }

    private var currentStatusColor = StatusColor(Color.BLACK, false)
    private var currentLyricStyle: LyricStyle? = null

    var linkedTextView: TextView? = null

    init {
        tag = VIEW_TAG
    }

    var eventListener: EventListener? = null

    interface EventListener {
        fun enteringInterludeMode(duration: Long)
        fun exitInterludeMode()
    }

    override fun enteringInterludeMode(duration: Long) {
        eventListener?.enteringInterludeMode(duration)
    }

    override fun exitInterludeMode() {
        eventListener?.exitInterludeMode()
    }

    fun applyStyle(style: LyricStyle) {
        this.currentLyricStyle = style
        val textStyle = style.packageStyle.text

        updateViewLayout(textStyle)

        val config = getStyle().apply {
            val typeface = resolveTypeface(textStyle)
            val fontSize =
                if (textStyle.textSize > 0) textStyle.textSize.sp else (linkedTextView?.textSize
                    ?: 14f.sp)

            primary.apply {
                this.textColor = resolvePrimaryColor(textStyle)
                this.textSize = fontSize
                this.typeface = typeface
                enableRelativeProgress = textStyle.relativeProgress
                enableRelativeProgressHighlight = textStyle.relativeProgressHighlight
            }

            secondary.apply {
                this.textColor = primary.textColor
                this.textSize = fontSize * 0.8f
                this.typeface = typeface
            }

            this.marquee = buildMarqueeConfig(textStyle)
            this.syllable = buildSyllableConfig(textStyle)

            this.gradientProgressStyle = textStyle.gradientProgressStyle
            textSizeRatioInMultiLineMode = textStyle.textSizeRatioInMultiLineMode
        }

        setStyle(config)
    }

    override fun onColorChange(color: StatusColor) {
        this.currentStatusColor = color
        refreshColors()
    }

    private fun refreshColors() {
        val textStyle = currentLyricStyle?.packageStyle?.text ?: return

        val primaryColor = resolvePrimaryColor(textStyle)
        val syllableConfig = buildSyllableConfig(textStyle)

        updateColor(
            primaryColor,
            syllableConfig.backgroundColor,
            syllableConfig.highlightColor
        )
    }

    private fun updateViewLayout(textStyle: TextStyle) {
        val margins = textStyle.margins
        val paddings = textStyle.paddings

        val params = (layoutParams as? MarginLayoutParams)
            ?: MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )

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

    private fun buildSyllableConfig(textStyle: TextStyle) = DefaultSyllableConfig().apply {
        val customColor = textStyle.color(currentStatusColor.lightMode)
        val isCustomEnabled = textStyle.enableCustomTextColor && customColor != null

        backgroundColor = when {
            isCustomEnabled && customColor.background != 0 -> customColor.background
            else -> currentStatusColor.color.setColorAlpha(0.5f)
        }

        highlightColor = when {
            isCustomEnabled && customColor.highlight != 0 -> customColor.highlight
            else -> currentStatusColor.color
        }

    }

    private fun resolvePrimaryColor(textStyle: TextStyle): Int {
        val customColor = textStyle.color(currentStatusColor.lightMode)
        return if (textStyle.enableCustomTextColor
            && customColor != null
            && customColor.normal != 0
        ) {
            customColor.normal
        } else {
            currentStatusColor.color
        }
    }

    private fun resolveTypeface(textStyle: TextStyle): Typeface {
        val baseTypeface = textStyle.typeFace?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists()) {
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } else null
        } ?: linkedTextView?.typeface ?: Typeface.DEFAULT

        return if (textStyle.fontWeight > 0) {
            Typeface.create(
                baseTypeface,
                min(FONT_WEIGHT_MAX, textStyle.fontWeight),
                textStyle.typeFaceItalic
            )
        } else {
            val style = when {
                textStyle.typeFaceBold && textStyle.typeFaceItalic -> Typeface.BOLD_ITALIC
                textStyle.typeFaceBold -> Typeface.BOLD
                textStyle.typeFaceItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(baseTypeface, style)
        }
    }
}