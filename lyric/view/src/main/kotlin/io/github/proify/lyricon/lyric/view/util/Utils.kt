/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view.util

import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import kotlin.math.roundToInt

fun ViewGroup.getChildAtOrNull(index: Int): View? =
    if (childCount > index) getChildAt(index) else null

var View.visibilityIfChanged: Int
    get() = visibility
    set(value) {
        if (visibility != value) visibility = value
    }

internal fun View.hide() {
    if (visibility != View.GONE) visibility = View.GONE
}

internal fun View.show() {
    if (visibility != View.VISIBLE) visibility = View.VISIBLE
}

internal inline var View.visible: Boolean
    get() = isVisible
    set(value) {
        val newVisibility = if (value) View.VISIBLE else View.GONE
        if (visibility != newVisibility) visibility = newVisibility
    }

internal inline val Int.dp: Int
    get() = toFloat().dp.roundToInt()

internal inline val Float.dp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )

internal inline val Float.sp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this,
        Resources.getSystem().displayMetrics
    )