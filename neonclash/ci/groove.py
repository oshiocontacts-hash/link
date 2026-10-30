"""Neon Clash の動画用 BGM を合成する（numpy + scipy、外部音源なし）。

- テンポ・キー : 128 BPM（mod のビート既定値と同じ）、F マイナー
- コード進行   : | Fm9 | D♭maj9 | A♭maj7 | E♭6/9 |（i–VI–III–VII を 4 小節でループ）
- リズム       : 四つ打ちキック、2・4 拍のクラップ、16 分ハット（スウィング 56%）、裏拍のオープンハット、
                 シンコペーションするベース、裏拍のコードスタブ。キックで他のパートを沈めるサイドチェイン
- 構成         : Intro → Groove → Build → Drop → Breakdown → Drop 2 → … → Outro（動画の長さに合わせて伸縮）
- 楽器         : キック / クラップ / クローズド・オープンハット / スネアロール / シンセベース+サブ /
                 コードスタブ / スーパーソウ・パッド / アルペジオ（ピンポンディレイ）/ リード / ライザー / インパクト

ゲーム内のビートと揃えたいときは --beat-json に DemoDriver が書く demo_beat.json を渡す
（録画開始から最初の拍までの ms と、その拍番号。拍番号 % 4 == 0 を小節の頭にする）。

使い方: python3 groove.py --duration 120 --out bgm.wav [--offset 4.0 | --beat-json demo_beat.json]
"""
import argparse
import json
import math
import wave

import numpy as np
from scipy import signal

SR = 44100
BPM = 128.0
BEAT = 60.0 / BPM
BAR = BEAT * 4
SIX = BEAT / 4            # 16 分
SWING = 0.56              # 裏の 16 分を少し遅らせる
RNG = np.random.default_rng(20261030)

# ------------------------------------------------------------------ 和声

def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)

#            名前         パッド/スタブのボイシング      ベースのルート  アルペジオ
CHORDS = [
    ("Fm9",    [53, 56, 60, 63, 67], 41, [65, 68, 72, 75, 79, 75, 72, 68]),
    ("Dbmaj9", [49, 53, 56, 60, 63], 37, [61, 65, 68, 72, 75, 72, 68, 65]),
    ("Abmaj7", [56, 60, 63, 67],     44, [68, 72, 75, 79, 80, 79, 75, 72]),
    ("Eb6/9",  [51, 55, 58, 60, 65], 39, [63, 67, 70, 72, 77, 72, 70, 67]),
]

# ベース：16 分のステップ (step, 半音, 長さ[16分])。1 拍目はキックと重ねず、少しずらしてグルーヴを作る
BASS_PATTERN = [(0, 0, 1), (3, 0, 1), (6, 12, 1), (7, 0, 1), (10, 0, 2), (12, 7, 1), (14, 10, 1), (15, 12, 1)]
# リード：Drop の 4 小節のフレーズ (小節, 8分の位置, MIDI, 長さ[8分])
LEAD = [
    (0, 0, 72, 2), (0, 3, 68, 1), (0, 4, 72, 1), (0, 5, 75, 2), (0, 7, 72, 1),
    (1, 0, 70, 2), (1, 3, 68, 1), (1, 4, 65, 1), (1, 5, 68, 2), (1, 7, 70, 1),
    (2, 0, 75, 2), (2, 3, 72, 1), (2, 4, 75, 1), (2, 5, 79, 3),
    (3, 0, 77, 1), (3, 1, 75, 1), (3, 2, 72, 2), (3, 4, 70, 1), (3, 5, 72, 3),
]

# ------------------------------------------------------------------ 構成

def arrangement(n_bars):
    """小節数に合わせて [(セクション名, 小節数)] を作る。最初は Intro、最後は Outro。"""
    intro, outro = min(4, n_bars), 4
    body = max(0, n_bars - intro - outro)
    cycle = [("groove", 8), ("build", 4), ("drop", 8), ("break", 4), ("build", 2), ("drop2", 8)]
    out = [("intro", intro)]
    i = 0
    while body > 0:
        name, length = cycle[i % len(cycle)]
        if i >= len(cycle):
            name = {"groove": "groove2"}.get(name, name)
        take = min(length, body)
        out.append((name, take))
        body -= take
        i += 1
    if n_bars > intro:
        out.append(("outro", min(outro, n_bars - intro)))
    return out


