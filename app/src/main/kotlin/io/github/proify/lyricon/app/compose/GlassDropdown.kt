package io.github.proify.lyricon.app.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPopupContent
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.WindowNavigationEventScope

/**
 * 供 OS4 玻璃下拉弹窗采样的页面 backdrop（miuix-blur）。由页面容器在顶栏渐进模糊生效时提供
 * （录制时垫了不透明页面底色）；为 null 时（流光背景、底部弹窗内、Android 13 以下）弹窗使用不透明填充。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * OS4 玻璃下拉弹窗，移植自 Halcyon 的 EllaGlassListPopup：在平台 [Popup] 中承载
 * [GlassDropdownPopupContent]，未指定锚点时以父布局边界为锚点，退出动画播放完毕前保持组合。
 */
@Composable
fun GlassListPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorBoundsOverride: Rect? = null,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    maxHeight: Dp = GlassPopupDefaults.MaxHeight,
    onDismissFinished: (() -> Unit)? = null,
    adjacent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var capturedAnchor by remember { mutableStateOf(Rect.Zero) }
    val anchorBounds = anchorBoundsOverride ?: capturedAnchor
    val latestOnDismissFinished by rememberUpdatedState(onDismissFinished)
    LaunchedEffect(show) { if (show) open = true }
    if (!open) return
    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchor: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    capturedAnchor = Rect(
                        anchor.left.toFloat(), anchor.top.toFloat(),
                        anchor.right.toFloat(), anchor.bottom.toFloat(),
                    )
                    return IntOffset(-anchor.left, -anchor.top)
                }
            }
        },
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismissRequest,
    ) {
        WindowNavigationEventScope {
            Box(Modifier.fillMaxSize()) {
                GlassDropdownPopupContent(
                    show = show,
                    onDismissRequest = onDismissRequest,
                    anchorBounds = anchorBounds,
                    backdrop = backdrop,
                    sizing = GlassPopupSizing(maxHeight = maxHeight),
                    modifier = modifier,
                    adjacent = adjacent,
                    onDismissFinished = {
                        open = false
                        latestOnDismissFinished?.invoke()
                    },
                    content = content,
                )
            }
        }
    }
}

/**
 * WindowDropdownPreference 的 OS4 版本（对应 Halcyon 的 EllaSpinnerPreference）：
 * 行布局与标准偏好项一致，选项列表为锚定在该行的玻璃下拉弹窗。
 */
@Composable
fun Os4DropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val actualEnabled = enabled && items.isNotEmpty()
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val hapticFeedback = LocalHapticFeedback.current
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val setExpanded: (Boolean) -> Unit = { next ->
        if (expanded != next) {
            expanded = next
            currentOnExpandedChange?.invoke(next)
        }
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInWindow() },
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val value = items.getOrNull(selectedIndex)
                if (showValue && !value.isNullOrEmpty()) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                    )
                }
                DropdownArrowEndAction(actionColor = actionColor)
            }
        },
        insideMargin = insideMargin,
        onClick = {
            setExpanded(!expanded)
            if (expanded) hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        role = Role.DropdownList,
        holdDownState = expanded,
        enabled = actualEnabled,
    )

    GlassListPopup(
        show = expanded && actualEnabled,
        onDismissRequest = { setExpanded(false) },
        anchorBoundsOverride = rowBounds,
        backdrop = backdrop,
        maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight,
    ) {
        items.forEachIndexed { index, item ->
            GlassPopupItem(
                text = item,
                selected = index == selectedIndex,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    setExpanded(false)
                    onSelectedIndexChange?.invoke(index)
                },
            )
        }
    }
}

/**
 * OverlaySpinnerPreference 的 OS4 版本（对应 Halcyon 的 EllaSpinnerPreference）：选项带可选摘要，
 * 行右侧显示当前选项标题。
 */
@Composable
fun Os4SpinnerPreference(
    items: List<DropdownItem>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val actualEnabled = enabled && items.isNotEmpty()
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val hapticFeedback = LocalHapticFeedback.current
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val setExpanded: (Boolean) -> Unit = { next ->
        if (expanded != next) {
            expanded = next
            currentOnExpandedChange?.invoke(next)
        }
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInWindow() },
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val value = items.getOrNull(selectedIndex)?.text
                if (showValue && !value.isNullOrEmpty()) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                    )
                }
                DropdownArrowEndAction(actionColor = actionColor)
            }
        },
        insideMargin = insideMargin,
        onClick = {
            setExpanded(!expanded)
            if (expanded) hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        role = Role.DropdownList,
        holdDownState = expanded,
        enabled = actualEnabled,
    )

    GlassListPopup(
        show = expanded && actualEnabled,
        onDismissRequest = { setExpanded(false) },
        anchorBoundsOverride = rowBounds,
        backdrop = backdrop,
        maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight,
    ) {
        items.forEachIndexed { index, item ->
            GlassPopupItem(
                text = item.text,
                summary = item.summary,
                selected = index == selectedIndex,
                enabled = item.enabled,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    setExpanded(false)
                    onSelectedIndexChange?.invoke(index)
                    item.onClick?.invoke()
                },
            )
        }
    }
}

/**
 * 按 [DropdownEntry] 分组渲染玻璃弹窗内容，组间用分隔线隔开。
 *
 * @param collapseOnSelection 点击选项后是否关闭弹窗。
 */
@Composable
fun GlassEntriesPopup(
    show: Boolean,
    entries: List<DropdownEntry>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    maxHeight: Dp = GlassPopupDefaults.MaxHeight,
    collapseOnSelection: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    GlassListPopup(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        backdrop = backdrop,
        maxHeight = maxHeight,
    ) {
        entries.forEachIndexed { entryIdx, entry ->
            entry.items.forEach { option ->
                GlassPopupItem(
                    text = option.text,
                    summary = option.summary,
                    selected = option.selected,
                    enabled = entry.enabled && option.enabled,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        if (collapseOnSelection) onDismissRequest()
                        option.onClick?.invoke()
                    },
                )
            }
            if (entryIdx < entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    thickness = 1.dp,
                )
            }
        }
    }
}

/**
 * OverlayIconDropdownMenu 的 OS4 版本：图标按钮锚定一个分组玻璃下拉弹窗。
 */
@Composable
fun Os4IconDropdownMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    collapseOnSelection: Boolean = true,
    minHeight: Dp = 40.dp,
    maxHeight: Dp = GlassPopupDefaults.MaxHeight,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                expanded = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
            holdDownState = expanded,
            minHeight = minHeight,
        ) { content() }
        GlassEntriesPopup(
            show = expanded,
            entries = entries,
            onDismissRequest = { expanded = false },
            backdrop = backdrop,
            maxHeight = maxHeight,
            collapseOnSelection = collapseOnSelection,
        )
    }
}
