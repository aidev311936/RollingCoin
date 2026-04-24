# Bug-Fix Plan: Endlos-Scrape-Schleife bei Münzen an der Wand

## Verständnis der Aufgabe

Bei hoher Neigung und mehreren Münzen an der Wand entsteht eine Dauerschleife
aus Zittern, Scrape-Geräuschen und Vibration. Die Münzen kommen nie zur Ruhe.

## Was die Logs beweisen

### Primärursache 1: Divergente Kollisionsauflösung (dominanter Faktor)

Die Positionskorrektur (`a.x -= correction * mass_fraction`) kann mit
`COLLISION_ITERATIONS=3` und 7 gestapelten Münzen nicht konvergieren.

- Median-Überlappung im Bug-Capture: **11.28 px**
- Maximale Überlappung: **80.84 px** (eine Münze vollständig in der anderen)
- 97% der Frames haben `max_overlap > 20px`
- Selbst bei symmetrisch gecancelter Gravity liegt `relSpeed` median bei 13.6
  → Die Scrape-Auslösung kommt zu 76% aus der Korrektur selbst, nicht aus echter Bewegung

Die Schleife:
```
Gravity presst Münzen zusammen
→ Große Überlappung
→ Positionskorrektur injiziert hohe relSpeed
→ relSpeed > 3.0 → Scrape-Sound
→ Münzen werden auseinandergedrückt → Gravity drückt sofort wieder zusammen
→ nächster Frame: selbes Spiel
```

### Primärursache 2: Asymmetrische Gravity-Cancel (Hypothese A bestätigt)

- 99% aller Frames haben gemischten Cancel-Zustand (eine Münze gecancelt, Nachbar nicht)
- 45% aller Coin-Paare sind asymmetrisch
- 84% dieser asymmetrischen Paare haben gleichzeitig `relSpeed > 3.0` UND `|tangentVel| > 3.0`
- Paar 3–6 triggert Scrape in **jedem Frame**, ~3× pro Frame (einmal pro Iteration)

Beide Ursachen münden in dieselbe Schleife.

## Annahmen

1. Änderungen nur in `SimulationView.kt`, keine Änderungen in `Coin.kt`
2. Keine Änderungen an der Kollisionsauflösungs-Architektur (prevContacts, Impulse etc.)
3. Das Ziel ist "keine Geräusche/Vibration wenn Münzen ruhig an der Wand liegen",
   nicht "perfekte Physiksimulation"
4. Ein neuer Branch wird für die Änderungen erstellt

## Geplante Lösung: 2 chirurgische Eingriffe

### Fix 1: Wall-Rest-State (gegen Primärursache 1)

**Was:** Eine Münze, die gleichzeitig (a) an der Wand liegt UND (b) Gravitation in
die Wand drückt, bekommt `vx = 0` (bzw. `vy = 0`) gesetzt — statt wie bisher nur
Gravity-Cancel.

**Warum besser als bisheriger Gravity-Cancel:** Velocity-Null stoppt die
Positionskorrektur-Feedbackschleife vollständig. Gravity-Cancel allein verhindert
nur neue Beschleunigung, aber nicht die bereits vorhandene `vx` aus vorherigen
Korrekturen.

**Wo:** In `applyPhysics()`, ergänzend zur bestehenden `gx/gy`-Logik:
```kotlin
// Bestehend:
val gx = if ((atLeft && gravityX < 0f) || (atRight && gravityX > 0f)) 0f else gravityX
val gy = if ((atTop  && gravityY < 0f) || (atBottom && gravityY > 0f)) 0f else gravityY

// Neu: Velocity in Wandrichtung auf 0 dämpfen (Wall-Rest)
if (atLeft  && gravityX < 0f && coin.vx < 0f) coin.vx = 0f
if (atRight && gravityX > 0f && coin.vx > 0f) coin.vx = 0f
if (atTop   && gravityY < 0f && coin.vy < 0f) coin.vy = 0f
if (atBottom && gravityY > 0f && coin.vy > 0f) coin.vy = 0f
```

