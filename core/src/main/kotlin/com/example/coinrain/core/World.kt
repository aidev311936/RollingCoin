package com.example.coinrain.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.sqrt

class World(
    @Volatile var widthPx: Float,
    @Volatile var heightPx: Float
) {
    private val lock = ReentrantLock()
    private val _coins = mutableListOf<CoinState>()
    val coins: List<CoinState> get() = lock.withLock { _coins.toList() }

    private var accumulator: Float = 0f
    private val dt: Float = 1f / CoinRainConfig.Physics.SIM_HZ

    @Volatile
    var gravity: Vec2 = Vec2.ZERO
        set(value) {
            val delta = (value - field).length()
            field = value
            if (delta > CoinRainConfig.Physics.WAKE_SHAKE_THRESHOLD) wakeAll()
        }

    // Shake impulse delivered by the sensor thread; consumed at the start of stepFixed()
    @Volatile private var pendingImpulse: Vec2 = Vec2.ZERO

    fun addCoin(coin: CoinState) = lock.withLock { _coins.add(coin) }

    fun removeCoin(id: Int) = lock.withLock { _coins.removeAll { it.id == id } }

    fun clearCoins() = lock.withLock { _coins.clear() }

    fun wake(id: Int) = lock.withLock { _coins.find { it.id == id }?.also {
        it.sleeping = false; it.sleepFrames = 0
    } }

    fun wakeAll() = lock.withLock { _coins.forEach { it.sleeping = false; it.sleepFrames = 0 } }

    fun applyShakeImpulse(impulse: Vec2) {
        pendingImpulse = impulse
        wakeAll()
    }

    // Called from the render thread with real elapsed seconds.
    // Returns collision events for the Android layer to dispatch as sound/haptics.
    fun step(deltaSeconds: Float): List<CollisionEvent> {
        accumulator += deltaSeconds.coerceAtMost(0.25f)
        val events = mutableListOf<CollisionEvent>()
        while (accumulator >= dt) {
            events += stepFixed()
            accumulator -= dt
        }
        return events
    }

    private fun stepFixed(): List<CollisionEvent> {
        val events = mutableListOf<CollisionEvent>()
        lock.withLock {
            val impulse = pendingImpulse
            pendingImpulse = Vec2.ZERO

            // 1. Integrate forces
            for (coin in _coins) {
                if (coin.sleeping) continue
                if (impulse != Vec2.ZERO) {
                    coin.vel = coin.vel + impulse
                }
                // gravity acceleration: a = g (mass doesn't affect freefall, but
                // air drag is proportional to cross-section / mass → lighter coins fall slightly slower)
                val dragFactor = 1f - CoinRainConfig.Physics.AIR_DRAG_COEFF * coin.radiusPx * coin.radiusPx / coin.massG
                coin.vel = (coin.vel + gravity * dt) * CoinRainConfig.Physics.FRICTION * dragFactor.coerceIn(0.9f, 1f)
                coin.pos = coin.pos + coin.vel * dt
                // rolling rotation: dθ = vx * dt / r  (half-speed factor avoids spinner look)
                coin.angle += coin.vel.x * dt / coin.radiusPx * 0.5f
            }

            // 2. Resolve wall collisions (sleeping coins too — position correction can push them OOB)
            for (coin in _coins) {
                events += resolveWalls(coin)
            }

            // 3. Resolve coin-coin overlaps (position only, multiple iterations)
            val grid = UniformGrid(CoinRainConfig.COINS.maxOf { it.diameterMm } * 10f)
            for (coin in _coins) grid.insert(coin)

            repeat(CoinRainConfig.Physics.POSITION_SOLVER_ITERATIONS) {
                for ((a, b) in grid.candidatePairs()) {
                    resolveCoinPair(a, b)?.let { events += it }
                }
            }

            // 4. Update sleep state
            for (coin in _coins) {
                updateSleep(coin)
            }
        }
        return events
    }

    private fun resolveWalls(coin: CoinState): List<CollisionEvent> {
        val events = mutableListOf<CollisionEvent>()
        val r = coin.radiusPx
        val w = widthPx
        val h = heightPx
        val slop = CoinRainConfig.Physics.RESTITUTION_SLOP_VELOCITY

        // Left wall
        if (coin.pos.x < r) {
            val speed = -coin.vel.x
            coin.pos = coin.pos.copy(x = r)
            val e = if (speed < slop) 0f else CoinRainConfig.Physics.RESTITUTION
            coin.vel = coin.vel.copy(x = speed * e)
            if (speed > 0f) events += CollisionEvent(CollisionType.WALL, speed, coin.id)
        }
        // Right wall
        if (coin.pos.x > w - r) {
            val speed = coin.vel.x
            coin.pos = coin.pos.copy(x = w - r)
            val e = if (speed < slop) 0f else CoinRainConfig.Physics.RESTITUTION
            coin.vel = coin.vel.copy(x = -speed * e)
            if (speed > 0f) events += CollisionEvent(CollisionType.WALL, speed, coin.id)
        }
        // Top wall
        if (coin.pos.y < r) {
            val speed = -coin.vel.y
            coin.pos = coin.pos.copy(y = r)
            val e = if (speed < slop) 0f else CoinRainConfig.Physics.RESTITUTION
            coin.vel = coin.vel.copy(y = speed * e)
            if (speed > 0f) events += CollisionEvent(CollisionType.WALL, speed, coin.id)
        }
        // Bottom wall
        if (coin.pos.y > h - r) {
            val speed = coin.vel.y
            coin.pos = coin.pos.copy(y = h - r)
            val e = if (speed < slop) 0f else CoinRainConfig.Physics.RESTITUTION
            coin.vel = coin.vel.copy(y = -speed * e)
            if (speed > 0f) events += CollisionEvent(CollisionType.WALL, speed, coin.id)
        }
        return events
    }

    private fun resolveCoinPair(a: CoinState, b: CoinState): CollisionEvent? {
        val delta = b.pos - a.pos
        val minDist = a.radiusPx + b.radiusPx
        val distSq = delta.lengthSq()
        if (distSq >= minDist * minDist) return null

        val dist = sqrt(distSq)
        val normal = if (dist < 1e-4f) Vec2(1f, 0f) else delta * (1f / dist)
        val overlap = minDist - dist

        val totalMass = a.massG + b.massG
        val correction = overlap + CoinRainConfig.Physics.POSITION_CORRECTION_SLOP

        a.pos = a.pos - normal * (correction * b.massG / totalMass)
        b.pos = b.pos + normal * (correction * a.massG / totalMass)

        val relVel = b.vel - a.vel
        val relVelNormal = relVel.dot(normal)
        val impactSpeed = abs(relVelNormal)

        // Zero relative normal velocity (e=0 contact constraint).
        // Without this, a coin resting on another accumulates gravity velocity unchecked
        // because position correction adjusts position but not velocity.
        if (relVelNormal < 0f) {
            when {
                a.sleeping && !b.sleeping ->
                    b.vel = b.vel - normal * relVelNormal
                b.sleeping && !a.sleeping ->
                    a.vel = a.vel + normal * relVelNormal
                !a.sleeping && !b.sleeping -> {
                    val j = -relVelNormal / (1f / a.massG + 1f / b.massG)
                    a.vel = a.vel - normal * (j / a.massG)
                    b.vel = b.vel + normal * (j / b.massG)
                }
            }
        }

        // Wake sleeping coin only when struck with enough speed.
        val wakeThreshold = CoinRainConfig.Physics.SLEEP_VELOCITY_THRESHOLD
        if (a.sleeping && !b.sleeping && impactSpeed > wakeThreshold) { a.sleeping = false; a.sleepFrames = 0 }
        if (b.sleeping && !a.sleeping && impactSpeed > wakeThreshold) { b.sleeping = false; b.sleepFrames = 0 }

        return if (impactSpeed > 1f) CollisionEvent(CollisionType.COIN_COIN, impactSpeed, a.id, b.id) else null
    }

    private fun updateSleep(coin: CoinState) {
        if (coin.sleeping) return
        // Sleep when velocity is low enough — the wall/floor restitution slop ensures
        // resting coins converge to near-zero velocity regardless of gravity magnitude.
        if (coin.vel.length() < CoinRainConfig.Physics.SLEEP_VELOCITY_THRESHOLD) {
            coin.sleepFrames++
            if (coin.sleepFrames >= CoinRainConfig.Physics.SLEEP_FRAMES) {
                coin.sleeping = true
                coin.vel = Vec2.ZERO
            }
        } else {
            coin.sleepFrames = 0
        }
    }
}
