/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.model

import android.graphics.Paint
import android.graphics.Rect
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricMetadata
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator

data class LyricModel(
    val begin: Long = 0,
    val end: Long = 0,
    val duration: Long = 0,
    val text: String,
    val words: List<WordModel>,
    val isAlignedRight: Boolean = false,
    var metadata: LyricMetadata? = null,
) {
    var width: Float = 0f
        private set

    /**
     * 文本左侧 bearing（px），负值表示字形向左延伸超出 x=0。
     * 用于 clipRect 左边界的补偿，避免首字符左侧被裁剪。
     */
    var textLeftBearing: Float = 0f
        private set

    /**
     * 左侧 bearing 补偿（px），始终 >= 0。
     * 当 textLeftBearing < 0 时，此值 = abs(textLeftBearing)，
     * 用于将整体绘制向右偏移，避免字形超出 View 左边界被裁剪。
     */
    var textLeftBearingPadding: Float = 0f
        private set

    val wordText: String by lazy { words.toText() }
    val wordTimingNavigator: TimingNavigator<WordModel> by lazy { TimingNavigator(words.toTypedArray()) }
    val isPlainText: Boolean = words.isEmpty()

    fun updateSizes(paint: Paint) {
        textLeftBearing = computeLeftBearing(paint, text)
        textLeftBearingPadding = maxOf(0f, -textLeftBearing)
        width = getTextFullWidth(paint, text) + textLeftBearingPadding
        var previous: WordModel? = null
        words.forEach { word ->
            word.updateSizes(previous, paint)
            previous = word
        }
    }

    /**
     * 获取文字绘制所需的实际宽度
     * 额外添加一个像素的右侧 padding，防止最后一个字符的
     * 字形（如 italic 斜体或带重音符号的字符）被裁剪
     */
    private fun getTextFullWidth(paint: Paint, text: String): Float {
        val measureWidth = paint.measureText(text)
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        // 取 measureWidth 和 bounds.right 的较大值，再加 1px 安全边距
        val rawWidth = if (bounds.right > measureWidth) {
            bounds.right.toFloat()
        } else {
            measureWidth
        }
        return rawWidth + 1f
    }

    /**
     * 计算文本的最大负 left bearing。
     * 某些字体（如 italic、带重音符号的字形）的 bounds.left < 0，
     * 表示字形向左延伸超出 drawText 的 x 坐标。
     * 返回负值（或 0），用于 clipRect 左边界补偿。
     */
    private fun computeLeftBearing(paint: Paint, text: String): Float {
        val bounds = Rect()
        var minLeft = 0f
        for (i in text.indices) {
            paint.getTextBounds(text, i, i + 1, bounds)
            if (bounds.left < minLeft) {
                minLeft = bounds.left.toFloat()
            }
        }
        return minLeft
    }
}

internal fun emptyLyricModel(): LyricModel = LyricModel(
    words = emptyList(),
    text = ""
)

/**
 * 将 LyricLine 转换为 LyricModel
 */
internal fun LyricLine.createModel(): LyricModel = LyricModel(
    begin = begin,
    end = end,
    duration = duration,
    text = text.orEmpty(),
    words = words?.toWordModels() ?: emptyList(),
    isAlignedRight = isAlignedRight,
    metadata = metadata
)

/**
 * 将 LyricWord 列表转换为 WordModel 列表，并建立前后引用关系
 */
private fun List<LyricWord>.toWordModels(): List<WordModel> {
    val models = mutableListOf<WordModel>()
    var previousModel: WordModel? = null

    forEach { word ->
        val model = WordModel(
            begin = word.begin,
            end = word.end,
            duration = word.duration,
            text = word.text.orEmpty(),
            metadata = word.metadata
        )

        model.previous = previousModel
        previousModel?.next = model

        models.add(model)
        previousModel = model
    }
    return models
}