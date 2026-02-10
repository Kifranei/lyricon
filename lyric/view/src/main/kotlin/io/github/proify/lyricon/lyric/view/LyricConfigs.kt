/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.graphics.Color
import android.graphics.Typeface
import io.github.proify.lyricon.lyric.view.util.dp
import io.github.proify.lyricon.lyric.view.util.sp

open class LyricLineConfig(
    var text: TextConfig,
    var marquee: MarqueeConfig,
    var syllable: SyllableConfig,
    var gradientProgressStyle: Boolean,
    var fadingEdgeLength: Int
)

data class RichLyricLineConfig(
    var primary: MainTextConfig = MainTextConfig(),
    var secondary: SecondaryTextConfig = SecondaryTextConfig(),
    var marquee: MarqueeConfig = DefaultMarqueeConfig(),
    var syllable: SyllableConfig = DefaultSyllableConfig(),
    var gradientProgressStyle: Boolean = true,
    var scaleInMultiLine: Float = 1f,
    var fadingEdgeLength: Int = 10,
    var placeholderFormat: String = PlaceholderFormat.NAME_ARTIST
)

object PlaceholderFormat {
    const val NAME: String = "NameOnly"
    const val NAME_ARTIST: String = "NameAndArtist"
    const val NONE: String = "None"
}

interface TextConfig {
    var textColor: Int
    var textSize: Float
    var typeface: Typeface
}

interface MarqueeConfig {
    var ghostSpacing: Float
    var scrollSpeed: Float
    var initialDelay: Int
    var loopDelay: Int
    var repeatCount: Int
    var stopAtEnd: Boolean
}

open class DefaultMarqueeConfig(
    override var scrollSpeed: Float = 40f,
    override var ghostSpacing: Float = 70f.dp,
    override var initialDelay: Int = 300,
    override var loopDelay: Int = 700,
    override var repeatCount: Int = -1,
    override var stopAtEnd: Boolean = false,
) : MarqueeConfig

interface SyllableConfig {
    var backgroundColor: Int
    var highlightColor: Int
    // var disableHighlight: Boolean
}

data class MainTextConfig(
    override var textColor: Int = Color.BLACK,
    override var textSize: Float = 16f.sp,
    override var typeface: Typeface = Typeface.DEFAULT,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
) : TextConfig

class DefaultSyllableConfig(
    override var highlightColor: Int = Color.BLACK,
    override var backgroundColor: Int = Color.GRAY,
    // override var disableHighlight: Boolean = false,
) : SyllableConfig

class SecondaryTextConfig(
    override var textColor: Int = Color.GRAY,
    override var textSize: Float = 14f.sp,
    override var typeface: Typeface = Typeface.DEFAULT
) : TextConfig