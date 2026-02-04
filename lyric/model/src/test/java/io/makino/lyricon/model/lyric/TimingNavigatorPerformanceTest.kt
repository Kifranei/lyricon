/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.model.extensions

import io.github.proify.lyricon.lyric.model.LyricLine
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test

/**
 * TimingNavigator 性能测试
 *
 * 目标
 * - 顺序播放（热路径）
 * - 随机跳转（二分冷路径）
 * - 重叠歌词遍历
 *
 * 说明
 * - 这是一个纯 CPU 基准测试（micro benchmark）
 * - 不做断言，只输出耗时，用于相对对比
 */
class TimingNavigatorPerformanceTest {

    private val lyricCount = 500
    private val durationPerLine = 5_000L

    private fun buildLyrics(): Array<LyricLine> {
        return Array(lyricCount) { i ->
            val start = i * durationPerLine
            LyricLine(
                begin = start,
                end = start + durationPerLine + if (i % 10 == 0) 1_000 else 0 // 制造部分重叠
            )
        }
    }

    @Test
    fun sequentialPlayback_hotPath() {
        val lyrics = buildLyrics()
        val navigator = TimingNavigator(lyrics)

        val step = 100L
        val maxTime = lyrics.last().end

        val cost = measureNanoTime {
            var t = 0L
            while (t <= maxTime) {
                navigator.first(t)
                t += step
            }
        }

        println(
            "顺序播放 Hot Path：" +
                    " calls=${maxTime / step}, " +
                    " time=${cost / 1_000_000} ms"
        )
    }

    @Test
    fun randomSeek_coldPath() {
        val lyrics = buildLyrics()
        val navigator = TimingNavigator(lyrics)
        val random = Random(0)

        val maxTime = lyrics.last().end
        val seekCount = 200_000

        val cost = measureNanoTime {
            repeat(seekCount) {
                val pos = random.nextLong(0, maxTime)
                navigator.first(pos)
            }
        }

        println(
            "随机 Seek Cold Path：" +
                    " calls=$seekCount, " +
                    " time=${cost / 1_000_000} ms"
        )
    }

    @Test
    fun overlappingTraversal() {
        val lyrics = buildLyrics()
        val navigator = TimingNavigator(lyrics)

        val step = 250L
        val maxTime = lyrics.last().end
        var hitCount = 0

        val cost = measureNanoTime {
            var t = 0L
            while (t <= maxTime) {
                hitCount += navigator.forEachAt(t) { }
                t += step
            }
        }

        println(
            "重叠歌词遍历：" +
                    " steps=${maxTime / step}, " +
                    " totalHits=$hitCount, " +
                    " time=${cost / 1_000_000} ms"
        )
    }
}
