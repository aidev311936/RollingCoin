package com.example.coinrain.core

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {

    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun unaryMinus() = Vec2(-x, -y)

    fun dot(other: Vec2): Float = x * other.x + y * other.y

    fun lengthSq(): Float = x * x + y * y

    fun length(): Float = sqrt(lengthSq())

    fun normalized(): Vec2 {
        val len = length()
        return if (len < 1e-6f) ZERO else Vec2(x / len, y / len)
    }

    fun distanceTo(other: Vec2): Float = (this - other).length()

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}
