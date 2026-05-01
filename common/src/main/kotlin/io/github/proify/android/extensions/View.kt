/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout.LayoutParams

fun View.ensureMarginLayoutParams(): MarginLayoutParams {
    val lp = layoutParams as? MarginLayoutParams
        ?: MarginLayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )
    if (layoutParams == null) layoutParams = lp
    return lp
}