/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 强调辉光组 —— Apple Music 式 emphasize 特效的预计算参数。
 *
 * 移植自 applemusic-like-lyrics 的逐字强调动画参数体系：
 *
 * 1. 一行歌词按「连续的非空白、非 CJK 词合并，其余单词独组」切分为若干候选组；
 * 2. 组时长满足强调条件（>= 1s；CJK 不限字符数，非 CJK 还需 2..7 个字符）时生成该组参数；
 * 3. 渲染层按字符错峰播放「缩放 + 左右展开 + 上浮 + 白色辉光」动画，
 *    组内每个字符的动画开始时间由 [charStartMs] 给出。
 *
 * @property lineBegin 行开始时间（ms），用于把组内错峰换算为绝对时间
 * @property startMs 组开始时间（merged begin，ms）
 * @property durationMs 组动画时长（>= 1000ms；行末组会再乘 1.2）
 * @property amount 缩放幅度系数（源项目 amount，上限 1.2）
 * @property blur 辉光强度系数（源项目 blur，上限 0.8）
 * @property charCount 组内参与动画的字符总数（锚定错峰跨度）
 */
internal class EmphasisGroup(
    private val lineBegin: Long,
    val startMs: Long,
    val durationMs: Long,
    val amount: Float,
    val blur: Float,
    val charCount: Int,
) {

    /** 组内第 [charIndex] 个字符的动画开始绝对时间（ms），含逐字错峰（跨度 du / 2.5）。 */
    fun charStartMs(charIndex: Int): Long {
        val de = max(0L, startMs - lineBegin)
        val stagger = durationMs / 2.5f * charIndex / charCount
        return lineBegin + de + stagger.toLong()
    }
}

/**
 * 源项目 `LyricLineBase.shouldEmphasize` 规则：
 * - CJK 词：时长 >= 1000ms；
 * - 非 CJK 词：时长 >= 1000ms 且去除空白后长度为 2..7。
 */
internal fun shouldEmphasize(isCjk: Boolean, trimmedText: String, durationMs: Long): Boolean {
    if (isCjk) return durationMs >= 1000L
    val length = trimmedText.length
    return durationMs >= 1000L && length > 1 && length <= 7
}

/**
 * 为一行歌词计算强调组，并把结果写入各词的 [WordModel.emphasisGroup] /
 * [WordModel.emphasisCharOffset]。
 *
 * 与源项目 `chunkAndSplitLyricWords` 的合并语义一致：空白词跳过，且空白是组边界——
 * 词文本按空白切分后，仅「前一文本片段不以空白结尾、后一片段不以空白开头」的
 * 相邻非 CJK 词才合并（例如 normalize 把空格词并入前词尾部形成的 "Cards " 与
 * "on " 之间因尾随空白而断开）。合并组按 `merged`（最早开始 / 最晚结束 / 拼接文本）
 * 再作一次强调判定；行末组（覆盖行内最后一个文本词）的 amount / blur / 时长分别乘
 * 1.6 / 1.5 / 1.2。
 */
internal fun List<WordModel>.assignEmphasisGroups(lineBegin: Long) {
    if (isEmpty()) return
    val lastTextWord = lastOrNull { it.text.isNotBlank() }

    var i = 0
    while (i < size) {
        val first = this[i]
        if (first.text.isBlank()) {
            i++
            continue
        }

        // 收集组：连续「非空白 && 非 CJK && 边界无空白粘连」合并；CJK 词独组。
        val group = ArrayList<WordModel>(4)
        group += first
        if (!first.isCjk) {
            var j = i + 1
            while (j < size) {
                val next = this[j]
                if (next.text.isBlank() || next.isCjk) break
                // 空白是组边界：前词尾随空白或后词前导空白时断开（源项目按空白拆分词）。
                if (group[group.size - 1].text.last().isWhitespace() || next.text.first().isWhitespace()) break
                group += next
                j++
            }
        }
        i += group.size

        val groupBegin = group.minOf { it.begin }
        val groupEnd = group.maxOf { it.end }
        val trimmed = group.joinToString("") { it.text }.trim()
        val isCjkGroup = group.size == 1 && group[0].isCjk

        val anyEmphasized = group.any {
            shouldEmphasize(it.isCjk, it.text.trim(), it.end - it.begin)
        }
        val mergedEmphasized = !isCjkGroup && shouldEmphasize(false, trimmed, groupEnd - groupBegin)
        if (!anyEmphasized && !mergedEmphasized) continue

        val isLastWord = group[group.size - 1] === lastTextWord

        // 源项目公式：amount/blur 先按原始 du 计算，行末组再整体放大。
        var du = max(1000L, groupEnd - groupBegin)
        var amount = powerCurve(du / 2000f) * 0.6f
        var blur = powerCurve(du / 3000f) * 0.5f
        if (isLastWord) {
            amount *= 1.6f
            blur *= 1.5f
            du = (du * 1.2f).toLong()
        }
        amount = min(1.2f, amount)
        blur = min(0.8f, blur)

        val charCount = group.sumOf { it.text.trim().length }
        if (charCount <= 0) continue

        val emphasis = EmphasisGroup(lineBegin, groupBegin, du, amount, blur, charCount)
        var offset = 0
        for (word in group) {
            word.emphasisGroup = emphasis
            word.emphasisCharOffset = offset
            offset += word.text.trim().length
        }
    }
}

/** 源项目曲线：v > 1 时开方，v <= 1 时立方。 */
private fun powerCurve(v: Float): Float = if (v > 1f) sqrt(v) else v.pow(3)
