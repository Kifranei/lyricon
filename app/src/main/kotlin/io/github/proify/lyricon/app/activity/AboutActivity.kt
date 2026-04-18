/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarContainer
import io.github.proify.lyricon.app.compose.effect.BgEffectBackground
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.launchBrowser
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AboutContent() }
    }

    @Composable
    private fun AboutContent() {
        val buildTimeFormat =
            Instant
                .ofEpochMilli(BuildConfig.BUILD_TIME)
                .atZone(ZoneId.systemDefault())
                .format(
                    DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(AppLangUtils.getLocale()),
                )
        val versionSummary =
            stringResource(
                id = R.string.item_app_version_summary,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE.toString(),
                BuildConfig.BUILD_TYPE,
            )
        val githubHome = stringResource(id = R.string.github_home)
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val headerProgress =
            remember(listState) {
                derivedStateOf {
                    val offset = if (listState.firstVisibleItemIndex > 0) 9999 else listState.firstVisibleItemScrollOffset
                    (offset / 220f).coerceIn(0f, 1f)
                }
            }.value
        val headerLiftPx = with(density) { 72.dp.toPx() }

        AppToolBarContainer(
            title = stringResource(id = R.string.activity_about),
            canBack = true,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                AboutAnimatedBackground()

                AboutHeader(
                    versionSummary = versionSummary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = padding.calculateTopPadding() + 136.dp),
                    progress = headerProgress,
                    liftPx = headerLiftPx,
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                ) {
                    item("header_spacer") {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(370.dp),
                        )
                    }

                    item("info_title") {
                        SectionTitle(text = stringResource(id = R.string.section_about_info))
                    }

                    item("info_card") {
                        AboutInfoCard(
                            items =
                                listOf(
                                    stringResource(id = R.string.item_app_version) to versionSummary,
                                    stringResource(id = R.string.item_build_info) to "${BuildConfig.BUILD_REPO}\n${BuildConfig.BUILD_BRANCH} @ ${BuildConfig.BUILD_COMMIT}",
                                    stringResource(id = R.string.item_build_author) to BuildConfig.BUILD_AUTHOR,
                                    stringResource(id = R.string.item_build_time) to buildTimeFormat,
                                ),
                        )
                    }

                    item("project_title") {
                        SectionTitle(text = stringResource(id = R.string.section_about_project))
                    }

                    item("project_card") {
                        AboutProjectCard(
                            items =
                                listOf(
                                    Triple(
                                        stringResource(id = R.string.app_name),
                                        githubHome.removePrefix("https://"),
                                    ) {
                                        launchBrowser(githubHome)
                                    },
                                    Triple(
                                        stringResource(id = R.string.item_open_source_license),
                                        stringResource(id = R.string.item_open_source_license_summary),
                                    ) {
                                        startActivity(Intent(this@AboutActivity, LicensesActivity::class.java))
                                    },
                                ),
                        )
                    }

                    item("nav_bottom") {
                        Spacer(
                            modifier =
                                Modifier
                                    .height(24.dp)
                                    .navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AboutAnimatedBackground() {
        val isDark = isSystemInDarkTheme()
        val palette: List<Color> =
            remember(isDark) {
                if (isDark) {
                    listOf(
                        Color(0xFF4B255F),
                        Color(0xFF223A84),
                        Color(0xFF5F2C78),
                        Color(0xFF1B5668),
                    )
                } else {
                    listOf(
                        Color(0xFFF7C7E2),
                        Color(0xFFC7D9FF),
                        Color(0xFFE7C8FF),
                        Color(0xFFC6F1EA),
                    )
                }
            }
        val transition = rememberInfiniteTransition(label = "about_page_bg")
        val phase =
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = if (isDark) 18000 else 14000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "about_page_bg_phase",
            ).value

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val t = phase * (2f * PI).toFloat()

            drawRect(
                brush =
                    Brush.linearGradient(
                        colors =
                            if (isDark) {
                                listOf(Color(0xFF241A3A), Color(0xFF13254B), Color(0xFF17162B))
                            } else {
                                listOf(Color(0xFFFFF1F7), Color(0xFFF4F0FF), Color(0xFFF2FBFF))
                            },
                        start = Offset.Zero,
                        end = Offset(width, height),
                    ),
            )

            val blobs: List<Triple<Offset, Float, Color>> =
                listOf(
                    Triple(Offset(width * (0.18f + 0.08f * sin(t)), height * (0.15f + 0.06f * cos(t * 0.82f))), width * 0.68f, palette[0]),
                    Triple(Offset(width * (0.84f + 0.07f * cos(t * 0.9f)), height * (0.22f + 0.06f * sin(t * 1.1f))), width * 0.62f, palette[1]),
                    Triple(Offset(width * (0.26f + 0.09f * cos(t * 1.18f + 1.2f)), height * (0.76f + 0.06f * sin(t * 0.7f + 0.8f))), width * 0.76f, palette[2]),
                    Triple(Offset(width * (0.82f + 0.08f * sin(t * 0.75f + 2.4f)), height * (0.84f + 0.06f * cos(t * 1.05f + 1.4f))), width * 0.64f, palette[3]),
                )

            blobs.forEach { (center, radius, color) ->
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = if (isDark) 0.48f else 0.72f), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                    radius = radius,
                    center = center,
                )
            }

            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors =
                            if (isDark) {
                                listOf(Color.White.copy(alpha = 0.03f), Color.Transparent, Color.Black.copy(alpha = 0.16f))
                            } else {
                                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.White.copy(alpha = 0.06f))
                            },
                    ),
            )
        }
    }

    @Composable
    private fun AboutHeader(
        versionSummary: String,
        modifier: Modifier = Modifier,
        progress: Float = 0f,
        liftPx: Float = 0f,
    ) {
        Column(
            modifier =
                modifier.graphicsLayer {
                    translationY = -liftPx * progress
                    alpha = 1f - progress
                    scaleX = 1f - (progress * 0.06f)
                    scaleY = 1f - (progress * 0.06f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = versionSummary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        SmallTitle(
            text = text,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
    }

    @Composable
    private fun AboutInfoCard(items: List<Pair<String, String>>) {
        val isDark = isSystemInDarkTheme()
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 18.dp),
            colors =
                CardDefaults.defaultColors(
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.68f),
                ),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, (title, summary) ->
                    AboutInfoRow(
                        title = title,
                        summary = summary,
                        showDivider = index != items.lastIndex,
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutProjectCard(items: List<Triple<String, String, () -> Unit>>) {
        val isDark = isSystemInDarkTheme()
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 18.dp),
            colors =
                CardDefaults.defaultColors(
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.68f),
                ),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    AboutProjectRow(
                        title = item.first,
                        summary = item.second,
                        onClick = item.third,
                        showDivider = index != items.lastIndex,
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutInfoRow(
        title: String,
        summary: String,
        showDivider: Boolean,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 15.sp,
            )
        }
        if (showDivider) {
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 20.dp),
            )
        }
    }

    @Composable
    private fun AboutProjectRow(
        title: String,
        summary: String,
        onClick: () -> Unit,
        showDivider: Boolean,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 15.sp,
                )
            }
            Text(
                text = "›",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 24.sp,
            )
        }
        if (showDivider) {
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 20.dp),
            )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun AboutContentPreview() {
        AboutContent()
    }
}
