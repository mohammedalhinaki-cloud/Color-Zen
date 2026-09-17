#!/usr/bin/env python3
"""Synthesise Color Zen's six sound effects as raw WAV files.

The game deliberately contains NO music - only short, calm sound effects
(pour, select, complete, click, hint, bubble) that the player can mute in
Settings. They are generated here with numpy and checked into
app/src/main/res/raw/ so the build needs no binary assets from elsewhere.

Design goals
------------
* Soft and rounded: low-pass everything, never a harsh transient.
* Short: 0.1-1.0 s so overlapping pours stay pleasant.
* Mono 44.1 kHz 16-bit PCM (SoundPool friendly, tiny APK footprint).
* Peak-normalised to about -1 dBFS with 5 ms fades to avoid click-onsets.

Usage: python3 tools/generate_sfx.py
"""

from __future__ import annotations

import os
import wave

import numpy as np

SR = 44100
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "src", "main", "res", "raw")


def t(seconds: float) -> np.ndarray:
    return np.arange(int(seconds * SR)) / SR


def fade(sig: np.ndarray, ms: float = 5.0) -> np.ndarray:
    n = max(1, int(ms / 1000.0 * SR))
    n = min(n, len(sig) // 4)
    ramp = np.linspace(0.0, 1.0, n)
    sig = sig.copy()
    sig[:n] *= ramp
    sig[-n:] *= ramp[::-1]
    return sig


def lowpass(sig: np.ndarray, cutoff: float, q: float = 0.7) -> np.ndarray:
    """Simple 2-pole resonant low-pass implemented as cascaded one-poles."""
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    out = np.empty_like(sig)
    y1 = y2 = 0.0
    for i in range(len(sig)):
        y1 = (1 - a) * sig[i] + a * y1
        y2 = (1 - a) * y1 + a * y2
        out[i] = y2
    return out


def sweep(start: float, end: float, seconds: float, kind: str = "sine") -> np.ndarray:
    """Frequency-swept oscillator (phase-integrated so the sweep is smooth)."""
    tt = t(seconds)
    f = np.linspace(start, end, len(tt))
    phase = 2.0 * np.pi * np.cumsum(f) / SR
    if kind == "sine":
        return np.sin(phase)
    if kind == "triangle":
        return 2.0 / np.pi * np.arcsin(np.sin(phase))
    raise ValueError(kind)


def env(seconds: float, attack: float, decay: float, sustain: float = 0.0) -> np.ndarray:
    tt = t(seconds)
    e = np.ones_like(tt) * sustain
    a = np.clip(tt / max(attack, 1e-4), 0, 1)
    d = np.exp(-np.clip(tt - attack, 0, None) / decay)
    e = a * d
    return e


def write_wav(name: str, sig: np.ndarray, target_rms: float = 0.16) -> None:
    """Normalise perceived loudness (RMS), then cap the peak below clipping."""
    sig = fade(sig)
    rms = float(np.sqrt(np.mean(sig ** 2))) or 1.0
    sig = sig * (target_rms / rms)
    peak = float(np.max(np.abs(sig)))
    if peak > 0.89:
        sig = sig * (0.89 / peak)
        peak = 0.89
    pcm = np.int16(np.clip(sig, -1, 1) * 32767)
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, f"{name}.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    rms = float(np.sqrt(np.mean(sig ** 2)))
    print(f"{name}.wav  {len(sig)/SR:5.2f}s  peak {peak:.2f}  rms {rms:.3f}")


# ---------------------------------------------------------------------------
# The six effects
# ---------------------------------------------------------------------------
def sfx_pour() -> np.ndarray:
    """Gentle liquid pour: low-passed noise sweeping down + slow burble LFO."""
    dur = 0.62
    tt = t(dur)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(len(tt))
    cutoff = np.linspace(2400, 500, len(tt))
    # time-varying one-pole low pass on the noise
    out = np.empty_like(noise)
    y = 0.0
    for i in range(len(noise)):
        a = np.exp(-2.0 * np.pi * cutoff[i] / SR)
        y = (1 - a) * noise[i] + a * y
        out[i] = y
    burble = 0.72 + 0.28 * np.sin(2 * np.pi * 9.0 * tt + 0.6 * np.sin(2 * np.pi * 2.3 * tt))
    amp = env(dur, 0.05, 0.30, 0.0) ** 1.3
    body = out * burble * amp
    # soft water "glug" resonance underneath
    glug = sweep(320, 150, dur, "sine") * amp * 0.25
    return body * 0.9 + glug


def sfx_select() -> np.ndarray:
    """Soft 'pop' when a bottle is picked up or put down."""
    dur = 0.14
    tone = sweep(430, 720, dur, "sine")
    amp = env(dur, 0.006, 0.045)
    return tone * amp + 0.15 * sweep(900, 1300, dur, "sine") * env(dur, 0.004, 0.02)


def sfx_complete() -> np.ndarray:
    """Rising two-note chime for a finished level: warm, bell-like, no fanfare."""
    dur = 1.0
    tt = t(dur)
    notes = [(0.00, 587.33, 0.55), (0.16, 880.00, 0.50)]
    sig = np.zeros(len(tt))
    for start, freq, gain in notes:
        i0 = int(start * SR)
        n = len(tt) - i0
        if n <= 0:
            continue
        tl = tt[:n]
        partial = (np.sin(2 * np.pi * freq * tl)
                   + 0.35 * np.sin(2 * np.pi * freq * 2.01 * tl)
                   + 0.12 * np.sin(2 * np.pi * freq * 3.02 * tl))
        a = np.clip(tl / 0.012, 0, 1) * np.exp(-tl / 0.42)
        sig[i0:] += partial * a * gain
    # airy shimmer on top
    sig += 0.06 * np.sin(2 * np.pi * 1760.0 * tt) * np.exp(-tt / 0.3) * np.clip(tt / 0.2, 0, 1)
    return lowpass(sig, 5200)


def sfx_click() -> np.ndarray:
    """Dry, quiet UI click for buttons."""
    dur = 0.09
    rng = np.random.default_rng(3)
    tick = rng.standard_normal(len(t(dur))) * env(dur, 0.001, 0.008)
    tone = sweep(1250, 820, dur, "sine") * env(dur, 0.002, 0.030)
    return lowpass(tick, 3800) * 0.5 + tone * 0.8


def sfx_hint() -> np.ndarray:
    """Two-tone bell when a hint is revealed."""
    dur = 0.7
    tt = t(dur)
    sig = np.zeros(len(tt))
    for start, freq, gain in [(0.0, 987.77, 0.5), (0.13, 1318.51, 0.45)]:
        i0 = int(start * SR)
        n = len(tt) - i0
        tl = tt[:n]
        partial = (np.sin(2 * np.pi * freq * tl)
                   + 0.28 * np.sin(2 * np.pi * freq * 2.0 * tl))
        sig[i0:] += partial * np.clip(tl / 0.008, 0, 1) * np.exp(-tl / 0.26) * gain
    return lowpass(sig, 6000)


def sfx_bubble() -> np.ndarray:
    """Three rising blips - bubbles escaping the bottle."""
    dur = 0.42
    tt = t(dur)
    sig = np.zeros(len(tt))
    for i, (start, f0, f1) in enumerate([(0.0, 300, 430), (0.12, 400, 560), (0.24, 520, 720)]):
        i0 = int(start * SR)
        n = len(tt) - i0
        tl = tt[:n]
        f = np.linspace(f0, f1, n)
        phase = 2 * np.pi * np.cumsum(f) / SR
        sig[i0:] += np.sin(phase) * np.clip(tl / 0.006, 0, 1) * np.exp(-tl / 0.05) * 0.55
    return lowpass(sig, 4200)


def main() -> None:
    write_wav("sfx_pour", sfx_pour(), target_rms=0.17)
    write_wav("sfx_select", sfx_select(), target_rms=0.16)
    write_wav("sfx_complete", sfx_complete(), target_rms=0.15)
    write_wav("sfx_click", sfx_click(), target_rms=0.13)
    write_wav("sfx_hint", sfx_hint(), target_rms=0.14)
    write_wav("sfx_bubble", sfx_bubble(), target_rms=0.14)
    print(f"-> {os.path.relpath(OUT_DIR)}")


if __name__ == "__main__":
    main()
