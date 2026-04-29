package com.example.coinrain.core

import kotlin.math.*
import kotlin.random.Random

class SoundSynthesizer(val sampleRate: Int = 44100) {

    private var rng = Random(System.nanoTime())

    // Returns null if kinetic energy is below threshold (no audible impact).
    fun synthesizeImpact(spec: CoinSpec, velocityPxPerSec: Float): ShortArray? {
        val energy = 0.5f * velocityPxPerSec * velocityPxPerSec
        if (energy < CoinRainConfig.Sound.ENERGY_THRESHOLD) return null

        val volume = (energy / (energy + 1_000_000f)).coerceIn(0.1f, 1.0f)
        val pitchFactor = 1f + (rng.nextFloat() * 2f - 1f) * CoinRainConfig.Sound.PITCH_VARIATION
        val decayFactor = 1f + (rng.nextFloat() * 2f - 1f) * CoinRainConfig.Sound.DECAY_VARIATION
        val baseFreq = spec.soundFreqHz * pitchFactor
        val decayMs = spec.soundDecayMs * decayFactor

        val noiseDurationSamples = (CoinRainConfig.Sound.NOISE_DURATION_MS / 1000f * sampleRate).toInt()
        val decaySamples = (decayMs / 1000f * sampleRate).toInt()
        val totalSamples = maxOf(noiseDurationSamples, decaySamples)
        val buf = FloatArray(totalSamples)

        // Noise burst (bandpass 2–8 kHz) for the initial transient click
        val lowHz = CoinRainConfig.Sound.NOISE_FILTER_LOW_HZ
        val highHz = CoinRainConfig.Sound.NOISE_FILTER_HIGH_HZ
        addBandpassNoise(buf, noiseDurationSamples, lowHz, highHz, volume * 0.4f)

        // Three harmonically-related damped sinusoids for the ring tone
        val decayRate = ln(0.001f) / decaySamples  // amplitude → 0.1% at end of decay
        for (harmonic in 1..3) {
            val freq = baseFreq * harmonic
            val amp = volume * 0.6f / harmonic
            for (i in buf.indices) {
                val env = exp(decayRate * i)
                buf[i] += amp * env * sin(2f * PI.toFloat() * freq * i / sampleRate)
            }
        }

        // Convert to 16-bit PCM
        return ShortArray(totalSamples) { i ->
            (buf[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun addBandpassNoise(
        buf: FloatArray,
        samples: Int,
        lowHz: Float,
        highHz: Float,
        amplitude: Float
    ) {
        // Single-pole highpass + lowpass approximation (IIR)
        val dtOverTauHp = 1f / (sampleRate / (2f * PI.toFloat() * lowHz) + 1f)
        val dtOverTauLp = 1f / (sampleRate / (2f * PI.toFloat() * highHz) + 1f)

        var prevRaw = 0f
        var hpOut = 0f
        var lpOut = 0f

        for (i in 0 until minOf(samples, buf.size)) {
            val raw = rng.nextFloat() * 2f - 1f
            hpOut = (1f - dtOverTauHp) * (hpOut + raw - prevRaw)
            lpOut += dtOverTauLp * (hpOut - lpOut)
            buf[i] += amplitude * lpOut
            prevRaw = raw
        }
    }
}
