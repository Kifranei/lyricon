/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.custom.miuix.basic.Card
import io.github.proify.lyricon.app.compose.custom.miuix.extra.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.preference.InputPreference
import io.github.proify.lyricon.app.compose.preference.InputType
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.BasicStyle
import top.yukonga.miuix.kmp.extra.SpinnerEntry
import top.yukonga.miuix.kmp.extra.SuperSpinner

class BasicLyricStyleActivity : AbstractLyricActivity() {
    private val preferences by lazy { LyricPrefs.basicStylePrefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences.registerOnSharedPreferenceChangeListener(this)
        setContent { Content() }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current

        AppToolBarListContainer(
            title = stringResource(R.string.activity_base_lyric_style),
            canBack = true
        ) {
            item(key = "location") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    val anchor = rememberStringPreference(
                        preferences,
                        "lyric_style_base_anchor",
                        BasicStyle.Defaults.ANCHOR
                    )

                    SuperArrow(
                        title = stringResource(R.string.item_base_anchor),
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_locationon))
                        },
                        summary = anchor.value,
                        onClick = {
                            context.startActivity(
                                Intent(context, AnchorViewTreeActivity::class.java)
                            )
                        }
                    )

                    val insertionOrder = preferences.getInt(
                        "lyric_style_base_insertion_order",
                        BasicStyle.Defaults.INSERTION_ORDER
                    )

                    val selectedIndex = remember { mutableIntStateOf(0) }

                    val optionKeys = listOf(
                        BasicStyle.INSERTION_ORDER_BEFORE,
                        BasicStyle.INSERTION_ORDER_AFTER
                    )

                    val options = listOf(
                        SpinnerEntry(title = stringResource(R.string.item_base_insertion_before)),
                        SpinnerEntry(title = stringResource(R.string.item_base_insertion_after)),
                    )

                    optionKeys.forEachIndexed { index, key ->
                        if (insertionOrder == key) {
                            selectedIndex.intValue = index
                        }
                    }

                    SuperSpinner(
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_stack))
                        },
                        title = stringResource(R.string.item_base_insertion_order),
                        items = options,
                        selectedIndex = selectedIndex.intValue,
                        onSelectedIndexChange = {
                            selectedIndex.intValue = it
                            preferences.editCommit {
                                putInt(
                                    "lyric_style_base_insertion_order",
                                    optionKeys[it]
                                )
                            }
                        }
                    )

                    RectInputPreference(
                        preferences,
                        "lyric_style_base_margins",
                        stringResource(R.string.item_base_margins),
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_margin))
                        },
                    )

                    RectInputPreference(
                        preferences,
                        "lyric_style_base_paddings",
                        stringResource(R.string.item_base_paddings),
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_padding))
                        }
                    )


                    InputPreference(
                        sharedPreferences = preferences,
                        key = "lyric_style_base_width",
                        title = stringResource(R.string.item_base_width),
                        inputType = InputType.DOUBLE,
                        maxValue = 1000.0,
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_width_normal))
                        },
                    )

                    if (Utils.isOPlus) {
                        InputPreference(
                            sharedPreferences = preferences,
                            key = "lyric_style_base_width_in_coloros_capsule_mode",
                            title = stringResource(R.string.item_base_width_color_os_capsule),
                            inputType = InputType.DOUBLE,
                            maxValue = 1000.0,
                            leftAction = {
                                IconActions(painterResource(R.drawable.ic_width_normal))
                            },
                        )
                    }

                    SuperArrow(
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_visibility))
                        },
                        title = stringResource(R.string.item_config_view_rules),
                        onClick = {
                            context.startActivity(
                                Intent(context, ViewRulesTreeActivity::class.java)
                            )
                        }
                    )
                }
            }

            item(key = "visibility") {
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {

                    SwitchPreference(
                        preferences,
                        "lyric_style_base_hide_on_lock_screen",
                        defaultValue = BasicStyle.Defaults.HIDE_ON_LOCK_SCREEN,
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        },
                        title = stringResource(R.string.item_base_lockscreen_hidden),
                    )

                    HideWhenNoLyric()
                    HideWhenNoUpdate()
                }
            }

            item("bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun HideWhenNoLyric() {
        val hideWhenNoLyricAfterSeconds = rememberStringPreference(
            preferences,
            "lyric_style_base_hide_when_no_lyric_after_seconds",
            BasicStyle.Defaults.HIDE_WHEN_NO_LYRIC_AFTER_SECONDS.toString()
        )
        val hideWhenNoLyricAfterSecondsInt = remember(hideWhenNoLyricAfterSeconds.value) {
            hideWhenNoLyricAfterSeconds.value?.toLongOrNull()
                ?: BasicStyle.Defaults.HIDE_WHEN_NO_LYRIC_AFTER_SECONDS.toLong()
        }
        val hideWhenNoLyricSummary = remember(hideWhenNoLyricAfterSecondsInt) {
            hideWhenNoLyricAfterSecondsInt
        }.let { seconds ->
            if (seconds <= 0) {
                stringResource(R.string.option_no_lyric_timeout_never)
            } else null
        }

        InputPreference(
            sharedPreferences = preferences,
            key = "lyric_style_base_hide_when_no_lyric_after_seconds",
            title = stringResource(R.string.item_base_hide_when_no_lyric_after_seconds),
            inputType = InputType.INTEGER,
            maxValue = 3600000.0,
            summary = hideWhenNoLyricSummary,
            leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
            isTimeUnit = true,
            formatMultiplier = 1000
        )
    }

    @Composable
    private fun HideWhenNoUpdate() {
        val seconds = rememberStringPreference(
            preferences,
            "lyric_style_base_hide_when_no_update_after_seconds",
            BasicStyle.Defaults.HIDE_WHEN_NO_UPDATE_AFTER_SECONDS.toString()
        )
        val secondsInt = remember(seconds.value) {
            seconds.value?.toLong()
                ?: BasicStyle.Defaults.HIDE_WHEN_NO_UPDATE_AFTER_SECONDS.toLong()
        }
        val summary = remember(secondsInt) {
            secondsInt
        }.let { seconds ->
            if (seconds <= 0) {
                stringResource(R.string.option_no_update_timeout_never)
            } else null
        }

        InputPreference(
            sharedPreferences = preferences,
            key = "lyric_style_base_hide_when_no_update_after_seconds",
            title = stringResource(R.string.item_base_hide_when_no_update_after_seconds),
            inputType = InputType.INTEGER,
            maxValue = 3600000.0,
            summary = summary,
            leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
            isTimeUnit = true,
            formatMultiplier = 1000
        )
    }

    @Preview(showBackground = true)
    @Composable
    private fun ContentPreview() {
        Content()
    }
}