/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperCheckbox
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.lyric.style.AnimStyle
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun AnimPage(
    scrollBehavior: ScrollBehavior,
    sharedPreferences: SharedPreferences
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val registry = YoYoPresets.registry
        val keys = registry.keys.toList()
        var selectedId by rememberStringPreference(
            sharedPreferences, "lyric_style_anim_id",
            AnimStyle.Defaults.ID
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
            // .hazeSource(hazeState)
        ) {

            item("enable") {
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        sharedPreferences,
                        "lyric_style_anim_enable",
                        defaultValue = AnimStyle.Defaults.ENABLE,
                        startAction = {
                            IconActions(painterResource(R.drawable.masked_transitions_24px))
                        },
                        title = stringResource(R.string.item_logo_enable),
                    )
                }
            }

            item("list") {
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth(),
                ) {
                    val context = LocalContext.current
                    keys.forEach { key ->
                        SuperCheckbox(
                            title = YoYoTranslates.getLabel(context, key),
                            checked = selectedId == key,
                            onCheckedChange = {
                                selectedId = key
                            }
                        )
                    }
                }
            }
        }
    }
}