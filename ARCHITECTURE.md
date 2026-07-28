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

## Haptics (`:coinrain/CoinHapticsPlayer.kt`)

Short vibration ticks (15 ms) are triggered in sync with sound events — same velocity
threshold, same event loop. The vibration motor quality varies widely across devices,
so the player makes a gated decision at startup:

| Priority | Condition | Result |
|---|---|---|
| 1 | `haptics.mode = "off"` in config | always off |
| 2 | `haptics.mode = "on"` in config | always on (if hardware present) |
| 3 | `Build.MODEL` or `Build.DEVICE` matches `modelBlocklist` entry | off |
| 4 | matches `modelAllowlist` entry | on |
| 5 | `Vibrator.hasAmplitudeControl() = true` | on (auto heuristic) |
| 6 | `hasAmplitudeControl() = false` | off (auto heuristic) |

The `hasAmplitudeControl()` heuristic is **not** a documented motor-quality API; it is
the best proxy available without OEM-specific APIs. Fine LRA motors (Galaxy S-series)
tend to return `true`; coarse ERM motors (budget devices) tend to return `false`.

At startup, `CoinHapticsPlayer` logs (tag `CoinRainHaptics`) the exact `Build.MANUFACTURER`,
`Build.MODEL`, `Build.DEVICE` strings and the decision reason. Use this output to maintain
the block/allowlist without guessing.

## Runtime Amount API (`CoinRainView.rain()`)

The host app controls which amount to rain and which denominations to allow at **runtime**,
not at build time. This allows different users to receive different amounts without a rebuild.

```kotlin
coinRainView.rain(amountCents: Int, allowedCoins: Set<Denomination>? = null)
coinRainView.rain(amount: String,   allowedCoins: Set<Denomination>? = null)  // "2.43" → 243 cents
```

**Coin decomposition** (`:core/CoinChange.kt`): standard DP coin-change minimisation over
the allowed denominations. If the amount is not exactly representable (e.g. {50c,20c} cannot
make 243c since 243 is not divisible by 10), the largest representable amount is covered by
DP and the remainder is filled with CENT_1 coins. This guarantees every non-negative integer
cent amount is representable — no "not representable" error class exists.

**1c fallback guarantee:** CENT_1 is implicitly always available as a gap-filler, even when
the host does not include it in `allowedCoins`. The host's denomination choice governs the
bulk of the coins; 1c only appears for the unavoidable arithmetic remainder.

**Error handling at the module boundary** — `CoinRainView` never throws to the host:
- Invalid `amountCents` (< 0 or > MAX): logs ERROR, calls `onError(PromoAmountInvalid)`,
  falls back to `spawnDefaultCoins()`.
- Unparseable amount string: same path via `PromoAmountParseException` from `AmountParser`.
- Host wires `coinRainView.onError = { error -> … }` to observe errors without crashing.

**`fallbackStartCoins` / `fallbackAllowedCoins`** in `coinrain.json` are used when the host
calls `spawnDefaultCoins()` (XML-only use, no `rain()` call) or passes `allowedCoins = null`.

## Coin Renderer (`:coinrain/CoinRenderer.kt`)

`CoinRenderer` is a three-method interface (`onSizeChanged`, `draw`, `release`). Two implementations:

| Class | When used | Notes |
|---|---|---|
| `PngCoinRenderer` | `rendering.renderer = "png"` (default) | Loads drawable-nodpi PNGs, scales to physical size, bilinear filtered, falls back per-coin to procedural if asset missing |
| `ProceduralCoinRenderer` | `rendering.renderer = "procedural"` | Draws coins with AWT-style Canvas ops, no external assets needed |

**Physical sizing:** coin radius in pixels = `diameterMm / 2 × (xdpi / 25.4)`. Uses `DisplayMetrics.xdpi`
(physical DPI), never `densityDpi` (rounded bucket). This ensures every coin has the correct real-world
size in millimetres on every device. Locked by `CoinSizingTest`.

**PNG asset location:** `coinrain/src/main/res/drawable-nodpi/` — `nodpi` prevents Android from
auto-scaling; the renderer scales bitmaps to the exact physical target size itself.

**Placeholder generation:** `./gradlew :core:generateCoinAssets` renders placeholder PNGs using the host
JVM's AWT. Replace files with real assets (same names) and rebuild — no code change needed.

**Adding a third renderer** (e.g. SVG): implement `CoinRenderer`, wire it in `CoinRainView` under a new
`rendering.renderer` value. No interface changes required.

## Sound Design Tool (`:soundgen`)

A plain HTML/JS web app — no build pipeline, no dependencies. Opens directly in
the browser or via `./gradlew :soundgen:run`. Exports `coin_impact.wav` which
the developer manually copies to `coinrain/src/main/res/raw/`.
