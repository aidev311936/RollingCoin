package com.example.coinrain.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main(args: Array<String>) {
    val outDir = File(if (args.isNotEmpty()) args[0] else "build/sound-preview")
    outDir.mkdirs()

    val synth = SoundSynthesizer()
    val testVelocity = 2000f

    for (spec in CoinRainConfig.COINS) {
        val samples = synth.synthesizeImpact(spec, testVelocity) ?: continue
        val file = File(outDir, "impact_${spec.id}.wav")
        writeWav(file, samples, synth.sampleRate)
        println("Wrote ${file.name} (${samples.size} samples)")
    }
}

private fun writeWav(file: File, samples: ShortArray, sampleRate: Int) {
    val dataBytes = samples.size * 2
    val raf = RandomAccessFile(file, "rw")
    raf.setLength(0)

    fun writeInt(v: Int) { raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()) }
    fun writeShort(v: Int) { raf.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()) }

    raf.writeBytes("RIFF")
    writeInt(36 + dataBytes)
    raf.writeBytes("WAVEfmt ")
    writeInt(16)
    writeShort(1)       // PCM
    writeShort(1)       // mono
    writeInt(sampleRate)
    writeInt(sampleRate * 2)  // byte rate
    writeShort(2)       // block align
    writeShort(16)      // bits per sample
    raf.writeBytes("data")
    writeInt(dataBytes)

    val buf = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    for (s in samples) buf.putShort(s)
    raf.write(buf.array())
    raf.close()
}
