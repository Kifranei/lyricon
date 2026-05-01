/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.effect.BgEffectBackground
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.launchBrowser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AboutContent() }
    }

    @Composable
    private fun AboutContent() {
        val versionSummary = getVersionSummary()
        val buildTimeFormat = getBuildTimeFormat()
        val githubHome = getString(R.string.github_home)
        val scrollBehavior = MiuixScrollBehavior()
        val lazyListState = rememberLazyListState()
        val isDark = isSystemInDarkTheme()
        var logoHeightPx by remember { mutableIntStateOf(0) }
        val scrollProgress by remember {
            derivedStateOf {
                if (logoHeightPx <= 0) 0f else {
                    val index = lazyListState.firstVisibleItemIndex
                    val offset = lazyListState.firstVisibleItemScrollOffset
                    if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
                }
            }
        }

        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = getString(R.string.activity_about),
                    scrollBehavior = scrollBehavior,
                    color = colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                    titleColor = if (isDark) Color.White.copy(alpha = scrollProgress) else colorScheme.onSurface.copy(alpha = scrollProgress),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        val layoutDirection = LocalLayoutDirection.current
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_back),
                                tint = if (isDark) Color.White else colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            AboutBody(
                padding = PaddingValues(top = innerPadding.calculateTopPadding()),
                scrollProgress = scrollProgress,
                lazyListState = lazyListState,
                onLogoHeightChanged = { logoHeightPx = it },
                versionSummary = versionSummary,
                buildTimeFormat = buildTimeFormat,
                githubHome = githubHome,
                isDark = isDark,
            )
        }
    }

    @Composable
    private fun AboutBody(
        padding: PaddingValues,
        scrollProgress: Float,
        lazyListState: LazyListState,
        onLogoHeightChanged: (Int) -> Unit,
        versionSummary: String,
        buildTimeFormat: String,
        githubHome: String,
        isDark: Boolean,
    ) {
        val backdrop = rememberLayerBackdrop()
        val blurEnable = remember { isRenderEffectSupported() }
        val shaderSupported = remember { isRuntimeShaderSupported() }
        val density = LocalDensity.current

        var logoHeightDp by remember { androidx.compose.runtime.mutableStateOf(260.dp) }
        var logoAreaY by remember { mutableFloatStateOf(0f) }
        var titleY by remember { mutableFloatStateOf(0f) }
        var versionY by remember { mutableFloatStateOf(0f) }
        var titleProgress by remember { mutableFloatStateOf(0f) }
        var versionProgress by remember { mutableFloatStateOf(0f) }
        var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

        val titleBlend = remember(isDark) {
            if (isDark) {
                emptyList()
            } else {
                listOf(
                    BlendColorEntry(Color(0xCC4A4A4A.toInt()), BlurBlendMode.ColorBurn),
                    BlendColorEntry(Color(0xFF4F4F4F.toInt()), BlurBlendMode.LinearLight),
                    BlendColorEntry(Color(0xFF1AF200.toInt()), BlurBlendMode.Lab),
                )
            }
        }

        val cardBlendColors = remember(isDark) {
            if (isDark) {
                listOf(
                    BlendColorEntry(Color(0x66436BFF), BlurBlendMode.Screen),
                    BlendColorEntry(Color(0x334A7CFF), BlurBlendMode.Lighten),
                    BlendColorEntry(Color(0x1AFFFFFF), BlurBlendMode.Luminosity),
                )
            } else {
                listOf(
                    BlendColorEntry(Color(0x34F7D6FF), BlurBlendMode.Overlay),
                    BlendColorEntry(Color(0xB3FFFFFF.toInt()), BlurBlendMode.HardLight),
                )
            }
        }

        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
                .collect { offset ->
                    if (lazyListState.firstVisibleItemIndex > 0) {
                        if (titleProgress != 1f) titleProgress = 1f
                        if (versionProgress != 1f) versionProgress = 1f
                        return@collect
                    }
                    if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                        initialLogoAreaY = logoAreaY
                    }
                    val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY
                    val stage1TotalLength = refLogoAreaY - versionY
                    val stage2TotalLength = versionY - titleY
                    val versionDelay = stage1TotalLength * 0.5f
                    versionProgress = ((offset.toFloat() - versionDelay) /
                        (stage1TotalLength - versionDelay).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    titleProgress = ((offset.toFloat() - stage1TotalLength) /
                        stage2TotalLength.coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
        }

        BgEffectBackground(
            dynamicBackground = shaderSupported,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.layerBackdrop(backdrop),
            effectBackground = shaderSupported,
            alpha = { 1f - scrollProgress },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding() + 122.dp)
                    .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .textureBlur(
                            backdrop = backdrop,
                            shape = SmoothRoundedCornerShape(45.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = titleBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = blurEnable,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = if (isDark) Color.White else colorScheme.onBackground,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            if (titleY != 0f) return@onGloballyPositioned
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            titleY = y + size.height
                        }
                        .graphicsLayer {
                            alpha = 1 - titleProgress
                            scaleX = 1 - (titleProgress * 0.05f)
                            scaleY = 1 - (titleProgress * 0.05f)
                        }
                        .then(
                            if (isDark) Modifier else Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = SmoothRoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurColors(blendColors = titleBlend),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = blurEnable,
                            )
                        ),
                    text = stringResource(R.string.app_name),
                    color = if (isDark) Color.White else colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = 1 - versionProgress
                            scaleX = 1 - (versionProgress * 0.05f)
                            scaleY = 1 - (versionProgress * 0.05f)
                        }
                        .onGloballyPositioned { coordinates ->
                            if (versionY != 0f) return@onGloballyPositioned
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            versionY = y + size.height
                        },
                    color = if (isDark) Color.White.copy(alpha = 0.72f) else colorScheme.onSurfaceVariantSummary,
                    text = versionSummary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                overscrollEffect = null,
            ) {
                item(key = "logoSpacer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(logoHeightDp + 228.dp)
                            .onSizeChanged { size -> onLogoHeightChanged(size.height) }
                            .onGloballyPositioned { coordinates ->
                                val y = coordinates.positionInWindow().y
                                val size = coordinates.size
                                logoAreaY = y + size.height
                            },
                    )
                }

                item(key = "about_info") {
                    SmallTitle(text = stringResource(R.string.section_about_info))
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        BasicComponent(title = stringResource(R.string.item_app_version), summary = versionSummary)
                        BasicComponent(title = stringResource(R.string.item_build_time), summary = buildTimeFormat)
                    }
                }

                item(key = "about_project") {
                    SmallTitle(text = stringResource(R.string.section_about_project))
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        BasicComponent(
                            title = stringResource(R.string.item_app_version),
                            summary = versionSummary,
                        )
                        BasicComponent(
                            title = stringResource(R.string.app_name),
                            summary = githubHome.removePrefix("https://"),
                            onClick = { launchBrowser(githubHome) },
                        )
                    }
                }

                item(key = "about_thanks") {
                    SmallTitle(text = stringResource(R.string.section_about_thanks))
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        BasicComponent(
                            title = "tomakino",
                            summary = "Upstream Lyricon",
                            onClick = { launchBrowser("https://github.com/tomakino/lyricon") },
                        )
                        BasicComponent(
                            title = "Kifranei",
                            summary = "Fork customization",
                        )
                    }
                }

                item(key = "about_open_source") {
                    SmallTitle(text = stringResource(R.string.section_about_open_source))
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        BasicComponent(
                            title = "Miuix",
                            summary = "MIUI/HyperOS Compose UI",
                            onClick = { launchBrowser("https://github.com/miuix-kmp/miuix") },
                        )
                        BasicComponent(
                            title = "Lyricon",
                            summary = "Provider API and status bar lyric",
                            onClick = { launchBrowser(githubHome) },
                        )
                        BasicComponent(
                            title = stringResource(R.string.item_open_source_licenses),
                            summary = stringResource(R.string.item_open_source_licenses_summary),
                            onClick = { startActivity(Intent(this@AboutActivity, LicensesActivity::class.java)) },
                        )
                    }
                }

                item {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }

    @Composable
    private fun FrostedCard(
        backdrop: LayerBackdrop,
        blurEnable: Boolean,
        cardBlendColors: List<BlendColorEntry>,
        content: @Composable () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .textureBlur(
                    backdrop = backdrop,
                    shape = SmoothRoundedCornerShape(18.dp),
                    blurRadius = 64f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = cardBlendColors),
                    enabled = blurEnable,
                ),
            colors = CardDefaults.defaultColors(
                if (blurEnable) Color.Transparent else colorScheme.surfaceContainer,
                Color.Transparent,
            ),
        ) {
            content()
        }
    }

    private fun getVersionSummary(): String = getString(
        R.string.item_app_version_summary,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE.toString(),
        BuildConfig.BUILD_TYPE,
    )

    private fun getBuildTimeFormat(): String = Instant
        .ofEpochMilli(BuildConfig.BUILD_TIME)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(AppLangUtils.getLocale()),
        )
}
