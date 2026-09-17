package io.github.proify.lyricon.app.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.BasicLyricSettingsScreen

@Composable
fun ConfigPage(bottomBar: @Composable () -> Unit = {}) {
    BasicLyricSettingsScreen(
        title = stringResource(R.string.tab_config),
        canBack = false,
        bottomBar = bottomBar,
    )
}
