/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.opencc.lite

import io.github.proify.opencc.lite.dic.STCharactersDictionary
import io.github.proify.opencc.lite.dic.STPhrasesDictionary
import io.github.proify.opencc.lite.dic.TSCharactersDictionary
import io.github.proify.opencc.lite.dic.TSPhrasesDictionary

class OpenCCLite(mode: OpenCCMode) {

    companion object {
        val S2T: OpenCCLite by lazy { OpenCCLite(OpenCCMode.S2T) }
        val T2S: OpenCCLite by lazy { OpenCCLite(OpenCCMode.T2S) }
    }

    private val trie = Trie()

    init {
        when (mode) {
            OpenCCMode.S2T -> loadDictionaries(
                STPhrasesDictionary.entries,
                STCharactersDictionary.entries
            )

            OpenCCMode.T2S -> loadDictionaries(
                TSPhrasesDictionary.entries,
                TSCharactersDictionary.entries
            )
        }
    }

    fun convert(input: String): String {
        if (input.isEmpty()) {
            return input
        }

        val result = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val match = trie.longestMatch(input, index)
            if (match != null) {
                result.append(match.value)
                index += match.length
            } else {
                val codePoint = input.codePointAt(index)
                result.appendCodePoint(codePoint)
                index += Character.charCount(codePoint)
            }
        }
        return result.toString()
    }

    private fun loadDictionaries(vararg dictionaries: Map<String, String>) {
        dictionaries.forEach { dictionary ->
            dictionary.forEach { (key, value) -> trie.insert(key, value) }
        }
    }
}
