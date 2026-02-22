@file:Suppress("SameParameterValue")

package io

import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import org.junit.Test
import java.util.Random
import kotlin.system.measureNanoTime

/**
 * 歌词导航器性能对比测试。
 * 验证 TimingNavigator 在顺序播放 (O1) 和随机跳转 (LogN) 下的优势。
 */
class TimingNavigatorBenchmark {

    @Test
    fun runComparativeBenchmark() {
        val totalItems = 20000
        val iterations = 10
        val random = Random()

        // 构造测试数据
        val source = Array(totalItems) { i ->
            val start = i * 1000L
            LyricLine(begin = start, end = start + 800, text = "Lyric Line $i")
        }

        val navigator = TimingNavigator(source)
        val naive = NaiveNavigator(source)
        val binary = BinaryNavigator(source)

        println("=== 性能测试 (数据规模: $totalItems 条) ===")

        // 1. 模拟顺序播放场景 (Step = 500ms)
        println("\n[场景 1: 顺序播放 - 模拟高频 UI 刷新]")

        val tNaiveSeq = benchmarkAction(iterations) {
            for (t in 0 until (totalItems * 1000) step 500) {
                naive.first(t.toLong())
            }
        }
        println("线性查找 (Naive):   ${tNaiveSeq / 1_000_000.0} ms")

        val tBinarySeq = benchmarkAction(iterations) {
            for (t in 0 until (totalItems * 1000) step 500) {
                binary.first(t.toLong())
            }
        }
        println("标准二分 (Binary):  ${tBinarySeq / 1_000_000.0} ms")

        val tOptimalSeq = benchmarkAction(iterations) {
            for (t in 0 until (totalItems * 1000) step 500) {
                navigator.first(t.toLong())
            }
        }
        println("双径导航 (Optimal): ${tOptimalSeq / 1_000_000.0} ms -> 缓存命中显著提升性能")

        // 2. 模拟随机跳转场景 (Seek)
        println("\n[场景 2: 随机跳转 - 模拟拖动进度条]")

        val tNaiveRand = benchmarkAction(iterations) {
            repeat(1000) {
                val pos = random.nextInt(totalItems * 1000).toLong()
                naive.first(pos)
            }
        }
        println("线性查找 (Naive):   ${tNaiveRand / 1_000_000.0} ms")

        val tBinaryRand = benchmarkAction(iterations) {
            repeat(1000) {
                val pos = random.nextInt(totalItems * 1000).toLong()
                binary.first(pos)
            }
        }
        println("标准二分 (Binary):  ${tBinaryRand / 1_000_000.0} ms")

        val tOptimalRand = benchmarkAction(iterations) {
            repeat(1000) {
                val pos = random.nextInt(totalItems * 1000).toLong()
                navigator.first(pos)
            }
        }
        println("双径导航 (Optimal): ${tOptimalRand / 1_000_000.0} ms -> 回退至二分效率")
    }

    /**
     * 执行测试并计算平均耗时。
     */
    private inline fun benchmarkAction(iterations: Int, action: () -> Unit): Long {
        repeat(10) { action() } // JVM 预热
        var totalTime = 0L
        repeat(iterations) {
            totalTime += measureNanoTime { action() }
        }
        return totalTime / iterations
    }
}