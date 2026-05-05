'use strict';

function audioBufferToWav(audioBuffer, bitDepth, normalize) {
    const samples = audioBuffer.getChannelData(0);
    let data = new Float32Array(samples);

    if (normalize) {
        let peak = 0;
        for (let i = 0; i < data.length; i++) peak = Math.max(peak, Math.abs(data[i]));
        if (peak > 0.0001) {
            const scale = 0.98 / peak;
            for (let i = 0; i < data.length; i++) data[i] *= scale;
        }
    }

    const sampleRate  = audioBuffer.sampleRate;
    const numSamples  = data.length;
    const bytesPerSample = bitDepth / 8;
    const dataSize    = numSamples * bytesPerSample;
    const buffer      = new ArrayBuffer(44 + dataSize);
    const view        = new DataView(buffer);

    function writeStr(offset, str) {
        for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i));
    }

    writeStr(0, 'RIFF');
    view.setUint32(4, 36 + dataSize, true);
    writeStr(8, 'WAVE');
    writeStr(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);             // PCM
    view.setUint16(22, 1, true);             // mono
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * bytesPerSample, true);
    view.setUint16(32, bytesPerSample, true);
    view.setUint16(34, bitDepth, true);
    writeStr(36, 'data');
    view.setUint32(40, dataSize, true);

    if (bitDepth === 16) {
        for (let i = 0; i < numSamples; i++) {
            const s = Math.max(-1, Math.min(1, data[i]));
            view.setInt16(44 + i * 2, s < 0 ? s * 32768 : s * 32767, true);
        }
    } else { // 24-bit
        for (let i = 0; i < numSamples; i++) {
            const s = Math.max(-1, Math.min(1, data[i]));
            const val = s < 0 ? s * 8388608 : s * 8388607;
            const intVal = Math.round(val);
            const offset = 44 + i * 3;
            view.setUint8(offset,     intVal & 0xFF);
            view.setUint8(offset + 1, (intVal >> 8) & 0xFF);
            view.setUint8(offset + 2, (intVal >> 16) & 0xFF);
        }
    }

    return new Blob([buffer], { type: 'audio/wav' });
}

async function downloadWav(params) {
    const audioBuffer = await renderToAudioBuffer(params);
    const blob = audioBufferToWav(audioBuffer, params.bitDepth, params.normalize);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'coin_impact.wav';
    a.click();
    URL.revokeObjectURL(url);
}
