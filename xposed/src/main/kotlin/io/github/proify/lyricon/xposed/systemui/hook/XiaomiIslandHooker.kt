/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.content.ContentResolver
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 小米超级岛（HyperOS 灵动岛）监听与临时隐藏控制器。
 *
 * HyperOS 3 的灵动岛不在 SystemUI 主进程的 WindowManagerGlobal.mViews 里
 * （实测该列表仅含 ShellDropTarget 等少数窗口），也不通过标准
 * `View.setVisibility` 显隐——因此早期基于窗口扫描 / setVisibility 拦截的方案全部失效。
 *
 * 逆向 MIUISystemUIPlugin 后发现灵动岛内容视图
 * [miui.systemui.dynamicisland.window.content.DynamicIslandContentView] 暴露了
 * 明文的 `showIslandLayout()` / `hideIslandLayout()` 显隐 API。本控制器直接 hook
 * 这两个方法：
 *  - 记录内容视图实例，并以 `showIslandLayout` 是否被系统调用推断岛的"想显示"状态；
 *  - 歌词显示时对实例调用 `hideIslandLayout()` 抑制岛，歌词消失后调用
 *    `showIslandLayout()` 恢复；
 *  - 系统在抑制期间若再次 `showIslandLayout`，立即改判为隐藏，避免闪现。
 *
 * 插件类由 SystemUI 用独立 ClassLoader 动态加载，主 ClassLoader 通常加载不到，
 * 因此通过全局 `View.onAttachedToWindow` 钩子在任一 `miui.systemui.dynamicisland.*`
 * 视图 attach 时自举拿到插件 ClassLoader，再 hook 上述方法。
 */
object XiaomiIslandHooker {
    private const val TAG = "XiaomiIslandHooker"
    private const val ISLAND_PKG_PREFIX = "miui.systemui.dynamicisland"
    private const val ISLAND_CONTENT_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"

    /** 灵动岛窗口根视图（FrameLayout 子类，含背景胶囊 + 内容）。拦截其绘制以整体隐藏 */
    private const val ISLAND_WINDOW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"

    private val listeners = CopyOnWriteArrayList<IslandStateChangeListener>()

    /** 系统是否"想显示"灵动岛（不含本模块的抑制动作） */
    @Volatile
    var isShowing: Boolean = false
        private set

    @Volatile
    private var hideByLyric = false

    private var lastNotifiedShowing: Boolean? = null

    /** 已捕获的灵动岛内容视图实例（DynamicIslandContentView） */
    private val contentViews = mutableListOf<WeakReference<Any>>()
    private val contentViewsLock = Any()

    /** 已捕获的灵动岛窗口根视图（DynamicIslandWindowView），绘制被拦截以整体隐藏 */
    private val windowViews = mutableListOf<WeakReference<View>>()
    private val windowViewsLock = Any()
    private var dispatchDrawHooked = false
    private var dispatchDrawHandle: XposedInterface.HookHandle? = null

    /** 防止本模块调用 show/hideIslandLayout 时触发自身 hook 递归判定 */
    private val selfCalling = ThreadLocal.withInitial { false }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingHideRunnable: Runnable? = null
    private const val HIDE_DEBOUNCE_MS = 250L

    private var module: XposedModule? = null
    private var attachHookHandle: XposedInterface.HookHandle? = null
    private var showHookHandle: XposedInterface.HookHandle? = null
    private var hideHookHandle: XposedInterface.HookHandle? = null

    @Volatile
    private var islandClassHooked = false
    private var showMethod: Method? = null
    private var hideMethod: Method? = null

    /* --------------- 诊断（Settings.Global，adb 可实时读取） --------------- */
    private const val DIAG_KEY = "lyricon_island_diag"
    private var contentResolver: ContentResolver? = null

    private fun publishDiag() {
        val resolver = contentResolver ?: return
        runCatching { Settings.Global.putString(resolver, DIAG_KEY, dumpStatus()) }
    }

    fun isSupported(): Boolean {
        val supported = isXiaomiFamilyDevice() && detectHyperOsMajor() >= 3
        if (!supported) {
            YLog.info(
                TAG,
                "Not supported: xiaomiFamily=${isXiaomiFamilyDevice()} " +
                        "hyperOsMajor=${detectHyperOsMajor()} brand=${Build.BRAND}"
            )
        }
        return supported
    }

