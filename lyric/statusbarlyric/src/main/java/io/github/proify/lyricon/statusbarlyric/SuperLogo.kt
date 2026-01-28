/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.toBitmap
import io.github.proify.android.extensions.visibilityIfChanged
import io.github.proify.lyricon.common.util.SVGHelper
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.RectF
import io.github.proify.lyricon.lyric.view.util.Interpolates
import io.github.proify.lyricon.provider.ProviderLogo
import java.io.File
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * 用于显示歌词来源图标、APP图标或专辑封面的视图组件。
 * 负责处理图标样式的动态切换、进度绘制以及状态栏颜色适配。
 */
@SuppressLint("AppCompatCustomView")
class SuperLogo(context: Context) : ImageView(context) {

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

    private var currentStatusColor: StatusColor = StatusColor(Color.BLACK, false, Color.TRANSPARENT)
    private var lyricStyle: LyricStyle? = null

    // --- 进度条绘制属性 ---
    private var progress: Float = 0f
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 255
    }
    private val progressRect = android.graphics.RectF()
    private var progressAnimator: ValueAnimator? = null

    companion object {
        private const val DEFAULT_ROTATION_DURATION_MS = 12_000L
        private const val TEXT_SIZE_MULTIPLIER = 1.2f
        private const val DEFAULT_TEXT_SIZE_DP = 14
        private const val SQUIRCLE_CORNER_RADIUS_DP = 4
        const val VIEW_TAG: String = "lyricon:logo_view"
    }

    var coverFile: File? = null
    var oplusCapsuleShowing: Boolean = false
        set(value) {
            field = value
            updateVisibilityState()
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
                interpolator = Interpolates.linear
                addUpdateListener { animator ->
                    progress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 仅在进度有效且不为 0 或 1 时绘制，避免视觉干扰
        if (strategy is CoverStrategy && progress > 0f && progress < 1f) {
            drawProgress(canvas)
        }
    }

    private fun drawProgress(canvas: Canvas) {
        val strokeWidth = 2.dp.toFloat()
        val padding = strokeWidth / 2

        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = currentStatusColor.color

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
        this.imageTintList = null
        this.scaleType = ScaleType.FIT_CENTER // 默认缩放模式
    }

    private fun reassessStrategy() {
        val logoConfig = lyricStyle?.packageStyle?.logo ?: return

        val newStrategy = when (logoConfig.style) {
            LogoStyle.STYLE_COVER_SQUIRCLE,
            LogoStyle.STYLE_COVER_CIRCLE -> CoverStrategy()

            LogoStyle.STYLE_PROVIDER_LOGO ->
                if (providerLogo == null) AppLogoStrategy() else ProviderStrategy()

            else -> AppLogoStrategy()
        }

        // 如果策略类型发生变化，执行完整的切换流程
        if (strategy?.javaClass != newStrategy.javaClass) {
            strategy?.onDetach() // 让旧策略清理资源
            resetViewAttributes() // 彻底重置 View 属性

            strategy = newStrategy

            // 如果 View 已经 attach，立即触发新策略的 attach
            if (isAttachedToWindow) {
                newStrategy.onAttach()
            }
            // 初始渲染
            newStrategy.updateContent()
        } else {
            // 策略未变，仅更新内容
            strategy?.updateContent()
        }

        updateVisibilityState()
    }

    internal fun updateVisibilityState() {
        val logoConfig = lyricStyle?.packageStyle?.logo
        val isEnabled = logoConfig?.enable == true
        val isEffective = strategy?.isEffective == true
        val isHideInCapsule =
            logoConfig?.hideInColorOSCapsuleMode == true && oplusCapsuleShowing

        this.visibilityIfChanged =
            if (!isHideInCapsule && isEnabled && isEffective) VISIBLE else GONE
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

    private fun applyMargins(params: LinearLayout.LayoutParams, margins: RectF) {
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

    // region Strategies

    /**
     * Logo 显示策略接口。
     * 定义了不同内容源（App图标、Provider图标、封面）的渲染和生命周期行为。
     */
    interface ILogoStrategy {
        val isEffective: Boolean

        /**
         * 加载或更新显示内容。
         * 在策略初始化、内容源变更或系统封面更新时调用。
         */
        fun updateContent()

        /**
         * 响应状态栏颜色变化。
         */
        fun onColorUpdate()

        /**
         * 当 View 附加到窗口时调用。
         * 用于恢复动画、注册特定监听器等。
         */
        fun onAttach()

        /**
         * 当 View 从窗口移除时调用。
         * 用于停止动画、清理重型资源。
         */
        fun onDetach()

        /**
         * 当 View 的可见性发生变化时调用。
         */
        fun onVisibilityChanged(visible: Boolean)
    }

    /**
     * 策略：显示歌词提供方（Provider）的 Logo。
     * 通常为单色 SVG 或 Bitmap，需要适配状态栏颜色。
     */
    inner class ProviderStrategy : ILogoStrategy {
        override var isEffective: Boolean = false
            private set

        private var cachedBitmap: Bitmap? = null
        private var lastProviderSignature: String? = null

        fun invalidateCache() {
            cachedBitmap = null
            lastProviderSignature = null
        }

        override fun updateContent() {
            // 提供者 Logo 通常无需裁剪 Outline
            if (outlineProvider != null) outlineProvider = null

            val bitmap = loadProviderBitmap()
            setImageBitmap(bitmap)
            isEffective = bitmap != null

            onColorUpdate() // 内容更新后立即应用颜色
            updateVisibilityState()
        }

        override fun onColorUpdate() {
            imageTintList = when {
                providerLogo?.colorful == true -> null
                else -> calculateTint()
            }
        }

        override fun onAttach() {
            // 如果在 detach 期间清理了 Bitmap，这里可以尝试重新加载
            if (drawable == null && isEffective) {
                updateContent()
            }
        }

        override fun onDetach() {
            // 释放 Bitmap 引用以减轻内存压力
            cachedBitmap = null
            lastProviderSignature = null
            setImageDrawable(null)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible && drawable == null) {
                updateContent()
            }
        }

        private fun loadProviderBitmap(): Bitmap? {
            val logo = providerLogo ?: return null
            val signature = "${logo.hashCode()}_${width}_${height}_${logo.type}"

            if (signature == lastProviderSignature && cachedBitmap != null) {
                return cachedBitmap
            }

            val bmp = when (logo.type) {
                ProviderLogo.TYPE_BITMAP -> logo.toBitmap()
                ProviderLogo.TYPE_SVG -> {
                    val svgString = logo.toSvg()
                    if (svgString.isNullOrBlank()) null
                    else SVGHelper.create(svgString)?.createBitmap(width, height)
                }

                else -> null
            }

            cachedBitmap = bmp
            lastProviderSignature = signature
            return bmp
        }

        private fun calculateTint(): ColorStateList {
            val logoStyle = lyricStyle?.packageStyle?.logo
                ?: return ColorStateList.valueOf(currentStatusColor.color)

            if (!logoStyle.enableCustomColor) {
                return ColorStateList.valueOf(currentStatusColor.color)
            }

            val logoColorConfig = logoStyle.color(currentStatusColor.lightMode)
            val finalColor = when {
                logoColorConfig.followTextColor -> resolveFollowTextColor()
                logoColorConfig.color != 0 -> logoColorConfig.color
                else -> currentStatusColor.color
            }

            return ColorStateList.valueOf(finalColor)
        }

        private fun resolveFollowTextColor(): Int {
            val textStyle = lyricStyle?.packageStyle?.text
            if (textStyle?.enableCustomTextColor != true) {
                return currentStatusColor.color
            }
            val textColorConfig = textStyle.color(currentStatusColor.lightMode)
            return if (textColorConfig != null && textColorConfig.normal != 0) {
                textColorConfig.normal
            } else {
                currentStatusColor.color
            }
        }
    }

    /**
     * 策略：显示专辑封面。
     * 支持圆形旋转（唱片模式）和圆角矩形，不跟随状态栏颜色。
     */
    inner class CoverStrategy : ILogoStrategy {
        private var rotationAnimator: ObjectAnimator? = null
        private var lastFileSignature: String? = null

        override var isEffective: Boolean = false
            private set

        var style: Int = LogoStyle.STYLE_COVER_CIRCLE

        override fun updateContent() {
            // 封面模式清除 Tint
            if (imageTintList != null) imageTintList = null

            val coverFile = coverFile
            if (coverFile == null || !coverFile.exists()) {
                setImageDrawable(null)
                isEffective = false
                lastFileSignature = null
            } else {
                val signature = coverFile.lastModified().toString()

                // 只有文件变动或未初始化时才重新加载
                if (signature != lastFileSignature || drawable == null) {
                    val bitmap: Bitmap? = coverFile.toBitmap(width, height)
                    setImageBitmap(bitmap)
                    lastFileSignature = signature
                    isEffective = bitmap != null
                }
            }

            // 始终应用 Outline 和 动画状态检查，以防 Style 变更
            applyStyleAndAnimation()
            updateVisibilityState()
        }

        private fun applyStyleAndAnimation() {
            val currentStyle = lyricStyle?.packageStyle?.logo?.style ?: LogoStyle.STYLE_COVER_CIRCLE
            val oldStyle = this.style
            this.style = currentStyle

            // 设置裁剪轮廓
            applyOutlineProvider(currentStyle)

            // 如果从圆形切换到其他样式，必须强制重置旋转角度
            if (oldStyle == LogoStyle.STYLE_COVER_CIRCLE && currentStyle != LogoStyle.STYLE_COVER_CIRCLE) {
                this@SuperLogo.rotation = 0f
            }

            // 检查动画状态
            checkAnimationState()
        }

        override fun onColorUpdate() {
            // 封面保持原色，不应用 Tint
        }

        override fun onAttach() {
            // 恢复视图状态
            updateContent()
            checkAnimationState()
        }

        override fun onDetach() {
            stopAnimation()
            // 可以在此释放图片，下次 onAttach 时会通过 updateContent 重新加载
            // setImageDrawable(null)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) checkAnimationState() else stopAnimation()
        }

        private fun applyOutlineProvider(style: Int) {
            val provider = when (style) {
                LogoStyle.STYLE_COVER_CIRCLE -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }

                LogoStyle.STYLE_COVER_SQUIRCLE -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(
                            0,
                            0,
                            view.width,
                            view.height,
                            SQUIRCLE_CORNER_RADIUS_DP.dp.toFloat()
                        )
                    }
                }

                else -> null
            }
            outlineProvider = provider
            clipToOutline = provider != null
        }

        private fun checkAnimationState() {
            if (isAttachedToWindow && isShown && isEffective && style == LogoStyle.STYLE_COVER_CIRCLE) {
                startAnimation()
            } else {
                stopAnimation()
            }
        }

        private fun startAnimation() {
            if (rotationAnimator?.isRunning == true) return

            rotationAnimator =
                ObjectAnimator.ofFloat(this@SuperLogo, "rotation", rotation, rotation + 360f)
                    .apply {
                        duration = DEFAULT_ROTATION_DURATION_MS
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.RESTART
                        interpolator = LinearInterpolator()
                        start()
                    }
        }

        private fun stopAnimation() {
            rotationAnimator?.cancel()
            rotationAnimator = null
            // 注意：这里不重置 rotation 为 0，以便暂停后恢复时视觉连贯。
            // 彻底重置由 LyricLogoView.resetViewAttributes() 在切换策略时处理。
        }
    }

    /**
     * 策略：显示当前活跃 App 的图标。
     * 作为兜底策略，不应用特殊颜色或动画。
     */
    inner class AppLogoStrategy : ILogoStrategy {
        // 使用弱引用缓存防止 Context 泄漏
        private val cacheIcons = WeakHashMap<String, Drawable>()

        override var isEffective: Boolean = false
            private set

        override fun updateContent() {
            if (imageTintList != null) imageTintList = null
            if (outlineProvider != null) outlineProvider = null

            val activePackage = activePackage
            val icon = if (activePackage.isNullOrBlank()) null else getIcon(activePackage)

            setImageDrawable(icon)
            isEffective = icon != null
            updateVisibilityState()
        }

        private fun getIcon(packageName: String): Drawable? {
            if (packageName.isBlank()) return null

            cacheIcons[packageName]?.let { return it }

            return try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                cacheIcons[packageName] = icon
                icon
            } catch (_: Exception) {
                null
            }
        }

        override fun onColorUpdate() {
            // App 图标保持原色
        }

        override fun onAttach() {
            if (drawable == null) updateContent()
        }

        override fun onDetach() {
            // App 图标通常较小且由系统缓存管理，可不主动清理，或根据内存策略清理
        }

        override fun onVisibilityChanged(visible: Boolean) {}
    }
}