package com.neonclash.match;

import com.neonclash.compat.TaczBridge;
import com.neonclash.config.NCConfig;
import com.neonclash.core.round.ArenaZone;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.core.round.RoundEngine.Event;
import com.neonclash.core.round.RoundEngine.MatchEnded;
import com.neonclash.core.round.RoundEngine.RoundEnded;
import com.neonclash.core.round.RoundEngine.RoundStarted;
import com.neonclash.core.round.RoundEngine.ShrinkStarted;
import com.neonclash.core.round.Team;
import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.UpgradeDef;
import com.neonclash.core.upgrade.UpgradeDraft;
import com.neonclash.game.Combatant;
import com.neonclash.game.Combatants;
import com.neonclash.game.HitFeedback;
import com.neonclash.game.MovementHandler;
import com.neonclash.game.SlowService;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CHudPacket;
import com.neonclash.network.S2CUpgradeOfferPacket;
import com.neonclash.registry.NCDamageTypes;
import com.neonclash.registry.NCItems;
import com.neonclash.registry.NCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 1試合の進行。ラウンドのルールは core の {@link RoundEngine}、ここはワールドへの反映
 * （スポーン・脱落→観戦・エリア縮小とダメージ・強化3択・HUD 同期・演出）を担当する。
 */
public final class MatchController {
    /**
     * @param giveLoadout ハンドガンと剣を配るか
     * @param spawnOffset 中心から各チームのスポーンまでの距離
     */
    public record Options(boolean giveLoadout, double spawnOffset) {
        public static Options fromConfig() {
            return new Options(true, NCConfig.SERVER.spawnOffset.get());
        }
    }

    private final ServerLevel level;
    private final Vec3 center;
    private final RoundEngine engine;
    private final ArenaZone zone;
    private final Options options;
    private final Random rng = new Random();
    private final Map<UUID, Team> roster;
    private final Map<UUID, GameType> originalModes = new HashMap<>();
    private final Map<UUID, List<UpgradeDef>> offers = new HashMap<>();
    private final Map<UUID, Integer> chosen = new HashMap<>();
    private final Set<UUID> hiddenMobs = new HashSet<>();
    private int ticks;
    private int finishCountdown = -1;
    private boolean stopped;

    private MatchController(ServerLevel level, Vec3 center, Map<UUID, Team> roster, Options options) {
        NCConfig.Server cfg = NCConfig.SERVER;
        this.level = level;
        this.center = center;
        this.roster = new LinkedHashMap<>(roster);
        this.options = options;
        RoundEngine.Settings rs = cfg.roundSettings();
        this.engine = new RoundEngine(rs, this::tieBreak);
        this.zone = new ArenaZone(cfg.arenaRadius.get(), Math.min(cfg.arenaRadius.get(), cfg.arenaEndRadius.get()),
                rs.shrinkStartTick(), rs.roundTicks());
    }

    /** 試合を始める。参加者はプレイヤーでもモブでもよい（GameTest ではモブを使う）。 */
    public static MatchController start(ServerLevel level, Vec3 center, Map<? extends LivingEntity, Team> members, Options options) {
        Map<UUID, Team> roster = new LinkedHashMap<>();
        members.forEach((e, t) -> {
            roster.put(e.getUUID(), t);
            Combatant c = Combatants.getOrCreate(e);
            c.team = t;
            c.inMatch = true;
            c.upgrades.clear();
            c.style.reset();
            c.killStreak.reset();
        });
        MatchController m = new MatchController(level, center, roster, options);
        for (LivingEntity e : members.keySet()) {
            if (e instanceof ServerPlayer sp) {
                m.originalModes.put(sp.getUUID(), sp.gameMode.getGameModeForPlayer());
            }
        }
        MatchRegistry.add(m);
        m.handle(m.engine.start(roster));
        return m;
    }

    public boolean contains(UUID id) {
        return roster.containsKey(id);
    }

    public RoundEngine engine() {
        return engine;
    }

    public Vec3 center() {
        return center;
    }

    @Nullable
    private LivingEntity entity(UUID id) {
        Entity e = level.getEntity(id);
        if (e == null) {
            e = level.getServer().getPlayerList().getPlayer(id);
        }
        return e instanceof LivingEntity le ? le : null;
    }

