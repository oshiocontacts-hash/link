package com.neonclash.compat;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.beat.BeatClock;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.game.BeatService;
import com.neonclash.game.Combatant;
import com.neonclash.game.Combatants;
import com.neonclash.game.FxService;
import com.neonclash.game.HitFeedback;
import com.neonclash.game.StyleService;
import com.neonclash.game.SwordService;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.network.S2CFxPacket;
import com.neonclash.registry.NCSounds;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
        GunItemBuilder b = GunItemBuilder.create().setId(id).setAmmoCount(ammo).setAmmoInBarrel(ammo > 0);
        // TaCZ 自身のアイテム生成と同じく、発射モードは定義の先頭、熱データは定義に従う
        TimelessAPI.getCommonGunIndex(id).ifPresent(idx -> {
            if (!idx.getGunData().getFireModeSet().isEmpty()) b.setFireMode(idx.getGunData().getFireModeSet().get(0));
            b.setHeatData(idx.getGunData().hasHeatData());
        });
        return b.build();
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
        long now = event.getLevel().getGameTime();
        boolean beat = c != null && c.lastOnBeatFireTick == now;
        if (beat) {
            bullet.getPersistentData().putBoolean(TAG_BEAT, true);
        }
        BeamService.track(bullet, beat);
        // 散弾でも 1 回の発射につき 1 回だけ。GunFireEvent は弾切れでも来るので、ここ（本当に弾が出た）で鳴らす
        if (c != null && c.lastShotFxTick != now && event.getLevel() instanceof ServerLevel level) {
            c.lastShotFxTick = now;
            LivingEntity shooter = c.entity();
            FxService.muzzle(level, shooter, bullet.getDeltaMovement().normalize(), c.teamColor(), beat);
            float pitch = 1f + (level.random.nextFloat() - 0.5f) * NCConfig.SERVER.shotPitchVariance.get().floatValue();
            level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), beat ? NCSounds.SHOT_BEAT.get() : NCSounds.SHOT.get(),
                    SoundSource.PLAYERS, NCConfig.SERVER.shotSoundVolume.get().floatValue(), pitch);
        }
    }

    /** 命中・ダメージ計算前：強化とビートボーナスをダメージに反映。 */
    @SubscribeEvent
    public static void onHurtPre(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (event.getHurtEntity() instanceof LivingEntity victim) {
            BeamService.onHit(event.getBullet(), victim);
            Combatant vc = Combatants.get(victim);
            if (vc != null) {
                // 倒した場合、Post より先に CombatEvents の死亡処理が走るので、ここで向きを覚えておく
                vc.lastHitDir = HitFeedback.direction(event.getAttacker(), victim, event.getBullet());
                vc.lastHitTick = victim.level().getGameTime();
            }
        }
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
        int flags = flags(event);
        Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        if (attacker != null) {
            FxService.damageNumber(attacker, at, event.getAmount(), HitFeedback.colorOf(attacker), flags);
        }
        if (vc != null) {
            vc.gunHitsTaken++;
            if (vc.eliminated) {
                return; // 試合中のキル（死亡はキャンセル済み）。キル演出は CombatEvents で済んでいる
            }
            SwordService.interrupt(vc);
        }
        HitFeedback.hit(attacker, victim, at, HitFeedback.direction(attacker, victim, event.getBullet()), flags);
    }

    private static int flags(EntityHurtByGunEvent event) {
        int flags = event.isHeadShot() ? S2CFxPacket.HEAD : 0;
        if (event.getBullet() != null && event.getBullet().getPersistentData().getBoolean(TAG_BEAT)) {
            flags |= S2CFxPacket.BEAT;
        }
        return flags;
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
        HitFeedback.burst(level, at, color, NCConfig.SERVER.hitParticleCount.get() / 2);
        FxService.impact(level, at, Vec3.atLowerCornerOf(event.getHitResult().getDirection().getNormal()), color);
        level.playSound(null, at.x, at.y, at.z, NCSounds.IMPACT.get(), SoundSource.PLAYERS, NCSounds.IMPACT_VOLUME, 1f + (level.random.nextFloat() - 0.5f) * 0.3f);
    }
}
