/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import io.github.proify.lyricon.lyric.view.line.HdrColor
import java.util.WeakHashMap

/**
 * 图标 / 音乐封面的 HDR 内容绘制。
 *
 * ## 为什么需要它
 *
 * 状态栏窗口本身已经被
 * `io.github.proify.lyricon.xposed.systemui.hook.HdrStatusBarController`
 * 切换成 HDR 画布：`colorMode=HDR`、`setDesiredHdrHeadroom()`、
 * `setExtendedRangeBrightness()`、`setDataSpace(scRGB)`、`ThreadedRenderer.setWideGamut(true)`。
 * 但「画布支持 HDR」不等于「画进去的像素是 HDR 像素」。
 *
 * 歌词路径用 [HdrColor] 的 `Color.pack(..., ColorSpace.Named.EXTENDED_SRGB)`
 * 主动输出超过 SDR 白点（1.0）的分量；而封面 / 图标一直停留在
 * `ARGB_8888` Bitmap + [BitmapShader] + [android.graphics.ColorMatrixColorFilter]，
 * 采样结果天然被限制在 0..1，所以在同一块 HDR 画布上依然是 SDR 内容 —— 这正是
 * HDR 高亮「只对歌词生效、对图标/封面无效」的根因。
 *
 * 本类用 AGSL（[RuntimeShader]）在 GPU 采样阶段把内容推入扩展色域：
 *
 * - [bindImage]：图像提亮，按亮度加权，只抬高亮部，避免整张封面灰雾化；
 * - [bindTint]：单色图标，用位图 alpha 作蒙版，填充与歌词同源的扩展色域 tint。
 *
 * Android 13（API 33）起才有 [RuntimeShader]；更低版本 [isActive] 恒为 false，
 * 调用方回退到原有 SDR 路径（歌词侧的回退策略与此一致）。
 *
 * ## 每个 View 一份实例
 *
 * [RuntimeShader] 的 uniform 是可变的，而硬件加速下 `Canvas` 记录的是 display list，
 * 真正的绘制可能延后到 RenderThread。共享同一个 shader 实例会让后写入的 uniform
 * 覆盖先前记录下来的绘制。因此每个 `SuperLogo` 持有自己的实例，且每帧只绑定一次。
 *
 * ## 坐标与预乘约定
 *
 * - 传入的 [Matrix] 与 [BitmapShader.setLocalMatrix] 语义一致：**设备坐标 → 位图坐标**。
 *   它被设置在 [RuntimeShader] 自己身上，因此内部的 `uImage.eval(fragCoord)` 拿到的
 *   `fragCoord` 已经在位图像素空间；子 [BitmapShader] 必须保持单位矩阵。
 *   这样既避免了子着色器 localMatrix 语义的歧义，也与项目内 GlowSeekBar 的
 *   AGSL 写法（`uTex.eval(clampedSt * uHeadSize)`）保持一致。
 * - AGSL 运行时着色器的输入与输出都按**预乘 alpha** 处理（与 GlowSeekBar 一致），
 *   因此这里的算术全部在预乘空间完成。
 */
internal class HdrLogoShader {

