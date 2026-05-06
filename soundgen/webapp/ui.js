'use strict';

const PRESETS = {
    plastic_small: {
        label: 'Münze auf Plastik (klein)',
        params: { clickDuration: 4, clickHighpass: 4000, clickVolume: 0.8,
                  resonanceFreq1: 1800, resonanceFreq2: 3600, resonanceMix: 0.6,
                  resonanceVolume: 0.9, attack: 0, decay: 60, decayExponential: true,
                  lowpassCutoff: 5000, noiseAmount: 0.12 }
    },
    plastic_large: {
        label: 'Münze auf Plastik (groß)',
        params: { clickDuration: 6, clickHighpass: 2000, clickVolume: 0.7,
                  resonanceFreq1: 900, resonanceFreq2: 1800, resonanceMix: 0.65,
                  resonanceVolume: 1.0, attack: 0, decay: 120, decayExponential: true,
                  lowpassCutoff: 3500, noiseAmount: 0.15 }
    },
    glass: {
        label: 'Münze auf Glas',
        params: { clickDuration: 3, clickHighpass: 5000, clickVolume: 0.5,
                  resonanceFreq1: 3000, resonanceFreq2: 6000, resonanceMix: 0.5,
                  resonanceVolume: 1.0, attack: 0, decay: 200, decayExponential: true,
                  lowpassCutoff: 8000, noiseAmount: 0.03 }
    },
    wood: {
        label: 'Holz',
        params: { clickDuration: 9, clickHighpass: 700, clickVolume: 0.5,
                  resonanceFreq1: 350, resonanceFreq2: 700, resonanceMix: 0.7,
                  resonanceVolume: 0.85, attack: 2, decay: 55, decayExponential: true,
                  lowpassCutoff: 1800, noiseAmount: 0.28 }
    },
    dull_plastic: {
        label: 'Dumpfes Plastik',
        params: { clickDuration: 11, clickHighpass: 500, clickVolume: 0.35,
                  resonanceFreq1: 550, resonanceFreq2: 1100, resonanceMix: 0.6,
                  resonanceVolume: 0.9, attack: 0, decay: 110, decayExponential: true,
                  lowpassCutoff: 1400, noiseAmount: 0.32 }
    }
};

let currentParams = { ...DEFAULT_PARAMS };
let debounceTimer = null;

function debouncePreview() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => previewSound(currentParams), 150);
}

function getParam(id) {
    return currentParams[id];
}

function setParam(id, value) {
    currentParams[id] = value;
    const el = document.getElementById(id);
    if (el) {
        if (el.type === 'checkbox') el.checked = value;
        else el.value = value;
    }
    const display = document.getElementById(id + '_val');
    if (display) display.textContent = formatValue(id, value);
}

function formatValue(id, value) {
    if (typeof value === 'boolean') return '';
    const units = {
        clickDuration: 'ms', clickHighpass: 'Hz', resonanceFreq1: 'Hz',
        resonanceFreq2: 'Hz', attack: 'ms', decay: 'ms', lowpassCutoff: 'Hz'
    };
    const unit = units[id] || '';
    if (typeof value === 'number') {
        return value % 1 === 0 ? `${value}${unit}` : `${value.toFixed(2)}${unit}`;
    }
    return `${value}`;
}

function bindSlider(id, min, max, step, defaultVal) {
    const slider = document.getElementById(id);
    const display = document.getElementById(id + '_val');
    if (!slider) return;
    slider.min = min;
    slider.max = max;
    slider.step = step;
    slider.value = defaultVal;
    if (display) display.textContent = formatValue(id, defaultVal);
    slider.addEventListener('input', () => {
        const v = parseFloat(slider.value);
        currentParams[id] = v;
        if (display) display.textContent = formatValue(id, v);
        debouncePreview();
    });
}

function bindCheckbox(id, defaultVal) {
    const cb = document.getElementById(id);
    if (!cb) return;
    cb.checked = defaultVal;
    cb.addEventListener('change', () => {
        currentParams[id] = cb.checked;
        debouncePreview();
    });
}

function bindSelect(id, defaultVal) {
    const sel = document.getElementById(id);
    if (!sel) return;
    sel.value = defaultVal;
    sel.addEventListener('change', () => {
        currentParams[id] = parseInt(sel.value, 10);
    });
}