    fun initialize(module: XposedModule, classLoader: ClassLoader, context: Context? = null) {
        this.module = module
        contentResolver = context?.contentResolver ?: contentResolver

        if (attachHookHandle == null) {
            // 全局 View.onAttachedToWindow：任一灵动岛视图 attach 时自举拿到插件 ClassLoader
            val onAttached = View::class.java.getDeclaredMethod("onAttachedToWindow")
            @Suppress("ObjectLiteralToLambda")
            attachHookHandle = module.hook(onAttached)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        val view = chain.thisObject as? View ?: return result
                        val name = view.javaClass.name
                        if (name.startsWith(ISLAND_PKG_PREFIX)) {
                            hookIslandContentClass(view.javaClass.classLoader)
                            if (name == ISLAND_CONTENT_CLASS) {
                                trackContentView(view)
                            }
                            if (name == ISLAND_WINDOW_CLASS) {
                                trackWindowView(view)
                                if (hideByLyric) view.invalidate()
                            }
                        }
                        return result
                    }
                })
        }

        // SystemUI 主 ClassLoader 一般加载不到插件类，失败无妨，等自举
        hookIslandContentClass(classLoader)
        publishDiag()
        YLog.info(TAG, "Initialized (HyperOS major=${detectHyperOsMajor()})")
    }

    /** 用给定 ClassLoader hook DynamicIslandContentView 的显隐方法（仅一次） */
    private fun hookIslandContentClass(classLoader: ClassLoader?) {
        if (islandClassHooked || classLoader == null) return
        val mod = module ?: return
        val clazz = runCatching { classLoader.loadClass(ISLAND_CONTENT_CLASS) }.getOrNull() ?: return

        val show = runCatching { clazz.getDeclaredMethod("showIslandLayout") }.getOrNull() ?: return
        val hide = runCatching { clazz.getDeclaredMethod("hideIslandLayout") }.getOrNull() ?: return
        show.isAccessible = true
        hide.isAccessible = true
        showMethod = show
        hideMethod = hide
        islandClassHooked = true

        @Suppress("ObjectLiteralToLambda")
        showHookHandle = mod.hook(show).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val self = selfCalling.get() == true
                val thisObj = chain.thisObject
                if (thisObj != null) trackContentView(thisObj)
                if (self) return chain.proceed()

                // 系统要显示岛
                isShowing = true
                notifyIfChanged()
                val result = chain.proceed()
                // 抑制期内触发窗口根视图重绘，让 dispatchDraw 拦截立即生效，避免闪现
                if (hideByLyric) collectWindowViews().forEach { runCatching { it.invalidate() } }
                publishDiag()
                return result
            }
        })

        @Suppress("ObjectLiteralToLambda")
        hideHookHandle = mod.hook(hide).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val self = selfCalling.get() == true
                val result = chain.proceed()
                if (!self) {
                    // 系统主动隐藏岛
                    isShowing = false
                    notifyIfChanged()
                    publishDiag()
                }
                return result
            }
        })

        hookWindowDispatchDraw(classLoader)
        YLog.info(TAG, "Island content class hooked via ${classLoader}")
        publishDiag()
    }

    /**
     * 拦截灵动岛窗口根视图的 dispatchDraw：抑制期间跳过绘制，使整岛（背景胶囊 + 内容）
     * 不可见。绘制级拦截不会被系统的显示/动画流程重置，比 setVisibility 更稳。
     */
    private fun hookWindowDispatchDraw(classLoader: ClassLoader) {
        if (dispatchDrawHooked) return
        val mod = module ?: return
        val clazz = runCatching { classLoader.loadClass(ISLAND_WINDOW_CLASS) }.getOrNull() ?: return
        val method = runCatching {
            clazz.getDeclaredMethod("dispatchDraw", Canvas::class.java)
        }.getOrNull() ?: return
        method.isAccessible = true
        dispatchDrawHooked = true

        @Suppress("ObjectLiteralToLambda")
        dispatchDrawHandle = mod.hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                (chain.thisObject as? View)?.let { trackWindowView(it) }
                // 抑制期：跳过 dispatchDraw，子视图（背景 + 内容）不绘制
                if (hideByLyric) return null
                return chain.proceed()
            }
        })
        YLog.info(TAG, "Island window dispatchDraw hooked")
    }

    fun registerListener(listener: IslandStateChangeListener): Boolean = listeners.add(listener)
    fun unregisterListener(listener: IslandStateChangeListener): Boolean =
        listeners.remove(listener)

    /**
     * 歌词显示联动：显示歌词时抑制灵动岛，恢复时让系统重新显示。
     */
    fun setHideByLyric(shouldHide: Boolean) {
        if (hideByLyric == shouldHide) return
        hideByLyric = shouldHide

        // dispatchDraw 拦截依据 hideByLyric 判定，改变后需触发窗口根视图重绘使其生效
        collectWindowViews().forEach { runCatching { it.invalidate() } }
        publishDiag()
        YLog.info(
            TAG,
            "setHideByLyric: $shouldHide (windowViews=${collectWindowViews().size} " +
                    "contentViews=${collectContentViews().size})"
        )
    }


    private fun trackWindowView(view: View) {
        synchronized(windowViewsLock) {
            var exists = false
            val iterator = windowViews.iterator()
            while (iterator.hasNext()) {
                val tracked = iterator.next().get()
                if (tracked == null) iterator.remove()
                else if (tracked === view) exists = true
            }
            if (!exists) {
                windowViews.add(WeakReference(view))
                YLog.info(TAG, "Tracked island window view: ${view.javaClass.name}")
                if (hideByLyric) runCatching { view.invalidate() }
            }
        }
    }

    private fun collectWindowViews(): List<View> {
        synchronized(windowViewsLock) {
            val result = ArrayList<View>(windowViews.size)
            val iterator = windowViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next().get()
                if (view == null) iterator.remove() else result.add(view)
            }
            return result
        }
    }

    private fun trackContentView(view: Any) {
        synchronized(contentViewsLock) {
            var exists = false
            val iterator = contentViews.iterator()
            while (iterator.hasNext()) {
                val tracked = iterator.next().get()
                if (tracked == null) iterator.remove()
                else if (tracked === view) exists = true
            }
            if (!exists) {
                contentViews.add(WeakReference(view))
                YLog.info(TAG, "Tracked island content view: ${view.javaClass.name}")
            }
        }
    }

    private fun collectContentViews(): List<Any> {
        synchronized(contentViewsLock) {
            val result = ArrayList<Any>(contentViews.size)
            val iterator = contentViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next().get()
                if (view == null) iterator.remove() else result.add(view)
            }
            return result
        }
    }

    private fun notifyIfChanged() {
        val showing = isShowing
        if (showing) {
            pendingHideRunnable?.let { handler.removeCallbacks(it) }
            pendingHideRunnable = null
            emit(showing)
        } else if (lastNotifiedShowing != false && pendingHideRunnable == null) {
            val runnable = Runnable {
                pendingHideRunnable = null
                emit(isShowing)
            }
            pendingHideRunnable = runnable
            handler.postDelayed(runnable, HIDE_DEBOUNCE_MS)
        }
    }

    private fun emit(showing: Boolean) {
        if (lastNotifiedShowing == showing) return
        lastNotifiedShowing = showing
        listeners.forEach { it.onXiaomiIslandVisibilityChanged(showing) }
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        return listOf(brand, manufacturer, product).any { source ->
            source.contains("xiaomi") || source.contains("redmi") || source.contains("poco")
        }
    }

    private fun detectHyperOsMajor(): Int {
        val sources = listOfNotNull(
            getSystemProperty("ro.mi.os.version.name"),
            getSystemProperty("ro.system.build.version.incremental"),
            getSystemProperty("ro.build.version.incremental"),
            getSystemProperty("ro.vendor.build.version.incremental"),
            getSystemProperty("ro.system.build.fingerprint"),
            getSystemProperty("ro.vendor.build.fingerprint"),
            Build.DISPLAY,
            Build.FINGERPRINT
        )
        val regex = Regex("""(?i)\bOS(\d+)(?:\.\d+)*""")
        return sources
            .mapNotNull { source -> regex.find(source)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
    }

    private fun getSystemProperty(key: String): String? {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, "") as? String)?.trim().orEmpty()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    interface IslandStateChangeListener {
        fun onXiaomiIslandVisibilityChanged(isShowing: Boolean)
    }

    /**
     * 诊断字符串。可用
     * `adb shell settings get global lyricon_island_diag` 实时读取，
     * 无需打开 App、不打断状态栏歌词与灵动岛。
     */
    fun dumpStatus(): String {
        return "pid=${android.os.Process.myPid()} proc=${processName()} " +
                "supported=${isXiaomiFamilyDevice() && detectHyperOsMajor() >= 3} " +
                "hyperOsMajor=${detectHyperOsMajor()} " +
                "attachHooked=${attachHookHandle != null} " +
                "islandClassHooked=$islandClassHooked drawHooked=$dispatchDrawHooked " +
                "contentViews=${collectContentViews().size} windowViews=${collectWindowViews().size} " +
                "isShowing=$isShowing hideByLyric=$hideByLyric"
    }

    private fun processName(): String = runCatching {
        java.io.File("/proc/self/cmdline").readText().trim { it <= ' ' }
    }.getOrDefault("?")
}
