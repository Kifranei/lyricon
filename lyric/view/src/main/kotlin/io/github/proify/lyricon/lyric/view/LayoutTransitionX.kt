/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.animation.LayoutTransition
import android.animation.TimeInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

class LayoutTransitionX(
    config: String? = TRANSITION_CONFIG_SMOOTH
) : LayoutTransition() {

    init {
        applyConfig(config)
    }

    private fun applyConfig(config: String?) {
        val normalized = config?.trim().takeUnless { it.isNullOrEmpty() } ?: TRANSITION_CONFIG_SMOOTH

        when (normalized) {
            TRANSITION_CONFIG_NONE -> {
                disableTransitionType(CHANGING)
                disableTransitionType(APPEARING)
                disableTransitionType(DISAPPEARING)
                disableTransitionType(CHANGE_APPEARING)
                disableTransitionType(CHANGE_DISAPPEARING)
            }

            TRANSITION_CONFIG_FAST -> {
                configureChanging(durationMs = 100L, interpolator = AccelerateInterpolator())
            }

            TRANSITION_CONFIG_SLOW -> {
                configureChanging(durationMs = 280L, interpolator = DecelerateInterpolator())
            }

            else -> {
                configureChanging(
                    durationMs = 180L,
                    interpolator = AccelerateDecelerateInterpolator()
                )
            }
        }
    }

    private fun configureChanging(durationMs: Long, interpolator: TimeInterpolator) {
        enableTransitionType(CHANGING)
        setStartDelay(CHANGING, 0L)
        setDuration(CHANGING, durationMs)
        setInterpolator(CHANGING, interpolator)
    }

    companion object {
        const val TRANSITION_CONFIG_SMOOTH = "smooth"
        const val TRANSITION_CONFIG_FAST = "fast"
        const val TRANSITION_CONFIG_SLOW = "slow"
        const val TRANSITION_CONFIG_NONE = "none"
    }
}
