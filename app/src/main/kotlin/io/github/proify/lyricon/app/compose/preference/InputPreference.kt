/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("AssignedValueIsNeverRead")

package io.github.proify.lyricon.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.android.extensions.formatToString
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.NumberTextField
import io.github.proify.lyricon.app.compose.color
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperDialog
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.TimeFormatter
import io.github.proify.lyricon.app.util.editCommit
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class InputType {
    STRING,
    INTEGER,
    DOUBLE
}

@Composable
fun InputPreference(
    modifier: Modifier = Modifier,
    sharedPreferences: SharedPreferences,
    key: String,
    title: String,
    defaultValue: String? = null,
    syncKeys: Array<String> = emptyArray(),
    showKeyboard: Boolean = true,
    inputType: InputType = InputType.STRING,
    minValue: Double = 0.0,
    maxValue: Double = 0.0,
    autoHoldDownState: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    leftAction: @Composable (() -> Unit)? = null,
    rightActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    isTimeUnit: Boolean = false,
    formatMultiplier: Int = 1,
    label: String? = null,
) {
    val fixDefaultValue = if (inputType == InputType.INTEGER && defaultValue?.contains(".") == true)
        defaultValue.substringBefore(".")
    else defaultValue

    val prefValueState = rememberStringPreference(sharedPreferences, key, fixDefaultValue)
    val currentSummary = summary ?: prefValueState.value
    ?: (fixDefaultValue ?: stringResource(id = R.string.defaultText))

    var showDialog by remember { mutableStateOf(false) }

    val finalSummary = when {
        isTimeUnit && inputType == InputType.INTEGER -> {
            val value = currentSummary.toLongOrNull()
            if (value != null) TimeFormatter.formatTime(
                value * formatMultiplier,
                AppLangUtils.getLocale()
            ) else currentSummary
        }

        else -> currentSummary
    }

    SuperArrow(
        title = title,
        titleColor = titleColor,
        summary = finalSummary,
        summaryColor = summaryColor,
        startAction = leftAction,
        endActions = rightActions,
        modifier = modifier,
        insideMargin = insideMargin,
        onClick = {
            onClick?.invoke()
            showDialog = true
        },
        holdDownState = holdDownState || (autoHoldDownState && showDialog),
        enabled = enabled,
    )

    if (showDialog) {
        InputPreferenceDialog(
            title = title,
            initialValue = prefValueState.value ?: "",
            inputType = inputType,
            minValue = minValue,
            maxValue = maxValue,
            showKeyboard = showKeyboard,
            onDismiss = { showDialog = false },
            label = label,
            onSave = { newValue ->
                showDialog = false
                sharedPreferences.editCommit {
                    if (newValue.isEmpty()) {
                        remove(key)
                        prefValueState.value = null

                        for (syncKey in syncKeys) {
                            remove(syncKey)
                        }
                    } else {
                        val value = when (inputType) {
                            else -> newValue
                        }

                        putString(key, value)
                        for (syncKey in syncKeys) {
                            putString(syncKey, value)
                        }
                    }
                }
            }
        )
    }
}

/**
 * 输入弹窗组件
 */
@Composable
private fun InputPreferenceDialog(
    title: String,
    initialValue: String,
    inputType: InputType,
    minValue: Double,
    maxValue: Double,
    showKeyboard: Boolean,
    onDismiss: () -> Unit,
    label: String? = null,
    onSave: (String) -> Unit
) {
    var inputValue by remember { mutableStateOf(initialValue) }

    // STRING 类型专用 TextFieldValue（必须持久保存 selection）
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isNumberInput = inputType != InputType.STRING
    val hasRangeLimit = isNumberInput && maxValue > minValue

    // 验证输入是否有效
    val isValidInput = remember(inputValue, inputType, minValue, maxValue) {
        validateInput(inputValue, inputType, minValue, maxValue)
    }

    // 范围提示文本
    val hintText = remember(inputValue, hasRangeLimit) {
        if (hasRangeLimit) {
            val currentVal = inputValue.toDoubleOrNull() ?: 0.0
            "${currentVal.formatToString()} (${minValue.formatToString()}-${maxValue.formatToString()})"
        } else {
            ""
        }
    }

    fun dismiss() {
        onDismiss()
        keyboardController?.hide()
    }

    // 处理键盘和焦点
    LaunchedEffect(Unit) {
        if (showKeyboard) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    SuperDialog(
        title = title,
        show = remember { mutableStateOf(true) },
        onDismissRequest = { dismiss() }
    ) {
        Column(modifier = Modifier.imePadding()) {

            when (inputType) {

                InputType.STRING -> {
                    TextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            inputValue = it.text
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text
                        ),
                        modifier = Modifier.focusRequester(focusRequester),
                        label = label.orEmpty()
                    )
                }

                InputType.INTEGER -> {
                    NumberTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        allowDecimal = false,
                        allowNegative = minValue < 0,
                        modifier = Modifier.focusRequester(focusRequester),
                        autoSelectOnFocus = true,
                        borderColor = if (isValidInput) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.error
                        },
                        label = label.orEmpty()
                    )
                }

                InputType.DOUBLE -> {
                    NumberTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        allowDecimal = true,
                        allowNegative = minValue < 0,
                        modifier = Modifier.focusRequester(focusRequester),
                        autoSelectOnFocus = true,
                        borderColor = if (isValidInput) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.error
                        },
                        label = label.orEmpty()
                    )
                }
            }

            if (hintText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = hintText,
                        fontSize = 13.sp,
                        color = if (isValidInput) {
                            BasicComponentDefaults.summaryColor().color(true)
                        } else {
                            MiuixTheme.colorScheme.error
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = { dismiss() },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(20.dp))

                TextButton(
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    text = stringResource(id = R.string.action_save),
                    onClick = {
                        val finalValue = formatFinalValue(inputValue, inputType)
                        onSave(finalValue)
                        keyboardController?.hide()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isValidInput
                )
            }
        }
    }
}


/**
 * 验证输入值是否有效
 */
private fun validateInput(
    text: String,
    inputType: InputType,
    min: Double,
    max: Double
): Boolean {
    if (text.isEmpty()) return true

    return when (inputType) {
        InputType.STRING -> true
        InputType.INTEGER -> {
            val num = text.toDoubleOrNull()
            num != null && (max <= min || num in min..max)
        }

        InputType.DOUBLE -> {
            val num = text.toDoubleOrNull()
            num != null && (max <= min || num in min..max)
        }
    }
}

/**
 * 格式化最终保存的值
 */
private fun formatFinalValue(text: String, inputType: InputType): String {
    if (text.isEmpty() || inputType == InputType.STRING) {
        return text
    }

    // 格式化数字，去除多余的0和小数点
    return text.toDoubleOrNull()?.formatToString() ?: text
}