package com.neonclash.game;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.slow.SlowZone;
import com.neonclash.core.slow.SlowZones;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CSlowMoPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * スロー。
 * <ul>
 *   <li>範囲スロー：球の中のエンティティ（移動速度）と弾（速度ベクトル）を倍率で下げる。出れば元に戻る</li>
 *   <li>個別スロー：パリィされた相手など</li>
 *   <li>全員スロー：ラウンド決着キル時。サーバーから全員に演出パケットを送る</li>
 * </ul>
 * 生き物は MOVEMENT_SPEED の属性モディファイア（クライアントに同期される）、
 * 弾は速度ベクトルを「今掛かっている倍率 → 欲しい倍率」で掛け直す（persistentData に今の倍率を記録）。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class SlowService {
    public static final UUID SLOW_ID = UUID.fromString("0b9a3f55-7d1e-4b5e-8a6c-3c6d1f2e9b77");
    private static final String SLOW_NAME = "neonclash.slow";
    private static final String TAG_APPLIED = "neonclash_slow";

    private record Timed(double factor, long until) {
    }

    private static final Map<ResourceKey<Level>, SlowZones> ZONES = new HashMap<>();
    private static final Map<UUID, Timed> ENTITY_SLOWS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Timed> GLOBAL = new HashMap<>();
    /** 現在モディファイアや倍率を掛けているエンティティ（範囲外に出たら戻すため） */
    private static final Map<ResourceKey<Level>, Set<UUID>> AFFECTED = new HashMap<>();

    private SlowService() {
    }

    public static void addZone(ServerLevel level, Vec3 center, double radius, double factor, int ticks) {
        if (radius <= 0 || ticks <= 0) return;
        ZONES.computeIfAbsent(level.dimension(), k -> new SlowZones())
                .add(new SlowZone(Vecs.core(center), radius, factor, level.getGameTime() + ticks));
    }

    public static void slowEntity(LivingEntity e, double factor, int ticks) {
        if (ticks <= 0) return;
        long until = e.level().getGameTime() + ticks;
        ENTITY_SLOWS.merge(e.getUUID(), new Timed(factor, until),
                (a, b) -> new Timed(Math.min(a.factor, b.factor), Math.max(a.until, b.until)));
    }

    /** 全員スロー（ラウンド決着キル）。弾も含めてレベル内の全員。演出パケットも送る。 */
    public static void globalSlow(ServerLevel level, double factor, int ticks) {
        if (ticks <= 0) return;
        GLOBAL.put(level.dimension(), new Timed(factor, level.getGameTime() + ticks));
        NCNetwork.sendToLevel(level, new S2CSlowMoPacket(ticks, NCConfig.SERVER.slowMoClientStrength.get().floatValue()));
    }

    /** 自分以外の全員（半径内）をスロー。ラストスタンド用。 */
    public static void slowAllExcept(ServerLevel level, LivingEntity self, double radius, double factor, int ticks) {
        AABB box = self.getBoundingBox().inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t != self && t.isAlive())) {
            slowEntity(e, factor, ticks);
        }
        // 弾も遅くする（自分の弾は除く）：自分を中心にした範囲スローとして扱い、自分の弾は tick で除外
        addZone(level, self.position(), radius, factor, ticks);
        EXEMPT_OWNERS.put(self.getUUID(), level.getGameTime() + ticks);
        S2CSlowMoPacket pkt = new S2CSlowMoPacket(ticks, NCConfig.SERVER.slowMoClientStrength.get().floatValue());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(self) <= radius * radius) {
                NCNetwork.send(p, pkt);
            }
        }
    }

    /** ラストスタンド中の本人とその弾は範囲スローの対象外。 */
    private static final Map<UUID, Long> EXEMPT_OWNERS = new HashMap<>();

    /** 今この位置に掛かるべき倍率（1.0 = スロー無し）。 */
    public static double factorFor(ServerLevel level, Entity e) {
        long now = level.getGameTime();
        double f = 1.0;
        UUID owner = e instanceof Projectile p && p.getOwner() != null ? p.getOwner().getUUID() : e.getUUID();
        Long exempt = EXEMPT_OWNERS.get(owner);
        boolean exempted = exempt != null && now < exempt;
        SlowZones zones = ZONES.get(level.dimension());
        if (zones != null && !exempted) {
            f = Math.min(f, zones.factorAt(Vecs.core(e.position()), now));
        }
        Timed t = ENTITY_SLOWS.get(e.getUUID());
        if (t != null && now < t.until) {
            f = Math.min(f, t.factor);
        }
        Timed g = GLOBAL.get(level.dimension());
        if (g != null && now < g.until) {
            f = Math.min(f, g.factor);
        }
        return f;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        SlowZones zones = ZONES.get(level.dimension());
        if (zones != null) zones.prune(now);
        ENTITY_SLOWS.values().removeIf(t -> now >= t.until);
        EXEMPT_OWNERS.values().removeIf(t -> now >= t);
        Timed g = GLOBAL.get(level.dimension());
        boolean globalActive = g != null && now < g.until;
        if (g != null && !globalActive) GLOBAL.remove(level.dimension());

        Set<UUID> affected = AFFECTED.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        Set<Entity> candidates = new HashSet<>();
        if (globalActive) {
            level.getAllEntities().forEach(candidates::add);
        } else {
            if (zones != null) {
                for (SlowZone z : zones.active()) {
                    double r = z.radius();
                    AABB box = new AABB(z.center().x() - r, z.center().y() - r, z.center().z() - r,
                            z.center().x() + r, z.center().y() + r, z.center().z() + r);
                    candidates.addAll(level.getEntities((Entity) null, box, en -> en instanceof LivingEntity || en instanceof Projectile));
                }
            }
            for (UUID id : ENTITY_SLOWS.keySet()) {
                Entity en = level.getEntity(id);
                if (en != null) candidates.add(en);
            }
        }
        for (Iterator<UUID> it = affected.iterator(); it.hasNext(); ) {
            Entity en = level.getEntity(it.next());
            if (en == null) {
                it.remove();
            } else {
                candidates.add(en);
            }
        }
        for (Entity en : candidates) {
            if (!(en instanceof LivingEntity) && !(en instanceof Projectile)) continue;
            double f = factorFor(level, en);
            apply(en, f);
            if (f < 1.0) {
                affected.add(en.getUUID());
            } else {
                affected.remove(en.getUUID());
            }
        }
    }

    private static void apply(Entity e, double factor) {
        if (e instanceof LivingEntity le) {
            AttributeInstance attr = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr == null) return;
            AttributeModifier cur = attr.getModifier(SLOW_ID);
            if (factor >= 1.0) {
                if (cur != null) attr.removeModifier(SLOW_ID);
                return;
            }
            double amount = factor - 1.0;
            if (cur == null || Math.abs(cur.getAmount() - amount) > 1.0e-6) {
                if (cur != null) attr.removeModifier(SLOW_ID);
                attr.addTransientModifier(new AttributeModifier(SLOW_ID, SLOW_NAME, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else if (e instanceof Projectile p) {
            CompoundTag data = p.getPersistentData();
            double applied = data.contains(TAG_APPLIED) ? data.getDouble(TAG_APPLIED) : 1.0;
            if (Math.abs(applied - factor) < 1.0e-6) return;
            p.setDeltaMovement(p.getDeltaMovement().scale(factor / applied));
            if (factor >= 1.0) {
                data.remove(TAG_APPLIED);
            } else {
                data.putDouble(TAG_APPLIED, factor);
            }
            p.hurtMarked = true; // 速度をクライアントへ送る
        }
    }

    /** GameTest 用：エンティティに今スローモディファイアが掛かっているか。 */
    public static boolean isSlowed(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        return attr != null && attr.getModifier(SLOW_ID) != null;
    }

    public static void clearAll() {
        ZONES.clear();
        ENTITY_SLOWS.clear();
        GLOBAL.clear();
        EXEMPT_OWNERS.clear();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        clearAll();
        AFFECTED.clear();
    }
}
