package io.github.proify.lyricon.app.compose.effect

import androidx.compose.ui.graphics.Brush
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import kotlin.math.cos
import kotlin.math.sin

/**
 * 流光背景绘制器。
 *
 * @param isOs3 true 使用 OS3 着色器（颜色随时间在三组配色间插值），
 *              false 使用 OS2 着色器（配色固定，四个光点随时间做圆周漂移）。
 * @param deviceType OS2 观感下手机 / 平板使用不同的光点布局与配色。
 */
class BgEffectPainter(
    private val isOs3: Boolean = true,
    private val deviceType: DeviceType = DeviceType.PHONE,
) {
    val runtimeShader by lazy {
        RuntimeShader(if (isOs3) OS3_BG_FRAG else OS2_BG_FRAG).also {
            initStaticUniforms(it)
        }
    }

    val brush: Brush by lazy { runtimeShader.asBrush() }

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private var animTime = Float.NaN
    private val pointsAnimBuffer = FloatArray(8)
    private var isDarkCached: Boolean? = null
    private var deviceTypeCached: DeviceType? = null
    private var presetApplied = false

    companion object {
        private const val U_TRANSLATE_Y = 0f
        private const val U_ALPHA_MULTI = 1f
        private const val U_NOISE_SCALE = 1.5f
        private const val U_POINT_RADIUS_MULTI = 1f
        private const val U_ALPHA_OFFSET = 0.1f
        private const val U_SHADOW_OFFSET = 0.01f
    }

    private fun initStaticUniforms(shader: RuntimeShader) {
        shader.setFloatUniform("uTranslateY", U_TRANSLATE_Y)
        shader.setFloatUniform("uNoiseScale", U_NOISE_SCALE)
        shader.setFloatUniform("uPointRadiusMulti", U_POINT_RADIUS_MULTI)
        shader.setFloatUniform("uAlphaMulti", U_ALPHA_MULTI)
        if (isOs3) {
            shader.setFloatUniform("uAlphaOffset", U_ALPHA_OFFSET)
            shader.setFloatUniform("uShadowOffset", U_SHADOW_OFFSET)
        }
    }

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)

        // OS2 的流动来自光点位移（配色固定），需逐帧推送新的光点位置；
        // OS3 的流动由着色器内部按 uAnimTime 自行完成。
        if (!isOs3) {
            val preset = BgEffectConfig.get(deviceType, isDarkCached ?: false, isOs3 = false)
            val offset = preset.pointOffset
            for (i in 0 until 4) {
                val srcX = preset.points[i * 3]
                val srcY = preset.points[i * 3 + 1]
                val animX = srcX + sin(time + srcY) * offset
                val animY = srcY + cos(time + animX) * offset
                pointsAnimBuffer[i * 2] = animX
                pointsAnimBuffer[i * 2 + 1] = animY
            }
            runtimeShader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
        }
    }

    fun updateColors(colors: FloatArray) {
        runtimeShader.setFloatUniform("uColors", colors)
    }

    fun updatePresetIfNeeded(logoHeight: Float, height: Float, width: Float, isDark: Boolean) {
        if (presetApplied && isDarkCached == isDark && deviceTypeCached == deviceType) return
        updateBound(logoHeight, height, width)
        applyPreset(isDark)
        isDarkCached = isDark
        deviceTypeCached = deviceType
        presetApplied = true
    }

    private fun applyPreset(isDark: Boolean) {
        val preset = BgEffectConfig.get(deviceType, isDark, isOs3)
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        runtimeShader.setFloatUniform("uBound", bound)
        if (isOs3) {
            runtimeShader.setFloatUniform("uPointOffset", preset.pointOffset)
            runtimeShader.setFloatUniform("uShadowColorMulti", preset.shadowColorMulti)
            runtimeShader.setFloatUniform("uShadowColorOffset", preset.shadowColorOffset)
            runtimeShader.setFloatUniform("uShadowNoiseScale", preset.shadowNoiseScale)
        }
    }

    private fun updateBound(logoHeight: Float, totalHeight: Float, totalWidth: Float) {
        val heightRatio = logoHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }
}
