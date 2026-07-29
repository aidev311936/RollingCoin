package com.example.coinrain.core

import org.junit.Assert.*
import org.junit.Test

class WorldCollisionTest {

    private fun coin(id: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) = CoinState(
        id = id, specId = "CENT_20", radiusPx = 50f, massG = 5.74f,
        pos = Vec2(x, y), vel = Vec2(vx, vy)
    )

    @Test
    fun `slow coin at floor stops dead (restitution slop)`() {
        val world = World(widthPx = 400f, heightPx = 400f)
        world.gravity = Vec2(0f, 5000f)
        // Place coin exactly at floor (no free-fall needed) with a velocity below the slop threshold
        val slowVy = CoinRainConfig.Physics.RESTITUTION_SLOP_VELOCITY * 0.3f  // ~60 px/s < 200 slop
        val floorY = 400f - 50f  // heightPx - radiusPx
        world.addCoin(coin(0, 200f, floorY, vy = slowVy))

        world.step(1f / 60f)

        val c = world.coins[0]
        assertTrue("Coin should stay at or near floor", c.pos.y >= floorY - 5f)
        assertTrue("Coin should not bounce upward strongly after slop hit", c.vel.y >= -50f)
    }

    @Test
    fun `fast coin bounces off wall`() {
        val world = World(widthPx = 400f, heightPx = 400f)
        world.gravity = Vec2.ZERO
        world.addCoin(coin(0, 60f, 200f, vx = -3000f))

        world.step(1f / 60f)

        val c = world.coins[0]
        assertTrue("Coin should have bounced rightward", c.vel.x > 0f)
    }

    @Test
    fun `two overlapping coins are separated`() {
        val world = World(widthPx = 800f, heightPx = 800f)
        world.gravity = Vec2.ZERO
        world.addCoin(coin(0, 395f, 400f))
        world.addCoin(coin(1, 405f, 400f))  // overlap: 100px total diameter, only 10px apart

        world.step(1f / 60f)

        val a = world.coins[0]
        val b = world.coins[1]
        val dist = a.pos.distanceTo(b.pos)
        assertTrue("Coins should be separated after one step (dist=$dist)", dist >= 99f)
    }

    @Test
    fun `coin stays within bounds`() {
        val world = World(widthPx = 400f, heightPx = 400f)
        world.gravity = Vec2(0f, 5000f)
        world.addCoin(coin(0, 200f, 100f))

        repeat(300) { world.step(1f / 60f) }

        val c = world.coins[0]
        val r = c.radiusPx
        assertTrue("Coin x out of bounds: ${c.pos.x}", c.pos.x in r..(400f - r))
        assertTrue("Coin y out of bounds: ${c.pos.y}", c.pos.y in r..(400f - r))
    }
}
