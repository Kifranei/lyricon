/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.android.extensions

import java.security.MessageDigest

/**
 * 将字符串转换为 MD5 哈希值
 * 采用 32 位小写十六进制格式输出
 */
fun String.md5(): String {
    return MessageDigest.getInstance("MD5")
        .digest(this.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}