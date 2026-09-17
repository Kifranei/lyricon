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
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog

/**
 * 状态栏视图解析工具类 (StatusBarViewResolver)
 * 职责：通过 Hook 拦截系统资源加载过程，捕获状态栏根视图 (Root View) 并分发给订阅者。
 */
object StatusBarViewResolver {

    private const val TAG = "StatusBarViewResolver"

    /** 状态栏根布局 / 根视图的资源条目名，跨 ROM 保持一致。 */
    private const val STATUS_BAR_ENTRY = "status_bar"

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
     * @param module XposedModule 实例
     * @param context 系统上下文（建议使用 SystemUI 的 Context）
     * @param classLoader 对应的 ClassLoader
     */
    @SuppressLint("DiscouragedApi")
    fun init(
        module: XposedModule,
        context: Context,
        classLoader: ClassLoader = context.classLoader
    ) {
        if (isInitialized) return

        // 宿主 SystemUI 自身的 layout/status_bar；插件化 ROM（如 MIUI 的
        // miui.systemui.plugin）由独立资源表提供状态栏，这里会拿不到或对不上，
        // 因此 id 只作快速路径，真正的判定见 isStatusBarLayout。
        val targetId = context.resources.getIdentifier(
            "status_bar",
            "layout",
            context.packageName
        )
        if (targetId == 0) {
            YLog.warning(
                TAG,
                "layout/status_bar not found in ${context.packageName}; " +
                        "falling back to name matching only"
            )
        }

        try {
            val inflaterClass = classLoader.loadClass("android.view.LayoutInflater")
            val inflateMethod = inflaterClass.getDeclaredMethod(
                "inflate",
                Int::class.javaPrimitiveType,
                ViewGroup::class.java,
                Boolean::class.javaPrimitiveType
            )

            @Suppress("ObjectLiteralToLambda")
            module.hook(inflateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val root = result as? ViewGroup ?: return result
                    val layoutId = chain.args[0] as Int

                    if (isStatusBarLayout(layoutId, targetId, root)) {
                        YLog.info(
                            TAG,
                            "Status bar resolved: ${root.javaClass.name} " +
                                    "layoutId=0x${layoutId.toString(16)}"
                        )
                        notifyAll(root)
                    }
                    return result
                }
            })
            isInitialized = true
        } catch (t: Throwable) {
            YLog.error(TAG, "Error during LayoutInflater inflation hook", t)
        }
    }

    /**
     * 判定一次 inflate 的产物是否为状态栏根视图。
     *
     * 三重判定，任一命中即可：
     * 1. 布局 id 与宿主 SystemUI 的 `layout/status_bar` 相等（非插件化 ROM 的快速路径）；
     * 2. 布局 id 在**当前 inflate 所用资源**中的条目名为 `status_bar`
     *    —— 插件由独立资源表提供布局，id 与宿主对不上，但条目名一致；
     * 3. 产物根视图自身的 id 条目名为 `status_bar`（兜底，覆盖布局改名的情况）。
     */
    private fun isStatusBarLayout(layoutId: Int, targetId: Int, root: ViewGroup): Boolean {
        if (targetId != 0 && layoutId == targetId) return true
        if (entryName(root, layoutId) == STATUS_BAR_ENTRY) return true
        return entryName(root, root.id) == STATUS_BAR_ENTRY
    }

    /** 用视图自身的资源表解析条目名；跨包/跨资源表时以此为准。 */
    private fun entryName(view: ViewGroup, id: Int): String? {
        if (id == 0 || id == -1) return null
        return runCatching { view.resources.getResourceEntryName(id) }.getOrNull()
    }

    private fun notifyAll(view: ViewGroup) {
        registry.forEach { it.invoke(view) }
    }
}