LAYERS = {
    #          kick clap hat  ohat bass stab pad  arp  lead roll riser
    "intro":   (0,   0,   1,   0,   0,   0,   1,   0,   0,   0,   0),
    "groove":  (1,   1,   1,   1,   1,   1,   0,   0,   0,   0,   0),
    "groove2": (1,   1,   1,   1,   1,   1,   1,   0,   0,   0,   0),
    "build":   (1,   0,   1,   0,   1,   1,   1,   1,   0,   1,   1),
    "drop":    (1,   1,   1,   1,   1,   1,   1,   1,   1,   0,   0),
    "break":   (0,   0,   1,   0,   0,   0,   1,   1,   0,   0,   0),
    "drop2":   (1,   1,   1,   1,   1,   1,   1,   1,   1,   0,   0),
    "outro":   (1,   0,   1,   0,   1,   0,   1,   0,   0,   0,   0),
}
NAMES = ["kick", "clap", "hat", "ohat", "bass", "stab", "pad", "arp", "lead", "roll", "riser"]

# ------------------------------------------------------------------ 合成の部品

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def lowpass(x, cutoff, order=2):
    sos = signal.butter(order, min(cutoff, SR * 0.45), "low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, cutoff, order=2):
    sos = signal.butter(order, cutoff, "high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], "band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def saw(f, t, phase=0.0):
    return 2.0 * ((f * t + phase) % 1.0) - 1.0


def pulse(f, t, width=0.5):
    return np.where((f * t) % 1.0 < width, 1.0, -1.0)


def adsr(n, a, d, s, r, hold):
    """n サンプルのエンベロープ（秒指定）。hold 秒で鍵盤を離す。"""
    t = np.arange(n) / SR
    env = np.where(t < a, t / max(a, 1e-6), 1.0)
    dec = np.clip((t - a) / max(d, 1e-6), 0, 1)
    env = np.where(t >= a, 1.0 - (1.0 - s) * dec, env)
    rel = np.clip((t - hold) / max(r, 1e-6), 0, 1)
    return env * np.where(t > hold, 1.0 - rel, 1.0)


def place(buf, start_sec, sound, gain=1.0, pan=0.0):
    """ステレオバッファ buf (2, N) に sound を置く。pan: -1 左 … 1 右。"""
    i = int(round(start_sec * SR))
    if i >= buf.shape[1] or i + len(sound) <= 0:
        return
    s = sound
    if i < 0:
        s, i = s[-i:], 0
    s = s[: buf.shape[1] - i]
    left = math.cos((pan + 1) * math.pi / 4)
    right = math.sin((pan + 1) * math.pi / 4)
    buf[0, i:i + len(s)] += s * gain * left * math.sqrt(2)
    buf[1, i:i + len(s)] += s * gain * right * math.sqrt(2)


# ------------------------------------------------------------------ 楽器

def kick():
    t = t_axis(0.42)
    f = 46 + 120 * np.exp(-t * 32)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    click = highpass(RNG.standard_normal(len(t)), 2000) * np.exp(-t * 400) * 0.35
    return np.tanh((body + click) * 1.6)


def clap():
    t = t_axis(0.5)
    noise = bandpass(RNG.standard_normal(len(t)), 900, 3200)
    env = np.zeros_like(t)
    for k, off in enumerate((0.0, 0.011, 0.022)):
        env += np.where(t >= off, np.exp(-(t - off) * (180 if k < 2 else 22)), 0)
    return noise * env * 0.6


def hat(open_=False):
    t = t_axis(0.35 if open_ else 0.08)
    noise = highpass(RNG.standard_normal(len(t)), 7000, order=4)
    metal = sum(pulse(f, t) for f in (3150, 4270, 5580, 6990)) * 0.08
    return (noise + highpass(metal, 6000)) * np.exp(-t * (9 if open_ else 55))


def snare():
    t = t_axis(0.2)
    tone = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30)
    noise = bandpass(RNG.standard_normal(len(t)), 1500, 8000) * np.exp(-t * 22)
    return tone * 0.5 + noise * 0.7


def bass_note(midi, length):
    t = t_axis(length + 0.05)
    f = hz(midi)
    raw = saw(f, t) * 0.6 + saw(f * 1.005, t) * 0.4
    env_f = 300 + 1600 * np.exp(-t * 14)               # フィルターエンベロープで「ブン」と鳴らす
    # 区間ごとにカットオフを変える（短いブロックで近似）
    out = np.zeros_like(raw)
    block = 512
    zi = None
    for i in range(0, len(raw), block):
        sos = signal.butter(2, env_f[i], "low", fs=SR, output="sos")
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        out[i:i + block], zi = signal.sosfilt(sos, raw[i:i + block], zi=zi)
    sub = np.sin(2 * np.pi * f / 2 * t) * 0.7
    env = adsr(len(t), 0.003, 0.12, 0.7, 0.04, length)
    return np.tanh((out + sub) * 1.3) * env


def supersaw(midis, length, voices=5, detune=0.012, cutoff=2200, attack=0.01, release=0.08):
    t = t_axis(length + release)
    x = np.zeros_like(t)
    for m in midis:
        f = hz(m)
        for v in range(voices):
            d = 1 + detune * (v - (voices - 1) / 2) / ((voices - 1) / 2 or 1)
            x += saw(f * d, t, RNG.random())
    x /= voices * len(midis)
    return lowpass(x, cutoff) * adsr(len(t), attack, 0.2, 0.8, release, length)


def stab(midis):
    s = supersaw(midis, 0.13, voices=3, detune=0.01, cutoff=2800, attack=0.002, release=0.06)
    return s * 2.2


def pad(midis, length):
    return supersaw(midis, length, voices=7, detune=0.018, cutoff=1500, attack=0.6, release=0.8) * 1.6


def pluck(midi):
    t = t_axis(0.3)
    f = hz(midi)
    x = pulse(f, t, 0.3) * 0.5 + saw(f * 2, t) * 0.2
    return lowpass(x, 3500) * np.exp(-t * 16)


def lead_note(midi, length):
    t = t_axis(length + 0.15)
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.5 * t) * np.clip(t / 0.25, 0, 1)
    f = hz(midi) * vib
    ph = np.cumsum(f) / SR
    x = (2 * (ph % 1) - 1) * 0.5 + np.where(ph * 1.003 % 1 < 0.5, 1, -1) * 0.3
    return lowpass(x, 4200) * adsr(len(t), 0.01, 0.15, 0.75, 0.12, length)


