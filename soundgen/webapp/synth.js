'use strict';

const DEFAULT_PARAMS = {
    clickDuration:    5,
    clickHighpass:    3000,
    clickVolume:      0.7,
    resonanceFreq1:   1200,
    resonanceFreq2:   2400,
    resonanceMix:     0.6,
    resonanceVolume:  1.0,
    attack:           0,
    decay:            80,
    decayExponential: true,
    lowpassCutoff:    4000,
    noiseAmount:      0.1,
    masterVolume:     0.8,
    normalize:        true,
    sampleRate:       44100,
    bitDepth:         16
};

function buildGraph(params, ctx) {
    const now = ctx.currentTime;
    const attackSec  = params.attack / 1000;
    const decaySec   = params.decay  / 1000;
    const totalDur   = Math.max(attackSec + decaySec + 0.05, 0.2);

    // Output chain: lowpass → masterGain → destination
    const masterGain = ctx.createGain();
    masterGain.gain.value = params.masterVolume;
    const lowpass = ctx.createBiquadFilter();
    lowpass.type = 'lowpass';
    lowpass.frequency.value = params.lowpassCutoff;
    lowpass.Q.value = 0.5;
    lowpass.connect(masterGain);
    masterGain.connect(ctx.destination);

    function applyEnvelope(gainNode, peak, exponential) {
        const g = gainNode.gain;
        if (attackSec > 0.0005) {
            g.setValueAtTime(0, now);
            g.linearRampToValueAtTime(peak, now + attackSec);
        } else {
            g.setValueAtTime(peak, now);
        }
        if (exponential) {
            g.setTargetAtTime(0.0001, now + attackSec, decaySec / 5);
        } else {
            g.linearRampToValueAtTime(0.0001, now + attackSec + decaySec);
        }
    }

    // --- Component 1: Click (short noise burst through highpass) ---
    const clickSamples = Math.ceil(ctx.sampleRate * params.clickDuration / 1000);
    const clickBuf = ctx.createBuffer(1, clickSamples, ctx.sampleRate);
    const clickData = clickBuf.getChannelData(0);
    for (let i = 0; i < clickData.length; i++) clickData[i] = Math.random() * 2 - 1;

    const clickSrc = ctx.createBufferSource();
    clickSrc.buffer = clickBuf;
    const hpf = ctx.createBiquadFilter();
    hpf.type = 'highpass';
    hpf.frequency.value = params.clickHighpass;
    hpf.Q.value = 0.3;
    const clickGain = ctx.createGain();
    clickGain.gain.setValueAtTime(params.clickVolume, now);
    clickGain.gain.exponentialRampToValueAtTime(0.0001, now + params.clickDuration / 1000);

    clickSrc.connect(hpf);
    hpf.connect(clickGain);
    clickGain.connect(lowpass);
    clickSrc.start(now);
    clickSrc.stop(now + params.clickDuration / 1000 + 0.001);

    // --- Component 2: Resonance (two sine oscillators) ---
    const osc1 = ctx.createOscillator();
    osc1.type = 'sine';
    osc1.frequency.value = params.resonanceFreq1;
    const osc2 = ctx.createOscillator();
    osc2.type = 'sine';
    osc2.frequency.value = params.resonanceFreq2;

    const mix1 = ctx.createGain();
    mix1.gain.value = params.resonanceMix;
    const mix2 = ctx.createGain();
    mix2.gain.value = 1 - params.resonanceMix;

    const resEnv = ctx.createGain();
    applyEnvelope(resEnv, params.resonanceVolume, params.decayExponential);

    osc1.connect(mix1); mix1.connect(resEnv);
    osc2.connect(mix2); mix2.connect(resEnv);
    resEnv.connect(lowpass);
    osc1.start(now); osc2.start(now);
    osc1.stop(now + totalDur); osc2.stop(now + totalDur);

    // --- Component 3: Noise tail (plastic character) ---
    const noiseSamples = Math.ceil(ctx.sampleRate * totalDur);
    const noiseBuf = ctx.createBuffer(1, noiseSamples, ctx.sampleRate);
    const noiseData = noiseBuf.getChannelData(0);
    for (let i = 0; i < noiseData.length; i++) noiseData[i] = Math.random() * 2 - 1;

    const noiseSrc = ctx.createBufferSource();
    noiseSrc.buffer = noiseBuf;
    const noiseEnv = ctx.createGain();
    applyEnvelope(noiseEnv, params.noiseAmount, params.decayExponential);

    noiseSrc.connect(noiseEnv);
    noiseEnv.connect(lowpass);
    noiseSrc.start(now);
    noiseSrc.stop(now + totalDur);

    return totalDur;
}

async function renderToAudioBuffer(params) {
    const totalDur = Math.max((params.attack + params.decay) / 1000 + 0.05, 0.2);
    const offlineCtx = new OfflineAudioContext(1, Math.ceil(params.sampleRate * totalDur), params.sampleRate);
    buildGraph(params, offlineCtx);
    return offlineCtx.startRendering();
}

let _previewCtx = null;

function getPreviewContext() {
    if (!_previewCtx || _previewCtx.state === 'closed') {
        _previewCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (_previewCtx.state === 'suspended') _previewCtx.resume();
    return _previewCtx;
}

function previewSound(params) {
    buildGraph(params, getPreviewContext());
}
