/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.extra.OverlayDialog
import io.github.proify.lyricon.app.compose.custom.miuix.extra.WindowDialog
import io.github.proify.lyricon.app.compose.custom.miuix.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.DoubleInputPreference
import io.github.proify.lyricon.app.compose.preference.IntInputPreference
import io.github.proify.lyricon.app.compose.preference.StringInputPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.app.util.toast
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.style.TextStyle.Companion.KEY_AI_TRANSLATION_API_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.util.Locale

@Composable
fun AiTranslationPreference(preferences: SharedPreferences) {
    var isAiTranslationEnabled by rememberBooleanPreference(
        sharedPreferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_ENABLED,
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_ENABLED
    )
    SwitchPreference(
        checked = isAiTranslationEnabled,
        onCheckedChange = { isAiTranslationEnabled = it },
        title = stringResource(R.string.item_translation_openai),
        startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
    )

    var isAiTranslationAutoIgnoreChinese by rememberBooleanPreference(
        sharedPreferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_IGNORE_CHINESE,
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_IGNORE_CHINESE
    )
    SwitchPreference(
        checked = isAiTranslationAutoIgnoreChinese,
        onCheckedChange = { isAiTranslationAutoIgnoreChinese = it },
        title = stringResource(R.string.item_translation_auto_ignore_chinese),
        summary = stringResource(R.string.item_translation_auto_ignore_chinese_summary),
        startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
    )

    TranslationTargetLanguagePreference(preferences)

    StringInputPreference(
        preferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_BASE_URL,
        title = stringResource(R.string.item_translation_base_url),
        dialogSummary = stringResource(R.string.dialog_summary_translation_base_url),
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_HOST,
        startAction = { IconActions(painterResource(R.drawable.link_24px)) },
        maxLines = 1
    )

    TranslationApiKeyPreference(preferences)
    TranslationModelPreference(preferences)
    TranslationAdvancedOptionsPreference(preferences)

    StringInputPreference(
        preferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_PROMPT,
        title = stringResource(R.string.item_translation_custom_prompt),
        dialogSummary = stringResource(R.string.dialog_summary_translation_custom_prompt),
        defaultValue = TextStyle.Defaults.AI_TRANSLATION_PROMPT,
        startAction = { IconActions(painterResource(R.drawable.title_24px)) },
    )

    ClearTranslationDB()
}


