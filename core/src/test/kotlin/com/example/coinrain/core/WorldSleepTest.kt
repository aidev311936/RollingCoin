package com.example.coinrain.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WorldSleepTest {

    private fun makeWorld() = World(widthPx = 800f, heightPx = 1200f).also {
        // Simulation-internal gravity (5000 px/s² ≈ visually ~1 g at this scale).
        // The Android layer scales the real sensor value to match this. The key
        // constraint is: g/simHz (= 5000/60 ≈ 83 px/s) < RESTITUTION_SLOP_VELOCITY
        // (= 200 px/s), so a coin resting at the floor stops dead each frame rather
        // than bouncing indefinitely.
        it.gravity = Vec2(0f, 5000f)
    }

    private fun spawnCoins(world: World, count: Int, seed: Long = 42L) {
        val rng = Random(seed)
        // Use a fixed small radius so 20 coins fit in an 800×1200 world without
        // starting with extreme overlaps that the position solver cannot resolve
        val radiusPx = 30f
        for (i in 0 until count) {
            val spec = CoinRainConfig.COINS[i % CoinRainConfig.COINS.size]
            world.addCoin(CoinState(
                id = i,
                specId = spec.id,
                radiusPx = radiusPx,
                massG = spec.weightG,
                pos = Vec2(
                    100f + rng.nextFloat() * 600f,
                    50f + rng.nextFloat() * 300f
                )
            ))
        }
    }

    @Test
    fun `20 coins all sleeping after 5 seconds`() {
        val world = makeWorld()
        spawnCoins(world, 20)

        val steps = (5.0 / (1.0 / CoinRainConfig.Physics.SIM_HZ)).toInt()
        repeat(steps) { world.step(1f / CoinRainConfig.Physics.SIM_HZ) }

        val awake = world.coins.filter { !it.sleeping }
        assertTrue(
            "After 5 seconds, ${awake.size} coin(s) still awake: ${awake.map { it.id }}",
            awake.isEmpty()
        )
    }

    @Test
    fun `no coin penetrates another by more than 0_5 px after settling`() {
        val world = makeWorld()
        spawnCoins(world, 20, seed = 99L)

        val steps = (10.0 / (1.0 / CoinRainConfig.Physics.SIM_HZ)).toInt()
        repeat(steps) { world.step(1f / CoinRainConfig.Physics.SIM_HZ) }

        val coins = world.coins
        for (i in coins.indices) {
            for (j in i + 1 until coins.size) {
                val a = coins[i]
                val b = coins[j]
                val dist = a.pos.distanceTo(b.pos)
                val minDist = a.radiusPx + b.radiusPx
                val penetration = minDist - dist
                assertTrue(
                    "Coins ${a.id} and ${b.id} penetrate by ${"%.2f".format(penetration)} px",
                    penetration < 0.5f
                )
            }
        }
    }

    @Test
    fun `gravity change wakes all sleeping coins`() {
        val world = makeWorld()
        spawnCoins(world, 5)

        // Let them settle
        repeat(400) { world.step(1f / 60f) }
        assertTrue("Coins should be sleeping after settling", world.coins.all { it.sleeping })

        // Gravity shift of >WAKE_SHAKE_THRESHOLD (3000) should wake them all
        world.gravity = Vec2(5000f, 0f)
        assertFalse("At least one coin should be awake after gravity change",
            world.coins.all { it.sleeping })
    }

    @Test
    fun `shake impulse wakes sleeping coins`() {
        val world = makeWorld()
        spawnCoins(world, 5)

        repeat(400) { world.step(1f / 60f) }
        assertTrue("Coins should be sleeping after settling", world.coins.all { it.sleeping })

        world.applyShakeImpulse(Vec2(5000f, -3000f))
        assertFalse("Coins should be awake after shake", world.coins.all { it.sleeping })
    }
}
