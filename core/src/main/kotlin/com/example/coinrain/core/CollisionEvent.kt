package com.example.coinrain.core

data class CollisionEvent(
    val type: CollisionType,
    val velocityPxPerSec: Float,
    val coinAId: Int,
    val coinBId: Int = -1
)

enum class CollisionType { WALL, COIN_COIN }
