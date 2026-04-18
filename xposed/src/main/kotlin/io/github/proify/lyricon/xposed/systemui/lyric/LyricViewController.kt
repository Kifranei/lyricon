/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.SuperLogo
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.OplusCapsuleHooker
import java.io.File

/**
 * 歌词视图控制器 (UI 表现层)
 *
 * 职责：
 * 1. 订阅 [LyricDataHub] 分发的已加工歌词数据并驱动 UI 刷新。
 * 2. 处理 SystemUI 特有的视图逻辑（如 Logo 颜色提取、Visibility 协调）。
 * 3. 监听外部配置变更并通知调度中心重走加工流程。
 */
object LyricViewController : ActivePlayerListener,
    OplusCapsuleHooker.CapsuleStateChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    private const val TAG = "LyricViewController"
    private const val DEBUG = true

    /** 当前播放状态缓存 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 当前激活的播放器包名 */
    @Volatile
    var activePackage: String = ""
        private set

    /** 翻译显示开关状态 */
    @Volatile
    private var isDisplayTranslation: Boolean = true

    /** 罗马音显示开关状态 */
    @Volatile
    private var isDisplayRoma: Boolean = true

    init {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Initializing LyricViewController...")

        // 注册到数据调度中心
        LyricDataHub.addListener(this)

        // 注册系统级 UI 状态监听
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    // --- ActivePlayerListener 实现 (数据驱动 UI) ---

    /**
     * 当加工完毕的歌词数据到达时触发渲染
     * 注：此方法可能会被调用两次（Pre-processing 后一次，Post-processing 全部完成后一次）
     */
    override fun onSongChanged(song: Song?) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Song changed: $song")

        updateAllControllers {
            lyricView.setSong(song)
            refreshTranslationVisibility(lyricView)
        }
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        if (DEBUG) YLog.debug(
            tag = TAG,
            msg = "Active provider changed: ${providerInfo?.playerPackageName}"
        )

        this.activePackage = providerInfo?.playerPackageName.orEmpty()
        LyricPrefs.activePackageName = this.activePackage

        updateAllControllers {
            resetViewForNewPlayer(this, providerInfo)
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Playback state changed: $isPlaying")

        this.isPlaying = isPlaying
        updateAllControllers { lyricView.setPlaying(isPlaying) }
    }

    override fun onPositionChanged(position: Long) {
        updateAllControllers { lyricView.setPosition(position) }
    }

    override fun onSeekTo(position: Long) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Seek to: $position")

        updateAllControllers { lyricView.seekTo(position) }
    }

    override fun onReceiveText(text: String?) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Receive text: $text")

        updateAllControllers { lyricView.setText(text) }
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Display translation changed: $isDisplayTranslation")

        this.isDisplayTranslation = isDisplayTranslation
        updateAllControllers { refreshTranslationVisibility(lyricView) }
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Display Roma changed: $isDisplayRoma")

        this.isDisplayRoma = isDisplayRoma
        updateAllControllers { lyricView.updateDisplayTranslation(displayRoma = isDisplayRoma) }
    }

    // --- 业务配置变更 API ---

    /**
     * 应用新的歌词样式配置
     * 当用户在设置页面修改了颜色、字体、翻译开关、繁简模式等，应调用此方法。
     *
     * @param style 最新的样式配置对象
     */
    fun applyConfigurationUpdate(style: LyricStyle) {
        // 1. 同步非内容类的 UI 样式（如颜色、大小、边距）
        updateAllControllers { updateLyricStyle(style) }

        // 2. 通知数据中心重走流水线（处理内容层面的变更，如繁简切换、AI 开关）
        LyricDataHub.reprocessCurrentSong()
    }

    // --- 内部 UI 辅助逻辑 ---

    /**
     * 当切换播放器源时，重置所有视图状态并刷新 Logo 封面
     */
    private fun resetViewForNewPlayer(
        controller: StatusBarViewController,
        provider: ProviderInfo?
    ) {
        val view = controller.lyricView
        view.setSong(null)
        view.setPlaying(false)

        // 加载当前包名的特定样式
        controller.updateLyricStyle(LyricPrefs.getLyricStyle())
        view.updateVisibility()

        // 更新 Logo 与封面
        view.logoView.apply {
            val activePackage = provider?.playerPackageName.orEmpty()
            this.activePackage = activePackage

            val cover = if (activePackage.isBlank()) null else NotificationCoverHelper.getCoverFile(
                activePackage
            )
            this.coverFile = cover
            controller.updateCoverThemeColors(cover)
            post { this.providerLogo = provider?.logo }
        }
    }

    /**
     * 根据全局开关和当前包名配置刷新翻译行的可见性
     */
    private fun refreshTranslationVisibility(view: StatusBarLyric) {
        val style = LyricPrefs.activePackageStyle
        val shouldShow = isDisplayTranslation &&
                !style.text.isDisableTranslation &&
                !style.text.isTranslationOnly

        view.updateDisplayTranslation(displayTranslation = shouldShow)
    }

    /**
     * 遍历所有状态栏实例并确保在 UI 线程执行
     */
    private inline fun updateAllControllers(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEach { controller ->
            runCatching {
                controller.lyricView.post { controller.block() }
            }.onFailure { e ->
                YLog.error(tag = TAG, msg = "Dispatch UI update failed", e = e)
            }
        }
    }

    // --- 系统事件回调实现 ---

    override fun onColorOsCapsuleVisibilityChanged(isShowing: Boolean) {
        updateAllControllers { lyricView.setOplusCapsuleVisibility(isShowing) }
    }

    override fun onCoverUpdated(packageName: String, coverFile: File) {
        if (packageName != activePackage) return
        updateAllControllers {
            lyricView.logoView.apply {
                this.coverFile = coverFile
                (strategy as? SuperLogo.CoverStrategy)?.updateContent()
            }
            updateCoverThemeColors(coverFile)
        }
    }
}