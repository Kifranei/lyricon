package io.github.proify.lyricon.app.ui.tabs

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.AppBackup
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.AboutActivity
import io.github.proify.lyricon.app.activity.MainActivity
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.event.SettingChangedEvent
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.EventBus
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
                FloatingBarSetting()
            }
        }

        item("language") {
            SettingsSectionCard(topPadding = 16.dp) {
                LanguageSetting(restartSelf)
            }
        }

        item("core_service") {
            SettingsSectionCard(topPadding = 16.dp) {
                DesktopIconSetting()
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp, end = 26.dp), color = MiuixTheme.colorScheme.surfaceVariant)
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

        item("about_entry") {
            SettingsSectionCard(topPadding = 16.dp) {
                ArrowPreference(
                    startAction = { IconActions(painterResource(R.drawable.ic_info)) },
                    title = stringResource(R.string.activity_about),
                    summary = stringResource(R.string.item_summary_about_app),
                    onClick = {
                        context.startActivity(Intent(context, AboutActivity::class.java))
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DesktopIconSetting() {
    val context = LocalContext.current
    val launcherAlias = remember {
        ComponentName(context, "io.github.proify.lyricon.app.activity.LauncherAlias")
    }
    var showDesktopIcon by rememberBooleanPreference(
        context.defaultSharedPreferences,
        "show_desktop_icon",
        true
    )

    SwitchPreference(
        checked = showDesktopIcon,
        startAction = { IconActions(painterResource(R.drawable.ic_android)) },
        title = stringResource(R.string.item_show_desktop_icon),
        summary = stringResource(R.string.item_show_desktop_icon_summary),
        onCheckedChange = { checked ->
            showDesktopIcon = checked
            context.packageManager.setComponentEnabledSetting(
                launcherAlias,
                if (checked) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
            if (!checked) {
                Toast.makeText(
                    context,
                    R.string.toast_desktop_icon_hidden,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
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
private fun FloatingBarSetting() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.defaultSharedPreferences }

    var floatingBarEnabled by rememberBooleanPreference(
        sharedPreferences,
        "enable_floating_nav_bar",
        false
    )

    SwitchPreference(
        checked = floatingBarEnabled,
        startAction = { IconActions(painterResource(R.drawable.ic_extension)) },
        title = stringResource(R.string.item_floating_bottom_bar),
        summary = stringResource(R.string.item_summary_floating_bottom_bar),
        onCheckedChange = {
            floatingBarEnabled = it
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
