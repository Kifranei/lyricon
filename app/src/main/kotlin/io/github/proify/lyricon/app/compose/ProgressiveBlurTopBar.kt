package io.github.proify.lyricon.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.pow

/*
 * 顶栏渐进模糊（移植自 Halcyon 的 EllaSmallTopAppBar / Miuix 示例 BlurredBar）：
 * 页面内容通过 [layerBackdrop] 录制为 backdrop，顶栏下方铺一层可变半径的 progressiveTextureBlur，
 * 模糊与着色沿同一条 smoothstep^curve 曲线从顶部向下淡出，并向下溢出 [ProgressiveOverhang]，
 * 让过渡结束在内容上而不是顶栏底边。RuntimeShader 不可用（API < 33）时退化为同曲线的纯色渐变。
 */

/** Miuix 示例 BlurredBar 的渐进模糊参数（与 Halcyon 保持一致）。 */
private const val ProgressiveBlurRadius = 10f
private const val ProgressiveTintAlpha = 0.3f
private val ProgressiveGradient = ProgressiveBlur.Top.copy(curve = 2.2f)

/** 无模糊时仅靠着色区分标题与内容，因此更浓。 */
private const val FallbackTintAlpha = 0.86f

/** 渐进层向顶栏下方溢出的高度。 */
private val ProgressiveOverhang = 16.dp

private const val EasedFadeStops = 12

/** 是否启用顶栏渐进模糊；页面容器根据「顶栏渐进模糊」设置提供。 */
val LocalProgressiveTopBarBlur = staticCompositionLocalOf { true }

/**
 * 创建供顶栏渐进模糊采样的页面 backdrop。
 *
 * 内容层在透明底上录制会被着色器归一化成不透明光晕，因此先铺一层不透明的页面底色。
 * 未启用或 RuntimeShader 不可用时返回 `null`，此时 [ProgressiveBlurTopBar] 使用纯色渐变兜底。
 *
 * @param color 录制内容时垫在下方的不透明底色，一般为页面背景色。
 */
@Composable
fun rememberProgressiveBlurBackdrop(
    color: Color = MiuixTheme.colorScheme.surface,
): LayerBackdrop? {
    if (!LocalProgressiveTopBarBlur.current || !isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }
}

/**
 * 把页面内容录制进 [backdrop]；[backdrop] 为 `null` 时不做任何处理。
 *
 * @param backdrop [rememberProgressiveBlurBackdrop] 返回的 backdrop。
 */
fun Modifier.progressiveBlurSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * 带渐进模糊背景的顶栏容器，内部顶栏需使用透明背景（`color = Color.Transparent`）。
 *
 * @param backdrop 页面内容 backdrop，`null` 时使用纯色渐变兜底。
 * @param modifier 修饰符。
 * @param color 着色颜色，一般为页面背景色。
 * @param enabled 关闭时直接使用不透明的 [color] 作为顶栏背景。
 * @param content 顶栏内容。
 */
@Composable
fun ProgressiveBlurTopBar(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    enabled: Boolean = LocalProgressiveTopBarBlur.current,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxWidth().background(color)) { content() }
        return
    }
    val layer = if (backdrop != null) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = ProgressiveBlurRadius,
            gradient = ProgressiveGradient,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(color = color.copy(alpha = ProgressiveTintAlpha))),
            ),
        )
    } else {
        val fallbackTint = remember(color) { easedFadeBrush(color, FallbackTintAlpha) }
        Modifier.background(fallbackTint)
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .overhangBelow(ProgressiveOverhang)
                .then(layer),
        )
        content()
    }
}

/**
 * 以比槽位高 [extra] 的尺寸绘制，但向父布局报告原槽位尺寸，不影响调用方的内边距计算。
 *
 * @param extra 额外向下溢出的高度。
 */
private fun Modifier.overhangBelow(extra: Dp): Modifier = layout { measurable, constraints ->
    // 没有顶栏（高度为 0）时不溢出，避免在页面顶部画出一条模糊带。
    if (!constraints.hasBoundedHeight || !constraints.hasBoundedWidth || constraints.maxHeight == 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra.roundToPx()),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
}

/**
 * 与 Miuix 渐进着色器 rampWeight 相同的权重：smoothstep(1 − smoothstep(t)^curve)，两端斜率为 0。
 *
 * @param t 归一化的纵向位置。
 */
private fun easedFadeWeight(t: Float): Float {
    val s = t * t * (3f - 2f * t)
    val w = 1f - s.pow(ProgressiveGradient.curve)
    return w * w * (3f - 2f * w)
}

private fun easedFadeBrush(color: Color, maxAlpha: Float): Brush = Brush.verticalGradient(
    *Array(EasedFadeStops + 1) { i ->
        val t = i / EasedFadeStops.toFloat()
        t to color.copy(alpha = color.alpha * maxAlpha * easedFadeWeight(t))
    },
)
