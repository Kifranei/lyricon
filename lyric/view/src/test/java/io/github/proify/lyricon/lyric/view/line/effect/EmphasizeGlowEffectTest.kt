/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.effect

import io.github.proify.lyricon.lyric.view.line.model.EmphasisGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强调辉光特效的空间自适应强度（strength）行为验证：
 * 状态栏等小空间下按比例收缩缩放 / 位移 / 辉光幅度，避免字形挤出边界。
 */
class EmphasizeGlowEffectTest {

    private fun apply(
        strength: Float,
        currentTimeMs: Long = 500L,
        textSize: Float = 50f,
    ): UnitTransform {
        val unit = DrawUnit().apply {
            emphasisGroup = EmphasisGroup(
                lineBegin = 0,
                startMs = 0,
                durationMs = 2000,
                amount = 0.6f,
                blur = 0.5f,
                charCount = 2,
            )
            emphasisCharIndex = 0
        }
        val param = EffectParam().apply {
            this.textSize = textSize
            this.currentTimeMs = currentTimeMs
            this.strength = strength
        }
        val out = UnitTransform()
        EmphasizeGlowEffect().apply(unit, param, out)
        return out
    }

    @Test
    fun `full strength produces visible effect`() {
        val out = apply(1f)
        assertTrue(out.scale > 1f)
        assertTrue(out.dx != 0f)
        assertTrue(out.dy < 0f)
        assertTrue(out.glowRadius > 0f)
        assertTrue(out.glowAlpha > 0f)
    }

    @Test
    fun `halved strength halves all magnitudes`() {
        val full = apply(1f)
        val half = apply(0.5f)

        assertEquals((full.scale - 1f) / 2f, half.scale - 1f, 1e-4f)
        assertEquals(full.dx / 2f, half.dx, 1e-4f)
        assertEquals(full.dy / 2f, half.dy, 1e-4f)
        assertEquals(full.glowRadius / 2f, half.glowRadius, 1e-4f)
        assertEquals(full.glowAlpha / 2f, half.glowAlpha, 1e-4f)
    }

    @Test
    fun `completed pulse returns to rest regardless of strength`() {
        // elapsed >= du → x = 1 → eased = 0（脉冲回落），变换归零。
        val out = apply(1f, currentTimeMs = 2000L)
        assertEquals(1f, out.scale, 1e-5f)
        assertEquals(0f, out.dx, 1e-5f)
        assertEquals(0f, out.dy, 1e-5f)
        assertEquals(0f, out.glowRadius, 1e-5f)
        assertEquals(0f, out.glowAlpha, 1e-5f)
    }
}
