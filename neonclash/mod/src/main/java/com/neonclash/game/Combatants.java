package com.neonclash.game;

import com.neonclash.NeonClash;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の戦闘者状態の置き場。プレイヤーはログイン時に自動で作られる。
 * モブは試合や GameTest で明示的に {@link #getOrCreate} したものだけが対象になる。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class Combatants {
    private static final Map<UUID, Combatant> STATES = new ConcurrentHashMap<>();

    private Combatants() {
    }

    public static Combatant getOrCreate(LivingEntity entity) {
        Combatant c = STATES.computeIfAbsent(entity.getUUID(), id -> new Combatant(entity));
        c.entity = entity;
        return c;
    }

    @Nullable
    public static Combatant get(@Nullable Entity entity) {
        return entity == null ? null : STATES.get(entity.getUUID());
    }

    @Nullable
    public static Combatant get(UUID id) {
        return STATES.get(id);
    }

    public static Collection<Combatant> all() {
        return STATES.values();
    }

    public static void remove(UUID id) {
        STATES.remove(id);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            getOrCreate(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Combatant c = get(event.getEntity());
        if (c != null && !c.inMatch) {
            remove(c.id);
        }
    }

    /** リスポーンや次元移動でプレイヤーのオブジェクトが作り直されたら付け替える。 */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity le) {
            Combatant c = STATES.get(le.getUUID());
            if (c != null) {
                c.entity = le;
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Combatant c = get(event.getEntity());
        if (c != null && !c.inMatch) {
            c.newLife();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        STATES.values().removeIf(c -> c.entity.isRemoved() && !(c.entity instanceof ServerPlayer) && !c.inMatch);
        for (Combatant c : STATES.values()) {
            if (c.entity.isRemoved()) {
                continue;
            }
            c.tick();
            MovementHandler.apply(c);
            SwordService.tick(c);
            HitFeedback.tickHitStop(c);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        STATES.clear();
    }
}
