/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.WindowManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * HDR 状态栏控制器
 *
 * Hook ViewRootImpl.performTraversals()，在方法执行前修改 mWindowAttributes，
 * 每帧设置 colorMode=2 (HDR) + mDesiredHdrHeadroom=ratio，
 * 使状态栏窗口在 SurfaceFlinger 层面切换至 HDR 色彩空间。
 *
 * 执行流程：
 *   我们的 hook（方法执行前）：
 *     - 读取 mWindowAttributes，检查是否为 StatusBar
 *     - 修改 mWindowAttributes 的 colorMode + headroom
 *   performTraversals() 开始执行：
 *     1. mWindowAttributesCopy = new LayoutParams(mWindowAttributes) ← 副本已含 HDR
 *     2. mWindowAttributes.xxx = yyy ← 标志位赋值
 *     3. relayoutWindow(mWindowAttributes, ...) ← WMS 收到 HDR 参数
 *     4. mWindowAttributes = mWindowAttributesCopy ← 从副本恢复（副本也含 HDR）
 *
 *   结果：HDR 在当前帧生效，且在后续帧持久化。
 *
 * 性能约定：
 * - [initialize] 只做准备，真正的 performTraversals / setWideGamut hook 延迟到首次 enable；
 * - HDR 完全空闲（未启用、无需恢复 SDR、无 overlay probe）时，hook 回调零反射零日志；
 * - disable 后在 SDR 窗口恢复完成（或超时）且 overlay probe 未激活时卸掉 hook；再次 enable 可重装；
 * - 非状态栏 ViewRootImpl 在首次识别后用弱引用集合跳过。
 */
object HdrStatusBarController {
    private const val TAG = "HdrStatusBarController"
    private const val COLOR_MODE_HDR = 2
    private const val TYPE_STATUS_BAR = 2000
    private const val DEFAULT_HDR_RATIO = 1.5f
    private const val MIN_HDR_RATIO = 1.0f
    private const val MAX_HDR_RATIO = 4.0f

    /** 热路径诊断日志开关：开启后每 ~300 帧打一次，默认关闭以减负 */
    private const val HOT_PATH_DIAG = false

    /**
     * disable 后若状态栏迟迟不走 performTraversals，超时后放弃等待并卸钩，
     * 避免 hook 永久残留。窗口 colorMode 可能延后到下次自然 relayout 才回到 SDR。
     */
    private const val RESTORE_UNHOOK_FALLBACK_MS = 2000L

    private var unhookHandle: XposedInterface.HookHandle? = null
    private var wideGamutHookHandle: XposedInterface.HookHandle? = null

    /** 延迟安装 hook 用：initialize 只缓存引用，首次 enable 再挂钩 */
    private var moduleRef: XposedModule? = null
    private var classLoaderRef: ClassLoader? = null
    @Volatile
    private var hooksInstalled = false
    private val hookInstallLock = Any()

    /** disable 后等待 SDR 恢复的超时卸钩任务 */
    private var restoreUnhookFallback: Runnable? = null

    /**
     * 已确认非状态栏（且非 overlay probe）的 ViewRootImpl。
     * WeakHashMap 键弱引用，窗口销毁后自动回收，避免每帧对无关窗口做反射。
     */
    private val nonStatusBarViewRoots =
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    private var hdrHeadroomField: java.lang.reflect.Field? = null
    private var headroomFieldResolved = false
    private var headroomFieldNotFoundLogged = false

    /** 缓存的 mWindowAttributes 字段反射，搜索父类，避免每帧反射查找 */
    private var mWindowAttributesField: java.lang.reflect.Field? = null
    private var mWindowAttributesResolved = false

    private var mSurfaceControlField: java.lang.reflect.Field? = null
    private var mSurfaceControlResolved = false
    private var mSurfaceControlFieldNotFoundLogged = false
    private var mAttachInfoField: java.lang.reflect.Field? = null
    private var mAttachInfoResolved = false
    private var mThreadedRendererField: java.lang.reflect.Field? = null
    private var mThreadedRendererResolved = false

    private var surfaceControlClass: Class<*>? = null
    private var surfaceTransactionClass: Class<*>? = null
    private var transactionSetDesiredHdrHeadroom: java.lang.reflect.Method? = null
    private var transactionSetExtendedRangeBrightness: java.lang.reflect.Method? = null
    private var transactionSetDataSpace: java.lang.reflect.Method? = null
    private var transactionApply: java.lang.reflect.Method? = null
    private var surfaceIsValidMethod: java.lang.reflect.Method? = null
    private var surfaceTransactionResolved = false
    private var surfaceTransactionFailureLogged = false
    private var scrgbDataSpace: Int = 0
    private var scrgbLinearDataSpace: Int = 0
    private var unknownDataSpace: Int = 0

    @Volatile
    private var lastStatusBarSurface: Any? = null
    private var lastHdrAppliedSurface: Any? = null
    private var lastHdrAppliedRatio: Float = DEFAULT_HDR_RATIO
    private var lastSurfaceValid: Boolean? = null
    private var lastSurfaceTransactionSucceeded: Boolean? = null
    private var lastHdrTransactionFrame: Long = -1L
    private var lastWindowHdrFrame: Long = -1L
    private var lastRestoreSurface: Any? = null
    private var pulseFrameCount: Long = 0L
    private var lastProbeSurface: Any? = null
    private var lastProbeSurfaceValid: Boolean? = null
    private var lastProbeTransactionSucceeded: Boolean? = null
    private var lastProbeRestoreSurface: Any? = null
    private var overlayProbeWindowActive: Boolean = false
    private var overlayProbeRatio: Float = DEFAULT_HDR_RATIO
    private var lastOverlayRootSurface: Any? = null
    private var lastOverlayRootSurfaceValid: Boolean? = null
    private var lastOverlayRootTransactionSucceeded: Boolean? = null
    private var lastOverlayRootRestoreSurface: Any? = null
    private var rendererSetWideGamutMethod: java.lang.reflect.Method? = null
    private var rendererSetWideGamutResolved = false
    private var rendererSetWideGamutFailureLogged = false
    private var lastWideGamutRenderer: Any? = null
    private var lastHdrForcedRenderer: Any? = null
    private var lastHdrForcedVri: Any? = null
    private var lastHdrForcedRatio: Float = DEFAULT_HDR_RATIO
    private var lastWideGamutHookFrame: Long = -1L
    private val rendererIntrospectionLoggedClasses = mutableSetOf<String>()
    private val rendererForcePlans = mutableMapOf<String, RendererForcePlan>()
    private val rendererForceFailureLogged = mutableSetOf<String>()

    private data class RendererForcePlan(
        val booleanMethods: List<java.lang.reflect.Method>,
        val intMethods: List<java.lang.reflect.Method>,
        val floatMethods: List<java.lang.reflect.Method>,
        val intFloatMethods: List<java.lang.reflect.Method>,
        val nativeBooleanMethods: List<java.lang.reflect.Method>,
        val nativeIntMethods: List<java.lang.reflect.Method>,
        val nativeFloatMethods: List<java.lang.reflect.Method>,
        val booleanFields: List<java.lang.reflect.Field>,
        val intFields: List<java.lang.reflect.Field>,
        val floatFields: List<java.lang.reflect.Field>,
        val nativeProxyFields: List<java.lang.reflect.Field>
    ) {
        val isEmpty: Boolean
            get() = booleanMethods.isEmpty() &&
                    intMethods.isEmpty() &&
                    floatMethods.isEmpty() &&
                    intFloatMethods.isEmpty() &&
                    nativeBooleanMethods.isEmpty() &&
                    nativeIntMethods.isEmpty() &&
                    nativeFloatMethods.isEmpty() &&
                    booleanFields.isEmpty() &&
                    intFields.isEmpty() &&
                    floatFields.isEmpty()
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var hdrFrameCallbackScheduled = false

    private val hdrFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isEnabled || !applyRootSurfaceTransaction) {
                hdrFrameCallbackScheduled = false
                return
            }

