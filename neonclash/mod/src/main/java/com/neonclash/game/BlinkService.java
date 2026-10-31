package com.neonclash.game;

import com.neonclash.config.NCConfig;
import com.neonclash.core.beat.BeatClock;
import com.neonclash.core.movement.BlinkResolver;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.registry.NCSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** ブリンク：視線方向に短距離ワープ。判定はすべてサーバー。 */
public final class BlinkService {
    private BlinkService() {
    }

    public static boolean tryBlink(Combatant c) {
        LivingEntity e = c.entity();
        if (!(e.level() instanceof ServerLevel level) || c.blink.charges() <= 0) {
            return false;
        }
        NCConfig.Server cfg = NCConfig.SERVER;
        Vec3 look = e.getLookAngle();
        Optional<BlinkResolver.Result> result = BlinkResolver.resolve(
                Vecs.core(e.position()), Vecs.core(look), e.getBbWidth(), e.getBbHeight(),
                cfg.blinkSettings(c.upgrades.get(UpgradeStat.BLINK_DISTANCE)),
                box -> level.noCollision(e, Vecs.mc(box)));
        if (result.isEmpty()) {
            return false;
        }
        BeatClock.Judgement beat = BeatService.judge(e);
        double cooldown = cfg.blinkCooldownTicks.get()
                * c.upgrades.reductionMultiplier(UpgradeStat.BLINK_COOLDOWN, cfg.upgradeReductionFloor.get())
                * (beat.onBeat() ? cfg.beatCooldownMultiplier.get() : 1.0);
        if (!c.blink.tryConsume((int) Math.round(cooldown))) {
            return false;
        }

        Vec3 from = e.position();
        Vec3 to = Vecs.mc(result.get().destination());
        e.teleportTo(to.x, to.y, to.z);
        e.fallDistance = 0;
        e.setDeltaMovement(look.scale(cfg.blinkExitVelocity.get()));
        e.hurtMarked = true; // プレイヤーへ速度を送る
        c.lastBlinkTick = level.getGameTime();

        FxService.blink(level, from, to, e.getBbHeight(), c.teamColor());
        level.playSound(null, from.x, from.y, from.z, NCSounds.BLINK.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, to.x, to.y, to.z, NCSounds.BLINK.get(), SoundSource.PLAYERS, 0.8f, 1.5f);
        NCNetwork.send(e, S2CFeedbackPacket.of(S2CFeedbackPacket.Kind.BLINK));
        if (beat.onBeat()) {
            StyleService.apply(c, StyleEvent.ON_BEAT);
            NCNetwork.send(e, S2CFeedbackPacket.of(S2CFeedbackPacket.Kind.BEAT_HIT));
        }
        return true;
    }
}
