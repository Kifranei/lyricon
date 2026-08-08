/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog

/**
 * 视图可见性追踪器
 *
 * 通过 Hook [View.setFlags] 方法拦截视图可见性变更，
 * 支持自定义可见性标记（绕过业务逻辑强制显示/隐藏），
 * 并记录被追踪视图的原始可见性状态以便恢复。
 */
object ViewVisibilityTracker {
    private const val TAG = "ViewVisibilityTracker"

    /** 用于标记被追踪视图的 Tag ID */
    const val TRACKING_TAG_ID: Int = 0x7F137666

    /** 自定义可见性标记：强制可见，用于绕过业务逻辑 */
    const val CUSTOM_VISIBLE: Int = 114514

    /** 自定义可见性标记：强制隐藏，用于绕过业务逻辑 */
    const val CUSTOM_GONE: Int = 1919810

    /** View 标志位中可见性相关位的掩码 (0x0000000C) */
    private const val VISIBILITY_FLAG_MASK = 0x0000000C

    /** 视图原始可见性缓存，Key 为 View ID，Value 为被篡改前的可见性值 */
    private val originalVisibilityMap = HashMap<Int, Int>()

    /** 当前 Hook 句柄，用于反注册旧 Hook 防止重复注入 */
    private var unhookHandle: XposedInterface.HookHandle? = null

    /**
     * 初始化 Hook，拦截 [View.setFlags] 方法
     *
     * @param module XposedModule 实例
     * @param classLoader 宿主应用的 ClassLoader
     */
    fun initialize(module: XposedModule, classLoader: ClassLoader) {
        try {
            // 移除旧 Hook 实例，防止内存泄漏或重复 Hook
            unhookHandle?.unhook()

            @SuppressLint("SoonBlockedPrivateApi")
            val setFlagsMethod = classLoader.loadClass(View::class.java.name)
                .getDeclaredMethod(
                    "setFlags",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )

            @Suppress("ObjectLiteralToLambda")
            unhookHandle =
                module.hook(setFlagsMethod)
                    .intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val view = chain.thisObject as View
                            val newArgs = handleSetFlags(view, chain)

                            if (newArgs != null) {
                                chain.proceed(newArgs)
                            } else {
                                chain.proceed()
                            }
                            return null
                        }
                    })
            YLog.info(TAG, "Successfully hooked View.setFlags $unhookHandle")
        } catch (t: Throwable) {
            YLog.error(TAG, "Failed to initialize hook", t)
        }
    }

    /**
     * 处理 [View.setFlags] 的参数
     *
     * - 拦截自定义可见性标记，替换为系统标准值并保存原始状态
     * - 当视图带有追踪标记时，记录系统实际设置的可见性
     *
     * @param view 目标 View 实例
     * @param param Hook 调用链，用于获取原始参数
     * @return 修改后的参数数组，不需要修改时返回 null
     */
    private fun handleSetFlags(view: View, param: XposedInterface.Chain): Array<Any>? {
        val viewId = view.id
        if (viewId == View.NO_ID) return null

        val flags = param.args[0] as Int
        val mask = param.args[1] as Int

        // 仅处理可见性掩码变更
        if (mask != VISIBILITY_FLAG_MASK) return null

        var newArgs: Array<Any>? = null
        when (flags) {
            CUSTOM_GONE -> {
                saveOriginalVisibilityIfNeeded(viewId, view.visibility)
                newArgs = arrayOf(View.GONE, mask)
            }

            CUSTOM_VISIBLE -> {
                saveOriginalVisibilityIfNeeded(viewId, view.visibility)
                newArgs = arrayOf(View.VISIBLE, mask)
            }

            else -> {
                // 被追踪视图：记录系统设置的原始可见性
                if (view.getTag(TRACKING_TAG_ID) != null) {
                    originalVisibilityMap[viewId] = flags
                }
            }
        }
        return newArgs
    }

    /**
     * 仅在未缓存时保存视图当前可见性
     *
     * @param viewId 视图 ID
     * @param currentVisibility 当前实际可见性值
     */
    private fun saveOriginalVisibilityIfNeeded(viewId: Int, currentVisibility: Int) {
        if (!originalVisibilityMap.containsKey(viewId)) {
            originalVisibilityMap[viewId] = currentVisibility
        }
    }

    /**
     * 获取视图被篡改前的原始可见性
     *
     * @param viewId 视图 ID
     * @param defaultValue 无记录时的默认返回值，默认为 -1
     * @return 原始可见性值 (VISIBLE=0, INVISIBLE=4, GONE=8) 或默认值
     */
    fun getOriginalVisibility(viewId: Int, defaultValue: Int = -1): Int {
        return originalVisibilityMap.getOrDefault(viewId, defaultValue)
    }

    /**
     * 移除指定视图的追踪记录
     *
     * @param viewId 视图 ID
     */
    fun clearTracking(viewId: Int) {
        originalVisibilityMap.remove(viewId)
    }

    /** 清空所有可见性追踪记录 */
    fun clearAllTracking() {
        originalVisibilityMap.clear()
    }
}