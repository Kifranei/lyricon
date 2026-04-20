package io.github.proify.lyricon.app.ui.tabs

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.AppBackup
import io.github.proify.lyricon.app.AppConstants
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.MainActivity
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.GoogleRainbowText
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.event.SettingChangedEvent
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.EventBus
import io.github.proify.lyricon.app.util.launchBrowser
import io.github.proify.lyricon.app.util.resolveLanguageName
import io.github.proify.lyricon.app.util.updateRemoteLyricStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsPage(bottomBar: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val applySettingsWithNotify: (suspend () -> Unit) -> Unit = { task ->
        coroutineScope.launch(Dispatchers.IO) {
            task()
            withContext(Dispatchers.Main) {
                EventBus.post(SettingChangedEvent)
                updateRemoteLyricStyle()
            }
        }
    }

    val restartSelf = {
        EventBus.post(SettingChangedEvent)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        applySettingsWithNotify {
            context.contentResolver.openOutputStream(uri)?.let { output ->
                AppBackup.export(output)
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        applySettingsWithNotify {
            context.contentResolver.openInputStream(uri)?.let { input ->
                AppBackup.restore(input)
            }
        }
    }

    AppToolBarListContainer(
        title = stringResource(R.string.tab_settings),
        canBack = false,
        bottomBar = bottomBar
    ) {
        
        item("ui_theme") {
            SettingsSectionCard(topPadding = 16.dp) {
                ThemeSetting(restartSelf)
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp, end = 26.dp), color = MiuixTheme.colorScheme.surfaceVariant)
                FloatingBarAndLiquidGlassSetting()
            }
        }

        item("language") {
            SettingsSectionCard(topPadding = 16.dp) {
                LanguageSetting(restartSelf)
            }
        }

        item("core_service") {
            SettingsSectionCard(topPadding = 16.dp) {
                CoreServiceSetting()
            }
        }

        item("backup") {
            SettingsSectionCard(topPadding = 16.dp) {
                BackupSetting(
                    onExport = { backupExportLauncher.launch("lyricon_backup.bin") },
                    onImport = { backupImportLauncher.launch(arrayOf("*/*")) }
                )
            }
        }

        item("about") {
            Spacer(Modifier.height(16.dp))
            AboutSection()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val isEnableMonet = AppThemeUtils.isEnableMonet(context)
    val buildTimeFormat = remember {
        Instant.ofEpochMilli(BuildConfig.BUILD_TIME)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(AppLangUtils.getLocale())
            )
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        val drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)

        Box(modifier = Modifier.fillMaxWidth()) {
            if (Build.VERSION.SDK_INT >= 31 && !isEnableMonet) {
                Row(modifier = Modifier.matchParentSize().blur(150.dp)) {
                    Image(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .scale(2f)
                            .rotate(40.toFloat()),
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            (if (isEnableMonet) MiuixTheme.colorScheme.primaryVariant
                            else AppConstants.APP_COLOR).copy(alpha = 0.4f)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    modifier = Modifier.size(64.dp),
                    painter = rememberDrawablePainter(drawable),
                    contentDescription = null,
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.height(16.dp))

                GoogleRainbowText(
                    text = stringResource(id = R.string.app_name),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                )
                
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = stringResource(
                        id = R.string.item_app_version_summary,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toString(),
                        BuildConfig.BUILD_TYPE
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp
                )

                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = buildTimeFormat,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        startAction = { IconActions(painterResource(R.drawable.ic_github)) },
                        title = stringResource(id = R.string.item_view_on_github),
                        summary = stringResource(id = R.string.github_home),
                        onClick = {
                            context.launchBrowser(context.getString(R.string.github_home))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 54.dp, end = 26.dp), color = MiuixTheme.colorScheme.surfaceVariant)
                    ArrowPreference(
                        startAction = { IconActions(painterResource(R.drawable.ic_license)) },
                        title = stringResource(id = R.string.item_open_source_license),
                        onClick = {
                            context.startActivity(Intent(context, io.github.proify.lyricon.app.activity.LicensesActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreServiceSetting() {
    val context = LocalContext.current
    var enable by rememberBooleanPreference(
        context.defaultSharedPreferences,
        "core_service_disable",
        false
    )

    SwitchPreference(
        checked = enable,
        startAction = { IconActions(painterResource(R.drawable.ic_core_bear)) },
        title = stringResource(R.string.item_core_service_disable),
        summary = stringResource(R.string.item_core_service_disable_summary),
        onCheckedChange = {
            enable = it
        }
    )
}

@Composable
private fun FloatingBarAndLiquidGlassSetting() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.defaultSharedPreferences }
    
    // 悬浮底栏设置
    var floatingBarEnabled by rememberBooleanPreference(
        sharedPreferences,
        "enable_floating_nav_bar",
        false
    )
    
    // 液态玻璃设置
    var liquidGlassEnabled by rememberBooleanPreference(
        sharedPreferences,
        "enable_liquid_glass",
        false
    )

    SwitchPreference(
        checked = floatingBarEnabled,
        startAction = { IconActions(painterResource(R.drawable.ic_extension)) },
        title = stringResource(R.string.item_floating_bottom_bar),
        summary = stringResource(R.string.item_summary_floating_bottom_bar),
        onCheckedChange = { isChecked ->
            floatingBarEnabled = isChecked
            if (!isChecked) {
                liquidGlassEnabled = false
            }
            EventBus.post(SettingChangedEvent)
        }
    )
    
    HorizontalDivider(modifier = Modifier.padding(start = 54.dp, end = 26.dp), color = MiuixTheme.colorScheme.surfaceVariant)

    SwitchPreference(
        checked = liquidGlassEnabled,
        startAction = { IconActions(painterResource(R.drawable.ic_palette)) },
        title = stringResource(R.string.item_liquid_glass),
        summary = stringResource(R.string.item_summary_liquid_glass),
        enabled = floatingBarEnabled,
        onCheckedChange = {
            liquidGlassEnabled = it
            EventBus.post(SettingChangedEvent)
        }
    )
}

@Composable
private fun BackupSetting(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    ArrowPreference(
        startAction = { IconActions(painterResource(R.drawable.ic_save)) },
        title = stringResource(R.string.item_app_backup),
        onClick = onExport
    )
    HorizontalDivider(modifier = Modifier.padding(start = 54.dp, end = 26.dp), color = MiuixTheme.colorScheme.surfaceVariant)
    ArrowPreference(
        startAction = { IconActions(painterResource(R.drawable.ic_settings_backup_restore)) },
        title = stringResource(R.string.item_app_restore),
        onClick = onImport
    )
}

@Composable
private fun SettingsSectionCard(
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(start = 16.dp, top = topPadding, end = 16.dp)
            .fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun ThemeSetting(onApplied: () -> Unit) {
    val context = LocalContext.current

    val themeModeOptions = listOf(
        R.string.option_app_theme_mode_system to AppThemeUtils.MODE_SYSTEM,
        R.string.option_app_theme_mode_light to AppThemeUtils.MODE_LIGHT,
        R.string.option_app_theme_mode_dark to AppThemeUtils.MODE_DARK
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val monetEnabled = remember {
            AppThemeUtils.isEnableMonet(context)
        }
        SwitchPreference(
            startAction = { IconActions(painterResource(R.drawable.ic_palette)) },
            title = stringResource(R.string.item_app_theme_monet_color),
            checked = monetEnabled,
            onCheckedChange = {
                AppThemeUtils.setEnableMonet(context, it)
                onApplied()
            }
        )
        HorizontalDivider(modifier = Modifier.padding(start = 54.dp, end = 26.dp), color = MiuixTheme.colorScheme.surfaceVariant)
    }

    val currentThemeMode = remember {
        AppThemeUtils.getMode(context)
    }

    val selectedIndex = remember(currentThemeMode) {
        themeModeOptions.indexOfFirst { it.second == currentThemeMode }
            .coerceAtLeast(0)
    }

    OverlayDropdownPreference(
        startAction = { IconActions(painterResource(R.drawable.ic_routine)) },
        title = stringResource(R.string.item_app_theme_mode),
        items = themeModeOptions.map { stringResource(it.first) },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            if (index != selectedIndex) {
                AppThemeUtils.setMode(context, themeModeOptions[index].second)
                onApplied()
            }
        }
    )
}

@Composable
private fun LanguageSetting(onApplied: () -> Unit) {
    val context = LocalContext.current

    val languageCodes = remember {
        buildList {
            addAll(getSupportedLanguageCodes().sorted())
            add(0, AppLangUtils.DEFAULT_LANGUAGE)
        }
    }

    val currentLanguage = remember {
        AppLangUtils.getCustomizeLang(context)
    }

    val spinnerItems = remember(languageCodes) {
        languageCodes.map { code ->
            val primaryName = context.resolveLanguageName(code)
            val fallbackName =
                context.resolveLanguageName(code, AppLangUtils.DEFAULT_LOCALE)
            SpinnerEntry(
                title = primaryName,
                summary = if (primaryName == fallbackName) null else fallbackName
            )
        }
    }

    val selectedIndex = remember(currentLanguage) {
        languageCodes.indexOf(currentLanguage).coerceAtLeast(0)
    }

    OverlaySpinnerPreference(
        startAction = { IconActions(painterResource(R.drawable.ic_language)) },
        title = stringResource(R.string.item_app_language),
        items = spinnerItems,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            AppLangUtils.saveCustomizeLanguage(context, languageCodes[index])
            onApplied()
        }
    )
}

private fun getSupportedLanguageCodes(): List<String> =
    LyriconApp.instance.resources.getStringArray(R.array.language_codes).toList()

