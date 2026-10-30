package com.neonclash.match;

import com.neonclash.NeonClash;
import com.neonclash.core.round.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 進行中の試合と、/pvp team で決めたチーム分け。 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class MatchRegistry {
    private static final List<MatchController> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Team> ASSIGNMENTS = new HashMap<>();

    private MatchRegistry() {
    }

    public static void add(MatchController m) {
        ACTIVE.add(m);
    }

    public static void remove(MatchController m) {
        ACTIVE.remove(m);
    }

    public static List<MatchController> active() {
        return List.copyOf(ACTIVE);
    }

    public static Optional<MatchController> findFor(UUID id) {
        for (MatchController m : ACTIVE) {
            if (m.contains(id)) return Optional.of(m);
        }
        return Optional.empty();
    }

    public static void assign(UUID player, Team team) {
        ASSIGNMENTS.put(player, team);
    }

    public static Map<UUID, Team> assignments() {
        return Map.copyOf(ASSIGNMENTS);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (MatchController m : List.copyOf(ACTIVE)) {
            m.tick();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        findFor(event.getEntity().getUUID()).ifPresent(m -> m.removeCombatant(event.getEntity().getUUID()));
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (MatchController m : List.copyOf(ACTIVE)) {
            m.stop();
        }
        ACTIVE.clear();
        ASSIGNMENTS.clear();
    }
}
