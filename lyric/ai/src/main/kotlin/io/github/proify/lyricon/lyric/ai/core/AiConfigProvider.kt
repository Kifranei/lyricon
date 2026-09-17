/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.ai.core

import android.content.SharedPreferences
import io.github.proify.lyricon.lyric.ai.core.AiConfigProviderHolder.init
import io.github.proify.lyricon.lyric.ai.core.AiConfigProviderHolder.instance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI 配置统一访问接口。
 *
 * 所有 AI 功能（翻译、解读等）通过此接口获取当前激活的 [AiConfig]，
 * 而非直接读取 [SharedPreferences] 或 [BasicStyle] 中的字段。
 *
 * 核心职责：
 * - 暴露当前激活配置的 [StateFlow]，供 UI 和业务层观察
 * - 提供同步获取方法，供非协程环境使用
 * - 监听 [AiConfigStore] 的变更并自动更新
 *
 * @author Tomakino
 * @since 2026
 */
interface AiConfigProvider {

    /**
     * 当前激活的配置（可能为 null，表示未配置或配置不可用）。
     */
    val activeConfig: StateFlow<AiConfig?>

    /**
     * 同步获取当前激活的配置。
     *
     * 适用于非协程环境（如 xposed 模块的同步方法）。
     * 返回 null 表示未配置或配置不可用。
     */
    fun getActiveConfig(): AiConfig?

    /**
     * 检查当前配置是否可用（能否发起 AI 请求）。
     */
    fun isConfigUsable(): Boolean = getActiveConfig()?.isUsable == true
}

/**
 * [AiConfigProvider] 的默认实现。
 *
 * 基于 [AiConfigStore] 和 [SharedPreferences] 实现，支持跨进程同步。
 * 通过 [MutableStateFlow] 暴露配置变更，供 UI 和业务层观察。
 *
 * @param preferences 读取配置的 SharedPreferences 实例
 */
class AiConfigProviderImpl(
    private val preferences: SharedPreferences,
) : AiConfigProvider {

    private val _activeConfig = MutableStateFlow<AiConfig?>(null)

    override val activeConfig: StateFlow<AiConfig?> = _activeConfig.asStateFlow()

    init {
        // 初始加载
        reload()

        // 监听 SharedPreferences 变更
        preferences.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == AiConfigStore.KEY_COLLECTION) {
                reload()
            }
        }
    }

    /**
     * 重新加载配置。
     *
     * 从 [AiConfigStore] 读取配置集合，找到激活项并更新 [_activeConfig]。
     */
    private fun reload() {
        val collection = AiConfigStore.load(preferences)
        val activeId = collection.activeId

        val activeProfile = if (activeId != null) {
            collection.profiles.find { it.id == activeId }
        } else {
            // 如果没有激活项，使用第一个配置（如果存在）
            collection.profiles.firstOrNull()
        }

        _activeConfig.value = activeProfile?.config
    }

    override fun getActiveConfig(): AiConfig? {
        // 如果缓存为空，尝试重新加载
        if (_activeConfig.value == null) {
            reload()
        }
        return _activeConfig.value
    }
}

/**
 * 全局 [AiConfigProvider] 单例。
 *
 * 在 Application 初始化时通过 [init] 注入实现，
 * 其他模块通过 [instance] 访问。
 *
 * 注意：此单例仅用于 app 模块内部访问。
 * xposed 模块需要自行创建 [AiConfigProviderImpl] 实例。
 */
object AiConfigProviderHolder {

    private var _provider: AiConfigProvider? = null

    /**
     * 当前的 [AiConfigProvider] 实例。
     * 如果未初始化，抛出 [IllegalStateException]。
     */
    val instance: AiConfigProvider
        get() = _provider ?: throw IllegalStateException(
            "AiConfigProvider not initialized. Call AiConfigProviderHolder.init() first."
        )

    /**
     * 初始化全局 [AiConfigProvider]。
     *
     * 应在 Application.onCreate() 中调用。
     *
     * @param provider [AiConfigProvider] 实例
     */
    fun init(provider: AiConfigProvider) {
        _provider = provider
    }

    /**
     * 检查是否已初始化。
     */
    fun isInitialized(): Boolean = _provider != null
}
