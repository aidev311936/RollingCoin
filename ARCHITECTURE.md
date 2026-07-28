# Architecture

## Module Boundaries

```
:core      — Pure Kotlin. No android.* imports. Kotlin Multiplatform ready.
:coinrain  — Android library. Thin adapter (SurfaceView, Sensor, SoundPool).
:app       — Demo activity. Depends only on :coinrain.
:soundgen  — Static web app (HTML/JS). No Android dependency.
```

The strict boundary between `:core` and `:coinrain` exists to keep the physics
engine portable (iOS, desktop) and testable on the JVM without an Android emulator.

## Physics Engine (`:core/World.kt`)

Fixed-timestep simulation at 60 Hz with an accumulator. The render thread runs
free; `world.step(deltaSeconds)` sub-steps internally.

Key design decisions (do not revert without discussion):

1. **No coin–coin impulse transfer — dissipative per-coin clamp instead.**
   Coin–coin contacts do position correction (overlap resolution), then each coin's
   own approaching velocity component along the contact normal is zeroed independently.
   No momentum passes from one coin to the other.

   This was reached after measuring three variants (V1 = 50/50 split, V2 = per-coin
   clamp, V3 = pure position only):
   - V1 and the previous mass-weighted variant both produced a stable ~200 px/s
     limit cycle in corner configurations (phone tilted 45°) that never decayed —
     even with `positionSolverIterations` as low as 3.
   - V3 (pure position, no velocity handling) diverged to >2000 px/s because gravity
     accumulates unchecked on coins that don't directly touch a wall.
   - V2 (per-coin clamp) settled all scenarios in < 2 s, zero wall events in
     steady-state, ≤ 0 px penetration even at 40 coins.

   The key property: the clamp is **strictly dissipative** — kinetic energy can only
   decrease at a coin–coin contact, never increase. This prevents energy from
   circulating in a corner pile regardless of how many contacts exist.

2. **Restitution slop.** Below `restitutionSlopVelocity` (200 px/s) wall contacts
   set velocity to zero (no bounce). This prevents micro-bouncing at rest.

3. **Per-coin sleep state.** Coins below `sleepVelocityThreshold` for
   `sleepFrames` consecutive frames are frozen. They wake on shake, coin strike,
   or gravity change above `wakeShakeThreshold`.

4. **Fixed timestep, free renderer.** Never feed `deltaTime` directly into the
   physics integrator.

## Sound (`:coinrain/CoinImpactPlayer.kt`)

A single `coin_impact.wav` resource is played via `SoundPool` on every collision
event above `minImpactVelocityForSound`. Volume scales with impact velocity.

The procedural synthesizer that existed before this architecture was removed
because runtime tuning was disproportionately complex relative to the value it
added for a marketing-gag app. A WAV file tuned once with `:soundgen` is simpler,
lighter, and easier to iterate on.

## Sound Design Tool (`:soundgen`)

A plain HTML/JS web app — no build pipeline, no dependencies. Opens directly in
the browser or via `./gradlew :soundgen:run`. Exports `coin_impact.wav` which
the developer manually copies to `coinrain/src/main/res/raw/`.
