package top.yukonga.miuix.kmp.extra

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun SuperDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    startAction: @Composable (() -> Unit)? = null,
) {
    OverlaySpinnerPreference(
        startAction = startAction,
        title = title,
        items = items.map { SpinnerEntry(title = it) },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}
