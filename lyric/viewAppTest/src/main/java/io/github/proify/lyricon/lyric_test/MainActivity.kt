/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric_test

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ThemeUtils
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.view.RichLyricLineConfig
import io.github.proify.lyricon.lyric_test.databinding.ActivityMainBinding
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    /** 是否正在拖动进度条 */
    private var startTrackingTouch = false

    /** 歌曲文件名列表 */
    private lateinit var songNames: Array<String>

    /** 当前播放歌曲索引 */
    private var index: Int = 0

    /** ExoPlayer 实例，由生命周期管理 */
    private var player: ExoPlayer? = null

    /** 暂存待切换音轨路径，当 player 尚未初始化时使用 */
    private var pendingTrackPath: String? = null

    /** JSON 解析器配置，忽略未知字段 */
    private val json = Json { ignoreUnknownKeys = true }

    /** LyriconProvider 帮助类，用于歌词同步和状态管理 */
    internal val provider by lazy {
        LyriconProvider(
            context = this,
            providerPackageName = packageName,
            logo = ProviderLogo.fromDrawable(this, R.drawable.play_arrow_24px),
            centralPackageNames = listOf("io.github.lyricon.localcentralapp")
        )
    }
    private var showtranslation = false

    @OptIn(ExperimentalUuidApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)


        // 初始化播放器
        player = ExoPlayer.Builder(this).build()

        // 初始化 UI 和事件
        setupUi()
        initializeLyricStyle()

        // 延迟注册 LyriconProvider
        lifecycleScope.launch {
            delay(0)
            provider.register()
        }

        // 生命周期安全的进度更新协程
        lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                val pos = player?.currentPosition ?: 0L
                if (!startTrackingTouch) {
                    val max = binding.slider.valueTo
                    binding.slider.value = pos.toFloat().coerceAtMost(max)
                    provider.player.setPosition(pos)
                }
                binding.lyric.setPosition(pos)
                delay(16L)
            }
        }

        // 默认加载歌曲
        songNames = assets.list("songs") ?: arrayOf()
        if (songNames.isNotEmpty()) loadSongFromAssets(0)
        play()


        binding.sendText.setOnClickListener {
            provider.player.sendText(Uuid.random().toString())
        }
        binding.toggletran.setOnClickListener {
            showtranslation = !showtranslation
            provider.player.setDisplayTranslation(showtranslation)

            binding.lyric.setDisplayTranslation(showtranslation)
        }
    }

    /**
     * 初始化 UI 事件监听
     */
    private fun setupUi() {
        // 长按状态栏弹出歌曲选择对话框
        binding.status.setOnLongClickListener { v ->
            val names = songNames.map { it.substringBeforeLast(".") }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle((v as TextView).text)
                .setItems(names) { _, i -> loadSongFromAssets(i) }
                .show()
            false
        }

        // 点击状态栏切换播放/暂停
        binding.status.setOnClickListener { toggle() }

        // 上一首
        binding.pre.setOnClickListener {
            var p = index - 1
            if (p < 0) p = songNames.size - 1
            loadSongFromAssets(p)
        }

        // 下一首
        binding.next.setOnClickListener {
            var p = index + 1
            if (p >= songNames.size) p = 0
            loadSongFromAssets(p)
        }

//        binding.testMarquee.setOnClickListener {
//            startActivity(Intent(this, MarqueeTestActivity::class.java))
//        }

        // 初始化滑动条
        binding.slider.valueFrom = 0f
        binding.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                startTrackingTouch = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                startTrackingTouch = false
                player?.seekTo(slider.value.toLong()) ?: provider.player.setPosition(
                    slider.value.toLong()
                )
            }
        })

        // 滑动更新时间文本
        binding.slider.addOnChangeListener { _, value, _ ->
            binding.time.text = value.toInt().toTimeString()
        }
    }

    /**
     * 切换播放/暂停
     */
    private fun toggle() {
        val isPlaying = player?.isPlaying == true
        if (isPlaying) pause() else play()
    }

    /**
     * 初始化歌词样式
     */
    @SuppressLint("RestrictedApi")
    private fun initializeLyricStyle() {
        val textColorPrimary = ThemeUtils.getThemeAttrColor(this, android.R.attr.textColorPrimary)
        val textColorSecondary =
            ThemeUtils.getThemeAttrColor(this, android.R.attr.textColorSecondary)
        val colorPrimary = ThemeUtils.getThemeAttrColor(this, android.R.attr.colorPrimary)

        val config = RichLyricLineConfig().apply {
            primary.run {
                textSize = 34f.sp
                textColor = textColorPrimary
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            syllable.run { highlightColor = colorPrimary }
            secondary.run {
                textSize = 26f.sp
                textColor = textColorSecondary
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }

        binding.lyric.setStyle(config)
    }

    /**
     * 从 assets 加载歌曲并显示歌词
     *
     * @param i 歌曲索引
     */
    @SuppressLint("LogConditional")
    fun loadSongFromAssets(i: Int) {
        if (songNames.isEmpty()) return
        val fileName = songNames[i]
        index = i

        val jsonText = assets.open("songs/$fileName").readAllBytes().toString(Charsets.UTF_8)
        val song = json.decodeFromString<Song>(jsonText)

        binding.slider.valueFrom = 0f
        if (song.duration > 0) binding.slider.valueTo = song.duration.toFloat()

        binding.toolbar.subtitle = song.name

        // 设置歌词右对齐
        // song.lyrics?.forEach { it.isAlignedRight = true }
        binding.lyric.song = song
        binding.lyric.setPosition(0)

        // 切换音轨
        val mp3Name = fileName.substringBeforeLast(".") + ".mp3"
        switchTrack(mp3Name)

        Log.d(TAG, song.toString())
        provider.player.setSong(song)
    }

    /**
     * 切换播放音轨
     *
     * @param fileName 音轨文件名（assets/media/下）
     */
    private fun switchTrack(fileName: String) {
        val uri = "asset:///media/$fileName"
        val mediaItem = MediaItem.fromUri(uri)
        player?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        } ?: run {
            pendingTrackPath = fileName
        }
    }

    /**
     * 播放
     */
    private fun play() {
        provider.player.setPlaybackState(true)
        binding.status.setIconResource(R.drawable.pause_24px)
        player?.takeIf { !it.isPlaying }?.play()
    }

    /**
     * 暂停
     */
    private fun pause() {
        provider.player.setPlaybackState(false)
        binding.status.setIconResource(R.drawable.play_arrow_24px)
        player?.takeIf { it.isPlaying }?.pause()
    }

    /**
     * Int 扩展函数，格式化毫秒为 mm:ss
     */
    private fun Int.toTimeString(): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}