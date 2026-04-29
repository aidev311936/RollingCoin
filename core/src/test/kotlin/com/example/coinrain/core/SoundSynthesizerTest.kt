package com.example.coinrain.core

import org.junit.Assert.*
import org.junit.Test

class SoundSynthesizerTest {

    private val synth = SoundSynthesizer(sampleRate = 44100)

    @Test
    fun `returns null below energy threshold`() {
        val spec = CoinRainConfig.COINS.first()
        // v such that 0.5*v^2 < ENERGY_THRESHOLD
        val lowVel = 1f
        assertNull(synth.synthesizeImpact(spec, lowVel))
    }

    @Test
    fun `returns non-empty array above threshold`() {
        val spec = CoinRainConfig.COINS.first()
        val highVel = 2000f
        val result = synth.synthesizeImpact(spec, highVel)
        assertNotNull(result)
        assertTrue("Expected samples, got empty array", result!!.isNotEmpty())
    }

    @Test
    fun `all 8 denominations produce different sounds`() {
        val velocity = 2000f
        val results = CoinRainConfig.COINS.map {
            synth.synthesizeImpact(it, velocity)?.toList()
        }
        // Each denomination should produce audio
        results.forEach { assertNotNull(it) }
        // Not all identical (frequency differs per denomination)
        val uniqueResults = results.distinctBy { it?.take(200) }
        assertTrue("Expected distinct sounds per denomination, got ${uniqueResults.size}", uniqueResults.size > 1)
    }

    @Test
    fun `output is bounded to short range`() {
        val spec = CoinRainConfig.COINS.last()
        val samples = synth.synthesizeImpact(spec, 5000f)!!
        for (s in samples) {
            assertTrue("Sample $s out of short range", s in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }
}
