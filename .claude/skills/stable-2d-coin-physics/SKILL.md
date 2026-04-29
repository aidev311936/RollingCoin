---
name: stable-2d-coin-physics
description: How to build a lightweight 2D physics simulation for falling coins (or similar circular rigid bodies) where stacks come to rest reliably without jittering. Use this skill whenever the user is implementing falling/stacking circular bodies, coin-rain effects, marble-bin simulations, or any 2D rigid-body simulation that needs stable resting contacts on mobile (Android/Kotlin or portable Kotlin code). Triggers on mentions of coin physics, falling coins, 2D collision, stacking jitter, resting contacts, sleep state, or any custom physics engine where bodies must come to rest at the bottom of a container. Especially relevant when the previous attempt had jittering stacks, bodies bouncing forever, or stacks that never settle — this skill encodes the specific fixes for those failure modes.
---

# Stable 2D Coin Physics (Lightweight Custom Engine)

This skill captures hard-won lessons for building a small, deterministic 2D rigid-body
simulator for circular bodies (coins, marbles, pucks) — specifically tuned for the case
where many bodies stack up at the bottom of a container and **must come to rest reliably**.

It's designed for cases where pulling in a full physics library (Box2D, Chipmunk, LibGDX
physics) is overkill or undesirable — for example, a small Android module that needs to
stay portable to Kotlin Multiplatform / iOS without a native dependency.

---

## When to use this skill

Use this skill if the task involves any of the following:

- A 2D simulation of **circular bodies falling under gravity** in a bounded area.
- Bodies that need to **stack and come to rest** (not just fall and disappear).
- A previous attempt had bodies **jittering, hopping forever at the floor, or never
  settling** — this is the canonical failure mode this skill addresses.
- A requirement to keep the engine **lightweight, dependency-free, and portable**
  (no JBox2D, no LibGDX, no Rapier).
- The simulation needs to be **deterministic** (reproducible from a seed) for unit
  testing.

Do **not** use this skill if a full-featured physics engine is acceptable — in that
case Box2D-equivalents handle the same problems with more generality and you don't
need to roll your own.

---

## Core principles (these are the actual fixes)

The reason naive 2D-physics implementations fail at stacking is almost always the same
small set of mistakes. The four principles below, applied together, are the difference
between a stack that settles in two seconds and a stack that vibrates forever.

### 1. Fixed timestep with accumulator

**Never** feed `dt = lastFrameDuration` directly into the physics step. With variable
timesteps, a single lag spike (GC pause, scheduler hiccup) produces a giant `dt` that
pushes bodies through walls and explodes the simulation.

Instead: run the simulation at a **fixed rate** (60 Hz is plenty for coins) and use
an accumulator pattern:

```kotlin
private var accumulator = 0.0
private val fixedDt = 1.0 / 60.0

fun update(realFrameDt: Double) {
    accumulator += realFrameDt.coerceAtMost(0.25)  // clamp to avoid spiral of death
    while (accumulator >= fixedDt) {
        physicsStep(fixedDt)
        accumulator -= fixedDt
    }
    // optionally: interpolate render state using accumulator / fixedDt
}
```

The clamp on `realFrameDt` prevents the "spiral of death" if the app is paused or
backgrounded. The fixed `dt` makes the simulation reproducible from a seed —
essential for the regression test in step 5.

### 2. Restitution slop (the single most important fix for jittering stacks)

A coin falling onto the floor with restitution `e = 0.3` will bounce. After a few
bounces its velocity is small. With floating-point math, a body with downward velocity
of `0.001 px/s` and `e = 0.3` re-launches at `0.0003 px/s` upward — and gravity
immediately pulls it back. This produces an infinite micro-bounce that looks like
jittering.

**Fix:** below a velocity threshold, treat the collision as fully inelastic.

```kotlin
fun resolveWallCollision(coin: Coin, normal: Vec2, restitution: Double) {
    val vn = coin.velocity.dot(normal)
    if (vn >= 0) return  // separating, ignore

    val effectiveRestitution = if (abs(vn) < RESTITUTION_SLOP) 0.0 else restitution
    val j = -(1 + effectiveRestitution) * vn
    coin.velocity += normal * j
}
```

`RESTITUTION_SLOP` of around 50 px/s works well for typical phone-screen scales.
Tune in config.

### 3. Coin-vs-coin: position correction only, no impulse transfer

