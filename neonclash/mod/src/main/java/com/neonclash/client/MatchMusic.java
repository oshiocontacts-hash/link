package com.neonclash.client;

import com.neonclash.config.NCConfig;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.network.S2CHudPacket;
import com.neonclash.registry.NCSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 試合の BGM。リズムの層とメロディの層を同時に鳴らし、スタイルランクが上がるほどメロディの層が大きくなる。
 * 再生の開始を小節の頭に合わせるので、BGM のキックがゲームの拍と揃う。
 */
@OnlyIn(Dist.CLIENT)
public final class MatchMusic {
    /** 曲のテンポ（tools/sfx/make_sfx.py と同じ）。サーバーの bpm が違えばピッチで合わせる。 */
    private static final double TRACK_BPM = 128.0;
    private static final int BEATS_PER_BAR = 4;
    private static final float FADE = 0.15f;

    private static Stem base;
    private static Stem drive;
    private static long lastBeat = Long.MIN_VALUE;

    private MatchMusic() {
    }

    /** 毎フレーム呼ぶ。tick ごとだと開始が最大 50ms ずれる。 */
    static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (!NCConfig.CLIENT.musicEnabled.get() || mc.level == null || !ClientState.inMatch() || !ClientState.BEAT.enabled()) {
            fadeOut();
            return;
        }
        long beat = ClientState.BEAT.beatIndexAt(ClientState.serverNowMs() + NCConfig.CLIENT.musicLatencyMs.get());
        if (beat == lastBeat) return;
        lastBeat = beat;
        if (Math.floorMod(beat, BEATS_PER_BAR) != 0 || (base != null && mc.getSoundManager().isActive(base))) return;
        mc.getMusicManager().stopPlaying();
        base = new Stem(NCSounds.MUSIC_BASE.get(), false);
        drive = new Stem(NCSounds.MUSIC_DRIVE.get(), true);
        mc.getSoundManager().play(base);
        mc.getSoundManager().play(drive);
    }

    /** 拍の基準が変わったら、次の小節の頭で鳴らし直す。 */
    static void resync() {
        Minecraft mc = Minecraft.getInstance();
        if (base != null) mc.getSoundManager().stop(base);
        if (drive != null) mc.getSoundManager().stop(drive);
        base = drive = null;
    }

    private static void fadeOut() {
        if (base != null) base.finish();
        if (drive != null) drive.finish();
        base = drive = null;
    }

    /** BGM が聞こえているか（聞こえている間は拍のクリックを鳴らさない。キックが拍を刻むので）。 */
    static boolean audible() {
        Minecraft mc = Minecraft.getInstance();
        return base != null && mc.getSoundManager().isActive(base) && mc.options.getSoundSourceVolume(SoundSource.MUSIC) > 0;
    }

    static boolean playing() {
        return base != null;
    }

    private static float duck() {
        NCConfig.Client cfg = NCConfig.CLIENT;
        float v = 1f;
        if (ClientEvents.slowMoAmount(0) > 0) v *= cfg.musicSlowMoDuck.get().floatValue();
        S2CHudPacket hud = ClientState.hud;
        if (hud != null && hud.phase() != RoundEngine.Phase.ROUND.ordinal()) v *= cfg.musicIntermissionDuck.get().floatValue();
        return v;
    }

    private static float driveLevel() {
        List<? extends Number> levels = NCConfig.CLIENT.musicDriveByRank.get();
        int rank = ClientState.rank.ordinal();
        return rank < levels.size() ? Math.min(1f, levels.get(rank).floatValue()) : 0f;
    }

    /** 音量とピッチを毎 tick 更新できる BGM の 1 層。 */
    public static final class Stem extends AbstractTickableSoundInstance {
        private final boolean melody;
        private boolean finishing;

        Stem(SoundEvent event, boolean melody) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.melody = melody;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = target();
            pitch = pitch();
        }

        private float target() {
            float v = NCConfig.CLIENT.musicVolume.get().floatValue() * duck();
            return melody ? v * driveLevel() : v;
        }

        private static float pitch() {
            return (float) Math.max(0.5, Math.min(2.0, ClientState.BEAT.bpm() / TRACK_BPM));
        }

        void finish() {
            finishing = true;
        }

        /** メロディの層は音量 0 から始まることがある。 */
        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float goal = finishing ? 0f : target();
            volume += (goal - volume) * FADE;
            pitch = pitch();
            if (finishing && volume < 0.01f) {
                stop();
            }
        }
    }
}
