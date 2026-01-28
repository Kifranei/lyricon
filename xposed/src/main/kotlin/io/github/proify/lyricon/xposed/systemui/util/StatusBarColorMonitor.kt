/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.xposed.systemui.util

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArraySet

object StatusBarColorMonitor {

    private val colorBrightnessCache = mutableMapOf<Int, Boolean>()
    private val listeners = CopyOnWriteArraySet<OnColorChangeListener>()
    private val hookEntries = CopyOnWriteArraySet<XC_MethodHook.Unhook>()

    fun registerListener(listener: OnColorChangeListener): Boolean = listeners.add(listener)
    fun unregisterListener(listener: OnColorChangeListener): Boolean = listeners.remove(listener)

    fun hook(targetClass: Class<out View>) {
        unhookAll()

        val classLoader = targetClass.classLoader
        val targetMethods = findDarkChangedMethods(targetClass)

        YLog.debug("找到 ${targetMethods.size} 个 onDarkChanged 方法")

        targetMethods.forEach { method ->
            val hook = XposedBridge.hookMethod(method, DarkChangedHookCallback(classLoader))
            hookEntries.add(hook)
            YLog.debug("已 Hook 方法: $method")
        }
    }

    fun unhookAll() {
        hookEntries.forEach { it.unhook() }
        hookEntries.clear()
    }

    private fun findDarkChangedMethods(clazz: Class<*>): List<Method> {
        return clazz.methods
            .filter { it.name == "onDarkChanged" }
            .toList()
    }

    private fun Int.isDarkAgainst(@ColorInt background: Int = Color.BLACK): Boolean {
        val alpha = Color.alpha(this)

        // 完全透明时，直接判断背景亮度
        if (alpha == 0) {
            return ColorUtils.calculateLuminance(background) < 0.5
        }

        // 不透明时直接判断，半透明时与背景合成后判断
        val finalColor = if (alpha == 255) {
            this
        } else {
            ColorUtils.compositeColors(this, background)
        }

        return ColorUtils.calculateLuminance(finalColor) < 0.5
    }

    fun interface OnColorChangeListener {
        fun onColorChanged(color: Int, lightMode: Boolean)
    }

    private class DarkChangedHookCallback(
        private val classLoader: ClassLoader?
    ) : XC_MethodHook() {

        private var nonAdaptedColorFieldAvailable = true
        private var tintMethodAvailable = true

        private var darkIconDispatcherClass: Class<*>? = null

        override fun afterHookedMethod(param: MethodHookParam) {
            val target = param.thisObject
            val color = extractColor(param, target)

            if (color == 0 || listeners.isEmpty()) return

            val lightMode = colorBrightnessCache.getOrPut(color) {
                color.isDarkAgainst(Color.BLACK)
            }

            listeners.forEach { listener ->
                runCatching {
                    listener.onColorChanged(color, lightMode)
                }.onFailure { e ->
                    YLog.error("监听器回调失败", e)
                }
            }
        }

        /**
         * 从各种来源提取颜色值
         */
        private fun extractColor(param: MethodHookParam, target: Any): Int {
            // 1. 尝试从 mNonAdaptedColor 字段获取
            var color = extractNonAdaptedColor(target)

            // 2. 尝试从 tint 方法获取
            if (color == 0) {
                color = extractTintColor(param)
            }

            // 3. 如果目标是 TextView，尝试获取文本颜色
            if (color == 0 && target is TextView) {
                color = target.currentTextColor
            }

            return color
        }

        /**
         * 从 mNonAdaptedColor 字段提取颜色
         */
        @SuppressLint("PrivateApi")
        private fun extractNonAdaptedColor(target: Any): Int {
            if (!nonAdaptedColorFieldAvailable) return 0

            return runCatching {
                XposedHelpers.getIntField(target, "mNonAdaptedColor")
            }.onFailure {
                nonAdaptedColorFieldAvailable = false
                YLog.warn("mNonAdaptedColor 字段不可用: ${it.message}")
            }.getOrElse { 0 }
        }

        /**
         * 通过 DarkIconDispatcher.getTint 提取颜色
         */
        @SuppressLint("PrivateApi")
        private fun extractTintColor(param: MethodHookParam): Int {
            if (!tintMethodAvailable || param.args.size != 3) return 0

            return runCatching {
                val dispatcherClass = darkIconDispatcherClass ?: classLoader
                    ?.loadClass("com.android.systemui.plugins.DarkIconDispatcher")
                    ?.also { darkIconDispatcherClass = it }
                ?: return 0

                XposedHelpers.callStaticMethod(
                    dispatcherClass,
                    "getTint",
                    param.args[0],
                    param.thisObject,
                    param.args[2]
                ) as Int
            }.onFailure {
                tintMethodAvailable = false
                YLog.warn("DarkIconDispatcher.getTint 方法不可用: ${it.message}")
            }.getOrElse { 0 }
        }
    }
}