/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 */

package io.github.proify.lyricon.app.activity.lyric.provider

import android.icu.text.Collator
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.BaseActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.AsyncAppIcon
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.GoogleRainbowText
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.color
import io.github.proify.lyricon.app.compose.preference.rememberIntPreference
import io.github.proify.lyricon.app.util.AnimationEmoji
import io.github.proify.lyricon.app.util.LaunchBrowserCompose
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale

class LyricProviderActivity : BaseActivity() {

    private val viewModel: LyricProviderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LyricProviderContent() }
    }

    @Composable
    private fun LyricProviderContent() {
        val uiState by viewModel.uiState.collectAsState()
        val otherLabel = stringResource(R.string.other)

        val groupedModules by remember(uiState.modules, otherLabel) {
            derivedStateOf {
                val collator = Collator.getInstance(Locale.getDefault())
                val rawCategories = ModuleHelper.categorizeModules(uiState.modules, otherLabel)

                rawCategories.map { category ->
                    category.copy(items = category.items.sortedBy { it.label })
                }.sortedWith { c1, c2 ->
                    when {
                        c1.name == c2.name -> 0
                        c1.name == otherLabel -> 1
                        c2.name == otherLabel -> -1
                        else -> collator.compare(c1.name, c2.name)
                    }
                }
            }
        }

        var isListVisible by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.modules) {
            if (uiState.modules.isNotEmpty()) isListVisible = true
        }

        val showEmpty by remember {
            derivedStateOf { uiState.modules.isEmpty() && !uiState.isLoading }
        }

        AppToolBarListContainer(
            title = getString(R.string.activity_lyric_provider),
            canBack = true,
            showEmpty = showEmpty,
            empty = {
                EmptyStateView(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                    noQueryPermission = viewModel.noQueryPermission
                )
            },
            actions = { DisplayOptionsAction() }
        ) {
            if (uiState.isLoading && uiState.modules.isEmpty()) return@AppToolBarListContainer

            groupedModules.forEach { category ->
                // 渲染分类标题
                if (category.name.isNotBlank()) {
                    item(key = "header_${category.name}") {
                        AnimatedVisibility(
                            visible = isListVisible,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column {
                                SmallTitle(
                                    text = category.name,
                                    insideMargin = PaddingValues(start = 28.dp, end = 28.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }

                // 渲染分类下的应用卡片
                itemsIndexed(
                    items = category.items,
                    key = { _, item -> item.packageInfo.packageName }
                ) { _, item ->
                    AnimatedVisibility(
                        visible = isListVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            ModuleCard(
                                module = item,
                                showTags = viewModel.listStyle == ViewMode.FULL
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun EmptyStateView(modifier: Modifier, noQueryPermission: Boolean = false) {
        Column(
            modifier = modifier.overScrollVertical(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset(AnimationEmoji.getAssetsFile("Neutral-face"))
            )
            LottieAnimation(
                modifier = Modifier.size(100.dp),
                composition = composition,
                iterations = LottieConstants.IterateForever
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(if (noQueryPermission) R.string.no_query_app_permission else R.string.no_provider_available))
        }
    }

    @Composable
    private fun DisplayOptionsAction() {
        val context = LocalContext.current
        val sharedPreferences = remember { context.defaultSharedPreferences }

        var listStylePref by rememberIntPreference(
            sharedPreferences,
            "activity_provider_list_style",
            ViewMode.SHORT
        )

        val options = remember {
            listOf(
                R.string.option_provider_list_short to ViewMode.SHORT,
                R.string.option_provider_list_full to ViewMode.FULL
            )
        }

        val showPopup = remember { mutableStateOf(false) }
        val selectedIndex by remember(listStylePref) {
            derivedStateOf { options.indexOfFirst { it.second == listStylePref }.coerceAtLeast(0) }
        }

        var shouldLaunch by remember { mutableStateOf(false) }
        if (shouldLaunch) {
            LaunchBrowserCompose(stringResource(R.string.provider_release_home))
            shouldLaunch = false
        }

        Row(modifier = Modifier.padding(end = 14.dp)) {
            IconButton(onClick = { shouldLaunch = true }) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box {
                IconButton(onClick = { showPopup.value = true }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.More,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }

                SuperListPopup(
                    show = showPopup,
                    alignment = PopupPositionProvider.Align.TopEnd,
                    onDismissRequest = { showPopup.value = false }
                ) {
                    ListPopupColumn {
                        options.forEachIndexed { index, (labelRes, value) ->
                            DropdownImpl(
                                text = stringResource(labelRes),
                                optionSize = options.size,
                                isSelected = index == selectedIndex,
                                onSelectedIndexChange = {
                                    showPopup.value = false
                                    listStylePref = value
                                    viewModel.listStyle = value
                                },
                                index = index
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModuleCard(module: LyricModule, showTags: Boolean) {
        val titleColor = BasicComponentDefaults.titleColor()
        val summaryColor = BasicComponentDefaults.summaryColor()
        val unknownText = stringResource(R.string.unknown)

        val secondaryInfo = remember(module.packageInfo.versionName, module.author) {
            getString(
                R.string.item_provider_info_secondary,
                module.packageInfo.versionName ?: unknownText,
                getString(R.string.author),
                module.author ?: unknownText
            )
        }

        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    module.packageInfo.applicationInfo?.let { appInfo ->
                        AsyncAppIcon(application = appInfo, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = module.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = titleColor.color(true)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (module.isCertified && viewModel.listStyle == ViewMode.FULL) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    painter = painterResource(R.drawable.verified_24px),
                                    contentDescription = null,
                                    tint = MaterialPalette.Green.Primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = secondaryInfo,
                                fontSize = 14.sp,
                                color = summaryColor.color(true)
                            )
                        }
                    }
                }

                if (!module.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = module.description,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = summaryColor.color(true)
                    )
                }

                if (module.tags.isNotEmpty() && showTags) {
                    ModuleTagsFlow(module.tags)
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ModuleTagsFlow(tags: List<ModuleTag>) {
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            tags.forEach { tag ->
                val title =
                    if (tag.titleRes != -1) stringResource(tag.titleRes) else tag.title.orEmpty()
                Card(
                    modifier = Modifier.padding(end = 10.dp, top = 10.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconSize = 18.dp
                        tag.iconRes?.let {
                            Icon(
                                painterResource(it),
                                null,
                                Modifier.size(iconSize),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        tag.imageVector?.let {
                            Image(it, null, Modifier.size(iconSize))
                            Spacer(Modifier.width(4.dp))
                        }

                        if (tag.isRainbow) {
                            GoogleRainbowText(
                                text = title,
                                style = MiuixTheme.textStyles.body2.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        } else {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}