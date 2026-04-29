package com.example.coinrain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.example.coinrain.core.CoinRainConfig
import com.example.coinrain.core.Vec2

class SensorAdapter(private val context: Context, private val view: CoinRainView) : SensorEventListener {
    private val world get() = view.world

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Exponential smoothing state for gravity sensor
    private var smoothGx = 0f
    private var smoothGy = 0f
    private val alpha = 0.8f       // fast response — low alpha caused tilt to feel sluggish
    private val deadZone = 0.3f    // m/s²

    // Reference gravity: the value at the last wakeAll() call.
    // Compared against current smoothed gravity to detect meaningful tilt changes
    // that would otherwise be too gradual to exceed the per-step wake threshold.
    private var refGravityX = 0f
    private var refGravityY = 0f
    private val tiltWakeThreshold = 1000f  // px/s² — ~20% of full gravity

    // Scale from m/s² to px/s² (target: 5000 px/s² = 1 g)
    // 1 g = 9.81 m/s², target = 5000 px/s²
    private val gravityScale = 5000f / 9.81f

    // Shake threshold in m/s² (linear acceleration)
    private val shakeThreshold = 8f
    private val shakeImpulse = 3000f  // px/s impulse magnitude

    fun register() {
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                // Remap sensor axes to screen axes based on rotation
                val (sx, sy) = remapGravity(event.values[0], event.values[1], rotation)
                val gx = if (kotlin.math.abs(sx) < deadZone) 0f else sx
                val gy = if (kotlin.math.abs(sy) < deadZone) 0f else sy
                smoothGx = alpha * gx + (1f - alpha) * smoothGx
                smoothGy = alpha * gy + (1f - alpha) * smoothGy
                val newGravityX = smoothGx * gravityScale
                val newGravityY = smoothGy * gravityScale
                val dx = newGravityX - refGravityX
                val dy = newGravityY - refGravityY
                if (dx * dx + dy * dy > tiltWakeThreshold * tiltWakeThreshold) {
                    world.wakeAll()
                    refGravityX = newGravityX
                    refGravityY = newGravityY
                }
                world.gravity = Vec2(newGravityX, newGravityY)
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val magnitude = kotlin.math.sqrt(
                    event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
                )
                if (magnitude > shakeThreshold) {
                    val (ix, iy) = remapGravity(event.values[0], event.values[1], rotation)
                    val scale = shakeImpulse / magnitude
                    world.applyShakeImpulse(Vec2(ix * scale, iy * scale))
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    // TYPE_GRAVITY reports the reaction force (normal force), not the gravitational pull.
    // Device X is parallel to screen X, so the reaction-force sign must be flipped for X.
    // Device Y is anti-parallel to screen Y (device +Y = up, screen +Y = down), so the
    // two negations cancel and rawY maps correctly without extra sign flip.
    private fun remapGravity(rawX: Float, rawY: Float, rotation: Int): Pair<Float, Float> =
        when (rotation) {
            Surface.ROTATION_0   ->  Pair(-rawX,  rawY)
            Surface.ROTATION_90  ->  Pair(-rawY,  rawX)
            Surface.ROTATION_180 ->  Pair( rawX, -rawY)
            Surface.ROTATION_270 ->  Pair( rawY, -rawX)
            else                 ->  Pair(-rawX,  rawY)
        }
}
