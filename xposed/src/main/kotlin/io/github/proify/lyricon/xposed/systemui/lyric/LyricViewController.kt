/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.Handler
import android.os.Handler.Callback
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.SuperLogo
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.systemui.util.ChineseConverter.toSimplified
import io.github.proify.lyricon.xposed.systemui.util.ChineseConverter.toTraditional
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.util.XiaomiIslandHooker
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 歌词视图控制器
 *
 * 状态分层：
 * 1. rawSong：播放器原始数据
 * 2. aiTranslatedSong：AI 翻译后的数据
 * 3. displaySong：最终展示给视图的数据
 *
 * 处理原则：
 * - AI 翻译只吃 rawSong
 * - 屏蔽词、简繁转换、translationOnly 只作用于展示层
 * - 任何样式变化都通过统一的展示重建入口处理
 */
object LyricViewController : ActivePlayerListener, Callback,
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
    private const val WHAT_VENDOR_SYNC_TICK = 10

    private const val TOKEN_PROCESS_SONG = "ProcessSong"
    private const val PLAYBACK_ACTIVE_STALE_MS = 2500L

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var activePackage: String = ""
        private set

    @Volatile
    var providerInfo: ProviderInfo? = null
        private set

    @Volatile
    private var isDisplayTranslation: Boolean = true

    @Volatile
    private var isDisplayRoma: Boolean = true

    @Volatile
    private var rawSong: Song? = null

    @Volatile
    private var aiTranslatedSong: Song? = null

    @Volatile
    private var displaySong: Song? = null

    private var translationSettingSignature = ""
    private var lastChineseConversionMode: Int = BasicStyle.CHINESE_CONVERSION_OFF
    private var songStateVersion: Int = 0
    private var lastLyricStyle: LyricStyle? = null
    @Volatile
    private var lastPositionUpdateAt: Long = 0L
    @Volatile
    private var lastKnownPosition: Long = 0L

    private val uiHandler by lazy { Handler(Looper.getMainLooper(), this) }
    private val workerThread = HandlerThread("LyricViewController").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val displayProcessingVersion = AtomicInteger(0)

    init {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Initializing LyricViewController")
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        uiHandler.obtainMessage(WHAT_PLAYER_CHANGED, providerInfo).sendToTarget()
    }

    override fun onSongChanged(song: Song?) {
        if (song == null) {
            clearCurrentSongState()
            sendClearDisplaySong()
            return
        }

        songStateVersion++
        rawSong = song
        aiTranslatedSong = null

        requestDisplaySongRebuild()
        maybeStartAiTranslationTask()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            lastPositionUpdateAt = SystemClock.uptimeMillis()
            uiHandler.removeMessages(WHAT_VENDOR_SYNC_TICK)
            uiHandler.sendEmptyMessageDelayed(WHAT_VENDOR_SYNC_TICK, PLAYBACK_ACTIVE_STALE_MS)
        } else {
            uiHandler.removeMessages(WHAT_VENDOR_SYNC_TICK)
        }
        uiHandler.obtainMessage(WHAT_PLAYBACK_STATE_CHANGED, if (isPlaying) 1 else 0, 0)
            .sendToTarget()
    }

    override fun onPositionChanged(position: Long) {
        lastKnownPosition = position.coerceAtLeast(0L)
        lastPositionUpdateAt = SystemClock.uptimeMillis()
        uiHandler.removeMessages(WHAT_VENDOR_SYNC_TICK)
        uiHandler.sendEmptyMessageDelayed(WHAT_VENDOR_SYNC_TICK, PLAYBACK_ACTIVE_STALE_MS)
        uiHandler.removeMessages(WHAT_POSITION_CHANGED)
        sendLongMessage(WHAT_POSITION_CHANGED, position)
    }

    override fun onSeekTo(position: Long) {
        lastKnownPosition = position.coerceAtLeast(0L)
        lastPositionUpdateAt = SystemClock.uptimeMillis()
        uiHandler.removeMessages(WHAT_VENDOR_SYNC_TICK)
        uiHandler.sendEmptyMessageDelayed(WHAT_VENDOR_SYNC_TICK, PLAYBACK_ACTIVE_STALE_MS)
        sendLongMessage(WHAT_SEEK_TO, position)
    }

    override fun onReceiveText(text: String?) {
        uiHandler.obtainMessage(WHAT_TEXT_RECEIVED, text).sendToTarget()
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        this.isDisplayTranslation = isDisplayTranslation
        uiHandler.obtainMessage(WHAT_TRANSLATION_TOGGLE, if (isDisplayTranslation) 1 else 0, 0)
            .sendToTarget()

        requestDisplaySongRebuild()
        if (isDisplayTranslation) {
            maybeStartAiTranslationTask()
        }
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        this.isDisplayRoma = isDisplayRoma
        uiHandler.obtainMessage(WHAT_ROMA_TOGGLE, if (isDisplayRoma) 1 else 0, 0)
            .sendToTarget()
    }

    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == WHAT_VENDOR_SYNC_TICK) {
            syncVendorTemporaryUi()
            return true
        }

        when (msg.what) {
            WHAT_PLAYER_CHANGED -> {
                handlePlayerChanged(msg.obj as? ProviderInfo)
                dispatchToControllers(msg)
            }

            WHAT_SONG_CHANGED -> handleDisplaySongChanged(msg)

            WHAT_PLAYBACK_STATE_CHANGED -> {
                isPlaying = msg.arg1 == 1
                dispatchToControllers(msg)
            }

            WHAT_AI_TRANSLATION_FINISHED -> handleAiTranslationFinished(msg)

            WHAT_POSITION_CHANGED,
            WHAT_SEEK_TO,
            WHAT_TEXT_RECEIVED,
            WHAT_TRANSLATION_TOGGLE,
            WHAT_ROMA_TOGGLE -> dispatchToControllers(msg)
        }
        if (shouldSyncVendorForMessage(msg.what)) {
            syncVendorTemporaryUi()
        }
        return true
    }

    private fun handlePlayerChanged(provider: ProviderInfo?) {
        clearCurrentSongState()

        providerInfo = provider
        activePackage = provider?.playerPackageName.orEmpty()
        LyricPrefs.activePackageName = activePackage
    }

    private fun handleDisplaySongChanged(msg: Message) {
        if (msg.arg1 != displayProcessingVersion.get()) return

        displaySong = msg.obj as? Song
        dispatchDisplaySongToControllers(displaySong)
    }

    private fun handleAiTranslationFinished(msg: Message) {
        if (msg.arg1 != songStateVersion) return

        aiTranslatedSong = msg.obj as? Song
        requestDisplaySongRebuild()
    }

    private fun clearCurrentSongState() {
        songStateVersion++
        rawSong = null
        aiTranslatedSong = null
        displaySong = null
        lastKnownPosition = 0L
        lastPositionUpdateAt = 0L

        displayProcessingVersion.incrementAndGet()
        workerHandler.removeCallbacksAndMessages(TOKEN_PROCESS_SONG)
        uiHandler.removeMessages(WHAT_VENDOR_SYNC_TICK)
    }

    private fun sendClearDisplaySong() {
        val version = displayProcessingVersion.incrementAndGet()
        workerHandler.removeCallbacksAndMessages(TOKEN_PROCESS_SONG)
        uiHandler.obtainMessage(WHAT_SONG_CHANGED, version, 0, null).sendToTarget()
    }

    private fun requestDisplaySongRebuild() {
        val source = resolveDisplaySourceSong()
        if (source == null) {
            sendClearDisplaySong()
            return
        }
        scheduleDisplaySongRebuild(source)
    }

    private fun resolveDisplaySourceSong(): Song? {
        val style = LyricPrefs.activePackageStyle
        val canUseAiTranslation =
            isDisplayTranslation &&
                    !style.text.isDisableTranslation &&
                    style.text.isAiTranslationEnable

        return when {
            canUseAiTranslation -> aiTranslatedSong ?: rawSong
            else -> rawSong
        }
    }

    private fun scheduleDisplaySongRebuild(songToProcess: Song) {
        val version = displayProcessingVersion.incrementAndGet()

        workerHandler.removeCallbacksAndMessages(TOKEN_PROCESS_SONG)

        workerHandler.postAtTime({
            val startTime = SystemClock.elapsedRealtime()

            val processed = songToProcess.deepCopy()
                .let(::filterBlockedWords)
                .let(::processSimpleAndTraditionalChinese)
                .let(::applyTranslationStyleToSong)

            if (DEBUG) {
                YLog.debug(
                    tag = TAG,
                    msg = "Display song rebuild finished in ${SystemClock.elapsedRealtime() - startTime} ms"
                )
            }

            if (version != displayProcessingVersion.get()) return@postAtTime

            uiHandler.obtainMessage(WHAT_SONG_CHANGED, version, 0, processed).sendToTarget()
        }, TOKEN_PROCESS_SONG, SystemClock.uptimeMillis())
    }

    fun updateLyricViewStyle(style: LyricStyle) {
        lastLyricStyle = style

        forEachController { updateLyricStyle(style) }

        val translationChanged = evaluateTranslationSettings(style)
        val baseChanged = evaluateBaseSettings(style.basicStyle)

        if (translationChanged) {
            forEachController { refreshTranslationVisibility(lyricView) }
            maybeStartAiTranslationTask()
        }

        if (translationChanged || baseChanged) {
            requestDisplaySongRebuild()
        }
    }

    private fun evaluateBaseSettings(baseStyle: BasicStyle): Boolean {
        val newMode = baseStyle.chineseConversionMode
        if (lastChineseConversionMode == newMode) return false

        lastChineseConversionMode = newMode

        if (DEBUG) {
            YLog.debug(tag = TAG, msg = "Base setting changed: ChineseConversionMode = $newMode")
        }
        return true
    }

    private fun evaluateTranslationSettings(style: LyricStyle): Boolean {
        val textStyle = style.packageStyle.text
        val signature =
            "${textStyle.isAiTranslationEnable}|${textStyle.isTranslationOnly}|${textStyle.isDisableTranslation}"

        if (translationSettingSignature == signature) return false
        translationSettingSignature = signature

        if (DEBUG) {
            YLog.debug(tag = TAG, msg = "Translation setting changed: $signature")
        }
        return true
    }

    fun notifyTranslationDbChange() {
        translationSettingSignature = ""
        lastLyricStyle?.let { style ->
            val changed = evaluateTranslationSettings(style)
            if (changed) {
                forEachController { refreshTranslationVisibility(lyricView) }
                maybeStartAiTranslationTask()
                requestDisplaySongRebuild()
            }
        }
    }

    fun refreshLyricTranslationDisplayConfig() {
        val song = displaySong
        forEachController {
            song?.let {
                lyricView.setSong(it)
                if (lastKnownPosition > 0L) {
                    lyricView.seekTo(lastKnownPosition)
                    lyricView.setPosition(lastKnownPosition)
                }
            }
            refreshTranslationVisibility(lyricView)
        }
        syncVendorTemporaryUi()
    }

    fun notifyLyricVisibilityChanged() {
        syncVendorTemporaryUi()
    }

    private fun dispatchToControllers(msg: Message) {
        forEachController {
            try {
                val view = lyricView
                when (msg.what) {
                    WHAT_PLAYER_CHANGED -> resetViewForNewPlayer(this, msg.obj as? ProviderInfo)
                    WHAT_PLAYBACK_STATE_CHANGED -> view.setPlaying(isPlaying)
                    WHAT_POSITION_CHANGED -> view.setPosition(unpackLong(msg.arg1, msg.arg2))
                    WHAT_SEEK_TO -> view.seekTo(unpackLong(msg.arg1, msg.arg2))
                    WHAT_TEXT_RECEIVED -> view.setText(msg.obj as? String)
                    WHAT_TRANSLATION_TOGGLE -> refreshTranslationVisibility(view)
                    WHAT_ROMA_TOGGLE -> view.updateDisplayTranslation(displayRoma = isDisplayRoma)
                }
            } catch (e: Throwable) {
                YLog.error(tag = TAG, msg = "Dispatch WHAT_${msg.what} failed", e = e)
            }
        }
    }

    private fun dispatchDisplaySongToControllers(song: Song?) {
        forEachController {
            try {
                val view = lyricView
                view.setSong(song)
                if (song != null && lastKnownPosition > 0L) {
                    view.seekTo(lastKnownPosition)
                    view.setPosition(lastKnownPosition)
                }
                refreshTranslationVisibility(view)
            } catch (e: Throwable) {
                YLog.error(tag = TAG, msg = "Dispatch display song failed", e = e)
            }
        }
    }

    private fun resetViewForNewPlayer(
        controller: StatusBarViewController,
        provider: ProviderInfo?
    ) {
        val view = controller.lyricView
        view.setSong(null)
        view.setPlaying(false)
        controller.updateLyricStyle(LyricPrefs.getLyricStyle())
        view.updateVisibility()

        view.logoView.apply {
            this.activePackage = this@LyricViewController.activePackage
            val activePackage = this@LyricViewController.activePackage

            val cover =
                if (activePackage.isBlank()) null else NotificationCoverHelper.getCoverFile(
                    activePackage
                )

            coverFile = cover
            controller.updateCoverThemeColors(cover)
            post { providerLogo = provider?.logo }
        }
    }

    private fun applyTranslationStyleToSong(song: Song?): Song? {
        val style = LyricPrefs.activePackageStyle
        if (song == null || !style.text.isTranslationOnly) return song

        return song.deepCopy().copy(
            lyrics = song.lyrics?.map { line ->
                if (!line.translation.isNullOrBlank()) {
                    line.copy(
                        text = line.translation,
                        words = null,
                        translation = null,
                        translationWords = null
                    )
                } else {
                    line
                }
            }
        )
    }

    private fun refreshTranslationVisibility(view: StatusBarLyric) {
        val style = LyricPrefs.activePackageStyle
        val shouldShow = isDisplayTranslation &&
                !style.text.isDisableTranslation &&
                !style.text.isTranslationOnly

        view.updateDisplayTranslation(displayTranslation = shouldShow)
    }

    private fun maybeStartAiTranslationTask() {
        val song = rawSong ?: return
        if (aiTranslatedSong != null) return

        startAiTranslationTask(song.deepCopy(), songStateVersion)
    }

    private fun startAiTranslationTask(song: Song, version: Int) {
        val style = LyricPrefs.activePackageStyle
        val config = style.text.aiTranslationConfigs

        if (!isDisplayTranslation) return
        if (style.text.isDisableTranslation) return
        if (!style.text.isAiTranslationEnable) return
        if (config?.isUsable != true) return
        if (song.isTranslated()) return

        AiTranslationManager.translateSongIfNeededAsync(song, config) { translated ->
            uiHandler.obtainMessage(WHAT_AI_TRANSLATION_FINISHED, version, 0, translated)
                .sendToTarget()
        }
    }

    private fun Song.isTranslated(): Boolean =
        lyrics.orEmpty().all { !it.translation.isNullOrBlank() }

    private fun sendLongMessage(what: Int, value: Long) {
        uiHandler.obtainMessage(what, (value shr 32).toInt(), (value and 0xFFFFFFFFL).toInt())
            .sendToTarget()
    }

    private fun unpackLong(high: Int, low: Int): Long =
        (high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFFL)

    private inline fun forEachController(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEach {
            runCatching { it.block() }.onFailure { e ->
                YLog.error(tag = TAG, msg = "Iteration error", e = e)
            }
        }
    }

    override fun onColorOsCapsuleVisibilityChanged(isShowing: Boolean) {
        forEachController { lyricView.setOplusCapsuleVisibility(isShowing) }
        syncVendorTemporaryUi()
    }

    override fun onCoverUpdated(packageName: String, coverFile: File) {
        if (packageName != activePackage) return

        forEachController {
            lyricView.logoView.apply {
                this.coverFile = coverFile
                (strategy as? SuperLogo.CoverStrategy)?.updateContent()
            }
            updateCoverThemeColors(coverFile)
        }
        syncVendorTemporaryUi()
    }

    fun filterBlockedWords(songToProcess: Song?): Song? {
        val style = LyricPrefs.baseStyle
        val regex = style.blockedWordsRegex ?: return songToProcess

        val lyrics = songToProcess?.lyrics?.mapNotNull { line ->
            val text = line.text ?: return@mapNotNull null
            if (regex.containsMatchIn(text)) null else line
        }

        return songToProcess?.copy(lyrics = lyrics)
    }

    fun processSimpleAndTraditionalChinese(song: Song?): Song? {
        val style = LyricPrefs.baseStyle
        val mode = style.chineseConversionMode
        if (song == null || mode == BasicStyle.CHINESE_CONVERSION_OFF) return song

        val convert: (String?) -> String? = when (mode) {
            BasicStyle.CHINESE_CONVERSION_TRADITIONAL -> { value -> value?.toTraditional() }
            BasicStyle.CHINESE_CONVERSION_SIMPLIFIED -> { value -> value?.toSimplified() }
            else -> { value -> value }
        }

        val lyrics = song.lyrics?.map { line ->
            line.copy(
                words = line.words?.map { it.copy(text = convert(it.text)) },
                secondaryWords = line.secondaryWords?.map { it.copy(text = convert(it.text)) },
                translationWords = line.translationWords?.map { it.copy(text = convert(it.text)) }
            )
        }

        return song.copy(
            name = convert(song.name),
            artist = convert(song.artist),
            lyrics = lyrics
        ).normalize()
    }

    private fun shouldSyncVendorForMessage(what: Int): Boolean {
        return when (what) {
            WHAT_PLAYER_CHANGED,
            WHAT_SONG_CHANGED,
            WHAT_PLAYBACK_STATE_CHANGED,
            WHAT_TEXT_RECEIVED,
            WHAT_TRANSLATION_TOGGLE,
            WHAT_ROMA_TOGGLE,
            WHAT_AI_TRANSLATION_FINISHED -> true

            else -> false
        }
    }

    private fun syncVendorTemporaryUi() {
        val enableXiaomiIslandHide = LyricPrefs.baseStyle.xiaomiIslandTempHideEnabled
        val now = SystemClock.uptimeMillis()
        val playbackActive = isPlaying &&
            (lastPositionUpdateAt <= 0L || now - lastPositionUpdateAt <= PLAYBACK_ACTIVE_STALE_MS)
        val shouldHideXiaomiIsland = StatusBarViewManager.controllers.any { controller ->
            val view = controller.lyricView
            enableXiaomiIslandHide &&
                playbackActive &&
                view.isAttachedToWindow &&
                view.visibility == View.VISIBLE &&
                view.textView.shouldShow()
        }
        XiaomiIslandHooker.setHideByLyric(shouldHideXiaomiIsland)
    }
}
