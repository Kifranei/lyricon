/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.android.extensions.json
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.custom.miuix.basic.Card
import io.github.proify.lyricon.app.util.launchBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LicensesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val licensesState = produceState(emptyList()) {
                value = withContext(Dispatchers.IO) { loadLicenses() }
            }
            Content(licensesState.value)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadLicenses(): List<OpenSourceLibrary> {
        return try {
            assets.open("open_source_licenses.json").use {
                json.decodeFromStream<List<OpenSourceLibrary>>(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Composable
    private fun Content(sourceLibraries: List<OpenSourceLibrary>) {
        AppToolBarListContainer(
            title = stringResource(R.string.activity_open_source_license),
            canBack = true,
        ) {
            items(
                items = sourceLibraries,
                key = { it.toString() }
            ) { library ->
                val sourceLibrary = remember(library) {
                    library
                }
                val developers = sourceLibrary.developers
                val url = sourceLibrary.url
                val description = sourceLibrary.description
                val year = sourceLibrary.version
                val project = sourceLibrary.project
                val licenses = sourceLibrary.licenses

                val indication = LocalIndication.current
                val toolbarColor = MiuixTheme.colorScheme.surface.toArgb()

                val clickableModifier = if (url.isNullOrBlank()) Modifier else Modifier.clickable(
                    indication = indication,
                    interactionSource = null,
                    onClick = {
                        launchBrowser(url, toolbarColor)
                    }
                )

                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(clickableModifier)
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                modifier = Modifier
                                    .weight(1f),
                                text = project.orEmpty(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            if (!year.isNullOrEmpty()) {
                                Text(
                                    modifier = Modifier.padding(start = 16.dp),
                                    text = year,
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }

                        if (!developers.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = developers.joinToString(),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }

                        if (!description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = description,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }

                        if (!licenses.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = licenses
                                        .filter { !it.name.isNullOrBlank() }
                                        .joinToString { it.name.orEmpty() },
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    @Serializable
    private data class LicenseInfo(
        val name: String? = null,
        val url: String? = null
    )

    @Serializable
    private data class OpenSourceLibrary(
        val project: String? = null,
        val description: String? = null,
        val version: String? = null,
        val developers: List<String>? = null,
        val url: String? = null,
        val year: String? = null,
        val licenses: List<LicenseInfo>? = null,
        val dependency: String? = null
    )
}