function applyPreset(key) {
    const preset = PRESETS[key];
    if (!preset) return;
    Object.entries(preset.params).forEach(([k, v]) => setParam(k, v));
    debouncePreview();
}

function resetToDefaults() {
    currentParams = { ...DEFAULT_PARAMS };
    Object.entries(DEFAULT_PARAMS).forEach(([k, v]) => {
        const el = document.getElementById(k);
        if (!el) return;
        if (el.type === 'checkbox') el.checked = v;
        else el.value = v;
        const display = document.getElementById(k + '_val');
        if (display) display.textContent = formatValue(k, v);
    });
    debouncePreview();
}

function exportSettings() {
    const json = JSON.stringify(currentParams, null, 2);
    navigator.clipboard.writeText(json).then(
        () => showToast('Settings copied to clipboard'),
        () => { document.getElementById('import_field').value = json; showToast('Clipboard unavailable — see import field'); }
    );
}

function importSettings() {
    const text = document.getElementById('import_field').value.trim();
    if (!text) return;
    try {
        const parsed = JSON.parse(text);
        Object.entries(parsed).forEach(([k, v]) => {
            if (k in DEFAULT_PARAMS) setParam(k, v);
        });
        debouncePreview();
        showToast('Settings imported');
    } catch {
        showToast('Invalid JSON');
    }
}

function showToast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('visible');
    setTimeout(() => t.classList.remove('visible'), 2000);
}

document.addEventListener('DOMContentLoaded', () => {
    // Click section
    bindSlider('clickDuration',  1,   20,   1,    DEFAULT_PARAMS.clickDuration);
    bindSlider('clickHighpass',  500, 8000, 100,  DEFAULT_PARAMS.clickHighpass);
    bindSlider('clickVolume',    0,   1,    0.05, DEFAULT_PARAMS.clickVolume);

    // Resonance section
    bindSlider('resonanceFreq1',   200,  4000, 10,   DEFAULT_PARAMS.resonanceFreq1);
    bindSlider('resonanceFreq2',   200,  6000, 10,   DEFAULT_PARAMS.resonanceFreq2);
    bindSlider('resonanceMix',     0,    1,    0.05, DEFAULT_PARAMS.resonanceMix);
    bindSlider('resonanceVolume',  0,    1,    0.05, DEFAULT_PARAMS.resonanceVolume);

    // Envelope section
    bindSlider('attack',  0,   10,  1,    DEFAULT_PARAMS.attack);
    bindSlider('decay',   20,  500, 5,    DEFAULT_PARAMS.decay);
    bindCheckbox('decayExponential', DEFAULT_PARAMS.decayExponential);

    // Plastic character
    bindSlider('lowpassCutoff', 500,  10000, 100,  DEFAULT_PARAMS.lowpassCutoff);
    bindSlider('noiseAmount',   0,    0.5,   0.01, DEFAULT_PARAMS.noiseAmount);

    // Output
    bindSlider('masterVolume', 0, 1, 0.05, DEFAULT_PARAMS.masterVolume);
    bindCheckbox('normalize', DEFAULT_PARAMS.normalize);
    bindSelect('sampleRate', DEFAULT_PARAMS.sampleRate);
    document.querySelectorAll('input[name="bitDepth"]').forEach(r => {
        r.checked = parseInt(r.value) === DEFAULT_PARAMS.bitDepth;
        r.addEventListener('change', () => {
            currentParams.bitDepth = parseInt(r.value);
        });
    });

    // Buttons
    document.getElementById('btn_play').addEventListener('click', () => previewSound(currentParams));
    document.getElementById('btn_download').addEventListener('click', () => downloadWav(currentParams));
    document.getElementById('btn_reset').addEventListener('click', resetToDefaults);
    document.getElementById('btn_export').addEventListener('click', exportSettings);
    document.getElementById('btn_import').addEventListener('click', importSettings);

    // Presets
    Object.keys(PRESETS).forEach(key => {
        const btn = document.getElementById('preset_' + key);
        if (btn) btn.addEventListener('click', () => applyPreset(key));
    });
});
