package io.github.proify.lyricon.app.compose

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 底栏 / 顶栏外观相关的配置键。
 */
object BarStylePrefs {
    /** 悬浮底栏样式，取值 [STYLE_IOS] / [STYLE_OS4]。 */
    const val KEY_FLOATING_BAR_STYLE = "floating_nav_bar_style"

    /** iOS 风格液态玻璃悬浮底栏（原有样式，默认）。 */
    const val STYLE_IOS = 0

    /** OS4 风格玻璃胶囊底栏（移植自 Halcyon 的 miuix-glass GlassNavigationBar）。 */
    const val STYLE_OS4 = 1

    /** 顶栏渐进模糊开关，默认开启。 */
    const val KEY_PROGRESSIVE_TOP_BAR_BLUR = "enable_progressive_top_bar_blur"
}

/** 当前悬浮底栏样式，由 MainActivity 根据设置提供。 */
val LocalFloatingBottomBarStyle = staticCompositionLocalOf { BarStylePrefs.STYLE_IOS }
