/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.proify.lyricon.xposed.logger.YLog

/**
 * 状态栏视图解析工具类 (StatusBarViewResolver)
 * 职责：通过 Hook 拦截系统资源加载过程，捕获状态栏根视图 (Root View) 并分发给订阅者。
 */
object StatusBarViewResolver {

    /**
     * 状态栏视图获取成功的回调定义
     */
    typealias OnViewResolvedListener = (statusBarRoot: ViewGroup) -> Unit

    private val registry = mutableListOf<OnViewResolvedListener>()
    private var isInitialized = false

    /**
     * 订阅状态栏视图。
     * 如果 Hook 触发时视图加载完成，将通知所有订阅者。
     * @param listener 接收 ViewGroup 的回调函数
     */
    fun subscribe(listener: OnViewResolvedListener) {
        if (!registry.contains(listener)) {
            registry.add(listener)
        }
    }

    /**
     * 取消订阅
     */
    fun unsubscribe(listener: OnViewResolvedListener) {
        registry.remove(listener)
    }

    /**
     * 启动拦截任务
     * * @param context 系统上下文（建议使用 SystemUI 的 Context）
     * @param classLoader 对应的 ClassLoader
     */
    @SuppressLint("DiscouragedApi")
    fun init(context: Context, classLoader: ClassLoader = context.classLoader) {
        if (isInitialized) return

        val targetId = context.resources.getIdentifier(
            "status_bar",
            "layout",
            context.packageName
        )

        if (targetId == 0) return

        try {
            XposedHelpers.findAndHookMethod(
                "android.view.LayoutInflater",
                classLoader,
                "inflate",
                Int::class.javaPrimitiveType,
                ViewGroup::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val currentLayoutId = param.args[0] as Int

                        if (currentLayoutId == targetId) {
                            val viewGroup = param.result as? ViewGroup
                            viewGroup?.let { root ->
                                notifyAll(root)
                            }
                        }
                    }
                }
            )
            isInitialized = true
        } catch (t: Throwable) {
            YLog.error("StatusBarViewResolver", "Error during LayoutInflater inflation hook", t)
        }
    }

    private fun notifyAll(view: ViewGroup) {
        registry.forEach { it.invoke(view) }
    }
}