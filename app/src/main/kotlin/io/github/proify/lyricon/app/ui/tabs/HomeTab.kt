package io.github.proify.lyricon.app.ui.tabs

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.MainActivity.MainViewModel
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
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
        bottomBar = bottomBar
    ) {
        item("status_card") {
            val safeMode = model.safeMode.value
            HomeStatusCard(safeMode = safeMode, isMonet = model.isMonet)
        }

        item("system_info") {
            SystemInfoCard()
        }
    }
}

@Composable
private fun HomeStatusCard(safeMode: Boolean, isMonet: Boolean) {
    val inspectionMode = LocalInspectionMode.current
    val isActive = AppBridge.isModuleActive() || inspectionMode

    val titleText = when {
        safeMode -> stringResource(id = R.string.module_status_system_ui_safe_mode)
        isActive -> stringResource(id = R.string.module_status_activated)
        else -> stringResource(id = R.string.module_status_not_activated)
    }

    val summaryText = stringResource(R.string.module_status_summary, BuildConfig.VERSION_NAME)
    val isDark = isSystemInDarkTheme()

    val cardColor = when {
        safeMode || !isActive -> if (isDark) Color(0xFF4A1E20) else Color(0xFFFDECEE)
        isMonet -> MiuixTheme.colorScheme.secondaryContainer
        else -> if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
    }

    val iconColor = when {
        safeMode || !isActive -> if (isDark) Color(0xFFB3261E) else Color(0xFFE25B5B)
        isMonet -> MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> Color(0xFF36D167)
    }

    val iconVector = if (isActive && !safeMode) 
        androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_check_circle) 
    else 
        androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_sentiment_dissatisfied)

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = {}
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = 38.dp, y = 45.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = iconVector,
                    tint = iconColor,
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = summaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun SystemInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(
                title = stringResource(R.string.home_info_app_version),
                content = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                bottomPadding = 24.dp
            )
            InfoText(
                title = stringResource(R.string.home_info_android_version),
                content = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                bottomPadding = 0.dp
            )
        }
    }
}

@Composable
private fun InfoText(
    title: String,
    content: String,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface
    )
    Text(
        text = content,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding)
    )
}
