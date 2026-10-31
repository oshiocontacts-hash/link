"""Neon Clash の効果音と試合の BGM を合成して ogg に書き出す（numpy + scipy + ffmpeg、外部音源なし）。

- 音程のある音は F マイナーの音に揃えている（BGM に重なっても濁らない）。楽器は ci/groove.py のものを使う
- BGM は 128 BPM・16 小節（ちょうど 30 秒）のループで、リズムの層（music_base）とメロディの層（music_drive）に分かれる。
  ゲームは 2 つを同時に鳴らし、スタイルランクでメロディの層の音量を変える
- 効果音はモノラル（Minecraft はモノラルの音にだけ距離減衰と定位を掛ける）

使い方: python tools/sfx/make_sfx.py [--only hit,kill]
出力先: mod/src/main/resources/assets/neonclash/sounds/
"""
import argparse
import pathlib
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci"))
import groove as G  # noqa: E402

SR = G.SR
OUT = ROOT / "mod/src/main/resources/assets/neonclash/sounds"
R = np.random.default_rng(20261031)

# F マイナーの音（Hz）
F1, F3, C4, F4, AB4, C5, EB5, F5, AB5, C6, EB6, F6, AB6, C7, F7, C8 = (
    43.65, 174.61, 261.63, 349.23, 415.30, 523.25, 622.25, 698.46, 830.61, 1046.50, 1244.51, 1396.91, 1661.22, 2093.00, 2793.83, 4186.01)


def axis(sec):
    return np.arange(int(sec * SR)) / SR


def noise(t):
    return R.standard_normal(len(t))


def decay(t, rate, start=0.0):
    return np.where(t >= start, np.exp(-(t - start) * rate), 0.0)


def sweep(f0, f1, t, seconds=None):
    """f0 から f1 へ指数的に動く周波数の列。"""
    s = seconds or t[-1]
    return f0 * (f1 / f0) ** np.clip(t / s, 0, 1)


def sine(freq, t):
    """周波数の列（または定数）から正弦波を作る。"""
    f = np.broadcast_to(freq, t.shape)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def saw_of(freq, t):
    f = np.broadcast_to(freq, t.shape)
    return 2 * (np.cumsum(f) / SR % 1.0) - 1


def fm_bell(f, t, ratio=3.01, index=2.2, rate=6.0):
    mod = np.sin(2 * np.pi * f * ratio * t) * index * np.exp(-t * rate * 1.6)
    return np.sin(2 * np.pi * f * t + mod) * np.exp(-t * rate)


def moving_band(x, centers, q=1.5, block=512):
    """中心周波数が動くバンドパス（短いブロックで近似）。"""
    out = np.zeros_like(x)
    for i in range(0, len(x), block):
        c = float(centers[min(i, len(centers) - 1)])
        c = min(max(c, 60.0), SR * 0.42)
        chunk = G.bandpass(x[max(0, i - 2048):i + block], c / q, min(c * q, SR * 0.45))
        out[i:i + block] = chunk[-len(out[i:i + block]):]
    return out


def fade_tail(x, ms=8):
    n = min(len(x), int(SR * ms / 1000))
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n)
    return x


def fit(x, peak_db):
    """ピークを揃える。"""
    return fade_tail(x) * 10 ** (peak_db / 20) / max(1e-9, np.max(np.abs(x)))


# ------------------------------------------------------------------ 銃

def shot_body(t):
    zap = G.lowpass(saw_of(sweep(1500, 130, t, 0.09), t), 5200) * decay(t, 26)
    laser = sine(sweep(2700, 320, t, 0.07), t) * decay(t, 32)
    sub = sine(sweep(190, 52, t, 0.06), t) * decay(t, 17)
    click = G.highpass(noise(t), 3000) * decay(t, 420) * 0.5
    return np.tanh((zap * 0.6 + laser * 0.55 + sub * 0.9 + click) * 1.5)


def shot():
    return fit(shot_body(axis(0.3)), -3)


def shot_beat():
    """ビートに乗った射撃：同じ銃声に F の鈴を重ねる。"""
    t = axis(0.6)
    bells = fm_bell(F6, t, rate=7) + fm_bell(C7, t, rate=8) * 0.6 + fm_bell(F7, t, rate=10) * 0.3
    return fit(shot_body(t) + bells * 0.42, -2)


def impact():
    t = axis(0.17)
    x = G.bandpass(noise(t), 1800, 6500) * decay(t, 70)
    x = x + sine(sweep(2600, 480, t, 0.05), t) * decay(t, 38) * 0.6
    return fit(x, -6)


# ------------------------------------------------------------------ 命中

