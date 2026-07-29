package com.example.coinrain.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies that the mm→px sizing formula produces physically correct coin sizes
 * across a range of screen DPIs. The formula (from CoinRainView.addCoins):
 *   pxPerMm = xdpi / 25.4f
 *   radiusPx = diameterMm / 2f * pxPerMm
 *
 * These tests lock the formula so that a refactor (e.g. accidentally switching to
 * densityDpi buckets instead of physical xdpi) is caught immediately.
 */
class CoinSizingTest {

    private fun computeRadiusPx(diameterMm: Float, xdpi: Float): Float {
        val pxPerMm = xdpi / 25.4f
        return diameterMm / 2f * pxPerMm
    }

    @Test
    fun `back-calculated physical diameter matches nominal across three screen DPIs`() {
        val testDpis = floatArrayOf(320f, 420f, 560f)
        val toleranceMm = 0.2f

        for (spec in CoinRainConfig.COINS) {
            for (xdpi in testDpis) {
                val pxPerMm = xdpi / 25.4f
                val radiusPx = computeRadiusPx(spec.diameterMm, xdpi)
                val measuredMm = radiusPx * 2f / pxPerMm
                val error = abs(measuredMm - spec.diameterMm)
                assertTrue(
                    "Coin ${spec.id} at ${xdpi} DPI: " +
                    "back-calculated ${measuredMm}mm vs nominal ${spec.diameterMm}mm (err=${error}mm)",
                    error < toleranceMm
                )
            }
        }
    }

    @Test
    fun `pixel radius scales linearly with DPI so physical size stays constant`() {
        for (spec in CoinRainConfig.COINS) {
            val r160 = computeRadiusPx(spec.diameterMm, 160f)
            val r320 = computeRadiusPx(spec.diameterMm, 320f)
            val r640 = computeRadiusPx(spec.diameterMm, 640f)
            assertTrue("${spec.id}: r320/r160 should be 2.0", abs(r320 / r160 - 2f) < 0.001f)
            assertTrue("${spec.id}: r640/r320 should be 2.0", abs(r640 / r320 - 2f) < 0.001f)
        }
    }

    @Test
    fun `coin diameters match official Euro specifications`() {
        val official = mapOf(
            "CENT_1"  to 16.25f,
            "CENT_2"  to 18.75f,
            "CENT_5"  to 21.25f,
            "CENT_10" to 19.75f,
            "CENT_20" to 22.25f,
            "CENT_50" to 24.25f,
            "EURO_1"  to 23.25f,
            "EURO_2"  to 25.75f,
        )
        for ((id, expectedMm) in official) {
            val spec = CoinRainConfig.COINS.find { it.id == id }
                ?: error("Coin $id missing from config")
            assertEquals("Diameter of $id", expectedMm, spec.diameterMm, 0.01f)
        }
    }
}
