/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * ColorOS style flowing-light progress bar.
 *
 * Ported from RawS-Music (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0):
 *   core/ui/.../widget/player/ClassicNativeProgressView.kt
 *   ClassicProgressPainter.kt, ClassicProgressMotion.kt, ClassicProgressInteraction.kt,
 *   ClassicProgressColor.kt (tone-80 tint), ClassicTimelineEffects.kt (frame loop / gestures),
 *   assets/classic_progress_conic.agsl (adapted from SystemUI COUIIntentSeekBar).
 * Changes: Compose gesture/frame loop replaced by plain View touch handling and
 * Choreographer-driven ticks; shader embedded as a string; android.widget.SeekBar API kept.
 */

package io.github.proify.lyricon.xposed.systemui.lyric.control

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.PowerManager
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Motion primitives (ClassicProgressMotion.kt)
// ---------------------------------------------------------------------------------------------

private enum class ConeMode { Off, Playing, Dragging }

private fun coneMode(playing: Boolean, dragging: Boolean) = when {
    !playing -> ConeMode.Off
    dragging -> ConeMode.Dragging
    else -> ConeMode.Playing
}

/** Playback wins over dragging (as in the original renderer). */
private fun headLength(fraction: Float, playing: Boolean, dragging: Boolean): Float =
    min(
        fraction.coerceIn(0f, 1f), when {
            playing -> 0.2f
            dragging -> 0.1f
            else -> 0f
        }
    )

private class Spring(initial: Float) {
    var value = initial
        private set
    var target = initial
    var velocity = 0f
        private set
    val running get() = abs(value - target) > 0.00001f || abs(velocity) > 0.0001f

    fun snap(next: Float) {
        value = next; target = next; velocity = 0f
    }

    /** COUISpringForce: response = 2π/ω, dampingRatio = 1 - bounce. */
    fun advance(seconds: Float, response: Float, bounce: Float = 0f) {
        if (!running) {
            snap(target); return
        }
        val dt = seconds.coerceIn(0f, 0.064f)
        val omega = (2.0 * PI / response.coerceAtLeast(0.01f)).toFloat()
        val displacement = value - target
        val damping = (1f - bounce).coerceIn(0.01f, 1f)
        if (damping >= 0.9999f) {
            val c = velocity + omega * displacement
            val decay = exp(-omega * dt)
            value = target + (displacement + c * dt) * decay
            velocity = (velocity - omega * c * dt) * decay
        } else {
            val dampedOmega = omega * sqrt(1f - damping * damping)
            val decay = exp(-damping * omega * dt)
            val cosine = cos(dampedOmega * dt)
            val sine = sin(dampedOmega * dt)
            val oldVelocity = velocity
            value = target + decay * (displacement * cosine +
                    (oldVelocity + damping * omega * displacement) / dampedOmega * sine)
            velocity = decay * (oldVelocity * cosine -
                    (damping * omega * oldVelocity + omega * omega * displacement) / dampedOmega * sine)
        }
        if (!running) snap(target)
    }
}

private class Fling {
    var fraction = 0f
        private set
    var velocityPx = 0f
        private set
    var running = false
        private set
    private var widthPx = 1f

    fun start(fraction: Float, velocityPx: Float, widthPx: Float): Boolean {
        this.fraction = fraction.coerceIn(0f, 1f)
        this.widthPx = widthPx.coerceAtLeast(1f)
        this.velocityPx = velocityPx.coerceIn(-8000f, 8000f)
        running = widthPx > 1f && abs(this.velocityPx) >= 100f
        return running
    }

    fun advance(seconds: Float) {
        if (!running) return
        var remaining = seconds.coerceIn(0f, 0.064f)
        while (remaining > 0f && running) {
            val dt = min(remaining, 1f / 120f)
            velocityPx /= 1f + 15f * dt
            val next = fraction + velocityPx * dt / widthPx
            fraction = next.coerceIn(0f, 1f)
            if (next <= 0f || next >= 1f || abs(velocityPx) < 1f) cancel()
            remaining -= dt
        }
    }