def hit():
    """当てた本人に鳴る短い「チッ」＋音程。連続で当てるとピッチが上がる。"""
    t = axis(0.17)
    x = sine(C7, t) * decay(t, 34) + sine(C7 * 1.5, t) * decay(t, 52) * 0.5
    x = x + sine(C8, t) * decay(t, 90) * 0.25 + G.highpass(noise(t), 5000) * decay(t, 600) * 0.5
    return fit(x, -3)


def hit_head():
    t = axis(0.34)
    x = fm_bell(F7, t, rate=13, index=1.4) + sine(C8, t) * decay(t, 22) * 0.5 + sine(C7, t) * decay(t, 30) * 0.5
    x = x + G.highpass(noise(t), 5000) * decay(t, 500) * 0.6
    return fit(x, -2)


def hurt():
    """撃たれた側に鳴る鈍い音。"""
    t = axis(0.26)
    x = sine(sweep(150, 46, t, 0.14), t) * decay(t, 13) + G.lowpass(noise(t), 800) * decay(t, 34) * 0.7
    return fit(np.tanh(x * 1.6), -4)


def kill():
    """倒した本人に鳴るご褒美。Fm の和音を素早くかき鳴らし、低音が落ちる。"""
    t = axis(1.15)
    x = np.zeros_like(t)
    for i, f in enumerate((F5, AB5, C6, F6)):
        delay = int(0.014 * i * SR)
        x[delay:] += fm_bell(f, t[:len(t) - delay], rate=4.2, index=2.0)[:len(t) - delay] * (1 - 0.12 * i)
    sub = sine(sweep(130, F1, t, 0.22), t) * decay(t, 5.5)
    snap = G.highpass(noise(t), 2500) * decay(t, 170) * 0.6
    return fit(np.tanh((x * 0.42 + sub * 0.9 + snap) * 1.3), -1.5)


def shatter():
    """倒れた相手が砕ける音。その場で鳴る。"""
    t = axis(0.95)
    x = np.zeros_like(t)
    for _ in range(70):
        f = R.uniform(2200, 9500)
        start = R.exponential(0.09)
        x += np.sin(2 * np.pi * f * t) * decay(t, R.uniform(28, 75), start) * R.uniform(0.2, 1)
    x = x / 12 + G.highpass(noise(t), 3500) * decay(t, 13) * 0.35 + sine(sweep(110, 44, t, 0.2), t) * decay(t, 9) * 0.6
    return fit(x, -3)


# ------------------------------------------------------------------ 移動・剣

def blink():
    t = axis(0.34)
    centers = np.where(t < 0.13, 450 * (7500 / 450) ** (t / 0.13), 7500 * (1400 / 7500) ** ((t - 0.13) / 0.21))
    x = moving_band(noise(t), centers, q=1.4) * np.minimum(t / 0.05, 1) * decay(t, 7)
    x = x + sine(sweep(320, 2100, t, 0.13), t) * decay(t, 20) * 0.35
    return fit(x, -4)


def windup():
    """溜めの間（0.3 秒）に音程が上がる。"""
    t = axis(0.34)
    f = sweep(F3, 1100, t, 0.3)
    x = G.lowpass(saw_of(f, t) + saw_of(f * 1.01, t), 3200) * (0.25 + 0.75 * (t / t[-1]) ** 1.5)
    x = x * (0.72 + 0.28 * np.sin(2 * np.pi * 34 * t))
    return fit(x, -6)


def slash():
    t = axis(0.34)
    swish = moving_band(noise(t), sweep(7500, 800, t, 0.2), q=1.6) * np.minimum(t / 0.012, 1) * decay(t, 13)
    edge = G.lowpass(saw_of(sweep(1200, 130, t, 0.14), t), 4200) * decay(t, 20) * (0.7 + 0.3 * np.sign(np.sin(2 * np.pi * 95 * t)))
    return fit(swish + edge * 0.5, -3)


def slash_hit():
    t = axis(0.7)
    boom = sine(sweep(170, F1, t, 0.14), t) * decay(t, 6.5)
    crack = G.bandpass(noise(t), 900, 7000) * decay(t, 42)
    ring = fm_bell(F4, t, ratio=2.76, rate=7, index=1.6)
    return fit(np.tanh((boom + crack * 0.7 + ring * 0.3) * 1.5), -1.5)


# ------------------------------------------------------------------ スキル

def metal(f0, t, rates, ratios=(1.0, 1.67, 2.76, 3.9, 5.4)):
    return sum(sine(f0 * r, t) * decay(t, rates * (1 + 0.5 * i)) / (1 + 0.35 * i) for i, r in enumerate(ratios))


def parry():
    t = axis(0.16)
    return fit(metal(1900, t, 42) + G.highpass(noise(t), 4000) * decay(t, 500) * 0.6, -5)


