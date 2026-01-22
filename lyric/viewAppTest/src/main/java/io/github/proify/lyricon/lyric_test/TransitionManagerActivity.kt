/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric_test

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.Scene
import android.transition.TransitionManager
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class TransitionManagerActivity : AppCompatActivity() {

    private lateinit var rootLayout: ViewGroup
    private lateinit var scene1: Scene
    private lateinit var scene2: Scene

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transition_manager)

        rootLayout = findViewById(R.id.root_layout)

        // 创建 Scene
        scene1 = Scene.getSceneForLayout(rootLayout, R.layout.activity_main, this)
        scene2 = Scene.getSceneForLayout(rootLayout, R.layout.scene2, this)

        // Scene1 按钮点击事件
        rootLayout.findViewById<Button>(R.id.button_switch).setOnClickListener {
            val transition = AutoTransition().apply { duration = 500 }
            TransitionManager.go(scene2, transition)

            // 延迟查找 scene2 内按钮
            rootLayout.post {
                val buttonBack = rootLayout.findViewById<Button>(R.id.button_back)
                buttonBack?.setOnClickListener {
                    val backTransition = AutoTransition().apply { duration = 500 }
                    TransitionManager.go(scene1, backTransition)
                }
            }
        }

        // 示例：在同一布局内直接过渡某个 View（可选）
        // val myView = findViewById<android.view.View>(R.id.text1)
        // rootLayout.postDelayed({
        //     TransitionManager.beginDelayedTransition(rootLayout)
        //     myView.visibility = android.view.View.GONE
        // }, 2000)
    }
}
