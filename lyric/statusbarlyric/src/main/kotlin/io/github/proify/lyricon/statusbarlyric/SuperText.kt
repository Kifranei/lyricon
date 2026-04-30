/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.sp
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.view.AnimParams
import io.github.proify.lyricon.lyric.view.Highlight
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.lyric.view.LyricViewStyle
import io.github.proify.lyricon.lyric.view.Marquee
import io.github.proify.lyricon.lyric.view.RichLyricLineView
import io.github.proify.lyricon.lyric.view.TextLook
import io.github.proify.lyricon.lyric.view.TitleSlot
import io.github.proify.lyricon.lyric.view.WordMotion
import java.io.File
import kotlin.math.min

class SuperText(context: Context) : LyricPlayerView(context) {

    @Suppress("unused")
    companion object {
        const val VIEW_TAG: String = "lyricon:text_view"
        private const val TAG = "SuperText"
        private const val DEBUG = false
        private const val MAX_FONT_WEIGHT: Int = 1000
    }

    var linkedTextView: TextView? = null

    var eventListener: EventListener? = null

    private var currentStatusColor = StatusColor()
    private var currentLyricStyle: LyricStyle? = null

    interface EventListener {
        fun enteringInterludeMode(duration: Long)
        fun exitInterludeMode()
    }

    init {
        tag = VIEW_TAG
    }

    override fun enteringInterludeMode(duration: Long) {
        super.enteringInterludeMode(duration)
        eventListener?.enteringInterludeMode(duration)
    }

    override fun exitInterludeMode() {
        super.exitInterludeMode()
        eventListener?.exitInterludeMode()
    }

    fun applyStyle(style: LyricStyle) {
        this.currentLyricStyle = style
        val textStyle = style.packageStyle.text

        setTransitionConfig(textStyle.transitionConfig)
        updateContainerLayout(textStyle)

        val resolvedTypeface = resolveTypeface(textStyle)
        val fontSize = if (textStyle.textSize > 0) {
            textStyle.textSize.sp
        } else {
            linkedTextView?.textSize ?: 14f.sp
        }

        setStyle(
            LyricViewStyle(
                primary = TextLook(
                    color = resolvePrimaryColor(textStyle),
                    size = fontSize,
                    typeface = resolvedTypeface,
                    relativeProgress = textStyle.relativeProgress,
                    relativeHighlight = textStyle.relativeProgressHighlight,
                ),
                secondary = TextLook(
                    color = resolvePrimaryColor(textStyle),
                    size = fontSize * 0.76f,
                    typeface = resolvedTypeface,
                ),
                highlight = Highlight(
                    background = resolveBgColor(textStyle),
                    foreground = resolveHighlightColor(textStyle),
                ),
                marquee = buildMarquee(textStyle),
                wordMotion = WordMotion(
                    enabled = textStyle.wordMotionEnabled,
                    cjkLiftFactor = textStyle.wordMotionCjkLiftFactor,
                    cjkWaveFactor = textStyle.wordMotionCjkWaveFactor,
                    latinLiftFactor = textStyle.wordMotionLatinLiftFactor,
                    latinWaveFactor = textStyle.wordMotionLatinWaveFactor,
                ),
                gradient = textStyle.gradientProgressStyle,
                fadingEdge = textStyle.fadingEdgeLength.coerceAtLeast(0).dp,
                scaleMultiLine = textStyle.scaleInMultiLine,
                animation = AnimParams(
                    enabled = style.packageStyle.anim.enable,
                    presetId = style.packageStyle.anim.id,
                ),
                placeholder = when (textStyle.placeholderFormat
                    ?: TextStyle.Defaults.PLACEHOLDER_FORMAT) {
                    TextStyle.PlaceholderFormat.NONE -> TitleSlot.NONE
                    TextStyle.PlaceholderFormat.NAME -> TitleSlot.NAME
                    TextStyle.PlaceholderFormat.NAME_ARTIST -> TitleSlot.NAME_ARTIST
                    else -> TitleSlot.NAME_ARTIST
                }
            )
        )
    }

    fun setStatusBarColor(color: StatusColor) {
        this.currentStatusColor = color
        refreshVisualColors()
    }

    private fun refreshVisualColors() {
        val textStyle = currentLyricStyle?.packageStyle?.text ?: return
        updateColor(
            primary = resolvePrimaryColor(textStyle),
            background = resolveBgColor(textStyle),
            highlight = resolveHighlightColor(textStyle)
        )
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

    private fun buildMarquee(textStyle: TextStyle) = Marquee(
        speed = textStyle.marqueeSpeed,
        spacing = textStyle.marqueeGhostSpacing,
        initialDelay = textStyle.marqueeInitialDelay,
        loopDelay = textStyle.marqueeLoopDelay,
        repeatCount = if (textStyle.marqueeRepeatUnlimited) -1 else textStyle.marqueeRepeatCount,
        stopAtEnd = textStyle.marqueeStopAtEnd,
    )

    private fun resolvePrimaryColor(textStyle: TextStyle): IntArray {
        val customColor = textStyle.color(currentStatusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.normal?.isNotEmpty() == true) {
            customColor.normal
        } else {
            currentStatusColor.color
        }
    }

    private fun resolveBgColor(textStyle: TextStyle): IntArray {
        val customColor = textStyle.color(currentStatusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.background?.isNotEmpty() == true) {
            customColor.background
        } else {
            currentStatusColor.translucentColor
        }
    }

    private fun resolveHighlightColor(textStyle: TextStyle): IntArray {
        val customColor = textStyle.color(currentStatusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.highlight?.isNotEmpty() == true) {
            customColor.highlight
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

    fun shouldShow(): Boolean {
        if (isEmpty()) return false
        var visibleCount = 0
        forEach {
            if (it.isVisible) {
                if (it is RichLyricLineView) {
                    if (it.main.isVisible || it.secondary.isVisible) visibleCount++
                } else {
                    visibleCount++
                }
            }
        }
        return visibleCount > 0
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
