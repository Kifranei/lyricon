/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric.provider

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
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.GoogleRainbowText
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.custom.miuix.basic.BasicComponentDefaults
import io.github.proify.lyricon.app.compose.preference.rememberIntPreference
import io.github.proify.lyricon.app.ui.activity.BaseActivity
import io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.sheet.AsyncAppIcon
import io.github.proify.lyricon.app.util.AnimationEmoji
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.DropdownImpl
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.ImmersionMore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

class LyricProviderActivity : BaseActivity() {

    private val viewModel: LyricProviderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LyricProviderContent() }
    }

    @Composable
    private fun LyricProviderContent() {
        val uiState by viewModel.uiState.collectAsState()

        // 提取“其它”标签，用于排序识别
        val otherLabel = stringResource(R.string.other)

        // 使用 derivedStateOf 保证仅在 modules 变化时重新计算排序
        val groupedModules by remember(uiState.modules) {
            derivedStateOf {
                // 1. 调用 Helper 进行初步分类
                val rawCategories = ModuleHelper.categorizeModules(
                    uiState.modules,
                    otherLabel
                )

                // 2. 对分类列表进行排序
                rawCategories
                    .map { category ->
                        // 2a. 【可选】对分类内部的应用也按 label 排序，确保整体整洁
                        category.copy(items = category.items.sortedBy { it.label })
                    }
                    .sortedWith { c1, c2 ->
                        when {
                            c1.name == c2.name -> 0
                            // “其它”类别始终排在最后 (返回 1 表示 c1 大于 c2)
                            c1.name == otherLabel -> 1
                            c2.name == otherLabel -> -1
                            // 其他普通类别按本地语言字母顺序排序 (支持中文拼音)
                            else -> java.text.Collator.getInstance(java.util.Locale.getDefault())
                                .compare(c1.name, c2.name)
                        }
                    }
            }
        }

        var isListVisible by remember { mutableStateOf(false) }

        LaunchedEffect(uiState.modules) {
            if (uiState.modules.isNotEmpty()) {
                isListVisible = true
            }
        }

        val showEmpty by remember {
            derivedStateOf {
                uiState.modules.isEmpty() && !uiState.isLoading
            }
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
            if (uiState.isLoading && uiState.modules.isEmpty()) {
                return@AppToolBarListContainer
            }

            // 这里的列表已经过“分类名排序”且“其它在最后”
            groupedModules.forEach { category ->
                itemsIndexed(
                    items = category.items,
                    key = { _, item -> item.packageInfo.packageName }
                ) { index, item ->
                    AnimatedVisibility(
                        visible = isListVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            // 仅在每个分类的第一个 item 处显示分类标题
                            if (index == 0 && category.name.isNotBlank()) {
                                SmallTitle(
                                    text = category.name,
                                    insideMargin = PaddingValues(start = 28.dp, end = 28.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

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
            modifier = modifier
                .overScrollVertical(),
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

        // 计算当前选中的索引
        val selectedIndex by remember(listStylePref) {
            derivedStateOf { options.indexOfFirst { it.second == listStylePref }.coerceAtLeast(0) }
        }

        Box(modifier = Modifier.padding(end = 14.dp)) {
            IconButton(onClick = { showPopup.value = true }) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    imageVector = MiuixIcons.Useful.ImmersionMore,
                    contentDescription = stringResource(id = R.string.action_restart),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            ListPopup(
                show = showPopup,
                alignment = PopupPositionProvider.Align.TopRight,
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

    @Composable
    private fun ModuleCard(
        module: LyricModule,
        showTags: Boolean
    ) {
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

//        val backgroundBitmap = remember(module.packageInfo.packageName) {
//            AppCache.getBitmap(module.packageInfo.packageName)?.asImageBitmap()
//        }

        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 背景模糊图
//                if (backgroundBitmap != null) {
//                    Image(
//                        modifier = Modifier
//                            .matchParentSize()
//                            .blur(200.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
//                        bitmap = backgroundBitmap,
//                        contentDescription = null,
//                        contentScale = ContentScale.Crop
//                    )
//                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // 头部信息：图标 + 标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        module.packageInfo.applicationInfo?.let { appInfo ->
                            AsyncAppIcon(
                                application = appInfo,
                                modifier = Modifier.size(40.dp)
                            )
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

                    // 描述信息
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

                    // 标签组
                    if (module.tags.isNotEmpty() && showTags) {
                        ModuleTagsFlow(module.tags)
                    }
                }
            }
        }
    }

    @Composable
    private fun ModuleTagsFlow(tags: List<ModuleTag>) {
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxLines = 10
        ) {
            tags.forEachIndexed { index, tag ->
                val title =
                    if (tag.titleRes != -1) stringResource(tag.titleRes) else tag.title.orEmpty()

                Card(
                    modifier = Modifier.padding(
                        end = if (index == tags.size - 1) 0.dp else 10.dp,
                        top = 10.dp
                    ),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surface.copy(alpha = 1f)
                    ),
                    onClick = {},
                    showIndication = true
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconSize = 18.dp
                        val iconTint = MiuixTheme.colorScheme.onSurfaceVariantSummary

                        when {
                            tag.iconRes != null -> Icon(
                                modifier = Modifier.size(iconSize),
                                painter = painterResource(tag.iconRes),
                                contentDescription = null,
                                tint = iconTint
                            )

                            tag.imageVector != null -> Image(
                                modifier = Modifier.size(iconSize),
                                imageVector = tag.imageVector,
                                contentDescription = null,
                            )
                        }

                        if (tag.iconRes != null || tag.imageVector != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        if (tag.isRainbow) {
                            GoogleRainbowText(
                                text = title,
                                style = MiuixTheme.textStyles.body2.copy(
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                            )
                        } else {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}