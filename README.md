# RollingCoin

Android coin physics simulation — tilt your phone to roll Euro coins around the screen.

## Testapp: Betrag und Stückelungen ändern (ohne Rebuild)

Die Testapp liest ihre Konfiguration beim Start von:

```
/sdcard/Android/data/com.example.rollingcoin/files/testapp.json
```

Beim ersten Start wird dort automatisch eine Standarddatei erstellt und der Pfad in Logcat
geloggt (Tag `CoinRainTestApp`).

**Datei per ADB übertragen:**
```bash
adb push testapp.json /sdcard/Android/data/com.example.rollingcoin/files/testapp.json
```

Dann App neu starten — kein Rebuild nötig.

**Format:**
```json
{
  "amount": "2.43",
  "allowedCoins": ["EURO_1", "CENT_50", "CENT_20", "CENT_10", "CENT_5", "CENT_1"]
}
```

- `amount`: Betrag in Euro mit bis zu 2 Dezimalstellen (`"2.43"` = 243 Cent).
- `allowedCoins`: erlaubte Stückelungen. Mögliche Werte:
  `CENT_1`, `CENT_2`, `CENT_5`, `CENT_10`, `CENT_20`, `CENT_50`, `EURO_1`, `EURO_2`.
  Wird das Feld weggelassen, sind alle Stückelungen erlaubt.

**1-Cent-Garantie:** Jeder ganzzahlige Centbetrag ist immer darstellbar — der Algorithmus
ergänzt automatisch 1-Cent-Münzen für den Rest, den die gewählten Stückelungen nicht exakt
abdecken.

---

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
| `haptics.mode` | `"auto"` | `"auto"` / `"on"` / `"off"` — see below |
| `haptics.scaleWithImpact` | `true` | Scale amplitude with velocity (requires amplitude control) |
| `haptics.modelBlocklist` | `["IV2201"]` | `Build.MODEL`/`DEVICE` substrings that force haptics off |
| `haptics.modelAllowlist` | `["SM-G960","SM-G965"]` | Substrings that force haptics on |
| `rendering.renderer` | `"png"` | `"png"` uses PNG assets; `"procedural"` uses the built-in vector renderer |

## Coin PNG Assets

Coin images live in `coinrain/src/main/res/drawable-nodpi/`. The required filenames are:

| File | Denomination |
|---|---|
| `coin_1c.png` | 1 Cent |
| `coin_2c.png` | 2 Cent |
| `coin_5c.png` | 5 Cent |
| `coin_10c.png` | 10 Cent |
| `coin_20c.png` | 20 Cent |
| `coin_50c.png` | 50 Cent |
| `coin_1e.png` | 1 Euro |
| `coin_2e.png` | 2 Euro |

To replace the shipped assets with different artwork: prepare 8 square PNGs (recommended ≥ 512 × 512 px,
transparent background, circular coin face), name them as above, drop into `drawable-nodpi/`, and rebuild.
No code change needed.

**Why `drawable-nodpi`?** Android must NOT auto-scale these bitmaps — the app calculates the exact
physical size (mm → px via screen DPI) and scales them itself. `nodpi` disables system scaling.

**Haptics mode `"auto"`:** uses `Vibrator.hasAmplitudeControl()` as a motor-quality heuristic —
`true` → fine LRA motor assumed → haptics on; `false` → coarse ERM → off. Override for specific
devices via block/allowlist. The exact `Build.MODEL` and `Build.DEVICE` strings are logged at
startup (tag `CoinRainHaptics`) to help maintain these lists.

## Module Structure

```
:core      — Pure Kotlin physics engine. No Android imports.
:coinrain  — Android library: SurfaceView, sensor, SoundPool.
:app       — Demo activity.
:soundgen  — Web app for designing the coin impact WAV.
```

## Credits

### Coin Images

The 8 Euro coin value-side PNGs (`coin_1c.png` … `coin_2e.png`) are sourced from Wikimedia Commons
and released under the **Creative Commons CC0 1.0 Universal (Public Domain Dedication)** license —
no attribution required, but credited here for reference.

| File | Source | License |
|---|---|---|
| `coin_1c.png` – `coin_2e.png` | [Wikimedia Commons — Euro coins](https://commons.wikimedia.org/wiki/Category:Euro_coins) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |

Retrieved: 2026-07-28.
