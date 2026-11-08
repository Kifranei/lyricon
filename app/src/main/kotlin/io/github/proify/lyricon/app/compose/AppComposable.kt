/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.MathUtils.lerp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.BaseActivity
import androidx.compose.runtime.getValue
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.compose.custom.miuix.basic.MiuixScrollBehavior
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.custom.miuix.basic.TopAppBar
import io.github.proify.lyricon.app.compose.effect.BgEffectBackground
import io.github.proify.lyricon.app.compose.theme.AppTheme
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

val LocalBottomBarBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalFloatingBottomBarEnabled = staticCompositionLocalOf { false }
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }
val LocalSharedBackgroundEnabled = staticCompositionLocalOf { false }
private val FloatingBottomBarScrollPadding = 96.dp

@Composable
fun NavigationBackIcon(
    context: Context = LocalContext.current,
    backEvent: () -> Unit = {
        if (context is BaseActivity) context.onBackPressedDispatcher.onBackPressed()
    },
) {
    IconButton(onClick = { backEvent.invoke() }) {
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        Icon(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = if (isRtl) -1f else 1f
                },
            imageVector = MiuixIcons.Back,
            contentDescription = stringResource(R.string.action_back)
        )
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun BlurTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    largeTitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    horizontalPadding: Dp = 20.dp,
    hazeState: HazeState? = null,
    titleDropdown: Boolean = false,
    titleOnClick: () -> Unit = {},
) {
    val blurRadius = if (isSystemInDarkTheme()) 50.dp else 25.dp
    val hazeStyle = HazeStyle(
        blurRadius = blurRadius,
        noiseFactor = 0f,
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(
            MiuixTheme.colorScheme.surface.copy(
                if (scrollBehavior == null || scrollBehavior.state.collapsedFraction <= 0f) {
                    1f
                } else {
                    lerp(1f, 0.76f, (scrollBehavior.state.collapsedFraction))
                }
            )
        )
    )

    TopAppBar(
        title = title,
        modifier = if (hazeState != null) modifier.hazeEffect(hazeState, hazeStyle) else modifier,
        color = color,
        largeTitle = largeTitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        horizontalPadding = horizontalPadding,
        titleDropdown = titleDropdown,
        titleOnClick = titleOnClick
    )
}

@Composable
fun getCurrentTitle(): String? {
    val context = LocalContext.current
    return if (context is Activity) context.title?.toString() else null
}

