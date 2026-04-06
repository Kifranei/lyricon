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
 * 歌词视图控制器
 *
 * 核心职责：
 * 1. 监听并分发播放器状态（播放/暂停/进度/歌曲变更等事件）。
 * 2. 管理 AI 翻译生命周期，通过版本控制确保异步回调数据的一致性。
 * 3. 动态响应全局及特定应用的样式配置变更，并同步到底层视图。
 */
object LyricViewController : ActivePlayerListener, Handler.Callback,
    OplusCapsuleHooker.CapsuleStateChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    private const val TAG = "LyricViewController"
    private const val DEBUG = true

    private const val WHAT_PLAYER_CHANGED = 1
    private const val WHAT_SONG_CHANGED = 2
    private const val WHAT_PLAYBACK_STATE_CHANGED = 3
    private const val WHAT_POSITION_CHANGED = 4
    private const val WHAT_SEEK_TO = 5
    private const val WHAT_TEXT_RECEIVED = 6
    private const val WHAT_TRANSLATION_TOGGLE = 7
    private const val WHAT_ROMA_TOGGLE = 8
    private const val WHAT_AI_TRANSLATION_FINISHED = 9

    /** 当前播放器是否处于播放中状态 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 当前活跃播放器的包名 */
    @Volatile
    var activePackage: String = ""
        private set

    /** 当前活跃播放器的提供者信息集合 */
    @Volatile
    var providerInfo: ProviderInfo? = null
        private set

    /** 翻译显示开关（受全局或系统广播控制） */
    @Volatile
    private var isDisplayTranslation: Boolean = true

    /** 罗马音显示开关（受全局或系统广播控制） */
    @Volatile
    private var isDisplayRoma: Boolean = true

    /** 原始歌曲数据对象（不包含动态 AI 翻译结果，用于配置变更时的状态回退） */
    @Volatile
    private var rawSong: Song? = null

    /** 当前实际展示的歌曲数据对象（可能已合并 AI 翻译结果） */
    @Volatile
    private var currentSong: Song? = null

    /** 翻译样式的配置签名，用于检测配置是否发生实质性变更 */
    private var translationSettingSignature = ""

    /** 歌曲状态版本号，自增用于废弃过期的异步翻译任务回调 */
    private var songStateVersion: Int = 0

    /** 主线程 UI 消息处理器 */
    private val uiHandler by lazy { Handler(Looper.getMainLooper(), this) }

    /** 记录最近一次应用的歌词样式 */
    private var lastLyricStyle: LyricStyle? = null

    init {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Initializing LyricViewController")
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        uiHandler.obtainMessage(WHAT_PLAYER_CHANGED, providerInfo).sendToTarget()
    }

    override fun onSongChanged(song: Song?) {
        /**
         * 根据用户配置的屏蔽词正则过滤歌词行
         */
        fun filterBlockedWords(songToProcess: Song?): Song? {
            val style = LyricPrefs.baseStyle
            val blockedWordsRegex = style.blockedWordsRegex ?: return songToProcess

            val lyrics = songToProcess?.lyrics?.mapNotNull { line ->
                val text = line.text
                if (text.isNullOrEmpty()) return@mapNotNull null
                if (blockedWordsRegex.containsMatchIn(text)) {
                    null
                } else line
            }
            return songToProcess?.copy(lyrics = lyrics)
        }
        val processedSong = filterBlockedWords(song)
        uiHandler.obtainMessage(WHAT_SONG_CHANGED, processedSong).sendToTarget()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        uiHandler.obtainMessage(WHAT_PLAYBACK_STATE_CHANGED, if (isPlaying) 1 else 0, 0)
            .sendToTarget()
    }

    override fun onPositionChanged(position: Long) {
        // 进度更新高频触发，移除旧消息以减轻主线程队列压力
        uiHandler.removeMessages(WHAT_POSITION_CHANGED)
        sendLongMessage(WHAT_POSITION_CHANGED, position)
    }

    override fun onSeekTo(position: Long) {
        sendLongMessage(WHAT_SEEK_TO, position)
    }

    override fun onReceiveText(text: String?) {
        uiHandler.obtainMessage(WHAT_TEXT_RECEIVED, text).sendToTarget()
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        this.isDisplayTranslation = isDisplayTranslation
        uiHandler.obtainMessage(WHAT_TRANSLATION_TOGGLE, if (isDisplayTranslation) 1 else 0, 0)
            .sendToTarget()
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        this.isDisplayRoma = isDisplayRoma
        uiHandler.obtainMessage(WHAT_ROMA_TOGGLE, if (isDisplayRoma) 1 else 0, 0).sendToTarget()
    }

    /**
     * 处理由监听器分发至主线程的各类事件消息
     */
    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            WHAT_PLAYER_CHANGED -> {
                songStateVersion++
                rawSong = null
                currentSong = null
                val provider = msg.obj as? ProviderInfo
                providerInfo = provider
                activePackage = provider?.playerPackageName.orEmpty()
                LyricPrefs.activePackageName = activePackage
            }

            WHAT_SONG_CHANGED -> {
                songStateVersion++
                val song = msg.obj as? Song
                rawSong = song
                currentSong = song
                if (song != null) startAiTranslationTask(song.deepCopy())
            }

            WHAT_PLAYBACK_STATE_CHANGED -> {
                isPlaying = msg.arg1 == 1
            }

            WHAT_AI_TRANSLATION_FINISHED -> {
                if (msg.arg1 == songStateVersion) {
                    (msg.obj as? Song)?.let { currentSong = it }
                } else return true
            }
        }

        dispatchToControllers(msg)
        return true
    }

    /**
     * 将解析后的事件具体分发给所有已注册的状态栏视图控制器以驱动 UI 更新
     *
     * @param msg 携带状态更新数据的消息对象
     */
    private fun dispatchToControllers(msg: Message) {
        forEachController {
            try {
                val view = lyricView
                when (msg.what) {
                    WHAT_PLAYER_CHANGED -> resetViewForNewPlayer(this, msg.obj as? ProviderInfo)
                    WHAT_SONG_CHANGED -> view.setSong(applyTranslationStyleToSong(msg.obj as? Song))
                    WHAT_PLAYBACK_STATE_CHANGED -> view.setPlaying(isPlaying)
                    WHAT_POSITION_CHANGED -> view.setPosition(unpackLong(msg.arg1, msg.arg2))
                    WHAT_SEEK_TO -> view.seekTo(unpackLong(msg.arg1, msg.arg2))
                    WHAT_TEXT_RECEIVED -> view.setText(msg.obj as? String)
                    WHAT_TRANSLATION_TOGGLE -> refreshTranslationVisibility(view)
                    WHAT_ROMA_TOGGLE -> view.updateDisplayTranslation(displayRoma = isDisplayRoma)
                    WHAT_AI_TRANSLATION_FINISHED -> {
                        if (msg.arg1 == songStateVersion) {
                            view.setSong(applyTranslationStyleToSong(msg.obj as? Song))
                        }
                    }
                }
            } catch (e: Throwable) {
                YLog.error(tag = TAG, msg = "Dispatch WHAT_${msg.what} failed", e = e)
            }
        }
    }

    /**
     * 更新歌词视图基础样式并触发相关配置的连锁变更检测
     *
     * @param style 新的歌词样式配置
     */
    fun updateLyricViewStyle(style: LyricStyle) {
        lastLyricStyle = style
        forEachController { updateLyricStyle(style) }
        evaluateTranslationSettings(style)
    }

    /**
     * 评估翻译相关设置是否发生变更，并在必要时重启翻译任务或重置展示状态
     *
     * @param style 包含最新翻译配置规则的样式对象
     */
    private fun evaluateTranslationSettings(style: LyricStyle) {
        val textStyle = style.packageStyle.text
        val currentSignature =
            "${textStyle.isAiTranslationEnable}|${textStyle.isTranslationOnly}|${textStyle.isDisableTranslation}"

        if (translationSettingSignature == currentSignature) return
        translationSettingSignature = currentSignature

        if (DEBUG) YLog.debug(
            tag = TAG,
            msg = "Translation settings signature changed, re-evaluating..."
        )

        rawSong?.let { startAiTranslationTask(it) }

        forEachController {
            lyricView.setSong(applyTranslationStyleToSong(currentSong))
            refreshTranslationVisibility(lyricView)
        }
    }

    /**
     * 通知翻译数据库已变更，强制使翻译配置签名失效并重新评估
     */
    fun notifyTranslationDbChange() {
        translationSettingSignature = ""
        lastLyricStyle?.let { evaluateTranslationSettings(it) }
    }

    /**
     * 处理播放器源的切换，重置视图数据并加载新播放器的 Logo 与封面
     *
     * @param controller 目标状态栏视图控制器
     * @param provider 新的播放器提供者信息
     */
    private fun resetViewForNewPlayer(controller: StatusBarViewController, provider: ProviderInfo?) {
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
     * 根据当前样式偏好（如“仅显示翻译”）对原歌曲数据进行最终显示前的加工
     *
     * @param song 待处理的歌曲对象
     * @return 经过样式适配处理后的新歌曲对象
     */
    private fun applyTranslationStyleToSong(song: Song?): Song? {
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
     * 根据当前全局开关及具体应用的样式设定，刷新翻译层的可见性
     *
     * @param view 需要刷新状态的目标歌词视图
     */
    private fun refreshTranslationVisibility(view: StatusBarLyric) {
        val style = LyricPrefs.activePackageStyle
        val shouldShow = isDisplayTranslation &&
                !style.text.isDisableTranslation &&
                !style.text.isTranslationOnly
        view.updateDisplayTranslation(displayTranslation = shouldShow)
    }

    /**
     * 启动异步 AI 翻译任务
     * 任务完成后将通过消息机制回传结果，由版本号保证数据有效性
     *
     * @param song 需要进行翻译的歌曲对象
     */
    private fun startAiTranslationTask(song: Song) {
        val style = LyricPrefs.activePackageStyle
        val configs = style.text.aiTranslationConfigs

        if (!isDisplayTranslation
            || !style.text.isAiTranslationEnable
            || configs?.isUsable != true
            || song.isTranslated()
        ) {
            return
        }

        val version = songStateVersion
        AiTranslationManager.translateSongIfNeededAsync(song, configs) { translated ->
            uiHandler.obtainMessage(WHAT_AI_TRANSLATION_FINISHED, version, 0, translated)
                .sendToTarget()
        }
    }

    /**
     * 判断该歌曲是否已包含完整的翻译内容
     */
    private fun Song.isTranslated(): Boolean {
        return lyrics.orEmpty().all {
            !it.translation.isNullOrBlank()
        }
    }

    /**
     * 拆分 Long 型数值并通过 Handler 发送，避免创建不必要的包装对象
     */
    private fun sendLongMessage(what: Int, value: Long) {
        uiHandler.obtainMessage(what, (value shr 32).toInt(), (value and 0xFFFFFFFFL).toInt())
            .sendToTarget()
    }

    /**
     * 还原由两个 Int 组合而成的 Long 型数值
     */
    private fun unpackLong(high: Int, low: Int): Long =
        (high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFFL)

    /**
     * 安全地遍历所有注册的状态栏控制器并执行操作
     */
    private inline fun forEachController(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEach {
            runCatching { it.block() }.onFailure { error ->
                YLog.error(
                    tag = TAG,
                    msg = "Controller iteration error",
                    e = error
                )
            }
        }
    }

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