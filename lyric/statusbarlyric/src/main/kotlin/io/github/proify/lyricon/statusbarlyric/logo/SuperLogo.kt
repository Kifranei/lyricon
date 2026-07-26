/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
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

    var currentStatusColor: StatusColor = StatusColor()
    var lyricStyle: LyricStyle? = null

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

    companion object {
        private const val TEXT_SIZE_MULTIPLIER = 1.2f
        private const val DEFAULT_TEXT_SIZE_DP = 14
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