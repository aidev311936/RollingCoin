package com.example.coinrain

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
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
 */
class CoinHapticsPlayer(context: Context) {

    private val vibrator: Vibrator?
    private val enabled: Boolean
    private val amplitudeControl: Boolean

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
     * Called from the render thread — Vibrator is thread-safe.
     */
    fun play(velocityPxPerSec: Float) {
        if (!enabled) return
        if (velocityPxPerSec < CoinRainConfig.Sound.MIN_IMPACT_VELOCITY_FOR_SOUND) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val amplitude = if (CoinRainConfig.Haptics.SCALE_WITH_IMPACT && amplitudeControl) {
                    ((velocityPxPerSec / 2000f) * 255f).toInt().coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                vib.vibrate(VibrationEffect.createOneShot(15L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(15L)
            }
        } catch (_: Exception) {
            // Permission missing or hardware fault — disable silently.
            // Cannot set enabled=false here (val), but crashes are not acceptable.
        }
    }

    fun release() { /* Vibrator holds no owned resources */ }

    companion object {
        private const val TAG = "CoinRainHaptics"
    }
}
