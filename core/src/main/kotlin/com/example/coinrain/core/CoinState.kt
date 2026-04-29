package com.example.coinrain.core

class CoinState(
    val id: Int,
    val specId: String,
    val radiusPx: Float,
    val massG: Float,
    var pos: Vec2,
    var vel: Vec2 = Vec2.ZERO,
    var angle: Float = 0f,
    var sleepFrames: Int = 0,
    var sleeping: Boolean = false
)
