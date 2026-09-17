/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app.activity

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.libxposed.service.XposedService
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.LyriconApp.Companion.addXposedServiceStateListener
import io.github.proify.lyricon.app.LyriconApp.Companion.removeXposedServiceStateListener
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.LocalBottomBarBackdrop
import io.github.proify.lyricon.app.compose.LocalFloatingBottomBarEnabled
import io.github.proify.lyricon.app.compose.MainBottomBar
import io.github.proify.lyricon.app.compose.MainNavigationRail
import io.github.proify.lyricon.app.compose.shouldShowNavigationRail
import io.github.proify.lyricon.app.compose.MainBottomBarItem
import io.github.proify.lyricon.app.compose.OpaqueDropdownPopupTheme
import io.github.proify.lyricon.app.compose.theme.AppTheme
import io.github.proify.lyricon.app.ui.tabs.ConfigPage
import io.github.proify.lyricon.app.ui.tabs.HomeTab
import io.github.proify.lyricon.app.ui.tabs.SettingsPage
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
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : BaseActivity(), LyriconApp.XposedServiceStateListener {

    private companion object {
        const val PREF_KEY_LAST_VERSION = "last_version"
        private const val TAG = "MainActivity"
        private const val RESTART_DEBOUNCE_MS = 666L
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleVersionUpdate()

        setContent {
            MainContent(
                model = viewModel,
                onRestartSystemUI = ::restartSystemUI,
                onRestartApp = ::restartApp
            )
        }
        setupEventListeners()

        addXposedServiceStateListener(this)
        viewModel.startConnectTimeout()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeXposedServiceStateListener(this)
    }

    private fun handleVersionUpdate() {
        val sharedPreferences = defaultSharedPreferences
        val savedVersionCode = sharedPreferences.getLong(PREF_KEY_LAST_VERSION, 0)
        if (savedVersionCode <= 0) {
            sharedPreferences.edit {
                putLong(PREF_KEY_LAST_VERSION, BuildConfig.VERSION_CODE.toLong())
            }
        } else if (savedVersionCode < BuildConfig.VERSION_CODE) {
            viewModel.setWaitingForReboot(true)
        }
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
                delay(RESTART_DEBOUNCE_MS)
                viewModel.setWaitingForReboot(false)
            }
        }
        val result = Utils.killSystemUI()
        if (result.result == -1) {
            viewModel.showRestartFailedDialog.value = true
        }
    }

    private fun saveCurrentVersionCode() {
        defaultSharedPreferences.editCommit {
            putLong(PREF_KEY_LAST_VERSION, LyriconApp.versionCode)
        }
    }

    override fun onServiceStateChanged(service: XposedService?) {
        viewModel.isModuleActive.value = service != null
    }

    /**
     * 界面状态管理层
     */
    class MainViewModel : ViewModel() {
        private val _safeMode = mutableStateOf(false)
        val showRestartFailedDialog: MutableState<Boolean> = mutableStateOf(false)
        private val _isWaitingForReboot = mutableStateOf(false)

        val safeMode: State<Boolean> get() = _safeMode
        val isWaitingForReboot: State<Boolean> get() = _isWaitingForReboot

        val showRestartMenu: MutableState<Boolean> = mutableStateOf(false)
        val isModuleActive: MutableState<Boolean> = mutableStateOf(false)
        var isServiceConnecting = mutableStateOf(false)

        private val handler = Handler(Looper.getMainLooper())
        fun startConnectTimeout() {
            isServiceConnecting.value = true
            handler.postDelayed({
                isServiceConnecting.value = false
            }, 2000)
        }

        fun updateSafeMode(isSafe: Boolean) {
            _safeMode.value = isSafe
            LyriconApp.setSafeMode(isSafe)
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
        val fallbackShowRestartMenu = remember { mutableStateOf(false) }
        val showRestartMenuState = model?.showRestartMenu ?: fallbackShowRestartMenu

        AdaptiveMainContent(
            model = model,
            showRestartMenuState = showRestartMenuState,
            onRestartSystemUI = onRestartSystemUI,
            onRestartApp = onRestartApp
        )
    }

    @Composable
    private fun AdaptiveMainContent(
        model: MainViewModel?,
        showRestartMenuState: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        val context = LocalContext.current
        val sharedPreferences = remember { context.defaultSharedPreferences }
        var selectedIndex by rememberSaveable("home_config_settings") { mutableIntStateOf(0) }
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

        val viewModel = model ?: remember { MainViewModel() }
        val useRail = shouldShowNavigationRail()
        val showFloatingBar = isFloating && !useRail
        val items = listOf(
            MainBottomBarItem(
                stringResource(R.string.tab_home),
                MiuixIcons.Home
            ),
            MainBottomBarItem(
                stringResource(R.string.tab_config),
                ImageVector.vectorResource(id = R.drawable.ic_palette_swatch_variant)
            ),
            MainBottomBarItem(
                stringResource(R.string.tab_settings),
                ImageVector.vectorResource(id = R.drawable.ic_settings)
            ),
        )
        val bottomBarContent: @Composable () -> Unit = {
            MainBottomBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it }
            )
        }

        // 浮动液态玻璃底栏需要采样页面内容，所以在页面外层录制 backdrop 并以覆盖层绘制；
        // 停靠底栏则交给页面 Scaffold 正常占位，避免列表尾部被遮挡。
        val overlayBackdrop = rememberLayerBackdrop()
        val pageBottomBar: @Composable () -> Unit = if (useRail || showFloatingBar) ({}) else bottomBarContent

        CompositionLocalProvider(
            LocalFloatingBottomBarEnabled provides showFloatingBar,
            LocalBottomBarBackdrop provides overlayBackdrop,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        AppTheme {
                            MainNavigationRail(
                                items = items,
                                selectedIndex = selectedIndex,
                                onSelected = { selectedIndex = it },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .layerBackdrop(overlayBackdrop)
                    ) {
                        when (selectedIndex) {
                            0 -> HomeTab(
                                model = viewModel,
                                actions = {
                                    TopBarActions(showRestartMenuState, onRestartSystemUI, onRestartApp)
                                },
                                bottomBar = pageBottomBar
                            )

                            1 -> ConfigPage(bottomBar = pageBottomBar)
                            2 -> SettingsPage(bottomBar = pageBottomBar)
                        }
                    }
                }

                if (showFloatingBar) {
                    // 覆盖层在各页面的 AppTheme 之外，需要自己套主题，否则暗色下取到亮色配色
                    AppTheme {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            bottomBarContent()
                        }
                    }
                }

                AppTheme {
                    RestartFailedDialog(showState = viewModel.showRestartFailedDialog)
                }
            }
        }
    }

    @Composable
    private fun RestartFailedDialog(showState: MutableState<Boolean>) {
        OverlayDialog(
            title = stringResource(R.string.restart_fail),
            summary = stringResource(R.string.message_app_restart_fail),
            show = showState.value,
            onDismissRequest = { showState.value = false }
        ) {
            TextButton(
                text = stringResource(R.string.ok),
                onClick = { showState.value = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun TopBarActions(
        showRestartMenu: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        Box(modifier = Modifier.padding(end = 14.dp)) {
            IconButton(onClick = { showRestartMenu.value = true }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(id = R.string.action_restart),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            RestartMenuPopup(
                showRestartMenu = showRestartMenu,
                onRestartSystemUI = onRestartSystemUI,
                onRestartApp = onRestartApp
            )
        }
    }

    @Composable
    private fun RestartMenuPopup(
        showRestartMenu: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        val items = listOf(
            stringResource(R.string.restart_system_ui),
            stringResource(R.string.restart_app)
        )

        OverlayListPopup(
            show = showRestartMenu.value,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            enableWindowDim = true,
            onDismissRequest = { showRestartMenu.value = false },
            minWidth = 200.dp,
            content = {
                OpaqueDropdownPopupTheme {
                    ListPopupColumn {
                        items.forEachIndexed { index, string ->
                            DropdownImpl(
                                text = string,
                                optionSize = items.size,
                                isSelected = false,
                                onSelectedIndexChange = {
                                    if (index == 0) onRestartSystemUI() else onRestartApp()
                                    showRestartMenu.value = false
                                },
                                index = index
                            )
                        }
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
