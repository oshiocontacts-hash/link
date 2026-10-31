package com.neonclash.client;

import com.neonclash.config.NCConfig;
import com.neonclash.core.style.StyleRank;
import com.neonclash.game.MovementHandler;
import com.neonclash.network.S2CBeamPacket;
import com.neonclash.network.S2CBeatPacket;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.network.S2CFxPacket;
import com.neonclash.network.S2CHudPacket;
import com.neonclash.network.S2CSlowMoPacket;
import com.neonclash.network.S2CUpgradeOfferPacket;
import com.neonclash.registry.NCSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** S2C パケットのクライアント側処理（DistExecutor 経由でのみ呼ばれる）。 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void onFeedback(S2CFeedbackPacket p) {
        switch (p.kind()) {
            case SHAKE -> {
                float scale = NCConfig.CLIENT.cameraShakeScale.get().floatValue();
                if (p.value() * scale >= ClientState.shakeDegrees || ClientState.shakeTicks == 0) {
                    ClientState.shakeDegrees = p.value() * scale;
                    ClientState.shakeTicks = ClientState.shakeTotal = Math.max(1, p.ticks());
                }
            }
            case HIT_STOP -> ClientState.hitStopTicks = Math.max(ClientState.hitStopTicks, p.ticks());
            case HIT_CONFIRM -> {
                ClientState.hitMarkerTicks = NCConfig.CLIENT.hitMarkerTicks.get();
                ClientState.hitMarkerFlags = p.extra();
            }
            case KILL_CONFIRM -> {
                NCConfig.Client cfg = NCConfig.CLIENT;
                ClientState.killMarkerTicks = cfg.killMarkerTicks.get();
                ClientState.killBannerTicks = cfg.killBannerTicks.get();
                ClientState.killFlashTicks = cfg.flashTicks.get();
                ClientState.speedLineTicks = cfg.speedLineTicks.get();
                ClientState.killStreak = p.extra();
                kickFov(cfg.killFovKick.get().floatValue());
            }
            case HURT -> ClientState.addHurt(p.value(), NCConfig.CLIENT.hurtIndicatorTicks.get());
            case RANK -> {
                StyleRank r = StyleRank.values()[Math.max(0, Math.min(StyleRank.values().length - 1, p.extra()))];
                ClientState.rankWentUp = p.value() > 0.5f;
                ClientState.rank = r;
                ClientState.rankPopTicks = NCConfig.CLIENT.rankPopTicks.get();
            }
            case BEAT_HIT -> ClientState.beatFlashTicks = NCConfig.CLIENT.flashTicks.get();
            case WINDUP -> {
                ClientState.windupTicks = ClientState.windupTotal = Math.max(1, p.ticks());
                ClientState.windupCancelTicks = 0;
            }
            case WINDUP_CANCEL -> {
                ClientState.windupTicks = 0;
                ClientState.windupCancelTicks = NCConfig.CLIENT.flashTicks.get();
            }
            case PARRY -> ClientState.parryTicks = p.ticks();
            case PARRY_SUCCESS -> ClientState.parryFlashTicks = NCConfig.CLIENT.flashTicks.get();
            case LAST_STAND -> ClientState.lastStandTicks = p.ticks();
            case BLINK -> {
                NCConfig.Client cfg = NCConfig.CLIENT;
                ClientState.blinkFlashTicks = cfg.flashTicks.get();
                ClientState.speedLineTicks = cfg.speedLineTicks.get();
                kickFov(cfg.blinkFovKick.get().floatValue());
            }
        }
    }

    private static void kickFov(float amount) {
        ClientState.fovKick = amount;
        ClientState.fovKickTicks = NCConfig.CLIENT.fovKickTicks.get();
    }

    public static void onSlowMo(S2CSlowMoPacket p) {
        ClientState.slowMoTicks = ClientState.slowMoTotal = Math.max(ClientState.slowMoTicks, p.ticks());
        ClientState.slowMoStrength = p.strength();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(NCSounds.SLOWMO.get(), 1.0f, 0.8f));
    }

    public static void onBeam(S2CBeamPacket p) {
        BeamRenderer.add(p);
    }

    public static void onFx(S2CFxPacket p) {
        FxRenderer.add(p);
    }

    public static void onHud(S2CHudPacket p) {
        S2CHudPacket before = ClientState.hud;
        if (before != null && p.phase() >= 0 && before.round() != p.round()) {
            MatchMusic.resync(); // 長いループのわずかなずれをラウンドごとに消す
        }
        ClientState.hud = p.phase() >= 0 ? p : null;
        MovementHandler.clientJumpUpgrade = p.jumpUpgrade();
        if (p.phase() >= 0) {
            StyleRank r = StyleRank.values()[Math.max(0, Math.min(StyleRank.values().length - 1, p.rank()))];
            if (r != ClientState.rank && ClientState.rankPopTicks == 0) {
                ClientState.rankWentUp = r.ordinal() > ClientState.rank.ordinal();
                ClientState.rank = r;
            }
        }
    }

    public static void onBeat(S2CBeatPacket p) {
        long oneWay = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) oneWay = info.getLatency() / 2;
        }
        ClientState.serverOffsetMs = (p.serverNowMs() + oneWay) - System.currentTimeMillis();
        boolean moved = ClientState.BEAT.bpm() != p.bpm() || ClientState.BEAT.anchorMs() != p.anchorMs() || ClientState.BEAT.anchorIndex() != p.anchorIndex();
        ClientState.BEAT.sync(p.bpm(), p.anchorMs(), p.anchorIndex(), p.toleranceMs(), p.enabled());
        if (moved) {
            MatchMusic.resync();
        }
    }

    public static void onUpgradeOffer(S2CUpgradeOfferPacket p) {
        ClientState.offers = p.offers();
        ClientState.offerTicks = p.ticks();
        ClientState.offerRank = p.rank();
        ClientState.offerChosen = p.chosen();
        Minecraft mc = Minecraft.getInstance();
        if (p.offers().isEmpty()) {
            if (mc.screen instanceof UpgradeScreen) mc.setScreen(null);
        } else if (!(mc.screen instanceof UpgradeScreen)) {
            // チャットやインベントリを開いていても 3 択を前に出す（5 秒しか無いため）
            mc.setScreen(new UpgradeScreen());
        }
    }
}
