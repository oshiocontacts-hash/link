package com.neonclash.game;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.beat.BeatClock;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CBeatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * サーバーが固定 BPM で全員共通のビートを刻む。判定はサーバー時刻（ms）で、
 * プレイヤーの片道遅延（ping/2, 上限あり）を差し引いて「押した瞬間」に近づける。
 * 拍の音・クロスヘアの脈動はクライアントが同期済みの時計で鳴らす（サーバー tick の 50ms 粒度に縛られないため）。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class BeatService {
    private static BeatClock clock;
    private static long lastSyncMs;

    private BeatService() {
    }

    public static long nowMs() {
        return System.currentTimeMillis();
    }

    public static BeatClock clock() {
        if (clock == null) {
            clock = NCConfig.SERVER.newBeatClock(nowMs());
        }
        return clock;
    }

    public static BeatClock.Judgement judge(LivingEntity e) {
        long comp = 0;
        if (e instanceof ServerPlayer sp) {
            comp = Math.min(sp.latency / 2, NCConfig.SERVER.beatMaxLatencyCompensationMs.get());
        }
        return clock().judge(nowMs() - comp);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        clock = NCConfig.SERVER.newBeatClock(nowMs());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        NCNetwork.send(event.getEntity(), packet());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NCConfig.Server cfg = NCConfig.SERVER;
        BeatClock c = clock();
        long now = nowMs();
        boolean changed = false;
        // config の変更（/reload やファイル編集）を反映。BPM は直前の拍を基準に切り替える
        if (Math.abs(c.bpm() - cfg.beatBpm.get()) > 1.0e-9) {
            c.setBpm(cfg.beatBpm.get(), now);
            changed = true;
        }
        if (Math.abs(c.toleranceMs() - cfg.beatToleranceMs.get()) > 1.0e-9 || c.enabled() != cfg.beatEnabled.get()) {
            c.setToleranceMs(cfg.beatToleranceMs.get());
            c.setEnabled(cfg.beatEnabled.get());
            changed = true;
        }
        if (changed || now - lastSyncMs >= cfg.beatResyncSeconds.get() * 1000L) {
            lastSyncMs = now;
            NCNetwork.sendToAll(packet());
        }
    }

    public static S2CBeatPacket packet() {
        BeatClock c = clock();
        return new S2CBeatPacket(c.bpm(), c.anchorMs(), c.anchorIndex(), c.toleranceMs(), c.enabled(), nowMs());
    }
}
