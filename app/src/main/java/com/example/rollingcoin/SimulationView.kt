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
        private const val WALL_HAPTIC_BUFFER = 2.5f
        private const val WALL_IMPACT_THRESHOLD = 1.5f
        private const val COIN_IMPACT_THRESHOLD = 1.8f
        private const val SCRAPE_TANGENT_THRESHOLD = 3.0f
        private const val VIBRATION_COOLDOWN_MS = 110L
        private const val SCRAPE_COOLDOWN_MS = 200L

        // Reference mass for acceleration scaling (roughly the midpoint of Euro coin weights)
        private const val REFERENCE_MASS_G = 5.0f
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
            // Lighter coins react more strongly to tilt than heavier ones
            val massScale = REFERENCE_MASS_G / coin.mass
            coin.vx += gravityX * SENSOR_SCALE * accelerationMultiplier * massScale
            coin.vy += gravityY * SENSOR_SCALE * accelerationMultiplier * massScale
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

        applyPhysics()

        var triggerVibrate = false
        var triggerImpactSound = false
        var triggerScrapeSound = false
        var maxImpactVelocity = 0f

        repeat(COLLISION_ITERATIONS) {
            for (coin in coins) {
                resolveWallCollision(coin) { impactVelocity ->
                    triggerVibrate = true
                    if (impactVelocity > WALL_IMPACT_THRESHOLD) triggerImpactSound = true
                    if (impactVelocity > maxImpactVelocity) maxImpactVelocity = impactVelocity
                }
            }
            resolveCoinCollisions { impactVelocity, isScrape ->
                if (isScrape) {
                    triggerScrapeSound = true
                } else {
                    triggerVibrate = true
                    if (impactVelocity > COIN_IMPACT_THRESHOLD) triggerImpactSound = true
                    if (impactVelocity > maxImpactVelocity) maxImpactVelocity = impactVelocity
                }
            }
        }

        for (coin in coins) {
            val bitmap = coinBitmaps[coin.type]
            if (bitmap != null) {
                drawRect.set(
                    coin.x - coin.radius,
                    coin.y - coin.radius,
                    coin.x + coin.radius,
                    coin.y + coin.radius
                )
                canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
            }
        }

        if (triggerVibrate) vibrate(maxImpactVelocity)
        if (triggerImpactSound) playImpactSound(maxImpactVelocity)
        if (triggerScrapeSound) playScrapeSound()

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
            coin.vx *= -BOUNCE
            coin.wasHitLeft = true
        } else if (coin.x > r + WALL_HAPTIC_BUFFER) {
            coin.wasHitLeft = false
        }

        if (coin.x > width - r) {
            val impact = abs(coin.vx)
            coin.x = width - r - 0.15f
            if (!coin.wasHitRight && impact > vibrationThreshold) onImpact(impact)
            coin.vx *= -BOUNCE
            coin.wasHitRight = true
        } else if (coin.x < width - r - WALL_HAPTIC_BUFFER) {
            coin.wasHitRight = false
        }

        if (coin.y < r) {
            val impact = abs(coin.vy)
            coin.y = r + 0.15f
            if (!coin.wasHitTop && impact > vibrationThreshold) onImpact(impact)
            coin.vy *= -BOUNCE
            coin.wasHitTop = true
        } else if (coin.y > r + WALL_HAPTIC_BUFFER) {
            coin.wasHitTop = false
        }

        if (coin.y > bottomLimit) {
            val impact = abs(coin.vy)
            coin.y = bottomLimit - 0.15f
            if (!coin.wasHitBottom && impact > vibrationThreshold) onImpact(impact)
            coin.vy *= -BOUNCE
            coin.wasHitBottom = true
        } else if (coin.y < bottomLimit - WALL_HAPTIC_BUFFER) {
            coin.wasHitBottom = false
        }
    }

    private fun resolveCoinCollisions(onCollision: (velocity: Float, isScrape: Boolean) -> Unit) {
        for (i in 0 until coins.size) {
            for (j in i + 1 until coins.size) {
                val a = coins[i]
                val b = coins[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val distSq = dx * dx + dy * dy
                val minDist = a.radius + b.radius

                if (distSq >= minDist * minDist) {
                    a.collidingWith.remove(b.id)
                    b.collidingWith.remove(a.id)
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

                // Separate overlapping coins — heavier coin moves less
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

                // Coins already moving apart — no impulse needed
                if (velAlongNormal >= 0) continue

                val impact = abs(velAlongNormal)
                if (!a.collidingWith.contains(b.id)) {
                    // First frame of contact — check for impact sound/vibration
                    if (impact > vibrationThreshold) onCollision(impact, false)
                } else {
                    // Already in contact — check for scrape sound
                    val tangentX = -normalY
                    val tangentY = normalX
                    val tangentVelocity = relVelX * tangentX + relVelY * tangentY
                    if (abs(tangentVelocity) > SCRAPE_TANGENT_THRESHOLD) onCollision(0f, true)
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
        soundPool.play(impactSoundId, volume, volume, 1, 0, 1f)
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
