package com.example.coinrain

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.coinrain.core.*

class CoinRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    var onAnimationFinished: (() -> Unit)? = null
    var onError: ((CoinRainError) -> Unit)? = null

    private val fallbackAllowedDenoms: Set<Denomination> by lazy {
        val ids = CoinRainConfig.FALLBACK_ALLOWED_COINS
        if (ids.isEmpty()) Denomination.values().toSet()
        else ids.mapNotNull { Denomination.fromSpecId(it) }.toSet()
    }

    private val specMap: Map<String, CoinSpec> = CoinRainConfig.COINS.associateBy { it.id }
    val world = World(widthPx = 1f, heightPx = 1f)
    private val renderer: CoinRenderer = when (CoinRainConfig.Rendering.RENDERER) {
        "png" -> PngCoinRenderer(context)
        else  -> ProceduralCoinRenderer()
    }
    private val player = CoinImpactPlayer(context)
    private val haptics = CoinHapticsPlayer(context, this)
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
                ),
                angle = rng.nextFloat() * kotlin.math.PI.toFloat() * 2f
            ))
        }
    }

    fun setCoins(specId: String, count: Int) {
        world.clearCoins()
        addCoins(specId, count)
    }

    fun spawnDefaultCoins() {
        world.clearCoins()
        CoinRainConfig.FALLBACK_START_COINS.forEach { specId -> addCoins(specId, 1) }
    }

    /**
     * Clears existing coins and spawns the minimum number of coins that represent
     * [amountCents]. The denomination mix is chosen by [CoinChange] using [allowedCoins]
     * (or [CoinRainConfig.FALLBACK_ALLOWED_COINS] / all denominations when null/empty).
     *
     * On invalid input the error is reported via [onError] and the fallback start coins
     * are shown instead. This method never throws.
     */
    fun rain(amountCents: Int, allowedCoins: Set<Denomination>? = null) {
        if (amountCents < 0 || amountCents > AmountParser.MAX_AMOUNT_CENTS) {
            val msg = "Invalid amountCents=$amountCents (must be 0..${AmountParser.MAX_AMOUNT_CENTS})"
            Log.e(TAG, msg)
            onError?.invoke(CoinRainError.PromoAmountInvalid(msg))
            spawnDefaultCoins()
            return
        }
        val effective = if (allowedCoins.isNullOrEmpty()) fallbackAllowedDenoms else allowedCoins
        val denoms = CoinChange.decompose(amountCents, effective)
        spawnDenominations(denoms)
    }

    /**
     * Convenience overload: parses [amount] ("2.43" → 243 cents) then delegates to
     * [rain(Int, Set)]. On parse failure reports [CoinRainError.PromoAmountInvalid] via
     * [onError] and falls back to the default start coins.
     */
    fun rain(amount: String, allowedCoins: Set<Denomination>? = null) {
        val cents = try {
            AmountParser.parse(amount)
        } catch (e: PromoAmountParseException) {
            Log.e(TAG, "rain(\"$amount\"): ${e.message}")
            onError?.invoke(CoinRainError.PromoAmountInvalid(e.message ?: ""))
            spawnDefaultCoins()
            return
        }
        rain(cents, allowedCoins)
    }

    private fun spawnDenominations(denoms: List<Denomination>) {
        world.clearCoins()
        val pxPerMm = resources.displayMetrics.xdpi / 25.4f
        val rng = java.util.Random()
        denoms.forEachIndexed { i, denom ->
            val spec = specMap[denom.specId] ?: return@forEachIndexed
            val radiusPx = spec.diameterMm / 2f * pxPerMm
            world.addCoin(CoinState(
                id = i,
                specId = denom.specId,
                radiusPx = radiusPx,
                massG = spec.weightG,
                pos = Vec2(
                    (radiusPx + rng.nextFloat() * (world.widthPx - 2 * radiusPx)).coerceAtLeast(radiusPx),
                    -radiusPx * (i + 1)
                ),
                angle = rng.nextFloat() * kotlin.math.PI.toFloat() * 2f
            ))
        }
    }

    fun start() {
        renderThread?.let { if (it.running) return }
    }

    fun pause() = renderThread?.pauseRendering()

    fun resume() = renderThread?.resumeRendering()

    private fun stopRenderThread() {
        renderThread?.running = false
        renderThread?.resumeRendering()
        renderThread?.join()
        renderThread = null
    }

    fun stop() {
        stopRenderThread()
        player.release()
        haptics.release()
        renderer.release()
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        player.load()
        val rt = RenderThread(h, world, renderer, player, haptics, specMap)
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
        stopRenderThread()
    }

    companion object {
        private const val TAG = "CoinRainView"
    }
}