**Risiko:** Könnte Klebeeffekt erzeugen wenn Münze von einer anderen an die Wand
gedrückt wird (dann gibt's keine Gravity, aber doch einen Push). Muss nach Fix 2
getestet werden.

### Fix 2: Scrape-Unterdrückung bei großer Überlappung (gegen Primärursache 1+2)

**Was:** Scrape wird nur ausgelöst wenn `overlap < r * 0.15` — d.h. nicht wenn
Münzen gerade per Positionskorrektur auseinandergedrückt werden.

**Warum:** Median-Overlap im Bug beträgt 11.28px, Münzradius ≈50px → 11.28/50 = 22%.
Bei 15% Schwelle wird Scrape bei Korrektur-induzierter Bewegung unterdrückt, bleibt
aber bei leichter Berührung mit echter Tangentialbewegung erhalten.

**Wo:** In `resolveCoinCollisions()`, im scrape-Zweig:
```kotlin
// Bisher:
if (abs(tangentVelocity) > SCRAPE_TANGENT_THRESHOLD &&
    aSpeed > SCRAPE_MIN_SPEED && bSpeed > SCRAPE_MIN_SPEED) onCollision(0f, true)

// Neu: zusätzliche Bedingung
val minRadius = minOf(a.radius, b.radius)
if (abs(tangentVelocity) > SCRAPE_TANGENT_THRESHOLD &&
    aSpeed > SCRAPE_MIN_SPEED && bSpeed > SCRAPE_MIN_SPEED &&
    overlap < minRadius * 0.15f) onCollision(0f, true)
```

**Warum `minRadius * 0.15f`:** Skaliert automatisch mit Münzgröße. Bei r=50 ergibt
das 7.5px — deutlich unter Median-Overlap im Bug (11.28px), aber groß genug um
echten Scrape bei normaler Berührung (overlap ≈ 1-3px) zu erlauben.

## Was wir NICHT ändern

- `COLLISION_ITERATIONS` nicht erhöhen (Performancekosten, falscher Ansatz)
- `BOUNCE`, `WALL_HAPTIC_BUFFER`, `MAX_WALL_BOUNCE` bleiben unverändert
- `prevContacts`-Mechanismus bleibt unverändert
- `COIN_IMPACT_THRESHOLD`, `SCRAPE_MIN_SPEED` bleiben unverändert
- Keine Revert auf c7a50f5 (die anderen Fixes aus den letzten Iterationen sind valide)

## Erwartetes Ergebnis

- Münzen an der Wand mit Neigung → keine Geräusche, kein Zittern
- Normale Würfe/Kollisionen → Sounds und Vibration wie bisher
- Kein Klebeeffekt (Fix 1 greift nur wenn Gravity aktiv in Wandrichtung drückt)

## Offene Fragen / Risiken

1. **Klebeeffekt durch Fix 1?** Wenn Münze A Münze B gegen die Wand drückt und
   Gravity nicht in Wandrichtung zeigt → Fix 1 greift nicht → kein Klebeeffekt.
   Wenn Gravity in Wandrichtung zeigt → Fix 1 greift → Münze bleibt an Wand, aber
   das ist das gewünschte Verhalten.

2. **Zu aggressives Scrape-Unterdrücken durch Fix 2?** Der Schwellwert 0.15f ist
   konservativ gewählt. Bei echter gleitender Berührung (Münze rollt an Nachbarmünze
   entlang) ist overlap typischerweise < 3px << 7.5px Schwelle.

3. **Was passiert wenn sehr viele Münzen aufeinanderstapeln?** Mit Fix 1 werden
   wandnahe Münzen eingefroren — Münzen dahinter werden weiter von Gravity
   beschleunigt und prallen gegen die eingefrorene Münze. Das erzeugt korrekte
   Impact-Events, keine endlose Schleife.

---

## Phase 2: Impact-Sound-Schleife (nach Scrape-Entfernung)

### Analyse des verbleibenden Problems

Nach Entfernung des Scrape-Sounds und Erhöhung der Schwellenwerte (WALL/COIN auf 3.5)
bleibt ein Impact-Sound in Dauerschleife bei übereinanderliegenden Münzen unter
starker Neigung.

**Warum Schwellenwert-Erhöhung nicht hilft:**
- Die Impact-Velocity in der Schleife überschreitet 3.5 weit (Münzen unter hoher
  Gravity erzeugen hohe Relativgeschwindigkeit)
- Das Problem liegt nicht im *Wert* des Impulses, sondern darin, dass jeder Frame
  als "Erstkontakt" erkannt wird

**Warum wasHitLeft-Reset-Fix nicht hilft:**
- Die verbleibende Schleife kommt NICHT von Wandkollisionen
- `wasHitLeft` schützt korrekt vor Wand-Retriggern
- Die Quelle ist: **Coin-Coin-Erstkontakt-Retrigger durch Korrekturgap**

### Root Cause: Korrekturgap verhindert persistente Kontakt-Tracking

Der `prevContacts`-Mechanismus schützt vor Retriggern — aber nur wenn das Münzpaar
am Ende des vorherigen Frames in `collidingWith` war. Das ist nicht immer der Fall:

**Szenario — Münzstapel an der Wand unter hoher Neigung:**

```
Frame N:
  Iteration 1: A und B überlappen (5px)
               → Positionskorrektur trennt sie: dist = minDist + 0.15f
               → correction = overlap + 0.15f → dist_nach = minDist + 0.15f
               → distSq >= minDist² → 'continue' (Zeile 462)
               → A-B NICHT in collidingWith

  Iterationen 2-3: Nachbarmünze C drückt B zurück zu A
               → A-B überlappen erneut
               → Korrektur & Impulse → velAlongNormal >= 0 (divergierend)
               → 'continue' bei velAlongNormal >= 0 (Zeile 516)
               → collidingWith.add() wird übersprungen
               → A-B NICHT in collidingWith am Frame-Ende

Frame N+1:
  prevContacts[A] enthält B NICHT
  Gravity drückt A in B → Überlappung
  wasCollidingLastFrame = false → ERSTKONTAKT → Sound!
  → Wiederholt sich jeden Frame
```

**Zwei Pfade die collidingWith nicht befüllen:**
1. Nach Korrektur `distSq >= minDist²` → `continue` vor `collidingWith.add`
2. Überlappend aber `velAlongNormal >= 0` → `continue` vor `collidingWith.add`

### Fix 6: Kontakt-Hysterese

**Was:** In beiden oben genannten Pfaden, wenn das Paar letzten Frame in Kontakt war,
trotzdem zu `collidingWith` hinzufügen. Kein Sound, kein Impuls — nur Kontakt-Tracking.

**Pfad 1** (minimaler Gap nach Korrektur):
```kotlin
if (distSq >= minDist * minDist) {
    // Kontakt-Hysterese: Wenn letzten Frame in Kontakt und Gap < Schwellwert,
    // Kontakt aufrechterhalten — verhindert Erstkontakt-Retrigger durch Korrekturen.
    if (prevContacts[a.id]?.contains(b.id) == true) {
        val dist = sqrt(distSq.toDouble()).toFloat()
        if (dist - minDist < CONTACT_HYSTERESIS) {
            a.collidingWith.add(b.id)
            b.collidingWith.add(a.id)
        }
    }
    continue
}
```

**Pfad 2** (überlappend aber divergierend):
```kotlin
if (velAlongNormal >= 0) {
    // Münzen überlappen noch, divergieren aber (Impuls aus vorheriger Iteration).
    // Kontakt aufrechterhalten damit nächster Frame keinen Erstkontakt sieht.
    a.collidingWith.add(b.id)
    b.collidingWith.add(a.id)
    continue
}
```

**Neuer Konstante:** `CONTACT_HYSTERESIS = 3f` (3 Pixel)

**Warum 3px:**
- Gap nach Korrektur ist immer genau 0.15px (correction = overlap + 0.15f)
- Mit Jitter max ~2px — 3px fängt alle Korrekturgaps ab
- Echter Bounce: Münzen fliegen auseinander → Gap wächst schnell über 3px → kein False-Positive

**Risiko — false positive bei echtem Bounce:**
Frame N: A trifft B, bounce. velAlongNormal > 0 → beide in collidingWith (Pfad 2).
Frame N+1: A und B fliegen auseinander. Gap >> 3px → Pfad 1 Hysterese greift nicht.
Frame N+k: A und B kommen wieder zusammen → `wasCollidingLastFrame = false` → Sound ✓
→ Kein Problem. Ein Frame "zu lang" in collidingWith schadet nicht.
