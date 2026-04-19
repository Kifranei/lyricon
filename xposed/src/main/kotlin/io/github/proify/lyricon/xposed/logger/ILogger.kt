/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.logger

interface ILogger {
    fun v(tag: String? = null, message: String? = null)
    fun d(tag: String? = null, message: String? = null)
    fun w(tag: String? = null, message: String? = null)
    fun i(tag: String? = null, message: String? = null)
    fun e(tag: String? = null, message: String? = null, throwable: Throwable? = null)
}