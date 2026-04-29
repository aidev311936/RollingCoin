package com.example.coinrain

import android.graphics.*
import com.example.coinrain.core.CoinRainConfig
import com.example.coinrain.core.CoinSpec
import com.example.coinrain.core.CoinState

class ProceduralCoinRenderer : CoinRenderer {

    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    override fun onSizeChanged(widthPx: Int, heightPx: Int, pixelsPerMm: Float) {
        bitmapCache.values.forEach { it.recycle() }
        bitmapCache.clear()
        // Bitmaps are built on-demand per spec with the actual radiusPx at render time.
    }

    override fun draw(canvas: Canvas, coins: List<CoinState>, specs: Map<String, CoinSpec>) {
        for (coin in coins) {
            val spec = specs[coin.specId] ?: continue
            val bitmap = bitmapCache.getOrPut("${coin.specId}_${coin.radiusPx.toInt()}") {
                buildCoinBitmap(spec, coin.radiusPx)
            }
            matrix.reset()
            matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            matrix.postRotate(Math.toDegrees(coin.angle.toDouble()).toFloat())
            matrix.postTranslate(coin.pos.x, coin.pos.y)
            canvas.drawBitmap(bitmap, matrix, null)
        }
    }

    override fun release() {
        bitmapCache.values.forEach { it.recycle() }
        bitmapCache.clear()
    }

    private fun buildCoinBitmap(spec: CoinSpec, radiusPx: Float): Bitmap {
        val size = (radiusPx * 2f + 2f).toInt()
        val cx = size / 2f
        val cy = size / 2f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Base disk
        p.color = spec.color
        c.drawCircle(cx, cy, radiusPx, p)

        // Bimetallic center
        if (spec.bimetallic) {
            p.color = spec.centerColor
            c.drawCircle(cx, cy, radiusPx * 0.55f, p)
        }

        // Rim ridges
        p.color = spec.rimColor
        p.strokeWidth = radiusPx * 0.07f
        p.style = Paint.Style.STROKE
        val ridgeR = radiusPx * 0.92f
        val ridgeCount = CoinRainConfig.Rendering.RIM_RIDGE_COUNT
        val ridgeStep = (2 * Math.PI / ridgeCount).toFloat()
        for (i in 0 until ridgeCount) {
            if (i % 2 == 0) {
                val a0 = i * ridgeStep
                val a1 = a0 + ridgeStep * 0.6f
                val oval = RectF(cx - ridgeR, cy - ridgeR, cx + ridgeR, cy + ridgeR)
                c.drawArc(oval, Math.toDegrees(a0.toDouble()).toFloat(),
                    Math.toDegrees((a1 - a0).toDouble()).toFloat(), false, p)
            }
        }
        p.style = Paint.Style.FILL

        // EU stars (only for euro coins — 1€ and 2€ are bimetallic, ≥23mm)
        if (spec.bimetallic) {
            p.color = Color.argb(200, 255, 215, 0)
            val starR = radiusPx * CoinRainConfig.Rendering.STAR_RADIUS_RATIO
            val starCount = CoinRainConfig.Rendering.STAR_COUNT
            val starAngleStep = (2 * Math.PI / starCount).toFloat()
            for (i in 0 until starCount) {
                val a = i * starAngleStep - Math.PI.toFloat() / 2f
                val sx = cx + starR * kotlin.math.cos(a)
                val sy = cy + starR * kotlin.math.sin(a)
                c.drawCircle(sx, sy, radiusPx * 0.05f, p)
            }
        }

        // Radial gloss gradient
        val glossColors = intArrayOf(
            Color.argb((spec.glossAlpha * 255).toInt(), 255, 255, 255),
            Color.TRANSPARENT
        )
        val glossShader = RadialGradient(
            cx - radiusPx * 0.3f, cy - radiusPx * 0.3f,
            radiusPx * 0.9f,
            glossColors, null, Shader.TileMode.CLAMP
        )
        p.shader = glossShader
        c.drawCircle(cx, cy, radiusPx, p)
        p.shader = null

        // Rim outline
        p.style = Paint.Style.STROKE
        p.strokeWidth = radiusPx * 0.04f
        p.color = Color.argb(120, 0, 0, 0)
        c.drawCircle(cx, cy, radiusPx - p.strokeWidth / 2f, p)

        return bmp
    }
}
