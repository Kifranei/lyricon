/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.contains
import androidx.core.view.isNotEmpty
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX

@SuppressLint("ViewConstructor")
class StatusBarLyric(
    context: Context,
    initialStyle: LyricStyle,
    linkedTextView: TextView?
) : LinearLayout(context) {

    companion object {
        const val VIEW_TAG: String = "lyricon:lyric_view"
    }

    val logoView: SuperLogo = SuperLogo(context).apply {
        this.linkedTextView = linkedTextView
    }

    val textView: SuperText = SuperText(context).apply {
        this.linkedTextView = linkedTextView
        eventListener = object : SuperText.EventListener {
            override fun enteringInterludeMode(duration: Long) {
                logoView.syncProgress(0, duration)
            }

            override fun exitInterludeMode() {
                logoView.clearProgress()
            }
        }
    }

    var currentStyle: LyricStyle = initialStyle
        private set

    var currentStatusColor: StatusColor = StatusColor(Color.BLACK, false, Color.GRAY)
        private set

    private var isPlaying: Boolean = false

    private val textViewOnHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            updateVisibility()
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {
            updateVisibility()
        }
    }

    /**
     * 启用单次过渡变化动画，随后禁用
     */
    private fun enableTransitionChangeType() {
        myLayoutTransition.enableTransitionType(LayoutTransition.CHANGING)
    }

    var oplusCapsuleShowing: Boolean = false

    fun setOplusCapsuleVisibility(isShowing: Boolean) {
        oplusCapsuleShowing = isShowing
        enableTransitionChangeType()
        updateWidth(currentStyle)
        logoView.oplusCapsuleShowing = isShowing
    }

    val myLayoutTransition: LayoutTransition = LayoutTransitionX().apply {
        setAnimateParentHierarchy(true)
        enableTransitionType(LayoutTransition.CHANGING)

//        addTransitionListener(object : LayoutTransition.TransitionListener {
//            override fun endTransition(
//                transition: LayoutTransition?,
//                container: ViewGroup?,
//                view: View?,
//                transitionType: Int
//            ) {
//                //禁用
//                //disableTransitionType(LayoutTransition.CHANGING)
//            }
//
//            override fun startTransition(
//                transition: LayoutTransition?,
//                container: ViewGroup?,
//                view: View?,
//                transitionType: Int
//            ) {
//            }
//        })
    }

    var sleepMode: Boolean = false
        set(value) {
            //YLog.debug("休眠模式：$value")
            field = value
            if (value) {
                dataDuringSleepMode = DataDuringSleepMode()
            } else {
                dataDuringSleepMode?.let { updatePosition(it.position) }
                dataDuringSleepMode = null
            }
        }
    private var dataDuringSleepMode: DataDuringSleepMode? = null

    private class DataDuringSleepMode(
        var position: Long = 0,
    )

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        addView(
            textView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
        updateLogoLocation()
        visibility = GONE
        layoutTransition = myLayoutTransition

        applyStyle(initialStyle)
        textView.setOnHierarchyChangeListener(textViewOnHierarchyChangeListener)
    }

    private var lastLogoGravity: Int = -114
    private fun updateLogoLocation() {
        val logoStyle = currentStyle.packageStyle.logo
        val logoGravity = logoStyle.gravity
        if (logoGravity == lastLogoGravity) return
        lastLogoGravity = logoGravity

        if (contains(logoView)) removeView(logoView)
        val textIndex = indexOfChild(textView).coerceAtLeast(0)

        when (logoGravity) {
            LogoStyle.GRAVITY_START -> addView(logoView, textIndex)
            LogoStyle.GRAVITY_END -> addView(logoView, textIndex + 1)
            else -> addView(logoView, textIndex)
        }
    }

    fun updateStyle(style: LyricStyle) {
        currentStyle = style

        logoView.applyStyle(style)
        updateLogoLocation()

        textView.applyStyle(style)
        updateLayoutParams(style)
        invalidate()
        requestLayout()
    }

    private fun applyStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        updateLayoutParams(style)
    }

    private fun updateWidth(style: LyricStyle) {
        getMarginLayoutParams().apply {
            width = getWidth(style.basicStyle).dp
        }
        requestLayout()
    }

    private fun getMarginLayoutParams(): MarginLayoutParams {
        var lp = (layoutParams as? MarginLayoutParams)
        if (lp == null) {
            lp = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            layoutParams = lp
        }
        return lp
    }

    private fun getWidth(basicStyle: BasicStyle) =
        if (oplusCapsuleShowing) basicStyle.widthInColorOSCapsuleMode else basicStyle.width

    private fun updateLayoutParams(style: LyricStyle) {
        val basicStyle = style.basicStyle
        val margins = basicStyle.margins
        val paddings = basicStyle.paddings

        val params = getMarginLayoutParams()

        params.apply {
            width = getWidth(basicStyle).dp
            leftMargin = margins.left.dp
            topMargin = margins.top.dp
            rightMargin = margins.right.dp
            bottomMargin = margins.bottom.dp
        }

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
        requestLayout()
    }

    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        logoView.setStatusBarColor(color)
        textView.setStatusBarColor(color)
    }

    fun setPlaying(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        updateVisibility()
    }

    fun updateVisibility(temporarilyProhibitTransitions: Boolean = false) {
        val hideOnLockScreen = currentStyle.basicStyle.hideOnLockScreen && isKeyguardLocked()

        val curVisibility = visibility
        val newVisibility =
            if (isPlaying && !hideOnLockScreen && textView.isNotEmpty()) VISIBLE else GONE

        if (curVisibility != newVisibility) {
            if (temporarilyProhibitTransitions) layoutTransition = null
            visibility = newVisibility
            post { if (layoutTransition == null) layoutTransition = myLayoutTransition }
        }
    }

    fun seekTo(position: Long) {
        textView.seekTo(position)
    }

    fun updatePosition(position: Long) {
        if (sleepMode) {
            dataDuringSleepMode?.position = position
            return
        }
        textView.setPosition(position)
    }

    fun setSong(song: Song?) {
        textView.song = song
    }

    fun updateText(text: String?) {
        textView.text = text
    }

    fun setDisplayTranslation(isDisplayTranslation: Boolean) {
        textView.setDisplayTranslation(isDisplayTranslation)
    }

    private val keyguardManager by lazy { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private fun isKeyguardLocked() = keyguardManager.isKeyguardLocked
}