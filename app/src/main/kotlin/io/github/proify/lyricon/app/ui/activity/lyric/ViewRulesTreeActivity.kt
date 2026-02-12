/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import io.github.proify.lyricon.app.LyriconApp.Companion.systemUIChannel
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.Node
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperCheckbox
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import io.github.proify.lyricon.app.updateRemoteLyricStyle
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.common.util.ViewTreeNode
import io.github.proify.lyricon.lyric.style.VisibilityRule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ViewRulesTreeActivity : ViewTreeActivity() {
    internal val viewModel: RuleViewModel by viewModels()

    override fun onDestroy() {
        super.onDestroy()
        clearNormalRules()
    }

    private fun clearNormalRules() {
        LyricPrefs.setViewVisibilityRule(
            LyricPrefs.getViewVisibilityRule().filterNot { it.mode == VisibilityRule.MODE_NORMAL }
        )
    }

    override fun getToolBarTitle(): String = getString(R.string.activity_view_rules)

    override fun resetSettings() {
        LyricPrefs.setViewVisibilityRule(null)
        refreshTreeDisplay()
    }

    override fun createViewModel(): ViewTreeViewModel = @SuppressLint("StaticFieldLeak")
    object : ViewTreeViewModel() {
        override fun handleNodeClick(node: Node<ViewTreeNode>) {
            val nodeId = node.content.id ?: return
            viewModel.openSelection(nodeId)
        }

        override fun getNodeColor(node: ViewTreeNode): Color {
            val rules = LyricPrefs.getViewVisibilityRule()
            val hasActiveRule =
                rules.any { it.id == node.id && it.mode != VisibilityRule.MODE_NORMAL }
            return if (hasActiveRule) if (AppThemeUtils.isEnableMonet(application)) CurrentThemeConfigs.primaryContainer else MaterialPalette.Green.Primary
            else Color.Transparent
        }
    }

    private fun highlightView(id: String) {
        systemUIChannel.put(AppBridgeConstants.REQUEST_HIGHLIGHT_VIEW, id)
    }

    @Composable
    override fun OnScaffoldCreated() {
        LaunchedEffect(viewModel.showOptions.value) {
            highlightView(if (viewModel.showOptions.value) viewModel.editId.value else "")
        }

        val currentMode by viewModel.currentMode

        VisibilityRuleBottomSheet(
            show = viewModel.showOptions,
            nodeId = viewModel.editId.value,
            selectedMode = currentMode,
            onModeSelected = { newMode ->
                viewModel.updateRule(newMode)
                refreshTreeDisplay()
            }
        )
    }

    class RuleViewModel : ViewModel() {
        val showOptions: MutableState<Boolean> = mutableStateOf(false)
        val editId: MutableState<String> = mutableStateOf("")

        private val _currentMode = mutableIntStateOf(VisibilityRule.MODE_NORMAL)
        val currentMode: State<Int> = _currentMode

        fun openSelection(id: String) {
            editId.value = id
            val rules = LyricPrefs.getViewVisibilityRule()
            _currentMode.intValue = rules.find { it.id == id }?.mode ?: VisibilityRule.MODE_NORMAL
            showOptions.value = true
        }

        fun updateRule(newMode: Int) {
            val rules = LyricPrefs.getViewVisibilityRule().toMutableList()
            val existingIndex = rules.indexOfFirst { it.id == editId.value }

            if (existingIndex != -1) {
                rules[existingIndex] = VisibilityRule(id = editId.value, mode = newMode)
            } else {
                rules.add(VisibilityRule(id = editId.value, mode = newMode))
            }

            LyricPrefs.setViewVisibilityRule(rules)
            updateRemoteLyricStyle()
            _currentMode.intValue = newMode
            showOptions.value = false
        }
    }

    @Composable
    internal fun VisibilityRuleBottomSheet(
        show: MutableState<Boolean>,
        nodeId: String,
        selectedMode: Int,
        onModeSelected: (Int) -> Unit
    ) {
        val options = remember {
            listOf(
                VisibilityOption(VisibilityRule.MODE_NORMAL, R.string.option_visibility_default),
                VisibilityOption(
                    VisibilityRule.MODE_HIDE_WHEN_PLAYING,
                    R.string.option_visibility_hide_when_playing
                )
            )
        }

        val context = LocalContext.current

        SuperBottomSheet(
            backgroundColor = MiuixTheme.colorScheme.surface,
            title = nodeId,
            show = show,
            onDismissRequest = { show.value = false },
            insideMargin = DpSize(16.dp, 0.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    VisibilityOptionItem(option, selectedMode, onModeSelected, context)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun VisibilityOptionItem(
        option: VisibilityOption,
        selectedMode: Int,
        onModeSelected: (Int) -> Unit,
        context: Context
    ) {
        SuperCheckbox(
            title = stringResource(option.titleRes),
            checked = selectedMode == option.mode,
            onCheckedChange = { isChecked ->
                if (isChecked && selectedMode != option.mode) {
                    onModeSelected(option.mode)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        (context as? Activity)?.window?.decorView?.performHapticFeedback(
                            HapticFeedbackConstants.TOGGLE_ON
                        )
                    } else {
                        (context as? Activity)?.window?.decorView?.performHapticFeedback(
                            HapticFeedbackConstants.CONTEXT_CLICK
                        )
                    }
                }
            }
        )
    }

    private data class VisibilityOption(
        val mode: Int,
        val titleRes: Int
    )
}