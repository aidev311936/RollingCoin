package com.example.coinrain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.coinrain.core.CoinSpec
import com.example.coinrain.core.CoinState

/**
 * Renders coins from drawable-nodpi PNG assets.
 *
 * Assets are loaded from res/drawable-nodpi/ (coin_cent_1.png … coin_euro_2.png).
 * Each source PNG is loaded once and scaled to the physical target size at
 * [onSizeChanged]; within a run the scaled bitmaps are cached per specId+radiusPx.
 * Bilinear filtering (Paint.isFilterBitmap) ensures clean downscaling.
 *
 * Fallback: if a PNG cannot be loaded (missing asset, OOM, corrupt file), the coin
 * is delegated to [ProceduralCoinRenderer] — the host app never crashes due to a
 * missing asset.
 *
 * To replace placeholder PNGs with real assets: drop replacement files into
 * drawable-nodpi/ using the same names (coin_cent_1.png … coin_euro_2.png),
 * rebuild.
 */
class PngCoinRenderer(context: Context) : CoinRenderer {

    private val res = context.resources
    private val pkg = context.packageName

    private val scaledCache = mutableMapOf<String, Bitmap?>()
    private val fallback = ProceduralCoinRenderer()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val matrix = Matrix()

    override fun onSizeChanged(widthPx: Int, heightPx: Int, pixelsPerMm: Float) {
        scaledCache.values.forEach { it?.recycle() }
        scaledCache.clear()
        fallback.onSizeChanged(widthPx, heightPx, pixelsPerMm)
    }

    override fun draw(canvas: Canvas, coins: List<CoinState>, specs: Map<String, CoinSpec>) {
        val fallbackCoins = mutableListOf<CoinState>()
        for (coin in coins) {
            val key = "${coin.specId}_${coin.radiusPx.toInt()}"
            val bmp = scaledCache.getOrPut(key) { loadAndScale(coin.specId, coin.radiusPx) }
            if (bmp != null) {
                matrix.reset()
                matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                matrix.postRotate(Math.toDegrees(coin.angle.toDouble()).toFloat())
                matrix.postTranslate(coin.pos.x, coin.pos.y)
                canvas.drawBitmap(bmp, matrix, paint)
            } else {
                fallbackCoins += coin
            }
        }
        if (fallbackCoins.isNotEmpty()) fallback.draw(canvas, fallbackCoins, specs)
    }

    override fun release() {
        scaledCache.values.forEach { it?.recycle() }
        scaledCache.clear()
        fallback.release()
    }

    private fun loadAndScale(specId: String, radiusPx: Float): Bitmap? {
        val resName = "coin_${specId.lowercase()}"
        val resId = res.getIdentifier(resName, "drawable", pkg)
        if (resId == 0) return null
        return try {
            val src = BitmapFactory.decodeResource(res, resId) ?: return null
            val targetSize = (radiusPx * 2f + 2f).toInt().coerceAtLeast(4)
            val scaled = Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
            if (scaled !== src) src.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }
}
