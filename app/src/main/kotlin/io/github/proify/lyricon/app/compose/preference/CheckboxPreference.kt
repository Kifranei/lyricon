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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperCheckbox
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.CheckboxColors
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.extra.CheckboxLocation

@Composable
fun CheckboxPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    checkboxColors: CheckboxColors = CheckboxDefaults.checkboxColors(),
    startActions: @Composable () -> Unit = {},
    endActions: @Composable RowScope.() -> Unit = {},
    checkboxLocation: CheckboxLocation = CheckboxLocation.Start,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val checked = rememberBooleanPreference(sharedPreferences, key, defaultValue)

    val hapticFeedback = LocalHapticFeedback.current

    SuperCheckbox(
        title = title,
        checked = checked.value,
        onCheckedChange = {
            hapticFeedback.performHapticFeedback(
                if (it) HapticFeedbackType.ToggleOn
                else HapticFeedbackType.ToggleOff
            )
            checked.value = it
            onCheckedChange?.invoke(it)
        },
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        checkboxColors = checkboxColors,
        startActions = startActions,
        endActions = endActions,
        checkboxLocation = checkboxLocation,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}