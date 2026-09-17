/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强调辉光组预计算规则验证（移植自 applemusic-like-lyrics 的 emphasize 参数体系）。
 */
class EmphasisGroupTest {

    private fun word(text: String, begin: Long, end: Long) = WordModel(
        begin = begin,
        end = end,
        duration = end - begin,
        text = text,
    )

    private fun List<WordModel>.compute(lineBegin: Long): List<WordModel> =
        apply { assignEmphasisGroups(lineBegin) }

    @Test
    fun `CJK word lasting at least 1s is emphasized`() {
        val words = listOf(word("我们", 0, 1500)).compute(0)
        val group = words[0].emphasisGroup
        assertNotNull(group)
        assertEquals(2, group!!.charCount)
        assertEquals(0, words[0].emphasisCharOffset)
    }

    @Test
    fun `short CJK word is not emphasized`() {
        val words = listOf(word("你好", 0, 800)).compute(0)
        assertNull(words[0].emphasisGroup)
    }

    @Test
    fun `latin word with 2-7 chars lasting at least 1s is emphasized`() {
        val words = listOf(word("hello", 0, 1200)).compute(0)
        assertNotNull(words[0].emphasisGroup)
    }

    @Test
    fun `single char latin word is not emphasized`() {
        val words = listOf(word("I", 0, 1500)).compute(0)
        assertNull(words[0].emphasisGroup)
    }

    @Test
    fun `adjacent non-CJK words merge into one group sharing char offsets`() {
        val words = listOf(
            word("Life", 0, 600),
            word("is", 600, 1500),
        ).compute(0)

        val group = words[0].emphasisGroup
        assertNotNull(group)
        assertEquals(6, group!!.charCount)
        assertEquals(0, words[0].emphasisCharOffset)
        assertEquals(4, words[1].emphasisCharOffset)
        assertTrue(words[1].emphasisGroup === group)
    }

    @Test
    fun `trailing space words do not merge into one giant group`() {
        // 真实数据流：空格词 begin=-1 被 normalize 并入前词尾部（"Cards " 形态）。
        // 空白是组边界——整行若合并为一个 38 字大组会导致错峰跨数秒、幅度顶格的异常变形。
        val words = listOf(
            word("Cards ", 192704, 193406),
            word("on ", 193406, 193840),
            word("the ", 193840, 194257),
            word("table, ", 194257, 195810),
            word("we're ", 195810, 196297),
            word("both ", 196297, 197130),
            word("showing ", 197130, 198201),
            word("hearts", 198201, 200040),
        ).compute(192704)

        assertNull(words[0].emphasisGroup) // 702ms < 1s
        assertNull(words[1].emphasisGroup)
        assertNull(words[2].emphasisGroup)
        assertNull(words[4].emphasisGroup)
        assertNull(words[5].emphasisGroup)

        // 达标词各自独立成组，错峰与唱词时间对齐
        val table = words[3].emphasisGroup
        assertNotNull(table)
        assertEquals(6, table!!.charCount)
        assertEquals(194257L, table.charStartMs(0))
        assertEquals(1553L, table.durationMs)

        val showing = words[6].emphasisGroup
        assertNotNull(showing)
        assertEquals(7, showing!!.charCount)

        val hearts = words[7].emphasisGroup
        assertNotNull(hearts)
        assertTrue(hearts !== table)
        assertTrue(hearts !== showing)
    }

    @Test
    fun `blank separator words are skipped`() {
        val words = listOf(
            word("我们", 0, 1500),
            word(" ", 1500, 1500),
            word("一起", 1500, 3000),
        ).compute(0)

        assertNotNull(words[0].emphasisGroup)
        assertNull(words[1].emphasisGroup)
        assertNotNull(words[2].emphasisGroup)
        // 空白词不入组：第二组从行内第 0 个文本词的偏移开始重新计数。
        assertEquals(0, words[2].emphasisCharOffset)
    }

    @Test
    fun `last word group gets boosted amount`() {
        val words = listOf(
            word("你好", 0, 1500),
            word("世界", 1500, 3000),
        ).compute(0)

        val first = words[0].emphasisGroup!!
        val last = words[1].emphasisGroup!!
        assertTrue(last.amount > first.amount)
        assertTrue(last.blur > first.blur)
        assertTrue(last.durationMs > first.durationMs)
    }

    @Test
    fun `char stagger matches du divide 2_5 divide charCount`() {
        // 补充空格词与末词，使「你好」非行末组（行末组 du 会乘 1.2）。
        val words = listOf(
            word("你好", 500, 2500),
            word(" ", 2500, 2500),
            word("世界", 2500, 4000),
        ).compute(0)
        val group = words[0].emphasisGroup!!

        val firstStart = group.charStartMs(0)
        val secondStart = group.charStartMs(1)

        // de = max(0, 500 - 0) = 500
        assertEquals(500L, firstStart)
        // stagger = du / 2.5 / charCount;du = max(1000, 2000) = 2000
        assertEquals(500L + 2000L / 2.5f / 2f, secondStart.toFloat(), 0.5f)
    }

    @Test
    fun `duration of exactly 1s keeps du at 1000ms for non-last group`() {
        val words = listOf(
            word("你好", 0, 1000),
            word(" ", 1000, 1000),
            word("世界", 1000, 2000),
        ).compute(0)
        val group = words[0].emphasisGroup!!
        assertEquals(1000L, group.durationMs)
    }
}
