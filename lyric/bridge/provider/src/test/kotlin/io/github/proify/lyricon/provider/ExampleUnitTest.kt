/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider

import android.content.ContextWrapper
import org.junit.Assert
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {


    @Test
    fun addition_isCorrect() {
        Assert.assertEquals(4, 2 + 2)
    }

    @Test
    fun example() {
        val context = ContextWrapper(null)

//        //创建一个提供者实例，建议配置规范图标
//        val provider = LyriconProvider(
//            context,
//            //logo = ProviderLogo.fromDrawable(context, R.drawable.logo)
//            centralPackageNames = listOf(ProviderConstants.SYSTEM_UI_PACKAGE_NAME)
//        )

//        //添加连接状态监听器
//        provider.service.addConnectionListener {
//            onConnected {}
//            onReconnected {}
//            onDisconnected {}
//            onConnectTimeout {}
//        }
//
//        //注册提供者
//        provider.register()
//
//        val player = provider.player
//        //不需要判断激活状态，内部缓存自动同步
//
//        //设置播放状态
//        player.setPlaybackState(true)
//
//        //发送一个纯文本
//        player.sendText("我无法只是普通朋友")
//
//        //高级用法
//
//        //设置占位
//        player.setSong(Song(name = "普通朋友", artist = "陶喆"))
//
//        //只带有开始和结束时间的歌词
//        player.setSong(
//            Song(
//                id = "歌曲唯一标识",
//                name = "普通朋友",
//                artist = "陶喆",
//                duration = 1000,
//                lyrics = listOf(
//                    RichLyricLine(
//                        end = 1000,
//                        text = "我无法只是普通朋友",
//                    ),
//                    RichLyricLine(
//                        begin = 1000,
//                        end = 2000,
//                        text = "不想做普通朋友",
//                    ),
//                )
//            )
//        )
//
//        //带有单词，次要歌词，翻译的歌词
//        player.setSong(
//            Song(
//                id = "歌曲唯一标识",
//                name = "普通朋友",
//                artist = "陶喆",
//                duration = 1000,
//                lyrics = listOf(
//                    RichLyricLine(
//                        end = 1000,
//                        text = "我无法只是普通朋友",
//                        words = listOf(
//                            LyricWord(text = "我", end = 200),
//                            LyricWord(text = "无法", begin = 200, end = 400),
//                            LyricWord(text = "只是", begin = 400, end = 600),
//                            LyricWord(text = "普通", begin = 600, end = 800),
//                            LyricWord(text = "朋友", begin = 800, end = 1000),
//                        ),
//                        secondary = "（不想做普通朋友）",
//                        translation = "I can't just be a normal friend",
//                    )
//                )
//            )
//        )
//
//        //控制显示翻译
//        player.setDisplayTranslation(true)

    }
}