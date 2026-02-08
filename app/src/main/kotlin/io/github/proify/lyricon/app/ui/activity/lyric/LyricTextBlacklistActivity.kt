/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.custom.miuix.basic.Card
import io.github.proify.lyricon.app.event.SettingChangedEvent
import io.github.proify.lyricon.app.ui.activity.BaseActivity
import io.github.proify.lyricon.app.updateRemoteLyricStyle
import io.github.proify.lyricon.app.util.EventBus
import io.github.proify.lyricon.app.util.LyricPrefs
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LyricTextBlacklistActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Content() }
    }

    @Composable
    private fun Content() {
        val initialLines = remember { LyricPrefs.getLyricTextBlacklist() }
        var blacklistText by remember { mutableStateOf(initialLines.joinToString("\n")) }

        fun normalizeLines(text: String): List<String> =
            text.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        fun saveAndExit() {
            LyricPrefs.setLyricTextBlacklist(normalizeLines(blacklistText))
            EventBus.post(SettingChangedEvent)
            updateRemoteLyricStyle()
            finish()
        }

        AppToolBarListContainer(
            title = stringResource(R.string.activity_lyric_text_blacklist),
            canBack = true,
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.reset),
                        onClick = { blacklistText = "" },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(R.string.save),
                        onClick = { saveAndExit() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        ) {
            item("blacklist") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.item_summary_lyric_text_blacklist),
                        modifier = Modifier.padding(
                            PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)
                        )
                    )

                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(240.dp),
                        value = blacklistText,
                        onValueChange = { blacklistText = it },
                        singleLine = false
                    )

                    if (blacklistText.isBlank()) {
                        Text(
                            text = stringResource(R.string.hint_lyric_text_blacklist_placeholder),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}
