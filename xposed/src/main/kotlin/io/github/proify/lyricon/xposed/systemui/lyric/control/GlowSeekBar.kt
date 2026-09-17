/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Base64
import android.widget.SeekBar
import io.github.proify.android.extensions.dp
import kotlin.math.max

/**
 * 辉光进度条：以 AGSL 着色器绘制胶囊轨道与进度头部光晕。
 *
 * 继承 [SeekBar] 而非独立 View，是为了完整保留上游控制面板的拖动 / 点击 seek、
 * 无障碍与刚体锁定逻辑——本类只接管「怎么画」，不改「怎么交互」。
 *
 * Android 13 以下没有 [RuntimeShader]，此时回退到 [SeekBar] 自身的
 * progressDrawable（即面板原有的胶囊轨道），观感降级但功能不变。
 */
internal class GlowSeekBar(context: Context) : SeekBar(context) {

    /** 轨道厚度（像素）。 */
    var trackHeightPx: Float = LONG_ROUNDED_TRACK_HEIGHT_DP.dp.toFloat()
        set(value) {
            field = max(1f, value)
            invalidate()
        }

    /** 轨道左右留白，给头部光晕留出不被裁切的余量。 */
    var trackHorizontalPaddingPx: Float = TRACK_SIDE_PADDING_DP.dp.toFloat()
        set(value) {
            field = max(0f, value)
            invalidate()
        }

    /** 光晕着色；面板为深色封面背景，默认纯白。 */
    var glowColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var runtimeShader: RuntimeShader? = null
    private var headWidth = 75f
    private var headHeight = 38f
    private var shaderReady = false

