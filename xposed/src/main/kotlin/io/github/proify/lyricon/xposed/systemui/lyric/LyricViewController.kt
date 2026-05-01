/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.SystemClock
import android.view.View
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.statusbarlyric.SuperLogo
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.hook.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.XiaomiIslandHooker
import java.io.File

object LyricViewController : ActivePlayerListener,
    OplusCapsuleHooker.CapsuleStateChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    private const val TAG = "LyricViewController"
    private const val DEBUG = true
    private const val PLAYBACK_ACTIVE_STALE_MS = 2500L

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var activePackage: String = ""
        private set

    @Volatile
    private var isDisplayTranslation: Boolean = true

    @Volatile
    private var isDisplayRoma: Boolean = true

    @Volatile
    private var lastKnownPosition: Long = 0L

    @Volatile
    private var lastPositionUpdateAt: Long = 0L

    @Volatile
    private var lastRenderedSong: Song? = null

    init {
        if (DEBUG) YLog.debug(tag = TAG, msg = "Initializing LyricViewController...")
        LyricDataHub.addListener(this)
        OplusCapsuleHooker.registerListener(this)
        NotificationCoverHelper.registerListener(this)
    }

    override fun onSongChanged(song: Song?) {
        lastRenderedSong = song
        if (DEBUG) YLog.debug(tag = TAG, msg = "Rendering UI for song: ${song?.name ?: "None"}")
        YLog.info(TAG, "onSongChanged: $song")
        updateAllControllers {
            lyricView.setSong(song)
            if (song != null && lastKnownPosition > 0L) {
                lyricView.seekTo(lastKnownPosition)
                lyricView.setPosition(lastKnownPosition)
            }
            refreshTranslationVisibility(lyricView)
        }
        scheduleVendorSync()
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        activePackage = providerInfo?.playerPackageName.orEmpty()
        LyricPrefs.activePackageName = activePackage
        lastRenderedSong = null
        lastKnownPosition = 0L
        lastPositionUpdateAt = 0L
        YLog.info(TAG, "onActiveProviderChanged: $providerInfo")

        updateAllControllers {
            resetViewForNewPlayer(this, providerInfo)
        }
        scheduleVendorSync()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (this.isPlaying == isPlaying) return
        YLog.info(TAG, "onPlaybackStateChanged: $isPlaying")
        this.isPlaying = isPlaying
        if (isPlaying) {
            lastPositionUpdateAt = SystemClock.uptimeMillis()
        }
        updateAllControllers { lyricView.setPlaying(isPlaying) }
        scheduleVendorSync()
    }

    override fun onPositionChanged(position: Long) {
        lastKnownPosition = position.coerceAtLeast(0L)
        lastPositionUpdateAt = SystemClock.uptimeMillis()
        updateAllControllers { lyricView.setPosition(position) }
        scheduleVendorSync()
    }

    override fun onSeekTo(position: Long) {
        lastKnownPosition = position.coerceAtLeast(0L)
        lastPositionUpdateAt = SystemClock.uptimeMillis()
        updateAllControllers {
            lyricView.seekTo(position)
            lyricView.setPosition(position)
        }
        scheduleVendorSync()
    }

    override fun onReceiveText(text: String?) {
        YLog.info(TAG, "onReceiveText: $text")
        updateAllControllers { lyricView.setText(text) }
        scheduleVendorSync()
    }

    override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
        YLog.info(TAG, "onDisplayTranslationChanged: $isDisplayTranslation")

        this.isDisplayTranslation = isDisplayTranslation
        updateAllControllers { refreshTranslationVisibility(lyricView) }
        scheduleVendorSync()
    }

    override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
        YLog.info(TAG, "onDisplayRomaChanged: $isDisplayRoma")

        this.isDisplayRoma = isDisplayRoma
        updateAllControllers { lyricView.updateDisplayTranslation(displayRoma = isDisplayRoma) }
        scheduleVendorSync()
    }

    fun applyConfigurationUpdate(style: LyricStyle) {
        updateAllControllers { updateLyricStyle(style) }
        LyricDataHub.reprocessCurrentSong()
        scheduleVendorSync()
    }

    fun notifyTranslationDbChange() {
        LyricDataHub.reprocessCurrentSong()
    }

    fun refreshLyricTranslationDisplayConfig() {
        val song = lastRenderedSong
        updateAllControllers {
            if (song != null) {
                lyricView.setSong(song)
                if (lastKnownPosition > 0L) {
                    lyricView.seekTo(lastKnownPosition)
                    lyricView.setPosition(lastKnownPosition)
                }
            }
            refreshTranslationVisibility(lyricView)
        }
        scheduleVendorSync()
    }

    fun notifyLyricVisibilityChanged() {
        scheduleVendorSync()
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
            val currentPackage = provider?.playerPackageName.orEmpty()
            activePackage = currentPackage

            val cover = if (currentPackage.isBlank()) null else NotificationCoverHelper.getCoverFile(currentPackage)
            coverFile = cover
            controller.updateCoverThemeColors(cover)
            post { providerLogo = provider?.logo }
        }
    }

    private fun refreshTranslationVisibility(view: StatusBarLyric) {
        val style = LyricPrefs.activePackageStyle
        val shouldShow = isDisplayTranslation &&
                !style.text.isDisableTranslation &&
                !style.text.isTranslationOnly
        view.updateDisplayTranslation(displayTranslation = shouldShow)
    }

    private inline fun updateAllControllers(crossinline block: StatusBarViewController.() -> Unit) {
        StatusBarViewManager.forEach { controller ->
            runCatching {
                controller.lyricView.post { controller.block() }
            }.onFailure { e ->
                YLog.error(tag = TAG, msg = "Dispatch UI update failed", e = e)
            }
        }
    }

    override fun onColorOsCapsuleVisibilityChanged(isShowing: Boolean) {
        updateAllControllers { lyricView.setOplusCapsuleVisibility(isShowing) }
        scheduleVendorSync()
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
        scheduleVendorSync()
    }

    private fun scheduleVendorSync() {
        StatusBarViewManager.controllers.firstOrNull()?.lyricView?.post { syncVendorTemporaryUi() }
            ?: syncVendorTemporaryUi()
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
