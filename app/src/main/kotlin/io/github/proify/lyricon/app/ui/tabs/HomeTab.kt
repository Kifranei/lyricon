package io.github.proify.lyricon.app.ui.tabs

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proify.lyricon.app.activity.lyric.provider.LyricProviderViewModel
import io.github.proify.lyricon.app.util.LyricPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.provider.LyricProviderActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.PackageStyleActivity
import io.github.proify.lyricon.app.activity.MainActivity.MainViewModel
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.common.PackageNames
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun HomeTab(
    model: MainViewModel,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    AppToolBarListContainer(
        title = stringResource(R.string.tab_home),
        actions = actions,
        canBack = false,
        bottomBar = bottomBar,
    ) {
        item("status_card") {
            val safeMode = model.safeMode.value
            HomeStatusCard(
                safeMode = safeMode,
                isModuleActive = model.isModuleActive.value,
            )
        }

        item("quick_actions") {
            HomeQuickActions()
        }

        item("system_info") {
            SystemInfoCard()
        }
    }
}

@Composable
private fun HomeStatusCard(safeMode: Boolean, isModuleActive: Boolean) {
    val inspectionMode = LocalInspectionMode.current
    // 激活状态以 libxposed XposedService 绑定为准（LSPosed 1.0.2 标准探活）；
    // AppBridge.isActive() 是恒 false 的占位自 hook 探针，模块侧从未实现对应 hook
    val isActive = isModuleActive || AppBridge.isActive() || inspectionMode

    val titleText = when {
        safeMode -> stringResource(id = R.string.module_status_system_ui_safe_mode)
        isActive -> stringResource(id = R.string.module_status_activated)
        else -> stringResource(id = R.string.module_status_not_activated)
    }

    val summaryText = stringResource(R.string.module_status_summary, BuildConfig.VERSION_NAME)
    val isDark = CurrentThemeConfigs.isDark

    val cardColor = when {
        safeMode || !isActive -> if (isDark) Color(0xFF402626) else Color(0xFFFDECEE)
        else -> if (isDark) Color(0xFF102819) else Color(0xFFDFFAE4)
    }

    val iconColor = when {
        safeMode || !isActive -> if (isDark) Color(0xFFD84242) else Color(0xFFE25B5B)
        else -> Color(0xFF36D167)
    }
    val titleColor = if (isDark) Color.White else Color(0xFF0F1B13)
    val summaryColor = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF2F4637)

    val iconVector = if (isActive && !safeMode) {
        ImageVector.vectorResource(id = R.drawable.ic_home_status_check)
    } else {
        ImageVector.vectorResource(id = R.drawable.ic_home_status_warning)
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = {},
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 34.dp),
            ) {
                Icon(
                    modifier = Modifier.size(128.dp),
                    imageVector = iconVector,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = summaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = summaryColor,
                )
            }
        }
    }
}

@Composable
private fun HomeQuickActions() {
    val context = LocalContext.current
    val providerModel: LyricProviderViewModel = viewModel()
    val providers by providerModel.groupedModules.collectAsState()
    val otherLabel = stringResource(R.string.other)
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var styleCount by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh, otherLabel) {
        // The model skips scanning when runtime permission has not been granted.
        providerModel.loadProviders(otherLabel)
        styleCount = withContext(Dispatchers.IO) {
            (LyricPrefs.getConfiguredPackageNames() + LyricPrefs.DEFAULT_PACKAGE_NAME).size
        }
    }
    val providerCount = if (providerModel.noQueryPermission || providerModel.showLoading) {
        "—"
    } else {
        providers.sumOf { it.items.size }.toString()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeShortcutCard(
            title = stringResource(R.string.tab_provider),
            count = providerCount,
            modifier = Modifier.weight(1f),
            onClick = { context.startActivity(Intent(context, LyricProviderActivity::class.java)) },
        )
        HomeShortcutCard(
            title = stringResource(R.string.item_package_style_manager),
            count = styleCount?.toString() ?: "—",
            modifier = Modifier.weight(1f),
            onClick = { context.startActivity(Intent(context, PackageStyleActivity::class.java)) },
        )
    }
}

@Composable
private fun HomeShortcutCard(
    title: String,
    count: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = count,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SystemInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            InfoText(
                title = stringResource(R.string.home_info_app_version),
                content = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                bottomPadding = 24.dp,
            )
            InfoText(
                title = stringResource(R.string.home_info_android_version),
                content = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                bottomPadding = 24.dp,
            )
            InfoText(
                title = stringResource(R.string.home_info_device),
                content = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { Build.DEVICE },
                bottomPadding = 24.dp,
            )
            InfoText(
                title = stringResource(R.string.home_info_system_build),
                content = Build.DISPLAY.ifBlank { Build.ID },
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun InfoText(
    title: String,
    content: String,
    bottomPadding: Dp,
) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
        text = content,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
    )
}
