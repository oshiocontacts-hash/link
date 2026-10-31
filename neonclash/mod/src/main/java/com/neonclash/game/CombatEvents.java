package com.neonclash.game;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.match.MatchController;
import com.neonclash.match.MatchRegistry;
import com.neonclash.registry.NCDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 攻撃・被弾・死亡の共通処理（剣でも銃でも通る）。サーバー側のみ。 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class CombatEvents {
    private CombatEvents() {
    }

    @Nullable
    private static LivingEntity attackerOf(DamageSource src) {
        return src.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** プレイヤーのパリィ・切り札は試合中だけ（/kill を耐える等を防ぐ）。モブは明示的に登録したものなので常に有効。 */
    private static boolean skillsActive(Combatant c) {
        return c.inMatch || !(c.entity() instanceof net.minecraft.world.entity.player.Player);
    }

    /** ダメージ判定の前：脱落者・味方撃ち・パリィ。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        Combatant vc = Combatants.get(victim);
        if (vc == null) return;
        if (vc.eliminated) {
            event.setCanceled(true);
            return;
        }
        LivingEntity attacker = attackerOf(event.getSource());
        Combatant ac = Combatants.get(attacker);
        if (ac != null && ac != vc && vc.team != null && vc.team == ac.team && !NCConfig.SERVER.friendlyFire.get()) {
            event.setCanceled(true);
            return;
        }
        if (attacker != null && attacker != victim && skillsActive(vc) && SkillService.onIncomingAttack(vc, attacker)) {
            event.setCanceled(true);
        }
    }

    /** 実際にダメージが入った：被弾でスタイル低下。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0) return;
        Combatant vc = Combatants.get(event.getEntity());
        if (vc != null && !vc.eliminated) {
            StyleService.apply(vc, StyleEvent.DAMAGE_TAKEN);
        }
    }

    /** 致命傷：ラストスタンド → キル処理 → 試合なら脱落（死亡はキャンセルして観戦へ）。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        Combatant vc = Combatants.get(victim);
        if (vc == null || vc.eliminated) return;

        if (skillsActive(vc) && SkillService.tryLastStand(vc)) {
            event.setCanceled(true);
            return;
        }

        DamageSource src = event.getSource();
        LivingEntity killer = attackerOf(src);
        onKill(killer, victim, src);

        Optional<MatchController> match = MatchRegistry.findFor(victim.getUUID());
        if (match.isPresent()) {
            event.setCanceled(true);
            match.get().onCombatantDown(vc, killer);
        } else {
            vc.killStreak.reset();
        }
    }

    private static void onKill(@Nullable LivingEntity killer, LivingEntity victim, DamageSource src) {
        Combatant vc = Combatants.get(victim);
        if (vc != null) {
            StyleService.apply(vc, StyleEvent.DEATH);
        }
        Vec3 dir = vc != null && vc.lastHitTick == victim.level().getGameTime() ? vc.lastHitDir : HitFeedback.direction(killer, victim, null);
        HitFeedback.kill(killer, victim, victim.position().add(0, victim.getBbHeight() * 0.6, 0), dir);
        if (killer == null || killer == victim) return;
        Combatant kc = Combatants.get(killer);
        if (kc == null) return;

        NCConfig.Server cfg = NCConfig.SERVER;
        // キル報酬：短時間の回復＋スピードアップ
        killer.heal((float) (cfg.killHeal.get() + kc.upgrades.get(UpgradeStat.KILL_HEAL)));
        if (cfg.killSpeedTicks.get() > 0) {
            killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, cfg.killSpeedTicks.get(), cfg.killSpeedAmplifier.get(), false, false, true));
        }

        List<StyleEvent> ev = new ArrayList<>();
        ev.add(StyleEvent.KILL);
        if (!killer.onGround()) ev.add(StyleEvent.AIR_KILL);
        if (kc.recentlyBlinked(killer.level().getGameTime())) ev.add(StyleEvent.BLINK_KILL);
        if (src.is(NCDamageTypes.BLADE)) ev.add(StyleEvent.SWORD_KILL);
        StyleService.apply(kc, ev.toArray(new StyleEvent[0]));
    }
}
