package com.neonclash.compat;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.beam.BeamTrail;
import com.neonclash.core.math.Vec3d;
import com.neonclash.game.Combatant;
import com.neonclash.game.Combatants;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CBeamPacket;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * TaCZ の弾を tick ごとに追い、進んだ区間をチーム色のビームとして近くのプレイヤーへ送る。
 * 位置はサーバーの値を使うので、スロー中の弾でも見た目と当たりがずれない。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class BeamService {
    private static final class Tracked {
        final EntityKineticBullet bullet;
        final int color;
        final int ownerId;
        final boolean beat;
        Vec3 last;
        double length;
        @Nullable
        Vec3 hit;

        Tracked(EntityKineticBullet bullet, int color, int ownerId, boolean beat) {
            this.bullet = bullet;
            this.color = color;
            this.ownerId = ownerId;
            this.beat = beat;
            this.last = bullet.position();
        }
    }

    private static final List<Tracked> TRACKED = new ArrayList<>();
    private static long segmentsSent;
    @Nullable
    private static Vec3 lastEnd;

    private BeamService() {
    }

    /** 検証用：これまでに送った区間の数。 */
    public static long segmentsSent() {
        return segmentsSent;
    }

    /** 検証用：直近に終わったビームの終点。 */
    @Nullable
    public static Vec3 lastEnd() {
        return lastEnd;
    }

    public static void track(EntityKineticBullet bullet, boolean beat) {
        if (!NCConfig.SERVER.beamEnabled.get()) return;
        Entity owner = bullet.getOwner();
        Combatant c = Combatants.get(owner);
        int color = c != null ? c.teamColor() : NCConfig.parseColor(NCConfig.SERVER.neutralColor.get(), 0xF8F32B);
        TRACKED.add(new Tracked(bullet, color, owner != null ? owner.getId() : -1, beat));
    }

    /** 命中：弾はこの tick の終わりに 1 tick 分先まで進んでしまうので、相手の位置で止める点を覚えておく。 */
    public static void onHit(@Nullable Entity bullet, LivingEntity victim) {
        if (!(bullet instanceof EntityKineticBullet)) return;
        for (Tracked t : TRACKED) {
            if (t.bullet == bullet) {
                Vec3 mid = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
                Vec3d p = BeamTrail.closestOnSegment(vec(t.last), vec(t.last.add(bullet.getDeltaMovement())), vec(mid));
                t.hit = new Vec3(p.x(), p.y(), p.z());
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        double maxLength = NCConfig.SERVER.beamMaxLength.get();
        for (Iterator<Tracked> it = TRACKED.iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            EntityKineticBullet b = t.bullet;
            boolean gone = b.isRemoved();
            Vec3 end = gone ? endOf(t) : b.position();
            t.hit = null; // 貫通弾はまだ飛んでいるので、次の命中まで持ち越さない
            if (b.level() instanceof ServerLevel level) {
                send(level, t, end);
            }
            t.length += t.last.distanceTo(end);
            t.last = end;
            if (gone || t.length >= maxLength) {
                lastEnd = end;
                it.remove();
            }
        }
    }

    private static Vec3 endOf(Tracked t) {
        if (t.hit != null) {
            return t.hit;
        }
        // ブロックに当たった弾も同じく先へ進んでいるので、壁の手前で切る
        EntityKineticBullet b = t.bullet;
        return b.level().clip(new ClipContext(t.last, b.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b)).getLocation();
    }

    private static void send(ServerLevel level, Tracked t, Vec3 end) {
        if (t.last.distanceToSqr(end) < 1.0e-6) return;
        segmentsSent++;
        NCNetwork.sendNear(level, end, NCConfig.SERVER.beamViewDistance.get(), new S2CBeamPacket(
                t.last.x, t.last.y, t.last.z, end.x, end.y, end.z, t.color, t.ownerId, t.beat));
    }

    private static Vec3d vec(Vec3 v) {
        return new Vec3d(v.x, v.y, v.z);
    }
}
