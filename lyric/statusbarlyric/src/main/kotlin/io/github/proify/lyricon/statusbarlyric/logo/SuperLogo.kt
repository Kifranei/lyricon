/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import android.widget.TextView
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.isVisibleIfChanged
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusColor
import io.github.proify.lyricon.subscriber.ProviderLogo
import java.io.File
import kotlin.math.roundToInt

/**
 * 用于显示歌词来源图标、APP图标或专辑封面的视图组件。
 * 负责处理图标样式的动态切换、进度绘制以及状态栏颜色适配。
 */
@SuppressLint("AppCompatCustomView")
class SuperLogo(context: Context) : View(context) {

    var linkedTextView: TextView? = null

    var strategy: ILogoStrategy? = null
        private set

    var providerLogo: ProviderLogo? = null
        set(value) {
            if (field !== value) {
                field = value
                // 提供者变更时，若当前策略为 ProviderStrategy，需通知其重置缓存
                (strategy as? ProviderStrategy)?.invalidateCache()
                reassessStrategy()
            }
        }

    var currentStatusColor: StatusColor = StatusColor()
    var lyricStyle: LyricStyle? = null

    var hdrHighlightRatio: Float = 1.0f
        set(value) {
            val ratio = if (value.isFinite() && value > 1.0f) value else 1.0f
            if (field == ratio) return
            field = ratio
            if (strategy?.isEffective == true) {
                strategy?.onColorUpdate()
            }
            invalidate()
        }

    internal var forceHide = false
        set(value) {
            field = value
            updateVisibility()
        }

