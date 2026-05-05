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

1. **No coin–coin impulse transfer.** Coin–coin contacts do position correction
   only (overlap resolution). Velocity is zeroed for approaching pairs to prevent
   jitter from the position solver, but momentum is not transferred between coins.
   Previous attempts with impulse transfer caused lighter coins to be continuously
   pumped into walls by heavier stacked neighbours, producing audio loops.

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
