/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    allowDecimal: Boolean = false,
    allowNegative: Boolean = true,
    maxValue: Double? = null,
    minValue: Double? = null,
    autoSelectOnFocus: Boolean = false,
    borderColor: Color = MiuixTheme.colorScheme.primary,
) {
    // 初始 TextFieldValue 基于外部 value 构造（selection 放在末尾）
    val initialTf = remember(value) {
        TextFieldValue(text = value, selection = TextRange(value.length))
    }

    // 使用 rememberSaveable 保存 TextFieldValue（避免进程重启丢失）
    var textFieldValueState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(initialTf)
    }

    // 是否已经执行过“初始把光标放末尾”的动作
    var initialSelectionDone by remember { mutableStateOf(false) }

    // 聚焦状态
    var isFocused by remember { mutableStateOf(false) }
    var shouldSelectAll by remember { mutableStateOf(false) }

    // 同步外部 value：
    // - 初次组合或外部 value 变化且不在聚焦时，将文本替换并把光标放末尾（避免打断用户输入）
    LaunchedEffect(value) {
        if (textFieldValueState.text != value) {
            if (!isFocused) {
                textFieldValueState =
                    TextFieldValue(text = value, selection = TextRange(value.length))
            } else {
                // 如果正在聚焦并编辑，尽量只更新文本而保留现有 selection（防止光标跳动）
                val sel = textFieldValueState.selection
                val clamped = sel.end.coerceIn(0, value.length)
                textFieldValueState = TextFieldValue(text = value, selection = TextRange(clamped))
            }
        } else if (!initialSelectionDone) {
            // 初次组合且文本相同，确保初始 selection 已置于末尾（防止某些实现把 selection 置为 0）
            textFieldValueState = textFieldValueState.copy(selection = TextRange(value.length))
        }
        initialSelectionDone = true
    }

    // 全选控制（获取焦点时触发）
    LaunchedEffect(shouldSelectAll) {
        if (shouldSelectAll && textFieldValueState.text.isNotEmpty()) {
            textFieldValueState = textFieldValueState.copy(
                selection = TextRange(0, textFieldValueState.text.length)
            )
        }
        shouldSelectAll = false
    }

    Column(modifier = modifier) {
        TextField(
            borderColor = borderColor,
            label = label,
            value = textFieldValueState,
            onValueChange = { newValue ->
                // 过滤输入，只允许数字相关字符
                val filtered = filterNumericInput(
                    input = newValue.text,
                    allowDecimal = allowDecimal,
                    allowNegative = allowNegative
                )

                val isValid = validateRange(filtered, minValue, maxValue)

                // 保留合理的 selection：尝试基于 newValue.selection.end，裁剪到 filtered 长度范围内
                val rawSel = newValue.selection.end
                val clampedSel = rawSel.coerceIn(0, filtered.length)

                if (isValid || filtered.isEmpty() || filtered == "-" || filtered.endsWith(".")) {
                    textFieldValueState = TextFieldValue(
                        text = filtered,
                        selection = TextRange(clampedSel)
                    )
                    onValueChange(filtered)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isFocused && autoSelectOnFocus) {
                        shouldSelectAll = true
                    }
                    isFocused = focusState.isFocused
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            singleLine = true
        )
    }
}

/**
 * 过滤输入，只保留有效数字字符
 */
private fun filterNumericInput(
    input: String,
    allowDecimal: Boolean,
    allowNegative: Boolean
): String {
    if (input.isEmpty()) return input

    var result = input.filter { char ->
        char.isDigit() ||
                (char == '.' && allowDecimal) ||
                (char == '-' && allowNegative)
    }

    if (allowNegative) {
        val hasLeadingNegative = result.startsWith('-')
        result = result.replace("-", "")
        if (hasLeadingNegative) {
            result = "-$result"
        }
    }

    if (allowDecimal) {
        val firstDot = result.indexOf('.')
        if (firstDot != -1) {
            result = result.take(firstDot + 1) +
                    result.substring(firstDot + 1).replace(".", "")
        }
    }

    return result
}

/**
 * 校验数值范围
 */
private fun validateRange(
    value: String,
    minValue: Double?,
    maxValue: Double?
): Boolean {
    if (
        value.isEmpty() ||
        value == "-" ||
        value.endsWith(".") ||
        value == "-."
    ) {
        return true
    }

    val number = value.toDoubleOrNull() ?: return false

    if (minValue != null && number < minValue) return false
    if (maxValue != null && number > maxValue) return false

    return true
}