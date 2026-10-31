"""デモの録画に音を付けて動画にする。

ゲームの音を録音するのではなく、DemoDriver が書いた run/demo_sounds.jsonl（いつ・何が・どの音量とピッチで鳴ったか）から
同じ音源を並べ直して音声を作る。録画する環境に音声デバイスが無くても作れ、音量も後から揃えられる。
バニラの音（足音など）は含まない。BGM の音量の変化は記録どおりに再現する。

耳に優しいよう、全体の音量は小さめに揃える（--rms-db）。

使い方: python tools/sfx/mix_demo.py --fps 30 --out media/neonclash-demo-full.mp4
"""
import argparse
import collections
import json
import pathlib
import subprocess
import tempfile
import wave
import zipfile

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOUNDS = ROOT / "mod/src/main/resources/assets/neonclash/sounds"
RUN = ROOT / "mod/run"
SR = 44100
LOOP_SECONDS = 30.0
TACZ_PREFIX = "assets/tacz/custom/tacz_default_gun/assets/tacz/tacz_sounds/"


def decode(path, channels=1):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(path), "-f", "f32le", "-ac", str(channels), "-ar", str(SR), "-"],
                         check=True, capture_output=True).stdout
    x = np.frombuffer(raw, dtype="<f4").astype(np.float64)
    return x if channels == 1 else x.reshape(-1, channels).T


class Library:
    def __init__(self):
        jars = sorted((ROOT / "mod/libs").glob("tacz-*.jar"))
        self.jar = zipfile.ZipFile(jars[0]) if jars else None
        self.tacz = {pathlib.PurePosixPath(n).relative_to(TACZ_PREFIX).with_suffix("").as_posix(): n
                     for n in (self.jar.namelist() if self.jar else []) if n.startswith(TACZ_PREFIX) and n.endswith(".ogg")}
        self.cache = {}
        self.missing = collections.Counter()

    def get(self, name):
        if name in self.cache:
            return self.cache[name]
        ns, _, path = name.partition(":")
        x = None
        if ns == "neonclash" and (SOUNDS / f"{path}.ogg").exists():
            x = decode(SOUNDS / f"{path}.ogg")
        elif ns == "tacz" and path in self.tacz:
            with tempfile.TemporaryDirectory() as tmp:
                f = pathlib.Path(tmp) / "x.ogg"
                f.write_bytes(self.jar.read(self.tacz[path]))
                x = decode(f)
        if x is None:
            self.missing[name] += 1
        self.cache[name] = x
        return x


def repitch(x, pitch):
    if abs(pitch - 1) < 1e-3:
        return x
    n = int(len(x) / pitch)
    return np.interp(np.arange(n) * pitch, np.arange(len(x)), x)


def music_track(starts, levels, n):
    """stems を start ごとに鳴らし直す。音量は記録を線形補間する。"""
    base, drive = decode(SOUNDS / "music_base.ogg", 2), decode(SOUNDS / "music_drive.ogg", 2)
    loop = int(LOOP_SECONDS * SR)
    out = np.zeros((2, n))
    if not levels or not starts:
        return out
    t = np.array([l[0] for l in levels]) / 1000 * SR
    env_base = np.interp(np.arange(n), t, [l[1] for l in levels])
    env_drive = np.interp(np.arange(n), t, [l[2] for l in levels])
    for i, s in enumerate(starts):
        a = int(s / 1000 * SR)
        b = int(starts[i + 1] / 1000 * SR) if i + 1 < len(starts) else n
        pos = (np.arange(a, min(b, n)) - a) % loop
        for ch in range(2):
            out[ch, a:a + len(pos)] = base[ch, pos] * env_base[a:a + len(pos)] + drive[ch, pos] * env_drive[a:a + len(pos)]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--out", default="media/neonclash-demo-full.mp4")
    ap.add_argument("--rms-db", type=float, default=-24, help="全体の音量。小さいほど静か")
    ap.add_argument("--music", type=float, default=0.55, help="効果音に対する BGM の音量")
    args = ap.parse_args()

    frames = len(list((RUN / "demo_frames").glob("*.png")))
    n = int(frames / args.fps * SR)
    lib = Library()
    sfx = np.zeros(n)
    starts, levels = [], []
    played = collections.Counter()
    for line in open(RUN / "demo_sounds.jsonl", encoding="utf-8"):
        ev = json.loads(line)
        if "music" in ev:
            levels.append((ev["t"], *ev["music"]))
            continue
        if ev["name"] == "neonclash:music_base":
            starts.append(ev["t"])
            continue
        if ev["source"] == "music" or ev["gain"] <= 0:
            continue
        x = lib.get(ev["name"])
        if x is None:
            continue
        played[ev["name"]] += 1
        s = repitch(x, max(0.5, min(2.0, ev["pitch"]))) * ev["gain"]
        a = int(ev["t"] / 1000 * SR)
        if a >= n:
            continue
        s = s[: n - a]
        sfx[a:a + len(s)] += s

    music = music_track(starts, levels, n)
    mix = music * args.music + np.stack([sfx, sfx])
    mix *= 10 ** (args.rms_db / 20) / max(1e-9, np.sqrt(np.mean(mix ** 2)))
    # 重なった瞬間だけ大きくならないよう頭を抑える（-6dB で頭打ち）
    ceiling = 10 ** (-6 / 20)
    mix = np.tanh(mix / ceiling) * ceiling
    peak, rms = np.max(np.abs(mix)), np.sqrt(np.mean(mix ** 2))
    print(f"{frames} frames, {frames / args.fps:.1f}s, music starts {len(starts)}, peak {20 * np.log10(peak):.1f}dB rms {20 * np.log10(rms):.1f}dB")
    print("played:", played.most_common())
    print("skipped:", lib.missing.most_common())

    out = ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / "mix.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(mix.T, -1, 1) * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(args.fps), "-i", str(RUN / "demo_frames/%05d.png"), "-i", str(wav),
                        "-vf", "format=yuv420p", "-c:v", "libx264", "-preset", "medium", "-crf", "24", "-c:a", "aac", "-b:a", "192k", "-shortest", str(out)],
                       check=True)
    print("wrote", out, f"{out.stat().st_size / 1e6:.1f}MB")


if __name__ == "__main__":
    main()
