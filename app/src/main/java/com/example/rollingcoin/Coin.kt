package com.example.rollingcoin

class Coin(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
) {
    var wasHitLeft = false
    var wasHitRight = false
    var wasHitTop = false
    var wasHitBottom = false
    val collidingWith = mutableSetOf<Int>()
}
