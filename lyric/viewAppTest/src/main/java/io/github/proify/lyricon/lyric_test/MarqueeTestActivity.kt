/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric_test

import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.view.DefaultMarqueeConfig
import io.github.proify.lyricon.lyric.view.DefaultSyllableConfig
import io.github.proify.lyricon.lyric.view.LyricLineConfig
import io.github.proify.lyricon.lyric.view.MainTextConfig
import io.github.proify.lyricon.lyric_test.databinding.MarqueeBinding

class MarqueeTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = MarqueeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.line.setStyle(
            LyricLineConfig(
                text = MainTextConfig(),
                marquee = DefaultMarqueeConfig(),
                syllable = DefaultSyllableConfig(),
                gradientProgressStyle = true
            ).apply {
                text.apply {
                    textSize = 28f.sp
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                marquee.apply {
                    ghostSpacing = 0.dp.toFloat()
                    scrollSpeed = 80f
                    loopDelay = 500
                    repeatCount = 9999
                    stopAtEnd = false
                }
            })

        b.line.setLyric(LyricLine(text = "哈基米叮咚鸡胖宝宝踩踩背搞核酸"))
        b.line.startMarquee()
    }
}