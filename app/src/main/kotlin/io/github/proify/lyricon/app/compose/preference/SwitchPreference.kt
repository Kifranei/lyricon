/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.preference

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperSwitch
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.SwitchColors
import top.yukonga.miuix.kmp.basic.SwitchDefaults

@Composable
fun SwitchPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    title: String,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    switchColors: SwitchColors = SwitchDefaults.switchColors(),
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val checked = rememberBooleanPreference(sharedPreferences, key, defaultValue)
    //val hapticFeedback = LocalHapticFeedback.current

    SuperSwitch(
        checked = checked.value,
        onCheckedChange = {
            checked.value = it
            onCheckedChange.invoke(it)
        },
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = endActions,
        bottomAction = bottomAction,
        switchColors = switchColors,
        modifier = modifier,
        insideMargin = insideMargin,
        holdDownState = holdDownState,
        enabled = enabled
    )
}