            pulseFrameCount++
            val shouldLog = pulseFrameCount % 300 == 1L
            val surface = lastStatusBarSurface
            if (surface == null) {
                if (shouldLog) {
                    YLog.info(TAG, "Surface HDR pulse waiting for StatusBar surface")
                }
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            val valid = isSurfaceValid(surface)
            if (valid != lastSurfaceValid || shouldLog) {
                YLog.info(TAG, "StatusBar SurfaceControl valid=$valid surface=$surface source=pulse")
                lastSurfaceValid = valid
            }
            if (!valid && surface === lastHdrAppliedSurface) {
                clearStatusBarSurfaceHdrCache()
            }
            if (valid) {
                ensureStatusBarSurfaceHdr(surface, currentRatio, "pulse", shouldLog)
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @Volatile
    var isEnabled: Boolean = false
        private set

    @Volatile
    private var currentRatio: Float = DEFAULT_HDR_RATIO

    @Volatile
    private var applyRootSurfaceTransaction: Boolean = true

    @Volatile
    private var shouldRestoreWindowSdr: Boolean = false

    @Volatile
    var isInitialized: Boolean = false
        private set

    /**
     * 启动期只做准备：缓存 module/ClassLoader，标记可启用。
     * 真正挂钩 [ViewRootImpl.performTraversals] 推迟到首次 [enable]，
     * 避免 SystemUI 冷启动数百帧都被 Xposed 拦截。
     */
    fun initialize(module: XposedModule, classLoader: ClassLoader) {
        YLog.info(TAG, "initialize() called (hooks deferred until HDR enable)")
        moduleRef = module
        classLoaderRef = classLoader
        isInitialized = true
    }

    /**
     * 安装 performTraversals / setWideGamut hook（空闲卸钩后可再次安装）。
     * 由 [enable] / [enableWindowOnlyForProbe] / [enableOverlayWindowProbe] 触发。
     */
    private fun ensureHooksInstalled() {
        if (hooksInstalled) return
        synchronized(hookInstallLock) {
            if (hooksInstalled) return
            val module = moduleRef
            val classLoader = classLoaderRef
            if (module == null || classLoader == null) {
                YLog.warning(TAG, "ensureHooksInstalled skipped: not initialized")
                return
            }
            try {
                unhookHandle?.unhook()
                wideGamutHookHandle?.unhook()

                val viewRootImplClass = classLoader.loadClass("android.view.ViewRootImpl")

                val traversalsMethod = findPerformTraversals(viewRootImplClass)
                if (traversalsMethod == null) {
                    YLog.error(TAG, "performTraversals() not found")
                    return
                }
                YLog.info(TAG, "Found performTraversals: $traversalsMethod")

                @Suppress("ObjectLiteralToLambda")
                unhookHandle =
                    module.hook(traversalsMethod)
                        .intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                onPerformTraversals(chain)
                                return chain.proceed()
                            }
                        })
                YLog.info(TAG, "Hook installed: $unhookHandle")
                installWideGamutHook(module, classLoader)

                val preResolve = resolveWindowAttributesField(viewRootImplClass)
                YLog.info(TAG, "mWindowAttributes field resolved: $preResolve")

                val preResolveSurface = resolveSurfaceControlField(viewRootImplClass)
                YLog.info(TAG, "mSurfaceControl field resolved: $preResolveSurface")
                val preResolveAttachInfo = resolveAttachInfoField(viewRootImplClass)
                YLog.info(TAG, "mAttachInfo field resolved: $preResolveAttachInfo")

                hooksInstalled = true
            } catch (t: Throwable) {
                YLog.error(TAG, "ensureHooksInstalled failed", t)
            }
        }
    }

    /** 是否仍需要保持 performTraversals / wideGamut hook */
    private fun hooksStillNeeded(): Boolean {
        return isEnabled || shouldRestoreWindowSdr || overlayProbeWindowActive
    }

    /**
     * 在主线程下一拍尝试卸钩：先退出当前 intercept，避免与正在执行的 traversal 竞态。
     * 若此时又被 enable / overlay probe 重新需要 hook，则保留。
     */
    private fun scheduleMaybeUnhookHooksIfIdle() {
        mainHandler.post {
            maybeUnhookHooksIfIdle()
        }
    }

    /**
     * 空闲时卸掉 performTraversals 与 setWideGamut hook，并重置 [hooksInstalled]，
     * 以便下次 [ensureHooksInstalled] 重新安装。
     */
    private fun maybeUnhookHooksIfIdle() {
        synchronized(hookInstallLock) {
            if (!hooksInstalled) return
            if (hooksStillNeeded()) {
                YLog.info(
                    TAG,
                    "Skip unhook: enabled=$isEnabled restoring=$shouldRestoreWindowSdr " +
                            "overlayProbe=$overlayProbeWindowActive"
                )
                return
            }
            try {
                unhookHandle?.unhook()
                wideGamutHookHandle?.unhook()
                YLog.info(TAG, "HDR hooks uninstalled (idle)")
            } catch (t: Throwable) {
                YLog.error(TAG, "unhook failed", t)
            }
            unhookHandle = null
            wideGamutHookHandle = null
            hooksInstalled = false
        }
    }

    private fun scheduleRestoreUnhookFallback() {
        mainHandler.post {
            restoreUnhookFallback?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                restoreUnhookFallback = null
                if (shouldRestoreWindowSdr) {
                    YLog.warning(
                        TAG,
                        "SDR restore traversal timed out after ${RESTORE_UNHOOK_FALLBACK_MS}ms; " +
                                "clearing restore flag and unhooking if idle"
                    )
                    shouldRestoreWindowSdr = false
                }
                maybeUnhookHooksIfIdle()
            }
            restoreUnhookFallback = runnable
            mainHandler.postDelayed(runnable, RESTORE_UNHOOK_FALLBACK_MS)
        }
    }

    private fun cancelRestoreUnhookFallback() {
        mainHandler.post {
            restoreUnhookFallback?.let { mainHandler.removeCallbacks(it) }
            restoreUnhookFallback = null
        }
    }

    private fun installWideGamutHook(module: XposedModule, classLoader: ClassLoader) {
        try {
            val method = listOf(
                "android.view.ThreadedRenderer",
                "android.graphics.HardwareRenderer"
            ).firstNotNullOfOrNull { className ->
                runCatching {
                    findSetWideGamutMethod(classLoader.loadClass(className))
                }.getOrNull()
            }

            if (method == null) {
                YLog.warning(TAG, "Exact ThreadedRenderer.setWideGamut(boolean) not found; runtime renderer probe remains enabled")
                return
            }
            method.isAccessible = true
            wideGamutHookHandle =
                module.hook(method)
                    .intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isEnabled && chain.args.isNotEmpty() && chain.args[0] == false) {
                                chain.args[0] = true
                                if (frameCount - lastWideGamutHookFrame > 300) {
                                    lastWideGamutHookFrame = frameCount
                                    YLog.info(TAG, "ThreadedRenderer.setWideGamut(false) forced to true")
                                }
                            }
                            return chain.proceed()
                        }
                    })
            rendererSetWideGamutMethod = method
            rendererSetWideGamutResolved = true
            YLog.info(TAG, "Wide gamut hook installed: $method")
        } catch (t: Throwable) {
            YLog.error(TAG, "installWideGamutHook failed", t)
        }
    }

    /**
     * 在 ViewRootImpl 及父类中递归查找 mWindowAttributes 字段并缓存。
     */
    private fun resolveWindowAttributesField(viewRootImplClass: Class<*>): java.lang.reflect.Field? {
        if (mWindowAttributesResolved) return mWindowAttributesField
        var clazz: Class<*>? = viewRootImplClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField("mWindowAttributes")
                field.isAccessible = true
                mWindowAttributesField = field
                mWindowAttributesResolved = true
                YLog.info(TAG, "Found mWindowAttributes in ${clazz.simpleName}")
                return field
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        mWindowAttributesResolved = true
        YLog.warning(TAG, "mWindowAttributes not found in any superclass of ViewRootImpl")
        return null
    }

    private fun resolveSurfaceControlField(viewRootImplClass: Class<*>): java.lang.reflect.Field? {
        if (mSurfaceControlResolved) return mSurfaceControlField
        mSurfaceControlResolved = true
        try {
            val surfaceClass = Class.forName("android.view.SurfaceControl")
            surfaceControlClass = surfaceClass

            var clazz: Class<*>? = viewRootImplClass
            while (clazz != null && clazz != Any::class.java) {
                try {
                    val field = clazz.getDeclaredField("mSurfaceControl")
                    if (field.type == surfaceClass) {
                        field.isAccessible = true
                        mSurfaceControlField = field
                        YLog.info(TAG, "Found mSurfaceControl in ${clazz.simpleName}")
                        return field
                    }
                    YLog.warning(
                        TAG,
                        "mSurfaceControl found in ${clazz.simpleName}, but type=${field.type.name}; expected ${surfaceClass.name}"
                    )
                    return null
                } catch (_: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }
            if (!mSurfaceControlFieldNotFoundLogged) {
                YLog.warning(TAG, "mSurfaceControl not found in ViewRootImpl hierarchy; Surface HDR path unavailable")
                mSurfaceControlFieldNotFoundLogged = true
            }
        } catch (e: Exception) {
            YLog.error(TAG, "resolveSurfaceControlField failed", e)
        }
        return null
    }

    private fun resolveAttachInfoField(viewRootImplClass: Class<*>): java.lang.reflect.Field? {
        if (mAttachInfoResolved) return mAttachInfoField
        mAttachInfoResolved = true
        var clazz: Class<*>? = viewRootImplClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField("mAttachInfo")
                field.isAccessible = true
                mAttachInfoField = field
                YLog.info(TAG, "Found mAttachInfo in ${clazz.simpleName}")
                return field
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        YLog.warning(TAG, "mAttachInfo not found in ViewRootImpl hierarchy")
        return null
    }

    private fun resolveThreadedRendererField(attachInfoClass: Class<*>): java.lang.reflect.Field? {
        if (mThreadedRendererResolved) return mThreadedRendererField
        mThreadedRendererResolved = true
        try {
            runCatching {
                attachInfoClass.getDeclaredField("mThreadedRenderer")
            }.getOrNull()?.let { field ->
                field.isAccessible = true
                mThreadedRendererField = field
                YLog.info(TAG, "Found mThreadedRenderer in ${attachInfoClass.name}")
                return field
            }

            val field = attachInfoClass.declaredFields.firstOrNull {
                it.name.contains("renderer", ignoreCase = true)
            }
            if (field != null) {
                field.isAccessible = true
                mThreadedRendererField = field
                YLog.info(TAG, "Found renderer candidate ${field.name} in ${attachInfoClass.name}")
                return field
            }
        } catch (e: Exception) {
            YLog.error(TAG, "resolveThreadedRendererField failed", e)
        }
        YLog.warning(TAG, "mThreadedRenderer not found in ${attachInfoClass.name}")
        return null
    }

    private fun findPerformTraversals(clazz: Class<*>): java.lang.reflect.Method? {
        return clazz.declaredMethods
            .filter { it.name == "performTraversals" && it.parameterCount == 0 }
            .also {
                YLog.info(TAG, "Found ${it.size} performTraversals() methods")
                it.forEach { m -> YLog.info(TAG, "  $m") }
            }
            .firstOrNull()
    }

    private fun findSetWideGamutMethod(clazz: Class<*>): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val method = current.declaredMethods.firstOrNull {
                it.name == "setWideGamut" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == java.lang.Boolean.TYPE
            }
            if (method != null) return method
            current = current.superclass
        }
        return null
    }

    /**
     * 在 performTraversals() 执行前修改 mWindowAttributes。
     *
     * 当 isEnabled=true 时，每帧设置 colorMode=2 + headroom。
     * 当完全空闲（未启用、无需恢复、无 overlay probe）时立即返回，零反射。
     */
    private var frameCount: Long = 0

    private fun onPerformTraversals(chain: XposedInterface.Chain) {
        // 空闲快路径：HDR 关、无需恢复 SDR、无 probe → 不碰反射/日志/分配
        val enabled = isEnabled
        val restoring = shouldRestoreWindowSdr
        val probing = overlayProbeWindowActive
        if (!enabled && !restoring && !probing) return

        val vri = chain.thisObject ?: return

        // 已识别的非状态栏窗口：无 probe 时直接跳过（含仅恢复 SDR 的场景）
        if (!probing && nonStatusBarViewRoots.contains(vri)) return

        frameCount++
        val shouldDiag = HOT_PATH_DIAG && frameCount % 300 == 1L

        try {
            val field = resolveWindowAttributesField(vri.javaClass)
            if (field == null) {
                if (!mWindowAttributesResolved) {
                    YLog.warning(TAG, "mWindowAttributes field unresolved, skipping HDR")
                }
                return
            }

            val lp = field.get(vri) as? WindowManager.LayoutParams ?: return
            // overlay probe 未激活时跳过 title 字符串比较，避免每帧分配
            if (probing && handleOverlayProbeWindow(vri, lp)) return

            if (lp.type != TYPE_STATUS_BAR) {
                nonStatusBarViewRoots.add(vri)
                return
            }

            if (shouldDiag) {
                val surfaceForLog = getRootSurface(vri)
                val validForLog = if (surfaceForLog != null) isSurfaceValid(surfaceForLog) else null
                YLog.info(
                    TAG,
                    "=== FRAME #$frameCount ===" +
                            " isEnabled=$enabled" +
                            " rootSurfaceTransaction=$applyRootSurfaceTransaction" +
                            " cm=${lp.colorMode} ratio=$currentRatio" +
                            " surface=$surfaceForLog valid=$validForLog"
                )
            }

            if (!enabled) {
                if (restoring) {
                    applySdr(lp)
                    restoreRendererSdr(vri, shouldLog = true)
                    shouldRestoreWindowSdr = false
                    cancelRestoreUnhookFallback()
                    // 下一拍再卸钩：先完成本次 intercept，避免与 unhook 竞态
                    scheduleMaybeUnhookHooksIfIdle()
                }
                return
            }

            val windowNeedsHdrApply = lp.colorMode != COLOR_MODE_HDR || lastWindowHdrFrame < 0L
            if (windowNeedsHdrApply) {
                clearStatusBarSurfaceHdrCache()
                clearRendererHdrCache()
            }

            // 仅在首次应用或诊断开启时打日志，避免热路径字符串拼接
            val shouldLogWindow = shouldDiag || windowNeedsHdrApply
            applyHdr(lp, shouldLogWindow)
            forceRendererWideGamut(vri, shouldLogWindow)
            lastWindowHdrFrame = frameCount

            if (!applyRootSurfaceTransaction) return

            // 稳定态：窗口属性 / renderer / surface transaction 均已生效 → 跳过 getRootSurface+isValid
            if (!windowNeedsHdrApply &&
                !shouldDiag &&
                lastStatusBarSurface != null &&
                lastHdrAppliedSurface === lastStatusBarSurface &&
                lastSurfaceTransactionSucceeded == true &&
                lastHdrAppliedRatio == currentRatio
            ) {
                return
            }

            val surface = getRootSurface(vri) ?: return
            lastStatusBarSurface = surface

            val shouldLog = shouldDiag ||
                    lastHdrTransactionFrame < 0L ||
                    surface !== lastHdrAppliedSurface ||
                    windowNeedsHdrApply
            val valid = isSurfaceValid(surface)
            if (valid != lastSurfaceValid || shouldLog) {
                YLog.info(TAG, "StatusBar SurfaceControl valid=$valid surface=$surface")
                lastSurfaceValid = valid
            }
            if (!valid) {
                if (surface === lastHdrAppliedSurface) clearStatusBarSurfaceHdrCache()
                return
            }

            ensureStatusBarSurfaceHdr(surface, currentRatio, "traversal", shouldLog)
        } catch (e: Exception) {
            YLog.error(TAG, "onPerformTraversals failed", e)
        }
    }

    fun enable(ratio: Float = DEFAULT_HDR_RATIO) {
        val clampedRatio = ratio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        if (isEnabled && applyRootSurfaceTransaction && currentRatio == clampedRatio && hooksInstalled) {
            return
        }
        // 先声明仍需要 hook，再安装，避免与 maybeUnhook 竞态卸掉刚要用的钩子
        shouldRestoreWindowSdr = false
        cancelRestoreUnhookFallback()
        isEnabled = true
        applyRootSurfaceTransaction = true
        currentRatio = clampedRatio
        ensureHooksInstalled()
        lastWindowHdrFrame = -1L
        clearStatusBarSurfaceHdrCache()
        stopSurfacePulse()
        YLog.info(TAG, "HDR enabled, ratio=$clampedRatio")
    }

    fun enableWindowOnlyForProbe(ratio: Float = DEFAULT_HDR_RATIO) {
        val clampedRatio = ratio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        if (isEnabled && !applyRootSurfaceTransaction && currentRatio == clampedRatio && hooksInstalled) {
            return
        }

        val shouldRestoreRootSurface = isEnabled && applyRootSurfaceTransaction
        shouldRestoreWindowSdr = false
        cancelRestoreUnhookFallback()
        isEnabled = true
        applyRootSurfaceTransaction = false
        currentRatio = clampedRatio
        ensureHooksInstalled()
        lastWindowHdrFrame = -1L
        clearStatusBarSurfaceHdrCache()
        stopSurfacePulse()

        val surface = lastStatusBarSurface
        val restored = if (shouldRestoreRootSurface && surface != null) {
            restoreSurfaceSdr(surface)
        } else {
            false
        }
        YLog.info(
            TAG,
            "HDR window-only probe mode enabled, ratio=$clampedRatio " +
                    "rootSurfaceTransaction=false restoredRootSurface=$restored surface=$surface"
        )
    }

    fun disable() {
        if (!isEnabled) return
        // 先置 restoring 再清 enabled，避免中间态被误判为空闲而卸钩
        shouldRestoreWindowSdr = true
        isEnabled = false
        val shouldRestoreRootSurface = applyRootSurfaceTransaction
        applyRootSurfaceTransaction = false
        lastWindowHdrFrame = -1L
        stopSurfacePulse()
        val surface = lastStatusBarSurface
        val restored = if (shouldRestoreRootSurface && surface != null) restoreSurfaceSdr(surface) else false
        clearStatusBarSurfaceHdrCache()
        clearRendererHdrCache()
        // 等状态栏下一次 performTraversals 写回 SDR；超时则强制卸钩
        scheduleRestoreUnhookFallback()
        YLog.info(
            TAG,
            "HDR disabled, surfaceRestored=$restored surface=$surface " +
                    "waitingSdrRestore=true overlayProbe=$overlayProbeWindowActive"
        )
    }

    fun restoreStatusBarSurfaceForProbe(): Boolean {
        if (isEnabled) {
            disable()
            return true
        }
        val surface = lastStatusBarSurface ?: return false
        return restoreSurfaceSdr(surface)
    }

    fun isSurfaceControlValidForProbe(surface: Any?): Boolean {
        return surface != null && isSurfaceValid(surface)
    }

    fun applyHdrToProbeSurface(
        surface: Any?,
        ratio: Float,
        source: String,
        shouldLog: Boolean
    ): Boolean {
        val clampedRatio = ratio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        if (surface == null) {
            if (shouldLog || lastProbeSurface != null) {
                YLog.warning(TAG, "Probe Surface HDR skipped: source=$source surface=null ratio=$clampedRatio")
            }
            lastProbeSurface = null
            lastProbeSurfaceValid = false
            lastProbeTransactionSucceeded = false
            return false
        }

        val valid = isSurfaceValid(surface)
        if (shouldLog || surface !== lastProbeSurface || valid != lastProbeSurfaceValid) {
            YLog.info(TAG, "Probe SurfaceControl valid=$valid source=$source surface=$surface")
        }
        lastProbeSurface = surface
        lastProbeSurfaceValid = valid
        if (!valid) {
            lastProbeTransactionSucceeded = false
            return false
        }

        if (!resolveSurfaceTransactionMethods()) {
            lastProbeTransactionSucceeded = false
            return false
        }
        val probeDataSpace = if (scrgbLinearDataSpace != 0) scrgbLinearDataSpace else scrgbDataSpace
        val applied = applySurfaceTransaction(
            surface = surface,
            desiredRatio = clampedRatio,
            dataSpace = probeDataSpace,
            label = "Probe HDR",
            shouldLogFailure = shouldLog || lastProbeTransactionSucceeded != false
        )
        if (applied) {
            if (shouldLog || lastProbeTransactionSucceeded != true) {
                YLog.info(
                    TAG,
                    "Probe Surface HDR transaction applied: source=$source ratio=$clampedRatio dataSpace=$probeDataSpace surface=$surface"
                )
            }
        } else if (shouldLog || lastProbeTransactionSucceeded != false) {
            YLog.warning(TAG, "Probe Surface HDR transaction failed: source=$source ratio=$clampedRatio surface=$surface")
        }
        lastProbeTransactionSucceeded = applied
        return applied
    }

    fun restoreProbeSurfaceSdr(surface: Any?, source: String, shouldLog: Boolean = true): Boolean {
        if (surface == null) {
            if (shouldLog) {
                YLog.info(TAG, "Probe Surface SDR restore skipped: source=$source surface=null")
            }
            return false
        }
        val valid = isSurfaceValid(surface)
        if (!valid) {
            if (shouldLog) {
                YLog.warning(TAG, "Probe Surface SDR restore skipped: source=$source invalid surface=$surface")
            }
            return false
        }
        if (!resolveSurfaceTransactionMethods()) return false
        val restored = applySurfaceTransaction(
            surface = surface,
            desiredRatio = 1.0f,
            dataSpace = unknownDataSpace,
            label = "Probe SDR",
            shouldLogFailure = shouldLog
        )
        if (shouldLog || restored || surface !== lastProbeRestoreSurface) {
            YLog.info(
                TAG,
                "Probe Surface SDR restore result=$restored source=$source dataSpace=$unknownDataSpace surface=$surface"
            )
            lastProbeRestoreSurface = surface
        }
        if (surface === lastProbeSurface) {
            lastProbeTransactionSucceeded = false
        }
        return restored
    }

    fun enableOverlayWindowProbe(ratio: Float) {
        val clampedRatio = ratio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        // 先置 probe 标志再装 hook，避免与 idle unhook 竞态
        overlayProbeWindowActive = true
        overlayProbeRatio = clampedRatio
        ensureHooksInstalled()
    }

    fun disableOverlayWindowProbe() {
        overlayProbeWindowActive = false
        val surface = lastOverlayRootSurface
        if (surface != null) {
            restoreOverlayRootSurfaceSdr(surface)
        }
        lastOverlayRootSurface = null
        lastOverlayRootSurfaceValid = null
        lastOverlayRootTransactionSucceeded = null
        // probe 结束后若 HDR 也已关闭且不在恢复中，卸掉 hook
        scheduleMaybeUnhookHooksIfIdle()
    }

    fun applyHdrToWindowLayoutParams(
        lp: WindowManager.LayoutParams,
        ratio: Float,
        source: String,
        shouldLog: Boolean = true
    ) {
        val clampedRatio = ratio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        try {
            val oldColorMode = lp.colorMode
            lp.colorMode = COLOR_MODE_HDR
            setHeadroom(lp, clampedRatio)
            if (shouldLog || oldColorMode != lp.colorMode) {
                YLog.info(
                    TAG,
                    "applyHdrToWindowLayoutParams: source=$source type=${lp.type} " +
                            "colorMode $oldColorMode -> ${lp.colorMode}, headroom=$clampedRatio"
                )
            }
        } catch (e: Exception) {
            YLog.error(TAG, "applyHdrToWindowLayoutParams failed: source=$source", e)
        }
    }

    private fun handleOverlayProbeWindow(vri: Any, lp: WindowManager.LayoutParams): Boolean {
        val title = lp.title?.toString() ?: return false
        if (title != OVERLAY_PROBE_WINDOW_TITLE) return false

        if (!overlayProbeWindowActive) {
            return true
        }

        val shouldLog = frameCount % 300 == 1L || lastOverlayRootTransactionSucceeded != true
        applyHdrToWindowLayoutParams(lp, overlayProbeRatio, OVERLAY_ROOT_SOURCE, shouldLog)

        val surface = getRootSurface(vri) ?: return true
        lastOverlayRootSurface = surface
        val valid = isSurfaceValid(surface)
        if (valid != lastOverlayRootSurfaceValid || shouldLog) {
            YLog.info(
                TAG,
                "Overlay root SurfaceControl valid=$valid source=$OVERLAY_ROOT_SOURCE surface=$surface"
            )
            lastOverlayRootSurfaceValid = valid
        }
        if (!valid) {
            lastOverlayRootTransactionSucceeded = false
            return true
        }

        val applied = applyHdrToOverlayRootSurface(surface, overlayProbeRatio, shouldLog)
        lastOverlayRootTransactionSucceeded = applied
        return true
    }

    private fun startSurfacePulse() {
        mainHandler.post {
            if (!isEnabled || hdrFrameCallbackScheduled) return@post
            pulseFrameCount = 0L
            hdrFrameCallbackScheduled = true
            Choreographer.getInstance().postFrameCallback(hdrFrameCallback)
            YLog.info(TAG, "Surface HDR pulse started")
        }
    }

    private fun stopSurfacePulse() {
        mainHandler.post {
            if (!hdrFrameCallbackScheduled) return@post
            Choreographer.getInstance().removeFrameCallback(hdrFrameCallback)
            hdrFrameCallbackScheduled = false
            YLog.info(TAG, "Surface HDR pulse stopped")
        }
    }

    private fun applyHdr(lp: WindowManager.LayoutParams, shouldLog: Boolean) {
        try {
            val oldColorMode = lp.colorMode
            if (oldColorMode == COLOR_MODE_HDR && lastWindowHdrFrame >= 0L) {
                if (shouldLog) {
                    YLog.info(TAG, "applyHdr kept: colorMode=$oldColorMode, headroom=$currentRatio")
                }
                return
            }
            lp.colorMode = COLOR_MODE_HDR
            setHeadroom(lp, currentRatio)
            if (shouldLog || oldColorMode != lp.colorMode) {
                YLog.info(TAG,
                    "applyHdr: colorMode $oldColorMode -> ${lp.colorMode}, headroom=$currentRatio")
            }
        } catch (e: Exception) {
            YLog.error(TAG, "applyHdr failed", e)
        }
    }

    private fun applySdr(lp: WindowManager.LayoutParams) {
        try {
            val oldColorMode = lp.colorMode
            lp.colorMode = 0
            setHeadroom(lp, MIN_HDR_RATIO)
            YLog.info(TAG, "applySdr: colorMode $oldColorMode -> ${lp.colorMode}, headroom=$MIN_HDR_RATIO")
        } catch (e: Exception) {
            YLog.error(TAG, "applySdr failed", e)
        }
    }

    private fun forceRendererWideGamut(vri: Any, shouldLog: Boolean) {
        val ratio = currentRatio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        // 同一 StatusBar VRI + 比率未变：跳过 AttachInfo/Renderer 反射
        if (vri === lastHdrForcedVri &&
            lastHdrForcedRenderer != null &&
            lastHdrForcedRatio == ratio
        ) {
            if (shouldLog) {
                YLog.info(
                    TAG,
                    "Renderer HDR hints kept: vri-cached ratio=$ratio"
                )
            }
            return
        }

        val renderer = getThreadedRenderer(vri) ?: return
        val rendererClass = renderer.javaClass
        if (renderer === lastHdrForcedRenderer && lastHdrForcedRatio == ratio) {
            lastHdrForcedVri = vri
            if (shouldLog) {
                YLog.info(
                    TAG,
                    "Renderer HDR hints kept: class=${rendererClass.name} ratio=$ratio"
                )
            }
            return
        }

        logRendererIntrospection(rendererClass)

        val plan = resolveRendererForcePlan(rendererClass)
        applyRendererForcePlan(
            renderer = renderer,
            rendererClass = rendererClass,
            plan = plan,
            colorMode = COLOR_MODE_HDR,
            ratio = ratio,
            booleanValue = true,
            shouldLog = shouldLog,
            logLabel = "Renderer HDR hints forced"
        )
        lastHdrForcedRenderer = renderer
        lastHdrForcedVri = vri
        lastHdrForcedRatio = ratio
    }

    private fun restoreRendererSdr(vri: Any, shouldLog: Boolean) {
        val renderer = getThreadedRenderer(vri) ?: return
        val rendererClass = renderer.javaClass
        val plan = resolveRendererForcePlan(rendererClass)
        applyRendererForcePlan(
            renderer = renderer,
            rendererClass = rendererClass,
            plan = plan,
            colorMode = 0,
            ratio = MIN_HDR_RATIO,
            booleanValue = false,
            shouldLog = shouldLog,
            logLabel = "Renderer SDR hints restored"
        )
        clearRendererHdrCache()
    }

    private fun clearRendererHdrCache() {
        lastHdrForcedRenderer = null
        lastHdrForcedVri = null
        lastHdrForcedRatio = DEFAULT_HDR_RATIO
    }

    private fun applyRendererForcePlan(
        renderer: Any,
        rendererClass: Class<*>,
        plan: RendererForcePlan,
        colorMode: Int,
        ratio: Float,
        booleanValue: Boolean,
        shouldLog: Boolean,
        logLabel: String
    ) {
        if (plan.isEmpty) {
            if (shouldLog || renderer !== lastWideGamutRenderer) {
                YLog.warning(TAG, "Renderer force plan empty: class=${rendererClass.name} label=$logLabel")
                lastWideGamutRenderer = renderer
            }
            return
        }

        var applied = 0
        var failed = 0
        val targets = mutableListOf<String>()

        plan.booleanMethods.forEach { method ->
            if (invokeRendererMethod(renderer, method, booleanValue)) {
                applied++
                addRendererTarget(targets, "method:${method.name}=$booleanValue")
            } else {
                failed++
            }
        }

        plan.intMethods.forEach { method ->
            val value = rendererIntValueForName(method.name, colorMode) ?: return@forEach
            if (invokeRendererMethod(renderer, method, value)) {
                applied++
                addRendererTarget(targets, "method:${method.name}=$value")
            } else {
                failed++
            }
        }

        plan.floatMethods.forEach { method ->
            val value = rendererFloatValueForName(method.name, ratio) ?: return@forEach
            if (invokeRendererMethod(renderer, method, value)) {
                applied++
                addRendererTarget(targets, "method:${method.name}=$value")
            } else {
                failed++
            }
        }

        plan.intFloatMethods.forEach { method ->
            val value = rendererFloatValueForName(method.name, ratio) ?: return@forEach
            if (invokeRendererMethod(renderer, method, colorMode, value)) {
                applied++
                addRendererTarget(targets, "method:${method.name}=$colorMode,$value")
            } else {
                failed++
            }
        }

        plan.booleanFields.forEach { field ->
            if (setRendererField(renderer, field, booleanValue)) {
                applied++
                addRendererTarget(targets, "field:${field.name}=$booleanValue")
            } else {
                failed++
            }
        }

        plan.intFields.forEach { field ->
            val value = rendererIntValueForName(field.name, colorMode) ?: return@forEach
            if (setRendererField(renderer, field, value)) {
                applied++
                addRendererTarget(targets, "field:${field.name}=$value")
            } else {
                failed++
            }
        }

        plan.floatFields.forEach { field ->
            val value = rendererFloatValueForName(field.name, ratio) ?: return@forEach
            if (setRendererField(renderer, field, value)) {
                applied++
                addRendererTarget(targets, "field:${field.name}=$value")
            } else {
                failed++
            }
        }

        val nativeProxy = getRendererNativeProxy(renderer, plan)
        if (nativeProxy != null) {
            plan.nativeBooleanMethods.forEach { method ->
                if (invokeRendererNativeMethod(renderer, method, nativeProxy, booleanValue)) {
                    applied++
                    addRendererTarget(targets, "native:${method.name}=$booleanValue")
                } else {
                    failed++
                }
            }

            plan.nativeIntMethods.forEach { method ->
                val value = rendererIntValueForName(method.name, colorMode) ?: return@forEach
                if (invokeRendererNativeMethod(renderer, method, nativeProxy, value)) {
                    applied++
                    addRendererTarget(targets, "native:${method.name}=$value")
                } else {
                    failed++
                }
            }

            plan.nativeFloatMethods.forEach { method ->
                val value = rendererFloatValueForName(method.name, ratio) ?: return@forEach
                if (invokeRendererNativeMethod(renderer, method, nativeProxy, value)) {
                    applied++
                    addRendererTarget(targets, "native:${method.name}=$value")
                } else {
                    failed++
                }
            }
        } else if (plan.nativeBooleanMethods.isNotEmpty() ||
            plan.nativeIntMethods.isNotEmpty() ||
            plan.nativeFloatMethods.isNotEmpty()) {
            logRendererForceFailure(
                "nativeProxy:${rendererClass.name}",
                "Renderer native HDR methods found, but native proxy is unavailable: class=${rendererClass.name}"
            )
        }

        if (shouldLog || renderer !== lastWideGamutRenderer) {
            YLog.info(
                TAG,
                "$logLabel: class=${rendererClass.name} applied=$applied failed=$failed " +
                        "nativeProxy=${nativeProxy != null} targets=${targets.joinToString()}"
            )
            lastWideGamutRenderer = renderer
        }
    }

    private fun getThreadedRenderer(vri: Any): Any? {
        val attachInfoField = resolveAttachInfoField(vri.javaClass) ?: return null
        val attachInfo = try {
            attachInfoField.get(vri)
        } catch (e: Exception) {
            YLog.error(TAG, "get AttachInfo failed", e)
            return null
        } ?: return null

        val rendererField = resolveThreadedRendererField(attachInfo.javaClass) ?: return null
        return try {
            rendererField.get(attachInfo)
        } catch (e: Exception) {
            YLog.error(TAG, "get ThreadedRenderer failed", e)
            null
        }
    }

    private fun resolveRendererForcePlan(rendererClass: Class<*>): RendererForcePlan {
        rendererForcePlans[rendererClass.name]?.let { return it }

        val methods = collectRendererMethods(rendererClass)
        val fields = collectRendererFields(rendererClass)
        val exactWideGamutMethod = findSetWideGamutMethod(rendererClass)
        val booleanMethods = (listOfNotNull(exactWideGamutMethod) + methods.filter(::isRendererBooleanForceMethod))
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val intMethods = methods
            .filter(::isRendererIntForceMethod)
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val floatMethods = methods
            .filter(::isRendererFloatForceMethod)
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val intFloatMethods = methods
            .filter(::isRendererIntFloatForceMethod)
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val nativeBooleanMethods = methods
            .filter(::isRendererNativeBooleanForceMethod)
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val nativeIntMethods = methods
            .filter(::isRendererNativeIntForceMethod)
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val nativeFloatMethods = methods
            .filter(::isRendererNativeFloatForceMethod)
            .distinctBy(::rendererMethodKey)
            .mapNotNull { makeRendererAccessible(it) }
        val booleanFields = fields
            .filter(::isRendererBooleanForceField)
            .distinctBy(::rendererFieldKey)
            .mapNotNull { makeRendererAccessible(it) }
        val intFields = fields
            .filter(::isRendererIntForceField)
            .distinctBy(::rendererFieldKey)
            .mapNotNull { makeRendererAccessible(it) }
        val floatFields = fields
            .filter(::isRendererFloatForceField)
            .distinctBy(::rendererFieldKey)
            .mapNotNull { makeRendererAccessible(it) }
        val nativeProxyFields = fields
            .filter(::isRendererNativeProxyField)
            .distinctBy(::rendererFieldKey)
            .sortedBy { if (it.name == "mNativeProxy") 0 else 1 }
            .mapNotNull { makeRendererAccessible(it) }

        val plan = RendererForcePlan(
            booleanMethods = booleanMethods,
            intMethods = intMethods,
            floatMethods = floatMethods,
            intFloatMethods = intFloatMethods,
            nativeBooleanMethods = nativeBooleanMethods,
            nativeIntMethods = nativeIntMethods,
            nativeFloatMethods = nativeFloatMethods,
            booleanFields = booleanFields,
            intFields = intFields,
            floatFields = floatFields,
            nativeProxyFields = nativeProxyFields
        )
        rendererForcePlans[rendererClass.name] = plan

        YLog.info(
            TAG,
            "Renderer HDR force plan: class=${rendererClass.name} " +
                    "boolMethods=${booleanMethods.joinToString { it.name }} " +
                    "intMethods=${intMethods.joinToString { it.name }} " +
                    "floatMethods=${floatMethods.joinToString { it.name }} " +
                    "intFloatMethods=${intFloatMethods.joinToString { it.name }} " +
                    "nativeBoolMethods=${nativeBooleanMethods.joinToString { it.name }} " +
                    "nativeIntMethods=${nativeIntMethods.joinToString { it.name }} " +
                    "nativeFloatMethods=${nativeFloatMethods.joinToString { it.name }} " +
                    "boolFields=${booleanFields.joinToString { it.name }} " +
                    "intFields=${intFields.joinToString { it.name }} " +
                    "floatFields=${floatFields.joinToString { it.name }} " +
                    "nativeProxyFields=${nativeProxyFields.joinToString { it.name }}"
        )
        return plan
    }

    private fun logRendererIntrospection(rendererClass: Class<*>) {
        if (!rendererIntrospectionLoggedClasses.add(rendererClass.name)) return

        val methods = collectRendererMethods(rendererClass)
            .filter { isRendererIntrospectionName(it.name) }
            .sortedWith(compareBy({ it.declaringClass.name }, { it.name }, { it.parameterCount }))
        val fields = collectRendererFields(rendererClass)
            .filter { isRendererIntrospectionName(it.name) || isRendererIntrospectionName(it.type.name) }
            .sortedWith(compareBy({ it.declaringClass.name }, { it.name }))

        YLog.info(
            TAG,
            "Renderer runtime introspection: class=${rendererClass.name} " +
                    "methods=${methods.size} fields=${fields.size}"
        )
        methods.take(RENDERER_INTROSPECTION_LIMIT).forEach {
            YLog.info(TAG, "  renderer method: ${describeRendererMethod(it)}")
        }
        if (methods.size > RENDERER_INTROSPECTION_LIMIT) {
            YLog.info(TAG, "  renderer methods omitted=${methods.size - RENDERER_INTROSPECTION_LIMIT}")
        }
        fields.take(RENDERER_INTROSPECTION_LIMIT).forEach {
            YLog.info(TAG, "  renderer field: ${describeRendererField(it)}")
        }
        if (fields.size > RENDERER_INTROSPECTION_LIMIT) {
            YLog.info(TAG, "  renderer fields omitted=${fields.size - RENDERER_INTROSPECTION_LIMIT}")
        }
    }

    private fun collectRendererMethods(rendererClass: Class<*>): List<java.lang.reflect.Method> {
        val methods = mutableListOf<java.lang.reflect.Method>()
        var clazz: Class<*>? = rendererClass
        while (clazz != null && clazz != Any::class.java) {
            methods.addAll(clazz.declaredMethods)
            clazz = clazz.superclass
        }
        return methods
    }

    private fun collectRendererFields(rendererClass: Class<*>): List<java.lang.reflect.Field> {
        val fields = mutableListOf<java.lang.reflect.Field>()
        var clazz: Class<*>? = rendererClass
        while (clazz != null && clazz != Any::class.java) {
            fields.addAll(clazz.declaredFields)
            clazz = clazz.superclass
        }
        return fields
    }

    private fun isRendererBooleanForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 1 || method.parameterTypes[0] != java.lang.Boolean.TYPE) return false
        if (java.lang.reflect.Modifier.isAbstract(method.modifiers)) return false
        val name = method.name.lowercase()
        if (name == "setwidegamut") return true
        return rendererHasBooleanHdrSignal(name) && isRendererMutatorName(name)
    }

    private fun isRendererIntForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 1 || method.parameterTypes[0] != java.lang.Integer.TYPE) return false
        if (java.lang.reflect.Modifier.isAbstract(method.modifiers)) return false
        val name = method.name.lowercase()
        return rendererHasIntHdrSignal(name) && isRendererMutatorName(name)
    }

    private fun isRendererFloatForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 1 || method.parameterTypes[0] != java.lang.Float.TYPE) return false
        if (java.lang.reflect.Modifier.isAbstract(method.modifiers)) return false
        val name = method.name.lowercase()
        return rendererHasFloatHdrSignal(name) && isRendererMutatorName(name)
    }

    private fun isRendererIntFloatForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 2) return false
        if (method.parameterTypes[0] != java.lang.Integer.TYPE ||
            method.parameterTypes[1] != java.lang.Float.TYPE) return false
        if (java.lang.reflect.Modifier.isAbstract(method.modifiers)) return false
        val name = method.name.lowercase()
        return rendererHasIntHdrSignal(name) && isRendererMutatorName(name)
    }

    private fun isRendererNativeBooleanForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 2) return false
        if (method.parameterTypes[0] != java.lang.Long.TYPE ||
            method.parameterTypes[1] != java.lang.Boolean.TYPE) return false
        val name = method.name.lowercase()
        return name.startsWith("n") && rendererHasBooleanHdrSignal(name)
    }

    private fun isRendererNativeIntForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 2) return false
        if (method.parameterTypes[0] != java.lang.Long.TYPE ||
            method.parameterTypes[1] != java.lang.Integer.TYPE) return false
        val name = method.name.lowercase()
        return name.startsWith("n") && rendererHasIntHdrSignal(name)
    }

    private fun isRendererNativeFloatForceMethod(method: java.lang.reflect.Method): Boolean {
        if (method.parameterCount != 2) return false
        if (method.parameterTypes[0] != java.lang.Long.TYPE ||
            method.parameterTypes[1] != java.lang.Float.TYPE) return false
        val name = method.name.lowercase()
        return name.startsWith("n") && rendererHasFloatHdrSignal(name)
    }

    private fun isRendererBooleanForceField(field: java.lang.reflect.Field): Boolean {
        if (!isBooleanType(field.type)) return false
        if (java.lang.reflect.Modifier.isStatic(field.modifiers) ||
            java.lang.reflect.Modifier.isFinal(field.modifiers)) return false
        return rendererHasBooleanHdrSignal(field.name.lowercase())
    }

    private fun isRendererIntForceField(field: java.lang.reflect.Field): Boolean {
        if (!isIntType(field.type)) return false
        if (java.lang.reflect.Modifier.isStatic(field.modifiers) ||
            java.lang.reflect.Modifier.isFinal(field.modifiers)) return false
        return rendererHasIntHdrSignal(field.name.lowercase())
    }

    private fun isRendererFloatForceField(field: java.lang.reflect.Field): Boolean {
        if (!isFloatType(field.type)) return false
        if (java.lang.reflect.Modifier.isStatic(field.modifiers) ||
            java.lang.reflect.Modifier.isFinal(field.modifiers)) return false
        return rendererHasFloatHdrSignal(field.name.lowercase())
    }

    private fun isRendererNativeProxyField(field: java.lang.reflect.Field): Boolean {
        if (!isLongType(field.type)) return false
        val name = field.name.lowercase()
        return name == "mnativeproxy" || (name.contains("native") && name.contains("proxy"))
    }

    private fun rendererHasBooleanHdrSignal(name: String): Boolean {
        if (rendererLooksUnrelatedColorName(name)) return false
        return name.contains("wide") ||
                name.contains("gamut") ||
                name.contains("hdr") ||
                name.contains("colorspace") ||
                name.contains("colormode")
    }

    private fun rendererHasIntHdrSignal(name: String): Boolean {
        if (rendererLooksUnrelatedColorName(name)) return false
        return name.contains("dataspace") ||
                name.contains("colorspace") ||
                name.contains("colormode") ||
                name.contains("wide") ||
                name.contains("gamut") ||
                name.contains("color")
    }

    private fun rendererHasFloatHdrSignal(name: String): Boolean {
        if (rendererLooksUnrelatedColorName(name)) return false
        if (rendererLooksSdrWhitePointName(name)) return false
        return name.contains("hdr") ||
                name.contains("sdr") ||
                name.contains("ratio") ||
                name.contains("headroom") ||
                name.contains("target")
    }

    private fun rendererLooksSdrWhitePointName(name: String): Boolean {
        return name.contains("whitepoint") ||
                name.contains("white_point") ||
                name.contains("white")
    }

    private fun rendererLooksUnrelatedColorName(name: String): Boolean {
        return name.contains("backfill") ||
                name.contains("navbar") ||
                name.contains("navigationbar")
    }

    private fun isRendererMutatorName(name: String): Boolean {
        return name.startsWith("set") ||
                name.startsWith("force") ||
                name.startsWith("enable") ||
                name.startsWith("update") ||
                name.startsWith("request")
    }

    private fun isRendererIntrospectionName(name: String): Boolean {
        val lower = name.lowercase()
        return RENDERER_INTROSPECTION_KEYWORDS.any { lower.contains(it) }
    }

    private fun rendererIntValueForName(name: String, colorMode: Int): Int? {
        val lower = name.lowercase()
        return when {
            lower.contains("dataspace") || lower.contains("colorspace") -> {
                if (scrgbDataSpace == 0) {
                    resolveSurfaceTransactionMethods()
                }
                scrgbDataSpace.takeIf { it != 0 }
            }
            lower.contains("wide") || lower.contains("gamut") -> if (colorMode == 0) 0 else 1
            lower.contains("colormode") || lower.contains("color") -> colorMode
            else -> null
        }
    }

    private fun rendererFloatValueForName(name: String, ratio: Float): Float? {
        val lower = name.lowercase()
        if (!rendererHasFloatHdrSignal(lower) &&
            !lower.contains("colormode") &&
            !lower.contains("color")) return null
        return if (ratio.isFinite()) {
            ratio.coerceIn(MIN_HDR_RATIO, MAX_HDR_RATIO)
        } else {
            DEFAULT_HDR_RATIO
        }
    }

    private fun invokeRendererMethod(
        renderer: Any,
        method: java.lang.reflect.Method,
        value: Any
    ): Boolean {
        return try {
            val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else renderer
            method.invoke(receiver, value)
            true
        } catch (e: Exception) {
            logRendererForceFailure(
                "method:${rendererMethodKey(method)}",
                "Renderer HDR method failed: ${describeRendererMethod(method)} value=$value",
                e
            )
            false
        }
    }

    private fun invokeRendererMethod(
        renderer: Any,
        method: java.lang.reflect.Method,
        firstValue: Any,
        secondValue: Any
    ): Boolean {
        return try {
            val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else renderer
            method.invoke(receiver, firstValue, secondValue)
            true
        } catch (e: Exception) {
            logRendererForceFailure(
                "method:${rendererMethodKey(method)}",
                "Renderer HDR method failed: ${describeRendererMethod(method)} value=$firstValue,$secondValue",
                e
            )
            false
        }
    }

    private fun invokeRendererNativeMethod(
        renderer: Any,
        method: java.lang.reflect.Method,
        nativeProxy: Long,
        value: Any
    ): Boolean {
        return try {
            val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else renderer
            method.invoke(receiver, nativeProxy, value)
            true
        } catch (e: Exception) {
            logRendererForceFailure(
                "native:${rendererMethodKey(method)}",
                "Renderer native HDR method failed: ${describeRendererMethod(method)} value=$value",
                e
            )
            false
        }
    }

    private fun setRendererField(
        renderer: Any,
        field: java.lang.reflect.Field,
        value: Any
    ): Boolean {
        return try {
            val receiver = if (java.lang.reflect.Modifier.isStatic(field.modifiers)) null else renderer
            field.set(receiver, value)
            true
        } catch (e: Exception) {
            logRendererForceFailure(
                "field:${rendererFieldKey(field)}",
                "Renderer HDR field set failed: ${describeRendererField(field)} value=$value",
                e
            )
            false
        }
    }

    private fun getRendererNativeProxy(renderer: Any, plan: RendererForcePlan): Long? {
        plan.nativeProxyFields.forEach { field ->
            val value = runCatching {
                val receiver = if (java.lang.reflect.Modifier.isStatic(field.modifiers)) null else renderer
                field.get(receiver)
            }.getOrNull()
            val nativeProxy = when (value) {
                is Long -> value
                is Number -> value.toLong()
                else -> 0L
            }
            if (nativeProxy != 0L) return nativeProxy
        }
        return null
    }

    private fun <T : java.lang.reflect.AccessibleObject> makeRendererAccessible(member: T): T? {
        return try {
            member.isAccessible = true
            member
        } catch (e: Exception) {
            logRendererForceFailure(
                "accessible:$member",
                "Renderer member inaccessible: $member",
                e
            )
            null
        }
    }

    private fun addRendererTarget(targets: MutableList<String>, target: String) {
        if (targets.size < RENDERER_TARGET_LOG_LIMIT) {
            targets.add(target)
        }
    }

    private fun logRendererForceFailure(key: String, message: String, e: Exception? = null) {
        if (!rendererForceFailureLogged.add(key)) return
        if (e != null) {
            YLog.error(TAG, message, e)
        } else {
            YLog.warning(TAG, message)
        }
    }

    private fun rendererMethodKey(method: java.lang.reflect.Method): String {
        return "${method.declaringClass.name}.${method.name}(" +
                method.parameterTypes.joinToString(",") { it.name } + ")"
    }

    private fun rendererFieldKey(field: java.lang.reflect.Field): String {
        return "${field.declaringClass.name}.${field.name}"
    }

    private fun describeRendererMethod(method: java.lang.reflect.Method): String {
        return "${java.lang.reflect.Modifier.toString(method.modifiers)} ${method.returnType.name} " +
                rendererMethodKey(method)
    }

    private fun describeRendererField(field: java.lang.reflect.Field): String {
        return "${java.lang.reflect.Modifier.toString(field.modifiers)} ${field.type.name} " +
                rendererFieldKey(field)
    }

    private fun isBooleanType(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType
    }

    private fun isIntType(type: Class<*>): Boolean {
        return type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType
    }

    private fun isFloatType(type: Class<*>): Boolean {
        return type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType
    }

    private fun isLongType(type: Class<*>): Boolean {
        return type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType
    }

    private fun getRootSurface(vri: Any): Any? {
        val field = resolveSurfaceControlField(vri.javaClass) ?: return null
        return try {
            field.get(vri)
        } catch (e: Exception) {
            YLog.error(TAG, "getRootSurface failed", e)
            null
        }
    }

    private fun isSurfaceValid(surface: Any): Boolean {
        return try {
            val method = surfaceIsValidMethod
                ?: surface.javaClass.getDeclaredMethod("isValid").also {
                    it.isAccessible = true
                    surfaceIsValidMethod = it
                }
            method.invoke(surface) as? Boolean ?: false
        } catch (e: Exception) {
            YLog.error(TAG, "SurfaceControl.isValid() failed", e)
            false
        }
    }

    private fun ensureStatusBarSurfaceHdr(
        surface: Any,
        ratio: Float,
        source: String,
        shouldLog: Boolean
    ): Boolean {
        val alreadyApplied = surface === lastHdrAppliedSurface &&
                lastHdrAppliedRatio == ratio &&
                lastSurfaceTransactionSucceeded == true
        if (alreadyApplied) {
            if (shouldLog) {
                YLog.info(
                    TAG,
                    "Surface HDR transaction kept: source=$source ratio=$ratio surface=$surface"
                )
            }
            return true
        }

        val applied = applyHdrToSurface(surface, ratio, shouldLog, source)
        if (applied) {
            lastHdrAppliedSurface = surface
            lastHdrAppliedRatio = ratio
            lastHdrTransactionFrame = frameCount
        } else if (surface === lastHdrAppliedSurface) {
            clearStatusBarSurfaceHdrCache()
        }
        return applied
    }

    private fun clearStatusBarSurfaceHdrCache() {
        lastHdrAppliedSurface = null
        lastHdrAppliedRatio = DEFAULT_HDR_RATIO
        lastSurfaceTransactionSucceeded = null
        lastHdrTransactionFrame = -1L
    }

    private fun applyHdrToSurface(
        surface: Any,
        ratio: Float,
        shouldLog: Boolean,
        source: String
    ): Boolean {
        if (!resolveSurfaceTransactionMethods()) {
            lastSurfaceTransactionSucceeded = false
            return false
        }
        val applied = applySurfaceTransaction(
            surface = surface,
            desiredRatio = ratio,
            dataSpace = scrgbDataSpace,
            label = "HDR",
            shouldLogFailure = shouldLog || lastSurfaceTransactionSucceeded != false
        )
        if (applied) {
            if (shouldLog || lastSurfaceTransactionSucceeded != true || lastHdrTransactionFrame < 0L) {
                YLog.info(
                    TAG,
                    "Surface HDR transaction applied: source=$source ratio=$ratio " +
                            "dataSpace=$scrgbDataSpace surface=$surface"
                )
            }
        } else {
            if (shouldLog || lastSurfaceTransactionSucceeded != false) {
                YLog.warning(TAG, "Surface HDR transaction failed: source=$source ratio=$ratio surface=$surface")
            }
        }
        lastSurfaceTransactionSucceeded = applied
        return applied
    }

    private fun applyHdrToOverlayRootSurface(
        surface: Any,
        ratio: Float,
        shouldLog: Boolean
    ): Boolean {
        if (!resolveSurfaceTransactionMethods()) {
            return false
        }
        val dataSpace = if (scrgbLinearDataSpace != 0) scrgbLinearDataSpace else scrgbDataSpace
        val applied = applySurfaceTransaction(
            surface = surface,
            desiredRatio = ratio,
            dataSpace = dataSpace,
            label = "Overlay root HDR",
            shouldLogFailure = shouldLog || lastOverlayRootTransactionSucceeded != false
        )
        if (applied) {
            if (shouldLog || lastOverlayRootTransactionSucceeded != true) {
                YLog.info(
                    TAG,
                    "Overlay root Surface HDR transaction applied: ratio=$ratio " +
                            "dataSpace=$dataSpace surface=$surface"
                )
            }
        } else if (shouldLog || lastOverlayRootTransactionSucceeded != false) {
            YLog.warning(TAG, "Overlay root Surface HDR transaction failed: ratio=$ratio surface=$surface")
        }
        return applied
    }

    private fun restoreOverlayRootSurfaceSdr(surface: Any): Boolean {
        val valid = isSurfaceValid(surface)
        if (!valid) {
            YLog.warning(TAG, "Overlay root Surface SDR restore skipped: invalid surface=$surface")
            return false
        }
        if (!resolveSurfaceTransactionMethods()) return false
        val restored = applySurfaceTransaction(
            surface = surface,
            desiredRatio = 1.0f,
            dataSpace = unknownDataSpace,
            label = "Overlay root SDR",
            shouldLogFailure = true
        )
        if (restored || surface !== lastOverlayRootRestoreSurface) {
            YLog.info(TAG, "Overlay root Surface SDR restore result=$restored surface=$surface")
            lastOverlayRootRestoreSurface = surface
        }
        return restored
    }

    private fun restoreSurfaceSdr(surface: Any): Boolean {
        val valid = isSurfaceValid(surface)
        if (!valid) {
            YLog.warning(TAG, "Surface SDR restore skipped: invalid surface=$surface")
            return false
        }
        if (!resolveSurfaceTransactionMethods()) return false
        val restored = applySurfaceTransaction(
            surface = surface,
            desiredRatio = 1.0f,
            dataSpace = unknownDataSpace,
            label = "SDR",
            shouldLogFailure = true
        )
        if (restored || surface !== lastRestoreSurface) {
            YLog.info(TAG, "Surface SDR restore result=$restored surface=$surface")
            lastRestoreSurface = surface
        }
        if (restored) clearStatusBarSurfaceHdrCache()
        return restored
    }

    private fun applySurfaceTransaction(
        surface: Any,
        desiredRatio: Float,
        dataSpace: Int,
        label: String,
        shouldLogFailure: Boolean
    ): Boolean {
        return try {
            val transaction = surfaceTransactionClass!!.getDeclaredConstructor().newInstance()
            transactionSetDesiredHdrHeadroom!!.invoke(transaction, surface, desiredRatio)
            transactionSetExtendedRangeBrightness!!.invoke(transaction, surface, 1.0f, desiredRatio)
            transactionSetDataSpace!!.invoke(transaction, surface, dataSpace)
            transactionApply!!.invoke(transaction)
            true
        } catch (e: Exception) {
            if (shouldLogFailure) {
                YLog.error(TAG, "Surface $label transaction failed", e)
            }
            false
        }
    }

    private fun resolveSurfaceTransactionMethods(): Boolean {
        if (surfaceTransactionResolved) {
            return transactionSetDesiredHdrHeadroom != null &&
                transactionSetExtendedRangeBrightness != null &&
                transactionSetDataSpace != null &&
                transactionApply != null
        }
        surfaceTransactionResolved = true
        return try {
            val surfaceClass = surfaceControlClass ?: Class.forName("android.view.SurfaceControl").also {
                surfaceControlClass = it
            }
            val transactionClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val dataSpaceClass = Class.forName("android.hardware.DataSpace")

            surfaceTransactionClass = transactionClass
            scrgbDataSpace = dataSpaceClass.getField("DATASPACE_SCRGB").getInt(null)
            scrgbLinearDataSpace = dataSpaceClass.getField("DATASPACE_SCRGB_LINEAR").getInt(null)
            unknownDataSpace = dataSpaceClass.getField("DATASPACE_UNKNOWN").getInt(null)
            transactionSetDesiredHdrHeadroom =
                transactionClass.getDeclaredMethod("setDesiredHdrHeadroom", surfaceClass, java.lang.Float.TYPE)
            transactionSetExtendedRangeBrightness =
                transactionClass.getDeclaredMethod(
                    "setExtendedRangeBrightness",
                    surfaceClass,
                    java.lang.Float.TYPE,
                    java.lang.Float.TYPE
                )
            transactionSetDataSpace =
                transactionClass.getDeclaredMethod("setDataSpace", surfaceClass, Integer.TYPE)
            transactionApply = transactionClass.getDeclaredMethod("apply")

            listOf(
                transactionSetDesiredHdrHeadroom,
                transactionSetExtendedRangeBrightness,
                transactionSetDataSpace,
                transactionApply
            ).forEach { it?.isAccessible = true }

            YLog.info(
                TAG,
                "Surface transaction methods resolved, SCRGB=$scrgbDataSpace " +
                        "SCRGB_LINEAR=$scrgbLinearDataSpace UNKNOWN=$unknownDataSpace"
            )
            true
        } catch (e: Exception) {
            if (!surfaceTransactionFailureLogged) {
                YLog.error(TAG, "resolveSurfaceTransactionMethods failed", e)
                surfaceTransactionFailureLogged = true
            }
            false
        }
    }

    private fun setHeadroom(lp: WindowManager.LayoutParams, value: Float) {
        if (headroomFieldResolved && hdrHeadroomField == null && headroomFieldNotFoundLogged) {
            return
        }
        if (!headroomFieldResolved || hdrHeadroomField == null) {
            resolveHdrHeadroomField()
        }
        val field = hdrHeadroomField ?: return
        try {
            field.isAccessible = true
            field.setFloat(lp, value)
        } catch (e: Exception) {
            YLog.error(TAG, "setHeadroom failed", e)
        }
    }

    private fun resolveHdrHeadroomField() {
        headroomFieldResolved = true
        try {
            val candidates = mutableListOf<java.lang.reflect.Field>()
            var clazz: Class<*>? = WindowManager.LayoutParams::class.java

            while (clazz != null && clazz != Any::class.java) {
                for (f in clazz.declaredFields) {
                    val name = f.name.lowercase()
                    if (name.contains("hdr") || name.contains("headroom")) {
                        candidates.add(f)
                        YLog.info(TAG, "Candidate: ${f.name} in ${clazz.simpleName} (type=${f.type})")
                    }
                }
                clazz = clazz.superclass
            }

            hdrHeadroomField = when {
                candidates.isEmpty() -> {
                    if (!headroomFieldNotFoundLogged) {
                        YLog.warning(TAG, "No hdr/headroom field found. HDR will work without headroom (colorMode only).")
                        headroomFieldNotFoundLogged = true
                    }
                    null
                }
                candidates.size == 1 -> candidates[0]
                else -> {
                    val best = candidates.minByOrNull {
                        levenshtein(it.name.lowercase(), "desiredhdrheadroom")
                    }
                    YLog.info(TAG, "Multiple candidates, best match: ${best?.name}")
                    best
                }
            }

            if (hdrHeadroomField != null) {
                hdrHeadroomField!!.isAccessible = true
                YLog.info(TAG, "Resolved headroom field: ${hdrHeadroomField!!.name} (type=${hdrHeadroomField!!.type})")
            }
        } catch (e: Exception) {
            YLog.error(TAG, "resolveHdrHeadroomField failed", e)
        }
    }

    fun forceReResolve() {
        nonStatusBarViewRoots.clear()
        headroomFieldResolved = false
        headroomFieldNotFoundLogged = false
        hdrHeadroomField = null
        mWindowAttributesResolved = false
        mSurfaceControlResolved = false
        mAttachInfoResolved = false
        mThreadedRendererResolved = false
        mSurfaceControlFieldNotFoundLogged = false
        surfaceTransactionResolved = false
        surfaceTransactionFailureLogged = false
        rendererSetWideGamutResolved = false
        rendererSetWideGamutFailureLogged = false
        mWindowAttributesField = null
        mSurfaceControlField = null
        mAttachInfoField = null
        mThreadedRendererField = null
        surfaceControlClass = null
        surfaceTransactionClass = null
        rendererSetWideGamutMethod = null
        transactionSetDesiredHdrHeadroom = null
        transactionSetExtendedRangeBrightness = null
        transactionSetDataSpace = null
        transactionApply = null
        surfaceIsValidMethod = null
        scrgbDataSpace = 0
        scrgbLinearDataSpace = 0
        unknownDataSpace = 0
        lastStatusBarSurface = null
        lastHdrAppliedSurface = null
        lastHdrAppliedRatio = DEFAULT_HDR_RATIO
        lastSurfaceValid = null
        lastSurfaceTransactionSucceeded = null
        lastHdrTransactionFrame = -1L
        lastRestoreSurface = null
        lastProbeSurface = null
        lastProbeSurfaceValid = null
        lastProbeTransactionSucceeded = null
        lastProbeRestoreSurface = null
        overlayProbeWindowActive = false
        overlayProbeRatio = DEFAULT_HDR_RATIO
        lastOverlayRootSurface = null
        lastOverlayRootSurfaceValid = null
        lastOverlayRootTransactionSucceeded = null
        lastOverlayRootRestoreSurface = null
        lastWideGamutRenderer = null
        lastHdrForcedRenderer = null
        lastHdrForcedVri = null
        lastHdrForcedRatio = DEFAULT_HDR_RATIO
        lastWideGamutHookFrame = -1L
        rendererIntrospectionLoggedClasses.clear()
        rendererForcePlans.clear()
        rendererForceFailureLogged.clear()
        YLog.info(TAG, "Force re-resolve all reflection fields")
    }

    fun isSupported(): Boolean {
        val colorModeSupported = try {
            WindowManager.LayoutParams::class.java.getField("colorMode")
            true
        } catch (_: Exception) { false }

        val headroomSupported = try {
            var clazz: Class<*>? = WindowManager.LayoutParams::class.java
            var found = false
            while (clazz != null && clazz != Any::class.java) {
                for (f in clazz.declaredFields) {
                    if (f.name.lowercase().let { it.contains("hdr") || it.contains("headroom") }) {
                        found = true; break
                    }
                }
                if (found) break
                clazz = clazz.superclass
            }
            found
        } catch (_: Exception) { false }

        val supported = colorModeSupported || headroomSupported
        YLog.info(TAG, "isSupported=$supported (colorMode=$colorModeSupported, headroom=$headroomSupported)")
        return supported
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    private const val OVERLAY_PROBE_WINDOW_TITLE = "Lyricon HDR Overlay Probe"
    private const val OVERLAY_ROOT_SOURCE = "overlay-window-root"
    private const val RENDERER_INTROSPECTION_LIMIT = 80
    private const val RENDERER_TARGET_LOG_LIMIT = 12
    private val RENDERER_INTROSPECTION_KEYWORDS = listOf(
        "wide",
        "gamut",
        "color",
        "hdr",
        "dataspace",
        "render",
        "buffer",
        "format",
        "native",
        "proxy",
        "ratio",
        "headroom",
        "target",
        "sdr"
    )
}
