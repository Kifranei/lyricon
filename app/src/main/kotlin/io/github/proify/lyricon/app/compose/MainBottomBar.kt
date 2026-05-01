package io.github.proify.lyricon.app.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
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
        NavigationBar(
            modifier = modifier,
            color = MiuixTheme.colorScheme.surface,
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
        return
    }

    val shape = RoundedCornerShape(30.dp)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp + bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        FloatingGlassBar(
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = selectedIndex == index
                    val progress = animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = tween(220),
                        label = "main_bottom_bar_selected",
                    )
                    val pillColor = MiuixTheme.colorScheme.primary.copy(
                        alpha = 0.10f,
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minWidth = 64.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(pillColor.copy(alpha = pillColor.alpha * progress.value))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelected(index) },
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) {
                                    MiuixTheme.colorScheme.onSurface
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceSecondary
                                },
                            )
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) {
                                    MiuixTheme.colorScheme.onSurface
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceSecondary
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingGlassBar(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBottomBarBackdrop.current
    val isDark = CurrentThemeConfigs.isDark
    val surface = if (isDark) {
        MiuixTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val glassAlpha = if (isDark) 0.74f else 0.40f
    val chromeTint = if (isDark) {
        Color.Black.copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    val decorated = if (backdrop != null) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(6.dp.toPx())
            },
            onDrawSurface = {
                drawRect(surface.copy(alpha = glassAlpha))
            },
        )
    } else {
        modifier.background(surface.copy(alpha = 0.92f), shape)
    }

    Box(
        modifier = decorated
            .clip(shape)
            .background(chromeTint, shape)
            .padding(1.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(Color.Transparent, shape),
        ) {
            content()
        }
    }
}
