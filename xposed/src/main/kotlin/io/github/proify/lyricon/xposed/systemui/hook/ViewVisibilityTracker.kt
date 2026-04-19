/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.hook

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.proify.lyricon.xposed.logger.YLog
import java.util.concurrent.ConcurrentHashMap

/**
 * 视图可见性追踪器
 * * 该工具通过 Hook [android.view.View.setFlags] 方法来追踪和管理视图的原始可见性状态。
 * 使用原生 Xposed API 实现，不依赖第三方 Hook 框架。
 */
object ViewVisibilityTracker {
    private const val TAG = "ViewVisibilityTracker"

    /** 用于标记被追踪的视图的 Tag ID */
    const val TRACKING_TAG_ID: Int = 0x7F137666

    /** 自定义可见性标记：强制可见 (用于绕过业务逻辑) */
    const val CUSTOM_VISIBLE: Int = 114514

    /** 自定义可见性标记：强制隐藏 (用于绕过业务逻辑) */
    const val CUSTOM_GONE: Int = 1919810

    /** Android 系统 View 标志位中可见性的掩码：0x0000000C */
    private const val VISIBILITY_FLAG_MASK = 0x0000000C

    /** 存储视图原始可见性状态的 Map，Key 为 View 的 ID，Value 为原始的 Visibility 标志 */
    private val originalVisibilityMap = ConcurrentHashMap<Int, Int>()

    /** 存储当前 Hook 的句柄，用于初始化时清理旧 Hook */
    private var unhookHandle: XC_MethodHook.Unhook? = null

    /**
     * 初始化 Hook 逻辑
     * * @param classLoader 当前宿主 App 的 ClassLoader
     */
    fun initialize(classLoader: ClassLoader) {
        try {
            // 移除旧的 Hook 实例，防止内存泄漏或重复 Hook
            unhookHandle?.unhook()

            // Hook View.setFlags(int flags, int mask)
            unhookHandle = XposedHelpers.findAndHookMethod(
                View::class.java,
                "setFlags",
                Int::class.javaPrimitiveType, // flags
                Int::class.javaPrimitiveType, // mask
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        handleSetFlags(view, param)
                    }
                }
            )
            YLog.info(TAG, "Successfully hooked View.setFlags")
        } catch (t: Throwable) {
            YLog.error(TAG, "Failed to initialize hook", t)
        }
    }

    /**
     * 处理 setFlags 参数的内部逻辑
     * @param view 当前正在执行 setFlags 的 View 实例
     * @param param Xposed 方法回调参数
     */
    private fun handleSetFlags(view: View, param: XC_MethodHook.MethodHookParam) {
        val viewId = view.id
        // 如果 View 没有设置 ID，则不进行追踪（根据业务需求可调整）
        if (viewId == View.NO_ID) return

        val flags = param.args[0] as Int
        val mask = param.args[1] as Int

        // 仅当掩码包含可见性更改位时才进行处理
        if (mask != VISIBILITY_FLAG_MASK) return

        when (flags) {
            CUSTOM_GONE -> {
                saveOriginalVisibilityIfNeeded(viewId, view.visibility)
                // 将自定义标志修改为系统识别的 GONE
                param.args[0] = View.GONE
            }

            CUSTOM_VISIBLE -> {
                saveOriginalVisibilityIfNeeded(viewId, view.visibility)
                // 将自定义标志修改为系统识别的 VISIBLE
                param.args[0] = View.VISIBLE
            }

            else -> {
                // 如果该视图被打上了追踪标记，记录系统尝试设置的原始可见性
                if (view.getTag(TRACKING_TAG_ID) != null) {
                    originalVisibilityMap[viewId] = flags
                }
            }
        }
    }

    /**
     * 在必要时保存视图当前的可见性
     * * @param viewId 视图 ID
     * @param currentVisibility 当前 View 对象的实际可见性值
     */
    private fun saveOriginalVisibilityIfNeeded(viewId: Int, currentVisibility: Int) {
        if (!originalVisibilityMap.containsKey(viewId)) {
            originalVisibilityMap[viewId] = currentVisibility
        }
    }

    /**
     * 获取视图被篡改前的原始可见性
     *  @param viewId 视图 ID
     * @param defaultValue 找不到记录时的默认返回值，默认为 -1
     * @return 原始可见性标志 (0, 4, 8) 或默认值
     */
    fun getOriginalVisibility(viewId: Int, defaultValue: Int = -1): Int {
        return originalVisibilityMap.getOrDefault(viewId, defaultValue)
    }

    /**
     * 停止追踪特定视图并移除记录
     *  @param viewId 视图 ID
     */
    fun clearTracking(viewId: Int) {
        originalVisibilityMap.remove(viewId)
    }

    /**
     * 清空所有已保存的可见性追踪记录
     */
    fun clearAllTracking() {
        originalVisibilityMap.clear()
    }
}