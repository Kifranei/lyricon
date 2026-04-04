/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 监听状态栏禁用事件
 */
object StatusBarDisableHooker {

    private const val TAG = "StatusBarDisableHooker"

    // 状态标志位定义
    private const val FLAG_DISABLE_SYSTEM_INFO = 0x00800000

    private val listeners = CopyOnWriteArraySet<OnStatusBarDisableListener>()

    /**
     * 外部注册监听器
     */
    fun addListener(listener: OnStatusBarDisableListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * 外部移除监听器
     */
    fun removeListener(listener: OnStatusBarDisableListener) {
        listeners.remove(listener)
    }

    fun inject(appClassLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                appClassLoader,
                "disable",
                Int::class.javaPrimitiveType, // displayId
                Int::class.javaPrimitiveType, // state1
                Int::class.javaPrimitiveType, // state2
                Boolean::class.javaPrimitiveType, // animate
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state1 = param.args[1] as Int
                        val animate = param.args[3] as Boolean

                        val shouldHide = (state1 and FLAG_DISABLE_SYSTEM_INFO != 0)

                        listeners.forEach {
                            try {
                                it.onDisableStateChanged(shouldHide, animate)
                            } catch (e: Exception) {
                                YLog.error("$TAG -> 分发监听失败: ${e.message}")
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            YLog.error("$TAG -> Hook 注入失败: ${e.message}")
        }
    }

    interface OnStatusBarDisableListener {
        fun onDisableStateChanged(shouldHide: Boolean, animate: Boolean)
    }
}