    private List<LivingEntity> members() {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : roster.keySet()) {
            LivingEntity e = entity(id);
            if (e != null) out.add(e);
        }
        return out;
    }

    // ---------------------------------------------------------------- tick

    public void tick() {
        if (stopped) return;
        ticks++;
        if (finishCountdown >= 0) {
            if (--finishCountdown <= 0) stop();
            return;
        }
        handle(engine.tick());
        if (engine.phase() == RoundEngine.Phase.ROUND) {
            tickZone();
        }
        if (ticks % NCConfig.SERVER.hudSyncInterval.get() == 0) {
            syncHud();
        }
    }

    private void tickZone() {
        int elapsed = engine.elapsedTicks();
        double r = zone.radiusAt(elapsed);
        // 1 秒ごとにエリア外ダメージ
        if (elapsed % 20 == 0 && NCConfig.SERVER.outsideDamagePerSecond.get() > 0) {
            for (UUID id : roster.keySet()) {
                Combatant c = Combatants.get(id);
                LivingEntity e = entity(id);
                if (c == null || e == null || c.eliminated) continue;
                if (zone.isOutside(e.getX() - center.x, e.getZ() - center.z, elapsed)) {
                    e.invulnerableTime = 0;
                    e.hurt(NCDamageTypes.source(level, NCDamageTypes.ZONE), NCConfig.SERVER.outsideDamagePerSecond.get().floatValue());
                }
            }
        }
        // 境界の可視化（縮小中は毎 0.5 秒、それ以外は毎 2 秒）
        int interval = engine.isShrinking() ? 10 : 40;
        if (elapsed % interval == 0) {
            DustParticleOptions p = HitFeedback.dust(NCConfig.parseColor(NCConfig.SERVER.neutralColor.get(), 0xF8F32B));
            int points = Math.max(16, (int) (r * 4));
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                level.sendParticles(p, center.x + Math.cos(a) * r, center.y + 1.0, center.z + Math.sin(a) * r, 1, 0, 0.4, 0, 0);
            }
        }
    }

    // ---------------------------------------------------------------- events

    private void handle(List<Event> events) {
        for (Event ev : events) {
            if (ev instanceof RoundStarted rs) {
                onRoundStart(rs.round());
            } else if (ev instanceof ShrinkStarted) {
                broadcastTitle(Component.literal(""), Component.translatable("neonclash.zone.shrink").withStyle(ChatFormatting.YELLOW), NCSounds.ROUND_END.get());
            } else if (ev instanceof RoundEnded re) {
                onRoundEnd(re);
            } else if (ev instanceof MatchEnded me) {
                onMatchEnd(me);
            }
        }
    }

    private void onRoundStart(int round) {
        // 前のラウンド間に選ばなかった人はランダムに1つ
        for (Map.Entry<UUID, List<UpgradeDef>> e : offers.entrySet()) {
            if (!chosen.containsKey(e.getKey()) && !e.getValue().isEmpty()) {
                choose(e.getKey(), rng.nextInt(e.getValue().size()));
            }
        }
        offers.clear();
        chosen.clear();

        NCConfig.Server cfg = NCConfig.SERVER;
        Map<Team, Integer> index = new HashMap<>();
        for (Map.Entry<UUID, Team> entry : roster.entrySet()) {
            LivingEntity e = entity(entry.getKey());
            Combatant c = Combatants.get(entry.getKey());
            if (e == null || c == null) continue;
            c.newLife();
            restoreFromElimination(e);
            e.setHealth(e.getMaxHealth());
            e.clearFire();
            int i = index.merge(entry.getValue(), 1, Integer::sum) - 1;
            Vec3 spawn = spawnFor(entry.getValue(), i);
            float yaw = entry.getValue() == Team.RED ? -90f : 90f; // 中心の方を向く
            if (e instanceof ServerPlayer sp) {
                sp.teleportTo(level, spawn.x, spawn.y, spawn.z, yaw, 0f);
                sp.setGameMode(GameType.ADVENTURE);
                if (options.giveLoadout()) giveLoadout(sp, cfg);
                NCNetwork.send(sp, new S2CUpgradeOfferPacket(List.of(), 0, 0, -1));
            } else {
                e.teleportTo(spawn.x, spawn.y, spawn.z);
                e.setYRot(yaw);
                e.setYHeadRot(yaw);
            }
            e.setDeltaMovement(Vec3.ZERO);
        }
        broadcastTitle(Component.translatable("neonclash.round", round).withStyle(ChatFormatting.BOLD),
                Component.literal(engine.score(Team.RED) + " - " + engine.score(Team.BLUE)), NCSounds.ROUND_START.get());
    }

    private void onRoundEnd(RoundEnded re) {
        NCConfig.Server cfg = NCConfig.SERVER;
        // ラウンド決着キルだけ全員スロー
        if (re.reason() == RoundEngine.EndReason.ELIMINATION && !re.decisivePlayers().isEmpty()) {
            SlowService.globalSlow(level, cfg.globalSlowFactor.get(), cfg.globalSlowTicks.get());
        }
        Component title = re.isDraw()
                ? Component.translatable("neonclash.round.draw").withStyle(ChatFormatting.WHITE)
                : Component.translatable("neonclash.round.win." + re.winner().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(re.winner() == Team.RED ? ChatFormatting.RED : ChatFormatting.AQUA);
        broadcastTitle(title, Component.literal(engine.score(Team.RED) + " - " + engine.score(Team.BLUE)), NCSounds.ROUND_END.get());
        if (engine.phase() == RoundEngine.Phase.INTERMISSION) {
            offerUpgrades();
        }
    }

    private void onMatchEnd(MatchEnded me) {
        Component title = me.winner() == null
                ? Component.translatable("neonclash.match.draw")
                : Component.translatable("neonclash.match.win." + me.winner().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(me.winner() == Team.RED ? ChatFormatting.RED : ChatFormatting.AQUA, ChatFormatting.BOLD);
        broadcastTitle(title, Component.literal(me.redScore() + " - " + me.blueScore()), NCSounds.ROUND_END.get());
        finishCountdown = engine.settings().intermissionTicks();
    }

    private void offerUpgrades() {
        NCConfig.Server cfg = NCConfig.SERVER;
        List<UpgradeDef> pool = cfg.upgradeDefs();
        for (UUID id : roster.keySet()) {
            Combatant c = Combatants.get(id);
            if (c == null) continue;
            StyleRank rank = c.style.rank();
            Set<String> owned = c.upgrades.owned().stream().map(UpgradeDef::id).collect(Collectors.toSet());
            List<UpgradeDef> list = UpgradeDraft.draw(pool, rank, cfg.rarityWeights(), owned,
                    cfg.upgradeChoices.get(), cfg.upgradeDistinctCategories.get(), rng);
            offers.put(id, list);
            NCNetwork.send(c.entity(), new S2CUpgradeOfferPacket(list, engine.intermissionTicksLeft(), rank.ordinal(), -1));
        }
    }

    /** 強化を選ぶ（ラウンド間のみ・1回だけ）。 */
    public boolean choose(UUID id, int index) {
        List<UpgradeDef> list = offers.get(id);
        if (list == null || index < 0 || index >= list.size() || chosen.containsKey(id)) {
            return false;
        }
        Combatant c = Combatants.get(id);
        if (c == null) return false;
        UpgradeDef def = list.get(index);
        c.upgrades.add(def);
        c.refreshBlinkMax();
        chosen.put(id, index);
        NCNetwork.send(c.entity(), new S2CUpgradeOfferPacket(list, engine.intermissionTicksLeft(), c.style.rank().ordinal(), index));
        return true;
    }

    public List<UpgradeDef> offersFor(UUID id) {
        return offers.getOrDefault(id, List.of());
    }

    /** 試合中の戦闘者が倒れた（死亡はキャンセル済み）。脱落させて観戦へ。 */
    public void onCombatantDown(Combatant vc, @Nullable LivingEntity killer) {
        if (engine.phase() != RoundEngine.Phase.ROUND || vc.eliminated) {
            restoreHealth(vc.entity());
            return;
        }
        vc.eliminated = true;
        vc.killStreak.reset();
        vc.sword.reset();
        LivingEntity e = vc.entity();
        restoreHealth(e);
        if (e instanceof ServerPlayer sp) {
            sp.setGameMode(GameType.SPECTATOR);
            sp.teleportTo(level, center.x, center.y + 6, center.z, sp.getYRot(), 30f);
        } else {
            e.setInvisible(true);
            e.setInvulnerable(true);
            if (e instanceof Mob mob) mob.setNoAi(true);
            hiddenMobs.add(e.getUUID());
        }
        handle(engine.eliminate(vc.id));
    }

    private static void restoreHealth(LivingEntity e) {
        e.setHealth(e.getMaxHealth());
        e.clearFire();
        e.removeAllEffects();
    }

    private void restoreFromElimination(LivingEntity e) {
        if (hiddenMobs.remove(e.getUUID())) {
            e.setInvisible(false);
            e.setInvulnerable(false);
            if (e instanceof Mob mob) mob.setNoAi(false);
        }
    }

    public void removeCombatant(UUID id) {
        GameType original = originalModes.remove(id);
        LivingEntity e = entity(id);
        if (e instanceof ServerPlayer sp && original != null) {
            sp.setGameMode(original);
        }
        Combatant c = Combatants.get(id);
        if (c != null) {
            c.inMatch = false;
            c.team = null;
            c.eliminated = false;
            MovementHandler.apply(c);
        }
        handle(engine.removePlayer(id));
        roster.remove(id);
    }

    public void stop() {
        if (stopped) return;
        stopped = true;
        engine.stop();
        for (UUID id : List.copyOf(roster.keySet())) {
            LivingEntity e = entity(id);
            if (e != null) {
                restoreFromElimination(e);
                if (e instanceof ServerPlayer sp) {
                    GameType original = originalModes.getOrDefault(id, GameType.SURVIVAL);
                    sp.setGameMode(original);
                    NCNetwork.send(sp, new S2CUpgradeOfferPacket(List.of(), 0, 0, -1));
                    NCNetwork.send(sp, emptyHud());
                }
            }
            Combatant c = Combatants.get(id);
            if (c != null) {
                c.inMatch = false;
                c.team = null;
                c.eliminated = false;
                MovementHandler.apply(c);
            }
        }
        MatchRegistry.remove(this);
    }

    public boolean isStopped() {
        return stopped;
    }

    // ---------------------------------------------------------------- helpers

    private double tieBreak(Team team, Set<UUID> alive) {
        double sum = 0;
        for (UUID id : alive) {
            LivingEntity e = entity(id);
            if (e != null) sum += e.getHealth() / e.getMaxHealth();
        }
        return sum;
    }

    private Vec3 spawnFor(Team team, int index) {
        double offset = options.spawnOffset();
        double x = center.x + (team == Team.RED ? -offset : offset);
        double z = center.z + (index - 1.5) * 1.5;
        return safeY(new Vec3(x, center.y, z));
    }

    /** 中心の高さ付近で、足元が固く頭上が空いている高さを探す。 */
    private Vec3 safeY(Vec3 p) {
        BlockPos base = BlockPos.containing(p);
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[]{1, -1}) {
                BlockPos at = base.above(dy * sign);
                if (level.getBlockState(at.below()).isSolid() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                    return new Vec3(p.x, at.getY(), p.z);
                }
            }
        }
        return p;
    }

    private void giveLoadout(ServerPlayer sp, NCConfig.Server cfg) {
        boolean hasBlade = false, hasGun = false;
        for (ItemStack s : sp.getInventory().items) {
            if (s.is(NCItems.NEON_BLADE.get())) hasBlade = true;
            if (com.tacz.guns.api.item.IGun.getIGunOrNull(s) != null) hasGun = true;
        }
        if (!hasGun) {
            ItemStack gun = TaczBridge.makeGun(cfg.handgunId.get(), cfg.handgunAmmo.get());
            if (!gun.isEmpty()) sp.getInventory().add(gun);
            ItemStack ammo = TaczBridge.makeAmmo(cfg.spareAmmoItem.get(), cfg.spareAmmo.get());
            if (!ammo.isEmpty()) sp.getInventory().add(ammo);
        }
        if (!hasBlade) {
            sp.getInventory().add(new ItemStack(NCItems.NEON_BLADE.get()));
        }
    }

    private void broadcastTitle(Component title, Component subtitle, SoundEvent sound) {
        for (LivingEntity e : members()) {
            if (e instanceof ServerPlayer sp && sp.connection != null && !(sp instanceof net.minecraftforge.common.util.FakePlayer)) {
                sp.connection.send(new ClientboundSetTitlesAnimationPacket(2, 30, 8));
                sp.connection.send(new ClientboundSetTitleTextPacket(title));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                HitFeedback.playTo(sp, sound, 1.0f, 1.0f);
            }
        }
    }

    private void syncHud() {
        int phase = engine.phase().ordinal();
        int time = engine.phase() == RoundEngine.Phase.ROUND ? engine.ticksRemaining() : engine.intermissionTicksLeft();
        float radius = (float) zone.radiusAt(engine.elapsedTicks());
        for (UUID id : roster.keySet()) {
            Combatant c = Combatants.get(id);
            if (c == null || !(c.entity() instanceof Player)) continue;
            S2CHudPacket p = new S2CHudPacket(phase, engine.round(), time, engine.score(Team.RED), engine.score(Team.BLUE),
                    engine.settings().winsToWin(), c.team == null ? -1 : c.team.ordinal(),
                    engine.aliveCount(Team.RED), engine.aliveCount(Team.BLUE),
                    c.blink.charges(), c.blink.maxCharges(), c.blink.rechargeProgress(),
                    c.style.rank().ordinal(), c.style.progressToNext(), c.parry.cooldown().progress(),
                    c.lastStand.isAvailable(), c.sword.windupProgress(), engine.isShrinking(), radius,
                    center.x, center.z, (float) c.upgrades.increaseMultiplier(com.neonclash.core.upgrade.UpgradeStat.JUMP));
            NCNetwork.send(c.entity(), p);
        }
    }

    public static S2CHudPacket emptyHud() {
        return new S2CHudPacket(-1, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1f, 0, 0f, 1f, false, 0f, false, 0f, 0, 0, 1f);
    }

    public String describe() {
        return "round " + engine.round() + " " + engine.phase() + " " + engine.score(Team.RED) + "-" + engine.score(Team.BLUE);
    }
}
