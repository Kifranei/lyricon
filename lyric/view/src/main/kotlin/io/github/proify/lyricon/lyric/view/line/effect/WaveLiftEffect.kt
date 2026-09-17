/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.effect

import io.github.proify.lyricon.lyric.view.WordMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * 内置「Apple Music 式浮动」特效（默认启用，轻量版——状态栏/小空间用）：
 *
 * 1. **持续微上浮**：词被唱到后保持 `-0.05em × liftFactor` 的上浮位（归到上浮位，不再回落）；
 * 2. **上浮周期**：每个字/词在开始前 400ms 触发一次 `sin(π·x) × 0.05em` 的柔和浮动，
 *    时长 = max(1s, 词时长) × 1.4；
 *
 * 所有幅度都在 0.05em（≈3px）量级：柔和的起伏感而非大位移跳动；词内所有字共用词级时间窗，
 * 整词同时触发（无 stagger）。参数由 [WordMotion] 驱动（接口兼容）：
 * `liftFactor` = 上浮幅度（相对字号）。
 */
internal class WaveLiftEffect : LyricUnitEffect {

    override val name: String = "wave_lift"

    var cjkLiftFactor = WordMotion().cjkLiftFactor
    var cjkWaveFactor = WordMotion().cjkWaveFactor
    var latinLiftFactor = WordMotion().latinLiftFactor
    var latinWaveFactor = WordMotion().latinWaveFactor

    override fun apply(unit: DrawUnit, param: EffectParam, out: UnitTransform) {
        val lift = if (unit.isCjk) cjkLiftFactor else latinLiftFactor

        val duration = max(1000L, unit.endMs - unit.beginMs)
        val elapsed = param.currentTimeMs - unit.beginMs

        // 1) 持续微上浮：唱到后保持 -lift × 字号（0.05em 默认），不回落到基线。
        if (elapsed >= 0) {
            out.dy -= param.textSize * lift
        }

        // 2) 上浮周期：sin(π·x) × 0.05em，提前 400ms，时长 ×1.4。
        val floatDuration = (duration * 14L) / 10L
        val floatTime = elapsed + 400L
        if (floatTime in 0..floatDuration) {
            val x = floatTime.toFloat() / floatDuration
            out.dy -= param.textSize * lift * sin(x * PI).toFloat()
        }
    }
}
