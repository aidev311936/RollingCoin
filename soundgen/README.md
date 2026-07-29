# soundgen — Coin Impact Sound Generator

Interactive web app for designing the coin impact sound used in the Coin Rain app.

## Usage

**Option A — Gradle task (recommended):**
```
./gradlew :soundgen:run
```
Then open http://localhost:8765 in your browser. Press Ctrl+C to stop.

**Option B — direct file open:**
Open `webapp/index.html` by double-clicking it. Works in all modern browsers.

## Workflow

1. Start the app and tune the sliders until the sound is right.
2. Click **Download coin_impact.wav**.
3. Copy the downloaded file to:
   ```
   coinrain/src/main/res/raw/coin_impact.wav
   ```
   (overwrite the placeholder)
4. Rebuild the app: `./gradlew :app:assembleDebug`

## Saving your settings

Use **Export Settings (JSON)** to copy the current parameters to the clipboard,
then paste them somewhere safe. Use **Import** to restore them later.
