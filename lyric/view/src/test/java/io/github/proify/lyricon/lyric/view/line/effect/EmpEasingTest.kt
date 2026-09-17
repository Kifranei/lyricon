/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 分段缓动与贝塞尔求解器的数值验证。
 *
 * 源项目 empEasing 为脉冲形状：x = 0 起、x = 0.5 达峰值 1、x = 1 回落至 0
 * （第二段 `1 - bezOut`），动画结束后强调效果完全消失。
 */
class EmpEasingTest {

    private fun analytic(x: Float): Float = if (x < 0.5f) {
        CubicBezierEasing.eval(x / 0.5f, 0.2f, 0.4f, 0.58f, 1f)
    } else {
        1f - CubicBezierEasing.eval((x - 0.5f) / 0.5f, 0.3f, 0f, 0.58f, 1f)
    }

    @Test
    fun `pulse shape anchors`() {
        assertEquals(0f, EmpEasing.get(0f), 1e-4f)
        assertEquals(1f, EmpEasing.get(0.5f), 1e-4f)
        assertEquals(0f, EmpEasing.get(1f), 1e-4f)
    }

    @Test
    fun `pulse rises then falls`() {
        // 0 -> 0.5 单调上升
        var previous = -1f
        for (i in 0..256) {
            val v = EmpEasing.get(i / 512f)
            assertTrue("rise at ${i / 512f}", v >= previous)
            previous = v
        }
        // 0.5 -> 1 单调回落
        previous = 2f
        for (i in 256..512) {
            val v = EmpEasing.get(i / 512f)
            assertTrue("fall at ${i / 512f}", v <= previous)
            previous = v
        }
    }

    @Test
    fun `lut deviation from analytic solution stays below 5e-3`() {
        for (i in 0..100) {
            val x = i / 100f
            val deviation = abs(EmpEasing.get(x) - analytic(x))
            assertTrue("deviation at x=$x: $deviation", deviation < 5e-3f)
        }
    }
}
