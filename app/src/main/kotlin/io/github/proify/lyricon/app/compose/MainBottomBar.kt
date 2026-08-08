package io.github.proify.lyricon.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class MainBottomBarItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainBottomBar(
    items: List<MainBottomBarItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFloating = LocalFloatingBottomBarEnabled.current
    if (!isFloating) {
        val isDark = CurrentThemeConfigs.isDark
        val navContentColor = if (isDark) Color.White else Color(0xFF111111)
        val backdrop = LocalBottomBarBackdrop.current
        // 停靠底栏也采用 InstallerX 同款的背景高斯模糊（有 backdrop 时），
        // 半透明表面色叠加在模糊之上；莫奈开启时 surface 取动态色，模糊同样生效。
        val surface = MiuixTheme.colorScheme.surfaceContainer
        val decorated = if (backdrop != null) {
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(24.dp.toPx()) },
                onDrawSurface = { drawRect(surface.copy(alpha = if (isDark) 0.72f else 0.68f)) },
            )
        } else {
            modifier.background(if (isDark) Color(0xFF0B0B0D) else Color(0xFFF8F8FA))
        }
        // 底栏保留顶部分隔线；单独给它一个可见的 dividerLine，
        // 不受流光模式外层把 dividerLine 置透明的影响
        MiuixTheme(
            colors = MiuixTheme.colorScheme.copy(
                onSurfaceContainer = navContentColor,
                dividerLine = if (isDark) Color.White.copy(alpha = 0.14f)
                else Color.Black.copy(alpha = 0.08f),
            ),
        ) {
            NavigationBar(
                modifier = decorated,
                color = Color.Transparent,
                showDivider = true,
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == index,
                        onClick = { onSelected(index) },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        }
        return
    }

    val backdrop = LocalBottomBarBackdrop.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // FloatingBottomBar 用 selectedIndex lambda 实例作 remember/LaunchedEffect 的 key，
    // 每次重组新建 lambda 会重置内部状态并因 drop(1) 丢掉动画，指示器不跟手；
    // 这里通过 rememberUpdatedState 提供稳定 lambda。
    val currentSelected by rememberUpdatedState(selectedIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp + bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        if (backdrop == null) return@Box
        FloatingBottomBar(
            selectedIndex = remember { { currentSelected } },
            onSelected = onSelected,
            backdrop = backdrop,
            tabsCount = items.size,
            mode = FloatingBottomBarMode.LiquidGlass,
        ) {
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    onClick = { onSelected(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
