# Diagnose: Münzen kommen in der Ecke nicht zur Ruhe

## Messdaten (PhysicsDiagnosticsTest, Seed 42, 10 Münzen, World 400×700)

### Szenario A: Senkrechte Schwerkraft `(0, 5000)`
| Kennzahl | Wert |
|---|---|
| Ruheframe | 101 (1,68 s) |
| Endstatus | alle 10 Münzen schlafen |
| Wandereignisse >150 px/s, letzte 60 Frames | **0** |
| Verdict | **MONOTON ZUR RUHE** ✅ |

### Szenario B: Eckengravitation 45° `(3535, 3535)`
| Kennzahl | Wert |
|---|---|
| Ruheframe | **NIE** |
| Endstatus | alle 10 Münzen dauerhaft wach |
| Max-Geschwindigkeit Steady-State | **175–230 px/s** |
| Wandereignisse >150 px/s, letzte 60 Frames | **139 (≈ 2–5 pro Frame)** |
| Verdict | **STABILER GRENZZYKLUS** ❌ |

Das System divergiert nicht — es findet einen Fixpunkt bei ~200 px/s und verlässt ihn nicht.

---

## Antworten auf die 5 Analysefragen

### 1. Timestep
Korrekt implementiert: fixed 60 Hz mit `coerceAtMost(0.25f)` Clamp. **Kein Beitrag zum Bug.**

### 2. Schrittfolge `integrate → walls → coin-coin × 6 → sleep`
**Strukturproblem:** Wandconstraint (Schritt 2) zeroing-t wandgerichtete Geschwindigkeit mit `e=0`. Danach injiziert der Münze-Münze-Constraint (Schritt 3, 6×) neue Geschwindigkeit in beliebige Richtungen — auch zurück zur Wand. Wände laufen kein zweites Mal. Solange der Constraint pro Frame mehr Geschwindigkeit einspritzt als er ableitet, bleibt das System im Zyklus.

### 3. Schlaf-Erkennung
Schwellwert `SLEEP_VELOCITY_THRESHOLD = 100 px/s`. Steady-State im Eckszenario: **175–230 px/s**. Schlaf triggert nie — korrekt, die Münzen sind wirklich in Bewegung. Falsche Schlaf-Parameter sind **nicht** das Problem; das Schlaf-System funktioniert, aber die Geschwindigkeit wird nie niedrig genug.

### 4. Restitutions-Slop `200 px/s → e=0`
Konzept korrekt. Im Eckszenario liegt die Wandgeschwindigkeit bei 150–230 px/s:
- 150–200 px/s: `e=0` (kein Bounce) ✅, aber **Schallschwelle 150 px/s überschritten** → Sound ausgelöst ❌
- >200 px/s: `e=0.45` (Bounce), beiträgt ebenfalls Energie

Der Slop bremst Bouncing, schützt aber nicht vor Sound, weil der Constraint die Wandgeschwindigkeit dauerhaft über 150 px/s hält.

### 5. Positionskorrektur-Slop `0.15 px`
```kotlin
val correction = overlap + CoinRainConfig.Physics.POSITION_CORRECTION_SLOP  // 0.15
```
Bei Kontakt ohne Überlappung (`overlap=0`) werden Münzen trotzdem um 0,15 px auseinandergeschoben. Erzeugt ständige kleine Relativgeschwindigkeit zwischen berührenden Münzen. Nebenursache, nicht Hauptursache.

---

## Fehlerklassifikation: Stabiler Grenzzyklus

Mechanismus:
1. Schwerkraft: `+83 px/s` pro Frame in Eckenrichtung (5000/60)
2. Wandconstraint zeroing-t die wandgerichtete Komponente (`e=0` wegen Slop)
3. Münze-Münze-Constraint mit massegewichtetem Impuls, 6 Iterationen:
   ```kotlin
   val j = -relVelNormal / (1f / a.massG + 1f / b.massG)
   a.vel -= normal * (j / a.massG)
   b.vel += normal * (j / b.massG)
   ```
   Schwere Münze (8,5 g) drückt leichte Münze (2,3 g): Impuls teilt sich um Faktor 3,7 zuungunsten der leichten → **leichte Münze erhält disproportional hohe Geschwindigkeit**
4. In der Ecke: Kontaktnormals zeigen in alle Richtungen → Kraft zirkuliert statt abgebaut zu werden
5. Ergebnis: Leichte Münzen dauerhaft bei 175–230 px/s → Schall jeden Frame

Warum in Szenario A kein Problem: Alle Normals zeigen vertikal → Kraft läuft durch den Stapel zum Boden → Boden zeroing-t alles → stabile Ruhe.

---

## Empfehlung: Dreiteiliger Fix

### Fix 1 — Gleichmäßige Impulsaufteilung statt massegewichtet

**`World.kt`, Methode `resolveCoinPair`:**

```kotlin
// Vorher:
!a.sleeping && !b.sleeping -> {
    val j = -relVelNormal / (1f / a.massG + 1f / b.massG)
    a.vel = a.vel - normal * (j / a.massG)
    b.vel = b.vel + normal * (j / b.massG)
}

// Nachher:
!a.sleeping && !b.sleeping -> {
    val halfCorrection = relVelNormal * 0.5f
    a.vel = a.vel + normal * halfCorrection
    b.vel = b.vel - normal * halfCorrection
}
```

### Fix 2 — Solver-Iterationen reduzieren

**`config/coinrain.json`:**
```json
"positionSolverIterations": 3
```
(war: 6)

Bisheriger 50/50-Versuch schlug fehl, weil noch 6 Iterationen liefen — jede Iteration kann Geschwindigkeit injizieren.

### Fix 3 — Positions-Slop entfernen

**`config/coinrain.json`:**
```json
"positionCorrectionSlop": 0.0
```
(war: 0.15)

Entfernt künstliche Trennkraft bei Kontakt ohne Überlappung.

### Erwartetes Ergebnis nach dem Fix
- Steady-State-Maximalgeschwindigkeit: <100 px/s
- Wandereignisse >150 px/s in Steady-State: 0
- Eckszenario kommt zur Ruhe

### Risiko
Penetrationen werden mit 3 Iterationen etwas langsamer aufgelöst. Erwartet: maxPen ≤ 0,8 px (Steady-State mit 6 Iterationen war 0,26 px). Optionale Milderung: `positionSolverIterations: 4`.
