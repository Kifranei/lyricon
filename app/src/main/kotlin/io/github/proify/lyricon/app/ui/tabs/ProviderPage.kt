package io.github.proify.lyricon.app.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.pkg.sheet.AsyncAppIcon
import io.github.proify.lyricon.app.activity.lyric.provider.LyricModule
import io.github.proify.lyricon.app.activity.lyric.provider.LyricProviderViewModel
import io.github.proify.lyricon.app.activity.lyric.provider.ModuleTag
import io.github.proify.lyricon.app.activity.lyric.provider.ViewMode
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
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import io.github.proify.lyricon.app.util.launchBrowser

@Composable
fun ProviderPage(
    viewModel: LyricProviderViewModel = viewModel(),
    bottomBar: @Composable () -> Unit = {}
) {
    val groupedModules by viewModel.groupedModules.collectAsState()
    val otherLabel = stringResource(R.string.other)

    LaunchedEffect(Unit) {
        viewModel.loadProviders(otherLabel)
    }

    AppToolBarListContainer(
        title = stringResource(R.string.tab_provider),
        canBack = false,
        showEmpty = false,
        bottomBar = bottomBar,
        actions = { DisplayOptionsAction(viewModel) }
    ) {
        if (viewModel.showLoading && groupedModules.isEmpty()) {
            item(key = "state_loading") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingStateView()
                }
            }
            return@AppToolBarListContainer
        }

        if (!viewModel.showLoading && groupedModules.isEmpty()) {
            item(key = "state_empty") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        modifier = Modifier,
                        noQueryPermission = viewModel.noQueryPermission
                    )
                }
            }
            return@AppToolBarListContainer
        }

        groupedModules.forEachIndexed { index, category ->
            if (category.name.isNotBlank()) {
                item(key = "header_${category.name}") {
                    SmallTitle(
                        text = category.name,
                        modifier = Modifier.animateItem(),
                        insideMargin = PaddingValues(
                            start = 28.dp,
                            end = 28.dp,
                            top = if (index > 0) 16.dp else 8.dp,
                            bottom = 8.dp
                        )
                    )
                }
            }

            itemsIndexed(
                items = category.items,
                key = { _, it -> it.packageInfo.packageName }
            ) { index, module ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index > 0) 16.dp else 0.dp)
                        .animateItem(
                            placementSpec = spring(stiffness = Spring.StiffnessLow)
                        )
                ) {
                    ModuleCard(
                        module = module,
                        showTags = viewModel.listStyle == ViewMode.FULL,
                        viewModel = viewModel
                    )
                }
            }
        }

        item(key = "footer_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun LoadingStateView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
        )
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
        Text(
            text = stringResource(
                if (noQueryPermission) R.string.no_query_app_permission
                else R.string.no_provider_available
            ),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun DisplayOptionsAction(viewModel: LyricProviderViewModel) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.defaultSharedPreferences }

    var listStylePref by rememberIntPreference(
        sharedPreferences,
        "activity_provider_list_style",
        ViewMode.SHORT
    )

    Row(modifier = Modifier.padding(end = 14.dp)) {
        IconButton(onClick = { context.launchBrowser(context.getString(R.string.provider_release_home)) }) {
            Icon(
                modifier = Modifier.size(26.dp),
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Changed from dropdown to a direct toggle for the "Expand" functionality as requested
        val isFullStyle = listStylePref == ViewMode.FULL
        IconButton(onClick = {
            val nextStyle = if (isFullStyle) ViewMode.SHORT else ViewMode.FULL
            listStylePref = nextStyle
            viewModel.listStyle = nextStyle
        }) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = if (isFullStyle) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                contentDescription = "Toggle Complete Expand",
                tint = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModuleCard(module: LyricModule, showTags: Boolean, viewModel: LyricProviderViewModel) {
    val context = LocalContext.current
    val titleColor = BasicComponentDefaults.titleColor()
    val summaryColor = BasicComponentDefaults.summaryColor()
    val unknownText = stringResource(R.string.unknown)

    val secondaryInfo = remember(module.packageInfo.versionName, module.author) {
        context.getString(
            R.string.item_provider_info_secondary,
            module.packageInfo.versionName ?: unknownText,
            context.getString(R.string.author),
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
                    color = summaryColor.color(true),
                )
            }

            AnimatedVisibility(visible = module.tags.isNotEmpty() && showTags) {
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
