package com.example.coinrain

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.coinrain.core.CoinRainConfig

class CoinImpactPlayer(private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundId: Int = 0

    fun load() {
        soundId = soundPool.load(context, R.raw.coin_impact, 1)
    }

    fun play(velocityPxPerSec: Float) {
        if (!CoinRainConfig.Sound.ENABLED) return
        if (velocityPxPerSec < CoinRainConfig.Sound.MIN_IMPACT_VELOCITY_FOR_SOUND) return
        val vol = (velocityPxPerSec / 2000f).coerceIn(0f, 1f) * CoinRainConfig.Sound.MASTER_VOLUME
        soundPool.play(soundId, vol, vol, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