@Composable
private fun TranslationAdvancedOptionsPreference(preferences: SharedPreferences) {
    var showSheet by remember { mutableStateOf(false) }

    ArrowPreference(
        title = stringResource(R.string.item_translation_advanced_options),
        summary = stringResource(R.string.item_translation_advanced_options_summary),
        startAction = { IconActions(painterResource(R.drawable.more_horiz_24px)) },
        holdDownState = showSheet,
        onClick = { showSheet = true }
    )

    if (showSheet) {
        OverlayBottomSheet(
            show = showSheet,
            title = stringResource(R.string.item_translation_advanced_options),
            onDismissRequest = { showSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surface,
            insideMargin = DpSize(0.dp, 0.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .overScrollVertical()
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        DoubleInputPreference(
                            preferences = preferences,
                            key = TextStyle.KEY_AI_TRANSLATION_TEMPERATURE,
                            title = stringResource(R.string.item_translation_temperature),
                            dialogSummary = stringResource(R.string.dialog_summary_translation_temperature),
                            defaultValue = TextStyle.Defaults.AI_TRANSLATION_TEMPERATURE.toDouble(),
                            range = 0.0..2.0,
                            startAction = { IconActions(painterResource(R.drawable.device_thermostat_24px)) },
                        )

                        DoubleInputPreference(
                            preferences = preferences,
                            key = TextStyle.KEY_AI_TRANSLATION_TOP_P,
                            title = stringResource(R.string.item_translation_top_p),
                            dialogSummary = stringResource(R.string.dialog_summary_translation_top_p),
                            defaultValue = TextStyle.Defaults.AI_TRANSLATION_TOP_P.toDouble(),
                            range = 0.0..1.0,
                            startAction = { IconActions(painterResource(R.drawable.discover_tune_24px)) },
                        )

                        IntInputPreference(
                            preferences = preferences,
                            key = TextStyle.KEY_AI_TRANSLATION_MAX_TOKENS,
                            title = stringResource(R.string.item_translation_max_tokens),
                            dialogSummary = stringResource(R.string.dialog_summary_translation_max_tokens),
                            defaultValue = TextStyle.Defaults.AI_TRANSLATION_MAX_TOKENS,
                            range = 0..200000,
                            summary = {
                                if (it == 0) {
                                    stringResource(R.string.item_translation_max_tokens_default)
                                } else {
                                    null
                                }
                            },
                            startAction = { IconActions(painterResource(R.drawable.token_24px)) },
                        )

                        DoubleInputPreference(
                            preferences = preferences,
                            key = TextStyle.KEY_AI_TRANSLATION_PRESENCE_PENALTY,
                            title = stringResource(R.string.item_translation_presence_penalty),
                            dialogSummary = stringResource(R.string.dialog_summary_translation_presence_penalty),
                            defaultValue = TextStyle.Defaults.AI_TRANSLATION_PRESENCE_PENALTY.toDouble(),
                            range = -2.0..2.0,
                            startAction = { IconActions(painterResource(R.drawable.do_not_disturb_on_24px)) },
                        )

                        DoubleInputPreference(
                            preferences = preferences,
                            key = TextStyle.KEY_AI_TRANSLATION_FREQUENCY_PENALTY,
                            title = stringResource(R.string.item_translation_frequency_penalty),
                            dialogSummary = stringResource(R.string.dialog_summary_translation_frequency_penalty),
                            defaultValue = TextStyle.Defaults.AI_TRANSLATION_FREQUENCY_PENALTY.toDouble(),
                            range = -2.0..2.0,
                            startAction = { IconActions(painterResource(R.drawable.lightbulb_2_24px)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationModelPreference(preferences: SharedPreferences) {
    val preferenceKey = TextStyle.KEY_AI_TRANSLATION_MODEL
    val defaultModel = TextStyle.Defaults.AI_TRANSLATION_MODEL
    var modelPreference by rememberStringPreference(preferences, preferenceKey, defaultModel)
    val currentModel = modelPreference ?: defaultModel
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val title = stringResource(R.string.item_translation_model)
    val apiKeyNotSetMessage = stringResource(R.string.item_translation_model_api_key_not_set)
    val noModelsMessage = stringResource(R.string.item_translation_model_empty)
    val unknownErrorMessage = stringResource(R.string.unknown)

    val showMsgDialog = remember { mutableStateOf(false) }
    var msgDialogTitle by remember { mutableStateOf("") }
    var msgDialogSummary by remember { mutableStateOf("") }

    @Composable
    fun MessageDialog(
        show: MutableState<Boolean>,
        title: String,
        summary: String,
    ) {
        WindowDialog(
            title = title,
            summary = summary,
            show = show.value,
            onDismissRequest = { show.value = false }
        ) {
            val dismiss = LocalDismissState.current
            TextButton(
                text = stringResource(R.string.ok),
                onClick = { dismiss?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
    MessageDialog(showMsgDialog, msgDialogTitle, msgDialogSummary)

    StringInputPreference(
        preferences = preferences,
        key = preferenceKey,
        title = title,
        dialogSummary = stringResource(R.string.dialog_summary_translation_model),
        defaultValue = defaultModel,
        startAction = { IconActions(painterResource(R.drawable.psychology_24px)) },
        maxLines = 1,
        endActions = {
            IconButton(
                onClick = {
                    if (isLoading) return@IconButton

                    val apiKey = preferences.getString(KEY_AI_TRANSLATION_API_KEY, null)
                    if (apiKey.isNullOrBlank()) {
                        toast(apiKeyNotSetMessage)
                        return@IconButton
                    }

                    val baseUrl = preferences.getString(
                        TextStyle.KEY_AI_TRANSLATION_BASE_URL,
                        TextStyle.Defaults.AI_TRANSLATION_HOST
                    ).orEmpty()

                    isLoading = true
                    coroutineScope.launch {
                        val result = fetchOpenAiModels(baseUrl, apiKey)
                        isLoading = false

                        result.onSuccess { fetchedModels ->
                            if (fetchedModels.isEmpty()) {
                                toast(noModelsMessage)
                            } else {
                                models = (fetchedModels + currentModel)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                showDialog = true
                            }
                        }.onFailure { error ->
                            val context = LyriconApp.get()

                            msgDialogTitle =
                                context.getString(R.string.title_translation_model_load_failed)
                            msgDialogSummary = error.message ?: unknownErrorMessage
                            showMsgDialog.value = true
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null
                    )
                }
            }
        }
    )

    if (showDialog) {
        WindowBottomSheet(
            show = showDialog,
            title = stringResource(R.string.dialog_title_available_models),
            onDismissRequest = { showDialog = false },
            backgroundColor = MiuixTheme.colorScheme.surface,
            insideMargin = DpSize(0.dp, 0.dp),
        ) {

            val dismiss = LocalDismissState.current
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .overScrollVertical()
            ) {
                itemsIndexed(
                    items = models,
                    key = { _, it -> it }
                ) { _, model ->

                    Card(
                        modifier =
                            Modifier
                                .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                                .fillMaxWidth()
                    ) {
                        CheckboxPreference(
                            title = model,
                            checked = currentModel == model,
                            onCheckedChange = {
                                modelPreference = model
                                dismiss?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun fetchOpenAiModels(
    baseUrl: String,
    apiKey: String
): Result<List<String>> = withContext(Dispatchers.IO) {
    runCatching {
        val modelsUrl = buildOpenAiModelsUrl(baseUrl)
        val connection = (URL(modelsUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15 * 1000
            readTimeout = 30 * 1000
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                error("HTTP $responseCode ${body.take(160)}".trim())
            }

            json.decodeFromString<OpenAiModelsResponse>(body)
                .data
                .map { it.id }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        } finally {
            connection.disconnect()
        }
    }
}

private fun buildOpenAiModelsUrl(baseUrl: String): String {
    val trimmedUrl = baseUrl.trim().removeSuffix("/")
    val normalizedBaseUrl = when {
        trimmedUrl.endsWith("/models") -> return trimmedUrl
        trimmedUrl.endsWith("/chat/completions") -> trimmedUrl.removeSuffix("/chat/completions")
        trimmedUrl.isBlank() -> TextStyle.Defaults.AI_TRANSLATION_HOST.removeSuffix("/")
        else -> trimmedUrl
    }

    return "$normalizedBaseUrl/models"
}

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModel> = emptyList()
)

@Serializable
private data class OpenAiModel(
    val id: String = ""
)

@Composable
private fun ClearTranslationDB() {
    val showDialog = remember { mutableStateOf(false) }
    OverlayDialog(
        title = stringResource(R.string.alert_dialog_title_translation_clear),
        summary = stringResource(R.string.alert_dialog_message_translation_clear),
        show = showDialog.value,
        onDismissRequest = { showDialog.value = false }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = stringResource(id = R.string.cancel),
                onClick = { showDialog.value = false },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                colors = ButtonDefaults.textButtonColorsPrimary(),
                text = stringResource(id = R.string.yes),
                onClick = {
                    showDialog.value = false
                    LyriconBridge.with(LyriconApp.get())
                        .to(PackageNames.SYSTEM_UI)
                        .key(AppBridgeConstants.REQUEST_CLEAR_TRANSLATION_DB)
                        .send()
                },
                modifier = Modifier.weight(1f),
            )
        }

    }
    ArrowPreference(
        title = stringResource(R.string.item_translation_clear_db),
        startAction = { IconActions(painterResource(R.drawable.ic_settings_backup_restore)) },
        onClick = {
            showDialog.value = true
        }
    )
}

@Composable
private fun TranslationTargetLanguagePreference(preferences: SharedPreferences) {
    val targetLanguageName = TextStyle.Defaults.AI_TRANSLATION_TARGET_LANGUAGE_DISPLAY_NAME
    var showLanguageSheet by remember { mutableStateOf(false) }
    @Suppress("VariableNeverRead") var targetLanguage by rememberStringPreference(
        preferences,
        TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE,
        targetLanguageName
    )
    var targetLanguageCode by rememberStringPreference(
        preferences,
        TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE_CODE,
        ""
    )

    StringInputPreference(
        preferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE,
        defaultValue = targetLanguageName,
        title = stringResource(R.string.item_translation_target_language),
        dialogSummary = stringResource(R.string.dialog_summary_translation_target_language),
        startAction = { IconActions(painterResource(R.drawable.ic_language)) },
        endActions = {
            IconButton(onClick = { showLanguageSheet = true }) {
                Icon(
                    painter = painterResource(R.drawable.list_24px),
                    contentDescription = stringResource(R.string.dialog_title_translation_languages)
                )
            }
        }
    )

    if (showLanguageSheet) {
        val displayLocale = LocalLocale.current.platformLocale
        val languageGroups =
            remember(displayLocale) { buildTranslationLanguageGroups(displayLocale) }
        var query by remember { mutableStateOf("") }
        var selectedLanguageCode by remember { mutableStateOf<String?>(null) }
        val filteredLanguageGroups = remember(languageGroups, query) {
            filterTranslationLanguageGroups(languageGroups, query)
        }
        val selectedLanguageGroup = remember(languageGroups, selectedLanguageCode) {
            languageGroups.firstOrNull { it.languageCode == selectedLanguageCode }
        }

        var isExpanded by remember { mutableStateOf(false) }

        WindowBottomSheet(
            show = showLanguageSheet,
            title = stringResource(R.string.dialog_title_translation_languages),
            onDismissRequest = { showLanguageSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surface,
            insideMargin = DpSize(0.dp, 0.dp),
            enableNestedScroll = false
        ) {
            val dismiss = LocalDismissState.current

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {

                SearchBar(
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            label = stringResource(R.string.hint_search),
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 0.dp)
                                .fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                                    imageVector = MiuixIcons.Search,
                                    contentDescription = stringResource(R.string.action_search),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                            onSearch = {
                                isExpanded = false
                            },
                            expanded = isExpanded,
                            onExpandedChange = {
                                isExpanded = it
                            },
                        )
                    },
                    onExpandedChange = {
                        isExpanded = it
                    },
                ) {}
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical()
                ) {


                    if (query.isBlank()) {
                        itemsIndexed(
                            items = languageGroups,
                            key = { _, group -> "language_${group.languageCode}" }
                        ) { _, group ->
                            Card(
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                                    .fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = group.name,
                                    summary = group.options
                                        .drop(1)
                                        .take(3)
                                        .joinToString(" / ") { it.name }
                                        .takeIf { it.isNotBlank() },
                                    onClick = { selectedLanguageCode = group.languageCode }
                                )
                            }
                        }
                    } else {
                        filteredLanguageGroups.forEach { group ->
                            item(key = "language_group_${group.languageCode}") {
                                SmallTitle(
                                    text = group.name,
                                    insideMargin = PaddingValues(
                                        start = 26.dp,
                                        end = 26.dp,
                                        bottom = 10.dp
                                    )
                                )
                            }

                            itemsIndexed(
                                items = group.options,
                                key = { _, option -> option.code }
                            ) { _, option ->
                                TranslationLanguageOptionPreference(
                                    option = option,
                                    checked = targetLanguageCode == option.code,
                                    onClick = {
                                        targetLanguage = option.applyLanguage
                                        targetLanguageCode = option.code
                                        dismiss?.invoke()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedLanguageGroup != null) {
            WindowBottomSheet(
                show = true,
                title = selectedLanguageGroup.name,
                onDismissRequest = { selectedLanguageCode = null },
                backgroundColor = MiuixTheme.colorScheme.surface,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                val dismiss = LocalDismissState.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical()
                ) {
                    itemsIndexed(
                        items = selectedLanguageGroup.options,
                        key = { _, option -> option.code }
                    ) { _, option ->
                        TranslationLanguageOptionPreference(
                            option = option,
                            checked = targetLanguageCode == option.code,
                            onClick = {
                                targetLanguage = option.applyLanguage
                                targetLanguageCode = option.code
                                selectedLanguageCode = null
                                dismiss?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationLanguageOptionPreference(
    option: TranslationLanguageOption,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
            .fillMaxWidth()
    ) {
//        val summary = listOfNotNull(option.nativeName)
//            .distinct()
//            .joinToString(" / ")
        CheckboxPreference(
            title = option.name,
            //summary = summary.takeIf { it.isNotBlank() },
            checked = checked,
            onCheckedChange = { onClick() }
        )
    }
}

private fun buildTranslationLanguageGroups(displayLocale: Locale): List<TranslationLanguageGroup> {
    val collator = Collator.getInstance(displayLocale)
    return systemLanguageTags()
        .asSequence()
        .map { Locale.forLanguageTag(it) }
        .filter { locale -> locale.language.isNotBlank() && locale.language != "und" }
        .distinctBy { it.toLanguageTag() }
        .map { locale ->
            val languageName = locale.getDisplayLanguage(displayLocale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString()
            }
            val name = locale.getDisplayName(displayLocale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString()
            }
            val nativeName = locale.getDisplayName(locale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }
            val englishName = locale.getDisplayName(Locale.ENGLISH).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
            }
            TranslationLanguageOption(
                code = locale.toLanguageTag(),
                languageCode = locale.language,
                languageName = languageName,
                name = name,
                nativeName = nativeName.takeIf { it.isNotBlank() && it != name },
                englishName = englishName.takeIf { it.isNotBlank() && it != name }
            )
        }
        .groupBy { it.languageCode }
        .map { (languageCode, options) ->
            TranslationLanguageGroup(
                languageCode = languageCode,
                name = options.first().languageName,
                options = options.sortedWith { left, right ->
                    collator.compare(
                        left.name,
                        right.name
                    )
                }
            )
        }
        .sortedWith { left, right -> collator.compare(left.name, right.name) }
        .toList()
}

private fun systemLanguageTags(): List<String> {
    val systemLocaleTags = Locale.getAvailableLocales()
        .map { it.toLanguageTag() }
        .asSequence()
        .map { it.replace('_', '-') }
        .filter { it.isNotBlank() }
        .toList()

    return systemLocaleTags
}

private fun filterTranslationLanguageGroups(
    groups: List<TranslationLanguageGroup>,
    query: String
): List<TranslationLanguageGroup> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isBlank()) return groups

    return groups.mapNotNull { group ->
        if (group.searchText.contains(normalizedQuery)) {
            group
        } else {
            val options = group.options.filter { it.searchText.contains(normalizedQuery) }
            if (options.isEmpty()) null else group.copy(options = options)
        }
    }
}

private data class TranslationLanguageGroup(
    val languageCode: String,
    val name: String,
    val options: List<TranslationLanguageOption>,
) {
    val searchText: String = listOf(languageCode, name)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
}

private data class TranslationLanguageOption(
    val code: String,
    val languageCode: String,
    val languageName: String,
    val name: String,
    val nativeName: String?,
    val englishName: String?,
) {

    val applyLanguage = name

    val searchText: String =
        listOfNotNull(code, languageCode, languageName, name, nativeName, englishName)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
}

@Composable
private fun TranslationApiKeyPreference(preferences: SharedPreferences) {
    val apiKey = rememberStringPreference(preferences, KEY_AI_TRANSLATION_API_KEY, null)
    val summary =
        if (apiKey.value.isNullOrBlank()) {
            stringResource(R.string.item_translation_api_key_not_set)
        } else {
            stringResource(R.string.item_translation_api_key_set)
        }

    StringInputPreference(
        preferences = preferences,
        key = KEY_AI_TRANSLATION_API_KEY,
        title = stringResource(R.string.item_translation_api_key),
        summary = summary,
        dialogSummary = stringResource(R.string.dialog_summary_translation_api_key),
        startAction = { IconActions(painterResource(R.drawable.vpn_key_24px)) },
        maxLines = 1
    )
}