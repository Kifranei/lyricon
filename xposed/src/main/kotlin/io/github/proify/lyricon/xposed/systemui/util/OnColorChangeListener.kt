package io.github.proify.lyricon.xposed.systemui.util

/**
 * 状态栏颜色监听器
 */
interface OnColorChangeListener {
    fun onColorChanged(color: Int, darkIntensity: Float)
}