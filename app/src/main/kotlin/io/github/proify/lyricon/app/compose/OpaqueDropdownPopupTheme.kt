package io.github.proify.lyricon.app.compose

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Keeps popup surfaces opaque when the surrounding page uses translucent surface containers. */
@Composable
fun OpaqueDropdownPopupTheme(content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    MiuixTheme(
        colors = colors.copy(
            surfaceContainer = colors.surfaceContainer.copy(alpha = 1f),
            surfaceContainerHigh = colors.surfaceContainerHigh.copy(alpha = 1f),
            surfaceContainerHighest = colors.surfaceContainerHighest.copy(alpha = 1f),
            surfaceVariant = colors.surfaceVariant.copy(alpha = 1f),
        ),
        content = content,
    )
}
