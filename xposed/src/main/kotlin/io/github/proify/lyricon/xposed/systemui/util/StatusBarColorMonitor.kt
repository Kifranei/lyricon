/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.util

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 状态栏颜色监控工具
 * * 通过监听 onDarkChanged 实现 View 级别的颜色追踪。
 * 状态管理基于 View 实例域，支持多实例独立回调。
 */
object StatusBarColorMonitor {
    private const val TAG = "StatusBarColorMonitor"

    /** View Tag Key：存储实例上次回调的颜色值，确保 View 域状态隔离 */
    private const val TAG_KEY_COLOR = 0x7f010001.inv()

    /** 弱引用监听映射：防止 View 销毁导致内存泄漏 */
    private val listeners = WeakHashMap<View, OnColorChangeListener>()

    /** 已 Hook 类名缓存：避免静态方法重复 Hook */
    private val hookedClasses = CopyOnWriteArraySet<String>()

    private val hookEntries = CopyOnWriteArraySet<XC_MethodHook.Unhook>()

    /**
     * 注册 View 颜色监听
     * @param listener 传入 null 则移除监听
     */
    fun setListener(view: View, listener: OnColorChangeListener?) {
        if (listener == null) {
            listeners.remove(view)
            return
        }

        listeners[view] = listener

        val className = view.javaClass.name
        if (hookedClasses.add(className)) {
            performHook(view.javaClass)
        }
    }

    private fun performHook(targetClass: Class<*>) {
        runCatching {
            // 兼容 AOSP 及其定制版中所有可能的 onDarkChanged 方法签名
            val methods = targetClass.declaredMethods.filter { it.name == "onDarkChanged" }

            if (methods.isEmpty()) return@runCatching

            methods.forEach { method ->
                val callback = DarkChangedHookCallback(targetClass.classLoader)
                hookEntries.add(XposedBridge.hookMethod(method, callback))
            }
        }.onFailure {
            YLog.error(tag = TAG, msg = "Hook 失败: ${targetClass.name}", e = it)
            hookedClasses.remove(targetClass.name)
        }
    }

    @Suppress("unused")
    fun release() {
        hookEntries.forEach { it.unhook() }
        hookEntries.clear()
        hookedClasses.clear()
        listeners.clear()
    }

    private class DarkChangedHookCallback(
        private val classLoader: ClassLoader?
    ) : XC_MethodHook() {

        private var fieldAvailable = true
        private var methodAvailable = true
        private var dispatcherClass: Class<*>? = null

        override fun afterHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            val listener = listeners[view] ?: return

            try {
                // darkIntensity: 0.0 (全黑) 到 1.0 (全白/浅色)
                val darkIntensity = param.args.getOrNull(1) as? Float ?: return

                val color = extractColor(param, view)
                if (color == 0) return

                // 检查 View 域颜色缓存，防止同一实例频繁触发相同回调
                val lastColor = view.getTag(TAG_KEY_COLOR) as? Int
                if (color == lastColor) return

                view.setTag(TAG_KEY_COLOR, color)
                listener.onColorChanged(color, darkIntensity)

            } catch (e: Exception) {
                YLog.error(tag = TAG, msg = "颜色回调解析异常", e = e)
            }
        }

        private fun extractColor(param: MethodHookParam, target: View): Int {
            return getNonAdaptedColor(target)
                .takeIf { it != 0 }
                ?: getTintColor(param)
                    .takeIf { it != 0 }
                ?: (target as? TextView)?.currentTextColor
                ?: 0
        }

        /** 获取非自适应原始颜色 (部分定制 ROM) */
        @SuppressLint("PrivateApi")
        private fun getNonAdaptedColor(target: Any): Int {
            if (!fieldAvailable) return 0
            return runCatching { XposedHelpers.getIntField(target, "mNonAdaptedColor") }
                .onFailure { fieldAvailable = false }
                .getOrDefault(0)
        }

        /** 调用 DarkIconDispatcher.getTint 静态方法获取计算后的颜色 */
        @SuppressLint("PrivateApi")
        private fun getTintColor(param: MethodHookParam): Int {
            if (!methodAvailable || param.args.size < 3) return 0
            return runCatching {
                val clazz = dispatcherClass ?: classLoader
                    ?.loadClass("com.android.systemui.plugins.DarkIconDispatcher")
                    ?.also { dispatcherClass = it }

                XposedHelpers.callStaticMethod(
                    clazz, "getTint",
                    param.args[0], param.thisObject, param.args[2]
                ) as Int
            }.onFailure {
                methodAvailable = false
            }.getOrDefault(0)
        }
    }
}