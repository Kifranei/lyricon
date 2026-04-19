package top.yukonga.miuix.kmp.extra

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun SuperSpinner(
    title: String,
    items: List<SpinnerEntry>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    startAction: @Composable (() -> Unit)? = null,
) {
    OverlaySpinnerPreference(
        startAction = startAction,
        title = title,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}