@Composable
fun AppToolBarListContainer(
    context: Context = LocalContext.current,
    backEvent: () -> Unit = {
        (context as? BaseActivity)?.onBackPressedDispatcher?.onBackPressed()
    },
    title: Any? = getCurrentTitle(),
    canBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scaffoldContent: @Composable () -> Unit = {},
    titleDropdown: Boolean = false,
    titleOnClick: () -> Unit = {},
    showEmpty: Boolean = false,
    empty: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    AppTheme {
        val bottomBarBackdrop = rememberLayerBackdrop()
        val hazeState = remember { HazeState() }
        val scrollBehavior = MiuixScrollBehavior()

        val flowingBackground by rememberBooleanPreference(
            context.defaultSharedPreferences,
            "enable_flowing_background",
            true
        )

        val progressiveTopBarBlur by rememberBooleanPreference(
            context.defaultSharedPreferences,
            BarStylePrefs.KEY_PROGRESSIVE_TOP_BAR_BLUR,
            true
        )
        // 顶栏渐进模糊（Halcyon EllaSmallTopAppBar 同款）：流光背景开启时顶栏保持透明以透出流光，不做渐进模糊
        val useProgressiveBlur = progressiveTopBarBlur && !flowingBackground
        val surfaceColor = MiuixTheme.colorScheme.surface
        val blurBackdrop = if (useProgressiveBlur) rememberProgressiveBlurBackdrop(surfaceColor) else null

        val titleText = remember(title) {
            when (title) {
                is Int -> null
                else -> title?.toString() ?: ""
            }
        }

        androidx.compose.runtime.CompositionLocalProvider(
            LocalBottomBarBackdrop provides bottomBarBackdrop,
            LocalProgressiveTopBarBlur provides useProgressiveBlur,
            LocalGlassBackdrop provides blurBackdrop,
        ) {
            val floatingBottomPadding = if (LocalFloatingBottomBarEnabled.current) {
                FloatingBottomBarScrollPadding
            } else {
                0.dp
            }
            // 流光背景：开启后 Scaffold 透明，底层铺关于页同款着色器流光；四个 Tab 页共用此容器
            val scaffold = @Composable {
            Scaffold(
                containerColor = if (flowingBackground) Color.Transparent
                else MiuixTheme.colorScheme.surface,
                bottomBar = bottomBar,
                topBar = {
                    val topBar = @Composable {
                        BlurTopAppBar(
                            // 流光模式下顶栏不做 haze 模糊、保持透明，让底层流光透出覆盖顶栏区域；
                            // 渐进模糊时由外层 ProgressiveBlurTopBar 绘制背景
                            hazeState = if (flowingBackground || useProgressiveBlur) null else hazeState,
                            navigationIcon = {
                                if (canBack) {
                                    NavigationBackIcon(backEvent = backEvent)
                                }
                            },
                            title = if (title is Int) stringResource(title) else (titleText ?: ""),
                            scrollBehavior = scrollBehavior,
                            actions = actions,
                            titleDropdown = titleDropdown,
                            titleOnClick = titleOnClick
                        )
                    }
                    if (useProgressiveBlur) {
                        ProgressiveBlurTopBar(backdrop = blurBackdrop, color = surfaceColor) { topBar() }
                    } else {
                        topBar()
                    }
                }
            ) { paddingValues ->
                scaffoldContent()

                // 不消费底栏高度：内容延伸到停靠底栏之后，底栏的背景模糊才能采样到内容；
                // 列表用 contentPadding 避让底栏，视觉上不被遮挡。
                // 渐进模糊时内容延伸到顶栏下方（列表用 contentPadding 避让），顶栏才能采样到滚动中的内容
                val topPadding = paddingValues.calculateTopPadding()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(bottomBarBackdrop)
                        .progressiveBlurSource(blurBackdrop)
                        .then(if (useProgressiveBlur) Modifier else Modifier.padding(top = topPadding))
                ) {

                    AnimatedVisibility(
                        visible = !showEmpty,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
                                .hazeSource(hazeState),
                            contentPadding = PaddingValues(
                                top = if (useProgressiveBlur) topPadding else 0.dp,
                                bottom = floatingBottomPadding +
                                        paddingValues.calculateBottomPadding()
                            ),
                            content = content
                        )
                    }

                    AnimatedVisibility(
                        visible = showEmpty,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (useProgressiveBlur) Modifier.padding(top = topPadding) else Modifier)
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
                        ) {
                            empty()
                        }
                    }
                }
            }
            }

            if (flowingBackground) {
                // 用主题背景亮度实时判断明暗，避免全局 var（非响应式）滞后导致暗色误用亮色流光
                val isDarkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
                val flowingScaffold: @Composable () -> Unit = {
                    // 流光模式下让卡片（默认取 surfaceContainer 系列色）转为半透明，
                    // 复用关于页的观感——透出底层流光，而不是不透明黑块
                    val cs = MiuixTheme.colorScheme
                    val a = if (isDarkBg) 0.42f else 0.55f
                    MiuixTheme(
                        colors = cs.copy(
                            surfaceContainer = cs.surfaceContainer.copy(alpha = a),
                            surfaceContainerHigh = cs.surfaceContainerHigh.copy(alpha = a),
                            surfaceContainerHighest = cs.surfaceContainerHighest.copy(alpha = a),
                            surfaceVariant = cs.surfaceVariant.copy(alpha = a),
                            // 半透明卡片上分隔线会突出，流光模式下隐藏设置项之间的分界线
                            dividerLine = Color.Transparent,
                        ),
                    ) { scaffold() }
                }
                if (LocalSharedBackgroundEnabled.current) {
                    flowingScaffold()
                } else {
                    BgEffectBackground(
                        dynamicBackground = true,
                        modifier = Modifier.fillMaxSize(),
                        isDarkTheme = isDarkBg,
                    ) { flowingScaffold() }
                }
            } else {
                scaffold()
            }
        }
    }
}

