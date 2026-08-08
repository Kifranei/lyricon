/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

import android.graphics.drawable.Drawable
import java.util.WeakHashMap

/**
 * 策略：显示当前活跃 App 的图标。
 * 作为兜底策略，不应用特殊颜色或动画。
 */
class AppLogoStrategy(
    val view: SuperLogo
) : ILogoStrategy {
    // 使用弱引用缓存防止 Context 泄漏
    private val cacheIcons = WeakHashMap<String, Drawable>()

    override var isEffective: Boolean = false
        private set

    override fun updateContent() {
        if (view.imageTintList != null) view.imageTintList = null
        if (view.outlineProvider != null) view.outlineProvider = null

        val activePackage = view.activePackage
        val icon = if (activePackage.isNullOrBlank()) null else getIcon(activePackage)

        view.setImageDrawable(icon)
        isEffective = icon != null
        view.updateVisibility()
    }

    private fun getIcon(packageName: String): Drawable? {
        if (packageName.isBlank()) return null

        cacheIcons[packageName]?.let { return it }

        return try {
            val icon = view.context.packageManager.getApplicationIcon(packageName)
            cacheIcons[packageName] = icon
            icon
        } catch (_: Exception) {
            null
        }
    }

    override fun onColorUpdate() {
        // App 图标保持原色
    }

    override fun onAttach() {
        if (view.drawable == null) updateContent()
    }

    override fun onDetach() {
        // App 图标通常较小且由系统缓存管理，可不主动清理，或根据内存策略清理
    }

    override fun onVisibilityChanged(visible: Boolean) {}
}