def riser(length):
    t = t_axis(length)
    p = t / length
    noise = RNG.standard_normal(len(t))
    out = np.zeros_like(t)
    block = 1024
    for i in range(0, len(t), block):
        c = 400 + 9000 * p[i] ** 2
        out[i:i + block] = bandpass(noise[max(0, i - 2048):i + block], c, c * 1.6)[-len(out[i:i + block]):]
    sweep = saw(hz(48) * (1 + 3 * p ** 2), t) * 0.15
    return (out * 0.5 + lowpass(sweep, 2000)) * p ** 1.5


def impact():
    t = t_axis(2.5)
    boom = np.sin(2 * np.pi * np.cumsum(60 * np.exp(-t * 3) + 30) / SR) * np.exp(-t * 2.2)
    air = lowpass(RNG.standard_normal(len(t)), 5000) * np.exp(-t * 2.5) * 0.25
    return boom * 0.8 + air


# ------------------------------------------------------------------ エフェクト

def _comb(x, d, g):
    """y[n] = x[n] + g*y[n-d]。遅延 d ごとのブロックでベクトル計算する。"""
    y = np.array(x, dtype=float)
    for i in range(d, len(y), d):
        j = min(i + d, len(y))
        y[i:j] += g * y[i - d:j - d]
    return y