@Composable
fun AppToolBarContainer(
    context: Context = LocalContext.current,
    backEvent: () -> Unit = {
        if (context is BaseActivity) context.onBackPressedDispatcher.onBackPressed()
    },
    title: Any? = getCurrentTitle(),
    canBack: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    titleDropdown: Boolean = false,
    titleOnClick: () -> Unit = {},
    scrollBehavior: io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior = MiuixScrollBehavior(),
    hazeState: HazeState = rememberHazeState(),
    content: @Composable (PaddingValues) -> Unit,
) {
    AppTheme {
        val bottomBarBackdrop = rememberLayerBackdrop()
        val progressiveTopBarBlur by rememberBooleanPreference(
            context.defaultSharedPreferences,
            BarStylePrefs.KEY_PROGRESSIVE_TOP_BAR_BLUR,
            true
        )
        val surfaceColor = MiuixTheme.colorScheme.surface
        val blurBackdrop = if (progressiveTopBarBlur) rememberProgressiveBlurBackdrop(surfaceColor) else null
        androidx.compose.runtime.CompositionLocalProvider(
            LocalBottomBarBackdrop provides bottomBarBackdrop,
            LocalProgressiveTopBarBlur provides progressiveTopBarBlur,
            LocalGlassBackdrop provides blurBackdrop,
        ) {
            val floatingBottomPadding = if (LocalFloatingBottomBarEnabled.current) {
                FloatingBottomBarScrollPadding
            } else {
                0.dp
            }
            Scaffold(
                bottomBar = bottomBar,
                topBar = {
                    val topBar = @Composable {
                        BlurTopAppBar(
                            hazeState = if (progressiveTopBarBlur) null else hazeState,
                            navigationIcon = {
                                if (canBack) NavigationBackIcon(
                                    backEvent = backEvent
                                )
                            },
                            title = if (title is Int) stringResource(title) else title.toString(),
                            scrollBehavior = scrollBehavior,
                            actions = actions,
                            titleDropdown = titleDropdown,
                            titleOnClick = titleOnClick
                        )
                    }
                    if (progressiveTopBarBlur) {
                        ProgressiveBlurTopBar(backdrop = blurBackdrop, color = surfaceColor) { topBar() }
                    } else {
                        topBar()
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(bottomBarBackdrop)
                        .progressiveBlurSource(blurBackdrop)
                        .padding(bottom = floatingBottomPadding)
                ) {
                    content(paddingValues)
                }
            }
        }
    }
}


@Composable
fun IconActions(
    painter: Painter,
    contentDescription: String? = null,
    tint: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
) {
    Icon(
        modifier = Modifier
            .padding(
                start = 0.dp, end = 16.dp
            )
            .size(24.dp),
        painter = painter,
        contentDescription = contentDescription,
        tint = tint
    )
}


fun BasicComponentColors.color(enabled: Boolean): Color {
    return if (enabled) color else disabledColor
}

@Composable
fun ColoredIconBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    iconRes: Int,
    isMonet: Boolean = false
) {
    val iconSize = if (isMonet) 20.dp else 24.dp
    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .size(40.dp)
            .background(
                if (isMonet) MiuixTheme.colorScheme.primary else backgroundColor,
                CircleShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            modifier = modifier.size(iconSize),
            tint = Color.White,
            contentDescription = null
        )
    }
}
