/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.SuperLogo
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.OplusCapsuleHooker
import java.io.File

/**
 * 歌词视图控制器
 *
 * 核心职责：
 * 1. 监听并分发播放器状态（播放/暂停/进度/歌曲变更）。
 * 2. 管理 AI 翻译生命周期，确保异步回调的一致性。
 * 3. 动态响应全局及特定应用的样式配置变更。
 */
object LyricViewController : ActivePlayerListener, Handler.Callback,
    OplusCapsuleHooker.CapsuleStateChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    private const val TAG = "LyricViewController"
    private const val DEBUG = true

    // --- Message What Definitions ---
    private const val WHAT_PLAYER_CHANGED = 1
    private const val WHAT_SONG_CHANGED = 2
    private const val WHAT_PLAYBACK_STATE_CHANGED = 3
    private const val WHAT_POSITION_UPDATE = 4
    private const val WHAT_SEEK_TO = 5
    private const val WHAT_TEXT_RECEIVED = 6
    private const val WHAT_TRANSLATION_TOGGLE = 7
    private const val WHAT_ROMA_TOGGLE = 8
    private const val WHAT_AI_TRANSLATION_FINISHED = 9

    /** 当前是否处于播放状态 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 当前活跃播放器的包名 */
    @Volatile
    var activePackage: String = ""
        private set

    /** 当前播放器提供者信息 */
    @Volatile
    var providerInfo: ProviderInfo? = null
        private set

    /** 翻译显示开关状态（由全局/系统广播控制） */
    @Volatile
    private var isDisplayTranslation: Boolean = true

    /** 罗马音显示开关状态 */
    @Volatile
    private var isDisplayRoma: Boolean = true

    /** 原始歌曲对象，不包含 AI 翻译结果，用于回退 */
    @Volatile
    private var rawSong: Song? = null

    /** 当前展示的歌曲对象，可能包含 AI 翻译结果 */
    @Volatile
    private var currentSong: Song? = null

    /** 样式配置标识符，用于检测配置是否发生实质性变化 */
    private var translationSettingSign = ""

    /** 歌曲数据版本，用于解决异步翻译回调的竞态问题 */
    private var songDataVersion: Int = 0

    private val uiHandler by lazy { Handler(Looper.getMainLooper(), this) }

    private var lastLyricStyle: LyricStyle? = null

    init {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Initializing LyricViewController")
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    // --- ActivePlayerListener Implementation ---

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        uiHandler.obtainMessage(WHAT_PLAYER_CHANGED, providerInfo).sendToTarget()
    }

    override fun onSongChanged(song: Song?) {
        uiHandler.obtainMessage(WHAT_SONG_CHANGED, song).sendToTarget()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        uiHandler.obtainMessage(WHAT_PLAYBACK_STATE_CHANGED, if (isPlaying) 1 else 0, 0)
            .sendToTarget()
    }

    override fun onPositionChanged(position: Long) {
        // 进度更新高频触发，移除旧消息以减轻主线程压力
        uiHandler.removeMessages(WHAT_POSITION_UPDATE)
        sendLongMessage(WHAT_POSITION_UPDATE, position)
    }

    override fun onSeekTo(position: Long) {
        sendLongMessage(WHAT_SEEK_TO, position)
    }

    override fun onSendText(text: String?) {
        uiHandler.obtainMessage(WHAT_TEXT_RECEIVED, text).sendToTarget()
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        this.isDisplayTranslation = isDisplayTranslation
        uiHandler.obtainMessage(WHAT_TRANSLATION_TOGGLE, if (isDisplayTranslation) 1 else 0, 0)
            .sendToTarget()
    }

    override fun onDisplayRomaChanged(displayRoma: Boolean) {
        this.isDisplayRoma = displayRoma
        uiHandler.obtainMessage(WHAT_ROMA_TOGGLE, if (displayRoma) 1 else 0, 0).sendToTarget()
    }

    // --- Core Logic & Handler ---

    override fun handleMessage(msg: Message): Boolean {
        // 1. 全局状态同步：根据消息类型预更新内部变量
        when (msg.what) {
            WHAT_PLAYER_CHANGED -> {
                songDataVersion++ // 切歌或切换播放器，旧版本失效
                rawSong = null
                currentSong = null
                val provider = msg.obj as? ProviderInfo
                providerInfo = provider
                activePackage = provider?.playerPackageName.orEmpty()
                LyricPrefs.activePackageName = activePackage
            }

            WHAT_SONG_CHANGED -> {
                val song = msg.obj as? Song
                rawSong = song
                currentSong = song
                if (song != null) startAiTranslationTask(song)
            }

            WHAT_PLAYBACK_STATE_CHANGED -> {
                isPlaying = msg.arg1 == 1
            }

            WHAT_AI_TRANSLATION_FINISHED -> {
                // 仅当版本号匹配时才更新当前歌曲，防止覆盖新切的歌曲
                if (msg.arg1 == songDataVersion) {
                    (msg.obj as? Song)?.let { currentSong = it }
                } else return true
            }
        }

        // 2. 任务分发：更新所有已注册的状态栏视图控制器
        dispatchToControllers(msg)
        return true
    }

    /**
     * 将事件分发给所有状态栏控制器，处理 UI 更新
     */
    private fun dispatchToControllers(msg: Message) {
        forEachController {
            try {
                val view = lyricView
                when (msg.what) {
                    WHAT_PLAYER_CHANGED -> performPlayerChange(this, msg.obj as? ProviderInfo)
                    WHAT_SONG_CHANGED -> view.setSong(processSongByStyle(msg.obj as? Song))
                    WHAT_PLAYBACK_STATE_CHANGED -> view.setPlaying(isPlaying)
                    WHAT_POSITION_UPDATE -> view.setPosition(unpackLong(msg.arg1, msg.arg2))
                    WHAT_SEEK_TO -> view.seekTo(unpackLong(msg.arg1, msg.arg2))
                    WHAT_TEXT_RECEIVED -> view.setText(msg.obj as? String)
                    WHAT_TRANSLATION_TOGGLE -> refreshTranslationVisibility(view)
                    WHAT_ROMA_TOGGLE -> view.updateDisplayTranslation(displayRoma = isDisplayRoma)
                    WHAT_AI_TRANSLATION_FINISHED -> {
                        if (msg.arg1 == songDataVersion) {
                            view.setSong(processSongByStyle(msg.obj as? Song))
                        }
                    }
                }
            } catch (e: Throwable) {
                YLog.error(tag = TAG, msg = "Dispatch WHAT_${msg.what} failed", e = e)
            }
        }
    }

    // --- Style & Configuration Logic ---

    /**
     * 更新歌词视图样式并执行配置变更自适应
     */
    fun updateLyricViewStyle(style: LyricStyle) {
        lastLyricStyle = style
        forEachController { updateLyricStyle(style) }
        checkTranslationChange(style)
    }

    /**
     * 检查并应用翻译设置变更
     * 如果设置发生变化，会重新评估是否需要翻译或回退到原始歌曲
     */
    private fun checkTranslationChange(style: LyricStyle) {
        val textStyle = style.packageStyle.text
        val currentSign =
            "${textStyle.isAiTranslationEnable}|${textStyle.isTranslationOnly}|${textStyle.isDisableTranslation}"

        if (translationSettingSign == currentSign) return
        translationSettingSign = currentSign

        if (DEBUG) YLog.debug(
            tag = TAG,
            msg = "Translation settings signature changed, re-evaluating..."
        )

        // 设置变化后，基于原始歌曲重新执行任务流
        rawSong?.let { startAiTranslationTask(it) }

        forEachController {
            lyricView.setSong(processSongByStyle(currentSong))
            refreshTranslationVisibility(lyricView)
        }
    }

    fun notifyTranslationDbChange() {
        translationSettingSign = ""
        lastLyricStyle?.let { checkTranslationChange(it) }
    }

    /**
     * 执行播放器切换逻辑，重置视图状态
     */
    private fun performPlayerChange(controller: StatusBarViewController, provider: ProviderInfo?) {
        val view = controller.lyricView
        view.setSong(null)
        view.setPlaying(false)
        controller.updateLyricStyle(LyricPrefs.getLyricStyle())
        view.updateVisibility()

        view.logoView.apply {
            activePackage = this@LyricViewController.activePackage
            val cover = activePackage?.let { NotificationCoverHelper.getCoverFile(it) }
            coverFile = cover
            controller.updateCoverThemeColors(cover)
            post { providerLogo = provider?.logo }
        }
    }

    /**
     * 根据当前样式偏好（如“仅显示翻译”）二次加工歌曲数据
     */
    private fun processSongByStyle(song: Song?): Song? {
        val style = LyricPrefs.activePackageStyle
        if (style.text.isTranslationOnly && song != null) {
            return song.deepCopy().copy(
                lyrics = song.lyrics?.map { line ->
                    if (!line.translation.isNullOrBlank()) {
                        line.copy(
                            text = line.translation,
                            words = null,
                            translation = null,
                            translationWords = null
                        )
                    } else line
                }
            )
        }
        return song
    }

    /**
     * 自动刷新翻译内容的可见性
     */
    private fun refreshTranslationVisibility(view: StatusBarLyric) {
        val style = LyricPrefs.activePackageStyle
        val shouldShow = isDisplayTranslation &&
                !style.text.isDisableTranslation &&
                !style.text.isTranslationOnly
        view.updateDisplayTranslation(displayTranslation = shouldShow)
    }

    /**
     * 启动 AI 翻译任务。如果条件不满足，则主动同步原始歌曲以恢复状态。
     */
    private fun startAiTranslationTask(song: Song) {

        val style = LyricPrefs.activePackageStyle
        val configs = style.text.aiTranslationConfigs

        // 校验：如果不满足 AI 翻译条件，立即将 currentSong 同步为原始版本并通知分发
        if (!isDisplayTranslation || !style.text.isAiTranslationEnable || configs?.isUsable != true) {
            if (DEBUG) YLog.debug(
                tag = TAG,
                msg = "AI Translation requirement not met, falling back to raw song"
            )
            uiHandler.obtainMessage(WHAT_AI_TRANSLATION_FINISHED, songDataVersion, 0, song)
                .sendToTarget()
            return
        }

        val version = songDataVersion
        AiTranslationManager.translateSongIfNeededAsync(song, configs) { translated ->
            uiHandler.obtainMessage(WHAT_AI_TRANSLATION_FINISHED, version, 0, translated)
                .sendToTarget()
        }
    }

    // --- Internal Utils ---

    private fun sendLongMessage(what: Int, value: Long) {
        uiHandler.obtainMessage(what, (value shr 32).toInt(), (value and 0xFFFFFFFFL).toInt())
            .sendToTarget()
    }

    private fun unpackLong(high: Int, low: Int): Long =
        (high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFFL)

    private inline fun forEachController(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEach {
            runCatching { it.block() }.onFailure {
                YLog.error(
                    tag = TAG,
                    msg = "Controller iteration error",
                    e = it
                )
            }
        }
    }

    // --- Callback Overrides ---

    override fun onColorOsCapsuleVisibilityChanged(isShowing: Boolean) {
        forEachController { lyricView.setOplusCapsuleVisibility(isShowing) }
    }

    override fun onCoverUpdated(packageName: String, coverFile: File) {
        if (packageName != activePackage) return
        forEachController {
            lyricView.logoView.apply {
                this.coverFile = coverFile
                if (strategy is SuperLogo.CoverStrategy) (strategy as? SuperLogo.CoverStrategy)?.updateContent()
            }
            updateCoverThemeColors(coverFile)
        }
    }
}