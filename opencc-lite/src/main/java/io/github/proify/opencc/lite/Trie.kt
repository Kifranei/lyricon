/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.opencc.lite

internal class Trie {
    private val root = TrieNode()

    fun insert(key: String, value: String) {
        require(key.isNotEmpty()) { "Dictionary key must not be empty." }

        var node = root
        var index = 0
        while (index < key.length) {
            val codePoint = key.codePointAt(index)
            node = node.children.getOrPut(codePoint) { TrieNode() }
            index += Character.charCount(codePoint)
        }
        node.value = value
    }

    fun longestMatch(text: String, start: Int): Match? {
        var node = root
        var index = start
        var lastValue: String? = null
        var lastLength = 0

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            node = node.children[codePoint] ?: break
            index += Character.charCount(codePoint)

            node.value?.let {
                lastValue = it
                lastLength = index - start
            }
        }

        return lastValue?.let { Match(it, lastLength) }
    }
}
