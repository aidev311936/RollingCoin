package com.example.coinrain.core

import kotlin.math.floor

internal class UniformGrid(private val cellSize: Float) {

    private val cells = HashMap<Long, MutableList<CoinState>>(64)

    fun insert(coin: CoinState) {
        // A coin can overlap up to 4 cells — insert into all occupied cells
        val r = coin.radiusPx
        val x0 = cellCol(coin.pos.x - r)
        val x1 = cellCol(coin.pos.x + r)
        val y0 = cellRow(coin.pos.y - r)
        val y1 = cellRow(coin.pos.y + r)
        for (cx in x0..x1) {
            for (cy in y0..y1) {
                cells.getOrPut(key(cx, cy)) { mutableListOf() }.add(coin)
            }
        }
    }

    fun candidatePairs(): List<Pair<CoinState, CoinState>> {
        val seen = HashSet<Long>(cells.size * 4)
        val pairs = mutableListOf<Pair<CoinState, CoinState>>()
        for (cell in cells.values) {
            for (i in cell.indices) {
                for (j in i + 1 until cell.size) {
                    val a = cell[i]
                    val b = cell[j]
                    val pairKey = if (a.id < b.id) pairKey(a.id, b.id) else pairKey(b.id, a.id)
                    if (seen.add(pairKey)) {
                        pairs.add(Pair(a, b))
                    }
                }
            }
        }
        return pairs
    }

    fun clear() = cells.clear()

    private fun cellCol(x: Float) = floor(x / cellSize).toInt()
    private fun cellRow(y: Float) = floor(y / cellSize).toInt()
    private fun key(col: Int, row: Int): Long = col.toLong() shl 32 or (row.toLong() and 0xFFFFFFFFL)
    private fun pairKey(a: Int, b: Int): Long = a.toLong() shl 32 or (b.toLong() and 0xFFFFFFFFL)
}
