/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.activity.lyric.provider

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.sheet.AppCache
import io.github.proify.lyricon.app.util.SignatureValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class LyricProviderViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManager: PackageManager = application.packageManager

    // 状态管理
    private val _uiState = MutableStateFlow(ProviderUiState())
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    // 视图模式状态
    var listStyle by mutableIntStateOf(
        application.defaultSharedPreferences
            .getInt("activity_provider_list_style", ViewMode.SHORT)
    )

    var noQueryPermission by mutableStateOf(false)

    companion object {
        private val CERTIFIED_SIGNATURES = arrayOf(
            "d75a43f76dbe80d816046f952b8d0f5f7abd71c9bd7b57786d5367c488bd5816",
            "ba86f0c1f52d0f6a24e1b9a63eade0e8b80e7b9e20b8ef068da2e39c7b6e7b49", // trantor.app
            "8488d67a39978f84fd876510e0acb85e3a0504b90fbd56f11beb2123e285fa78"  // Salt Player
        )
    }

    init {
        loadProviders()
    }

    private fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            withContext(Dispatchers.IO) {
                // 1. 获取包列表
                val packageInfos = packageManager.getInstalledPackages(
                    PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
                )
                if (packageInfos.size <= 1) { // 考虑到部分环境下可能只返回自身
                    noQueryPermission = true
                }

                // 2. 筛选目标包 (Lyric Modules)
                val targetPackages = packageInfos.filter { isValidModule(it) }

                if (targetPackages.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = ProviderUiState(isLoading = false)
                    }
                    return@withContext
                }

                // 3. 准备本地化排序器
                // 使用当前系统语言，如果是中文环境，它将按照拼音首字母排序
                val collator = Collator.getInstance(Locale.getDefault())

                // 4. 分批处理以优化 UI 响应
                val batchSize = 5
                val loadedModules = mutableListOf<LyricModule>()

                targetPackages.chunked(batchSize).forEach { batch ->
                    val batchResults = batch.mapNotNull { processPackage(it) }
                    loadedModules.addAll(batchResults)

                    // 5. 本地化排序逻辑
                    // 排序规则：已认证应用排在前面，同级别下按 Label 本地化字母顺序排序
                    val sortedList = loadedModules.sortedWith { m1, m2 ->
                        if (m1.isCertified != m2.isCertified) {
                            // 已认证的（true）排在前面
                            m2.isCertified.compareTo(m1.isCertified)
                        } else {
                            // 使用 Collator 比较 Label
                            collator.compare(m1.label, m2.label)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = ProviderUiState(
                            modules = sortedList,
                            isLoading = loadedModules.size < targetPackages.size
                        )
                    }
                }
            }
        }
    }

    private fun isValidModule(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false

        // 排除系统应用和更新的系统应用
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        if (isSystem || isUpdatedSystem) return false

        return appInfo.metaData?.getBoolean("lyricon_module") == true
    }

    private fun processPackage(packageInfo: PackageInfo): LyricModule? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            val metaData = appInfo.metaData ?: return null

            val label = appInfo.loadLabel(packageManager).toString()
            AppCache.cacheLabel(packageInfo.packageName, label)

            val isCertified = SignatureValidator.validateSignature(
                packageInfo,
                *CERTIFIED_SIGNATURES
            )

            // 图标缓存逻辑
            if (AppCache.getCachedIcon(packageInfo.packageName) == null) {
                runCatching {
                    appInfo.loadIcon(packageManager)?.let { icon ->
                        AppCache.cacheIcon(packageInfo.packageName, icon)
                    }
                }
            }

            LyricModule(
                packageInfo = packageInfo,
                description = metaData.getString("lyricon_module_description"),
                homeUrl = metaData.getString("lyricon_module_home"),
                category = metaData.getString("lyricon_module_category"),
                author = metaData.getString("lyricon_module_author"),
                isCertified = isCertified,
                tags = extractTags(appInfo, metaData),
                label = label
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTags(appInfo: ApplicationInfo, metaData: Bundle): List<ModuleTag> {
        val tagsResId = metaData.getInt("lyricon_module_tags")

        val rawTags = if (tagsResId != 0) {
            runCatching {
                packageManager.getResourcesForApplication(appInfo).getStringArray(tagsResId)
                    .toList()
            }.getOrDefault(emptyList())
        } else {
            metaData.getString("lyricon_module_tags")?.let { listOf(it) } ?: emptyList()
        }

        return rawTags.mapNotNull { tagKey ->
            if (tagKey.isBlank()) return@mapNotNull null
            ModuleHelper.getPredefinedTag(tagKey) ?: ModuleTag(title = tagKey)
        }
    }
}