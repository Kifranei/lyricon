/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.content.Context
import android.graphics.Color
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
        private val RAINBOW_COLORS = intArrayOf(
            Color.parseColor("#FF4D4F"),
            Color.parseColor("#FF9F43"),
            Color.parseColor("#FFD93D"),
            Color.parseColor("#2ED573"),
            Color.parseColor("#1E90FF"),
            Color.parseColor("#5352ED"),
            Color.parseColor("#A55EEA"),
        )
        private val LIGHT_MODE_RAINBOW_COLORS = intArrayOf(
            Color.parseColor("#C53A3D"),
            Color.parseColor("#C06A00"),
            Color.parseColor("#B28800"),
            Color.parseColor("#1E8E3E"),
            Color.parseColor("#1565C0"),
            Color.parseColor("#3949AB"),
            Color.parseColor("#7B1FA2"),
        )
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
                sustainGlow = textStyle.sustainGlowEnabled,
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
        if (textStyle.enableRainbowTextColor) {
            return resolveRainbowColors(textStyle).normal
        }
        val customColor = textStyle.color(currentStatusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.normal?.isNotEmpty() == true) {
            customColor.normal.ensureStatusContrast()
        } else {
            currentStatusColor.color
        }
    }

    private fun resolveBgColor(textStyle: TextStyle): IntArray {
        if (textStyle.enableRainbowTextColor) {
            return resolveRainbowColors(textStyle).background
        }
        val customColor = textStyle.color(currentStatusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.background?.isNotEmpty() == true) {
            customColor.background.ensureStatusContrast(alpha = 0.5f)
        } else {
            currentStatusColor.translucentColor
        }
    }

    private fun resolveHighlightColor(textStyle: TextStyle): IntArray {
        if (textStyle.enableRainbowTextColor) {
            return resolveRainbowColors(textStyle).highlight
        }
        val customColor = textStyle.color(currentStatusColor.isLightMode)
        return if (textStyle.enableCustomTextColor && customColor?.highlight?.isNotEmpty() == true) {
            customColor.highlight.ensureStatusContrast()
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

    private fun isColorModeEnabled(textStyle: TextStyle): Boolean =
        textStyle.enableCustomTextColor ||
                textStyle.enableExtractCoverTextColor ||
                textStyle.enableExtractCoverTextGradient ||
                textStyle.enableRainbowTextColor

    private fun Int.withAlpha(ratio: Float): Int {
        val alpha = (ratio.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }

    private fun Int.withStatusContrast(alpha: Float = 1f): Int {
        val target = if (currentStatusColor.isLightMode) {
            if (luminance() > 0.42f) darken(0.58f) else this
        } else {
            if (luminance() < 0.58f) lighten(0.62f) else this
        }
        return target.withAlpha(alpha)
    }

    private fun IntArray.ensureStatusContrast(alpha: Float = 1f): IntArray =
        map { it.withStatusContrast(alpha) }.toIntArray()

    private fun Int.luminance(): Float {
        fun channel(value: Int): Float {
            val srgb = value / 255f
            return if (srgb <= 0.03928f) srgb / 12.92f
            else Math.pow(((srgb + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * channel(Color.red(this)) +
                0.7152f * channel(Color.green(this)) +
                0.0722f * channel(Color.blue(this))
    }

    private fun Int.darken(ratio: Float): Int {
        val keep = (1f - ratio).coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(this) * keep).toInt().coerceIn(0, 255),
            (Color.green(this) * keep).toInt().coerceIn(0, 255),
            (Color.blue(this) * keep).toInt().coerceIn(0, 255),
        )
    }

    private fun Int.lighten(ratio: Float): Int {
        val amount = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(this) + (255 - Color.red(this)) * amount).toInt().coerceIn(0, 255),
            (Color.green(this) + (255 - Color.green(this)) * amount).toInt().coerceIn(0, 255),
            (Color.blue(this) + (255 - Color.blue(this)) * amount).toInt().coerceIn(0, 255),
        )
    }

    private fun resolveRainbowColors(
        textStyle: TextStyle
    ): io.github.proify.lyricon.lyric.style.RainbowTextColor {
        val lightMode = currentStatusColor.isLightMode
        val custom = textStyle.color(lightMode)
        val fallback = defaultRainbowTextColor(lightMode)
        return io.github.proify.lyricon.lyric.style.RainbowTextColor(
            normal = custom?.normal?.takeIf { it.isNotEmpty() } ?: fallback.normal,
            background = custom?.background?.takeIf { it.isNotEmpty() } ?: fallback.background,
            highlight = custom?.highlight?.takeIf { it.isNotEmpty() } ?: fallback.highlight,
        )
    }

    private fun defaultRainbowTextColor(
        lightMode: Boolean
    ): io.github.proify.lyricon.lyric.style.RainbowTextColor {
        val normal = if (lightMode) LIGHT_MODE_RAINBOW_COLORS else RAINBOW_COLORS
        val bgAlpha = if (lightMode) 0.44f else 0.45f
        return io.github.proify.lyricon.lyric.style.RainbowTextColor(
            normal = normal,
            background = normal.map { it.withAlpha(bgAlpha) }.toIntArray(),
            highlight = normal,
        )
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
