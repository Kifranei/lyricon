/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperDialog
import io.github.proify.lyricon.app.compose.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.InputPreference
import io.github.proify.lyricon.app.compose.preference.InputType
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.TextColorPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.lyric.style.TextStyle
import io.github.proify.lyricon.lyric.style.TextStyle.Companion.KEY_AI_TRANSLATION_API_KEY
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.CheckboxLocation
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun TextPage(scrollBehavior: ScrollBehavior, preferences: SharedPreferences) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        item(key = "base") {
            SmallTitle(
                text = stringResource(R.string.basic),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 0.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_size",
                    title = stringResource(R.string.item_text_size),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                RectInputPreference(
                    preferences,
                    "lyric_style_text_margins",
                    stringResource(R.string.item_text_margins),
                    defaultValue = TextStyle.Defaults.MARGINS,
                    leftAction = { IconActions(painterResource(R.drawable.ic_margin)) },
                )
                RectInputPreference(
                    preferences,
                    "lyric_style_text_paddings",
                    stringResource(R.string.item_text_paddings),
                    defaultValue = TextStyle.Defaults.PADDINGS,
                    leftAction = { IconActions(painterResource(R.drawable.ic_padding)) },
                )

                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_size_ratio_in_multi_line_mode",
                    title = stringResource(R.string.item_text_size_scale_multi_line),
                    defaultValue = TextStyle.Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE.toString(),
                    inputType = InputType.DOUBLE,
                    minValue = 0.1,
                    maxValue = 1.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                TransitionConfigPreference(preferences)

                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_fading_edge_length",
                    title = stringResource(R.string.item_text_fading_edge_length),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )
                SwitchPreference(
                    preferences,
                    "lyric_style_text_gradient_progress_style",
                    defaultValue = TextStyle.Defaults.ENABLE_GRADIENT_PROGRESS_STYLE,
                    title = stringResource(R.string.item_text_fading_style),
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )
                PlaceholderFormatPreference(preferences)
            }
        }
        item(key = "color") {
            SmallTitle(
                text = stringResource(R.string.item_text_color),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                val extractCoverColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_extract_cover_color",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR
                )
                val customColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_enable_custom_color",
                    defaultValue = TextStyle.Defaults.ENABLE_CUSTOM_TEXT_COLOR
                )
                val rainbowColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_enable_rainbow_color",
                    defaultValue = TextStyle.Defaults.ENABLE_RAINBOW_TEXT_COLOR
                )

                SwitchPreference(
                    preferences,
                    "lyric_style_text_extract_cover_color",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR,
                    title = stringResource(R.string.item_text_extract_cover_color),
                    startAction = { IconActions(painterResource(R.drawable.colorize_24px)) },
                    onCheckedChange = {
                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_enable_custom_color", false)
                                putBoolean("lyric_style_text_enable_rainbow_color", false)
                            }
                        } else {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_extract_cover_gradient", false)
                            }
                        }
                    }
                )
                SwitchPreference(
                    preferences,
                    "lyric_style_text_extract_cover_gradient",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT,
                    title = stringResource(R.string.item_text_extract_cover_gradient),
                    startAction = { IconActions(painterResource(R.drawable.format_paint_24px)) },
                    enabled = extractCoverColorEnabled.value,
                    onCheckedChange = {
                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_enable_custom_color", false)
                                putBoolean("lyric_style_text_extract_cover_color", true)
                                putBoolean("lyric_style_text_enable_rainbow_color", false)
                            }
                        }
                    }
                )
                SwitchPreference(
                    preferences,
                    "lyric_style_text_enable_custom_color",
                    defaultValue = TextStyle.Defaults.ENABLE_CUSTOM_TEXT_COLOR,
                    title = stringResource(R.string.item_text_enable_custom_color),
                    startAction = { IconActions(painterResource(R.drawable.ic_palette)) },
                    onCheckedChange = {
                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_extract_cover_color", false)
                                putBoolean("lyric_style_text_extract_cover_gradient", false)
                                putBoolean("lyric_style_text_enable_rainbow_color", false)
                            }
                        }
                    }
                )
                SwitchPreference(
                    preferences = preferences,
                    key = "lyric_style_text_enable_rainbow_color",
                    defaultValue = TextStyle.Defaults.ENABLE_RAINBOW_TEXT_COLOR,
                    title = stringResource(R.string.item_text_enable_rainbow_color),
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                    onCheckedChange = {
                        if (it) {
                            preferences.editCommit {
                                putBoolean("lyric_style_text_enable_custom_color", false)
                                putBoolean("lyric_style_text_extract_cover_color", false)
                                putBoolean("lyric_style_text_extract_cover_gradient", false)
                            }
                        }
                    }
                )
                TextColorPreference(
                    preferences,
                    "lyric_style_text_rainbow_color_light_mode",
                    title = stringResource(R.string.item_text_color_light_mode),
                    leftAction = { IconActions(painterResource(R.drawable.ic_brightness7)) },
                    enabled = customColorEnabled.value && !rainbowColorEnabled.value,
                )
                TextColorPreference(
                    preferences,
                    "lyric_style_text_rainbow_color_dark_mode",
                    title = stringResource(R.string.item_text_color_dark_mode),
                    leftAction = { IconActions(painterResource(R.drawable.ic_darkmode)) },
                    enabled = customColorEnabled.value && !rainbowColorEnabled.value,
                )
            }
        }
        item(key = "font") {
            SmallTitle(
                text = stringResource(R.string.item_text_font),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_typeface",
                    title = stringResource(R.string.item_text_typeface),
                    startAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
                )

                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_weight",
                    title = stringResource(R.string.item_text_font_weight),
                    inputType = InputType.INTEGER,
                    maxValue = 1000.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
                )

                CheckboxPreference(
                    preferences,
                    key = "lyric_style_text_typeface_bold",
                    title = stringResource(R.string.item_text_typeface_bold),
                    startActions = { IconActions(painterResource(R.drawable.ic_formatbold)) },
                    checkboxLocation = CheckboxLocation.End
                )
                CheckboxPreference(
                    preferences,
                    key = "lyric_style_text_typeface_italic",
                    title = stringResource(R.string.item_text_typeface_italic),
                    startActions = { IconActions(painterResource(R.drawable.ic_format_italic)) },
                    checkboxLocation = CheckboxLocation.End
                )
            }
        }

        item(key = "item_text_syllable") {
            SmallTitle(
                text = stringResource(R.string.item_text_syllable),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                val customColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_enable_custom_color",
                    defaultValue = TextStyle.Defaults.ENABLE_CUSTOM_TEXT_COLOR
                )
                val extractCoverColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_extract_cover_color",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR
                )
                val extractCoverGradientEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_extract_cover_gradient",
                    defaultValue = TextStyle.Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT
                )
                val rainbowColorEnabled = rememberBooleanPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_enable_rainbow_color",
                    defaultValue = TextStyle.Defaults.ENABLE_RAINBOW_TEXT_COLOR
                )
                val colorModeEnabled = customColorEnabled.value
                        || extractCoverColorEnabled.value
                        || extractCoverGradientEnabled.value
                        || rainbowColorEnabled.value
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS,
                    preferences = preferences,
                    key = "lyric_style_text_relative_progress",
                    title = stringResource(R.string.item_text_relative_progress),
                    summary = stringResource(R.string.item_text_relative_progress_summary),
                    startAction = { IconActions(painterResource(R.drawable.ic_music_note)) },
                )
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS_HIGHLIGHT,
                    preferences = preferences,
                    key = "lyric_style_text_relative_progress_highlight",
                    title = stringResource(R.string.item_text_relative_progress_highlight),
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )
                InterludeIndicatorPreference(preferences)
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.SUSTAIN_LIFT_ENABLED,
                    preferences = preferences,
                    key = "lyric_style_text_sustain_lift",
                    title = stringResource(R.string.item_text_sustain_lift),
                    startAction = { IconActions(painterResource(R.drawable.ic_music_note)) },
                )
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.SUSTAIN_GLOW_ENABLED,
                    preferences = preferences,
                    key = "lyric_style_text_sustain_glow",
                    title = stringResource(R.string.item_text_sustain_glow),
                    summary = if (colorModeEnabled) {
                        stringResource(R.string.item_text_sustain_glow_color_mode_summary)
                    } else {
                        stringResource(R.string.item_text_sustain_glow_color_mode_hint)
                    },
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )
            }
        }

        item(key = "translation") {
            SmallTitle(
                text = stringResource(R.string.module_tag_translation),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )

            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_TEXT_TRANSLATION_DISABLE,
                    title = stringResource(R.string.item_translation_disable),
                    startAction = { IconActions(painterResource(R.drawable.ic_visibility_off)) },
                    onCheckedChange = { _ ->
//                        if (enabled) {
//                            preferences.editCommit {
//                                putBoolean("lyric_style_text_translation_only", false)
//                            }
//                        }
                    }
                )

                SwitchPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_TEXT_TRANSLATION_ONLY,
                    title = stringResource(R.string.item_translation_only),
                    startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
                    onCheckedChange = { _ ->
//                        if (enabled) {
//                            preferences.editCommit {
//                                putBoolean("lyric_style_text_hide_translation", false)
//                            }
//                        }
                    }
                )
            }

            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            ) {

                SwitchPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_AI_TRANSLATION_ENABLED,
                    defaultValue = TextStyle.Defaults.AI_TRANSLATION_ENABLED,
                    title = stringResource(R.string.item_translation_enable),
                    startAction = { IconActions(painterResource(R.drawable.translate_24px)) },
                )
                TranslationTargetLanguagePreference(preferences)

                TranslationApiKeyPreference(preferences)
                InputPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_AI_TRANSLATION_MODEL,
                    title = stringResource(R.string.item_translation_model),
                    defaultValue = TextStyle.Defaults.AI_TRANSLATION_MODEL,
                    startAction = { IconActions(painterResource(R.drawable.psychology_24px)) },
                )
                InputPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_AI_TRANSLATION_BASE_URL,
                    title = stringResource(R.string.item_translation_base_url),
                    defaultValue = TextStyle.Defaults.AI_TRANSLATION_HOST,
                    startAction = { IconActions(painterResource(R.drawable.link_24px)) },
                )
                InputPreference(
                    preferences = preferences,
                    key = TextStyle.KEY_AI_TRANSLATION_PROMPT,
                    title = stringResource(R.string.item_translation_custom_prompt),
                    defaultValue = TextStyle.Defaults.AI_TRANSLATION_PROMPT,
                    startAction = { IconActions(painterResource(R.drawable.title_24px)) },
                )

                ClearTranslationDB()
            }
        }

        item(key = "marquee") {
            SmallTitle(
                text = stringResource(R.string.item_text_marquee),
                insideMargin = PaddingValues(
                    start = 26.dp,
                    top = 16.dp,
                    end = 26.dp,
                    bottom = 10.dp
                )
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                    .fillMaxWidth(),
            ) {
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_speed",
                    title = stringResource(R.string.item_text_marquee_speed),
                    defaultValue = TextStyle.Defaults.MARQUEE_SPEED.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 500.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_speed)) },
                )
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_space",
                    title = stringResource(R.string.item_text_marquee_space),
                    defaultValue = TextStyle.Defaults.MARQUEE_GHOST_SPACING.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 1000.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_space_bar)) },
                )
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_initial_delay",
                    title = stringResource(R.string.item_text_marquee_initial_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_INITIAL_DELAY.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 3600000.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    isTimeUnit = true,
                )
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_loop_delay",
                    title = stringResource(R.string.item_text_marquee_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_LOOP_DELAY.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 3600000.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    isTimeUnit = true,
                )
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.MARQUEE_REPEAT_UNLIMITED,
                    preferences = preferences,
                    key = "lyric_style_text_marquee_repeat_unlimited",
                    title = stringResource(R.string.item_text_marquee_repeat_unlimited),
                    startAction = { IconActions(painterResource(R.drawable.ic_all_inclusive)) },
                )
                InputPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_repeat_count",
                    title = stringResource(R.string.item_text_marquee_repeat_count),
                    inputType = InputType.INTEGER,
                    minValue = 0.0,
                    maxValue = 3600000.0,
                    startAction = { IconActions(painterResource(R.drawable.ic_pin)) },
                )
                SwitchPreference(
                    preferences = preferences,
                    key = "lyric_style_text_marquee_stop_at_end",
                    title = stringResource(R.string.item_text_marquee_stop_at_end),
                    startAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
                )
            }
        }
    }
}

