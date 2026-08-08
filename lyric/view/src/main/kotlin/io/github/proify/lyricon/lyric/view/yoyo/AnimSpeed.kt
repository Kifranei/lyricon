/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.yoyo

import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

enum class AnimSpeed(
    private val defaultOutInterpolatorA: () -> Interpolator,
    private val defaultInInterpolatorA: () -> Interpolator
) {

    Fast(
        defaultOutInterpolatorA = { FastOutLinearInInterpolator() },
        defaultInInterpolatorA = { FastOutSlowInInterpolator() }
    ),
    Normal(
        defaultOutInterpolatorA = { FastOutLinearInInterpolator() },
        defaultInInterpolatorA = { FastOutSlowInInterpolator() }
    ),
    Slow(
        defaultOutInterpolatorA = { FastOutLinearInInterpolator() },
        defaultInInterpolatorA = { FastOutSlowInInterpolator() }
    );

    val defaultOutInterpolator get() = defaultOutInterpolatorA.invoke()
    val defaultInInterpolator get() = defaultInInterpolatorA.invoke()

    companion object {
        fun fromPref(value: String?): AnimSpeed = when (value) {
            "fast" -> Fast
            "slow" -> Slow
            else -> Normal
        }
    }
}