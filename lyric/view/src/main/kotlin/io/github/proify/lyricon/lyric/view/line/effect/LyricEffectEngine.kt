/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.effect

import io.github.proify.lyricon.lyric.view.line.model.EmphasisGroup

/**
 * 歌词特效引擎：持有并按注册顺序叠加一组 [LyricUnitEffect]。
 *
 * 每帧调用 [beginFrame] 更新全局参数，然后对每个绘制单元调用 [prepareUnit] 取得叠加后的
 * [UnitTransform]。内部全部复用对象，逐帧零分配；特效链可通过 [setEffects] 整体替换，
 * 或 [add] / [remove] 增量维护。
 *
 * @see LyricUnitEffect
 */
class LyricEffectEngine(effects: List<LyricUnitEffect>) {

    private var effects: List<LyricUnitEffect> = effects.toList()

    private val unit = DrawUnit()
    private val param = EffectParam()
    private val transform = UnitTransform()

    /** 当前特效链（按注册顺序叠加）。 */
    val effectList: List<LyricUnitEffect> get() = effects

    /** 整体替换特效链。 */
    fun setEffects(newEffects: List<LyricUnitEffect>) {
        effects = newEffects.toList()
    }

    /** 在链尾追加一个特效。 */
    fun add(effect: LyricUnitEffect) {
        effects = effects + effect
    }

    /** 按 [name] 移除特效（无匹配则不做任何事）。 */
    fun remove(name: String) {
        effects = effects.filterNot { it.name == name }
    }

    /** 更新本帧全局参数（行宽 / 高亮宽度 / 字号 / 当前播放时间 / 动画强度）。 */
    fun beginFrame(
        modelWidth: Float,
        highlightWidth: Float,
        textSize: Float,
        currentTimeMs: Long,
        strength: Float
    ) {
        param.modelWidth = modelWidth
        param.highlightWidth = highlightWidth
        param.textSize = textSize
        param.currentTimeMs = currentTimeMs
        param.strength = strength
        param.progress = if (modelWidth > 0f) {
            (highlightWidth / modelWidth).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * 计算指定单元叠加所有特效后的变换。
     *
     * @param beginMs / [endMs] 单元的时间窗口（毫秒，与播放进度同一时间轴）
     * @param emphasisGroup 单元所属强调辉光组；null 表示不参与强调特效
     * @param emphasisCharIndex 单元在强调组内的字符索引（逐字错峰与左右展开偏移用）
     * @return 引擎内部复用的 [UnitTransform]，仅作一次性读取；切勿持有或跨帧保存。
     */
    internal fun prepareUnit(
        textStart: Int,
        textLength: Int,
        x0: Float,
        x1: Float,
        isCjk: Boolean,
        beginMs: Long,
        endMs: Long,
        emphasisGroup: EmphasisGroup?,
        emphasisCharIndex: Int
    ): UnitTransform {
        unit.textStart = textStart
        unit.textLength = textLength
        unit.x0 = x0
        unit.x1 = x1
        unit.isCjk = isCjk
        unit.beginMs = beginMs
        unit.endMs = endMs
        unit.emphasisGroup = emphasisGroup
        unit.emphasisCharIndex = emphasisCharIndex
        transform.reset()
        for (effect in effects) {
            effect.apply(unit, param, transform)
        }
        return transform
    }
}