For a marketing-gag-style coin rain (which is what this skill was extracted from),
**you do not need full impulse-based collision response between coins**. Trying to
implement that without a proper sequential-impulse solver is the #1 cause of unstable
stacks: each pairwise impulse pushes the next pair into penetration, which pushes the
next, and the stack never converges.

The **correct simplification** is: when two coins overlap, just push them apart
proportional to inverse mass. Do not change velocities.

```kotlin
fun resolveCoinCoin(a: Coin, b: Coin) {
    val delta = b.position - a.position
    val dist = delta.length()
    val penetration = (a.radius + b.radius) - dist
    if (penetration <= 0) return

    val n = if (dist > 1e-6) delta / dist else Vec2(1.0, 0.0)
    val totalInvMass = a.invMass + b.invMass
    if (totalInvMass <= 0) return  // both static

    val correction = n * (penetration / totalInvMass)
    a.position -= correction * a.invMass
    b.position += correction * b.invMass
    // NOTE: deliberately no velocity change. Stacks settle because each coin
    // independently loses energy to gravity + wall friction, not because energy
    // propagates through the stack.
}
```

This is a **deliberate physical simplification**. It looks indistinguishable from
"real" stacking when the user is just watching coins pile up. Document it loudly
(`ARCHITECTURE.md`) so a future contributor doesn't "fix" it.

### 4. Sleep state per body

Even with the above fixes, floating-point accumulation will cause resting bodies to
drift by tiny amounts. Once a body's velocity *and* recent acceleration are both
under thresholds for N consecutive frames, mark it as **sleeping** and skip its
update entirely.

```kotlin
data class Coin(
    var position: Vec2,
    var velocity: Vec2,
    val radius: Double,
    val invMass: Double,
    var sleepFrames: Int = 0,
    var awake: Boolean = true,
)

const val V_SLEEP = 8.0          // px/s
const val A_SLEEP = 50.0         // px/s²
const val SLEEP_FRAME_THRESHOLD = 30  // ~0.5 s at 60 Hz

fun updateSleep(coin: Coin, accelerationMagnitude: Double) {
    if (coin.velocity.length() < V_SLEEP && accelerationMagnitude < A_SLEEP) {
        coin.sleepFrames++
        if (coin.sleepFrames >= SLEEP_FRAME_THRESHOLD) coin.awake = false
    } else {
        coin.sleepFrames = 0
    }
}
```

Wake conditions:
- A shake impulse from the accelerometer (apply to all sleeping bodies).
- A collision *initiated by an awake body* against a sleeping body.
- A change in gravity direction larger than a threshold (user rotated the device).

Without sleep, you'll see "vibrating" stacks in the rendered output even when the
math is technically correct, because sub-pixel position drift keeps re-triggering
collisions.

---

## Putting it together — the simulation step

```kotlin
fun physicsStep(dt: Double) {
    // 1. Integrate forces on awake bodies only
    for (coin in coins) {
        if (!coin.awake) continue
        val accel = gravity + airDrag(coin)
        coin.velocity += accel * dt
        coin.position += coin.velocity * dt
        updateSleep(coin, accel.length())
    }

    // 2. Resolve wall/floor collisions (with restitution slop)
    for (coin in coins) {
        if (!coin.awake) continue
        resolveWalls(coin)
    }

    // 3. Resolve coin-coin overlaps (position only, multiple iterations)
    repeat(POSITION_SOLVER_ITERATIONS) {
        for (pair in broadPhase.candidatePairs()) {
            if (!pair.a.awake && !pair.b.awake) continue
            resolveCoinCoin(pair.a, pair.b)
            if (!pair.a.awake) wakeUp(pair.a)  // touched by an awake body
            if (!pair.b.awake) wakeUp(pair.b)
        }
    }
}
```

`POSITION_SOLVER_ITERATIONS` of 4–8 is plenty for stacks up to ~50 deep.

For the broad phase, a uniform grid with cell size = max coin diameter is sufficient
for under ~100 bodies. Don't bother with quadtrees at this scale.

---

## The regression test (write this FIRST)

The single most valuable test, which catches every common regression of the failure
modes above:

