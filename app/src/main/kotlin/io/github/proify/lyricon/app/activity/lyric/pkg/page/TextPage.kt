/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.InputPreference
import io.github.proify.lyricon.app.compose.preference.InputType
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.TextColorPreference
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.TextStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
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
                    sharedPreferences = preferences,
                    key = "lyric_style_text_size",
                    title = stringResource(R.string.item_text_size),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
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
                    sharedPreferences = preferences,
                    key = "lyric_style_text_size_ratio_in_multi_line_mode",
                    title = stringResource(R.string.item_text_size_scale_multi_line),
                    defaultValue = TextStyle.Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE.toString(),
                    inputType = InputType.DOUBLE,
                    minValue = 0.1,
                    maxValue = 1.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                TransitionConfigPreference(preferences)

                InputPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_fading_edge_length",
                    title = stringResource(R.string.item_text_fading_edge_length),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
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
                SwitchPreference(
                    preferences,
                    "lyric_style_text_enable_custom_color",
                    title = stringResource(R.string.item_text_enable_custom_color),
                    startAction = { IconActions(painterResource(R.drawable.ic_palette)) },
                )
                TextColorPreference(
                    preferences,
                    "lyric_style_text_rainbow_color_light_mode",
                    title = stringResource(R.string.item_text_color_light_mode),
                    leftAction = { IconActions(painterResource(R.drawable.ic_brightness7)) },
                )
                TextColorPreference(
                    preferences,
                    "lyric_style_text_rainbow_color_dark_mode",
                    title = stringResource(R.string.item_text_color_dark_mode),
                    leftAction = { IconActions(painterResource(R.drawable.ic_darkmode)) },
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
                    sharedPreferences = preferences,
                    key = "lyric_style_text_typeface",
                    title = stringResource(R.string.item_text_typeface),
                    leftAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
                )

                InputPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_weight",
                    title = stringResource(R.string.item_text_font_weight),
                    inputType = InputType.INTEGER,
                    maxValue = 1000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
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
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS,
                    sharedPreferences = preferences,
                    key = "lyric_style_text_relative_progress",
                    title = stringResource(R.string.item_text_relative_progress),
                    summary = stringResource(R.string.item_text_relative_progress_summary),
                    startAction = { IconActions(painterResource(R.drawable.ic_music_note)) },
                )
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS_HIGHLIGHT,
                    sharedPreferences = preferences,
                    key = "lyric_style_text_relative_progress_highlight",
                    title = stringResource(R.string.item_text_relative_progress_highlight),
                    startAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )
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
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_speed",
                    title = stringResource(R.string.item_text_marquee_speed),
                    defaultValue = TextStyle.Defaults.MARQUEE_SPEED.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 500.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_speed)) },
                )
                InputPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_space",
                    title = stringResource(R.string.item_text_marquee_space),
                    defaultValue = TextStyle.Defaults.MARQUEE_GHOST_SPACING.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 1000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_space_bar)) },
                )
                InputPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_initial_delay",
                    title = stringResource(R.string.item_text_marquee_initial_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_INITIAL_DELAY.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 3600000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    isTimeUnit = true,
                )
                InputPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_loop_delay",
                    title = stringResource(R.string.item_text_marquee_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_LOOP_DELAY.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 3600000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    isTimeUnit = true,
                )
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.MARQUEE_REPEAT_UNLIMITED,
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_repeat_unlimited",
                    title = stringResource(R.string.item_text_marquee_repeat_unlimited),
                    startAction = { IconActions(painterResource(R.drawable.ic_all_inclusive)) },
                )
                InputPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_repeat_count",
                    title = stringResource(R.string.item_text_marquee_repeat_count),
                    inputType = InputType.INTEGER,
                    minValue = 0.0,
                    maxValue = 3600000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_pin)) },
                )
                SwitchPreference(
                    sharedPreferences = preferences,
                    key = "lyric_style_text_marquee_stop_at_end",
                    title = stringResource(R.string.item_text_marquee_stop_at_end),
                    startAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
                )
            }
        }
    }
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
    var selectedIndex by remember {
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