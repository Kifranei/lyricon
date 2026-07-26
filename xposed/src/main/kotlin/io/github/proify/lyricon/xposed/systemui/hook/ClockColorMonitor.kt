/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import java.util.WeakHashMap

/**
 * 时钟颜色监控器
 *
 * 用于监控系统界面中时钟控件的文本颜色变化。
 * 当时钟颜色发生变化时，会通过注册的监听器通知外部，并附带颜色的亮度信息。
 *
 * @author Proify, Tomakino
 * @since 2026
 */
object ClockColorMonitor {

    /** 无效的资源 ID 常量 */
    private const val INVALID_ID = -1

    /** 时钟控件的资源名称 */
    private const val CLOCK_ID_NAME = "clock"

    /** 时钟控件的资源类型 */
    private const val CLOCK_ID_TYPE = "id"

    /** 视图与颜色变化监听器的弱引用映射，避免内存泄漏 */
    private val listeners = WeakHashMap<View, OnColorChangeListener>()

    /** 颜色亮度缓存，用于避免重复计算同一颜色的亮度值 */
    private val luminanceCache = HashMap<Int, Float>()

    /** 是否已完成 Hook 的标志，使用 @Volatile 保证多线程可见性 */
    @Volatile
    private var hooked = false

    /** 时钟控件的资源 ID，使用 @Volatile 保证多线程可见性 */
    @Volatile
    private var clockId: Int = INVALID_ID

    /**
     * 为指定视图设置颜色变化监听器
     *
     * @param view 目标视图，通常为时钟控件
     * @param listener 颜色变化监听器，传入 null 可移除监听
     */
    fun setListener(view: View, listener: OnColorChangeListener?) {
        if (listener == null) {
            listeners.remove(view)
        } else {
            listeners[view] = listener
        }
    }

    /**
     * 执行 Hook 操作，拦截时钟控件的颜色设置方法
     *
     * 通过 Hook [TextView.setTextColor] 方法，监听时钟控件的颜色变化。
     * 该方法只会执行一次 Hook 操作，重复调用不会产生额外效果。
     *
     * @param module Xposed 模块实例
     * @param classLoader 用于加载目标类的类加载器
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (hooked) return
        hooked = true

        val classTextView = classLoader.loadClass("android.widget.TextView")
        val methods = arrayOf(
            classTextView.getDeclaredMethod(
                "setTextColor",
                ColorStateList::class.java
            ),
            classTextView.getDeclaredMethod(
                "setTextColor",
                Int::class.javaPrimitiveType
            )
        )
        @Suppress("ObjectLiteralToLambda")
        methods.forEach { method ->
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    chain.proceed()
                    val tv = chain.thisObject as? TextView
                    if (tv != null) {
                        afterSetColor(tv)
                    }
                    return null
                }
            })
        }
    }

    /**
     * 处理颜色设置后的逻辑
     *
     * 在时钟控件的文本颜色被修改后调用。负责识别时钟控件、计算颜色亮度，
     * 并通过注册的监听器通知外部。
     *
     * @param tv 发生颜色变化的 TextView 实例
     */
    @SuppressLint("DiscouragedApi")
    private fun afterSetColor(tv: TextView) {
        if (clockId == INVALID_ID) {
            clockId = tv.resources.getIdentifier(
                CLOCK_ID_NAME,
                CLOCK_ID_TYPE,
                tv.context.packageName
            )
            if (clockId == INVALID_ID) return
        }

        if (tv.id != clockId) return

        val listener = listeners[tv] ?: return

        val color = tv.currentTextColor
        val luminance = luminanceCache.getOrPut(color) {
            ColorUtils.calculateLuminance(color).toFloat()
        }

        listener.onColorChanged(color, luminance)
    }
}