package com.example.coinrain

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.coinrain.core.*

class CoinRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    var onAnimationFinished: (() -> Unit)? = null

    private val specMap: Map<String, CoinSpec> = CoinRainConfig.COINS.associateBy { it.id }
    val world = World(widthPx = 1f, heightPx = 1f)
    private val renderer = ProceduralCoinRenderer()
    private val synth = SoundSynthesizer()
    private val audio = AudioOutput(synth.sampleRate)
    private var renderThread: RenderThread? = null

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
    }

    fun clearCoins() = world.clearCoins()

    fun addCoins(specId: String, count: Int) {
        val spec = specMap[specId] ?: return
        val pxPerMm = resources.displayMetrics.xdpi / 25.4f
        val radiusPx = spec.diameterMm / 2f * pxPerMm
        val rng = java.util.Random()
        val existingCount = world.coins.size
        repeat(count) { i ->
            world.addCoin(CoinState(
                id = existingCount + i,
                specId = specId,
                radiusPx = radiusPx,
                massG = spec.weightG,
                pos = Vec2(
                    (radiusPx + rng.nextFloat() * (world.widthPx - 2 * radiusPx)).coerceAtLeast(radiusPx),
                    -radiusPx * (i + 1)
                )
            ))
        }
    }

    fun setCoins(specId: String, count: Int) {
        world.clearCoins()
        addCoins(specId, count)
    }

    fun start() {
        renderThread?.let { if (it.running) return }
    }

    fun pause() = renderThread?.pauseRendering()

    fun resume() = renderThread?.resumeRendering()

    fun stop() {
        renderThread?.running = false
        renderThread?.resumeRendering()
        renderThread?.join()
        renderThread = null
        audio.release()
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        val rt = RenderThread(h, world, renderer, audio, synth, specMap)
        renderThread = rt
        rt.start()
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        world.widthPx = width.toFloat()
        world.heightPx = height.toFloat()
        val pxPerMm = resources.displayMetrics.xdpi / 25.4f
        renderer.onSizeChanged(width, height, pxPerMm)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stop()
        renderer.release()
    }
}