def parry_success():
    t = axis(1.2)
    clang = metal(F5, t, 4.5) + metal(C7, t, 7) * 0.5
    air = G.highpass(noise(t), 6000) * decay(t, 9) * 0.3
    sub = sine(sweep(130, 52, t, 0.15), t) * decay(t, 10) * 0.8
    return fit(clang * 0.5 + air + sub, -1.5)


def last_stand():
    t = axis(1.6)
    x = sum(G.lowpass(saw_of(f, t) + saw_of(f * 1.006, t), 2400) for f in (F3, F3 * 1.189, C4, F4)) * 0.12
    swell = np.minimum(t / 0.25, 1) * decay(t, 2.4)
    boom = sine(sweep(150, F1, t, 0.25), t) * decay(t, 3.5)
    bell = fm_bell(F5, t, rate=3) + fm_bell(C6, t, rate=3.5) * 0.7
    return fit(np.tanh(x * swell * 1.4 + boom * 0.9 + bell * 0.25), -1.5)


def slowmo():
    """テープが止まるように音程が落ちる。"""
    t = axis(0.85)
    f = sweep(C6, 48, t, 0.7)
    x = G.lowpass(saw_of(f, t), 2600) * decay(t, 2.8) + sine(sweep(120, 38, t, 0.7), t) * np.minimum(t / 0.1, 1) * decay(t, 3)
    return fit(x, -3)


def beat():
    t = axis(0.06)
    return fit(G.highpass(noise(t), 7000, order=4) * decay(t, 130), -6)


# ------------------------------------------------------------------ 進行

def pluck_seq(notes, step, tail=0.4, decay_rate=9.0):
    t = axis(step * len(notes) + tail)
    x = np.zeros_like(t)
    for i, f in enumerate(notes):
        start = int(step * i * SR)
        s = fm_bell(f, t[:len(t) - start], rate=decay_rate, index=1.5)
        x[start:] += s
    return x


def rank_up():
    return fit(pluck_seq((F5, AB5, C6, F6), 0.055), -2.5)


def rank_down():
    t = axis(0.4)
    x = sine(np.where(t < 0.12, C5, AB4), t) * decay(t, 7) + sine(np.where(t < 0.12, C4, F3), t) * decay(t, 8) * 0.5
    return fit(G.lowpass(x, 1800), -5)


def chord_hit(freqs, t, rate=3.0):
    return sum(fm_bell(f, t, rate=rate) for f in freqs) / len(freqs)


def round_start():
    t = axis(1.5)
    x = np.zeros_like(t)
    rise = moving_band(noise(t), sweep(350, 8500, t, 0.75), q=1.5) * (t / 0.75) ** 2 * (t < 0.75)
    hit_at = int(0.75 * SR)
    tail = t[:len(t) - hit_at]
    x[hit_at:] += chord_hit((F4, C5, F5, AB5, C6), tail) * 1.6 + sine(sweep(140, F1, tail, 0.15), tail) * decay(tail, 3.5)
    return fit(np.tanh(rise * 0.7 + x), -1.5)


def round_end():
    t = axis(1.7)
    x = chord_hit((F4, AB4, C5, F5), t, 3.0) + np.pad(chord_hit((F4, C5, EB5, AB5), t, 2.4), (int(0.28 * SR), 0))[:len(t)]
    return fit(np.tanh(x * 1.3 + sine(sweep(120, F1, t, 0.2), t) * decay(t, 4) * 0.8), -2)


def zone_warn():
    t = axis(1.0)
    gate = (t * 5 % 1 < 0.72) * (t < 0.92)
    f = np.where(t * 5 % 2 < 1, F5, C5)
    x = G.lowpass(saw_of(f, t) + saw_of(f * 1.5, t) * 0.4, 2600) * gate
    return fit(G.lowpass(x, 3000) + sine(F3, t) * gate * 0.4, -4)


# ------------------------------------------------------------------ BGM

BARS = 16


