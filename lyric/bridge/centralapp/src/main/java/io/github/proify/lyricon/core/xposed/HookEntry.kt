/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.core.xposed

import android.app.Application
import androidx.annotation.Keep
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.proify.lyricon.central.BridgeCentral

@Keep
class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "Lyricon-Xposed"
        private const val TARGET_PACKAGE = "com.android.systemui"

        @Volatile
        private var isInitialized = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null || lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        XposedBridge.log("[$TAG] Hooking package: ${lpparam.packageName} (Process: ${lpparam.processName})")

        val appClassName = lpparam.appInfo?.className ?: "android.app.Application"

        try {
            XposedHelpers.findAndHookMethod(
                appClassName,
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return

                        synchronized(this@HookEntry) {
                            if (isInitialized) return
                            initLyriconCentral(app)
                            isInitialized = true
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook onCreate in $appClassName: ${e.message}")
        }
    }

    private fun initLyriconCentral(app: Application) {
        try {
            BridgeCentral.initialize(app)
            BridgeCentral.sendBootCompleted()
            XposedBridge.log("[$TAG] Lyricon BridgeCentral initialized successfully.")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Error during initialization: ${e.stackTraceToString()}")
        }
    }
}