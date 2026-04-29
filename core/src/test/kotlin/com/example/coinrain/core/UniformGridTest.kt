package com.example.coinrain.core

import org.junit.Assert.*
import org.junit.Test

class UniformGridTest {

    private fun coin(id: Int, x: Float, y: Float, r: Float = 20f) = CoinState(
        id = id, specId = "CENT_20", radiusPx = r, massG = 5f,
        pos = Vec2(x, y)
    )

    @Test
    fun `two close coins produce one candidate pair`() {
        val grid = UniformGrid(cellSize = 50f)
        grid.insert(coin(0, 25f, 25f))
        grid.insert(coin(1, 45f, 25f))
        val pairs = grid.candidatePairs()
        assertEquals(1, pairs.size)
    }

    @Test
    fun `two far coins produce no candidate pairs`() {
        val grid = UniformGrid(cellSize = 50f)
        grid.insert(coin(0, 10f, 10f))
        grid.insert(coin(1, 900f, 900f))
        val pairs = grid.candidatePairs()
        assertEquals(0, pairs.size)
    }

    @Test
    fun `no duplicate pairs for coins in multiple cells`() {
        val grid = UniformGrid(cellSize = 50f)
        // Coin on cell boundary — will be inserted into multiple cells
        grid.insert(coin(0, 50f, 50f, r = 30f))
        grid.insert(coin(1, 55f, 55f, r = 30f))
        val pairs = grid.candidatePairs()
        assertEquals("Expected exactly 1 pair, got ${pairs.size}", 1, pairs.size)
    }

    @Test
    fun `three mutually close coins produce three pairs`() {
        val grid = UniformGrid(cellSize = 100f)
        grid.insert(coin(0, 50f, 50f))
        grid.insert(coin(1, 60f, 50f))
        grid.insert(coin(2, 55f, 60f))
        val pairs = grid.candidatePairs()
        assertEquals(3, pairs.size)
    }
}
