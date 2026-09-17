package io.github.proify.lyricon.app.ui.about

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.LicensesActivity
import io.github.proify.lyricon.app.activity.UpdateActivity
import io.github.proify.lyricon.app.compose.AppSmallTopAppBar
import io.github.proify.lyricon.app.compose.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val GITHUB_REPO_URL = "https://github.com/kifranei/lyricon"

/**
 * 列表末尾留白，保证内容再少也能继续下滑、让顶部 Logo 完整收起。
 *
 * 按视口比例而非固定高度：长屏手机与平板的视口差异很大，
 * 固定值在大屏上不足以撑出滚动空间。
 */
private const val BOTTOM_SCROLL_SPACE_FRACTION = 0.72f
private val BOTTOM_SCROLL_SPACE_MIN = 160.dp


@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) 0f
            else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            AppSmallTopAppBar(
                title = stringResource(R.string.activity_about),
                scrollBehavior = scrollBehavior,
                color = colorScheme.surface.copy(alpha = scrollProgress.coerceIn(0f, 1f)),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.action_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // 横屏时避让挖孔与侧边导航栏，背景仍铺满全屏
        val layoutDirection = LocalLayoutDirection.current
        val horizontalInsets = WindowInsets.displayCutout
            .union(WindowInsets.navigationBars)
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues()
        AboutContent(
            padding = PaddingValues(
                start = horizontalInsets.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = horizontalInsets.calculateEndPadding(layoutDirection),
            ),
            scrollBehavior = scrollBehavior,
            scrollProgress = scrollProgress,
            lazyListState = lazyListState,
            onLogoHeightChanged = { logoHeightPx = it },
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrollProgress: Float,
    lazyListState: LazyListState,
    onLogoHeightChanged: (Int) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = colorScheme.background.luminance() < 0.5f
    val blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = padding.calculateStartPadding(layoutDirection)
    val endPadding = padding.calculateEndPadding(layoutDirection)
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val logoLiftPx = with(density) { 96.dp.toPx() }
    val heroTopPadding = 148.dp
    val heroBottomPadding = 112.dp

    val titleBlend = remember(isDark) { aboutTitleBlendColors(isDark) }
    val cardBlendColors = remember(isDark) { aboutCardBlendColors(isDark) }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = true,
        isDarkTheme = isDark,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = (1f - scrollProgress * 1.35f).coerceIn(0f, 1f)
                    translationY = -logoLiftPx * scrollProgress
                }
                .padding(
                    start = startPadding,
                    top = padding.calculateTopPadding() + heroTopPadding,
                    end = endPadding,
                )
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .padding(top = 0.dp, bottom = 5.dp)
                    .then(
                        if (blurEnable) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = titleBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = true,
                        ) else Modifier
                    ),
                text = stringResource(R.string.app_name),
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = startPadding,
                top = padding.calculateTopPadding(),
                end = endPadding,
            ),
            overscrollEffect = null,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(logoHeightDp + heroTopPadding + padding.calculateTopPadding() + heroBottomPadding)
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) },
                )
            }

            item {
                AboutSection {
                SmallTitle(text = stringResource(R.string.about_project))
                FrostedCard(
                    backdrop = backdrop,
                    blurEnable = blurEnable,
                    cardBlendColors = cardBlendColors,
                    scrollProgress = scrollProgress,
                ) {
                    BasicComponent(
                        title = stringResource(R.string.item_check_update),
                        summary = stringResource(R.string.item_check_update_summary),
                        onClick = {
                            context.startActivity(Intent(context, UpdateActivity::class.java))
                        },
                    )
                    BasicComponent(
                        title = stringResource(R.string.item_view_on_github),
                        summary = GITHUB_REPO_URL,
                        onClick = { uriHandler.openUri(GITHUB_REPO_URL) },
                    )
                    BasicComponent(
                        title = stringResource(R.string.item_open_source_licenses),
                        summary = stringResource(R.string.item_open_source_licenses_summary),
                        onClick = {
                            context.startActivity(Intent(context, LicensesActivity::class.java))
                        },
                    )
                }
                }
            }

            item {
                AboutSection {
                SmallTitle(text = stringResource(R.string.about_acknowledgements))
                FrostedCard(
                    backdrop = backdrop,
                    blurEnable = blurEnable,
                    cardBlendColors = cardBlendColors,
                    scrollProgress = scrollProgress,
                ) {
                    BasicComponent(
                        title = "Lyricon",
                        summary = stringResource(R.string.about_summary_lyricon_upstream),
                        onClick = { uriHandler.openUri("https://github.com/tomakino/lyricon") },
                    )
                    BasicComponent(
                        title = "Halcyon",
                        summary = stringResource(R.string.about_summary_halcyon),
                        onClick = { uriHandler.openUri("https://github.com/Kifranei/Halcyon") },
                    )
                }
                }
            }

            item {
                Spacer(
                    Modifier
                        .fillParentMaxHeight(BOTTOM_SCROLL_SPACE_FRACTION)
                        .heightIn(min = BOTTOM_SCROLL_SPACE_MIN)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun AboutSection(content: @Composable ColumnScope.() -> Unit) {
    Column(content = content)
}

@Composable
private fun FrostedCard(
    backdrop: LayerBackdrop,
    blurEnable: Boolean,
    cardBlendColors: List<BlendColorEntry>,
    scrollProgress: Float,
    content: @Composable () -> Unit,
) {
    val isDark = colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (blurEnable) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = if (isDark) 72f else 64f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = cardBlendColors),
                    enabled = true,
                ) else Modifier
            ),
        colors = CardDefaults.defaultColors(
            if (blurEnable) {
                Color.Transparent
            } else if (isDark) {
                aboutCardFallbackColor(isDark).copy(alpha = 0.86f + 0.08f * scrollProgress.coerceIn(0f, 1f))
            } else {
                colorScheme.surfaceContainer
            },
            colorScheme.onSurface,
        ),
    ) {
        content()
    }
}
