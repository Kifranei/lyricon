/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.page

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.custom.miuix.basic.Card
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.extra.IconActions
import io.github.proify.lyricon.app.compose.preference.CheckboxPreference
import io.github.proify.lyricon.app.compose.preference.InputPreference
import io.github.proify.lyricon.app.compose.preference.InputType
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.TextColorPreference
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.TextStyle
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun TextPage(scrollBehavior: ScrollBehavior, sharedPreferences: SharedPreferences) {

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
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_size",
                    title = stringResource(R.string.item_text_size),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                RectInputPreference(
                    sharedPreferences,
                    "lyric_style_text_margins",
                    stringResource(R.string.item_text_margins),
                    defaultValue = TextStyle.Defaults.MARGINS,
                    leftAction = { IconActions(painterResource(R.drawable.ic_margin)) },
                )
                RectInputPreference(
                    sharedPreferences,
                    "lyric_style_text_paddings",
                    stringResource(R.string.item_text_paddings),
                    defaultValue = TextStyle.Defaults.PADDINGS,
                    leftAction = { IconActions(painterResource(R.drawable.ic_padding)) },
                )

                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_size_ratio_in_multi_line_mode",
                    title = stringResource(R.string.item_text_size_scale_multi_line),
                    defaultValue = TextStyle.Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE.toString(),
                    inputType = InputType.DOUBLE,
                    minValue = 0.1,
                    maxValue = 1.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                TransitionConfigPreference(sharedPreferences)

                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_fading_edge_length",
                    title = stringResource(R.string.item_text_fading_edge_length),
                    inputType = InputType.DOUBLE,
                    maxValue = 100.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_size)) },
                )
                SwitchPreference(
                    sharedPreferences,
                    "lyric_style_text_gradient_progress_style",
                    defaultValue = TextStyle.Defaults.ENABLE_GRADIENT_PROGRESS_STYLE,
                    title = stringResource(R.string.item_text_fading_style),
                    leftAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
                )
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
                    sharedPreferences,
                    "lyric_style_text_enable_custom_color",
                    title = stringResource(R.string.item_text_enable_custom_color),
                    leftAction = { IconActions(painterResource(R.drawable.ic_palette)) },
                )
                TextColorPreference(
                    sharedPreferences,
                    "lyric_style_text_color_light_mode",
                    title = stringResource(R.string.item_text_color_light_mode),
                    defaultColor = Color.Black,
                    leftAction = { IconActions(painterResource(R.drawable.ic_brightness7)) },
                )
                TextColorPreference(
                    sharedPreferences,
                    "lyric_style_text_color_dark_mode",
                    title = stringResource(R.string.item_text_color_dark_mode),
                    defaultColor = Color.White,
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
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_typeface",
                    title = stringResource(R.string.item_text_typeface),
                    leftAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
                )

                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_weight",
                    title = stringResource(R.string.item_text_font_weight),
                    inputType = InputType.INTEGER,
                    maxValue = 1000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_fontdownload)) },
                )

                CheckboxPreference(
                    sharedPreferences,
                    key = "lyric_style_text_typeface_bold",
                    title = stringResource(R.string.item_text_typeface_bold),
                    leftAction = { IconActions(painterResource(R.drawable.ic_formatbold)) },
                )
                CheckboxPreference(
                    sharedPreferences,
                    key = "lyric_style_text_typeface_italic",
                    title = stringResource(R.string.item_text_typeface_italic),
                    leftAction = { IconActions(painterResource(R.drawable.ic_format_italic)) },
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
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_relative_progress",
                    title = stringResource(R.string.item_text_relative_progress),
                    summary = stringResource(R.string.item_text_relative_progress_summary),
                    leftAction = { IconActions(painterResource(R.drawable.ic_music_note)) },
                )
                SwitchPreference(
                    defaultValue = TextStyle.Defaults.RELATIVE_PROGRESS_HIGHLIGHT,
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_relative_progress_highlight",
                    title = stringResource(R.string.item_text_relative_progress_highlight),
                    leftAction = { IconActions(painterResource(R.drawable.ic_gradient)) },
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
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_marquee_speed",
                    title = stringResource(R.string.item_text_marquee_speed),
                    defaultValue = TextStyle.Defaults.MARQUEE_SPEED.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 500.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_speed)) },
                )
                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_marquee_space",
                    title = stringResource(R.string.item_text_marquee_space),
                    defaultValue = TextStyle.Defaults.MARQUEE_GHOST_SPACING.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 1000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_space_bar)) },
                )
//                SwitchPreference(
//                    currentSp,
//                    "lyric_style_text_marquee_enable_delay",
//                    title = "启用滚动延迟",
//                    leftAction = { IconActions(painterResource(R.drawable.ic_timer)) },
//                )
                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_marquee_initial_delay",
                    title = stringResource(R.string.item_text_marquee_initial_delay),
                    defaultValue = TextStyle.Defaults.MARQUEE_INITIAL_DELAY.toString(),
                    inputType = InputType.INTEGER,
                    maxValue = 3600000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_autopause)) },
                    isTimeUnit = true,
                )
                InputPreference(
                    sharedPreferences = sharedPreferences,
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
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_marquee_repeat_unlimited",
                    title = stringResource(R.string.item_text_marquee_repeat_unlimited),
                    leftAction = { IconActions(painterResource(R.drawable.ic_all_inclusive)) },
                )
                InputPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_marquee_repeat_count",
                    //defaultValue = TextStyle.Defaults.MARQUEE_REPEAT_COUNT.toString(),
                    title = stringResource(R.string.item_text_marquee_repeat_count),
                    inputType = InputType.INTEGER,
                    minValue = 0.0,
                    maxValue = 3600000.0,
                    leftAction = { IconActions(painterResource(R.drawable.ic_pin)) },
                )
                SwitchPreference(
                    sharedPreferences = sharedPreferences,
                    key = "lyric_style_text_marquee_stop_at_end",
                    title = stringResource(R.string.item_text_marquee_stop_at_end),
                    leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
                )
            }
        }
    }
}

@Composable
private fun TransitionConfigPreference(preferences: SharedPreferences) {
    val config = preferences.getString(
        "lyric_style_text_transition_config",
        TextStyle.TRANSITION_CONFIG_SMOOTH
    )

    val options = listOf(
        stringResource(R.string.option_text_transition_config_none),
        stringResource(R.string.option_text_transition_config_fast),
        stringResource(R.string.option_text_transition_config_smooth),
        stringResource(R.string.option_text_transition_config_slow)
    )
    val values = listOf(
        TextStyle.TRANSITION_CONFIG_NONE,
        TextStyle.TRANSITION_CONFIG_FAST,
        TextStyle.TRANSITION_CONFIG_SMOOTH,
        TextStyle.TRANSITION_CONFIG_SLOW
    )
    var selectedIndex by remember {
        mutableIntStateOf(
            values.indexOf(config)
        )
    }

    SuperDropdown(
        leftAction = { IconActions(painterResource(R.drawable.ic_speed)) },
        title = stringResource(R.string.item_text_transition_config),
        items = options,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = {
            selectedIndex = it
            preferences.editCommit {
                putString(
                    "lyric_style_text_transition_config",
                    values[it]
                )
            }
        }
    )
}