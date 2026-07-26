/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.yoyo

import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.daimajia.androidanimations.library.Techniques
import io.github.proify.lyricon.lyric.view.yoyo.anim.MyTechniques

object YoYoPresets {

    private fun pair(
        outTech: Any,
        outDur: Long,
        outInterp: Interpolator,
        inTech: Any,
        inDur: Long,
        inInterp: Interpolator
    ): Pair<AnimConfig, AnimConfig> {
        @Suppress("CascadeIf")
        if (outTech is Techniques && inTech is Techniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        } else if (outTech is MyTechniques && inTech is MyTechniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        } else if (outTech is MyTechniques && inTech is Techniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        } else if (outTech is Techniques && inTech is MyTechniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        }
        throw IllegalArgumentException("Invalid animation type")
    }

    // region Fade Animations

    fun fadeOutFadeIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOut, 150L, speed.defaultOutInterpolator,
            Techniques.FadeIn, 150L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOut, 300L, speed.defaultOutInterpolator,
            Techniques.FadeIn, 300L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOut, 520L, speed.defaultOutInterpolator,
            Techniques.FadeIn, 520L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutLeftFadeInRight(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutLeft, 250L, speed.defaultOutInterpolator,
            Techniques.FadeInRight, 350L, OvershootInterpolator(2f)
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutLeft, 350L, speed.defaultOutInterpolator,
            Techniques.FadeInRight, 450L, OvershootInterpolator(2f)
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutLeft, 450L, speed.defaultOutInterpolator,
            Techniques.FadeInRight, 550L, OvershootInterpolator(2.5f)
        )
    }

    private fun fadeOutLeftFadeInUp(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutLeft, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutLeft, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutLeft, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 750L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutLeftZoomIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutLeft, 150L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 220L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutLeft, 300L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 400L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutLeft, 500L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 680L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutLeftLanding(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutLeft, 150L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 400L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutLeft, 300L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutLeft, 500L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 1200L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutRightFadeInLeft(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutRight, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInLeft, 250L, OvershootInterpolator(2.4f)
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutRight, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInLeft, 450L, OvershootInterpolator(1.6f)
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutRight, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInLeft, 750L, OvershootInterpolator(1.1f)
        )
    }

    private fun fadeOutRightFadeInUp(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutRight, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutRight, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutRight, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 750L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutRightZoomIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutRight, 120L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 220L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutRight, 200L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 400L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutRight, 350L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 680L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutRightLanding(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutRight, 150L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 400L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutRight, 300L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutRight, 500L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 1200L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutUpFadeInUp(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutUp, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutUp, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutUp, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 750L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutDownFadeInDown(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutDown, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInDown, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutDown, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInDown, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutDown, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInDown, 750L, speed.defaultInInterpolator
        )
    }

    // endregion

    // region Slide Animations

    private fun slideOutLeftSlideInRight(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutLeft, 150L, speed.defaultOutInterpolator,
            Techniques.SlideInRight, 250L, OvershootInterpolator(2.8f)
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutLeft, 300L, speed.defaultOutInterpolator,
            Techniques.SlideInRight, 450L, OvershootInterpolator(2.0f)
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutLeft, 500L, speed.defaultOutInterpolator,
            Techniques.SlideInRight, 750L, OvershootInterpolator(1.4f)
        )
    }

    private fun slideOutLeftFadeInUp(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutLeft, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutLeft, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutLeft, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 750L, speed.defaultInInterpolator
        )
    }

    private fun slideOutLeftZoomIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutLeft, 150L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutLeft, 300L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutLeft, 500L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 750L, speed.defaultInInterpolator
        )
    }

    private fun slideOutLeftLanding(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutLeft, 150L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 400L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutLeft, 300L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutLeft, 500L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 1200L, speed.defaultInInterpolator
        )
    }

    private fun slideOutRightSlideInLeft(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutRight, 150L, speed.defaultOutInterpolator,
            Techniques.SlideInLeft, 250L, OvershootInterpolator(2.0f)
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutRight, 300L, speed.defaultOutInterpolator,
            Techniques.SlideInLeft, 450L, OvershootInterpolator(1.5f)
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutRight, 500L, speed.defaultOutInterpolator,
            Techniques.SlideInLeft, 750L, OvershootInterpolator(1.0f)
        )
    }

    private fun slideOutRightFadeInUp(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutRight, 150L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutRight, 300L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutRight, 500L, speed.defaultOutInterpolator,
            Techniques.FadeInUp, 750L, speed.defaultInInterpolator
        )
    }

    private fun slideOutRightZoomIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutRight, 150L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutRight, 300L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutRight, 500L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 750L, speed.defaultInInterpolator
        )
    }

    private fun slideOutRightLanding(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.SlideOutRight, 150L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 400L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.SlideOutRight, 300L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.SlideOutRight, 500L, speed.defaultOutInterpolator,
            MyTechniques.LandingSoft, 1200L, speed.defaultInInterpolator
        )
    }

    // endregion

    // region Flip/Rotate/Zoom Animations

    private fun flipOutXFlipInX(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FlipOutX, 150L, speed.defaultOutInterpolator,
            Techniques.FlipInX, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FlipOutX, 300L, speed.defaultOutInterpolator,
            Techniques.FlipInX, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FlipOutX, 500L, speed.defaultOutInterpolator,
            Techniques.FlipInX, 750L, speed.defaultInInterpolator
        )
    }

    private fun flipOutYFlipInY(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FlipOutY, 150L, speed.defaultOutInterpolator,
            Techniques.FlipInY, 250L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FlipOutY, 300L, speed.defaultOutInterpolator,
            Techniques.FlipInY, 450L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FlipOutY, 500L, speed.defaultOutInterpolator,
            Techniques.FlipInY, 750L, speed.defaultInInterpolator
        )
    }

    private fun rotateOutRotateIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.RotateOut, 120L, speed.defaultOutInterpolator,
            Techniques.RotateIn, 350L, OvershootInterpolator(1.5f)
        )

        AnimSpeed.Normal -> pair(
            Techniques.RotateOut, 200L, speed.defaultOutInterpolator,
            Techniques.RotateIn, 600L, OvershootInterpolator(1.0f)
        )

        AnimSpeed.Slow -> pair(
            Techniques.RotateOut, 350L, speed.defaultOutInterpolator,
            Techniques.RotateIn, 1000L, OvershootInterpolator(0.8f)
        )
    }

    private fun zoomOutZoomIn(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.ZoomOut, 120L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 220L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.ZoomOut, 200L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 400L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.ZoomOut, 350L, speed.defaultOutInterpolator,
            Techniques.ZoomIn, 680L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutLeftZoomInRight(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutLeft, 130L, speed.defaultOutInterpolator,
            Techniques.ZoomInRight, 350L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutLeft, 250L, speed.defaultOutInterpolator,
            Techniques.ZoomInRight, 600L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutLeft, 420L, speed.defaultOutInterpolator,
            Techniques.ZoomInRight, 1000L, speed.defaultInInterpolator
        )
    }

    private fun fadeOutRightZoomInLeft(speed: AnimSpeed) = when (speed) {
        AnimSpeed.Fast -> pair(
            Techniques.FadeOutRight, 130L, speed.defaultOutInterpolator,
            Techniques.ZoomInLeft, 350L, speed.defaultInInterpolator
        )

        AnimSpeed.Normal -> pair(
            Techniques.FadeOutRight, 250L, speed.defaultOutInterpolator,
            Techniques.ZoomInLeft, 600L, FastOutSlowInInterpolator()
        )

        AnimSpeed.Slow -> pair(
            Techniques.FadeOutRight, 420L, speed.defaultOutInterpolator,
            Techniques.ZoomInLeft, 1000L, speed.defaultInInterpolator
        )
    }

    // endregion

    val registry: Map<String, (AnimSpeed) -> Pair<AnimConfig, AnimConfig>> by lazy {
        mapOf(
            "default" to ::fadeOutLeftFadeInRight,
            "fade_out_fade_in" to ::fadeOutFadeIn,
            "fade_out_up_fade_in_up" to ::fadeOutUpFadeInUp,
            "fade_out_down_fade_in_down" to ::fadeOutDownFadeInDown,
            "fade_out_left_fade_in_right" to ::fadeOutLeftFadeInRight,
            "fade_out_left_fade_in_up" to ::fadeOutLeftFadeInUp,
            "fade_out_left_zoom_in" to ::fadeOutLeftZoomIn,
            "fade_out_left_landing" to ::fadeOutLeftLanding,
            "fade_out_right_fade_in_left" to ::fadeOutRightFadeInLeft,
            "fade_out_right_fade_in_up" to ::fadeOutRightFadeInUp,
            "fade_out_right_zoom_in" to ::fadeOutRightZoomIn,
            "fade_out_right_landing" to ::fadeOutRightLanding,
            "slide_out_left_slide_in_right" to ::slideOutLeftSlideInRight,
            "slide_out_left_fade_in_up" to ::slideOutLeftFadeInUp,
            "slide_out_left_zoom_in" to ::slideOutLeftZoomIn,
            "slide_out_left_landing" to ::slideOutLeftLanding,
            "slide_out_right_slide_in_left" to ::slideOutRightSlideInLeft,
            "slide_out_right_fade_in_up" to ::slideOutRightFadeInUp,
            "slide_out_right_zoom_in" to ::slideOutRightZoomIn,
            "slide_out_right_landing" to ::slideOutRightLanding,
            "flip_out_x_flip_in_x" to ::flipOutXFlipInX,
            "flip_out_y_flip_in_y" to ::flipOutYFlipInY,
            "rotate_out_rotate_in" to ::rotateOutRotateIn,
            "zoom_out_zoom_in" to ::zoomOutZoomIn,
            "fade_out_left_zoom_in_right" to ::fadeOutLeftZoomInRight,
            "fade_out_right_zoom_in_left" to ::fadeOutRightZoomInLeft
        )
    }

    fun getById(id: String?, speed: AnimSpeed = AnimSpeed.Normal): Pair<AnimConfig, AnimConfig>? =
        registry[id]?.invoke(speed)
}