    fun cancel() {
        running = false; velocityPx = 0f
    }
}

private class ConeFrame(
    val height: Float, val falloff: Float, val coneK: Float, val w0: Float,
    val alpha1: Float, val alpha2: Float,
)

private class ConeAnimation {
    private var mode = ConeMode.Off
    private var from = ConeFrame(0.1f, 0f, 0f, 0f, 0f, 0f)
    private var to = from
    private val shape = Spring(1f)
    private val alpha = Spring(1f)
    private var shapeResponse = 0.4f
    private var alphaResponse = 0.4f
    val running get() = shape.running || alpha.running

    fun transition(next: ConeMode) {
        if (mode == next) return
        from = frame()
        mode = next
        shapeResponse = if (next == ConeMode.Playing) 0.9f else 0.4f
        alphaResponse = if (next == ConeMode.Playing) 0.3f else 0.4f
        to = when (next) {
            ConeMode.Playing -> ConeFrame(0.6f, 0.6f, 0.2f, 0.008f, 1f, 0.6f)
            ConeMode.Dragging -> ConeFrame(0.2f, 2f, 0f, 0.014f, 0.3f, 0.3f)
            ConeMode.Off -> ConeFrame(0.1f, 0f, 0f, 0f, 0f, 0f)
        }
        shape.snap(0f); shape.target = 1f
        alpha.snap(0f); alpha.target = 1f
    }

    fun advance(dt: Float) {
        shape.advance(dt, shapeResponse)
        alpha.advance(dt, alphaResponse)
    }

