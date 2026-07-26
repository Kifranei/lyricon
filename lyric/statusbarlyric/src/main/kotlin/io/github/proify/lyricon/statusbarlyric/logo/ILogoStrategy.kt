/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric.logo

/**
 * Logo 显示策略接口。
 * 定义了不同内容源（App图标、Provider图标、封面）的渲染和生命周期行为。
 */
interface ILogoStrategy {
    val isEffective: Boolean

    /**
     * 加载或更新显示内容。
     * 在策略初始化、内容源变更或系统封面更新时调用。
     */
    fun updateContent()

    /**
     * 响应状态栏颜色变化。
     */
    fun onColorUpdate()

    /**
     * 当 View 附加到窗口时调用。
     * 用于恢复动画、注册特定监听器等。
     */
    fun onAttach()

    /**
     * 当 View 从窗口移除时调用。
     * 用于停止动画、清理重型资源。
     */
    fun onDetach()

    /**
     * 当 View 的可见性发生变化时调用。
     */
    fun onVisibilityChanged(visible: Boolean)
}