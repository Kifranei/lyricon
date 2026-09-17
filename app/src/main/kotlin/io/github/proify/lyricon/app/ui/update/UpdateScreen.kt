/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.update

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppSmallTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 与关于页 / 主界面一致的平板判定阈值与内容宽度上限。 */
private const val TABLET_MIN_WIDTH_DP = 600
private val TABLET_MAX_CONTENT_WIDTH = 900.dp

private enum class UpdateScreenMode {
    /** 无更新 / 检查中 / 检查失败：居中大字。 */
    Centered,

    /** 无更新但展开了当前版本更新日志。 */
    CurrentLog,

    /** 发现新版本。 */
    NewUpdate,
}

/** 页面底色：与卡片拉开层次，不跟随流光背景。 */
@Composable
private fun updatePageBackground(): Color {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
}

/**
 * 软件更新页（移植自 Halcyon）。
 *
 * 三种形态之间 Crossfade 切换，底部常驻一颗动作按钮，
 * 依次承担「检查更新 / 下载（带进度）/ 安装 / 重试」。
 */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = updatePageBackground()

    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Loading) }
    var showCurrentLog by remember { mutableStateOf(false) }

    val checkFailedText = stringResource(R.string.update_check_failed)

    fun checkUpdate() {
        state = UpdateUiState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }.fold(
                    onSuccess = { release ->
                        UpdateUiState.Ready(
                            release = release,
                            hasUpdate = compareVersionNames(
                                release.versionName,
                                BuildConfig.VERSION_NAME
                            ) > 0
                        )
                    },
                    onFailure = { UpdateUiState.Error(it.localizedMessage ?: checkFailedText) }
                )
            }
        }
    }

    // 略微延后再请求：让入场动画先跑完，避免刚进页面就闪一下状态
    LaunchedEffect(Unit) {
        delay(250)
        checkUpdate()
    }

    val readyState = state as? UpdateUiState.Ready
    val hasUpdate = readyState?.hasUpdate == true
    val isChecking = state is UpdateUiState.Loading
    val downloadState by UpdateDownloadManager.downloadState.collectAsState()

    // 回到页面时若缓存里已有对应版本的安装包，直接进入「安装」态，不必重下
    LaunchedEffect(readyState?.release?.matchedAsset) {
        val asset = readyState?.release?.matchedAsset
        if (asset != null && readyState.hasUpdate) {
            UpdateDownloadManager.checkExistingApk(
                context = context,
                asset = asset,
                expectedVersion = readyState.release.versionName,
            )
        }
    }

    val displayMode = when {
        hasUpdate -> UpdateScreenMode.NewUpdate
        showCurrentLog && readyState != null -> UpdateScreenMode.CurrentLog
        else -> UpdateScreenMode.Centered
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSmallTopAppBar(
                title = if (displayMode == UpdateScreenMode.Centered) {
                    ""
                } else {
                    stringResource(R.string.activity_update)
                },
                color = pageBackground,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {},
            )

            val bottomContentPadding =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 116.dp

            Crossfade(
                targetState = displayMode,
                animationSpec = tween(280),
                label = "UpdateScreenCrossfade",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { mode ->
                when (mode) {
                    UpdateScreenMode.Centered -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            NoUpdateView(
                                state = state,
                                isDark = isDark,
                                showCurrentLog = showCurrentLog,
                                onToggleCurrentLog = { showCurrentLog = !showCurrentLog },
                            )
                        }
                    }

                    UpdateScreenMode.CurrentLog -> {
                        val release = readyState?.release
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = bottomContentPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                UpdateSection {
                                    CurrentVersionHeadline(
                                        isDark = isDark,
                                        onCollapse = { showCurrentLog = false },
                                    )
                                }
                            }

                            if (release != null) {
                                item {
                                    UpdateSection {
                                        ChangelogCard(
                                            title = stringResource(R.string.update_current_version_log),
                                            changelog = release.body,
                                        )
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }
                    }

                    UpdateScreenMode.NewUpdate -> {
                        val release = readyState?.release
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = bottomContentPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (release != null) {
                                item {
                                    UpdateSection {
                                        NewUpdateHeaderCard(release = release, isDark = isDark)
                                    }
                                }
                                item {
                                    UpdateSection {
                                        ChangelogCard(
                                            title = stringResource(R.string.update_view_changelog),
                                            changelog = release.body,
                                        )
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }
                    }
                }
            }
        }

        // 底部悬浮动作按钮：上方一段渐变把滚动内容渐隐进底色
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            pageBackground.copy(alpha = 0.85f),
                            pageBackground,
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            BottomActionButton(
                modifier = Modifier.widthIn(max = TABLET_MAX_CONTENT_WIDTH),
                hasUpdate = hasUpdate,
                isChecking = isChecking,
                isDark = isDark,
                downloadState = downloadState,
                onButtonClick = {
                    when (val current = downloadState) {
                        is UpdateDownloadState.Downloading -> Unit

                        is UpdateDownloadState.Completed ->
                            UpdateDownloadManager.installApk(context, current.apkFile)

                        is UpdateDownloadState.Failed -> {
                            val asset = readyState?.release?.matchedAsset
                            if (asset != null) {
                                UpdateDownloadManager.startDownload(
                                    context = context,
                                    asset = asset,
                                    expectedVersion = readyState.release.versionName,
                                )
                            } else {
                                checkUpdate()
                            }
                        }

                        UpdateDownloadState.Idle -> {
                            if (readyState != null && readyState.hasUpdate) {
                                val asset = readyState.release.matchedAsset
                                if (asset != null) {
                                    UpdateDownloadManager.startDownload(
                                        context = context,
                                        asset = asset,
                                        expectedVersion = readyState.release.versionName,
                                    )
                                } else {
                                    // 这次发布没有可匹配的 APK 附件，交给浏览器
                                    uriHandler.openUri(
                                        readyState.release.downloadUrl ?: readyState.release.htmlUrl
                                    )
                                }
                            } else {
                                checkUpdate()
                            }
                        }
                    }
                },
            )
        }
    }
}

/** 展开当前版本日志时列表顶部的版本抬头。 */
@Composable
private fun CurrentVersionHeadline(isDark: Boolean, onCollapse: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_name) + " v" + BuildConfig.VERSION_NAME +
                    " · " + stringResource(R.string.update_already_latest),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.50f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        ChangelogLink(
            text = stringResource(R.string.update_collapse_changelog),
            isDark = isDark,
            onClick = onCollapse,
        )
    }
}

/** 分区容器：手机铺满，平板限制最大宽度并居中，与关于页一致。 */
@Composable
private fun UpdateSection(content: @Composable ColumnScope.() -> Unit) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_MIN_WIDTH_DP
    if (!isTablet) {
        Column(content = content)
        return
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = TABLET_MAX_CONTENT_WIDTH),
            content = content,
        )
    }
}
