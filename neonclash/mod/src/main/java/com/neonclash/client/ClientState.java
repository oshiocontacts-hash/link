package com.neonclash.client;

import com.neonclash.core.beat.BeatClock;
import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.UpgradeDef;
import com.neonclash.network.S2CHudPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** クライアントが持つ表示用の状態。 */
@OnlyIn(Dist.CLIENT)
public final class ClientState {
    private ClientState() {
    }

    public static S2CHudPacket hud;

    // ビート：サーバー時刻 = クライアント時刻 + offset
    public static final BeatClock BEAT = new BeatClock(120, 0, 100, false);
    public static long serverOffsetMs;
    public static long lastBeatIndex = Long.MIN_VALUE;
    public static long lastBeatAtMs;

    // 演出のタイマー（クライアント tick 単位）
    public static int slowMoTicks;
    public static int slowMoTotal;
    public static float slowMoStrength;
    public static int hitStopTicks;
    public static int shakeTicks;
    public static int shakeTotal;
    public static float shakeDegrees;
    public static int hitMarkerTicks;
    public static int hitMarkerFlags;
    public static int killMarkerTicks;
    public static int killBannerTicks;
    public static int killFlashTicks;
    public static int speedLineTicks;
    public static int fovKickTicks;
    public static float fovKick;
    public static int killStreak;
    public static int beatFlashTicks;
    public static int windupTicks;
    public static int windupTotal;
    public static int windupCancelTicks;
    public static int parryTicks;
    public static int parryFlashTicks;
    public static int lastStandTicks;
    public static int blinkFlashTicks;

    /** 撃たれた向きの表示。angle は世界の方角（度）。 */
    public static final class Hurt {
        public final float angle;
        public int ticks;

        Hurt(float angle, int ticks) {
            this.angle = angle;
            this.ticks = ticks;
        }
    }

    public static final List<Hurt> HURTS = new ArrayList<>();

    // スタイルランク
    public static StyleRank rank = StyleRank.D;
    public static int rankPopTicks;
    public static boolean rankWentUp;

    // 強化の3択
    public static List<UpgradeDef> offers = List.of();
    public static int offerRank;
    public static int offerChosen = -1;
    public static int offerTicks;

    public static long serverNowMs() {
        return System.currentTimeMillis() + serverOffsetMs;
    }

    public static boolean inMatch() {
        return hud != null && hud.phase() >= 0;
    }

    public static void tick() {
        if (slowMoTicks > 0) slowMoTicks--;
        if (hitStopTicks > 0) hitStopTicks--;
        if (shakeTicks > 0) shakeTicks--;
        if (hitMarkerTicks > 0) hitMarkerTicks--;
        if (killMarkerTicks > 0) killMarkerTicks--;
        if (killBannerTicks > 0) killBannerTicks--;
        if (killFlashTicks > 0) killFlashTicks--;
        if (speedLineTicks > 0) speedLineTicks--;
        if (fovKickTicks > 0) fovKickTicks--;
        HURTS.removeIf(h -> --h.ticks <= 0);
        if (beatFlashTicks > 0) beatFlashTicks--;
        if (windupTicks > 0) windupTicks--;
        if (windupCancelTicks > 0) windupCancelTicks--;
        if (parryTicks > 0) parryTicks--;
        if (parryFlashTicks > 0) parryFlashTicks--;
        if (lastStandTicks > 0) lastStandTicks--;
        if (blinkFlashTicks > 0) blinkFlashTicks--;
        if (rankPopTicks > 0) rankPopTicks--;
        if (offerTicks > 0) offerTicks--;
    }

    public static void addHurt(float angle, int ticks) {
        // 同じ方向からの連射は 1 本にまとめる
        HURTS.removeIf(h -> Math.abs(net.minecraft.util.Mth.wrapDegrees(h.angle - angle)) < 30f);
        HURTS.add(new Hurt(angle, ticks));
    }

    public static void reset() {
        hud = null;
        slowMoTicks = hitStopTicks = shakeTicks = hitMarkerTicks = killMarkerTicks = 0;
        killBannerTicks = killFlashTicks = speedLineTicks = fovKickTicks = 0;
        HURTS.clear();
        offers = List.of();
        offerChosen = -1;
        rank = StyleRank.D;
    }
}
