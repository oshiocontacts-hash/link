package com.neonclash.game;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.registry.NCDamageTypes;
import com.neonclash.registry.NCItems;
import com.neonclash.registry.NCSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Comparator;

/** 剣：溜め → 斬撃（短射程・当たれば一撃キル）。溜め中にハンドガンで撃たれると溜めリセット。 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class SwordService {
    private SwordService() {
    }

    public static boolean isHoldingBlade(LivingEntity e) {
        return e.getMainHandItem().is(NCItems.NEON_BLADE.get());
    }

    public static boolean tryStartWindup(Combatant c) {
        LivingEntity e = c.entity();
        if (e instanceof Player && !isHoldingBlade(e)) {
            return false;
        }
        NCConfig.Server cfg = NCConfig.SERVER;
        int windup = (int) Math.max(1, Math.round(cfg.swordWindupTicks.get()
                * c.upgrades.reductionMultiplier(UpgradeStat.SWORD_WINDUP, cfg.upgradeReductionFloor.get())));
        if (!c.sword.startWindup(windup, cfg.swordRecoveryTicks.get(), cfg.swordInterruptLockoutTicks.get())) {
            return false;
        }
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), NCSounds.WINDUP.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
        if (e.level() instanceof ServerLevel level) {
            FxService.windup(level, e, c.teamColor(), windup);
        }
        NCNetwork.send(e, new S2CFeedbackPacket(S2CFeedbackPacket.Kind.WINDUP, 0, windup, 0));
        return true;
    }

    /** ハンドガンの命中で呼ばれる。 */
    public static void interrupt(Combatant c) {
        if (c.sword.interrupt()) {
            LivingEntity e = c.entity();
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), NCSounds.PARRY.get(), SoundSource.PLAYERS, 0.6f, 0.6f);
            NCNetwork.send(e, S2CFeedbackPacket.of(S2CFeedbackPacket.Kind.WINDUP_CANCEL));
        }
    }

    static void tick(Combatant c) {
        if (c.sword.tick()) {
            swing(c);
        }
    }

    /** 斬撃。射程内・円錐内で一番近い相手に当てる。当たった相手を返す。 */
    @Nullable
    public static LivingEntity swing(Combatant c) {
        LivingEntity e = c.entity();
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive()) {
            return null;
        }
        NCConfig.Server cfg = NCConfig.SERVER;
        double range = cfg.swordRange.get() + c.upgrades.get(UpgradeStat.SWORD_RANGE);
        double cosLimit = Math.cos(Math.toRadians(cfg.swordConeDegrees.get()));
        Vec3 eye = e.getEyePosition();
        Vec3 look = e.getLookAngle();

        e.swing(InteractionHand.MAIN_HAND, true);
        e.setDeltaMovement(e.getDeltaMovement().add(look.x * cfg.swordLungeVelocity.get(), 0, look.z * cfg.swordLungeVelocity.get()));
        e.hurtMarked = true;
        level.playSound(null, e.getX(), e.getY(), e.getZ(), NCSounds.SLASH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        FxService.slash(level, eye, look, range, c.teamColor());

        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(range + 1.0),
                        t -> t != e && t.isAlive() && !t.isSpectator() && isEnemy(c, t))
                .stream()
                .filter(t -> {
                    Vec3 aim = closestPoint(t, eye);
                    Vec3 d = aim.subtract(eye);
                    double dist = d.length();
                    return dist <= range && (dist < 1.0e-6 || d.normalize().dot(look) >= cosLimit) && e.hasLineOfSight(t);
                })
                .min(Comparator.comparingDouble(t -> closestPoint(t, eye).distanceToSqr(eye)))
                .orElse(null);
        if (target != null) {
            bladeKill(e, target);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), NCSounds.SLASH_HIT.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
            // ラストスタンドで耐えた相手は大きく吹き飛ぶ
            Combatant tc = Combatants.get(target);
            if (target.isAlive() && (tc == null || !tc.eliminated)) {
                HitFeedback.knockback(target, target.position().subtract(e.position()), cfg.swordKnockback.get(), cfg.swordKnockbackLift.get());
            }
        }
        return target;
    }

    private static Vec3 closestPoint(LivingEntity t, Vec3 from) {
        var bb = t.getBoundingBox();
        return new Vec3(clamp(from.x, bb.minX, bb.maxX), clamp(from.y, bb.minY, bb.maxY), clamp(from.z, bb.minZ, bb.maxZ));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static boolean isEnemy(Combatant c, LivingEntity t) {
        Combatant other = Combatants.get(t);
        if (other != null && other.eliminated) {
            return false;
        }
        return c.team == null || other == null || other.team != c.team || NCConfig.SERVER.friendlyFire.get();
    }

    /** 一撃キル。防具・エンチャント・無敵時間を無視するダメージタイプで、残り体力以上を与える。 */
    public static boolean bladeKill(LivingEntity attacker, LivingEntity target) {
        DamageSource src = NCDamageTypes.source(target.level(), NCDamageTypes.BLADE, attacker);
        float lethal = target.getHealth() + target.getAbsorptionAmount() + target.getMaxHealth();
        target.invulnerableTime = 0;
        return target.hurt(src, lethal);
    }

    /** ブレードを持ったプレイヤーのバニラ攻撃は無効化（溜め斬りに置き換え）。 */
    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent event) {
        if (isHoldingBlade(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
