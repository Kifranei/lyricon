package io.github.proify.lyricon.app.ui.about

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.LicensesActivity
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
                defaultWindowInsetsPadding = false,
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
        AboutContent(
            padding = PaddingValues(top = innerPadding.calculateTopPadding()),
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
                .padding(top = padding.calculateTopPadding() + heroTopPadding)
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
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
                SmallTitle(text = stringResource(R.string.about_project))
                FrostedCard(
                    backdrop = backdrop,
                    blurEnable = blurEnable,
                    cardBlendColors = cardBlendColors,
                    scrollProgress = scrollProgress,
                ) {
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

            item {
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
                        title = "InstallerX Revived",
                        summary = stringResource(R.string.about_summary_installerx),
                        onClick = { uriHandler.openUri("https://github.com/wxxsfxyzm/InstallerX-Revived") },
                    )
                    BasicComponent(
                        title = "Halcyon",
                        summary = stringResource(R.string.about_summary_halcyon),
                        onClick = { uriHandler.openUri("https://github.com/Kifranei/Halcyon") },
                    )
                }
            }

            item {
                Spacer(
                    Modifier
                        .height(160.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
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
