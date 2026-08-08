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
import io.github.proify.lyricon.lyric.view.Highlight
import io.github.proify.lyricon.lyric.view.Marquee
import io.github.proify.lyricon.lyric.view.TextLook
import io.github.proify.lyricon.lyric_test.databinding.MarqueeBinding

class MarqueeTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MarqueeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.line.configureWith(
            text = TextLook(
                size = 28f,
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            ),
            highlight = Highlight(),
            marquee = Marquee(
                spacing = 10f,
                speed = 120f,
                loopDelay = 0,
                repeatCount = 2,
                stopAtEnd = false
            ),
            gradient = true,
            fadingEdge = 10,
        )

        binding.line.setLyric(LyricLine(text = "哈基米叮咚鸡胖宝宝踩踩背搞核酸"))
        binding.line.post {
            binding.line.requestScroll()
        }
    }
}
