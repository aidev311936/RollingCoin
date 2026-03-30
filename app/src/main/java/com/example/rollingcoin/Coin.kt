package com.example.rollingcoin

class Coin(
    val id: Int,
    val type: CoinType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 50f,
    val mass: Float = type.weightG,
    val rotation: Float = (Math.random() * 360).toFloat()
) {
    var wasHitLeft = false
    var wasHitRight = false
    var wasHitTop = false
    var wasHitBottom = false
    val collidingWith = mutableSetOf<Int>()
}
