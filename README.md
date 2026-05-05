# RollingCoin

Android coin physics simulation — tilt your phone to roll Euro coins around the screen.

## Sound Workflow

Impact sounds are authored with the `:soundgen` web app and shipped as a single WAV file.

1. Start the sound generator:
   ```
   ./gradlew :soundgen:run
   ```
   Then open http://localhost:8765 (or just double-click `soundgen/webapp/index.html`).

2. Tune the sliders until the coin impact sounds right, then click **Download coin_impact.wav**.

3. Copy the file to:
   ```
   coinrain/src/main/res/raw/coin_impact.wav
   ```

4. Rebuild the app:
   ```
   ./gradlew :app:assembleDebug
   ```

## Config Reference

Key settings in `config/coinrain.json`:

| Field | Default | Description |
|---|---|---|
| `sound.enabled` | `true` | Master switch for sound |
| `sound.masterVolume` | `0.8` | Overall playback volume (0–1) |
| `sound.minImpactVelocityForSound` | `80.0` | Min impact speed (px/s) to trigger a sound |
| `physics.restitution` | `0.45` | Wall/floor bounce coefficient |
| `physics.sleepVelocityThreshold` | `100.0` | Speed below which sleep counter increments |

## Module Structure

```
:core      — Pure Kotlin physics engine. No Android imports.
:coinrain  — Android library: SurfaceView, sensor, SoundPool.
:app       — Demo activity.
:soundgen  — Web app for designing the coin impact WAV.
```

## Credits

### Coin Images

All 8 Euro coin images are from the "Simple Worksheet Design" series on Wikimedia Commons.

| Image | Author | Source | License |
|---|---|---|---|
| 1 ct – 2 € | Various | [Wikimedia Commons](https://commons.wikimedia.org/wiki/Category:Euro_coins) | CC0 1.0 |
