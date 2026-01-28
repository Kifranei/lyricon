/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused", "unused", "unused", "unused")

package io.github.proify.lyricon.app.ui.activity.lyric

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.GoogleRainbowText
import io.github.proify.lyricon.app.compose.custom.miuix.basic.BasicComponentDefaults
import io.github.proify.lyricon.app.compose.icon.GeminiColor
import io.github.proify.lyricon.app.ui.activity.BaseActivity
import io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.sheet.AppCache
import io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.sheet.AsyncAppIcon
import io.github.proify.lyricon.app.util.AnimationEmoji
import io.github.proify.lyricon.app.util.SignatureValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical


@Suppress("unused")
class LyricProviderActivity : BaseActivity() {

    private val viewModel: ProviderViewModel by viewModels()

    companion object {
        private val CERTIFIED_SIGNATURE = arrayOf(
            "d75a43f76dbe80d816046f952b8d0f5f7abd71c9bd7b57786d5367c488bd5816"
        )

        internal val tagCodeName: Map<String, Tag> by lazy {
            mapOf(
                $$"$syllable" to Tag(
                    imageVector = GeminiColor,
                    titleRes = R.string.module_tag_syllable,
                    rainbow = true
                ),
                $$"$translation" to Tag(
                    icon = R.drawable.translate_24px,
                    titleRes = R.string.module_tag_translation
                )
            )
        }
    }

    data class Tag(
        val icon: Int? = null,
        val imageVector: ImageVector? = null,
        val title: String? = null,
        @field:StringRes val titleRes: Int = -1,
        val rainbow: Boolean = false
    )

    data class PackageItem(
        val packageInfo: PackageInfo,
        val description: String?,
        val home: String?,
        val category: String?,
        val author: String?,
        val certified: Boolean,
        val tags: List<Tag>,
        val label: String
    )

    data class UiState(
        val allApps: List<PackageItem> = emptyList(),
        val isLoading: Boolean = false
    )

    class ProviderViewModel(application: android.app.Application) :
        AndroidViewModel(application) {

        private val packageManager: PackageManager = application.packageManager

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        init {
            loadProviders()
        }

        private fun loadProviders() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)

                withContext(Dispatchers.IO) {
                    // 获取所有已安装的包
                    val packageInfos = packageManager.getInstalledPackages(
                        PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
                    )

                    // 快速过滤出目标包
                    val targetPackages = packageInfos.filter { packageInfo ->
                        val applicationInfo = packageInfo.applicationInfo ?: return@filter false
                        val flags = applicationInfo.flags

                        if (flags and ApplicationInfo.FLAG_SYSTEM != 0
                            || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                        ) return@filter false

                        applicationInfo.metaData?.getBoolean("lyricon_module") == true
                    }

                    // 如果没有目标包,直接返回
                    if (targetPackages.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = UiState(allApps = emptyList(), isLoading = false)
                        }
                        return@withContext
                    }

                    // 分批处理,提高初始响应速度
                    val batchSize = 5
                    val allApps = mutableListOf<PackageItem>()