```kotlin
@Test
fun `stack of 20 coins comes fully to rest within 5 seconds`() {
    val world = World(width = 400.0, height = 800.0, gravity = Vec2(0.0, 980.0))
    repeat(20) { i ->
        world.spawnCoin(
            position = Vec2(200.0, 100.0 - i * 30.0),  // dropped from above, staggered
            radius = 12.0,
            seed = i,
        )
    }

    val steps = (5.0 / (1.0 / 60.0)).toInt()  // 5 seconds at 60 Hz
    repeat(steps) { world.physicsStep(1.0 / 60.0) }

    val movingCoins = world.coins.filter { it.awake }
    assertEquals(emptyList(), movingCoins,
        "After 5 seconds, all coins must be sleeping. Still awake: ${movingCoins.size}")
}
```

If this test is red, **stop and fix** before adding any other features. Do not
continue to renderer, sound, or app integration. The whole point of this skill is
that this test stays green.

A second test worth writing:

```kotlin
@Test
fun `no coin penetrates another by more than 0,5 px after settling`() {
    val world = setupStackedWorld()
    repeat(600) { world.physicsStep(1.0 / 60.0) }
    for (a in world.coins) for (b in world.coins) {
        if (a === b) continue
        val overlap = (a.radius + b.radius) - (a.position - b.position).length()
        assertTrue(overlap < 0.5,
            "Penetration of $overlap px between coins exceeds tolerance")
    }
}
```

---

## Anti-patterns — what NOT to do

- **Don't transfer impulses between coins.** Pairwise impulse responses without a
  proper sequential-impulse solver propagate energy unstably through stacks. Use
  position-only correction (principle 3).
- **Don't use variable `dt` from frame timing.** Always fixed timestep.
- **Don't apply restitution unconditionally.** Always slop the low-velocity case.
- **Don't loop `while (any coin moving) resolveCollisions()`** — this is the most
  common bad fix, and it never converges with floating-point. Cap iterations and
  rely on sleep state.
- **Don't try to "improve realism" by adding rotational dynamics to the collision
  model.** Coins can rotate visually for cosmetic effect, but the collision model
  should treat them as plain circles. Rotational physics with stacking is hard and
  not worth it for a marketing app.
- **Don't add air drag heavy enough to noticeably brake horizontal motion.** Drag
  is for differentiating fall speeds of different coin sizes (small bit of
  authenticity), not for stability. Stability comes from sleep state, not drag.

---

## Tuning constants — sensible defaults

These are starting points; tune in config:

| Constant | Default | What it does |
|---|---|---|
| Gravity | 980 px/s² | Earth-like at typical phone DPI |
| Wall restitution | 0.3 | Plastic phone case feel |
| Floor restitution | 0.2 | Slightly more dampened than walls |
| Restitution slop | 50 px/s | Below this, treat as inelastic |
| Floor friction | 0.85 (multiplier per step) | Damps tangential velocity at the floor |
| `V_SLEEP` | 8 px/s | Sleep velocity threshold |
| `A_SLEEP` | 50 px/s² | Sleep acceleration threshold |
| `SLEEP_FRAME_THRESHOLD` | 30 frames | ~0.5 s before sleeping |
| Position solver iterations | 6 | Per physics step |
| Air drag coefficient | 0.0001 | Just enough to differentiate coin sizes |

---

## Sensor integration (Android-specific notes)

Although the physics core itself is Android-free, integration with phone sensors is
where the "feels alive in the case" effect comes from. Two principles:

- **Use `Sensor.TYPE_GRAVITY` for gravity direction.** It's already filtered by the
  OS and far less noisy than raw accelerometer. Apply additional exponential smoothing
  (α ≈ 0.1) on top.
- **Use `Sensor.TYPE_LINEAR_ACCELERATION` for shake impulses.** Add to all awake
  bodies' velocities (and wake all sleeping ones above a magnitude threshold).
- **Rotate sensor axes by display rotation.** Otherwise gravity points sideways in
  landscape mode.

---

## Summary checklist

Before saying the simulation is done, verify:

- [ ] Fixed timestep with accumulator and `dt` clamp
- [ ] Restitution slopped to 0 below threshold (walls AND floor)
- [ ] Coin-coin: position correction only, no impulse transfer
- [ ] Sleep state with wake conditions implemented
- [ ] Regression test: 20-coin stack settles in 5 s — GREEN
- [ ] Regression test: no penetration > 0.5 px after settling — GREEN
- [ ] Constants exposed via config, not hardcoded
- [ ] `ARCHITECTURE.md` documents the deliberate simplifications so future
  contributors don't "fix" them into instability
