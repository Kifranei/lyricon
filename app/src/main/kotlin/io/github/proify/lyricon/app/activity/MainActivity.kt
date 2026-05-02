/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.LocalFloatingBottomBarEnabled
import io.github.proify.lyricon.app.compose.MainBottomBar
import io.github.proify.lyricon.app.compose.MainBottomBarItem
import io.github.proify.lyricon.app.compose.custom.miuix.extra.OverlayDialog
import io.github.proify.lyricon.app.event.SettingChangedEvent
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.app.util.collectEvent
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.app.util.restartApp
import io.github.proify.lyricon.common.PackageNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : BaseActivity() {

    private companion object {
        const val PREF_KEY_LAST_VERSION = "last_version"
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = defaultSharedPreferences
        val savedVersionCode = sharedPreferences.getLong(PREF_KEY_LAST_VERSION, 0)
        if (savedVersionCode <= 0) {
            sharedPreferences.edit {
                putLong(PREF_KEY_LAST_VERSION, BuildConfig.VERSION_CODE.toLong())
            }
        } else if (savedVersionCode < BuildConfig.VERSION_CODE) {
            viewModel.setWaitingForReboot(true)
        }

        setContent {
            MainContent(
                model = viewModel,
                onRestartSystemUI = ::restartSystemUI,
                onRestartApp = ::restartApp
            )
        }

        setupEventListeners()
    }

    override fun onResume() {
        super.onResume()
        requestSafeModeCheck()
    }

    private fun setupEventListeners() {
        collectEvent<SettingChangedEvent>(state = Lifecycle.State.CREATED) {
            recreate()
        }
    }

    private fun requestSafeModeCheck() {
        lifecycleScope.launch {
            try {
                val response = LyriconBridge.with(this@MainActivity)
                    .to(PackageNames.SYSTEM_UI)
                    .key(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE)
                    .await()

                viewModel.updateSafeMode(response.getBoolean("result"))
            } catch (e: Exception) {
                Log.e(TAG, "IPC 调用失败: ${e.message}", e)
            }
        }
    }

    private fun restartSystemUI() {
        if (viewModel.isWaitingForReboot.value) {
            saveCurrentVersionCode()
            lifecycleScope.launch {
                delay(666)
                viewModel.setWaitingForReboot(false)
            }
        }
        val result = Utils.killSystemUI()
        if (result.result == -1) {
            viewModel.showRestartFailDialog.value = true
        }
    }

    private fun saveCurrentVersionCode() {
        defaultSharedPreferences.editCommit {
            putLong(PREF_KEY_LAST_VERSION, LyriconApp.versionCode)
        }
    }

    class MainViewModel : ViewModel() {
        private val _safeMode = mutableStateOf(false)
        val showRestartFailDialog: MutableState<Boolean> = mutableStateOf(false)
        private val _isWaitingForReboot = mutableStateOf(false)

        val safeMode: State<Boolean> get() = _safeMode
        val isWaitingForReboot: State<Boolean> get() = _isWaitingForReboot

        val showPopup: MutableState<Boolean> = mutableStateOf(false)
        fun updateSafeMode(isSafe: Boolean) {
            _safeMode.value = isSafe
            LyriconApp.updateSafeMode(isSafe)
        }

        fun setWaitingForReboot(waiting: Boolean) {
            _isWaitingForReboot.value = waiting
        }

        val isMonet: Boolean get() = AppThemeUtils.isEnableMonet(LyriconApp.get())
    }

    @Composable
    fun MainContent(
        model: MainViewModel? = null,
        onRestartSystemUI: () -> Unit = {},
        onRestartApp: () -> Unit = {}
    ) {
        val context = LocalContext.current
        val sharedPreferences = remember { context.defaultSharedPreferences }
        var selectedIndex by rememberSaveable { mutableStateOf(0) }
        var isFloating by remember {
            mutableStateOf(sharedPreferences.getBoolean("enable_floating_nav_bar", false))
        }
        DisposableEffect(sharedPreferences) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
                if (key == "enable_floating_nav_bar") {
                    isFloating = preferences.getBoolean(key, false)
                }
            }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        val isMonet = model?.isMonet == true

        val bottomBarContent: @Composable () -> Unit = {
            val items = listOf(
                MainBottomBarItem(stringResource(R.string.tab_home), ImageVector.vectorResource(id = R.drawable.ic_android)),
                MainBottomBarItem(stringResource(R.string.tab_config), ImageVector.vectorResource(id = R.drawable.ic_palette_swatch_variant)),
                MainBottomBarItem(stringResource(R.string.tab_provider), ImageVector.vectorResource(id = R.drawable.ic_extension)),
                MainBottomBarItem(stringResource(R.string.tab_settings), ImageVector.vectorResource(id = R.drawable.ic_settings))
            )

            MainBottomBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it }
            )
        }

        CompositionLocalProvider(
            LocalFloatingBottomBarEnabled provides isFloating
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedIndex) {
                    0 -> io.github.proify.lyricon.app.ui.tabs.HomeTab(
                        model = model!!,
                        actions = {
                            val showPopup = remember { mutableStateOf(false) }
                            Box(modifier = Modifier.padding(end = 14.dp)) {
                                IconButton(onClick = { showPopup.value = true }) {
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        imageVector = MiuixIcons.Refresh,
                                        contentDescription = stringResource(id = R.string.action_restart),
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                RestartMenuPopup(showPopup, onRestartSystemUI, onRestartApp)
                            }
                        },
                        bottomBar = {}
                    )
                    1 -> io.github.proify.lyricon.app.ui.tabs.ConfigPage(isMonet, bottomBar = {})
                    2 -> io.github.proify.lyricon.app.ui.tabs.ProviderPage(bottomBar = {})
                    3 -> io.github.proify.lyricon.app.ui.tabs.SettingsPage(bottomBar = {})
                }
                if (model?.showRestartFailDialog?.value == true) {
                    RestartFailDialog(model.showRestartFailDialog)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    bottomBarContent()
                }
            }
        }
    }

    @Composable
    private fun RestartFailDialog(showState: MutableState<Boolean>) {
        OverlayDialog(
            title = stringResource(R.string.restart_fail),
            summary = stringResource(R.string.message_app_restart_fail),
            show = showState.value,
            onDismissRequest = { showState.value = false }
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.ok),
                onClick = { showState.value = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun RestartMenuPopup(
        showPopup: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        val items = listOf(
            stringResource(R.string.restart_system_ui),
            stringResource(R.string.restart_app)
        )

        top.yukonga.miuix.kmp.overlay.OverlayListPopup(
            show = showPopup.value,
            popupModifier = Modifier,
            popupPositionProvider = top.yukonga.miuix.kmp.basic.ListPopupDefaults.DropdownPositionProvider,
            alignment = top.yukonga.miuix.kmp.basic.PopupPositionProvider.Align.TopEnd,
            enableWindowDim = true,
            onDismissRequest = { showPopup.value = false },
            maxHeight = null,
            minWidth = 200.dp,
            renderInRootScaffold = true,
            content = {
                top.yukonga.miuix.kmp.basic.ListPopupColumn {
                    items.forEachIndexed { index, string ->
                        top.yukonga.miuix.kmp.basic.DropdownImpl(
                            text = string,
                            optionSize = items.size,
                            isSelected = false,
                            onSelectedIndexChange = {
                                if (index == 0) onRestartSystemUI() else onRestartApp()
                                showPopup.value = false
                            },
                            index = index
                        )
                    }
                }
            })
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContentPreview() {
        MainContent()
    }
}
