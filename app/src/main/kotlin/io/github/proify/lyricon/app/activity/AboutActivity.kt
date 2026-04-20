package io.github.proify.lyricon.app.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.GoogleRainbowText
import io.github.proify.lyricon.app.compose.theme.AppTheme
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.foundation.layout.ColumnScope

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AboutContent()
        }
    }

    @Composable
    private fun AboutContent() {
        val buildTimeText = remember {
            Instant.ofEpochMilli(BuildConfig.BUILD_TIME)
                .atZone(ZoneId.systemDefault())
                .format(
                    DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(AppLangUtils.getLocale())
                )
        }
        val scrollBehavior = MiuixScrollBehavior()
        val listState = rememberLazyListState()
        val backdrop = rememberLayerBackdrop()
        val blurEnabled = remember { isRenderEffectSupported() }
        val titleBlend = remember {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200.toInt()), BlurBlendMode.Lab),
            )
        }
        val cardBlendColors = remember {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF.toInt()), BlurBlendMode.HardLight),
            )
        }

        AppTheme {
            Scaffold(
                topBar = {
                    SmallTopAppBar(
                        title = stringResource(R.string.activity_about),
                        scrollBehavior = scrollBehavior,
                        color = Color.Transparent,
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        },
                    )
                },
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFF3F4FB),
                                    Color(0xFFE9E8FF),
                                    Color(0xFFFFE4EA),
                                )
                            )
                        )
                        .layerBackdrop(backdrop)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = paddingValues.calculateTopPadding() + 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(18.dp),
                                    blurRadius = 150f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurColors(blendColors = titleBlend),
                                    contentBlendMode = BlendMode.DstIn,
                                    enabled = blurEnabled,
                                )
                        ) {
                            GoogleRainbowText(
                                text = stringResource(R.string.app_name),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 35.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onBackground,
                                ),
                            )
                        }
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                        overscrollEffect = null,
                    ) {
                        item("hero_spacer") {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(316.dp)
                            )
                        }

                        item("app_info_title") {
                            SmallTitle(text = stringResource(R.string.item_summary_about_app))
                        }

                        item("app_info_card") {
                            FrostedCard(
                                backdrop = backdrop,
                                blurEnabled = blurEnabled,
                                cardBlendColors = cardBlendColors,
                            ) {
                                BasicComponent(
                                    title = stringResource(R.string.item_app_version),
                                    summary = BuildConfig.VERSION_NAME,
                                )
                                BasicComponent(
                                    title = stringResource(R.string.item_build_type),
                                    summary = BuildConfig.BUILD_TYPE,
                                )
                                BasicComponent(
                                    title = stringResource(R.string.item_build_time),
                                    summary = buildTimeText,
                                )
                            }
                        }

                        item("project_title") {
                            SmallTitle(text = stringResource(R.string.item_project))
                        }

                        item("project_card") {
                            FrostedCard(
                                backdrop = backdrop,
                                blurEnabled = blurEnabled,
                                cardBlendColors = cardBlendColors,
                            ) {
                                ArrowPreference(
                                    title = stringResource(R.string.app_name),
                                    summary = stringResource(R.string.github_home),
                                    onClick = { launchBrowser(getString(R.string.github_home)) },
                                )
                                ArrowPreference(
                                    title = stringResource(R.string.item_open_source_license),
                                    summary = stringResource(R.string.activity_open_source_license),
                                    onClick = {
                                        startActivity(Intent(this@AboutActivity, LicensesActivity::class.java))
                                    },
                                )
                            }
                        }

                        item("bottom_spacer") {
                            Spacer(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .height(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FrostedCard(
        backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
        blurEnabled: Boolean,
        cardBlendColors: List<BlendColorEntry>,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(18.dp),
                    blurRadius = 60f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = cardBlendColors),
                    enabled = blurEnabled,
                ),
            colors = CardDefaults.defaultColors(
                if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                Color.Transparent,
            ),
            content = { content() },
        )
    }
}
