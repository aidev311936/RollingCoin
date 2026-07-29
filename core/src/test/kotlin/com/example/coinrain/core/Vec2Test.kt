package com.example.coinrain.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class Vec2Test {

    @Test fun `addition`() {
        assertEquals(Vec2(3f, 5f), Vec2(1f, 2f) + Vec2(2f, 3f))
    }

    @Test fun `subtraction`() {
        assertEquals(Vec2(-1f, -1f), Vec2(1f, 2f) - Vec2(2f, 3f))
    }

    @Test fun `scalar multiplication`() {
        assertEquals(Vec2(2f, 4f), Vec2(1f, 2f) * 2f)
    }

    @Test fun `unary minus`() {
        assertEquals(Vec2(-1f, -2f), -Vec2(1f, 2f))
    }

    @Test fun `dot product`() {
        assertEquals(11f, Vec2(1f, 2f).dot(Vec2(3f, 4f)), 1e-6f)
    }

    @Test fun `length`() {
        assertEquals(5f, Vec2(3f, 4f).length(), 1e-5f)
    }

    @Test fun `normalized returns unit vector`() {
        val n = Vec2(3f, 4f).normalized()
        assertEquals(1f, n.length(), 1e-5f)
        assertEquals(0.6f, n.x, 1e-5f)
        assertEquals(0.8f, n.y, 1e-5f)
    }

    @Test fun `normalized of zero vector returns ZERO`() {
        assertEquals(Vec2.ZERO, Vec2(0f, 0f).normalized())
    }

    @Test fun `distanceTo`() {
        assertEquals(sqrt(2f), Vec2(0f, 0f).distanceTo(Vec2(1f, 1f)), 1e-5f)
    }
}
