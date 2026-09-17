package io.github.proify.lyricon.app.compose.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.compose.preference.rememberIntPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    effectBackground: Boolean = true,
    isDarkTheme: Boolean? = null,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }
    Box(modifier = modifier) {
        val surface = MiuixTheme.colorScheme.surface

        // 流光观感：默认跟随 HyperOS 大版本（OS2 用 OS2 着色器，OS3+ / 非小米用 OS3，
        // OS1 不绘制），用户也可在设置里手动指定 OS2 / OS3。
        val bgEffectStyle by rememberIntPreference(
            LocalContext.current.defaultSharedPreferences,
            HyperOsDetector.KEY_BG_EFFECT_STYLE,
            HyperOsDetector.STYLE_AUTO
        )
        val bgEffectVersion = remember(bgEffectStyle) {
            HyperOsDetector.resolveBgEffectVersion(bgEffectStyle)
        }
        val isOs3 = bgEffectVersion != HyperOsDetector.BG_EFFECT_OS2
        val drawEffect = effectBackground && bgEffectVersion != HyperOsDetector.BG_EFFECT_NONE

        val deviceType = if (LocalConfiguration.current.screenWidthDp >= TABLET_MIN_WIDTH_DP) {
            DeviceType.PAD
        } else {
            DeviceType.PHONE
        }

        val painter = remember(isOs3, deviceType) { BgEffectPainter(isOs3, deviceType) }
        val animTime = rememberFrameTimeSeconds(dynamicBackground)
        val isDark = isDarkTheme ?: (MiuixTheme.colorScheme.background.luminance() < 0.5f)
        val preset = remember(isDark, deviceType, isOs3) {
            BgEffectConfig.get(deviceType, isDark, isOs3)
        }
        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset, drawEffect) {
            if (!dynamicBackground || !drawEffect) return@LaunchedEffect
            var targetStage = 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(bgModifier)) {
            drawRect(surface)
            if (drawEffect) {
                val drawHeight = size.height * 0.78f
                val stage = colorStage.value
                val base = stage.toInt()
                val fraction = stage - base
                val getColors = { index: Int ->
                    when (index % 4) {
                        0 -> preset.colors2
                        1 -> preset.colors1
                        2 -> preset.colors2
                        3 -> preset.colors3
                        else -> preset.colors2
                    }
                }
                val start = getColors(base)
                val end = getColors(base + 1)
                val currentColors = FloatArray(16) { i -> start[i] + (end[i] - start[i]) * fraction }
                painter.updateResolution(size.width, size.height)
                painter.updatePresetIfNeeded(drawHeight, size.height, size.width, isDark)
                painter.updateColors(currentColors)
                painter.updateAnimTime(animTime())
                drawRect(painter.brush, alpha = alpha())
            }
        }
        content()
    }
}

/** 与主界面一致的平板判定阈值。 */
private const val TABLET_MIN_WIDTH_DP = 600
