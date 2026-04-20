package io.github.proify.lyricon.app.ui.tabs

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.BasicLyricStyleActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.PackageStyleActivity
import io.github.proify.lyricon.app.compose.ColoredIconBox
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.MaterialPalette
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun ConfigPage(
    isMonet: Boolean,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    AppToolBarListContainer(
        title = stringResource(R.string.tab_config),
        canBack = false,
        bottomBar = bottomBar
    ) {
        item("style_settings") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    startAction = {
                        ColoredIconBox(
                            Modifier,
                            MaterialPalette.Teal.Primary,
                            R.drawable.ic_android,
                            isMonet
                        )
                    },
                    title = stringResource(id = R.string.item_base_lyric_style),
                    summary = stringResource(id = R.string.item_summary_base_lyric_style),
                    onClick = {
                        context.startActivity(Intent(context, BasicLyricStyleActivity::class.java))
                    }
                )
                ArrowPreference(
                    startAction = {
                        ColoredIconBox(
                            Modifier.padding(2.dp),
                            MaterialPalette.Orange.Primary,
                            R.drawable.ic_palette_swatch_variant,
                            isMonet
                        )
                    },
                    title = stringResource(id = R.string.item_package_style_manager),
                    summary = stringResource(id = R.string.item_summary_package_style_manager),
                    onClick = {
                        context.startActivity(Intent(context, PackageStyleActivity::class.java))
                    }
                )
            }
        }
    }
}
