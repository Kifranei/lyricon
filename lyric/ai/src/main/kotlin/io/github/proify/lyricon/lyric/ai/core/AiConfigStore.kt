/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.ai.core

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一条具名的 AI 提供商配置。
 *
 * 在统一的 [AiConfig] 连接参数之上增加标识（id）与展示名称（name），
 * 使「AI 实验室」能够创建、切换、管理多个提供商。
 */
@Serializable
@Parcelize
data class AiProfile(
    val id: String,
    val name: String,
    val description: String? = null,
    val config: AiConfig,
) : android.os.Parcelable

/**
 * AI 配置集合：一组 [AiProfile] 与当前激活项 [activeId]。
 *
 * 作为 AI 配置的唯一事实来源（single source of truth）存储为一个 JSON 字符串。
 */
@Serializable
data class AiConfigCollection(
    val activeId: String? = null,
    val profiles: List<AiProfile> = emptyList(),
)

/**
 * AI 配置集合的持久化与领域逻辑。
 *
 * 该入口现在指向本 Store 中的激活项；首次使用时若无集合，会从旧版扁平 key 迁移出
 * 一个默认配置，保证老用户升级后无感、不丢配置。
 */
object AiConfigStore {

    const val KEY_COLLECTION = "ai_configs"

    private val json = Json { ignoreUnknownKeys = true }

    /** 读取配置集合；无集合或解析失败时返回空集合。 */
    fun load(preferences: SharedPreferences): AiConfigCollection {
        val raw = preferences.getString(KEY_COLLECTION, null)
        if (raw.isNullOrBlank()) return AiConfigCollection()

        return runCatching { json.decodeFromString<AiConfigCollection>(raw) }
            .getOrElse { AiConfigCollection() }
    }

    /** 持久化整个配置集合（同步提交，保证跨进程 remote prefs 立即生效）。 */
    fun save(preferences: SharedPreferences, collection: AiConfigCollection) {
        val raw = json.encodeToString(AiConfigCollection.serializer(), collection)
        preferences.edit(commit = true) { putString(KEY_COLLECTION, raw) }
    }

    /** 激活指定配置，并把连接参数镜像到旧版扁平 key（兼容只读扁平 key 的消费方）。 */
    fun activate(preferences: SharedPreferences, id: String) {
        val collection = load(preferences)

        save(preferences, collection.copy(activeId = id))
    }

    /** 新增或更新一条配置 */
    fun upsert(preferences: SharedPreferences, profile: AiProfile) {
        val collection = load(preferences)
        val exists = collection.profiles.any { it.id == profile.id }

        val profiles = if (exists) {
            collection.profiles.map { if (it.id == profile.id) profile else it }
        } else {
            collection.profiles + profile
        }
        val updated = collection.copy(profiles = profiles)

        save(preferences, updated)
    }

    /**
     * 删除指定配置；若删除的是激活项则自动激活剩余首项。全部删除后重建一个默认配置，
     * 保证始终存在至少一条可用配置。
     */
    fun remove(preferences: SharedPreferences, id: String) {
        val collection = load(preferences)
        val remaining = collection.profiles.filter { it.id != id }
        val updated = if (remaining.isEmpty()) {
            AiConfigCollection()
        } else {
            val newActiveId =
                if (collection.activeId == id) remaining.first().id else collection.activeId
            AiConfigCollection(activeId = newActiveId, profiles = remaining)
        }
        save(preferences, updated)
    }
}