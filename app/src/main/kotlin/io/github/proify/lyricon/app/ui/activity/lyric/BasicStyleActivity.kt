/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.BasicStyle
import top.yukonga.miuix.kmp.extra.SpinnerEntry
import top.yukonga.miuix.kmp.extra.SuperSpinner

class BasicLyricStyleActivity : BaseLyricActivity() {
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
        AppToolBarListContainer(
            title = stringResource(R.string.activity_base_lyric_style),
            canBack = true
        ) {
            item("content") {
                MainContent()
            }
        }
    }

    @Composable
    private fun MainContent() {
        val context = LocalContext.current
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

            InputPreference(
                sharedPreferences = preferences,
                key = "lyric_style_base_width",
                leftAction = {
                    IconActions(painterResource(R.drawable.ic_width_normal))
                },
                inputType = InputType.DOUBLE,
                maxValue = 1000.0,
                title = stringResource(R.string.item_base_width),
            )

            InputPreference(
                sharedPreferences = preferences,
                key = "lyric_style_base_width_in_coloros_capsule_mode",
                leftAction = {
                    IconActions(painterResource(R.drawable.ic_width_normal))
                },
                inputType = InputType.DOUBLE,
                maxValue = 1000.0,
                title = stringResource(R.string.item_base_width_color_os_capsule),
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
                },
            )
            SwitchPreference(
                preferences,
                "lyric_style_base_hide_on_lock_screen",
                defaultValue = BasicStyle.Defaults.HIDE_ON_LOCK_SCREEN,
                leftAction = {
                    IconActions(painterResource(R.drawable.ic_visibility_off))
                },
                title = stringResource(R.string.item_base_hide_on_lock_screen),
            )
        }

        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
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

    @Preview(showBackground = true)
    @Composable
    private fun ContentPreview() {
        Content()
    }
}