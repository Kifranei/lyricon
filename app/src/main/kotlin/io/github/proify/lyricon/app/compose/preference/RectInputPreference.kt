/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.proify.android.extensions.formatToString
import io.github.proify.android.extensions.fromJsonOrNull
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.compose.RectFInputDialog
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.RectF
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

@Composable
fun RectInputPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: RectF = RectF(),
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    leftAction: @Composable (() -> Unit)? = null,
    rightActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true
) {

    val showDialog = remember { mutableStateOf(false) }
    val prefValueState = rememberStringPreference(sharedPreferences, key, null)

    val value = prefValueState.value
    val rectF = value?.fromJsonOrNull<RectF>() ?: defaultValue

    val currentSummary = summary
        ?: "${rectF.left.formatToString()}, ${rectF.top.formatToString()}, ${rectF.right.formatToString()}, ${rectF.bottom.formatToString()}"

    if (showDialog.value) {
        RectFInputDialog(
            initialLeft = rectF.left,
            initialTop = rectF.top,
            initialRight = rectF.right,
            initialBottom = rectF.bottom,
            show = showDialog,
            title = title,
            onConfirm = { left, top, right, bottom ->
                val rectF = RectF(left, top, right, bottom)
                sharedPreferences.editCommit {
                    putString(key, rectF.toJson())
                }
            }
        )
    }

    SuperArrow(
        title = title,
        titleColor = titleColor,
        summary = currentSummary,
        summaryColor = summaryColor,
        startAction = leftAction,
        endActions = rightActions,
        insideMargin = insideMargin,
        onClick = {
            showDialog.value = true
        },
        holdDownState = showDialog.value,
        enabled = enabled
    )
}