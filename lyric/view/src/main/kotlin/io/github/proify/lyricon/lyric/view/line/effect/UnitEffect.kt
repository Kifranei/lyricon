/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.effect

import io.github.proify.lyricon.lyric.view.line.model.EmphasisGroup

/**
 * 歌词动画系统引擎 —— 可扩展的「逐文本单元」特效契约。
 *
 * 渲染器（[TextDrawer]）把一行歌词切成若干绘制单元（CJK 词逐字、非 CJK 词整词），
 * 每个单元经由 [LyricEffectEngine] 依次叠加所注册的 [LyricUnitEffect]，得到最终呈现
 * 变换（位移 / 透明度 / 缩放）后绘制。新增特效只需实现本接口并注册，无需改动渲染内核。
 * 内置默认特效「WaveLift」即基于本契约实现：未唱的字保持下沉偏移、唱到后先快后慢归位的
 * 单向连续波浪（easeOutQuint 斜坡）。
 *
 * 自定义特效示例：
 * ```
 * class TypewriterEffect : LyricUnitEffect {
 *     override val name = "typewriter"
 *     override fun apply(unit: DrawUnit, param: EffectParam, out: UnitTransform) {
 *         // 尚未唱到的字隐藏（随高亮推进依次出现）
 *         if (unit.x0 > param.highlightWidth) out.alpha = 0f
 *     }
 * }
 * view.effects = listOf(TypewriterEffect())
 * ```
 *
 * @see LyricEffectEngine
 */
interface LyricUnitEffect {

    /** 特效唯一名称（用于管理、移除与调试）。 */
    val name: String

    /**
     * 对单元 [unit] 施加本特效：把结果直接叠加到 [out]（例如 `out.dy += ...`、`out.alpha *= ...`）。
     *
     * 约定：特效应保持无外部副作用（纯函数式），同参数下输出确定，以便与渲染合并策略配合。
     */
    fun apply(unit: DrawUnit, param: EffectParam, out: UnitTransform)
}

/** 一个绘制单元的描述（由渲染器生成；特效只读）。 */
class DrawUnit {
    /** 单元文本在整行文本（[io.github.proify.lyricon.lyric.view.line.model.LyricModel.wordText]）中的起始偏移。 */
    var textStart: Int = 0

    /** 单元文本长度（字符数）。 */
    var textLength: Int = 1

    /** 单元起始 x 位置。 */
    var x0: Float = 0f

    /** 单元结束 x 位置。 */
    var x1: Float = 0f

    /** 是否 CJK/日文/韩文单元（用于区分逐字/整词与动画策略）。 */
    var isCjk: Boolean = false

    /** 单元开始时间（毫秒，与播放进度同一时间轴；CJK 字按词时长均分）。 */
    var beginMs: Long = 0

    /** 单元结束时间（毫秒）。 */
    var endMs: Long = 0

    /** 强调辉光组（预计算）；null 表示该单元不参与强调特效。 */
    internal var emphasisGroup: EmphasisGroup? = null

    /** 单元在强调组内的字符索引（用于逐字错峰与左右展开偏移）。 */
    internal var emphasisCharIndex: Int = 0
}

/** 单帧特效参数（由引擎按帧更新；特效只读）。 */
class EffectParam {
    /** 整行渲染宽度。 */
    var modelWidth: Float = 0f

    /** 当前高亮宽度（进度条）：随唱词推进，0..modelWidth。 */
    var highlightWidth: Float = 0f

    /** 当前字号（dp 缩放后像素值）。 */
    var textSize: Float = 0f

    /** 行进度：highlightWidth / modelWidth，0..1。 */
    var progress: Float = 0f

    /** 当前播放时间（毫秒，与单元 beginMs/endMs 同一时间轴）。 */
    var currentTimeMs: Long = 0

    /**
     * 动画强度（0..1）：由渲染器按「视图可用高度 / 动画完整所需高度」计算，
     * 空间受限（如状态栏小空间）时整体收缩位移 / 缩放 / 辉光幅度，
     * 避免字形被挤出视图边界。
     */
    var strength: Float = 1f
}

/** 单元呈现变换（引擎内部复用的可变对象；仅当帧有效，特效通过 [apply] 修改）。 */
class UnitTransform {
    /** 水平位移。 */
    var dx: Float = 0f

    /** 垂直位移：Android Canvas 坐标下正值向下（移向屏幕下方）。 */
    var dy: Float = 0f

    /** 透明度：1 = 不透明，0 = 完全透明。 */
    var alpha: Float = 1f

    /** 缩放：1 = 原始大小。 */
    var scale: Float = 1f

    /** 白色辉光半径（像素）；0 = 无辉光。 */
    var glowRadius: Float = 0f

    /** 白色辉光强度（0..1，映射为辉光颜色的 alpha）。 */
    var glowAlpha: Float = 0f

    /** 重置为「无变换」状态（每单元调用前由引擎执行）。 */
    fun reset() {
        dx = 0f
        dy = 0f
        alpha = 1f
        scale = 1f
        glowRadius = 0f
        glowAlpha = 0f
    }
}

/** 强调特效的上浮幅度（em）。 */
internal const val EMPHASIS_LIFT_EM = 0.03f

/** 强调特效缩放（上限 1.12）的对称溢出比例（相对文本自然高度）。 */
internal const val SCALE_OVERFLOW_FRACTION = 0.12f

/** WaveLift 默认上浮幅度（em），用于估算动画完整所需高度。 */
internal const val DEFAULT_WAVE_LIFT_EM = 0.05f

/** 空间受限时动画强度的下限：保留少量动画感，避免完全静止。 */
internal const val MIN_STRENGTH = 0.2f