@Composable
private fun ClearTranslationDB() {
    val context = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    SuperDialog(
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
                    LyriconBridge
                        .with(context)
                        .to(PackageNames.SYSTEM_UI)
                        .key(AppBridgeConstants.REQUEST_CLEAR_TRANSLATION_DB)
                        .send()
                },
                modifier = Modifier.weight(1f),
            )
        }

    }
    SuperArrow(
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

    InputPreference(
        preferences = preferences,
        key = TextStyle.KEY_AI_TRANSLATION_TARGET_LANGUAGE,
        defaultValue = targetLanguageName,
        title = stringResource(R.string.item_translation_target_language),
        startAction = { IconActions(painterResource(R.drawable.ic_language)) },
    )
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

    InputPreference(
        preferences = preferences,
        key = KEY_AI_TRANSLATION_API_KEY,
        title = stringResource(R.string.item_translation_api_key),
        summary = summary,
        startAction = { IconActions(painterResource(R.drawable.vpn_key_24px)) },
    )
}

@Composable
private fun <T> DropdownPreference(
    preferences: SharedPreferences,
    preferenceKey: String,
    defaultValue: T,
    options: List<String>,
    values: List<T>,
    title: String,
    iconRes: Int = R.drawable.ic_settings
) {
    val currentValue = preferences.getString(preferenceKey, defaultValue.toString())
    var selectedIndex by remember(currentValue) {
        mutableIntStateOf(values.indexOfFirst { it.toString() == currentValue }.takeIf { it >= 0 }
            ?: 0)
    }

    SuperDropdown(
        startAction = { IconActions(painterResource(iconRes)) },
        title = title,
        items = options,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = {
            selectedIndex = it
            preferences.editCommit {
                putString(preferenceKey, values[it].toString())
            }
        }
    )
}

