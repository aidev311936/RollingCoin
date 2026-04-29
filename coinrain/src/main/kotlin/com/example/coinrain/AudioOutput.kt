package com.example.coinrain

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue

class AudioOutput(private val sampleRate: Int = 44100) {

    private data class StreamCursor(val samples: ShortArray, var pos: Int = 0)

    private val queue = ConcurrentLinkedQueue<StreamCursor>()
    private val maxStreams = com.example.coinrain.core.CoinRainConfig.Sound.MIXER_MAX_STREAMS

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
        )
        .build()

    private val mixerThread = Thread(::mixLoop, "CoinRainMixer").also {
        it.isDaemon = true
        it.start()
    }
    private val chunkSize = sampleRate / 100  // 10 ms chunks

    @Volatile private var running = true

    fun play(samples: ShortArray) {
        while (queue.size >= maxStreams) queue.poll()
        queue.add(StreamCursor(samples))
    }

    fun release() {
        running = false
        mixerThread.join(500)
        track.stop()
        track.release()
    }

    private fun mixLoop() {
        track.play()
        val chunk = ShortArray(chunkSize)
        while (running) {
            chunk.fill(0)
            val iter = queue.iterator()
            while (iter.hasNext()) {
                val cursor = iter.next()
                var mixed = false
                for (i in chunk.indices) {
                    if (cursor.pos >= cursor.samples.size) { iter.remove(); break }
                    val sum = chunk[i].toInt() + cursor.samples[cursor.pos++].toInt()
                    chunk[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    mixed = true
                }
                if (!mixed) iter.remove()
            }
            track.write(chunk, 0, chunk.size)
        }
    }
}
