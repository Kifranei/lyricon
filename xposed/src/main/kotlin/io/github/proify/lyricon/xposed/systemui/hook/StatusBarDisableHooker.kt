/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 状态栏禁用事件 Hook 处理器
 *
 * 通过 Hook 系统界面的 [CollapsedStatusBarFragment.disable] 方法，
 * 监听状态栏系统信息区域的显示/隐藏状态变化，并将事件分发给注册的监听器。
 *
 * 主要用于配合歌词显示等功能，在状态栏收起/展开时做出响应。
 *
 * @author Proify, Tomakino
 * @since 2026
 */
object StatusBarDisableHooker {

    /** 日志标签 */
    private const val TAG = "StatusBarDisableHooker"

    /**
     * 状态栏标志位：隐藏系统信息区域
     *
     * 当该标志位被置位时，表示系统信息区域（如时钟、通知图标等）应被隐藏。
     * 该值来源于 Android 系统内部的 View.STATUS_BAR_DISABLE_SYSTEM_INFO 标志位
     */
    private const val FLAG_DISABLE_SYSTEM_INFO = 0x00800000

    /**
     * 状态栏禁用事件监听器集合
     *
     * 使用 [CopyOnWriteArraySet] 保证线程安全，
     * 在遍历分发事件时可以进行安全的并发修改操作
     */
    private val listeners = CopyOnWriteArraySet<OnStatusBarDisableListener>()

    /**
     * 注册状态栏禁用事件监听器
     *
     * @param listener 要注册的监听器，不能为 null
     */
    fun addListener(listener: OnStatusBarDisableListener) {
        listeners.add(listener)
    }

    /**
     * 移除状态栏禁用事件监听器
     *
     * @param listener 要移除的监听器，不能为 null
     */
    fun removeListener(listener: OnStatusBarDisableListener) {
        listeners.remove(listener)
    }

    /**
     * 执行 Hook 注入
     *
     * 在系统界面进程中 Hook [CollapsedStatusBarFragment.disable] 方法，
     * 拦截状态栏的显示/隐藏状态变化。
     *
     * @param module Xposed 模块实例，用于执行 Hook 操作
     * @param classLoader 目标进程的类加载器，用于加载系统界面类
     */
    @SuppressLint("PrivateApi")
    fun inject(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = Class.forName(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                true,
                classLoader
            )
            val method = clazz.getDeclaredMethod(
                "disable",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            @Suppress("ObjectLiteralToLambda")
            module.hook(method).intercept(object : XposedInterface.Hooker {
                /**
                 * Hook 回调：在 disable 方法执行后拦截
                 *
                 * 解析方法参数，提取 `state1` 和 `animate` 标志，
                 * 判断系统信息区域的隐藏状态，并分发给所有注册的监听器。
                 *
                 * 单个监听器的异常不会影响其他监听器的通知流程。
                 *
                 * @param chain Hook 调用链，用于执行原始方法和获取参数
                 * @return 始终返回 null，因为原始方法返回值为 void
                 */
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    chain.proceed()
                    val state1 = chain.args[1] as Int
                    val animate = chain.args[3] as Boolean

                    val shouldHide = (state1 and FLAG_DISABLE_SYSTEM_INFO != 0)

                    listeners.forEach {
                        try {
                            it.onDisableStateChanged(shouldHide, animate)
                        } catch (e: Exception) {
                            YLog.error(TAG, "分发监听失败", e)
                        }
                    }
                    return null
                }

            })
        } catch (_: Throwable) {
            //YLog.error(TAG, " -> Hook 注入失败: ")
        }
    }

    /**
     * 状态栏禁用状态变化监听器接口
     *
     * 当 Hook 检测到状态栏系统信息区域显示/隐藏状态发生变化时回调
     */
    interface OnStatusBarDisableListener {
        /**
         * 禁用状态变化回调
         *
         * @param shouldHide 系统信息区域是否应该隐藏，true 表示正在隐藏
         * @param animate 此次状态变化是否伴随过渡动画
         */
        fun onDisableStateChanged(shouldHide: Boolean, animate: Boolean)
    }
}