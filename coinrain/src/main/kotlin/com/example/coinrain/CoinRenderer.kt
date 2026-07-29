package com.example.coinrain

import android.graphics.Canvas
import com.example.coinrain.core.CoinSpec
import com.example.coinrain.core.CoinState

interface CoinRenderer {
    fun onSizeChanged(widthPx: Int, heightPx: Int, pixelsPerMm: Float)
    fun draw(canvas: Canvas, coins: List<CoinState>, specs: Map<String, CoinSpec>)
    fun release()
}
