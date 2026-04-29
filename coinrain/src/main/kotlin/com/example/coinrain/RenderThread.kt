package com.example.coinrain

import android.graphics.Canvas
import android.graphics.Color
import android.view.SurfaceHolder
import com.example.coinrain.core.CoinRainConfig
import com.example.coinrain.core.CoinSpec
import com.example.coinrain.core.SoundSynthesizer
import com.example.coinrain.core.World

class RenderThread(
    private val holder: SurfaceHolder,
    private val world: World,
    private val renderer: CoinRenderer,
    private val audio: AudioOutput,
    private val synth: SoundSynthesizer,
    private val specs: Map<String, CoinSpec>
) : Thread("CoinRainRenderThread") {

    @Volatile var paused = false
    @Volatile var running = false

    private val pauseLock = Object()
    private val dt = 1f / CoinRainConfig.Physics.SIM_HZ

    override fun run() {
        running = true
        var lastNs = System.nanoTime()

        while (running) {
            synchronized(pauseLock) {
                while (paused && running) pauseLock.wait()
            }
            if (!running) break

            val nowNs = System.nanoTime()
            val delta = ((nowNs - lastNs) / 1_000_000_000f).coerceAtMost(0.25f)
            lastNs = nowNs

            val events = world.step(delta)
            for (event in events) {
                val spec = specs[world.coins.firstOrNull { it.id == event.coinAId }?.specId] ?: continue
                val samples = synth.synthesizeImpact(spec, event.velocityPxPerSec) ?: continue
                audio.play(samples)
            }

            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try {
                    canvas.drawColor(Color.BLACK)
                    renderer.draw(canvas, world.coins, specs)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }
    }

    fun pauseRendering() {
        paused = true
    }

    fun resumeRendering() {
        synchronized(pauseLock) {
            paused = false
            pauseLock.notifyAll()
        }
    }
}