def music():
    """2 周分作って 2 周目だけ切り出す（残響とサイドチェインが継ぎ目をまたいでつながる）。"""
    bar_len = G.BAR
    n = int(round(BARS * bar_len * SR))
    size = n * 2 + SR * 3
    rhythm = {k: np.zeros((2, size)) for k in ("drums", "bass", "music")}
    melody = {k: np.zeros((2, size)) for k in ("lead", "fx")}
    side = np.ones(size)
    k_snd, c_snd, h_snd, oh_snd, sn_snd = G.kick(), G.clap(), G.hat(), G.hat(True), G.snare()

    for loop in range(2):
        for bar in range(BARS):
            t0 = (loop * BARS + bar) * bar_len
            _, voicing, root, arp = G.CHORDS[bar % 4]
            full = bar >= 8          # 後半の 8 小節で厚くする
            last = bar == BARS - 1
            for step in range(16):
                ts = t0 + step * G.SIX + (G.SWING - 0.5) * 2 * G.SIX * (step % 2)
                if step % 4 == 0 and not (last and step >= 8):
                    G.place(rhythm["drums"], ts, k_snd, 0.55)
                    i = int(ts * SR)
                    duck = 1 - 0.5 * np.exp(-np.arange(int(G.BEAT * SR)) / SR * 9)
                    side[i:i + len(duck)] = np.minimum(side[i:i + len(duck)], duck[: size - i])
                if step in (4, 12):
                    G.place(rhythm["drums"], ts, c_snd, 2.0, pan=0.05)
                if step % 4 == 2:
                    G.place(rhythm["drums"], ts, oh_snd, 0.2, pan=-0.25)
                else:
                    G.place(rhythm["drums"], ts, h_snd, (0.17 if step % 2 else 0.24) * (0.8 + 0.4 * R.random()), pan=0.3)
                if step in (2, 6, 10, 14):
                    G.place(rhythm["music"], ts, G.stab(voicing), 0.5 if full else 0.38, pan=-0.45 if step in (2, 10) else 0.45)
                G.place(melody["lead"], ts, G.pluck(arp[step % 8]), 0.3, pan=0.25 * np.sin(step))
            if last:
                # 最後の小節はスネアロールで次の周の頭へ飛び込む
                for k in range(32):
                    G.place(rhythm["drums"], t0 + k * G.BEAT / 8, sn_snd, 0.12 + 0.4 * k / 32)
            for step, semis, ln in G.BASS_PATTERN:
                ts = t0 + step * G.SIX + (G.SWING - 0.5) * 2 * G.SIX * (step % 2)
                G.place(rhythm["bass"], ts, G.bass_note(root + semis, ln * G.SIX * 0.9), 0.33)
            G.place(rhythm["music"], t0, G.pad(voicing, bar_len * 0.98), 0.32, pan=-0.75)
            G.place(rhythm["music"], t0, G.pad(voicing, bar_len * 0.98), 0.32, pan=0.75)
            if full:
                for lb, pos, midi, ln in G.LEAD:
                    if lb == bar % 4:
                        G.place(melody["lead"], t0 + pos * G.BEAT / 2, G.lead_note(midi, ln * G.BEAT / 2 * 0.95), 0.42, pan=0.1)
            if bar == BARS - 2:
                G.place(melody["fx"], t0, G.riser(bar_len * 2), 0.34)
        G.place(melody["fx"], loop * BARS * bar_len, G.impact(), 0.32)

    def stereo_reverb(x, wet):
        return np.stack([G.reverb(x[0], wet), G.reverb(x[1], wet)])

    base = (stereo_reverb(rhythm["drums"], 0.1) + rhythm["bass"] * side + stereo_reverb(rhythm["music"] * side, 0.28))[:, n:2 * n]
    delay = G.pingpong(melody["lead"] * side, G.SIX * 3)
    drive = (stereo_reverb(delay, 0.2) + stereo_reverb(melody["fx"], 0.35))[:, n:2 * n]

    def master(x, rms_db):
        x = x * 10 ** (rms_db / 20) / np.sqrt(np.mean(x ** 2))
        return np.tanh(x * 1.1) / 1.1

    return master(base, -17), master(drive, -21)


# ------------------------------------------------------------------ 書き出し

def write_wav(path, x):
    data = (np.clip(x.T if x.ndim == 2 else x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2 if x.ndim == 2 else 1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def encode(name, x, quality):
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / "x.wav"
        write_wav(wav, x)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-map_metadata", "-1", "-c:a", "libvorbis",
                        "-q:a", str(quality), str(OUT / f"{name}.ogg")], check=True)
    peak = 20 * np.log10(max(1e-9, np.max(np.abs(x))))
    rms = 20 * np.log10(max(1e-9, np.sqrt(np.mean(x ** 2))))
    size = (OUT / f"{name}.ogg").stat().st_size
    print(f"{name:14s} {x.shape[-1] / SR:6.2f}s peak {peak:6.1f}dB rms {rms:6.1f}dB {size / 1024:7.1f}KB")


SOUNDS = [shot, shot_beat, impact, hit, hit_head, hurt, kill, shatter, blink, windup, slash, slash_hit, parry, parry_success,
          last_stand, slowmo, beat, rank_up, rank_down, round_start, round_end, zone_warn]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="カンマ区切りの名前。music で BGM")
    args = ap.parse_args()
    only = set(args.only.split(",")) if args.only else None
    for fn in SOUNDS:
        if only is None or fn.__name__ in only:
            encode(fn.__name__, fn(), 5)
    if only is None or "music" in only:
        base, drive = music()
        encode("music_base", base, 4)
        encode("music_drive", drive, 4)


if __name__ == "__main__":
    main()
