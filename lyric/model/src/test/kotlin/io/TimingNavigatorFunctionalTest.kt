package io

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 针对 TimingNavigator 的功能边界测试
 */
class TimingNavigatorFunctionalTest {

    @Test
    fun testOverlappingLyrics() {
        // 构造重叠场景：
        // 1. [1000, 5000] "长歌词"
        // 2. [2000, 3000] "短歌词A"
        // 3. [2500, 3500] "短歌词B"
        val source = arrayOf(
            LyricLine(1000, 5000, text = "Long"),
            LyricLine(2000, 3000, text = "Short A"),
            LyricLine(2500, 3500, text = "Short B")
        )
        val navigator = TimingNavigator(source)

        // 测试点：2700ms 应该同时命中上述三条
        val result = mutableListOf<LyricLine>()
        val count = navigator.forEachAt(2700L) { result.add(it) }

        assertEquals("应该匹配到3条重叠歌词", 3, count)
        assertEquals("第一条应该是长歌词", "Long", result[0].text)

        // 测试点：缓存更新后的连续查询
        result.clear()
        navigator.forEachAt(3200L) { result.add(it) }
        // 此时 Short A 已结束，应剩 Long 和 Short B
        assertEquals("3200ms 应该剩下2条", 2, result.size)
    }

    @Test
    fun testBoundaryConditions() {
        val source = arrayOf(
            LyricLine(1000, 2000, text = "Boundary")
        )
        val navigator = TimingNavigator(source)

        // 测试点：精确匹配边界
        assertEquals("起始边界命中", "Boundary", navigator.first(1000)?.text)
        assertEquals("结束边界命中", "Boundary", navigator.first(2000)?.text)

        // 测试点：超出范围
        assertEquals("早于起始点", null, navigator.first(999))
        assertEquals("晚于结束点", null, navigator.first(2001))
    }
}