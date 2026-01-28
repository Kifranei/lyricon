/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.proify.lyricon.common.util.safe

/**
 * 闪退检测工具类
 */
class CrashDetector private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).safe()

    companion object {
        private const val PREFS_NAME = "crash_detector_prefs"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_FIRST_CRASH_TIME = "first_crash_time"

        private const val TIME_WINDOW_MS = 20 * 1000L
        private const val CRASH_THRESHOLD = 3

        @Volatile
        private var instance: CrashDetector? = null

        fun getInstance(context: Context): CrashDetector {
            return instance ?: synchronized(this) {
                instance ?: CrashDetector(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val lock = Any()

    /**
     * 记录一次潜在的崩溃事件
     */
    fun record() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val currentCount = prefs.getInt(KEY_CRASH_COUNT, 0)
            val firstCrashTime = prefs.getLong(KEY_FIRST_CRASH_TIME, 0L)

            // 判断是否超出时间窗口
            val isOutOfWindow = (now - firstCrashTime) > TIME_WINDOW_MS

            val newCount: Int
            val newFirstCrashTime: Long

            if (isOutOfWindow || currentCount == 0) {
                // 重置计数,开始新的时间窗口
                newCount = 1
                newFirstCrashTime = now
            } else {
                // 在时间窗口内,累加计数
                newCount = currentCount + 1
                newFirstCrashTime = firstCrashTime
            }

            prefs.edit(commit = true) {
                putInt(KEY_CRASH_COUNT, newCount)
                putLong(KEY_FIRST_CRASH_TIME, newFirstCrashTime)
            }
        }
    }

    /**
     * 检查是否发生连续崩溃
     */
    fun isContinuousCrash(): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val count = prefs.getInt(KEY_CRASH_COUNT, 0)
            val firstCrashTime = prefs.getLong(KEY_FIRST_CRASH_TIME, 0L)

            // 检查是否在时间窗口内且达到阈值
            val inWindow = (now - firstCrashTime) <= TIME_WINDOW_MS
            val reachedThreshold = count >= CRASH_THRESHOLD

            val result = inWindow && reachedThreshold

            return result
        }
    }

    /**
     * 重置所有崩溃记录
     */
    fun reset() {
        synchronized(lock) {
            prefs.edit(commit = true) {
                clear()
            }
        }
    }
}