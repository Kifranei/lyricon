/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.lyricon.localcentralapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.lyricon.localcentralapp.databinding.ActivityMainBinding
import io.github.lyricon.localcentralapp.util.FloatingPermissionUtil
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

class MainActivity : AppCompatActivity() {

    var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding?.toggle?.setOnClickListener {
            toggleFloatingWindow()
        }

        if (FloatingPermissionUtil.hasPermission(this)) {
            onToggleClick()
        }

        ActivePlayerDispatcher.addActivePlayerListener(object : ActivePlayerListener {
            override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
                log("onActiveProviderChanged: $providerInfo")
            }

            override fun onSongChanged(song: Song?) {
                log("onSongChanged: ${song?.name}-${song?.artist},lyriccount=${song?.lyrics?.size}")
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) {
                log("onPlaybackStateChanged: $isPlaying")
            }

            override fun onPositionChanged(position: Long) {

            }

            override fun onSeekTo(position: Long) {
                log("onSeekTo: $position")
            }

            override fun onSendText(text: String?) {
                log("onSendText: $text")
            }

            override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
                log("onDisplayTranslationChanged: $isDisplayTranslation")
            }
        })
    }

    private fun log(text: String) {
        binding?.log?.length()?.let {
            if (it > 4096) {
                binding?.log?.text = ""
            }
        }
        binding?.log?.append(text, 0, text.length)
        binding?.log?.append("\n")
    }

    fun onToggleClick() {
        val intent = Intent(this, FloatWindowService::class.java).apply {
            action = FloatWindowService.ACTION_TOGGLE
        }
        startService(intent)
    }

    fun toggleFloatingWindow() {
        if (FloatingPermissionUtil.hasPermission(this)) {
            onToggleClick()
        } else {
            FloatingPermissionUtil.requestPermission(this)
        }
    }
}