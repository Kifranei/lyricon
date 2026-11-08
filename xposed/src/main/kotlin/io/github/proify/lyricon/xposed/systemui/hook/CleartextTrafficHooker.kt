/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.hook

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog

/**
 * NetworkSecurityPolicy 明文流量放行 Hook。
 *
 * AI 翻译 / 解读在 SystemUI 进程内直接发起 OpenAI 兼容请求（底层 OkHttp 会调用
 * [android.security.NetworkSecurityPolicy.isCleartextTrafficPermitted]），
 * 而 SystemUI 默认禁止明文流量：本地或局域网部署的服务（Ollama /
 * LM Studio / one-api 等）通常只有 HTTP，会被
 * "CLEARTEXT communication to <host> not permitted by network security policy" 拦截。
 *
 * 这里仅放行回环 / 局域网私有段等本地地址，公网主机的明文检测维持原策略。
 */
object CleartextTrafficHooker {
    private const val TAG = "CleartextTrafficHooker"

    fun inject(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = Class.forName(
                "android.security.NetworkSecurityPolicy",
                true,
                classLoader
            )
            val method = clazz.getDeclaredMethod(
                "isCleartextTrafficPermitted",
                String::class.java
            )

            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val host = chain.args.getOrNull(0) as? String
                    if (host != null && isLocalAddress(host)) return true
                    return chain.proceed()
                }
            })
            YLog.info(TAG, "NetworkSecurityPolicy cleartext hook injected")
        } catch (t: Throwable) {
            YLog.error(TAG, "Failed to hook NetworkSecurityPolicy", t)
        }
    }

    /**
     * 本地地址判定：localhost / IPv4 回环与私有段 / 链路本地 /
     * CGNAT（100.64.0.0/10，Tailscale 等组网）/ IPv6 ::1、ULA、link-local。
     */
    private fun isLocalAddress(host: String): Boolean {
        val h = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".localhost")) return true

        if (h.contains(':')) {
            if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
            val firstHextet = h.substringBefore(':').toIntOrNull(16) ?: return false
            return (firstHextet ushr 9) == 0x7e ||   // fc00::/7 ULA
                    (firstHextet ushr 6) == 0x3fa    // fe80::/10 link-local
        }

        val octets = h.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return a == 127 ||                      // 127.0.0.0/8 loopback
                a == 10 ||                      // 10.0.0.0/8 private
                (a == 172 && b in 16..31) ||    // 172.16.0.0/12 private
                (a == 192 && b == 168) ||       // 192.168.0.0/16 private
                (a == 169 && b == 254) ||       // 169.254.0.0/16 link-local
                (a == 100 && b in 64..127) ||   // 100.64.0.0/10 CGNAT
                a == 0                          // 0.0.0.0/8
    }
}
