# Tasks: Fix Endlos-Scrape-Schleife

Basierend auf [PLAN.md](PLAN.md) — zwei chirurgische Eingriffe in `SimulationView.kt`.

## Tasks

- [x] Fix-Branch erstellen (`fix/wall-rest-scrape-suppression`)
- [x] **Fix 1**: Wall-Rest-State in `applyPhysics()` — Velocity in Wandrichtung auf 0 setzen wenn Münze an Wand liegt UND Gravity in Wand drückt
- [x] **Fix 2**: Scrape-Unterdrückung bei großer Überlappung in `resolveCoinCollisions()` — Scrape nur auslösen wenn `overlap < minRadius * 0.15`
- [x] **Fix 3**: Gravity-Cancel-Zone von `1.5f` auf `WALL_HAPTIC_BUFFER` (6px) erweitern — 1.5px Zone zu eng, Coin verlässt sie in einem Frame nach dem Bounce und wird sofort von Gravity re-beschleunigt
- [x] **Fix 4**: Outgoing Bounce-Velocity nullen in `resolveWallCollision()` wenn Gravity die Münze gegen diese Wand drückt — Coin-Coin-Positionskorrektur schiebt Wandmünze in die Wand, Bounce gibt ihr vx=1.5, diese treibt Coin-Coin-Kollision an
- [x] **Fix 5**: Coin-Coin-Ruhezustand in `resolveCoinCollisions()` — Impulse und Sounds überspringen wenn beide Münzen Speed < COIN_REST_SPEED haben; Positionskorrektur bleibt aktiv
- [x] **Fix 6**: Kontakt-Hysterese in `resolveCoinCollisions()` — Wenn Paar letzten Frame in Kontakt war, `collidingWith` auch dann befüllen wenn (a) Korrektur minimalen Gap erzeugt hat oder (b) Münzen noch überlappen aber divergieren; verhindert Erstkontakt-Retrigger durch Positionskorrektur