@Composable
private fun PlaceholderFormatPreference(preferences: SharedPreferences) {
    DropdownPreference(
        preferences = preferences,
        preferenceKey = "lyric_style_text_placeholder_format",
        defaultValue = TextStyle.PlaceholderFormat.NAME_ARTIST,
        options = listOf(
            stringResource(R.string.option_text_placeholder_format_none),
            stringResource(R.string.option_text_placeholder_format_name_artist),
            stringResource(R.string.option_text_placeholder_format_name),
        ),
        values = listOf(
            TextStyle.PlaceholderFormat.NONE,
            TextStyle.PlaceholderFormat.NAME_ARTIST,
            TextStyle.PlaceholderFormat.NAME,
        ),
        title = stringResource(R.string.item_text_placeholder_format),
        iconRes = R.drawable.title_24px
    )
}

@Composable
private fun TransitionConfigPreference(preferences: SharedPreferences) {
    DropdownPreference(
        preferences = preferences,
        preferenceKey = "lyric_style_text_transition_config",
        defaultValue = TextStyle.TRANSITION_CONFIG_SMOOTH,
        options = listOf(
            stringResource(R.string.option_text_transition_config_none),
            stringResource(R.string.option_text_transition_config_fast),
            stringResource(R.string.option_text_transition_config_smooth),
            stringResource(R.string.option_text_transition_config_slow)
        ),
        values = listOf(
            TextStyle.TRANSITION_CONFIG_NONE,
            TextStyle.TRANSITION_CONFIG_FAST,
            TextStyle.TRANSITION_CONFIG_SMOOTH,
            TextStyle.TRANSITION_CONFIG_SLOW
        ),
        title = stringResource(R.string.item_text_transition_config),
        iconRes = R.drawable.ic_speed
    )
}

@Composable
private fun InterludeIndicatorPreference(preferences: SharedPreferences) {
    DropdownPreference(
        preferences = preferences,
        preferenceKey = TextStyle.KEY_TEXT_INTERLUDE_INDICATOR_STYLE,
        defaultValue = TextStyle.Defaults.INTERLUDE_INDICATOR_STYLE,
        options = listOf(
            stringResource(R.string.option_text_interlude_indicator_none),
            stringResource(R.string.option_text_interlude_indicator_dots)
        ),
        values = listOf(
            TextStyle.InterludeIndicatorStyle.NONE,
            TextStyle.InterludeIndicatorStyle.DOTS
        ),
        title = stringResource(R.string.item_text_interlude_indicator),
        iconRes = R.drawable.ic_music_note
    )
}