    companion object {
        private const val TAG = "HdrLogoShader"

        /** 比率上限，与歌词、HdrStatusBarController 保持一致。 */
        private const val MAX_RATIO = 4.0f

        private const val U_IMAGE = "uImage"
        private const val U_GAIN = "uGain"
        private const val U_RATIO_PROGRESS = "uRatioProgress"
        private const val U_MASK = "uMask"
        private const val U_TINT = "uTint"
        private const val U_TINT_ALPHA = "uTintAlpha"

        /**
         * 图像提亮着色器。
         *
         * 这里的配色数学是 `io.github.proify.lyricon.lyric.view.line.HdrColor` 的**逐像素移植**，
         * 常量一一对应（枢轴 0.5 / 饱和上限 1.35 / 彩色通道上限 2.0 / 彩色判据 0.08 与 0.12），
         * 目的就是让封面与歌词的高亮表现同源：
         *
         * 1. 整幅**等比**提亮（`color * ratio`），而不是只抬亮部 —— 等比乘法保持像素间对比，
         *    不会把暗封面洗成灰雾；也正因为是等比，即使最终被钳回 SDR，画面依然是肉眼可见的
         *    「变亮 + 变艳」，而不是「几乎没变化」；
         * 2. 彩色像素先把单通道封顶在 2.0（`COLORED_MAX_EXTENDED_CHANNEL`），
         *    避免纯色区域被推到刺眼过饱和；白色/灰色像素拿满比率，与歌词一致；
         * 3. 绕 0.5 枢轴提升饱和度，对应 `HdrColor.boostChannel`。
         */
        private const val IMAGE_BOOST_SHADER = """
            uniform shader uImage;
            uniform float uGain;
            uniform float uRatioProgress;

            vec4 main(vec2 fragCoord) {
                vec4 src = uImage.eval(fragCoord);
                // 输入是预乘色；先还原直通色再做配色运算
                vec3 straight = src.a > 0.0 ? src.rgb / src.a : src.rgb;

                // 与 HdrColor.isColored 同一套判据：色度 + 饱和度双阈值
                float maxC = max(straight.r, max(straight.g, straight.b));
                float minC = min(straight.r, min(straight.g, straight.b));
                float chroma = maxC - minC;
                float saturation = maxC > 0.0 ? chroma / maxC : 0.0;
                bool colored = chroma >= 0.08 && saturation >= 0.12;

                // 对应 HdrColor.boostChannel：绕 0.5 枢轴按倍数拉开通道
                float satBoost = colored ? mix(1.0, 1.35, uRatioProgress) : 1.0;
                vec3 boosted = clamp(vec3(0.5) + (straight - vec3(0.5)) * satBoost, 0.0, 1.0);

                // 对应 HdrColor.colorPreservingRatio：彩色像素压低有效比率
                float gain = uGain;
                if (colored) {
                    float boostedMax = max(boosted.r, max(boosted.g, boosted.b));
                    if (boostedMax > 0.0) {
                        gain = min(uGain, max(2.0 / boostedMax, 1.0));
                    }
                }
                return vec4(boosted * gain * src.a, src.a);
            }
        """

        private const val TINT_SHADER = """
            uniform shader uMask;
            uniform shader uTint;
            uniform float uTintAlpha;

            vec4 main(vec2 fragCoord) {
                float alpha = uMask.eval(fragCoord).a * uTintAlpha;
                return vec4(uTint.eval(fragCoord).rgb * alpha, alpha);
            }
        """

        private var capabilityChecked = false
        private var capabilitySupported = false

        /**
         * 设备是否具备 AGSL 扩展色域绘制能力。
         *
         * 只有 Android 13+ 且着色器能成功编译时为 true；整个进程只探测一次。
         */
        val isSupported: Boolean
            get() {
                ensureCapabilityChecked()
                return capabilitySupported
            }

        /** 是否应当走 HDR 绘制路径。比率等于 SDR 白点时不介入，保持原有观感。 */
        fun isActive(ratio: Float): Boolean = ratio.isFinite() && ratio > 1f && isSupported

        @SuppressLint("NewApi")
        private fun ensureCapabilityChecked() {
            if (capabilityChecked) return
            capabilityChecked = true

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.i(
                    TAG,
                    "AGSL unavailable (sdk=${Build.VERSION.SDK_INT}); icon/cover HDR disabled"
                )
                return
            }

            capabilitySupported = runCatching {
                RuntimeShader(IMAGE_BOOST_SHADER)
                RuntimeShader(TINT_SHADER)
            }.onFailure {
                Log.w(TAG, "AGSL shader compilation failed; icon/cover HDR disabled", it)
            }.isSuccess

            if (capabilitySupported) {
                Log.i(TAG, "AGSL icon/cover HDR available")
            }
        }
    }

    private var imageEffect: RuntimeShader? = null
    private var tintEffect: RuntimeShader? = null
    private var effectsFailed = false

    /** 位图 → 子着色器缓存。Bitmap 用标识哈希，弱键避免长时间持有封面。 */
    private val childShaders = WeakHashMap<Bitmap, BitmapShader>()

    /** 单色图标 tint 缓存：tint 颜色与比率都相同才复用同一个渐变。 */
    private var cachedTintShader: LinearGradient? = null
    private var cachedTintColor: Int = Int.MIN_VALUE
    private var cachedTintRatio: Float = Float.NaN

    private val loggedFailures = mutableSetOf<String>()
    private val loggedBinds = mutableSetOf<String>()

    /**
     * 把 [paint] 绑定成「HDR 图像提亮」绘制。
     *
     * @param matrix 设备坐标 → 位图坐标的映射
     * @return true 表示 [paint] 已可直接用于 `drawRect` 等绘制；false 表示调用方必须回退 SDR 路径
     */
    @SuppressLint("NewApi")
    fun bindImage(paint: Paint, bitmap: Bitmap, matrix: Matrix, ratio: Float): Boolean {
        if (!isActive(ratio)) return false
        val effect = obtainImageEffect() ?: return false
        val child = childShader(bitmap)
        if (child == null) {
            logOnce("image:noChild") {
                "AGSL image bind aborted: no child shader for config=${bitmap.config} " +
                        "colorSpace=${bitmap.colorSpace?.name} size=${bitmap.width}x${bitmap.height}"
            }
            return false
        }

        val bound = runCatching {
            effect.setInputShader(U_IMAGE, child)
            effect.setFloatUniform(U_GAIN, ratio.coerceAtMost(MAX_RATIO))
            effect.setFloatUniform(U_RATIO_PROGRESS, ratioProgress(ratio))
            effect.setLocalMatrix(matrix)
            paint.shader = effect
        }.onFailure {
            logOnce("bindImage") { "AGSL image bind failed: ${it.javaClass.simpleName}: ${it.message}" }
        }.isSuccess

        if (bound) logBoundOnce("image", bitmap, ratio)
        return bound
    }

    /**
     * 把 [paint] 绑定成「HDR 单色 tint」绘制。
     *
     * 用 [mask] 的 alpha 通道作蒙版，填充 [HdrColor] 产出的扩展色域颜色 —— 与状态栏歌词
     * 高亮共用同一套配色数学，因此单色图标与歌词的高光表现天然一致。
     *
     * @param matrix 设备坐标 → 位图坐标的映射
     * @return true 表示 [paint] 已可直接用于 `drawRect` 等绘制；false 表示调用方必须回退 SDR 路径
     */
    @SuppressLint("NewApi")
    fun bindTint(
        paint: Paint,
        mask: Bitmap,
        matrix: Matrix,
        tint: Int,
        ratio: Float
    ): Boolean {
        if (!isActive(ratio)) return false
        val effect = obtainTintEffect() ?: return false
        val maskShader = childShader(mask)
        if (maskShader == null) {
            logOnce("tint:noChild") {
                "AGSL tint bind aborted: no child shader for config=${mask.config} " +
                        "colorSpace=${mask.colorSpace?.name} size=${mask.width}x${mask.height}"
            }
            return false
        }
        val tintGradient = tintShader(tint, ratio) ?: return false

        val bound = runCatching {
            effect.setInputShader(U_MASK, maskShader)
            effect.setInputShader(U_TINT, tintGradient)
            effect.setFloatUniform(U_TINT_ALPHA, Color.alpha(tint) / 255f)
            effect.setLocalMatrix(matrix)
            paint.shader = effect
        }.onFailure {
            logOnce("bindTint") { "AGSL tint bind failed: ${it.javaClass.simpleName}: ${it.message}" }
        }.isSuccess

        if (bound) logBoundOnce("tint", mask, ratio)
        return bound
    }

    /**
     * 饱和提升进度。
     *
     * 与 `HdrColor.saturationBoost` 的进度一致：比率 1.0 时为 0，达到 [MAX_RATIO] 时为 1，
     * 从而把歌词侧 1.0→1.35 的饱和提升原样搬进着色器。
     */
    private fun ratioProgress(ratio: Float): Float =
        ((ratio - 1f) / (MAX_RATIO - 1f)).coerceIn(0f, 1f)

    @SuppressLint("NewApi")
    private fun obtainImageEffect(): RuntimeShader? {
        imageEffect?.let { return it }
        if (effectsFailed) return null
        return runCatching { RuntimeShader(IMAGE_BOOST_SHADER) }
            .onFailure {
                effectsFailed = true
                Log.w(TAG, "Failed to compile image boost shader", it)
            }
            .getOrNull()
            ?.also { imageEffect = it }
    }

    @SuppressLint("NewApi")
    private fun obtainTintEffect(): RuntimeShader? {
        tintEffect?.let { return it }
        if (effectsFailed) return null
        return runCatching { RuntimeShader(TINT_SHADER) }
            .onFailure {
                effectsFailed = true
                Log.w(TAG, "Failed to compile tint shader", it)
            }
            .getOrNull()
            ?.also { tintEffect = it }
    }

    /**
     * 位图子着色器。
     *
     * **保持单位 localMatrix**：适配（FIT_CENTER / CENTER_CROP …）由父着色器的
     * localMatrix 承担，见类注释。
     */
    private fun childShader(bitmap: Bitmap): BitmapShader? {
        childShaders[bitmap]?.let { return it }
        return runCatching {
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }.getOrNull()?.also { childShaders[bitmap] = it }
    }

    /**
     * 单色 tint 的恒定渐变。
     *
     * 用 [LinearGradient] 而不是 `vec4` uniform 传入颜色：长整型配色走 Android 的
     * `ColorSpace` 体系，Skia 会把它正确转换到当前工作色域（HDR 画布下是 scRGB），
     * 而 [RuntimeShader] 的浮点 uniform 只能由我们自己猜测工作色域。
     *
     * 首尾同色 + CLAMP ⇒ 任意坐标都返回同一个颜色。
     */
    @SuppressLint("NewApi")
    private fun tintShader(tint: Int, ratio: Float): LinearGradient? {
        val opaque = Color.argb(255, Color.red(tint), Color.green(tint), Color.blue(tint))
        if (cachedTintShader != null && cachedTintColor == opaque && cachedTintRatio == ratio) {
            return cachedTintShader
        }

        return runCatching {
            val packed = HdrColor.packHighlightColor(opaque, ratio)
            LinearGradient(
                0f, 0f, 1f, 0f,
                longArrayOf(packed, packed), null,
                Shader.TileMode.CLAMP
            )
        }.getOrNull()?.also {
            cachedTintShader = it
            cachedTintColor = opaque
            cachedTintRatio = ratio
        }
    }

    private fun logOnce(label: String, throwable: Throwable) {
        logOnce(label) { "AGSL $label failed: ${throwable.javaClass.simpleName}: ${throwable.message}" }
    }

    /**
     * 只在首次出现时打印的日志。
     *
     * 绑定是逐帧调用的，不能每帧都打；但「绑定失败」必须可见 —— 调用方会静默回退到 SDR，
     * 不做提示的话表现就是「图标在、就是不亮」，极难定位。
     */
    private fun logOnce(key: String, message: () -> String) {
        if (!loggedFailures.add(key)) return
        Log.w(TAG, message())
    }

    /** 首次绑定成功时打印一次，用来和「只是能力就绪」区分开。 */
    private fun logBoundOnce(kind: String, bitmap: Bitmap, ratio: Float) {
        val key = "bound:$kind:${bitmap.config}:${bitmap.colorSpace?.name}"
        if (!loggedBinds.add(key)) return
        Log.i(
            TAG,
            "AGSL $kind bound (extended-range output active): " +
                    "config=${bitmap.config} " +
                    "colorSpace=${bitmap.colorSpace?.name} " +
                    "bitmap=${bitmap.width}x${bitmap.height} ratio=$ratio"
        )
    }
}
