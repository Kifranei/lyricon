/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.logger

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 日志工具类
 * 优先使用 LSPosed 框架日志，回退到 Android Logcat
 */
object YLog {
    const val TAG = "Lyricon"

    private var xposedInterface: XposedInterface? = null

    /**
     * 初始化日志系统
     * @param ctx XposedInterface 实例（通常是 XposedModule）
     */
    fun init(ctx: XposedInterface) {
        xposedInterface = ctx
    }

    fun info(tag: String, msg: String) {
        log(Log.INFO, tag, msg)
    }

    fun debug(tag: String, msg: String) {
        log(Log.DEBUG, tag, msg)
    }

    fun error(tag: String, msg: String) {
        log(Log.ERROR, tag, msg)
    }

    fun verbose(tag: String, msg: String) {
        log(Log.VERBOSE, tag, msg)
    }

    fun warning(tag: String, msg: String) {
        log(Log.WARN, tag, msg)
    }

    fun error(tag: String, msg: String?, e: Throwable?) {
        val xi = xposedInterface
        if (xi != null) {
            xi.log(Log.ERROR, TAG, buildMessage(tag, msg), e)
        } else {
            Log.e(TAG, buildMessage(tag, msg), e)
        }
    }

    private fun log(priority: Int, tag: String, msg: String) {
        val xi = xposedInterface
        if (xi != null) {
            xi.log(priority, TAG, buildMessage(tag, msg))
        } else {
            when (priority) {
                Log.VERBOSE -> Log.v(TAG, buildMessage(tag, msg))
                Log.DEBUG -> Log.d(TAG, buildMessage(tag, msg))
                Log.INFO -> Log.i(TAG, buildMessage(tag, msg))
                Log.WARN -> Log.w(TAG, buildMessage(tag, msg))
                Log.ERROR -> Log.e(TAG, buildMessage(tag, msg))
            }
        }
    }

    private fun buildMessage(tag: String, msg: String?): String {
        return "[$tag] ${msg ?: ""}"
    }
}