                    targetPackages.chunked(batchSize).forEach { batch ->
                        val batchResults = batch.mapNotNull { packageInfo ->
                            try {
                                processPackageInfo(packageInfo)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        allApps.addAll(batchResults)

                        // 每批次处理完后立即更新UI
                        withContext(Dispatchers.Main) {
                            _uiState.value = UiState(
                                allApps = allApps.toList(),
                                isLoading = allApps.size < targetPackages.size
                            )
                        }
                    }
                }
            }
        }

        private fun processPackageInfo(packageInfo: PackageInfo): PackageItem? {
            val applicationInfo = packageInfo.applicationInfo ?: return null
            val metaData = applicationInfo.metaData ?: return null

            val label = applicationInfo.loadLabel(packageManager).toString()
            AppCache.cacheLabel(packageInfo.packageName, label)

            if (!metaData.getBoolean("lyricon_module")) return null

            val description = metaData.getString("lyricon_module_description")
            val category = metaData.getString("lyricon_module_category")
            val home = metaData.getString("lyricon_module_home")
            val author = metaData.getString("lyricon_module_author")

            val certified = SignatureValidator.validateSignature(
                packageInfo,
                *CERTIFIED_SIGNATURE
            )

            val tags = extractTags(applicationInfo, metaData)

            if (AppCache.getCachedIcon(packageInfo.packageName) == null) {
                try {
                    applicationInfo.loadIcon(packageManager)?.let { icon ->
                        AppCache.cacheIcon(packageInfo.packageName, icon)
                    }
                } catch (_: Exception) {
                }
            }

            return PackageItem(
                packageInfo = packageInfo,
                description = description,
                home = home,
                category = category,
                author = author,
                certified = certified,
                tags = tags,
                label = label
            )
        }

        private fun extractTags(
            applicationInfo: ApplicationInfo,
            metaData: Bundle
        ): List<Tag> {
            val tagsID = metaData.getInt("lyricon_module_tags")

            val tagStrings = when {
                tagsID != 0 -> {
                    try {
                        val resources = packageManager.getResourcesForApplication(applicationInfo)
                        resources.getStringArray(tagsID).toList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                else -> {
                    metaData.getString("lyricon_module_tags")?.let { listOf(it) } ?: emptyList()
                }
            }

            return tagStrings.mapNotNull { tag ->
                if (tag.isBlank()) return@mapNotNull null
                tagCodeName[tag] ?: Tag(title = tag)
            }
        }
    }

    private data class AppCategory(
        val name: String,
        val items: List<PackageItem>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Content() }
    }

    @Composable
    private fun Content() {
        val state by viewModel.uiState.collectAsState()

        val categorized by remember {
            derivedStateOf { categorizeApps(state.allApps) }
        }

        var itemsVisible by remember { mutableStateOf(false) }

        LaunchedEffect(state.allApps) {
            if (state.allApps.isNotEmpty()) {
                itemsVisible = true
            }
        }

        AppToolBarListContainer(
            title = getString(R.string.activity_lyric_provider),
            showEmpty = false,
            canBack = true
        ) {
            // 加载状态
            if (state.isLoading) {
//                item(key = "loading") {
//                    Box(
//                        modifier = Modifier
//                            .fillParentMaxSize()
//                            .overScrollVertical(),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        LoadingIndicator(size = LoadingIndicatorSize.XL)
//                    }
//                }
                return@AppToolBarListContainer
            }

            if (state.allApps.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .overScrollVertical(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.Asset(
                                AnimationEmoji.getAssetsFile("Neutral-face")
                            )
                        )
                        LottieAnimation(
                            modifier = Modifier.size(100.dp),
                            composition = composition,
                            iterations = LottieConstants.IterateForever
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(stringResource(R.string.no_provider_available))
                    }
                }
                return@AppToolBarListContainer
            }

            categorized.forEach { category ->
                itemsIndexed(
                    items = category.items,
                    key = { _, item -> item.packageInfo.packageName }
                ) { index, item ->

                    AnimatedVisibility(
                        visible = itemsVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
//                        enter = fadeIn(
//                            animationSpec = tween(
//                                durationMillis = 300,
//                                delayMillis = index * 50
//                            )
//                        ) + slideInVertically(
//                            animationSpec = tween(
//                                durationMillis = 300,
//                                delayMillis = index * 50
//                            ),
//                            initialOffsetY = { it / 3 }
//                        ),
//                        exit = fadeOut(animationSpec = tween(200)) +
//                                slideOutVertically(animationSpec = tween(200))
                    ) {
                        Column {
                            if (index == 0 && category.name.isNotBlank()) {
                                SmallTitle(
                                    text = category.name,
                                    insideMargin = PaddingValues(28.dp, 0.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            ListItem(item)
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

    private fun categorizeApps(apps: List<PackageItem>): List<AppCategory> {
        if (apps.isEmpty()) return emptyList()

        val otherCategory = getString(R.string.other)
        val grouped = apps.groupBy { it.category ?: otherCategory }

        if (grouped.size == 1 && grouped.containsKey(otherCategory)) {
            return listOf(AppCategory("", grouped[otherCategory]!!))
        }

        return grouped.map { (name, items) ->
            AppCategory(name, items)
        }
    }

    @Composable
    private fun ListItem(item: PackageItem) {
        val titleColor = BasicComponentDefaults.titleColor()
        val summaryColor = BasicComponentDefaults.summaryColor()
        val unknown = stringResource(R.string.unknown)

        val info = remember(item.packageInfo.versionName, item.author) {
            getString(
                R.string.item_provider_info_secondary,
                item.packageInfo.versionName ?: unknown,
                getString(R.string.author),
                item.author ?: unknown
            )
        }

        val imageBitmap = remember(item.packageInfo.packageName) {
            AppCache.getBitmap(item.packageInfo.packageName)?.asImageBitmap()
        }

        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                imageBitmap?.let { bitmap ->
                    Image(
                        modifier = Modifier
                            .matchParentSize()
                            .blur(
                                200.dp,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                            ),
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item.packageInfo.applicationInfo?.let { appInfo ->
                            AsyncAppIcon(
                                application = appInfo,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = titleColor.color(true)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.certified) {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(R.drawable.verified_24px),
                                        contentDescription = null,
                                        tint = Color(0XFF66BB6A)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = info,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = summaryColor.color(true)
                                )
                            }
                        }
                    }

                    item.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Text(
                                modifier = Modifier.padding(top = 10.dp),
                                text = desc,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = summaryColor.color(true)
                            )
                        }
                    }

                    if (item.tags.isNotEmpty()) {
                        TagView(item.tags)
                    }
                }
            }
        }
    }

    @Composable
    private fun TagView(tags: List<Tag>) {
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxLines = 10
        ) {
            tags.forEachIndexed { index, tag ->
                val title = when {
                    tag.titleRes >= 0 -> stringResource(id = tag.titleRes)
                    else -> tag.title.orEmpty()
                }
                Card(
                    modifier = Modifier.padding(
                        end = if (index == tags.size - 1) 0.dp else 10.dp,
                        top = 10.dp
                    ),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surface.copy(
                            alpha = 0.65f
                        )
                    ),
                    onClick = {},
                    showIndication = true
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fontSize = 13.sp

                        if (tag.icon != null) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(tag.icon),
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (tag.imageVector != null) {
                            Image(
                                modifier = Modifier.size(18.dp),
                                imageVector = tag.imageVector,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (tag.rainbow) {
                            GoogleRainbowText(
                                text = title,
                                style = MiuixTheme.textStyles.body2.copy(
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = fontSize,
                                )
                            )
                        } else {
                            Text(
                                text = title,
                                fontSize = fontSize,
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