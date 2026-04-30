/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.BasicLyricStyleActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.PackageStyleActivity
import io.github.proify.lyricon.app.activity.lyric.provider.LyricProviderActivity
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.EmojiInfiniteQueuePlayer
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.custom.miuix.basic.AppBasicComponent
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
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

class MainActivity : BaseActivity() {

    private companion object {
        const val PREF_KEY_LAST_VERSION = "last_version"
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleVersionUpdateLogic()

        setContent {
            MainContent(
                model = viewModel,
                onRestartSystemUI = ::restartSystemUI,
                onRestartApp = ::restartApp
            )
        }

        setupEventListeners()
    }

    /**
     * 版本更新逻辑处理
     */
    private fun handleVersionUpdateLogic() {
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

    /**
     * 界面状态管理层
     */
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

    private interface CardStatus {
        val colors: CardColors
        val content: @Composable ColumnScope.() -> Unit
    }

    private class StatusCard(
        override val colors: CardColors,
        val icon: ImageVector,
        val title: String,
        val showAnimatedEmoji: Boolean = false,
        val summary: String? = null,
        val rightActions: @Composable (RowScope.() -> Unit)? = null
    ) : CardStatus {

        override val content: @Composable ColumnScope.() -> Unit = {
            AppBasicComponent(
                insideMargin = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
                endActions = rightActions,
                startAction = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            modifier = Modifier.size(26.dp),
                            imageVector = icon,
                            tint = White,
                            contentDescription = null,
                        )
                    }
                },
                customTitle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = White
                        )
                        if (showAnimatedEmoji) {
                            EmojiInfiniteQueuePlayer(
                                modifier = Modifier
                                    .size(19.dp)
                                    .padding(start = 1.dp)
                            )
                        }
                    }
                },
                titleColor = BasicComponentColors(color = White, disabledColor = White),
                summary = summary,
                summaryColor = BasicComponentColors(
                    color = Color(color = 0xAFFFFFFF),
                    disabledColor = White,
                )
            )
        }
    }

    @Composable
    fun MainContent(
        model: MainViewModel? = null,
        onRestartSystemUI: () -> Unit = {},
        onRestartApp: () -> Unit = {}
    ) {
        val fallbackShowPopup = remember { mutableStateOf(false) }
        val showPopupState = model?.showPopup ?: fallbackShowPopup

        AppToolBarListContainer(
            title = stringResource(R.string.app_name),
            actions = { TopBarActions(showPopupState, onRestartSystemUI, onRestartApp) },
            scaffoldContent = {
                if (model != null) RestartFailDialog(showState = model.showRestartFailDialog)
            }
        ) {
            item("status_card") {
                val cardStatus = determineCardStatus(
                    safeMode = model?.safeMode?.value ?: false,
                    isWaitingForReboot = model?.isWaitingForReboot?.value ?: false,
                    onRestartSystemUI = onRestartSystemUI
                )
                StatusCardItem(cardStatus)
            }

            item("style_settings") { StyleSettingsCard() }
            item("provider_settings") { ProviderSettingsCard() }
            item("other_settings") { OtherSettingsCard() }
        }
    }

    @Composable
    private fun determineCardStatus(
        safeMode: Boolean,
        isWaitingForReboot: Boolean,
        onRestartSystemUI: () -> Unit
    ): CardStatus {
        val inspectionMode = LocalInspectionMode.current
        val summary = stringResource(R.string.module_status_summary, BuildConfig.VERSION_NAME)

        if (inspectionMode) {
            return StatusCard(
                colors = CardColors(MaterialPalette.Green.Primary, White),
                icon = ImageVector.vectorResource(id = R.drawable.ic_android),
                title = "Preview mode"
            )
        }

        if (safeMode) {
            return StatusCard(
                colors = CardColors(MaterialPalette.Red.Hue400, White),
                icon = ImageVector.vectorResource(id = R.drawable.ic_sentiment_dissatisfied),
                title = stringResource(id = R.string.module_status_system_ui_safe_mode),
                summary = summary
            )
        }

        if (AppBridge.isModuleActive()) {
            if (isWaitingForReboot) {
                return StatusCard(
                    colors = CardColors(MaterialPalette.Orange.Primary, White),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_info_fill),
                    title = stringResource(id = R.string.module_status_waiting_for_reboot),
                    summary = summary,
                    rightActions = {
                        IconButton(onClick = onRestartSystemUI) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh),
                                contentDescription = stringResource(id = R.string.action_restart),
                                tint = White
                            )
                        }
                    }
                )
            }

            return StatusCard(
                colors = when {
                    viewModel.isMonet -> CardColors(
                        MiuixTheme.colorScheme.primary,
                        MiuixTheme.colorScheme.onPrimary
                    )

                    else -> CardColors(MaterialPalette.Green.Primary, White)
                },
                icon = ImageVector.vectorResource(id = R.drawable.ic_check_circle),
                title = stringResource(id = R.string.module_status_activated),
                summary = summary,
                showAnimatedEmoji = true
            )
        }

        return StatusCard(
            colors = CardColors(MaterialPalette.Red.Primary, White),
            icon = ImageVector.vectorResource(id = R.drawable.ic_sentiment_dissatisfied),
            title = stringResource(id = R.string.module_status_not_activated),
            summary = summary
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun StatusCardItem(cardStatus: CardStatus) {
        Card(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth(),
            insideMargin = PaddingValues(vertical = 7.dp),
            colors = cardStatus.colors,
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = {},
            content = cardStatus.content
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun StyleSettingsCard() {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            ArrowPreference(
                startAction = {
                    ColoredIconBox(Modifier, MaterialPalette.Teal.Primary, R.drawable.ic_android)
                },
                title = stringResource(id = R.string.item_base_lyric_style),
                summary = stringResource(id = R.string.item_summary_base_lyric_style),
                onClick = {
                    context.startActivity(Intent(context, BasicLyricStyleActivity::class.java))
                }
            )
            ArrowPreference(
                startAction = {
                    ColoredIconBox(
                        Modifier.padding(2.dp),
                        MaterialPalette.Orange.Primary,
                        R.drawable.ic_palette_swatch_variant
                    )
                },
                title = stringResource(id = R.string.item_package_style_manager),
                summary = stringResource(id = R.string.item_summary_package_style_manager),
                onClick = {
                    context.startActivity(Intent(context, PackageStyleActivity::class.java))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ProviderSettingsCard() {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            ArrowPreference(
                startAction = {
                    ColoredIconBox(Modifier, MaterialPalette.Blue.Primary, R.drawable.ic_extension)
                },
                title = stringResource(id = R.string.item_provider_manager),
                summary = stringResource(id = R.string.item_summary_provider_manager),
                onClick = {
                    context.startActivity(Intent(context, LyricProviderActivity::class.java))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun OtherSettingsCard() {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            ArrowPreference(
                startAction = {
                    ColoredIconBox(
                        Modifier,
                        MaterialPalette.BlueGrey.Primary,
                        R.drawable.ic_settings
                    )
                },
                title = stringResource(id = R.string.item_app_settings),
                summary = stringResource(id = R.string.item_summary_app_settings),
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            )

            ArrowPreference(
                startAction = {
                    ColoredIconBox(Modifier, MaterialPalette.Green.Primary, R.drawable.ic_info_fill)
                },
                title = stringResource(id = R.string.item_about_app),
                summary = stringResource(id = R.string.item_summary_about_app),
                onClick = {
                    context.startActivity(Intent(context, AboutActivity::class.java))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ColoredIconBox(
        modifier: Modifier = Modifier,
        backgroundColor: Color,
        iconRes: Int
    ) {
        val iconSize = if (viewModel.isMonet) 20.dp else 24.dp
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(40.dp)
                .let {
                    if (viewModel.isMonet) {
                        it.background(MiuixTheme.colorScheme.primary, CircleShape)
                    } else {
                        it.background(backgroundColor, CircleShape)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                modifier = modifier.size(iconSize),
                tint = White,
                contentDescription = null
            )
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
            TextButton(
                text = stringResource(R.string.ok),
                onClick = { showState.value = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun TopBarActions(
        showPopup: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        Box(modifier = Modifier.padding(end = 14.dp)) {
            IconButton(onClick = { showPopup.value = true }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(id = R.string.action_restart),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            RestartMenuPopup(
                showPopup = showPopup,
                onRestartSystemUI = onRestartSystemUI,
                onRestartApp = onRestartApp
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

        OverlayListPopup(
            show = showPopup.value,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            enableWindowDim = true,
            onDismissRequest = { showPopup.value = false },
            minWidth = 200.dp,
            content = {
                ListPopupColumn {
                    items.forEachIndexed { index, string ->
                        DropdownImpl(
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