    // --- 进度条绘制属性 ---
    private var progress: Float = 0f
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 255
    }
    private val progressRect = RectF()
    private var progressAnimator: ValueAnimator? = null

    private var selfDrawnCoverBitmap: Bitmap? = null
    private var coverShader: BitmapShader? = null
    private var coverShaderBitmap: Bitmap? = null
    private var coverShaderWidth = -1
    private var coverShaderHeight = -1
    private val coverShaderMatrix = Matrix()
    private val coverRect = RectF()
    private val coverPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
    )
    private val imageRect = RectF()
    private var imageDrawable: Drawable? = null
    private var imageBitmap: Bitmap? = null
    private var imageShader: BitmapShader? = null
    private var imageShaderBitmap: Bitmap? = null
    private var imageShaderWidth = -1
    private var imageShaderHeight = -1
    private val imageShaderMatrix = Matrix()
    private val imagePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
    )
    private var imageTintFilter: ColorFilter? = null
    private var imageColorFilter: ColorFilter? = null

    var imageTintList: ColorStateList? = null
        set(value) {
            field = value
            imageTintFilter = value?.let {
                PorterDuffColorFilter(
                    it.getColorForState(drawableState, it.defaultColor),
                    PorterDuff.Mode.SRC_IN
                )
            }
            refreshImageColorFilter()
        }

    var imageAlpha: Int = 255
        set(value) {
            field = value.coerceIn(0, 255)
            imagePaint.alpha = field
            imageDrawable?.alpha = field
            invalidate()
        }

    var scaleType: ScaleType = ScaleType.FIT_CENTER
        set(value) {
            field = value
            imageShaderWidth = -1
            imageShaderHeight = -1
            invalidate()
        }

    val drawable: Drawable?
        get() = imageDrawable ?: imageBitmap?.let { BitmapDrawable(resources, it) }

    companion object {
        private const val TEXT_SIZE_MULTIPLIER = 1.2f
        private const val DEFAULT_TEXT_SIZE_DP = 14
        private const val COVER_SQUIRCLE_CORNER_RADIUS_DP = 3.5f
        const val VIEW_TAG: String = "lyricon:logo_view"
        const val TAG = "SuperLogo"
    }

    var coverFile: File? = null

    var isOplusCapsuleShowing: Boolean = false
        set(value) {
            field = value
            updateVisibility()
        }

    var activePackage: String? = null

    init {
        this.tag = VIEW_TAG
    }

    /**
     * 重置进度条状态并取消相关动画。
     */
    fun clearProgress() {
        progressAnimator?.cancel()
        progressAnimator = null
        this.progress = 0f
        invalidate()
    }

    /**
     * 同步当前播放进度，并在封面模式下启动进度条补间动画。
     */
    fun syncProgress(current: Long, duration: Long) {
        progressAnimator?.cancel()
        if (duration <= 0) return

        // 仅在圆形封面模式下显示进度条
        if (strategy !is CoverStrategy || (strategy as CoverStrategy).style != LogoStyle.STYLE_COVER_CIRCLE) {
            return
        }

        val startProgress = current.toFloat() / duration
        this.progress = startProgress
        invalidate()

        if (current < duration) {
            progressAnimator = ValueAnimator.ofFloat(startProgress, 1f).apply {
                this.duration = duration - current
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    progress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (strategy is CoverStrategy && selfDrawnCoverBitmap != null) {
            drawSelfDrawnCover(canvas)
        } else {
            drawImageContent(canvas)
        }
        // 仅在进度有效且不为 0 或 1 时绘制，避免视觉干扰
        if (strategy is CoverStrategy && progress > 0f && progress < 1f) {
            drawProgress(canvas)
        }
    }

    fun setImageBitmap(bitmap: Bitmap?) {
        if (imageBitmap === bitmap && imageDrawable == null && selfDrawnCoverBitmap == null) return

        imageDrawable?.callback = null
        imageDrawable = null
        imageBitmap = bitmap
        imageShader = null
        imageShaderBitmap = null
        imageShaderWidth = -1
        imageShaderHeight = -1
        clearSelfDrawnCover()
        invalidate()
    }

    fun setImageDrawable(drawable: Drawable?) {
        if (imageDrawable === drawable && imageBitmap == null && selfDrawnCoverBitmap == null) return

        imageDrawable?.callback = null
        imageBitmap = null
        imageShader = null
        imageShaderBitmap = null
        imageShaderWidth = -1
        imageShaderHeight = -1
        imageDrawable = drawable?.mutate()?.also {
            it.callback = this
            it.alpha = imageAlpha
            it.state = drawableState
            it.colorFilter = effectiveImageColorFilter()
        }
        clearSelfDrawnCover()
        invalidate()
    }

    fun setColorFilter(colorFilter: ColorFilter?) {
        if (imageColorFilter == colorFilter) return

        imageColorFilter = colorFilter
        refreshImageColorFilter()
    }

    fun clearColorFilter() {
        setColorFilter(null)
    }

    private fun refreshImageColorFilter() {
        val colorFilter = effectiveImageColorFilter()
        imagePaint.colorFilter = colorFilter
        imageDrawable?.colorFilter = colorFilter
        invalidate()
    }

    private fun effectiveImageColorFilter(): ColorFilter? = imageColorFilter ?: imageTintFilter

    private fun drawImageContent(canvas: Canvas) {
        val bitmap = imageBitmap
        if (bitmap != null && !bitmap.isRecycled && width > 0 && height > 0) {
            imagePaint.alpha = imageAlpha
            imagePaint.colorFilter = effectiveImageColorFilter()
            imagePaint.shader = getOrCreateImageShader(bitmap)
            canvas.drawRect(imageRect, imagePaint)
            imagePaint.shader = null
            return
        }

        imageDrawable?.let { drawable ->
            if (width <= 0 || height <= 0) return

            val saveCount = canvas.save()
            drawable.bounds = resolveDrawableBounds(drawable)
            drawable.alpha = imageAlpha
            drawable.state = drawableState
            drawable.colorFilter = effectiveImageColorFilter()
            drawable.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }

    private fun getOrCreateImageShader(bitmap: Bitmap): BitmapShader {
        if (imageShader == null || imageShaderBitmap !== bitmap) {
            imageShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            imageShaderBitmap = bitmap
            imageShaderWidth = -1
            imageShaderHeight = -1
        }

        if (imageShaderWidth != width || imageShaderHeight != height) {
            configureImageShaderMatrix(bitmap)
            imageShader?.setLocalMatrix(imageShaderMatrix)
            imageShaderWidth = width
            imageShaderHeight = height
        }

        return imageShader!!
    }

    private fun configureImageShaderMatrix(bitmap: Bitmap) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bitmapWidth = bitmap.width.toFloat().coerceAtLeast(1f)
        val bitmapHeight = bitmap.height.toFloat().coerceAtLeast(1f)

        val scaleX: Float
        val scaleY: Float
        var dx = 0f
        var dy = 0f

        when (scaleType) {
            ScaleType.FIT_XY -> {
                scaleX = viewWidth / bitmapWidth
                scaleY = viewHeight / bitmapHeight
                imageRect.set(0f, 0f, viewWidth, viewHeight)
            }

            ScaleType.CENTER_CROP -> {
                val scale = maxOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
                scaleX = scale
                scaleY = scale
                dx = (viewWidth - bitmapWidth * scale) * 0.5f
                dy = (viewHeight - bitmapHeight * scale) * 0.5f
                imageRect.set(0f, 0f, viewWidth, viewHeight)
            }

            ScaleType.CENTER -> {
                scaleX = 1f
                scaleY = 1f
                dx = (viewWidth - bitmapWidth) * 0.5f
                dy = (viewHeight - bitmapHeight) * 0.5f
                imageRect.set(dx, dy, dx + bitmapWidth, dy + bitmapHeight)
            }

            ScaleType.CENTER_INSIDE -> {
                val scale = minOf(1f, minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight))
                scaleX = scale
                scaleY = scale
                val scaledWidth = bitmapWidth * scale
                val scaledHeight = bitmapHeight * scale
                dx = (viewWidth - scaledWidth) * 0.5f
                dy = (viewHeight - scaledHeight) * 0.5f
                imageRect.set(dx, dy, dx + scaledWidth, dy + scaledHeight)
            }

            ScaleType.FIT_START,
            ScaleType.FIT_CENTER,
            ScaleType.FIT_END,
            ScaleType.MATRIX -> {
                val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
                scaleX = scale
                scaleY = scale
                val scaledWidth = bitmapWidth * scale
                val scaledHeight = bitmapHeight * scale
                dx = when (scaleType) {
                    ScaleType.FIT_END -> viewWidth - scaledWidth
                    ScaleType.FIT_START -> 0f
                    else -> (viewWidth - scaledWidth) * 0.5f
                }
                dy = when (scaleType) {
                    ScaleType.FIT_END -> viewHeight - scaledHeight
                    ScaleType.FIT_START -> 0f
                    else -> (viewHeight - scaledHeight) * 0.5f
                }
                imageRect.set(dx, dy, dx + scaledWidth, dy + scaledHeight)
            }
        }

        imageShaderMatrix.reset()
        imageShaderMatrix.setScale(scaleX, scaleY)
        imageShaderMatrix.postTranslate(dx, dy)
    }

    private fun resolveDrawableBounds(drawable: Drawable): Rect {
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: width
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: height
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        when (scaleType) {
            ScaleType.FIT_XY -> imageRect.set(0f, 0f, viewWidth, viewHeight)
            ScaleType.CENTER_CROP -> {
                val scale = maxOf(
                    viewWidth / intrinsicWidth.toFloat(),
                    viewHeight / intrinsicHeight.toFloat()
                )
                val scaledWidth = intrinsicWidth * scale
                val scaledHeight = intrinsicHeight * scale
                val left = (viewWidth - scaledWidth) * 0.5f
                val top = (viewHeight - scaledHeight) * 0.5f
                imageRect.set(left, top, left + scaledWidth, top + scaledHeight)
            }

            ScaleType.CENTER -> {
                val left = (viewWidth - intrinsicWidth) * 0.5f
                val top = (viewHeight - intrinsicHeight) * 0.5f
                imageRect.set(left, top, left + intrinsicWidth, top + intrinsicHeight)
            }

            ScaleType.CENTER_INSIDE -> {
                val scale = minOf(
                    1f,
                    minOf(
                        viewWidth / intrinsicWidth.toFloat(),
                        viewHeight / intrinsicHeight.toFloat()
                    )
                )
                val scaledWidth = intrinsicWidth * scale
                val scaledHeight = intrinsicHeight * scale
                val left = (viewWidth - scaledWidth) * 0.5f
                val top = (viewHeight - scaledHeight) * 0.5f
                imageRect.set(left, top, left + scaledWidth, top + scaledHeight)
            }

            ScaleType.FIT_START,
            ScaleType.FIT_CENTER,
            ScaleType.FIT_END,
            ScaleType.MATRIX -> {
                val scale = minOf(
                    viewWidth / intrinsicWidth.toFloat(),
                    viewHeight / intrinsicHeight.toFloat()
                )
                val scaledWidth = intrinsicWidth * scale
                val scaledHeight = intrinsicHeight * scale
                val left = when (scaleType) {
                    ScaleType.FIT_END -> viewWidth - scaledWidth
                    ScaleType.FIT_START -> 0f
                    else -> (viewWidth - scaledWidth) * 0.5f
                }
                val top = when (scaleType) {
                    ScaleType.FIT_END -> viewHeight - scaledHeight
                    ScaleType.FIT_START -> 0f
                    else -> (viewHeight - scaledHeight) * 0.5f
                }
                imageRect.set(left, top, left + scaledWidth, top + scaledHeight)
            }
        }

        return Rect(
            imageRect.left.roundToInt(),
            imageRect.top.roundToInt(),
            imageRect.right.roundToInt(),
            imageRect.bottom.roundToInt()
        )
    }

    private fun drawSelfDrawnCover(canvas: Canvas) {
        val bitmap = selfDrawnCoverBitmap ?: return
        if (bitmap.isRecycled || width <= 0 || height <= 0) return

        coverRect.set(0f, 0f, width.toFloat(), height.toFloat())
        coverPaint.shader = getOrCreateCoverShader(bitmap)

        when ((strategy as? CoverStrategy)?.style) {
            LogoStyle.STYLE_COVER_CIRCLE -> canvas.drawOval(coverRect, coverPaint)
            LogoStyle.STYLE_COVER_SQUIRCLE -> canvas.drawRoundRect(
                coverRect,
                COVER_SQUIRCLE_CORNER_RADIUS_DP.dp.toFloat(),
                COVER_SQUIRCLE_CORNER_RADIUS_DP.dp.toFloat(),
                coverPaint
            )

            else -> canvas.drawRect(coverRect, coverPaint)
        }

        coverPaint.shader = null
    }

    private fun getOrCreateCoverShader(bitmap: Bitmap): BitmapShader {
        if (coverShader == null || coverShaderBitmap !== bitmap) {
            coverShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            coverShaderBitmap = bitmap
            coverShaderWidth = -1
            coverShaderHeight = -1
        }

        if (coverShaderWidth != width || coverShaderHeight != height) {
            val scale = maxOf(
                width.toFloat() / bitmap.width.toFloat(),
                height.toFloat() / bitmap.height.toFloat()
            )
            val dx = (width - bitmap.width * scale) * 0.5f
            val dy = (height - bitmap.height * scale) * 0.5f
            coverShaderMatrix.reset()
            coverShaderMatrix.setScale(scale, scale)
            coverShaderMatrix.postTranslate(dx, dy)
            coverShader?.setLocalMatrix(coverShaderMatrix)
            coverShaderWidth = width
            coverShaderHeight = height
        }

        return coverShader!!
    }

    private fun drawProgress(canvas: Canvas) {
        val strokeWidth = 2.dp.toFloat()
        val padding = strokeWidth / 2

        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = currentStatusColor.color.firstOrNull() ?: Color.TRANSPARENT

        progressRect.set(padding, padding, width - padding, height - padding)
        canvas.drawArc(progressRect, -90f, 360f * progress, false, progressPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 恢复策略状态（如重新开始动画）
        strategy?.onAttach()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 暂停策略活动（如停止动画、释放临时资源）
        strategy?.onDetach()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        coverShaderWidth = -1
        coverShaderHeight = -1
        imageShaderWidth = -1
        imageShaderHeight = -1
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        imageTintFilter = imageTintList?.let {
            PorterDuffColorFilter(
                it.getColorForState(drawableState, it.defaultColor),
                PorterDuff.Mode.SRC_IN
            )
        }
        imageDrawable?.state = drawableState
        refreshImageColorFilter()
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        imageDrawable?.jumpToCurrentState()
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who === imageDrawable || super.verifyDrawable(who)
    }

    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        if (strategy?.isEffective == true) {
            strategy?.onColorUpdate()
        }
        invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        handleVisibilityChange(visibility)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        handleVisibilityChange(visibility)
    }

    private fun handleVisibilityChange(visibility: Int) {
        val visible = visibility == VISIBLE && isShown
        strategy?.onVisibilityChanged(visible)

        if (!visible) {
            progressAnimator?.cancel()
        }
    }

    // region Public API

    /**
     * 应用新的样式配置，触发布局参数更新及策略重新评估。
     */
    fun applyStyle(style: LyricStyle) {
        this.lyricStyle = style
        val logoConfig = style.packageStyle.logo

        updateLayoutParams(style, logoConfig)
        reassessStrategy()
    }

    // region Internal Logic

    /**
     * 清除 View 上由先前策略设置的特定属性，防止样式残留。
     * 包括：旋转角度、OutlineProvider、ColorFilter (Tint)。
     */
    private fun resetViewAttributes() {
        this.rotation = 0f
        this.outlineProvider = null
        this.clipToOutline = false
        clearSelfDrawnCover()
        resetImageVisualState()
        this.scaleType = ScaleType.FIT_CENTER // 默认缩放模式
    }

    internal fun resetImageVisualState() {
        this.imageTintList = null
        this.clearColorFilter()
        this.imageAlpha = 255
        this.alpha = 1f
    }

    internal val hasSelfDrawnCover: Boolean
        get() = selfDrawnCoverBitmap != null

    internal fun setSelfDrawnCover(bitmap: Bitmap?) {
        if (selfDrawnCoverBitmap === bitmap) return
        selfDrawnCoverBitmap = bitmap
        coverShader = null
        coverShaderBitmap = null
        invalidate()
    }

    internal fun setSelfDrawnCoverColorFilter(colorFilter: ColorFilter?) {
        if (coverPaint.colorFilter == colorFilter) return
        coverPaint.colorFilter = colorFilter
        invalidate()
    }

    fun describeRenderState(): String {
        val coverBitmap = selfDrawnCoverBitmap
        val bitmap = imageBitmap
        return "view=${javaClass.name} " +
                "strategy=${strategy?.javaClass?.simpleName} " +
                "size=${width}x${height} " +
                "cover=${coverBitmap?.width}x${coverBitmap?.height} " +
                "coverRecycled=${coverBitmap?.isRecycled} " +
                "coverFilter=${coverPaint.colorFilter != null} " +
                "imageBitmap=${bitmap?.width}x${bitmap?.height} " +
                "imageDrawable=${imageDrawable?.javaClass?.name} " +
                "imageTint=${imageTintList != null} " +
                "imageFilter=${imageColorFilter != null} " +
                "alpha=$alpha imageAlpha=$imageAlpha " +
                "layerType=$layerType"
    }

    private fun clearSelfDrawnCover() {
        selfDrawnCoverBitmap = null
        coverShader = null
        coverShaderBitmap = null
        coverPaint.colorFilter = null
        invalidate()
    }

    private fun reassessStrategy() {
        val logoConfig = lyricStyle?.packageStyle?.logo ?: return

        val newStrategy = when (logoConfig.style) {
            LogoStyle.STYLE_COVER_SQUIRCLE,
            LogoStyle.STYLE_COVER_CIRCLE -> CoverStrategy(this)

            LogoStyle.STYLE_PROVIDER_LOGO ->
                if (providerLogo == null) null else ProviderStrategy(this)

            LogoStyle.STYLE_APP_LOGO -> AppLogoStrategy(this)
            LogoStyle.STYLE_LOGO_CUSTOM -> CustomLogoStrategy(this)
            else -> null
        }

        // 如果策略类型发生变化，执行完整的切换流程
        if (strategy?.javaClass != newStrategy?.javaClass) {
            strategy?.onDetach() // 让旧策略清理资源
            resetViewAttributes() // 彻底重置 View 属性

            strategy = newStrategy

            // 如果 View 已经 attach，立即触发新策略的 attach
            if (isAttachedToWindow) {
                newStrategy?.onAttach()
            }
            // 初始渲染
            newStrategy?.updateContent()
        } else {
            // 策略未变，仅更新内容
            strategy?.updateContent()
        }

        updateVisibility()
    }

    fun updateVisibility() {
        val logoConfig = lyricStyle?.packageStyle?.logo
        val isEnabled = logoConfig?.enable == true
        val isEffective = strategy?.isEffective == true
        val isHideInCapsule =
            logoConfig?.hideInColorOSCapsuleMode == true && isOplusCapsuleShowing

        this.isVisibleIfChanged = !isHideInCapsule && isEnabled && isEffective && !forceHide
    }

    private fun updateLayoutParams(style: LyricStyle, logoStyle: LogoStyle) {
        val defaultSize = calculateDefaultSize(style)
        val width = if (logoStyle.width <= 0) defaultSize else logoStyle.width.dp
        val height = if (logoStyle.height <= 0) defaultSize else logoStyle.height.dp

        val params = (layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(
            width,
            height
        )
        params.width = width
        params.height = height
        applyMargins(params, logoStyle.margins)

        layoutParams = params
    }

    private fun applyMargins(
        params: LinearLayout.LayoutParams,
        margins: io.github.proify.lyricon.lyric.style.RectF
    ) {
        params.leftMargin = margins.left.dp
        params.topMargin = margins.top.dp
        params.rightMargin = margins.right.dp
        params.bottomMargin = margins.bottom.dp
    }

    private fun calculateDefaultSize(style: LyricStyle): Int {
        val configuredSize = style.packageStyle.text.textSize
        return when {
            configuredSize > 0 -> configuredSize.dp
            linkedTextView != null -> {
                (linkedTextView!!.textSize * TEXT_SIZE_MULTIPLIER).roundToInt()
            }

            else -> DEFAULT_TEXT_SIZE_DP.dp
        }
    }

}
