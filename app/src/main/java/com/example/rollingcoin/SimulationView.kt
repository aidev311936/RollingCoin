package com.example.rollingcoin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class SimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    // region Constants
    companion object {
        private const val BOUNCE = 0.65f
        private const val SENSOR_SCALE = 0.85f
        private const val COLLISION_ITERATIONS = 3
        private const val WALL_HAPTIC_BUFFER = 20.0f
        // Max outgoing speed after a wall bounce. Small value keeps coins within the
        // WALL_HAPTIC_BUFFER and close to neighbours so prevContacts stays intact —
        // preventing both visible oscillation and endless coin-coin re-triggers.
        private const val MAX_WALL_BOUNCE = 1.5f
        private const val WALL_IMPACT_THRESHOLD = 3.5f
        private const val COIN_IMPACT_THRESHOLD = 3.5f
        private const val SCRAPE_TANGENT_THRESHOLD = 3.0f
        private const val SCRAPE_MIN_SPEED = 3.0f
        // Below this speed, two coins in contact are treated as resting against each
        // other: impulse and sounds are suppressed, positional correction still runs.
        // Prevents corner oscillation where corrections inject velocity every frame.
        private const val COIN_REST_SPEED = 1.0f
        // Gap tolerance for contact hysteresis: a coin pair separated by less than this
        // distance after a positional correction is still treated as "in contact" for
        // first-contact detection purposes. Prevents the correction from producing a
        // 0.15 px gap that resets collidingWith and triggers a false first-contact sound
        // on the very next frame.
        private const val CONTACT_HYSTERESIS = 3f
        private const val VIBRATION_COOLDOWN_MS = 110L
        private const val SCRAPE_COOLDOWN_MS = 200L

        // Reference mass for acceleration scaling (roughly the midpoint of Euro coin weights)
        private const val REFERENCE_MASS_G = 5.0f

        // Set to true to enable CSV-style physics logging via adb logcat -s PHYSICS_DEBUG
        private const val DEBUG_PHYSICS = false
    }
    // endregion

    // Set by MainActivity to the actual measured height of the settings panel
    var bottomInset: Float = 0f

    // region Hardware
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    // endregion

    // region Sound
    private val soundPool: SoundPool
    private var impactSoundId: Int = -1
    private var scrapeSoundId: Int = -1
    var volumePercent = 0.5f
    private var lastScrapeTime = 0L
    // endregion

    // region Coins
    private val coins = mutableListOf<Coin>()
    private val random = Random()
    private var nextCoinId = 0
    // endregion

    // region Settings (property setters trigger recalculation of derived values)
    var radiusPercent = 0.3f
        set(value) { field = value; updateDerivedValues() }
    var weightPercent = 0.5f
        set(value) { field = value; updateDerivedValues() }
    var glidePercent = 0.3f
        set(value) { field = value; updateDerivedValues() }
    var vibrationPercent = 0.8f
        set(value) { field = value; updateDerivedValues() }
    // endregion

    // region Derived physics values (recalculated when settings change)
    private var coinRadius = 50f
    private var accelerationMultiplier = 1.0f
    private var friction = 0.97f
    private var vibrationIntensityFactor = 1.0f
    private var vibrationThreshold = 0.3f
    private var lastVibrationTime = 0L
    // endregion

    // region Sensor input
    private var gravityX = 0f
    private var gravityY = 0f
    // endregion

    // region Debug logging state (zero cost when DEBUG_PHYSICS = false)
    private var debugFrameCount = 0L
    private val debugGravityCancel = HashMap<Int, Boolean>(16)  // coinId -> gravity was cancelled this frame
    private val debugScrapeCoins = HashSet<Int>(8)              // coins involved in a scrape this frame
    private val debugImpactCoins = HashSet<Int>(8)              // coins involved in an impact this frame
    // endregion

    // region Bitmaps
    private val coinBitmaps: Map<CoinType, Bitmap> = CoinType.values().associateWith { type ->
        val resId = context.resources.getIdentifier(type.drawableName, "drawable", context.packageName)
        BitmapFactory.decodeResource(context.resources, resId)
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawRect = RectF()
    // endregion

    init {
        setBackgroundColor(Color.parseColor("#F5F5F5"))
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isHapticFeedbackEnabled = true

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        loadSounds()
    }

    // region Sound loading
    private fun loadSounds() {
        val customImpact = File(context.filesDir, "custom_impact.mp3")
        impactSoundId = if (customImpact.exists()) {
            soundPool.load(customImpact.absolutePath, 1)
        } else {
            val resId = context.resources.getIdentifier("coin_impact", "raw", context.packageName)
            if (resId != 0) soundPool.load(context, resId, 1) else -1
        }

        val customScrape = File(context.filesDir, "custom_scrape.mp3")
        scrapeSoundId = if (customScrape.exists()) {
            soundPool.load(customScrape.absolutePath, 1)
        } else {
            val resId = context.resources.getIdentifier("coin_scrape", "raw", context.packageName)
            if (resId != 0) soundPool.load(context, resId, 1) else -1
        }
    }

    fun reloadImpactSound() {
        impactSoundId = soundPool.load(File(context.filesDir, "custom_impact.mp3").absolutePath, 1)
    }

    fun reloadScrapeSound() {
        scrapeSoundId = soundPool.load(File(context.filesDir, "custom_scrape.mp3").absolutePath, 1)
    }

    fun resetImpactSound() {
        File(context.filesDir, "custom_impact.mp3").delete()
        val resId = context.resources.getIdentifier("coin_impact", "raw", context.packageName)
        if (resId != 0) impactSoundId = soundPool.load(context, resId, 1)
    }

    fun resetScrapeSound() {
        File(context.filesDir, "custom_scrape.mp3").delete()
        val resId = context.resources.getIdentifier("coin_scrape", "raw", context.packageName)
        if (resId != 0) scrapeSoundId = soundPool.load(context, resId, 1)
    }
    // endregion

    // region Coin management
    fun setCountForType(type: CoinType, count: Int) {
        coins.removeAll { it.type == type }
        repeat(count) {
            coins.add(
                Coin(
                    id = nextCoinId++,
                    type = type,
                    x = width / 2f + random.nextFloat() * 30,
                    y = height / 2f + random.nextFloat() * 30,
                    radius = radiusForType(type)
                )
            )
        }
        invalidate()
    }

    private fun radiusForType(type: CoinType): Float {
        val scale = type.diameterMm / CoinType.MAX_DIAMETER_MM
        return coinRadius * scale
    }
    // endregion

    // region Physics
    private fun updateDerivedValues() {
        if (width == 0) return

        val minDimension = min(width, height)
        val maxRadius = minDimension / 4.5f
        val minRadius = 20f
        coinRadius = minRadius + (maxRadius - minRadius) * radiusPercent
        accelerationMultiplier = 2.8f - (weightPercent * 2.5f)
        friction = 0.90f + (glidePercent * 0.098f)
        vibrationIntensityFactor = 0.4f + (vibrationPercent * 2.6f)
        vibrationThreshold = 1.5f - (vibrationPercent * 1.3f)

        // Update radius of all existing coins proportionally
        for (coin in coins) {
            coin.radius = radiusForType(coin.type)
        }
        invalidate()
    }

    private fun applyPhysics() {
        for (coin in coins) {
            val massScale = REFERENCE_MASS_G / coin.mass
            val r = coin.radius
            val bottomLimit = height - (r + bottomInset)

            // Cancel gravity perpendicular to a wall while the coin is within
            // WALL_HAPTIC_BUFFER of it. The zone must be at least as wide as the
            // distance a coin travels in one frame after a MAX_WALL_BOUNCE rebound
            // (1.5 × friction ≈ 1.39 px); using WALL_HAPTIC_BUFFER (6 px) ensures the
            // coin stays inside the cancel zone until friction drains its velocity to
            // zero — preventing gravity from re-accelerating it on the very next frame.
            val atLeft   = coin.x <= r + WALL_HAPTIC_BUFFER
            val atRight  = coin.x >= width - r - WALL_HAPTIC_BUFFER
            val atTop    = coin.y <= r + WALL_HAPTIC_BUFFER
            val atBottom = coin.y >= bottomLimit - WALL_HAPTIC_BUFFER

            val gx = if ((atLeft && gravityX < 0f) || (atRight && gravityX > 0f)) 0f else gravityX
            val gy = if ((atTop  && gravityY < 0f) || (atBottom && gravityY > 0f)) 0f else gravityY

            // Wall-rest: zero slow inward velocity near wall (correction-induced drift).
            // Speed-limited to MAX_WALL_BOUNCE so fast-approaching coins from outside
            // the zone are not stopped early — only the small correction-induced
            // velocities (< 1.5 px/frame) that would otherwise re-overlap neighbours.
            if (atLeft   && coin.vx < 0f && -coin.vx <= MAX_WALL_BOUNCE) coin.vx = 0f
            if (atRight  && coin.vx > 0f &&  coin.vx <= MAX_WALL_BOUNCE) coin.vx = 0f
            if (atTop    && coin.vy < 0f && -coin.vy <= MAX_WALL_BOUNCE) coin.vy = 0f
            if (atBottom && coin.vy > 0f &&  coin.vy <= MAX_WALL_BOUNCE) coin.vy = 0f

            // Corner-rest: when gravity is cancelled on BOTH axes (coin is pressed into a
            // corner), zero all slow velocity — not just the inward component. Coin-coin
            // corrections can inject small outward velocity that the single-axis wall-rest
            // above wouldn't catch, causing visible trembling in corners.
            if (gx == 0f && gy == 0f) {
                val speed = sqrt(coin.vx * coin.vx + coin.vy * coin.vy)
                if (speed <= MAX_WALL_BOUNCE) {
                    coin.vx = 0f
                    coin.vy = 0f
                }
            }

            if (DEBUG_PHYSICS) debugGravityCancel[coin.id] = (gx != gravityX || gy != gravityY)

            coin.vx += gx * SENSOR_SCALE * accelerationMultiplier * massScale
            coin.vy += gy * SENSOR_SCALE * accelerationMultiplier * massScale
            coin.vx *= friction
            coin.vy *= friction
            coin.x += coin.vx
            coin.y += coin.vy
        }
    }
    // endregion

    // region Sensor callbacks
    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        postInvalidateOnAnimation()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val rawX = -event.values[0]
        val rawY = event.values[1]
        gravityX = if (abs(rawX) < 0.25f) 0f else rawX
        gravityY = if (abs(rawY) < 0.25f) 0f else rawY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        for (coin in coins) {
            coin.x = w / 2f
            coin.y = h / 2f
        }
        updateDerivedValues()
    }
    // endregion

    // region Draw loop
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (DEBUG_PHYSICS) {
            debugFrameCount++
            debugGravityCancel.clear()
            debugScrapeCoins.clear()
            debugImpactCoins.clear()
            Log.d("PHYSICS_DEBUG",
                "F=$debugFrameCount FRAME coins=${coins.size}" +
                " gravX=${"%.3f".format(gravityX)} gravY=${"%.3f".format(gravityY)}"
            )
        }

        applyPhysics()

        var triggerVibrate = false
        var triggerImpactSound = false

        var maxImpactVelocity = 0f

        // Snapshot contacts from the previous frame before iterating.
        // First-contact detection must use this stable snapshot — not the live set —
        // because the live set is modified within each iteration, which would cause
        // false "first contact" re-triggers in iteration 3 after a removal in iteration 2.
        val prevContacts: Map<Int, Set<Int>> = coins.associate { it.id to it.collidingWith.toSet() }
        for (coin in coins) { coin.collidingWith.clear() }

        repeat(COLLISION_ITERATIONS) {
            for (coin in coins) {
                resolveWallCollision(coin) { impactVelocity ->
                    triggerVibrate = true
                    if (impactVelocity > WALL_IMPACT_THRESHOLD) triggerImpactSound = true
                    if (impactVelocity > maxImpactVelocity) maxImpactVelocity = impactVelocity
                }
            }
            resolveCoinCollisions(prevContacts) { impactVelocity, isScrape ->
                if (!isScrape && impactVelocity > COIN_IMPACT_THRESHOLD) {
                    // Use COIN_IMPACT_THRESHOLD as the floor for both sound and vibration.
                    // Wall-bounce residuals produce impacts well below this threshold,
                    // so resting coins at the edge don't trigger feedback endlessly.
                    triggerVibrate = true
                    triggerImpactSound = true
                    if (impactVelocity > maxImpactVelocity) maxImpactVelocity = impactVelocity
                }
            }
        }

        if (DEBUG_PHYSICS) {
            for (coin in coins) {
                val speed = sqrt(coin.vx * coin.vx + coin.vy * coin.vy)
                val wallContacts = (if (coin.wasHitLeft) 1 else 0) +
                    (if (coin.wasHitRight) 1 else 0) +
                    (if (coin.wasHitTop) 1 else 0) +
                    (if (coin.wasHitBottom) 1 else 0)
                Log.d("PHYSICS_DEBUG",
                    "F=$debugFrameCount COIN=${coin.id} type=${coin.type.name}" +
                    " x=${"%.1f".format(coin.x)} y=${"%.1f".format(coin.y)}" +
                    " vx=${"%.3f".format(coin.vx)} vy=${"%.3f".format(coin.vy)}" +
                    " speed=${"%.3f".format(speed)}" +
                    " gravCancelled=${debugGravityCancel[coin.id] == true}" +
                    " wallContacts=$wallContacts coinContacts=${coin.collidingWith.size}" +
                    " scrape=${coin.id in debugScrapeCoins} impact=${coin.id in debugImpactCoins}"
                )
            }
        }

        for (coin in coins) {
            val bitmap = coinBitmaps[coin.type] ?: continue
            drawRect.set(-coin.radius, -coin.radius, coin.radius, coin.radius)
            canvas.save()
            canvas.translate(coin.x, coin.y)
            canvas.rotate(coin.rotation)
            canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
            canvas.restore()
        }

        if (triggerVibrate) vibrate(maxImpactVelocity)
        if (triggerImpactSound) playImpactSound(maxImpactVelocity)

        postInvalidateOnAnimation()
    }
    // endregion

    // region Collision resolution
    private fun resolveWallCollision(coin: Coin, onImpact: (Float) -> Unit) {
        val r = coin.radius
        val bottomLimit = height - (r + bottomInset)

        if (coin.x < r) {
            val impact = abs(coin.vx)
            coin.x = r + 0.15f
            if (!coin.wasHitLeft && impact > vibrationThreshold) onImpact(impact)
            coin.vx = (-coin.vx * BOUNCE).coerceAtMost(MAX_WALL_BOUNCE)
            // Gravity pressing into this wall: kill outgoing bounce velocity.
            // Without this, coin-coin positional corrections push the wall coin
            // through the wall; the resulting bounce gives vx=MAX_WALL_BOUNCE which
            // then drives coin-coin collisions and sustains the scrape loop.
            if (gravityX < 0f) coin.vx = 0f
            coin.wasHitLeft = true
        } else if (coin.x > r + WALL_HAPTIC_BUFFER && coin.vx > MAX_WALL_BOUNCE) {
            // Require outward velocity > MAX_WALL_BOUNCE to reset: a correction that
            // briefly throws the coin past the buffer (at near-zero velocity) must not
            // count as "leaving the wall" and re-arm the haptic trigger.
            coin.wasHitLeft = false
        }

        if (coin.x > width - r) {
            val impact = abs(coin.vx)
            coin.x = width - r - 0.15f
            if (!coin.wasHitRight && impact > vibrationThreshold) onImpact(impact)
            coin.vx = (-coin.vx * BOUNCE).coerceAtLeast(-MAX_WALL_BOUNCE)
            if (gravityX > 0f) coin.vx = 0f
            coin.wasHitRight = true
        } else if (coin.x < width - r - WALL_HAPTIC_BUFFER && coin.vx < -MAX_WALL_BOUNCE) {
            coin.wasHitRight = false
        }

        if (coin.y < r) {
            val impact = abs(coin.vy)
            coin.y = r + 0.15f
            if (!coin.wasHitTop && impact > vibrationThreshold) onImpact(impact)
            coin.vy = (-coin.vy * BOUNCE).coerceAtMost(MAX_WALL_BOUNCE)
            if (gravityY < 0f) coin.vy = 0f
            coin.wasHitTop = true
        } else if (coin.y > r + WALL_HAPTIC_BUFFER && coin.vy > MAX_WALL_BOUNCE) {
            coin.wasHitTop = false
        }

        if (coin.y > bottomLimit) {
            val impact = abs(coin.vy)
            coin.y = bottomLimit - 0.15f
            if (!coin.wasHitBottom && impact > vibrationThreshold) onImpact(impact)
            coin.vy = (-coin.vy * BOUNCE).coerceAtLeast(-MAX_WALL_BOUNCE)
            if (gravityY > 0f) coin.vy = 0f
            coin.wasHitBottom = true
        } else if (coin.y < bottomLimit - WALL_HAPTIC_BUFFER && coin.vy < -MAX_WALL_BOUNCE) {
            coin.wasHitBottom = false
        }
    }

    private fun resolveCoinCollisions(
        prevContacts: Map<Int, Set<Int>>,
        onCollision: (velocity: Float, isScrape: Boolean) -> Unit
    ) {
        for (i in 0 until coins.size) {
            for (j in i + 1 until coins.size) {
                val a = coins[i]
                val b = coins[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val distSq = dx * dx + dy * dy
                val minDist = a.radius + b.radius

                if (distSq >= minDist * minDist) {
                    // Contact hysteresis: if this pair was in contact last frame and the
                    // gap is tiny (< CONTACT_HYSTERESIS), keep them in collidingWith.
                    // Positional corrections leave a ~0.15 px gap after each resolution;
                    // without this, the pair drops out of collidingWith every frame and
                    // triggers a false first-contact sound on the next frame.
                    if (prevContacts[a.id]?.contains(b.id) == true) {
                        val dist = sqrt(distSq.toDouble()).toFloat()
                        if (dist - minDist < CONTACT_HYSTERESIS) {
                            a.collidingWith.add(b.id)
                            b.collidingWith.add(a.id)
                        }
                    }
                    continue
                }

                val dist = sqrt(distSq.toDouble()).toFloat()
                val normalX: Float
                val normalY: Float
                val overlap: Float

                if (dist < 0.1f) {
                    // Coins are exactly on top of each other — push apart in a random direction
                    val angle = random.nextFloat() * 2 * Math.PI.toFloat()
                    normalX = cos(angle.toDouble()).toFloat()
                    normalY = sin(angle.toDouble()).toFloat()
                    overlap = minDist
                } else {
                    normalX = dx / dist
                    normalY = dy / dist
                    overlap = minDist - dist
                }

                if (DEBUG_PHYSICS) {
                    val relVx = b.vx - a.vx
                    val relVy = b.vy - a.vy
                    val relSpeed = sqrt(relVx * relVx + relVy * relVy)
                    val tX = -normalY; val tY = normalX
                    val tangentVel = relVx * tX + relVy * tY
                    val aGC = debugGravityCancel[a.id] == true
                    val bGC = debugGravityCancel[b.id] == true
                    Log.d("PHYSICS_DEBUG",
                        "F=$debugFrameCount PAIR=${a.id}-${b.id} overlap=${"%.2f".format(overlap)}" +
                        " relVx=${"%.3f".format(relVx)} relVy=${"%.3f".format(relVy)}" +
                        " relSpeed=${"%.3f".format(relSpeed)} tangentVel=${"%.3f".format(tangentVel)}" +
                        " aGravCancelled=$aGC bGravCancelled=$bGC" +
                        " asymmetric=${aGC != bGC}"
                    )
                }

                // Separate overlapping coins — heavier coin moves less.
                // Cap correction per frame so coins are never thrown past the wall-cancel
                // zone (WALL_HAPTIC_BUFFER). Without the cap, large overlaps (measured
                // at 11–80 px) fling coins 25+ px from the wall, re-activating gravity
                // and causing repeated wall impacts with audible sound and visible jumping.
                val totalMass = a.mass + b.mass
                val jitter = (random.nextFloat() - 0.5f) * 0.15f
                val correction = overlap + 0.15f
                a.x -= (normalX + jitter) * correction * (b.mass / totalMass)
                a.y -= (normalY + jitter) * correction * (b.mass / totalMass)
                b.x += (normalX + jitter) * correction * (a.mass / totalMass)
                b.y += (normalY + jitter) * correction * (a.mass / totalMass)

                val relVelX = b.vx - a.vx
                val relVelY = b.vy - a.vy
                val velAlongNormal = relVelX * normalX + relVelY * normalY

                // Coins already moving apart — no impulse needed, but maintain contact
                // state so the next frame doesn't see a false first-contact event.
                // This covers the case where a previous iteration's impulse made them
                // diverge while they're still physically overlapping (multi-coin chains).
                if (velAlongNormal >= 0) {
                    a.collidingWith.add(b.id)
                    b.collidingWith.add(a.id)
                    continue
                }

                // Resting contact: coins barely moving relative to each other.
                // Using relative speed (not individual speeds) correctly handles the case
                // where different-mass coins glide together under gravity at different
                // absolute speeds but with nearly zero relative motion — without this,
                // the lighter coin's higher gravity acceleration drives a micro-impulse
                // every frame, causing visible trembling whenever two coins touch.
                val relSpeed = sqrt(relVelX * relVelX + relVelY * relVelY)
                val aSpeed = if (DEBUG_PHYSICS) sqrt(a.vx * a.vx + a.vy * a.vy) else 0f
                val bSpeed = if (DEBUG_PHYSICS) sqrt(b.vx * b.vx + b.vy * b.vy) else 0f
                if (relSpeed < COIN_REST_SPEED) {
                    a.collidingWith.add(b.id)
                    b.collidingWith.add(a.id)
                    continue
                }

                val impact = abs(velAlongNormal)
                val wasCollidingLastFrame = prevContacts[a.id]?.contains(b.id) == true
                if (!wasCollidingLastFrame) {
                    // First frame of contact — check for impact sound/vibration
                    if (impact > vibrationThreshold) {
                        onCollision(impact, false)
                        if (DEBUG_PHYSICS) { debugImpactCoins += a.id; debugImpactCoins += b.id }
                    }
                } else {
                    // Already in contact — check for scrape sound.
                    // Require both coins to be moving; pure gravity-differential between
                    // different-mass coins resting at a wall creates relative velocity
                    // that must not be mistaken for an audible scrape.
                    val tangentX = -normalY
                    val tangentY = normalX
                    val tangentVelocity = relVelX * tangentX + relVelY * tangentY
                    // Suppress scrape when overlap is large: the relative velocity is
                    // driven by positional correction, not real sliding. Log data showed
                    // correction-induced overlaps of 11–80 px vs. genuine scrape overlap
                    // of 1–3 px. Threshold 15% of smaller radius (≈7.5 px at r=50)
                    // safely separates the two cases.
                    val scrapeOverlapLimit = minOf(a.radius, b.radius) * 0.15f
                    if (abs(tangentVelocity) > SCRAPE_TANGENT_THRESHOLD &&
                        aSpeed > SCRAPE_MIN_SPEED && bSpeed > SCRAPE_MIN_SPEED &&
                        overlap < scrapeOverlapLimit) {
                        onCollision(0f, true)
                        if (DEBUG_PHYSICS) { debugScrapeCoins += a.id; debugScrapeCoins += b.id }
                    }
                }

                // Mass-weighted impulse: heavier coin absorbs less velocity change
                val impulse = -(1 + BOUNCE) * velAlongNormal / (1f / a.mass + 1f / b.mass)
                a.vx -= (impulse / a.mass) * normalX
                a.vy -= (impulse / a.mass) * normalY
                b.vx += (impulse / b.mass) * normalX
                b.vy += (impulse / b.mass) * normalY

                a.collidingWith.add(b.id)
                b.collidingWith.add(a.id)
            }
        }
    }
    // endregion

    // region Feedback
    private fun playImpactSound(velocity: Float) {
        if (impactSoundId == -1) return
        val volume = (velocity / 25f).coerceIn(0.1f, 1.0f) * volumePercent
        // Pitch 0.6 → lower, duller tone — like a coin striking the inside of a phone case
        soundPool.play(impactSoundId, volume, volume, 1, 0, 0.6f)
    }

    private fun playScrapeSound() {
        if (scrapeSoundId == -1) return
        val now = System.currentTimeMillis()
        if (now - lastScrapeTime < SCRAPE_COOLDOWN_MS) return
        lastScrapeTime = now
        val volume = 0.2f * volumePercent
        val pitch = 0.85f + random.nextFloat() * 0.3f
        soundPool.play(scrapeSoundId, volume, volume, 0, 0, pitch)
    }

    private fun vibrate(velocity: Float) {
        val now = System.currentTimeMillis()
        if (now - lastVibrationTime < VIBRATION_COOLDOWN_MS) return
        lastVibrationTime = now

        // Samsung devices need an additional haptic call for reliable feedback
        if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
            performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (velocity * 35 * vibrationIntensityFactor).coerceIn(45f, 255f).toInt()
            val duration = (velocity * 2.5f * vibrationIntensityFactor).coerceIn(15f, 45f).toLong()
            try {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, duration),
                    intArrayOf(0, intensity),
                    -1
                )
                vibrator.vibrate(effect)
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
    // endregion
}
