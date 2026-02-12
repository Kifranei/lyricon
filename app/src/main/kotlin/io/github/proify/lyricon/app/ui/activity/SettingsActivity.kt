/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.AppBackup
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperSwitch
import io.github.proify.lyricon.app.event.SettingChangedEvent
import io.github.proify.lyricon.app.updateRemoteLyricStyle
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private val settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val backupExportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            uri ?: return@registerForActivityResult
            applySettingsWithNotify {
                contentResolver.openOutputStream(uri)?.let { output ->
                    AppBackup.export(output)
                }
            }
        }

    private val backupImportLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri ?: return@registerForActivityResult
            applySettingsWithNotify {
                contentResolver.openInputStream(uri)?.let { input ->
                    AppBackup.restore(input)
                }
            }
        }

    private fun applySettingsWithNotify(
        task: CoroutineScope.() -> Unit
    ) {
        settingsScope.launch {
            task()
            withContext(Dispatchers.Main) {
                EventBus.post(SettingChangedEvent)
                updateRemoteLyricStyle()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                onSettingsApplied = ::restartSelf,
                onBackupExport = { backupExportLauncher.launch("lyricon_backup.bin") },
                onBackupImport = { backupImportLauncher.launch(arrayOf("*/*")) }
            )
        }
    }

    private fun restartSelf() {
        EventBus.post(SettingChangedEvent)
        startActivity(Intent(this, SettingsActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }


    @Composable
    private fun SettingsScreen(
        onSettingsApplied: () -> Unit,
        onBackupExport: () -> Unit,
        onBackupImport: () -> Unit
    ) {
        AppToolBarListContainer(
            title = stringResource(R.string.activity_settings),
            canBack = true
        ) {
            item("language") {
                SettingsSectionCard {
                    LanguageSetting(onSettingsApplied)
                }
            }
            item("theme") {
                SettingsSectionCard(topPadding = 16.dp) {
                    ThemeSetting(onSettingsApplied)
                }
            }
            item("backup") {
                SettingsSectionCard(topPadding = 16.dp) {
                    BackupSetting(onBackupExport, onBackupImport)
                }
            }
        }
    }

    @Composable
    private fun BackupSetting(
        onExport: () -> Unit,
        onImport: () -> Unit
    ) {
        SuperArrow(
            startAction = { IconActions(painterResource(R.drawable.ic_save)) },
            title = stringResource(R.string.item_app_backup),
            onClick = onExport
        )
        SuperArrow(
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
            SuperSwitch(
                startAction = { IconActions(painterResource(R.drawable.ic_palette)) },
                title = stringResource(R.string.item_app_theme_monet_color),
                checked = monetEnabled,
                onCheckedChange = {
                    AppThemeUtils.setEnableMonet(context, it)
                    onApplied()
                }
            )
        }

        val currentThemeMode = remember {
            AppThemeUtils.getMode(context)
        }

        val selectedIndex = remember(currentThemeMode) {
            themeModeOptions.indexOfFirst { it.second == currentThemeMode }
                .coerceAtLeast(0)
        }

        SuperDropdown(
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

        SuperSpinner(
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

    private fun Context.resolveLanguageName(
        languageCode: String,
        displayLocale: Locale? = null
    ): String {
        if (languageCode == AppLangUtils.DEFAULT_LANGUAGE) {
            return getString(R.string.option_language_follow_system)
        }
        return runCatching {
            val locale = Locale.forLanguageTag(languageCode)
            locale.getDisplayName(displayLocale ?: locale)
                .replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                }
        }.getOrDefault(languageCode)
    }
}