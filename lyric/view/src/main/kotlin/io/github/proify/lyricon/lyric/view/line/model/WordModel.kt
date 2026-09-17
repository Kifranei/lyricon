/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.lyric.view.line.model

import android.graphics.Paint
import android.graphics.Rect
import io.github.proify.lyricon.lyric.model.LyricMetadata
import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming

/**
 * 表示歌词中的单词及其相关位置信息、时间信息和字符偏移。
 *
 * @property begin 单词开始时间，单位毫秒
 * @property end 单词结束时间，单位毫秒
 * @property duration 单词持续时间，单位毫秒
 * @property text 单词文本内容
 * @property previous 前一个单词模型，可为 null
 * @property next 下一个单词模型，可为 null
 * @property textWidth 单词文本的总宽度
 * @property startPosition 单词起始绘制位置
 * @property endPosition 单词结束绘制位置
 * @property chars 单词拆分后的字符数组
 * @property charWidths 各字符宽度数组
 * @property charStartPositions 各字符起始绘制位置数组
 * @property charEndPositions 各字符结束绘制位置数组
 */
data class WordModel(
    override var begin: Long,
    override var end: Long,
    override var duration: Long,
    val text: String,
    val metadata: LyricMetadata? = null,
) : ILyricTiming {

    /** 前一个单词 */
    var previous: WordModel? = null

    /** 下一个单词 */
    var next: WordModel? = null

    /** 该词文本在 [LyricModel.wordText] 中的起始偏移（构建时设置，供批量绘制定位）。 */
    var textOffset: Int = 0

    /** 单词文本总宽度 */
    var textWidth: Float = 0f
        private set

    /** 单词起始绘制位置 */
    var startPosition: Float = 0f
        private set

    /** 单词结束绘制位置 */
    var endPosition: Float = 0f
        private set

    /** 该词是否含 CJK 字符（决定逐字动画策略；仅在构建时计算一次，避免每帧重复判定）。 */
    val isCjk: Boolean = text.any { it.isCjkChar() }

    /**
     * 强调辉光组（Apple Music 式 emphasize 特效预计算参数）。
     * 非空表示本词参与强调动画；组内多个词（合并的非 CJK 词）共享同一实例。
     */
    internal var emphasisGroup: EmphasisGroup? = null

    /** 本词在强调组内的字符起始索引（去除空白字符后计数，跨组合并时连续累加）。 */
    internal var emphasisCharOffset: Int = 0

    /** 拆分后的字符数组 */
    val chars: CharArray = text.toCharArray()

    /** 各字符宽度数组 */
    val charWidths: FloatArray = FloatArray(text.length)

    /** 各字符起始绘制位置数组 */
    val charStartPositions: FloatArray = FloatArray(text.length)

    /** 各字符结束绘制位置数组 */
    val charEndPositions: FloatArray = FloatArray(text.length)

    /**
     * 整词墨迹超出 advance 宽度的右侧溢出量（px，>= 0）。
     * 斜体等字形的墨迹会越过 advance 边界，逐字绘制时需要
     * 把裁剪区向右扩展该值，否则词尾会被裁掉。
     */
    var rightOverhang: Float = 0f
        private set

    /** 各字符墨迹相对自身 advance 的右侧溢出量（px，>= 0） */
    val charRightOverhangs: FloatArray = FloatArray(text.length)

    /**
     * 更新单词及其字符的尺寸和位置信息
     *
     * @param previous 上一个单词模型
     * @param paint 绘制文本的 Paint 对象
     */
    fun updateSizes(previous: WordModel?, paint: Paint) {
        paint.getTextWidths(chars, 0, chars.size, charWidths)
        textWidth = charWidths.sum()
        startPosition = previous?.endPosition ?: 0f
        endPosition = startPosition + textWidth

        var currentPosition = startPosition
        for (i in chars.indices) {
            charStartPositions[i] = currentPosition
            currentPosition += charWidths[i]
            charEndPositions[i] = currentPosition
        }
        updateOverhangs(paint)
    }

    private fun updateOverhangs(paint: Paint) {
        if (text.isEmpty()) {
            rightOverhang = 0f
            return
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        rightOverhang = maxOf(0f, bounds.right - textWidth)
        for (i in chars.indices) {
            paint.getTextBounds(text, i, i + 1, bounds)
            charRightOverhangs[i] = maxOf(0f, bounds.right - charWidths[i])
        }
    }
}

internal fun List<WordModel>.toText(): String = joinToString("") { it.text }

/**
 * 判断字符是否属于需要逐字动画的 CJK/日文/韩文区间。
 *
 * 与绘制无关，仅用于动画策略；集中在此以便一次计算后缓存到 [WordModel.isCjk]。
 */
internal fun Char.isCjkChar(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
}