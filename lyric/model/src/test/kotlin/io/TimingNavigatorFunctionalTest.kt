/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.system.measureNanoTime

/**
 * 针对 TimingNavigator 的功能边界测试
 */
class TimingNavigatorFunctionalTest {

    private companion object {
        const val BENCHMARK_ITEMS = 6000
        const val WARMUP_ROUNDS = 10
        const val MEASURE_ROUNDS = 70
        const val PLAYBACK_STEP_MS = 16L
        const val PLAYBACK_WINDOW_MS = 60_000L
        const val SEEK_STEP_MS = 6000L
        const val RANDOM_QUERIES = 3000

        @Volatile
        var blackhole: Long = 0L
    }

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

    @Test
    fun benchmarkFirstLookupStrategies() {
        val source = Array(BENCHMARK_ITEMS) { i ->
            val start = i * 1000L
            LyricLine(begin = start, end = start + 800, text = "Lyric Line $i")
        }
        val playbackQueries = LongArray((PLAYBACK_WINDOW_MS / PLAYBACK_STEP_MS).toInt()) { index ->
            index * PLAYBACK_STEP_MS
        }
        val steppedSeekQueries =
            LongArray(((BENCHMARK_ITEMS * 1000L) / SEEK_STEP_MS).toInt()) { index ->
                index * SEEK_STEP_MS
            }
        val randomQueries = buildRandomQueries(RANDOM_QUERIES, BENCHMARK_ITEMS * 1000L)

        println("=== TimingNavigator benchmark ===")
        println("items=$BENCHMARK_ITEMS, warmup=$WARMUP_ROUNDS, rounds=$MEASURE_ROUNDS")

        runBenchmarkScenario("playback refresh", source, playbackQueries)
        runBenchmarkScenario("stepped seek", source, steppedSeekQueries)
        runBenchmarkScenario("random seek", source, randomQueries)
    }

    private fun runBenchmarkScenario(
        name: String,
        source: Array<LyricLine>,
        queries: LongArray
    ) {
        val naive = benchmark("Naive", queries) {
            val navigator = NaiveNavigator(source)
            runQueries(queries) { navigator.first(it) }
        }
        val binary = benchmark("Binary", queries) {
            val navigator = BinaryNavigator(source)
            runQueries(queries) { navigator.first(it) }
        }
        val timing = benchmark("TimingNavigator", queries) {
            val navigator = TimingNavigator(source)
            runQueries(queries) { navigator.first(it) }
        }

        assertEquals("$name: Binary checksum", naive.checksum, binary.checksum)
        assertEquals("$name: TimingNavigator checksum", naive.checksum, timing.checksum)

        println("\n[$name] queries=${queries.size}, checksum=${naive.checksum}")
        printBenchmarkResult(naive)
        printBenchmarkResult(binary)
        printBenchmarkResult(timing)
    }

    private inline fun benchmark(
        label: String,
        queries: LongArray,
        action: () -> Long
    ): BenchmarkResult {
        var checksum = 0L
        repeat(WARMUP_ROUNDS) {
            checksum = action()
        }

        val samples = LongArray(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { round ->
            samples[round] = measureNanoTime {
                checksum = action()
            }
        }

        blackhole = blackhole xor checksum
        return BenchmarkResult(label, queries.size, checksum, samples.sortedArray())
    }

    private inline fun runQueries(
        queries: LongArray,
        first: (Long) -> LyricLine?
    ): Long {
        var checksum = 0L
        for (position in queries) {
            checksum = checksum * 31 + (first(position)?.begin ?: -1L)
        }
        return checksum
    }

    private fun buildRandomQueries(size: Int, exclusiveUpperBound: Long): LongArray {
        var state = 0x9E3779B97F4A7C15UL
        return LongArray(size) {
            state = state * 6364136223846793005UL + 1442695040888963407UL
            ((state shr 1).toLong() % exclusiveUpperBound).let { if (it < 0) it + exclusiveUpperBound else it }
        }
    }

    private fun printBenchmarkResult(result: BenchmarkResult) {
        println(
            "${result.label.padEnd(16)} " +
                    "min=${result.minMillis.formatMillis()}ms, " +
                    "median=${result.medianMillis.formatMillis()}ms, " +
                    "p90=${result.p90Millis.formatMillis()}ms, " +
                    "ns/op=${result.medianNsPerOperation}"
        )
    }

    private fun Double.formatMillis(): String = "%.3f".format(this)

    private class BenchmarkResult(
        val label: String,
        val operations: Int,
        val checksum: Long,
        samples: LongArray
    ) {
        val minMillis: Double = samples.first() / 1_000_000.0
        val medianMillis: Double = samples[samples.size / 2] / 1_000_000.0
        val p90Millis: Double = samples[max(0, (samples.size * 9 / 10) - 1)] / 1_000_000.0
        val medianNsPerOperation: Long = samples[samples.size / 2] / operations
    }
}
