package com.example.coinrain

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.coinrain.core.CoinRainConfig

/**
 * Drives the vibration motor on coin impacts.
 *
 * Decision priority (highest first):
 *   1. config mode="off"  → never vibrate
 *   2. config mode="on"   → always vibrate (if hardware present)
 *   3. modelBlocklist match → off (overrides auto heuristic)
 *   4. modelAllowlist match → on  (overrides auto heuristic)
 *   5. auto heuristic: Vibrator.hasAmplitudeControl() — true → fine LRA motor assumed → on;
 *      false → coarse ERM motor assumed → off.
 *      NOTE: this is a heuristic, not a documented API for motor quality. It is the best
 *      proxy available without proprietary OEM APIs.
 *
 * Match is substring/prefix on Build.MODEL and Build.DEVICE so that regional variants
 * (e.g. SM-G960F, SM-G960U) are caught by a single blocklist/allowlist entry ("SM-G960").
 *
 * Startup log (tag "CoinRainHaptics") prints the exact Build strings so that unknown
 * devices can be added to the lists without guessing.
 *
 * OEM workaround (Samsung + Xiaomi/POCO): both OEMs silence VibrationEffect calls from
 * 3rd-party apps via VibratorService policy (Samsung: TYPE_EXTRA mag=0; Xiaomi HyperOS:
 * audio=ignore). View.performHapticFeedback() bypasses VibratorService entirely and works
 * on both. Confirmed via logcat on Galaxy S9 and POCO X7 Pro.
 */
class CoinHapticsPlayer(context: Context, private val view: View) {

    private val vibrator: Vibrator?
    private val enabled: Boolean
    private val amplitudeControl: Boolean
    private val useViewHapticWorkaround: Boolean

    @Volatile private var lastVibrationMs = 0L

    init {
        @Suppress("DEPRECATION")
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator = if (vib?.hasVibrator() == true) vib else null

        amplitudeControl = if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.hasAmplitudeControl() ?: false
        } else {
            false
        }

        val model = Build.MODEL
        val device = Build.DEVICE
        val manufacturer = Build.MANUFACTURER
        // Both Samsung and Xiaomi/POCO route VibrationEffect calls through a policy that
        // silences 3rd-party apps (Samsung: TYPE_EXTRA mag=0; Xiaomi HyperOS: audio=ignore).
        // View.performHapticFeedback bypasses VibratorService entirely and works on both.
        useViewHapticWorkaround = manufacturer.contains("samsung", ignoreCase = true)
                 || manufacturer.contains("xiaomi", ignoreCase = true)
        val blocklist = CoinRainConfig.Haptics.MODEL_BLOCKLIST
        val allowlist = CoinRainConfig.Haptics.MODEL_ALLOWLIST

        val matchesBlocklist = blocklist.any {
            model.contains(it, ignoreCase = true) || device.contains(it, ignoreCase = true)
        }
        val matchesAllowlist = allowlist.any {
            model.contains(it, ignoreCase = true) || device.contains(it, ignoreCase = true)
        }

        val mode = CoinRainConfig.Haptics.MODE
        enabled = when {
            vibrator == null  -> false
            mode == "off"     -> false
            mode == "on"      -> true
            matchesBlocklist  -> false
            matchesAllowlist  -> true
            else              -> amplitudeControl
        }

        val reason = when {
            vibrator == null  -> "off: no vibrator hardware"
            mode == "off"     -> "off: mode=off in config"
            mode == "on"      -> "on: mode=on in config"
            matchesBlocklist  -> "off: matched blocklist (MODEL=$model DEVICE=$device)"
            matchesAllowlist  -> "on: matched allowlist (MODEL=$model DEVICE=$device)"
            amplitudeControl  -> "on: hasAmplitudeControl=true (auto heuristic — LRA assumed)"
            else              -> "off: hasAmplitudeControl=false (auto heuristic — ERM assumed)"
        }

        Log.i(TAG, "MANUFACTURER=$manufacturer  MODEL=$model  DEVICE=$device")
        Log.i(TAG, "hasAmplitudeControl=$amplitudeControl  decision=$reason")
    }

    /**
     * Trigger a short haptic tick for an impact event.
     * Uses the same velocity threshold as the sound system for consistency.
     * Called from the render thread — Vibrator and View.post are thread-safe.
     */
    fun play(velocityPxPerSec: Float) {
        if (!enabled) return
        if (velocityPxPerSec < CoinRainConfig.Sound.MIN_IMPACT_VELOCITY_FOR_SOUND) return

        val now = SystemClock.uptimeMillis()
        if (now - lastVibrationMs < COOLDOWN_MS) return
        lastVibrationMs = now

        if (useViewHapticWorkaround) {
            // Samsung One UI routes all VibrationEffect calls to TYPE_EXTRA stream with
            // mag=0, silencing them regardless of AudioAttributes. performHapticFeedback
            // bypasses VibratorService routing entirely via Android's View haptic path.
            view.post {
                view.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            return
        }

        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val amplitude = if (CoinRainConfig.Haptics.SCALE_WITH_IMPACT && amplitudeControl) {
                    // Scale from threshold to 1500 px/s → amplitude 80–255.
                    // Floor at 80 ensures the vibration is perceptible on LRA motors.
                    val minV = CoinRainConfig.Sound.MIN_IMPACT_VELOCITY_FOR_SOUND
                    val t = ((velocityPxPerSec - minV) / (1500f - minV)).coerceIn(0f, 1f)
                    (80 + t * 175f).toInt().coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createOneShot(20L, amplitude)
                if (Build.VERSION.SDK_INT >= 33) {
                    vib.vibrate(effect, VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                        .build())
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(effect, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(20L)
            }
        } catch (_: Exception) {
            // Permission missing or hardware fault — disable silently.
            // Cannot set enabled=false here (val), but crashes are not acceptable.
        }
    }

    fun release() { /* Vibrator holds no owned resources */ }

    companion object {
        private const val TAG = "CoinRainHaptics"
        private const val COOLDOWN_MS = 110L
    }
}
