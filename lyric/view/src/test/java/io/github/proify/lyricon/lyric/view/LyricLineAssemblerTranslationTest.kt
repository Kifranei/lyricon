/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import io.github.proify.lyricon.lyric.model.RichLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricLineAssemblerTranslationTest {

    @Test
    fun `hide translation skips translation secondary line`() {
        val source = RichLyricLine(text = "otta carry my cross", translation = "我得学会自己背负十字架")
        val assembler = LyricLineAssembler(displayTranslation = false, displayRoma = false)

        val secondary = assembler.buildSecondary(source)

        assertTrue(secondary.line.text.isNullOrBlank())
        assertNull(secondary.line.metadata?.getBoolean("translation"))
        assertEquals(false, secondary.alwaysShow)
    }

    @Test
    fun `show translation keeps translation secondary line`() {
        val source = RichLyricLine(text = "otta carry my cross", translation = "我得学会自己背负十字架")
        val assembler = LyricLineAssembler(displayTranslation = true, displayRoma = false)

        val secondary = assembler.buildSecondary(source)

        assertEquals("我得学会自己背负十字架", secondary.line.text)
        assertEquals(true, secondary.line.metadata?.getBoolean("translation"))
        assertEquals(true, secondary.alwaysShow)
    }

    @Test
    fun `hide translation still allows true secondary harmony`() {
        val source = RichLyricLine(
            text = "main",
            secondary = "harmony",
            translation = "译文字幕",
        )
        val assembler = LyricLineAssembler(displayTranslation = false, displayRoma = false)

        val secondary = assembler.buildSecondary(source)

        assertEquals("harmony", secondary.line.text)
    }

    @Test
    fun `updateFlags can turn translation off after construction`() {
        val source = RichLyricLine(text = "hello", translation = "你好")
        val assembler = LyricLineAssembler(displayTranslation = true, displayRoma = false)

        assertEquals("你好", assembler.buildSecondary(source).line.text)

        assembler.updateFlags(
            displayTranslation = false,
            displayRoma = false,
            enableRelativeProgress = false,
            enableRelativeHighlight = false,
        )

        assertTrue(assembler.buildSecondary(source).line.text.isNullOrBlank())
    }
}
