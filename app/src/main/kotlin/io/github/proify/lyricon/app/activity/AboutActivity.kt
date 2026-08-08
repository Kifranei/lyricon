/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import io.github.proify.lyricon.app.compose.theme.AppTheme
import io.github.proify.lyricon.app.ui.about.AboutScreen

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AboutScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }
}
