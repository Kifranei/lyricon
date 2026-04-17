/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.util

//import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * 简繁转换扩展工具
 */
object ChineseConverter {

    /**
     * 繁体转简体
     * 注意：由于涉及字典 IO 操作，不建议在 UI 线程处理长文本
     */
    fun String.toSimplified(): String {
        return this
//        if (isEmpty() || isBlank()) return this
//
//        return try {
//            ZhConverterUtil.toSimple(this)
//        } catch (_: Exception) {
//            this
//        }
    }

    /**
     * 简体转繁体
     */
    fun String.toTraditional(): String {
        return this
//        if (isEmpty() || isBlank()) return this
//
//        return try {
//            ZhConverterUtil.toTraditional(this)
//        } catch (_: Exception) {
//            this
//        }
    }
}