def _allpass(x, d, g):
    """y[n] = -g*x[n] + x[n-d] + g*y[n-d]"""
    y = -g * np.array(x, dtype=float)
    y[d:] += x[:-d]
    for i in range(d, len(y), d):
        j = min(i + d, len(y))
        y[i:j] += g * y[i - d:j - d]
    return y


def reverb(x, wet=0.25):
    """Schroeder 型（コム 4 本 + オールパス 2 本）。"""
    out = np.zeros_like(x)
    for delay, g in ((1557, 0.84), (1617, 0.83), (1491, 0.82), (1422, 0.81)):
        out += _comb(x, delay, g)
    for delay, g in ((225, 0.5), (556, 0.5)):
        out = _allpass(out, delay, g)
    return x * (1 - wet) + lowpass(out, 6000) * wet * 0.35


def pingpong(st, delay_sec, feedback=0.35, wet=0.35):
    d = int(delay_sec * SR)
    left, right = st[0].copy(), st[1].copy()
    out = st.copy()
    for k in range(1, 5):
        g = wet * feedback ** (k - 1)
        src = right if k % 2 else left
        out[k % 2, d * k:] += src[: len(src) - d * k] * g
    return out


# ------------------------------------------------------------------ 本体

def render(duration, offset):
    """offset: 動画の何秒目に小節の頭（拍番号 % 4 == 0）が来るか。"""
    lead_in = math.ceil(max(offset, 0) / BAR) * BAR      # 動画の 0 秒より前から始めて、頭を offset に揃える
    start = offset - lead_in                              # <= 0
    n_bars = int(math.ceil((duration - start) / BAR))
    plan = arrangement(n_bars)
    total = n_bars * BAR + 3.0
    n = int(total * SR)
    bus = {k: np.zeros((2, n)) for k in ("drums", "bass", "music", "lead", "fx")}
    side = np.ones(n)                                     # サイドチェインのゲイン

    k_snd, c_snd, h_snd, oh_snd, sn_snd = kick(), clap(), hat(), hat(True), snare()
    bar = 0
    print(f"{BPM:.0f} BPM, F minor, bars={n_bars}, start={start:.3f}s (first downbeat at {offset:.3f}s)")
    for name, length in plan:
        layer = dict(zip(NAMES, LAYERS[name]))
        print(f"  bar {bar:3d}-{bar + length - 1:3d}  {name:8s} " + " ".join(k for k, v in layer.items() if v))
        sec_start = bar * BAR
        if name in ("drop", "drop2"):
            place(bus["fx"], sec_start, impact(), 0.55)
        if layer["riser"]:
            place(bus["fx"], sec_start, riser(length * BAR), 0.35)
        for b in range(length):
            chord_name, voicing, root, arp_notes = CHORDS[(bar + b) % 4]
            t0 = (bar + b) * BAR
            last_bar_of_build = name == "build" and b == length - 1
            for step in range(16):
                ts = t0 + step * SIX + (SWING - 0.5) * 2 * SIX * (step % 2)
                if layer["kick"] and step % 4 == 0 and not (last_bar_of_build and step >= 8):
                    place(bus["drums"], ts, k_snd, 0.55)
                    i = int(ts * SR)
                    duck = 1 - 0.55 * np.exp(-np.arange(int(BEAT * SR)) / SR * 9)
                    side[i:i + len(duck)] = np.minimum(side[i:i + len(duck)], duck[: len(side) - i])
                if layer["clap"] and step in (4, 12):
                    place(bus["drums"], ts, c_snd, 2.0, pan=0.05)
                if layer["hat"]:
                    vel = (0.32 if step % 4 == 2 else 0.17) * (0.5 if name in ("intro", "break") else 1)
                    if not (layer["ohat"] and step % 4 == 2):
                        place(bus["drums"], ts, h_snd, vel * (0.8 + 0.4 * RNG.random()), pan=0.3)
                if layer["ohat"] and step % 4 == 2:
                    place(bus["drums"], ts, oh_snd, 0.22, pan=-0.25)
                if layer["stab"] and step in (2, 6, 10, 14) and name not in ("outro",):
                    place(bus["music"], ts, stab(voicing), 0.42 if name == "groove" else 0.55, pan=-0.45 if step in (2, 10) else 0.45)
                if layer["arp"]:
                    place(bus["lead"], ts, pluck(arp_notes[step % 8]), 0.28, pan=0.0)
            if layer["roll"]:
                # スネアロール：8 分 → 16 分 → 32 分と細かく、だんだん大きく
                frac = b / max(1, length)
                div = 2 if frac < 0.5 else (4 if frac < 0.75 else 8)
                for k in range(4 * div):
                    vol = 0.12 + 0.35 * ((b + k / (4 * div)) / length)
                    place(bus["drums"], t0 + k * BEAT / div, sn_snd, vol, pan=0.0)
            if layer["bass"]:
                for step, semis, ln in BASS_PATTERN:
                    if name == "outro" and step > 8:
                        continue
                    ts = t0 + step * SIX + (SWING - 0.5) * 2 * SIX * (step % 2)
                    place(bus["bass"], ts, bass_note(root + semis, ln * SIX * 0.9), 0.33)
            if layer["pad"]:
                # 左右で別々に鳴らして広げる（位相・デチューンが乱数で変わる）
                place(bus["music"], t0, pad(voicing, BAR * 0.98), 0.58, pan=-0.75)
                place(bus["music"], t0, pad(voicing, BAR * 0.98), 0.58, pan=0.75)
            if layer["lead"]:
                for lb, pos, midi, ln in LEAD:
                    if lb == (bar + b) % 4:
                        place(bus["lead"], t0 + pos * BEAT / 2, lead_note(midi, ln * BEAT / 2 * 0.95), 0.42, pan=0.1)
        bar += length

    # ミックス
    music = bus["music"] * side
    music = np.stack([reverb(music[0], 0.3), reverb(music[1], 0.3)])
    lead = pingpong(bus["lead"] * side, SIX * 3)
    lead = np.stack([reverb(lead[0], 0.2), reverb(lead[1], 0.2)])
    bass = bus["bass"] * side
    drums = bus["drums"]
    claps = np.stack([reverb(drums[0], 0.12), reverb(drums[1], 0.12)])
    fx = np.stack([reverb(bus["fx"][0], 0.4), reverb(bus["fx"][1], 0.4)])
    mix = claps + bass + music * 0.9 + lead + fx

    # 動画の 0 秒に合わせて切り出し、最後はフェードアウト
    a = int(-start * SR)
    mix = mix[:, a:a + int(duration * SR)]
    fade = int(min(3.0, duration / 4) * SR)
    mix[:, -fade:] *= np.linspace(1, 0, fade) ** 1.5
    fade_in = int(0.05 * SR)
    mix[:, :fade_in] *= np.linspace(0, 1, fade_in)
    # マスター：一番大きいところで軽く飽和するくらいに揃えてからソフトクリップ（セクション間の強弱は残す）
    mix = mix / max(1e-9, np.percentile(np.abs(mix), 99.9))
    mix = np.tanh(mix * 0.9)
    mix *= 10 ** (-1 / 20) / max(1e-9, np.max(np.abs(mix)))
    return mix


def write_wav(path, st):
    data = (np.clip(st.T, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--duration", type=float, required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--offset", type=float, default=0.0, help="動画で最初の小節の頭が来る秒")
    ap.add_argument("--beat-json", help="DemoDriver の demo_beat.json（あれば --offset より優先）")
    args = ap.parse_args()
    offset = args.offset
    if args.beat_json:
        try:
            info = json.load(open(args.beat_json))
            if info.get("enabled", True):
                first = info["first_beat_ms"] / 1000.0
                to_bar = (-int(info["first_beat_index"])) % 4
                offset = first + to_bar * BEAT
                print(f"aligned to game beat: first beat {first:.3f}s (index {info['first_beat_index']}), downbeat {offset:.3f}s")
        except (OSError, KeyError, ValueError) as e:
            print(f"beat json not used: {e}")
    write_wav(args.out, render(args.duration, offset))
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
