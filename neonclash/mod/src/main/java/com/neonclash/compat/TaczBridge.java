package com.neonclash.compat;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.beat.BeatClock;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.game.BeatService;
import com.neonclash.game.Combatant;
import com.neonclash.game.Combatants;
import com.neonclash.game.HitFeedback;
import com.neonclash.game.StyleService;
import com.neonclash.game.SwordService;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * TaCZ（com.tacz.guns.api）との連携。使っているイベントは TaCZ 1.20.1 ブランチのソースで確認したもの：
 * <ul>
 *   <li>{@link GunFireEvent}（発射。サーバーでは弾薬チェック前・弾の生成直前に同じ呼び出しの中で投げられる）</li>
 *   <li>{@link EntityHurtByGunEvent.Pre}（命中・ダメージ計算前。setBaseAmount で変更可）</li>
 *   <li>{@link EntityHurtByGunEvent.Post}（命中して死ななかった）</li>
 *   <li>{@link EntityKillByGunEvent}（命中して死んだ）</li>
 *   <li>{@link AmmoHitBlockEvent}（ブロックに着弾）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class TaczBridge {
    private static final String TAG_BEAT = "neonclash_beat";

    private TaczBridge() {
    }

    public static boolean isBullet(@Nullable Entity e) {
        return e instanceof EntityKineticBullet;
    }

    public static ItemStack makeGun(String gunId, int ammo) {
        ResourceLocation id = ResourceLocation.tryParse(gunId);
        if (id == null) return ItemStack.EMPTY;
        return GunItemBuilder.create().setId(id).setAmmoCount(ammo).setAmmoInBarrel(ammo > 0).build();
    }

    public static ItemStack makeAmmo(String ammoId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(ammoId);
        if (id == null || count <= 0) return ItemStack.EMPTY;
        return AmmoItemBuilder.create().setId(id).setCount(count).build();
    }

    /** 発射：ビート判定。乗っていたらこの tick に生成される弾に印を付ける。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || event.isCanceled()) return;
        LivingEntity shooter = event.getShooter();
        Combatant c = Combatants.get(shooter);
        if (c == null) return;
        BeatClock.Judgement j = BeatService.judge(shooter);
        if (j.onBeat()) {
            c.lastOnBeatFireTick = shooter.level().getGameTime();
            StyleService.apply(c, StyleEvent.ON_BEAT);
            NCNetwork.send(shooter, S2CFeedbackPacket.of(S2CFeedbackPacket.Kind.BEAT_HIT));
        } else {
            c.lastOnBeatFireTick = Long.MIN_VALUE;
        }
    }

    @SubscribeEvent
    public static void onBulletSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityKineticBullet bullet)) return;
        Combatant c = Combatants.get(bullet.getOwner());
        if (c != null && c.lastOnBeatFireTick == event.getLevel().getGameTime()) {
            bullet.getPersistentData().putBoolean(TAG_BEAT, true);
        }
    }

    /** 命中・ダメージ計算前：強化とビートボーナスをダメージに反映。 */
    @SubscribeEvent
    public static void onHurtPre(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        Combatant ac = Combatants.get(event.getAttacker());
        if (ac == null) return;
        double mult = ac.upgrades.increaseMultiplier(UpgradeStat.GUN_DAMAGE);
        if (event.getBullet() != null && event.getBullet().getPersistentData().getBoolean(TAG_BEAT)) {
            mult *= 1.0 + NCConfig.SERVER.beatDamageBonus.get() + ac.upgrades.get(UpgradeStat.BEAT_DAMAGE);
        }
        if (mult != 1.0) {
            event.setBaseAmount((float) (event.getBaseAmount() * mult));
        }
    }

    /** 命中（死ななかった）：ヒットストップ・音・パーティクル、剣の溜めを潰す。 */
    @SubscribeEvent
    public static void onHurtPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getHurtEntity() instanceof LivingEntity victim)) return;
        LivingEntity attacker = event.getAttacker();
        Combatant vc = Combatants.get(victim);
        Combatant ac = Combatants.get(attacker);
        if (ac != null) ac.gunHitsDealt++;
        if (vc != null) {
            vc.gunHitsTaken++;
            if (vc.eliminated) {
                return; // 試合中のキル（死亡はキャンセル済み）。キル演出は CombatEvents で済んでいる
            }
            SwordService.interrupt(vc);
        }
        HitFeedback.hit(attacker, victim, hitPos(event.getBullet(), victim));
    }

    /** 命中して死んだ（試合外）。キル演出・報酬は LivingDeathEvent 側で共通処理済みなので数えるだけ。 */
    @SubscribeEvent
    public static void onKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        Combatant vc = Combatants.get(event.getKilledEntity());
        Combatant ac = Combatants.get(event.getAttacker());
        if (vc != null) vc.gunHitsTaken++;
        if (ac != null) ac.gunHitsDealt++;
    }

    /** ブロックへの着弾もチームカラーで光らせる。 */
    @SubscribeEvent
    public static void onBlockHit(AmmoHitBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Combatant c = Combatants.get(event.getAmmo().getOwner());
        int color = c != null ? c.teamColor() : NCConfig.parseColor(NCConfig.SERVER.neutralColor.get(), 0xF8F32B);
        Vec3 at = event.getHitResult().getLocation();
        HitFeedback.burst(level, at, color, Math.max(1, NCConfig.SERVER.hitParticleCount.get() / 2));
    }

    private static Vec3 hitPos(@Nullable Entity bullet, LivingEntity victim) {
        return bullet != null ? bullet.position() : victim.position().add(0, victim.getBbHeight() * 0.6, 0);
    }
}