    private val progressFraction: Float
        get() = if (max <= 0) 0f else progress.toFloat() / max.toFloat()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shaderReady = runCatching { configureShader() }.isSuccess
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!shaderReady) return
        // 光晕高于轨道本身，需保证足够高度，否则头部光晕会被裁掉
        val minHeight = GLOW_HEIGHT_DP.dp
        if (measuredHeight < minHeight) {
            setMeasuredDimension(measuredWidth, minHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!shaderReady || !drawGlow(canvas)) {
            super.onDraw(canvas)
        }
    }

    @SuppressLint("NewApi")
    private fun drawGlow(canvas: Canvas): Boolean {
        val shader = runtimeShader ?: return false
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return false

        val trackHeight = trackHeightPx.coerceAtMost(heightPx)
        val trackWidth = max(0f, widthPx - trackHorizontalPaddingPx * 2f)
        val canvasSize = floatArrayOf(widthPx, heightPx)

        return runCatching {
            shader.setFloatUniform("uResolution", canvasSize)
            shader.setFloatUniform("uTrackCanvasSize", canvasSize)
            shader.setFloatUniform(
                "uTrackPosition",
                floatArrayOf(trackHorizontalPaddingPx, heightPx / 2f)
            )
            shader.setFloatUniform("uTrackSize", floatArrayOf(trackWidth, trackHeight))
            shader.setFloatUniform("uHeadSize", floatArrayOf(headWidth, headHeight))
            shader.setFloatUniform("uTrackProgress", progressFraction)
            shader.setFloatUniform("uHeadGlowAlpha", 1f)
            shader.setIntUniform("uIsRtl", if (layoutDirection == LAYOUT_DIRECTION_RTL) 1 else 0)
            shader.setFloatUniform(
                "uContentColor",
                Color.red(glowColor) / 255f,
                Color.green(glowColor) / 255f,
                Color.blue(glowColor) / 255f
            )
            canvas.drawRect(0f, 0f, widthPx, heightPx, shaderPaint)
        }.isSuccess
    }

    @SuppressLint("NewApi")
    private fun configureShader() {
        val shader = RuntimeShader(GLOW_SHADER)
        shader.setInputShader("uTex", createHeadBitmapShader())
        runtimeShader = shader
        shaderPaint.shader = shader
    }

    /**
     * 头部光晕素材。
     *
     * 外圈一圈像素强制清零：素材边缘若残留不透明像素，采样越界时会沿轨道
     * 拖出一条亮边。
     */
    private fun createHeadBitmapShader(): BitmapShader {
        val bytes = Base64.decode(HEAD_PNG_BASE64, Base64.DEFAULT)
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val w = decoded.width
        val h = decoded.height

        val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until 3) pixels[y * w + x] = 0
            pixels[y * w + (w - 1)] = 0
        }
        for (x in 0 until w) {
            pixels[x] = 0
            pixels[(h - 1) * w + x] = 0
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        headWidth = w.toFloat()
        headHeight = h.toFloat()
        val tileMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Shader.TileMode.DECAL
        } else {
            Shader.TileMode.CLAMP
        }
        return BitmapShader(bitmap, tileMode, tileMode)
    }

    private companion object {
        /** 与面板原胶囊轨道一致的厚度。 */
        const val LONG_ROUNDED_TRACK_HEIGHT_DP = 5

        /** 轨道左右留白。 */
        const val TRACK_SIDE_PADDING_DP = 2

        /** 容纳头部光晕所需的最小控件高度。 */
        const val GLOW_HEIGHT_DP = 28

        const val HEAD_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAEsAAAAmCAQAAAAmLa7lAAAD9UlEQVRYw+2YvY7dNhBGz1CU7rpNkyJd3v8l7JcIkARxkSBO5WKdhe31ipOCI2r4p12nCBDAurgSRVHi0TfD4VDwbfuPNpW+XB9VfI2Kv+Nqk38PcvFMrY59A81PEv0KLBXRy+4F0HJn7jzXjV9TG0A9kWdg8gIlZPoaHm3eSu1/nKmHHYPJBKo/azUSe7BUeDQq4vZaALW6NkCLF/qcGkj51wAygJHOkKmYMaEFCQ82BFAZIM330p0diNJA1jplqFRqnGatXnGoEhcI/b8vhWLcZEDZARKJUOmlY72i02qmTVv2GL5WuutKAJREAhJiaI0Bh75VhYORMnNNapizlNU6/UkIBhYA2MtzFAG9MKJMwKQpn2gey/9CFd8CyUwpCLtBK8mekUZmjJXD9+NMOnPVMBSUFu70noSS2FGUgAKB3bS0ENzqFQfu3YLkt/JqBDsPpRxYSimwNIgZ7JEPPPCZZEYV03Lg9lG9DocaR2dL6Th3u1jNSrSajZWFhciNyMLCSuSOaPdEghlTeeB3fuJPPpVxKsX9OyOK63KxR8XSxVbOVzY7rtxYWdiI3NjK8Y7VSq9YWVkJBKKNQeUjv/Ga1/xhY1K7GaEy4kJkJbKxWrcrWzluhpQBcruboRzl3G6zFhlwYyMgxaOEL3zPzjvec1+0Or2sU0uKIrE8+EBaXWd1/Vp0OtocLxaLsRcLFHm78R0/8gN3fCjjfWLE4JKPNvWQKharywRSGbWhuRMLoPl6KnMik1xDxxlftMEr5poHQiKx80Qi8cTGbjUbSmIDdvZm+lWHFoAFWFyu8MR73vKOjxVUmmHtQGLns8l/7LPHLY3Db9xKKRsze9idtX9lnhe5YytYAeFvfuUNv/DJAqzOU5soqgk1vagiUg4QUoa62HEpNZsLChsLwUZibnNjNZcXFoR73vIzf/HFQeWYJiqjcKrWIE8OMgioZ1ANLqjm7gJi0MECjQCL+a2apyUeueeBR8snzrwCXpAGamV5sf05saZqPsRhSjM5qfPUw1t3K2mXdeEVi91i4Myz68UDnaNKidPB2oaCSYnt5xDK++Rqq0Dqzeiz0+cyTumm8HkWcQSDFmw3rL261rl8nTT3YEwzriPm9flWndhkJIpax1maQ5VU5tkM9Tpvl2HCSAm9HuwEPf1rrNY052Ky3JJhukiVy3u1ai9TB+uWsd7lx+tEmayDdIpWZ+/iBk+9RtQSHs4sS6dGHK6qZbh+brG1Wyd6z6KDa5awz6yqLxb8Mvz4IdU6+/wcQjXF+2vJxibXK+qLLzYZ7/iy0qyOZh9Jrr4AHZFe6rj+gk8jX/cxqQVtzqvUzl/LIO0cOKv7tv2Pt38A9nPaiTc6xcoAAAAASUVORK5CYII="

        val GLOW_SHADER = """
            uniform vec2 uResolution;
            uniform shader uTex;
            uniform float uTrackProgress;
            uniform vec2 uTrackSize;
            uniform vec2 uTrackPosition;
            uniform vec2 uTrackCanvasSize;
            uniform vec2 uHeadSize;
            uniform float uHeadGlowAlpha;
            uniform int uIsRtl;
            uniform vec3 uContentColor;

            vec4 alphaBlend(vec4 src, vec4 dst) {
                vec3 color = src.rgb + (1.0 - src.a) * dst.rgb;
                float alpha = src.a + dst.a * (1.0 - src.a);
                return vec4(color, alpha);
            }

            float rbx(in vec2 p, in vec2 b, in vec4 r) {
                r.xy = (p.x > 0.0) ? r.xy : r.zw;
                r.x = (p.y > 0.0) ? r.x : r.y;
                vec2 q = abs(p) - b + r.x;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
            }

            float capsule(in vec2 uv, in vec2 pos, in vec2 size) {
                vec2 p = uv - vec2(0.5);
                p.x *= uResolution.x / uResolution.y;
                vec2 b = size / uResolution.y / 2.0;
                p.y += (uResolution.y / 2.0 - pos.y - size.y / 2.0) / uResolution.y;
                p.x -= pos.x / uResolution.y + b.x - uResolution.x / uResolution.y / 2.0;
                vec4 r = vec4(min(size.x, size.y) / uResolution.y / 2.0);
                return rbx(p, b, r);
            }

            vec4 draw_track(in vec2 uv) {
                float d = capsule(uv, vec2(uTrackPosition.x, uResolution.y - uTrackPosition.y - uTrackSize.y / 2.0), uTrackSize);
                float a = smoothstep(1.0 / uResolution.y, -1.0 / uResolution.y, d);
                return vec4(0.2 * a);
            }

            vec4 draw_track_progress(in vec2 uv, in float progress) {
                float m = min(uTrackSize.x, uTrackSize.y);
                vec2 size = uTrackSize;
                size.x = mix(m, uTrackSize.x, progress);
                float d = capsule(uv, vec2(uTrackPosition.x, uResolution.y - uTrackPosition.y - uTrackSize.y / 2.0), size);
                float a = smoothstep(1.0 / uResolution.y, -1.0 / uResolution.y, d) * 0.6;

                vec2 hsize = vec2(75.0, 38.0) * 2.7551020408;
                float thight = 6.0 * 2.7551020408;
                float xoffset = 52.0 * 2.7551020408;
                vec2 st = uv;
                st.x = (st.x - uTrackPosition.x / uResolution.x) / (uTrackSize.x / uResolution.x);
                st.y -= (uResolution.y - uTrackPosition.y - uTrackSize.y / 2.0) / uResolution.y;
                st.y *= uResolution.y / uTrackSize.y;
                float startFade = smoothstep(0.0, uTrackSize.y / uTrackSize.x * (hsize.x / hsize.y) * 1.5, st.x);
                st.x -= mix(uTrackSize.y / 2.0, uTrackSize.x - uTrackSize.y / 2.0, progress) / uTrackSize.x;
                st.y -= 0.5;
                float headScale = clamp(uTrackSize.y / thight, 0.75, 1.15);
                st.x /= headScale * (hsize.x / uTrackSize.x);
                st.y /= headScale * (hsize.y / uTrackSize.y);
                st.y += 0.5;
                st.x -= -xoffset / hsize.x;
                vec2 clampedSt = clamp(st, 0.0, 1.0);
                float inBounds = step(0.0, st.x) * step(st.x, 1.0) * step(0.0, st.y) * step(st.y, 1.0);
                vec4 head = uTex.eval(clampedSt * uHeadSize) * (startFade * uHeadGlowAlpha * inBounds);
                return alphaBlend(head, vec4(a));
            }

            vec4 main(vec2 fragCoord) {
                vec2 vUv = fragCoord / uResolution;
                if (uIsRtl == 1) {
                    vUv.x = 1.0 - vUv.x;
                }
                vec4 color = vec4(0.0);
                color = alphaBlend(draw_track(vUv), color);
                color = alphaBlend(draw_track_progress(vUv, uTrackProgress), color);
                // Tint the (premultiplied) output so the bar can invert to dark on a light player.
                return vec4(color.rgb * uContentColor, color.a);
            }
        """.trimIndent()
    }
}