    fun frame(): ConeFrame {
        val s = shape.value.coerceIn(0f, 1f)
        val a = alpha.value.coerceIn(0f, 1f)
        fun mix(x: Float, y: Float, p: Float) = x + (y - x) * p
        return ConeFrame(
            mix(from.height, to.height, s), mix(from.falloff, to.falloff, s),
            mix(from.coneK, to.coneK, s), mix(from.w0, to.w0, s),
            mix(from.alpha1, to.alpha1, a), mix(from.alpha2, to.alpha2, a)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Painter (ClassicProgressPainter.kt)
// ---------------------------------------------------------------------------------------------

private class ProgressPainter {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val playedRect = RectF()
    private val headRect = RectF()
    private val clip = Path()
    private var headGradient: LinearGradient? = null
    private var gradientWidth = -1f
    private var gradientRtl = false
    private val matrix = Matrix()
    private val conic = if (Build.VERSION.SDK_INT >= 33) ConicShaderPainter() else null
    private var fallback: ConicSoftwarePainter? = null

    fun draw(
        canvas: Canvas, width: Float, centerY: Float, height: Float,
        fraction: Float, headFraction: Float, headAlpha: Float,
        trackArgb: Int, fillArgb: Int, cone: ConeFrame, rtl: Boolean, lightEnabled: Boolean,
    ) {
        val p = fraction.coerceIn(0f, 1f)
        val radius = height / 2f
        trackRect.set(0f, centerY - radius, width, centerY + radius)
        fillPaint.color = trackArgb
        canvas.drawRoundRect(trackRect, radius, radius, fillPaint)
        val headX = if (rtl) width * (1f - p) else width * p
        if (p > 0f) {
            clip.reset()
            clip.addRoundRect(trackRect, radius, radius, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clip)
            playedRect.set(
                if (rtl) headX else 0f, trackRect.top,
                if (rtl) width else headX, trackRect.bottom
            )
            fillPaint.color = fillArgb
            canvas.drawRoundRect(playedRect, radius, radius, fillPaint)
            // White head: keeps the previous length while its alpha fades out.
            val len = headFraction * width
            if (lightEnabled && len > 0f && headAlpha > 0f) {
                headRect.set(
                    if (rtl) headX else headX - len, trackRect.top,
                    if (rtl) headX + len else headX, trackRect.bottom
                )
                if (headGradient == null || len != gradientWidth || rtl != gradientRtl) {
                    headGradient = LinearGradient(
                        0f, 0f, len, 0f,
                        if (rtl) -1 else 0x00ffffff, if (rtl) 0x00ffffff else -1,
                        Shader.TileMode.CLAMP
                    )
                    gradientWidth = len
                    gradientRtl = rtl
                }
                matrix.setTranslate(headRect.left, 0f)
                headGradient!!.setLocalMatrix(matrix)
                headPaint.shader = headGradient
                headPaint.alpha = (headAlpha.coerceIn(0f, 1f) * 255f).toInt()
                canvas.drawRoundRect(headRect, radius, radius, headPaint)
            }
            canvas.restoreToCount(save)
        }
        if (lightEnabled && (cone.alpha1 > 0.00001f || cone.alpha2 > 0.00001f)) {
            val physical = if (rtl) 1f - p else p
            val startScale = if (physical > 0.2f) 1f else (physical / 0.21f).coerceIn(0f, 1f)
            val h = cone.height * startScale
            val f = cone.falloff * startScale
            val save = canvas.save()
            canvas.translate(-300f, centerY - 225f)
            if (conic != null && canvas.isHardwareAccelerated) {
                conic.draw(canvas, width, physical, fillArgb, cone, h, f, rtl)
            } else {
                val software = fallback ?: ConicSoftwarePainter().also { fallback = it }
                software.draw(canvas, width, physical, fillArgb, cone, h, f, rtl)
            }
            canvas.restoreToCount(save)
        }
    }

    fun release() {
        fallback?.release()
    }
}

private class ConicShaderPainter {
    private val shader = RuntimeShader(FlowSeekBar.CONIC_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = this@ConicShaderPainter.shader
        blendMode = BlendMode.SCREEN
    }

    fun draw(
        canvas: Canvas, width: Float, physical: Float, argb: Int,
        cone: ConeFrame, height: Float, falloff: Float, rtl: Boolean,
    ) {
        shader.setFloatUniform("u_resolution", width + 600f, 450f)
        val r = ((argb shr 16) and 255).toFloat()
        val g = ((argb shr 8) and 255).toFloat()
        val b = (argb and 255).toFloat()
        shader.setFloatUniform("u_startColor", r, g, b)
        shader.setFloatUniform("u_endColor", r, g, b)
        shader.setFloatUniform("u_progressHeightParams", physical, width, height, falloff)
        shader.setFloatUniform("u_basicParams", 3f, 1f, 2f, 0.2f)
        shader.setFloatUniform("u_alphaParams", cone.alpha1, cone.alpha2, cone.w0, 500f)
        shader.setFloatUniform("u_coneParams", cone.coneK, cone.coneK, 2f, 1f)
        shader.setFloatUniform("u_miscParams", 1f, if (rtl) 1f else 0f, 0f, 0f)
        canvas.drawRect(0f, 0f, width + 600f, 450f, paint)
    }
}

/** Same bloom equations sampled at low resolution (Android < 13 / software canvas). */
private class ConicSoftwarePainter {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val destination = RectF()

    fun draw(
        canvas: Canvas, width: Float, fraction: Float, argb: Int,
        cone: ConeFrame, coneHeight: Float, coneFalloff: Float, rtl: Boolean,
    ) {
        val rasterWidth = ((width + 600f) / 6f).toInt().coerceIn(1, 512)
        val rasterHeight = 75
        if (bitmap?.width != rasterWidth) {
            release()
            bitmap = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888)
            pixels = IntArray(rasterWidth * rasterHeight)
        }
        val fullWidth = width + 600f
        val spotX = 300f + fraction * width
        val maxHeight = 500f * coneHeight
        val red = ((argb shr 16) and 255) / 255f
        val green = ((argb shr 8) and 255) / 255f
        val blue = (argb and 255) / 255f
        for (y in 0 until rasterHeight) for (x in 0 until rasterWidth) {
            val qx = ((x + 0.5f) * fullWidth / rasterWidth - spotX) * if (rtl) -1f else 1f
            val index = y * rasterWidth + x
            if (qx >= 0f || maxHeight <= 0f || coneFalloff <= 0f) {
                pixels[index] = 0
                continue
            }
            val xp = -qx
            val ay = abs((y + 0.5f) * 450f / rasterHeight - 225f)
            val w = cone.coneK * xp + cone.w0 * 500f
            val edge = 1f - smoothStep(0f, 1000f, ay - w)
            val radial = exp(-(ay / (w + 0.0001f)).pow(2))
            val axial = exp(-2f * xp / 500f)
            val hf = (xp / (maxHeight + 0.0001f)).coerceIn(0f, 1f)
            val intensity = edge * radial * axial * (1f - hf.pow(coneFalloff)) *
                    exp(-max(0f, xp - maxHeight) / 500f * 3f) * smoothStep(0f, 15f, xp)
            val a1 = intensity * cone.alpha1
            val a2 = intensity * cone.alpha2
            val combinedAlpha = a1 * (1f - a2) + a2
            val percent = abs(a2 - a1) / max(combinedAlpha, 0.00001f)
            val r = blendedChannel(red * intensity, a1, a2, percent)
            val g = blendedChannel(green * intensity, a1, a2, percent)
            val b = blendedChannel(blue * intensity, a1, a2, percent)
            val alpha = max(combinedAlpha, max(r, max(g, b))).coerceIn(0f, 1f)
            fun channel(value: Float) =
                if (alpha <= 0f) 0 else (value / alpha * 255f).toInt().coerceIn(0, 255)
            pixels[index] = ((alpha * 255f).toInt() shl 24) or
                    (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
        bitmap!!.setPixels(pixels, 0, rasterWidth, 0, 0, rasterWidth, rasterHeight)
        destination.set(0f, 0f, fullWidth, 450f)
        canvas.drawBitmap(bitmap!!, null, destination, paint)
    }

    fun release() {
        bitmap?.recycle(); bitmap = null
    }

    private fun blendedChannel(base: Float, a1: Float, a2: Float, percent: Float): Float {
        var sum = min(1f, base * 2f)
        if (a1 >= a2) sum += (base - sum) * percent
        if (a2 >= a1) sum += (base - sum) * percent
        return sum.coerceIn(0f, 1f)
    }

    private fun smoothStep(start: Float, end: Float, x: Float): Float {
        val t = ((x - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

// ---------------------------------------------------------------------------------------------
// View
// ---------------------------------------------------------------------------------------------

/**
 * ColorOS 流光进度条。继承 [SeekBar] 以保留面板原有 API
 * （progress / max / [OnSeekBarChangeListener]）；绘制、弹簧动画与手势全部自行处理。
 *
 * 绘制会超出控件边界（光锥向左上下扩散），[onAttachedToWindow] 会关闭祖先的 clipChildren。
 */
internal class FlowSeekBar(context: Context) : SeekBar(context) {

    private val density = resources.displayMetrics.density
    private val painter = ProgressPainter()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val rubberLimit = 28f * density
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val progressSpring = Spring(0f)
    private val pressSpring = Spring(0f)
    private val translationSpring = Spring(0f)
    private val whiteHead = Spring(0f)
    private val cone = ConeAnimation()
    private val fling = Fling()
    private var lastHeadLength = 0f

    private var userListener: OnSeekBarChangeListener? = null
    private var velocity: VelocityTracker? = null

    private var pressed0 = false
    private var dragging = false
    private var flinging = false
    private var seekOwned = false
    private var dragFraction = 0f
    private var rubberTarget = 0f
    private var lastFrameNs = 0L
    private var ticking = false
    private var downX = 0f
    private var downY = 0f
    private var axisUndecided = true
    private var started = false
    private var longPressClaimed = false

    private var trackArgb = 0x59FFFFFF
    private var fillArgb = tone80(0xFFCDE8D5.toInt())

    var isPlaying: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            kick()
        }

    private val longPress = Runnable {
        if (pressed0 && !started && axisUndecided) {
            longPressClaimed = true
            axisUndecided = false
            beginSeek()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            step(System.nanoTime())
            if (isRunning()) schedule()
            invalidate()
        }
    }

    private val fraction: Float
        get() = if (max <= 0) 0f else progress.toFloat() / max.toFloat()

    private val lightEnabled: Boolean
        get() = (powerManager?.isPowerSaveMode != true) &&
                (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())

    private val rtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    fun setAccentSeed(seed: Int) {
        fillArgb = tone80(seed)
        invalidate()
    }

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        // 回调由本类在用户手势时手动派发，避免外部 setProgress 触发 fromUser=false 噪声。
        userListener = l
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        if (!dragging && !flinging) kick()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minH = (28f * density).toInt()
        if (measuredHeight < minH) setMeasuredDimension(measuredWidth, minH)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 光锥向左、上下扩散到控件之外：关闭祖先裁剪。
        var p = parent
        while (p is ViewGroup) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
        }
        progressSpring.snap(fraction)
        kick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(tick)
        removeCallbacks(longPress)
        ticking = false
        lastFrameNs = 0L
        fling.cancel()
        painter.release()
    }

    // ---- frame loop (ClassicTimelineEffects LaunchedEffect body) ----

    private fun isRunning() = progressSpring.running || pressSpring.running ||
            translationSpring.running || whiteHead.running || cone.running || fling.running ||
            dragging || flinging

    private fun kick() {
        if (!ticking && isAttachedToWindow) schedule()
    }

    private fun schedule() {
        ticking = true
        postOnAnimation(tick)
    }

    private fun step(nowNs: Long) {
        val dt = if (lastFrameNs == 0L) 0f else
            ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.064f)
        lastFrameNs = nowNs
        val light = lightEnabled
        pressSpring.target = if (pressed0) 1f else 0f
        translationSpring.target = rubberTarget
        cone.transition(if (light) coneMode(isPlaying, dragging) else ConeMode.Off)
        if (dragging) progressSpring.target = dragFraction
        else if (!flinging) progressSpring.snap(fraction)
        if (flinging) {
            fling.advance(dt)
            progressSpring.snap(fling.fraction)
            if (!fling.running) {
                flinging = false
                finishSeek(fling.fraction)
            }
        } else progressSpring.advance(dt, 0.15f)
        val desiredHead = if (light) headLength(progressSpring.value, isPlaying, dragging) else 0f
        if (desiredHead > 0f) lastHeadLength = desiredHead
        whiteHead.target = if (desiredHead > 0f) 1f else 0f
        whiteHead.advance(dt, 0.5f)
        pressSpring.advance(dt, 0.5f)
        translationSpring.advance(
            dt, if (pressed0) 0.15f else 0.42f,
            bounce = if (pressed0) 0.15f else 0.65f
        )
        cone.advance(dt)
        if (!isRunning()) lastFrameNs = 0L
    }

    // ---- drawing (ClassicNativeProgressView.onDraw) ----

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        if (w <= 0f || height <= 0) return
        val save = canvas.save()
        try {
            canvas.translate(translationSpring.value, 0f)
            painter.draw(
                canvas, w, height / 2f, (4f + 2f * pressSpring.value.coerceIn(0f, 1f)) * density,
                progressSpring.value.coerceIn(0f, 1f), lastHeadLength, whiteHead.value,
                trackArgb, fillArgb, cone.frame(), rtl, lightEnabled
            )
        } finally {
            canvas.restoreToCount(save)
        }
    }

    // ---- gestures (ClassicTimelineEffects pointerInput) ----

    private fun fractionAt(x: Float): Float {
        val physical = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        return if (rtl) 1f - physical else physical
    }

    private fun beginSeek() {
        if (started) return
        started = true
        dragFraction = progressSpring.value
        seekOwned = true
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        userListener?.onStartTrackingTouch(this)
        kick()
    }

    private fun updateDrag(fraction: Float, x: Float) {
        dragFraction = fraction
        val dx = x - downX
        rubberTarget = if (lightEnabled) {
            val amount = abs(dx) * 0.05f
            sign(dx) * rubberLimit * (1f - 1f / (1f + amount / rubberLimit))
        } else 0f
        val p = (fraction * max).toInt().coerceIn(0, max)
        super.setProgress(p)
        userListener?.onProgressChanged(this, p, true)
        kick()
    }

    private fun finishSeek(fraction: Float) {
        pressed0 = false
        dragging = false
        flinging = false
        rubberTarget = 0f
        kick()
        if (!seekOwned) return
        seekOwned = false
        super.setProgress((fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max))
        userListener?.onStopTrackingTouch(this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (fling.running) {
                    fling.cancel()
                    val f = fling.fraction
                    finishSeek(f)
                }
                pressed0 = true
                started = false
                axisUndecided = true
                longPressClaimed = false
                downX = event.x; downY = event.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                kick()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (axisUndecided) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        removeCallbacks(longPress)
                        axisUndecided = false
                        if (abs(dx) >= abs(dy)) beginSeek()
                        else {
                            pressed0 = false
                            kick()
                        }
                    }
                }
                if (started) updateDrag(fractionAt(event.x), event.x)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val released = event.actionMasked == MotionEvent.ACTION_UP
                velocity?.addMovement(event)
                pressed0 = false
                dragging = false
                rubberTarget = 0f
                if (started) {
                    var vx = 0f
                    velocity?.let {
                        it.computeCurrentVelocity(1000)
                        vx = it.xVelocity * if (rtl) -1f else 1f
                    }
                    val last = dragFraction
                    flinging = released && lightEnabled && fling.start(last, vx, width.toFloat())
                    if (!flinging) finishSeek(last)
                } else if (released && axisUndecided) {
                    // tap-to-seek
                    seekOwned = true
                    userListener?.onStartTrackingTouch(this)
                    val tapped = fractionAt(event.x)
                    progressSpring.target = tapped
                    finishSeek(tapped)
                } else {
                    finishSeek(progressSpring.value)
                }
                velocity?.recycle(); velocity = null
                started = false
                kick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    internal companion object {
        /** Seed -> Lab L*=80 with the same a/b (≈ HCT tone 80, as ClassicProgressColor). */
        fun tone80(seed: Int): Int {
            val lab = DoubleArray(3)
            ColorUtils.colorToLAB(seed, lab)
            return ColorUtils.LABToColor(80.0, lab[1], lab[2]) or (0xFF shl 24)
        }

        val CONIC_AGSL = """
uniform float2 u_resolution;
uniform float3 u_startColor;
uniform float3 u_endColor;
uniform float4 u_progressHeightParams;
uniform float4 u_basicParams;
uniform float4 u_alphaParams;
uniform float4 u_coneParams;
uniform float4 u_miscParams;

const float TO_POINT = 0.003921;

float3 blendPlusLighter(float3 base, float3 blend) {
    return min(float3(1.0), base + blend);
}
float4 getBloom(float2 q, float coneK, float alpha1, float2 fragCoord) {
    float u_height = u_progressHeightParams.z;
    float u_heightFalloff = u_progressHeightParams.w;
    float u_alpha = u_basicParams.y;
    float w0 = u_alphaParams.z;
    float u_spotRadius = u_alphaParams.w;
    float radialFalloff = u_coneParams.w;
    float axialFalloff = u_coneParams.z;

    float xp = max(-q.x, 0.0);
    float feather = u_spotRadius;
    float w0_px = w0 * feather;

    float wCone = coneK * xp + w0_px;
    float w = mix(w0_px, wCone, step(0.0, xp));

    float abs_y = abs(q.y);
    float d = abs_y - w;
    float edgeMask = 1.0 - smoothstep(0.0, feather * 2.0, d);

    float rNorm = abs_y / (w + 1e-4);
    float radial = exp(-radialFalloff * rNorm * rNorm);

    float xpNorm = xp / feather;
    float axial = exp(-axialFalloff * xpNorm);

    float maxHeight = feather * u_height;
    float heightFactor = clamp(xp / (maxHeight + 1e-4), 0.0, 1.0);
    float heightAttenuation = 1.0 - pow(heightFactor, u_heightFalloff);
    float cond = step(maxHeight, xp);
    float overDistance = (xp - maxHeight) / feather * cond;
    heightAttenuation *= exp(-overDistance * 3.0);

    float intensity = edgeMask * radial * heightAttenuation * axial;
    intensity *= smoothstep(0.0, 0.03 * feather, xp);

    float3 glowColor = mix(u_startColor, u_endColor, heightFactor) * TO_POINT;
    return half4(glowColor * intensity, intensity * alpha1 * u_alpha);
}

float max3(float3 v) {
    return max(max(v.r, v.g), v.b);
}

float4 premulForAdditiveBlend(float4 c) {
    c.rgb = clamp(c.rgb, 0.0, 1.0);
    c.a = clamp(max(c.a, max3(c.rgb)), 0.0, 1.0);
    return c;
}

float srgbChannelToLinear(float c) {
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
}

float4 srgbPremulToWorkingSpace(float4 c) {
    if (c.a <= 0.0) return float4(0.0);
    float3 straight = clamp(c.rgb / c.a, 0.0, 1.0);
    float3 linear = float3(srgbChannelToLinear(straight.r),
                           srgbChannelToLinear(straight.g),
                           srgbChannelToLinear(straight.b));
    return float4(fromLinearSrgb(linear) * c.a, c.a);
}

half4 main(float2 fragCoord) {
    float u_process = u_progressHeightParams.x;
    float u_rectWidth = u_progressHeightParams.y;
    float u_alpha1 = u_alphaParams.x;
    float u_alpha2 = u_alphaParams.y;
    float coneK = u_coneParams.x;
    float second_coneK = u_coneParams.y;
    float is_RTL = u_miscParams.y;

    float2 pixelPos = fragCoord - u_resolution * 0.5;
    float spotX = (u_process - 0.5) * u_rectWidth;
    float2 q = pixelPos - float2(spotX, 0.0);
    q.x *= 1.0 - 2.0 * is_RTL;

    float4 blend_color = float4(0.0);
    float4 bloom_color = getBloom(q, coneK, u_alpha1, fragCoord);
    float4 bloom_color2 = getBloom(q, second_coneK, u_alpha2, fragCoord);

    float4 base = bloom_color;
    float4 blend = bloom_color2;
    blend_color.rgb = blendPlusLighter(base.rgb, blend.rgb);
    blend_color.a = base.a * (1.0 - blend.a) + blend.a;

    float percent = abs(blend.a - base.a) / max(blend_color.a, 1e-5);
    float mix_bg = step(blend.a, base.a) * percent;
    float mix_fg = step(base.a, blend.a) * percent;
    blend_color.rgb = mix(blend_color.rgb, base.rgb, mix_bg);
    blend_color.rgb = mix(blend_color.rgb, blend.rgb, mix_fg);

    float4 final_color = mix(blend_color, float4(0.0), step(0.0, q.x));
    return srgbPremulToWorkingSpace(premulForAdditiveBlend(final_color));
}
"""
    }
}
