import com.neonclash.core.beat.BeatClock;
import com.neonclash.core.combat.KillStreak;
import com.neonclash.core.combat.SwordCharge;
import com.neonclash.core.math.Box;
import com.neonclash.core.math.CollisionQuery;
import com.neonclash.core.math.Vec3d;
import com.neonclash.core.movement.BlinkCharges;
import com.neonclash.core.movement.BlinkResolver;
import com.neonclash.core.round.ArenaZone;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.core.round.Team;
import com.neonclash.core.skill.LastStand;
import com.neonclash.core.skill.ParrySkill;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.style.StyleMeter;
import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.Rarity;
import com.neonclash.core.upgrade.RarityWeights;
import com.neonclash.core.upgrade.UpgradeDef;
import com.neonclash.core.upgrade.UpgradeDraft;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.core.upgrade.UpgradeStats;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

/**
 * 簡易プレイテスト：core のロジックをそのまま使って 2v2 を俯瞰で動かし、動画にする。
 * Minecraft の実機映像ではない（Forge / Minecraft を取得できない環境のための代替）。
 * 数値は mod の config 既定値と同じ（ラウンド時間・先取数だけ動画用に短縮）。
 *
 * 使い方: javac -cp neonclash-core.jar PlaytestSim.java && java -cp neonclash-core.jar:. PlaytestSim out.mp4
 */
public class PlaytestSim {
    // ---- 動画
    static final int W = 1280, H = 720, FPS = 20;
    static final double SCALE = 15.0; // px / block
    static final int ARENA = 40;
    static final double OX = W / 2.0 - ARENA * SCALE / 2, OY = 80;

    // ---- config 既定値（mod の NCConfig と同じ）
    static final double SPEED = 0.215 * 1.3;     // スプリント相当 × speedMultiplier
    static final BlinkResolver.Settings BLINK = new BlinkResolver.Settings(6.0, 0.25, 0.6, 0.6, 0.75);
    static final int BLINK_CD = 50, BLINK_KILL_WINDOW = 30;
    static final int WINDUP = 6, RECOVERY = 12, LOCKOUT = 4;
    static final double SWORD_RANGE = 2.8, SWORD_CONE = Math.cos(Math.toRadians(45)), LUNGE = 0.45;
    static final int HITSTOP = 3, SHAKE_TICKS = 5;
    static final double HIT_SHAKE = 0.8, KILL_SHAKE = 2.0;
    static final double GUN_DAMAGE = 5.0, BEAT_BONUS = 0.2, BEAT_CD_MULT = 0.6;
    static final int FIRE_INTERVAL = 7;
    static final double BULLET_SPEED = 3.0;
    static final double KILL_HEAL = 6.0;
    static final int KILL_SPEED_TICKS = 40;
    static final double GLOBAL_SLOW = 0.3; static final int GLOBAL_SLOW_TICKS = 10;
    static final double LS_FACTOR = 0.35; static final int LS_TICKS = 60; static final double LS_HEALTH = 4.0;
    static final double PARRY_TARGET = 0.4; static final int PARRY_TARGET_TICKS = 30;
    static final ParrySkill.Settings PARRY = new ParrySkill.Settings(6, 100, 0.5);
    static final KillStreak.Settings STREAK = new KillStreak.Settings(160, 0.9f, 0.15f, 2.0f);
    static final StyleMeter.Settings STYLE = new StyleMeter.Settings(new int[]{100, 250, 450, 700}, 1000, Map.of(
            StyleEvent.KILL, 60, StyleEvent.AIR_KILL, 60, StyleEvent.BLINK_KILL, 70, StyleEvent.SWORD_KILL, 90,
            StyleEvent.ON_BEAT, 10, StyleEvent.PARRY, 40, StyleEvent.DAMAGE_TAKEN, -50, StyleEvent.DEATH, -200));
    // 動画用に短縮：ラウンド 30 秒（20 秒で縮小開始）、ラウンド間 4 秒。先取数は既定どおり 3 本
    static final RoundEngine.Settings ROUND = new RoundEngine.Settings(30 * 20, 20 * 20, 4 * 20, 3, 7);
    static final ArenaZone ZONE = new ArenaZone(19, 6, ROUND.shrinkStartTick(), ROUND.roundTicks());
    static final double ZONE_DPS = 3.0;
    static final RarityWeights WEIGHTS = new RarityWeights()
            .set(StyleRank.D, 70, 25, 5, 0).set(StyleRank.C, 55, 30, 12, 3).set(StyleRank.B, 40, 35, 20, 5)
            .set(StyleRank.A, 25, 35, 28, 12).set(StyleRank.S, 10, 30, 35, 25);
    static final List<UpgradeDef> POOL = List.of(
            "gun_dmg_s|WEAPON|COMMON|GUN_DAMAGE|0.10|true", "gun_dmg_m|WEAPON|RARE|GUN_DAMAGE|0.20|true",
            "beat_dmg|WEAPON|EPIC|BEAT_DAMAGE|0.30|false", "gun_dmg_l|WEAPON|LEGENDARY|GUN_DAMAGE|0.45|false",
            "blade_quick_s|SWORD|COMMON|SWORD_WINDUP|0.15|true", "blade_reach|SWORD|RARE|SWORD_RANGE|0.6|true",
            "blade_quick_l|SWORD|EPIC|SWORD_WINDUP|0.35|false", "blade_long|SWORD|LEGENDARY|SWORD_RANGE|1.4|false",
            "stride|MOVEMENT|COMMON|MOVE_SPEED|0.06|true", "spring|MOVEMENT|COMMON|JUMP|0.10|true",
            "blink_far|MOVEMENT|RARE|BLINK_DISTANCE|1.5|true", "blink_cd|MOVEMENT|EPIC|BLINK_COOLDOWN|0.25|false",
            "blink_extra|MOVEMENT|LEGENDARY|BLINK_CHARGES|1|false", "parry_wide|ULTIMATE|COMMON|PARRY_WINDOW|2|true",
            "vamp|ULTIMATE|RARE|KILL_HEAL|4|true", "stand_long|ULTIMATE|EPIC|LAST_STAND_DURATION|30|false",
            "stand_tough|ULTIMATE|LEGENDARY|LAST_STAND_HEALTH|8|false").stream().map(UpgradeDef::parse).toList();
    static final Map<String, String> UP_NAME = Map.ofEntries(
            Map.entry("gun_dmg_s", "Hot Rounds"), Map.entry("gun_dmg_m", "Overcharge"), Map.entry("beat_dmg", "Downbeat"),
            Map.entry("gun_dmg_l", "Railheart"), Map.entry("blade_quick_s", "Quickdraw"), Map.entry("blade_reach", "Long Edge"),
            Map.entry("blade_quick_l", "Iaido"), Map.entry("blade_long", "Laser Sabre"), Map.entry("stride", "Stride"),
            Map.entry("spring", "Spring Heels"), Map.entry("blink_far", "Far Step"), Map.entry("blink_cd", "Flicker"),
            Map.entry("blink_extra", "Phase Cell"), Map.entry("parry_wide", "Wide Guard"), Map.entry("vamp", "Siphon"),
            Map.entry("stand_long", "Time Dilation"), Map.entry("stand_tough", "Unbroken"));

    static final Color RED = new Color(0xFF2E63), BLUE = new Color(0x00E5FF), YELLOW = new Color(0xF8F32B);
    static final Color BG = new Color(0x0B0D12), GRIDC = new Color(0x161A22), WALL = new Color(0x1E222B);

    // ---- 世界
    static final boolean[][] SOLID = new boolean[ARENA][ARENA];
    static final CollisionQuery WORLD = b -> {
        if (b.minY() < 0) return false;
        for (int x = (int) Math.floor(b.minX()); x < Math.ceil(b.maxX()); x++)
            for (int z = (int) Math.floor(b.minZ()); z < Math.ceil(b.maxZ()); z++) {
                if (x < 0 || z < 0 || x >= ARENA || z >= ARENA) return false;
                if (SOLID[x][z] && b.minY() < 3) return false;
            }
        return true;
    };

    static final Random RNG = new Random(20261030L);
    static final BeatClock BEAT = new BeatClock(128, 0, 100, true);

    static final class Bot {
        final String name; final Team team; final UUID id = UUID.randomUUID();
        double x, z, yaw, hp = 20, vx, vz;
        final BlinkCharges blink = new BlinkCharges(2);
        final SwordCharge sword = new SwordCharge();
        final ParrySkill parry = new ParrySkill();
        final LastStand lastStand = new LastStand();
        final StyleMeter style = new StyleMeter(STYLE);
        final KillStreak streak = new KillStreak();
        final UpgradeStats up = new UpgradeStats();
        boolean dead; int hitStop, fireCd, speedBoost, lsSlowOwner, rankPop, deathFlash;
        long lastBlink = -1000; double slowFactor = 1; int slowTicks;
        List<UpgradeDef> offer = List.of(); int chosen = -1;
        boolean aggressiveSword;
        Bot(String n, Team t, boolean sw) { name = n; team = t; aggressiveSword = sw; }
        Color color() { return team == Team.RED ? RED : BLUE; }
        Vec3d pos() { return new Vec3d(x, 0, z); }
    }

    record Bullet(Bot owner, double[] p, double dx, double dz, boolean beat, int[] life) {}
    record Fx(String kind, double x, double z, double x2, double z2, Color c, int[] t, int total, String text) {}

    static final List<Bot> bots = new ArrayList<>();
    static final List<Bullet> bullets = new ArrayList<>();
    static final List<Fx> fx = new ArrayList<>();
    static final Deque<String[]> feed = new ArrayDeque<>();
    static RoundEngine engine;
    static long tick;
    static int globalSlow, shake; static double shakeMag;
    static String banner = ""; static int bannerTicks; static Color bannerColor = Color.WHITE;
    static boolean matchOver;
    static final Map<String, Integer> STATS = new TreeMap<>();
    static final List<String> ROUND_LOG = new ArrayList<>();
    static long roundStartTick;
    static void stat(String k) { STATS.merge(k, 1, Integer::sum); }

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "playtest.mp4";
        buildArena();
        bots.add(new Bot("RED-1", Team.RED, true));
        bots.add(new Bot("RED-2", Team.RED, false));
        bots.add(new Bot("BLUE-1", Team.BLUE, true));
        bots.add(new Bot("BLUE-2", Team.BLUE, false));
        engine = new RoundEngine(ROUND, (t, alive) -> bots.stream().filter(b -> alive.contains(b.id)).mapToDouble(b -> b.hp / 20).sum());

        Process ff = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24",
                "-s", W + "x" + H, "-r", String.valueOf(FPS), "-i", "-", "-vf", "fps=30", "-c:v", "libx264",
                "-pix_fmt", "yuv420p", "-crf", "20", "-movflags", "+faststart", out)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        OutputStream os = ff.getOutputStream();
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);

        for (int i = 0; i < FPS * 4; i++) { drawTitle(img, i); write(os, img); }
        Map<UUID, Team> roster = new LinkedHashMap<>();
        for (Bot b : bots) roster.put(b.id, b.team);
        handle(engine.start(roster));
        int after = -1;
        while (tick < 20L * 60 * 4) {
            step();
            draw(img);
            write(os, img);
            if (matchOver && after < 0) after = FPS * 4;
            if (after >= 0 && --after <= 0) break;
        }
        os.close();
        int code = ff.waitFor();
        ROUND_LOG.forEach(System.out::println);
        System.out.println("final " + engine.score(Team.RED) + "-" + engine.score(Team.BLUE));
        STATS.forEach((k, v) -> System.out.println(k + " " + v));
        for (Bot b : bots) System.out.println(b.name + " rank=" + b.style.rank() + " points=" + b.style.points() + " upgrades=" + b.up.owned().stream().map(UpgradeDef::id).toList());
        System.exit(code);
    }

    static void write(OutputStream os, BufferedImage img) throws Exception {
        os.write(((DataBufferByte) img.getRaster().getDataBuffer()).getData());
    }

    static void buildArena() {
        int[][] pillars = {{12, 12}, {27, 12}, {12, 27}, {27, 27}, {19, 8}, {19, 31}, {8, 19}, {31, 19}};
        for (int[] p : pillars) for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) SOLID[p[0] + dx][p[1] + dz] = true;
        for (int z = 16; z <= 23; z++) { SOLID[16][z] = z != 19 && z != 20; SOLID[23][z] = z != 19 && z != 20; } // 中央の遮蔽
        for (int i = 0; i < ARENA; i++) { SOLID[i][0] = SOLID[i][ARENA - 1] = SOLID[0][i] = SOLID[ARENA - 1][i] = true; }
    }

    // ================================================================ simulation

    static void step() {
        tick++;
        double nowMs = tick * 50 + RNG.nextGaussian() * 30; // 入力タイミングの揺らぎ
        handle(engine.tick());
        if (globalSlow > 0) globalSlow--;
        if (shake > 0) shake--;
        if (bannerTicks > 0) bannerTicks--;
        boolean live = engine.phase() == RoundEngine.Phase.ROUND;
        List<UUID> downed = new ArrayList<>();
        for (Bot b : bots) {
            b.blink.tick(); b.parry.tick();
            if (b.hitStop > 0) b.hitStop--;
            if (b.speedBoost > 0) b.speedBoost--;
            if (b.slowTicks > 0 && --b.slowTicks == 0) b.slowFactor = 1;
            if (b.rankPop > 0) b.rankPop--;
            if (b.deathFlash > 0) b.deathFlash--;
            if (b.lsSlowOwner > 0) b.lsSlowOwner--;
            if (b.fireCd > 0) b.fireCd--;
            if (b.dead || !live) continue;
            if (b.sword.tick()) swing(b);
            think(b, nowMs);
            // エリア外ダメージ
            if (engine.elapsedTicks() % 20 == 0 && ZONE.isOutside(b.x - ARENA / 2.0, b.z - ARENA / 2.0, engine.elapsedTicks())) {
                damage(null, b, ZONE_DPS, false, false);
            }
        }
        // 弾
        for (Iterator<Bullet> it = bullets.iterator(); it.hasNext(); ) {
            Bullet bl = it.next();
            double f = Math.min(globalSlow > 0 ? GLOBAL_SLOW : 1, lsFactorFor(bl.owner));
            double nx = bl.p[0] + bl.dx * BULLET_SPEED * f, nz = bl.p[1] + bl.dz * BULLET_SPEED * f;
            Bot hit = null; double best = 1e9;
            for (Bot t : bots) {
                if (t.dead || t.team == bl.owner.team) continue;
                double d = segDist(bl.p[0], bl.p[1], nx, nz, t.x, t.z);
                if (d < 0.4) { double dd = Math.hypot(t.x - bl.p[0], t.z - bl.p[1]); if (dd < best) { best = dd; hit = t; } }
            }
            boolean wall = !WORLD.isFree(new Box(nx - 0.05, 1, nz - 0.05, nx + 0.05, 1.1, nz + 0.05));
            fx.add(new Fx("tracer", bl.p[0], bl.p[1], nx, nz, bl.owner.color(), new int[]{3}, 3, null));
            if (hit != null) {
                double dmg = GUN_DAMAGE * bl.owner.up.increaseMultiplier(UpgradeStat.GUN_DAMAGE)
                        * (bl.beat ? 1 + BEAT_BONUS + bl.owner.up.get(UpgradeStat.BEAT_DAMAGE) : 1);
                stat("gun_hits");
                damage(bl.owner, hit, dmg, true, false);
                it.remove();
            } else if (wall || --bl.life[0] <= 0) {
                if (wall) burst(nx, nz, bl.owner.color(), 5);
                it.remove();
            } else { bl.p[0] = nx; bl.p[1] = nz; }
        }
        for (Bot b : bots) if (b.hp <= 0 && !b.dead) downed.add(b.id);
        fx.removeIf(e -> --e.t()[0] <= 0);
    }

    static double lsFactorFor(Bot b) {
        double f = 1;
        for (Bot o : bots) if (o != b && o.lsSlowOwner > 0) f = Math.min(f, LS_FACTOR);
        return f;
    }

    static double speedFactor(Bot b) {
        double f = b.slowFactor;
        if (globalSlow > 0) f = Math.min(f, GLOBAL_SLOW);
        f = Math.min(f, lsFactorFor(b));
        return f;
    }

    static void think(Bot b, double nowMs) {
        Bot target = null; double best = 1e9;
        for (Bot t : bots) if (!t.dead && t.team != b.team) { double d = dist(b, t); if (d < best) { best = d; target = t; } }
        if (target == null) return;
        double dx = target.x - b.x, dz = target.z - b.z, d = Math.hypot(dx, dz);
        double desiredYaw = Math.atan2(dz, dx);
        b.yaw = lerpAngle(b.yaw, desiredYaw, 0.35);
        double lx = Math.cos(b.yaw), lz = Math.sin(b.yaw);

        // パリィ：相手が溜めている／撃ってきそう
        if (b.parry.cooldown().ready() && target.sword.isWindingUp() && d < 4 && RNG.nextDouble() < 0.35) {
            b.parry.tryActivate(new ParrySkill.Settings(PARRY.windowTicks() + (int) b.up.get(UpgradeStat.PARRY_WINDOW), PARRY.cooldownTicks(), PARRY.successCooldownMultiplier()), 1.0);
            fx.add(new Fx("ring", b.x, b.z, 0, 0, Color.WHITE, new int[]{6}, 6, null));
        }
        // 剣
        double range = SWORD_RANGE + b.up.get(UpgradeStat.SWORD_RANGE);
        if (d < range + 0.3 && !b.sword.isWindingUp() && b.sword.state() == SwordCharge.State.IDLE && (b.aggressiveSword || RNG.nextDouble() < 0.3)) {
            int w = (int) Math.max(1, Math.round(WINDUP * b.up.reductionMultiplier(UpgradeStat.SWORD_WINDUP, 0.25)));
            b.sword.startWindup(w, RECOVERY, LOCKOUT);
        }
        // ブリンク：剣持ちは詰める、銃持ちは被弾後に横へ逃げる
        boolean wantBlink = b.aggressiveSword ? (d > 4 && d < 9 && RNG.nextDouble() < 0.08) : (b.hitStop > 0 && RNG.nextDouble() < 0.5);
        if (wantBlink && b.blink.charges() > 0 && b.hitStop == 0) {
            double side = b.aggressiveSword ? 0 : (RNG.nextBoolean() ? 1 : -1);
            Vec3d look = new Vec3d(lx - lz * side * 1.4, 0, lz + lx * side * 1.4).normalize();
            BlinkResolver.Settings s = new BlinkResolver.Settings(BLINK.distance() + b.up.get(UpgradeStat.BLINK_DISTANCE), BLINK.step(), BLINK.maxStepUp(), BLINK.maxCeilingDrop(), BLINK.minDistance());
            Optional<BlinkResolver.Result> r = BlinkResolver.resolve(b.pos(), look, 0.6, 1.8, s, WORLD);
            if (r.isPresent()) {
                boolean onBeat = BEAT.judge(nowMs).onBeat();
                double cd = BLINK_CD * b.up.reductionMultiplier(UpgradeStat.BLINK_COOLDOWN, 0.25) * (onBeat ? BEAT_CD_MULT : 1);
                b.blink.tryConsume((int) Math.round(cd));
                fx.add(new Fx("blink", b.x, b.z, r.get().destination().x(), r.get().destination().z(), b.color(), new int[]{8}, 8, null));
                b.x = r.get().destination().x(); b.z = r.get().destination().z();
                b.vx = look.x() * 0.35; b.vz = look.z() * 0.35;
                b.lastBlink = tick;
                stat("blinks"); if (onBeat) stat("blinks_on_beat");
                if (onBeat) styleApply(b, StyleEvent.ON_BEAT);
            }
        }
        // 射撃
        if (!b.sword.isWindingUp() && b.fireCd == 0 && d < 22 && hasLos(b, target) && (!b.aggressiveSword || d > range + 1.5)) {
            boolean onBeat = BEAT.judge(nowMs).onBeat();
            double spread = Math.toRadians(RNG.nextGaussian() * 3.2);
            double a = Math.atan2(dz, dx) + spread;
            bullets.add(new Bullet(b, new double[]{b.x + Math.cos(a) * 0.5, b.z + Math.sin(a) * 0.5}, Math.cos(a), Math.sin(a), onBeat, new int[]{12}));
            b.fireCd = FIRE_INTERVAL + RNG.nextInt(3);
            stat("shots"); if (onBeat) stat("shots_on_beat");
            if (onBeat) { styleApply(b, StyleEvent.ON_BEAT); fx.add(new Fx("beat", b.x, b.z, 0, 0, Color.WHITE, new int[]{5}, 5, null)); }
        }
        // 移動（ヒットストップ中は止まる）
        if (b.hitStop > 0) { b.vx = b.vz = 0; return; }
        double want = b.aggressiveSword ? 1.6 : 9.0;
        double fwd = d > want ? 1 : (d < want - 2 ? -0.6 : 0);
        double strafe = Math.sin(tick * 0.07 + b.name.hashCode()) * 0.8;
        double mx = lx * fwd - lz * strafe, mz = lz * fwd + lx * strafe;
        double len = Math.hypot(mx, mz);
        if (len > 1e-6) { mx /= len; mz /= len; }
        double sp = SPEED * b.up.increaseMultiplier(UpgradeStat.MOVE_SPEED) * (b.speedBoost > 0 ? 1.4 : 1) * speedFactor(b);
        // 内側へ寄る（エリア外を避ける）
        double cx = ARENA / 2.0 - b.x, cz = ARENA / 2.0 - b.z;
        if (Math.hypot(cx, cz) > ZONE.radiusAt(engine.elapsedTicks()) - 2) { mx += cx * 0.1; mz += cz * 0.1; }
        b.vx = b.vx * 0.6 + mx * sp; b.vz = b.vz * 0.6 + mz * sp;
        move(b, b.vx, 0); move(b, 0, b.vz);
    }

    static void move(Bot b, double dx, double dz) {
        Vec3d n = new Vec3d(b.x + dx, 0, b.z + dz);
        if (WORLD.isFree(Box.ofEntity(n, 0.6, 1.8))) { b.x = n.x(); b.z = n.z(); }
    }

    static void swing(Bot b) {
        double lx = Math.cos(b.yaw), lz = Math.sin(b.yaw);
        b.vx += lx * LUNGE; b.vz += lz * LUNGE;
        double range = SWORD_RANGE + b.up.get(UpgradeStat.SWORD_RANGE);
        fx.add(new Fx("slash", b.x, b.z, b.yaw, range, b.color(), new int[]{6}, 6, null));
        Bot best = null; double bd = 1e9;
        for (Bot t : bots) {
            if (t.dead || t.team == b.team) continue;
            double dx = t.x - b.x, dz = t.z - b.z, d = Math.max(0, Math.hypot(dx, dz) - 0.3);
            if (d <= range && (d < 1e-6 || (dx * lx + dz * lz) / Math.hypot(dx, dz) >= SWORD_CONE) && hasLos(b, t) && d < bd) { bd = d; best = t; }
        }
        if (best != null) damage(b, best, Double.MAX_VALUE, false, true);
    }

    static void damage(Bot attacker, Bot victim, double amount, boolean gun, boolean blade) {
        if (victim.dead) return;
        // パリィ
        if (attacker != null && victim.parry.tryParry(PARRY)) {
            attacker.slowFactor = PARRY_TARGET; attacker.slowTicks = PARRY_TARGET_TICKS;
            attacker.sword.interrupt();
            styleApply(victim, StyleEvent.PARRY);
            stat("parries");
            fx.add(new Fx("text", victim.x, victim.z - 1.6, 0, 0, Color.WHITE, new int[]{20}, 20, "PARRY!"));
            burst(victim.x, victim.z, victim.color(), 14);
            return;
        }
        double after = blade ? 0 : victim.hp - amount;
        styleApply(victim, StyleEvent.DAMAGE_TAKEN);
        if (gun && victim.sword.interrupt()) {
            fx.add(new Fx("text", victim.x, victim.z - 1.6, 0, 0, new Color(0xFF3B3B), new int[]{16}, 16, "WIND-UP BROKEN"));
            stat("windups_broken");
        }
        if (after <= 0) {
            if (victim.lastStand.tryTrigger()) {
                victim.hp = Math.min(20, LS_HEALTH + victim.up.get(UpgradeStat.LAST_STAND_HEALTH));
                stat("last_stands");
                victim.lsSlowOwner = LS_TICKS + (int) victim.up.get(UpgradeStat.LAST_STAND_DURATION);
                victim.sword.reset();
                fx.add(new Fx("text", victim.x, victim.z - 1.6, 0, 0, new Color(0xFFD23F), new int[]{30}, 30, "LAST STAND"));
                burst(victim.x, victim.z, new Color(0xFFD23F), 30);
                banner("LAST STAND — " + victim.name, new Color(0xFFD23F), 30);
                return;
            }
            kill(attacker, victim, blade);
            return;
        }
        victim.hp = after;
        victim.hitStop = Math.max(victim.hitStop, HITSTOP);
        if (attacker != null) { if (shake == 0 || shakeMag < HIT_SHAKE) shakeMag = HIT_SHAKE; shake = SHAKE_TICKS; }
        burst(victim.x, victim.z, attacker != null ? attacker.color() : YELLOW, 8);
        fx.add(new Fx("hit", victim.x, victim.z, 0, 0, Color.WHITE, new int[]{5}, 5, null));
    }

    static void kill(Bot killer, Bot victim, boolean blade) {
        victim.hp = 0; victim.dead = true; victim.deathFlash = 12;
        styleApply(victim, StyleEvent.DEATH);
        victim.streak.reset();
        burst(victim.x, victim.z, killer != null ? killer.color() : YELLOW, 40);
        shake = SHAKE_TICKS; shakeMag = KILL_SHAKE;
        String how = killer == null ? "ZONE" : blade ? "BLADE" : "GUN";
        if (killer != null) {
            float pitch = killer.streak.onKill(tick, STREAK);
            killer.hp = Math.min(20, killer.hp + KILL_HEAL + killer.up.get(UpgradeStat.KILL_HEAL));
            killer.speedBoost = KILL_SPEED_TICKS;
            List<StyleEvent> ev = new ArrayList<>(List.of(StyleEvent.KILL));
            if (tick - killer.lastBlink <= BLINK_KILL_WINDOW) { ev.add(StyleEvent.BLINK_KILL); how += "+BLINK"; }
            if (blade) ev.add(StyleEvent.SWORD_KILL);
            styleApply(killer, ev.toArray(new StyleEvent[0]));
            String streak = killer.streak.streak() > 1 ? "  x" + killer.streak.streak() + String.format(Locale.ROOT, " (pitch %.2f)", pitch) : "";
            pushFeed(killer.name + "  [" + how + "]  " + victim.name + streak, killer.color());
            stat("kills_" + how.toLowerCase(Locale.ROOT));
        } else {
            pushFeed(victim.name + "  [ZONE]", YELLOW);
            stat("kills_zone");
        }
        handle(engine.eliminate(victim.id));
    }

    static void styleApply(Bot b, StyleEvent... e) {
        StyleMeter.Change c = b.style.apply(e);
        if (c.rankedUp()) b.rankPop = 12;
    }

    static void pushFeed(String s, Color c) {
        feed.addFirst(new String[]{s, String.valueOf(c.getRGB()), String.valueOf(tick)});
        while (feed.size() > 5) feed.removeLast();
    }

    static void banner(String s, Color c, int t) { banner = s; bannerColor = c; bannerTicks = t; }

    static void handle(List<RoundEngine.Event> events) {
        for (RoundEngine.Event e : events) {
            if (e instanceof RoundEngine.RoundStarted rs) {
                for (Bot b : bots) {
                    if (!b.offer.isEmpty() && b.chosen < 0) b.chosen = RNG.nextInt(b.offer.size());
                    if (b.chosen >= 0) { b.up.add(b.offer.get(b.chosen)); }
                    b.offer = List.of(); b.chosen = -1;
                    b.dead = false; b.hp = 20; b.hitStop = 0; b.slowFactor = 1; b.slowTicks = 0; b.lsSlowOwner = 0;
                    int max = 2 + (int) b.up.get(UpgradeStat.BLINK_CHARGES);
                    if (max != b.blink.maxCharges()) b.blink.setMaxCharges(max);
                    b.blink.refill(); b.sword.reset(); b.parry.reset(); b.lastStand.rearm();
                    int i = bots.stream().filter(o -> o.team == b.team).toList().indexOf(b);
                    b.x = ARENA / 2.0 + (b.team == Team.RED ? -12 : 12);
                    b.z = ARENA / 2.0 + (i == 0 ? -3 : 3);
                    b.yaw = b.team == Team.RED ? 0 : Math.PI;
                }
                bullets.clear();
                roundStartTick = tick;
                banner("ROUND " + rs.round(), Color.WHITE, 40);
            } else if (e instanceof RoundEngine.ShrinkStarted) {
                banner("ZONE SHRINKING", YELLOW, 30);
            } else if (e instanceof RoundEngine.RoundEnded re) {
                if (re.reason() == RoundEngine.EndReason.ELIMINATION && !re.decisivePlayers().isEmpty()) globalSlow = GLOBAL_SLOW_TICKS;
                ROUND_LOG.add(String.format(Locale.ROOT, "round %d: %s by %s in %.1fs", re.round(), re.isDraw() ? "DRAW" : re.winner(), re.reason(), (tick - roundStartTick) / 20.0));
                banner(re.isDraw() ? "DRAW" : re.winner() + " TAKES THE ROUND", re.isDraw() ? Color.WHITE : (re.winner() == Team.RED ? RED : BLUE), 50);
                if (engine.phase() == RoundEngine.Phase.INTERMISSION) {
                    for (Bot b : bots) {
                        Set<String> owned = new HashSet<>();
                        b.up.owned().forEach(d -> owned.add(d.id()));
                        b.offer = UpgradeDraft.draw(POOL, b.style.rank(), WEIGHTS, owned, 3, true, RNG);
                        b.chosen = -1;
                    }
                }
            } else if (e instanceof RoundEngine.MatchEnded me) {
                banner(me.winner() == null ? "MATCH DRAW" : me.winner() + " WINS  " + me.redScore() + "-" + me.blueScore(),
                        me.winner() == Team.RED ? RED : me.winner() == Team.BLUE ? BLUE : Color.WHITE, 200);
                matchOver = true;
            }
        }
        // ラウンド間：ボットは途中で 1 つ選ぶ
        if (engine.phase() == RoundEngine.Phase.INTERMISSION && engine.intermissionTicksLeft() == ROUND.intermissionTicks() / 2) {
            for (Bot b : bots) if (!b.offer.isEmpty()) b.chosen = pickBest(b.offer);
        }
    }

    static int pickBest(List<UpgradeDef> offer) {
        int best = 0;
        for (int i = 1; i < offer.size(); i++) if (offer.get(i).rarity().ordinal() > offer.get(best).rarity().ordinal()) best = i;
        return best;
    }

    static boolean hasLos(Bot a, Bot b) {
        int n = (int) (dist(a, b) * 4) + 1;
        for (int i = 1; i < n; i++) {
            double x = a.x + (b.x - a.x) * i / n, z = a.z + (b.z - a.z) * i / n;
            int gx = (int) Math.floor(x), gz = (int) Math.floor(z);
            if (gx < 0 || gz < 0 || gx >= ARENA || gz >= ARENA || SOLID[gx][gz]) return false;
        }
        return true;
    }

    static void burst(double x, double z, Color c, int n) {
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, s = 0.05 + RNG.nextDouble() * 0.25;
            fx.add(new Fx("spark", x, z, Math.cos(a) * s, Math.sin(a) * s, c, new int[]{8 + RNG.nextInt(8)}, 16, null));
        }
    }

    static double dist(Bot a, Bot b) { return Math.hypot(a.x - b.x, a.z - b.z); }
    static double segDist(double x1, double z1, double x2, double z2, double px, double pz) {
        double dx = x2 - x1, dz = z2 - z1, l = dx * dx + dz * dz;
        double t = l == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / l));
        return Math.hypot(px - (x1 + t * dx), pz - (z1 + t * dz));
    }
    static double lerpAngle(double a, double b, double t) {
        double d = Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return a + d * t;
    }

    // ================================================================ rendering

    static Font font(int size, boolean bold) { return new Font("DejaVu Sans", bold ? Font.BOLD : Font.PLAIN, size); }
    static Font jp(int size) { return new Font("IPAGothic", Font.PLAIN, size); }
    static double sx(double x) { return OX + x * SCALE; }
    static double sy(double z) { return OY + z * SCALE; }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void drawTitle(BufferedImage img, int frame) {
        Graphics2D g = g(img);
        g.setColor(BG); g.fillRect(0, 0, W, H);
        float a = Math.min(1f, frame / 15f);
        glowText(g, "NEON CLASH", W / 2, 300, font(84, true), new Color(255, 46, 99, (int) (255 * a)), true);
        g.setFont(font(26, true)); g.setColor(new Color(0, 229, 255, (int) (255 * a)));
        center(g, "QUICK PLAYTEST  —  2 vs 2 BOTS", W / 2, 360);
        g.setFont(jp(22)); g.setColor(new Color(220, 220, 230, (int) (255 * a)));
        center(g, "※ Minecraft の実機映像ではありません。core のゲームロジック（ブリンク判定・剣の溜め・ビート・", W / 2, 430);
        center(g, "スタイル・ラウンド進行・強化抽選）をそのまま動かした 2D 俯瞰シミュレーションです。", W / 2, 462);
        center(g, "数値は config 既定値。動画用にラウンド 30 秒・2 本先取に短縮。操作はボット。", W / 2, 494);
        g.dispose();
    }

    static void draw(BufferedImage img) {
        Graphics2D g = g(img);
        g.setColor(BG); g.fillRect(0, 0, W, H);
        double shx = 0, shy = 0;
        if (shake > 0) { double m = shakeMag * shake / SHAKE_TICKS * 4; shx = RNG.nextGaussian() * m; shy = RNG.nextGaussian() * m; }
        g.translate(shx, shy);
        // グリッド
        g.setColor(GRIDC);
        for (int i = 0; i <= ARENA; i++) {
            g.draw(new Line2D.Double(sx(i), sy(0), sx(i), sy(ARENA)));
            g.draw(new Line2D.Double(sx(0), sy(i), sx(ARENA), sy(i)));
        }
        // 壁（ネオンの縁）
        for (int x = 0; x < ARENA; x++) for (int z = 0; z < ARENA; z++) if (SOLID[x][z]) {
            g.setColor(WALL); g.fillRect((int) sx(x), (int) sy(z), (int) SCALE, (int) SCALE);
            g.setColor(new Color(0x2A3040)); g.drawRect((int) sx(x), (int) sy(z), (int) SCALE - 1, (int) SCALE - 1);
        }
        // エリア
        double r = ZONE.radiusAt(engine.elapsedTicks());
        g.setStroke(new BasicStroke(engine.isShrinking() ? 3f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{8, 6}, (tick % 14)));
        g.setColor(engine.isShrinking() ? new Color(0xFF3B3B) : new Color(248, 243, 43, 160));
        g.draw(new Ellipse2D.Double(sx(ARENA / 2.0 - r), sy(ARENA / 2.0 - r), r * 2 * SCALE, r * 2 * SCALE));
        g.setStroke(new BasicStroke(1f));
        // エフェクト（下層）
        for (Fx e : fx) drawFx(g, e, false);
        // ボット
        for (Bot b : bots) drawBot(g, b);
        for (Fx e : fx) drawFx(g, e, true);
        g.translate(-shx, -shy);

        // 全員スロー：彩度を落とす + レターボックス
        double slowAmt = globalSlow > 0 ? 1 : 0;
        boolean ls = bots.stream().anyMatch(b -> b.lsSlowOwner > 0);
        if (slowAmt > 0 || ls) {
            desaturate(img, slowAmt > 0 ? 0.25 : 0.6);
            int bar = (int) (H * 0.08);
            g.setColor(Color.BLACK); g.fillRect(0, 0, W, bar); g.fillRect(0, H - bar, W, bar);
            g.setFont(font(18, true)); g.setColor(slowAmt > 0 ? Color.WHITE : new Color(0xFFD23F));
            center(g, slowAmt > 0 ? "ROUND-DECIDING KILL — GLOBAL SLOW-MO" : "LAST STAND — EVERYONE ELSE SLOWED", W / 2, H - bar / 2 + 6);
        }
        drawHud(g);
        g.dispose();
    }

    static void desaturate(BufferedImage img, double sat) {
        byte[] d = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < d.length; i += 3) {
            int b = d[i] & 255, gg = d[i + 1] & 255, rr = d[i + 2] & 255;
            double l = 0.299 * rr + 0.587 * gg + 0.114 * b;
            d[i] = (byte) (l + (b - l) * sat); d[i + 1] = (byte) (l + (gg - l) * sat); d[i + 2] = (byte) (l + (rr - l) * sat);
        }
    }

    static void drawBot(Graphics2D g, Bot b) {
        double x = sx(b.x), y = sy(b.z), rad = 0.45 * SCALE;
        if (b.dead) {
            if (b.deathFlash > 0) {
                g.setColor(withAlpha(b.color(), b.deathFlash / 12f));
                double rr = rad * (1 + (12 - b.deathFlash) * 0.4);
                g.draw(new Ellipse2D.Double(x - rr, y - rr, rr * 2, rr * 2));
            }
            g.setColor(new Color(70, 70, 80));
            g.setFont(font(11, true)); center(g, "x", (int) x, (int) y + 4);
            return;
        }
        Color c = b.color();
        for (int i = 4; i >= 1; i--) { g.setColor(withAlpha(c, 0.08f * i)); double rr = rad + i * 3; g.fill(new Ellipse2D.Double(x - rr, y - rr, rr * 2, rr * 2)); }
        g.setColor(b.hitStop > 0 ? Color.WHITE : c);
        g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        g.setStroke(new BasicStroke(2f));
        g.setColor(Color.WHITE);
        g.draw(new Line2D.Double(x, y, x + Math.cos(b.yaw) * rad * 2.2, y + Math.sin(b.yaw) * rad * 2.2));
        if (b.sword.isWindingUp()) {
            g.setColor(c);
            int ang = (int) (360 * b.sword.windupProgress());
            g.drawArc((int) (x - rad - 6), (int) (y - rad - 6), (int) (rad * 2 + 12), (int) (rad * 2 + 12), 90, -ang);
        }
        if (b.parry.isActive()) { g.setColor(Color.WHITE); g.draw(new Ellipse2D.Double(x - rad - 9, y - rad - 9, rad * 2 + 18, rad * 2 + 18)); }
        if (b.slowFactor < 1 || speedFactor(b) < 1) { g.setColor(new Color(150, 150, 255, 160)); g.draw(new Ellipse2D.Double(x - rad - 4, y - rad - 4, rad * 2 + 8, rad * 2 + 8)); }
        g.setStroke(new BasicStroke(1f));
        // 名前・体力・ブリンク・ランク
        g.setFont(font(11, true)); g.setColor(c);
        center(g, b.name, (int) x, (int) (y - rad - 18));
        g.setColor(new Color(40, 40, 50)); g.fillRect((int) x - 15, (int) (y - rad - 14), 30, 4);
        g.setColor(c); g.fillRect((int) x - 15, (int) (y - rad - 14), (int) (30 * b.hp / 20), 4);
        for (int i = 0; i < b.blink.maxCharges(); i++) {
            g.setColor(i < b.blink.charges() ? Color.WHITE : new Color(80, 80, 90));
            g.fillRect((int) x - 9 + i * 7, (int) (y + rad + 5), 5, 3);
        }
        StyleRank rk = b.style.rank();
        float pop = b.rankPop > 0 ? 1 + 0.9f * (b.rankPop / 12f) * (b.rankPop / 12f) : 1;
        g.setFont(font((int) (13 * pop), true)); g.setColor(rankColor(rk));
        g.drawString(rk.name(), (int) (x + rad + 6), (int) (y + 4));
    }

    static void drawFx(Graphics2D g, Fx e, boolean top) {
        float a = e.t()[0] / (float) e.total();
        switch (e.kind()) {
            case "tracer" -> { if (top) return; g.setStroke(new BasicStroke(2f)); g.setColor(withAlpha(e.c(), a)); g.draw(new Line2D.Double(sx(e.x()), sy(e.z()), sx(e.x2()), sy(e.z2()))); }
            case "blink" -> {
                if (top) return;
                g.setStroke(new BasicStroke(5f * a + 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(withAlpha(e.c(), a * 0.8f)); g.draw(new Line2D.Double(sx(e.x()), sy(e.z()), sx(e.x2()), sy(e.z2())));
                g.setColor(withAlpha(Color.WHITE, a)); g.setStroke(new BasicStroke(1.5f));
                g.draw(new Line2D.Double(sx(e.x()), sy(e.z()), sx(e.x2()), sy(e.z2())));
            }
            case "slash" -> {
                if (!top) return;
                double r = e.z2() * SCALE; int start = (int) Math.toDegrees(-e.x2()) - 45;
                g.setStroke(new BasicStroke(4f * a + 1)); g.setColor(withAlpha(e.c(), a));
                g.drawArc((int) (sx(e.x()) - r), (int) (sy(e.z()) - r), (int) (r * 2), (int) (r * 2), start, 90);
            }
            case "spark" -> {
                double t = e.total() - e.t()[0];
                double px = sx(e.x() + e.x2() * t * 3), py = sy(e.z() + e.z2() * t * 3);
                g.setColor(withAlpha(e.c(), a)); g.fill(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
            }
            case "hit" -> {
                if (!top) return;
                double px = sx(e.x()), py = sy(e.z()); g.setStroke(new BasicStroke(2f)); g.setColor(withAlpha(Color.WHITE, a));
                for (int s : new int[]{-1, 1}) { g.draw(new Line2D.Double(px - 10, py - 10 * s, px - 5, py - 5 * s)); g.draw(new Line2D.Double(px + 5, py + 5 * s, px + 10, py + 10 * s)); }
            }
            case "ring", "beat" -> {
                if (!top) return;
                double rr = (e.kind().equals("beat") ? 10 : 14) + (1 - a) * 10;
                g.setColor(withAlpha(e.c(), a)); g.setStroke(new BasicStroke(1.5f));
                g.draw(new Ellipse2D.Double(sx(e.x()) - rr, sy(e.z()) - rr, rr * 2, rr * 2));
            }
            case "text" -> { if (!top) return; g.setFont(font(13, true)); g.setColor(withAlpha(e.c(), Math.min(1, a * 2))); center(g, e.text(), (int) sx(e.x()), (int) sy(e.z())); }
            default -> { }
        }
        g.setStroke(new BasicStroke(1f));
    }

    static void drawHud(Graphics2D g) {
        // 上部パネル
        int cx = W / 2;
        g.setColor(new Color(10, 12, 16, 220)); g.fillRect(cx - 190, 8, 380, 62);
        g.setColor(engine.isShrinking() ? new Color(0xFF3B3B) : Color.WHITE); g.fillRect(cx - 190, 68, 380, 2);
        boolean round = engine.phase() == RoundEngine.Phase.ROUND;
        int ticksLeft = round ? engine.ticksRemaining() : engine.intermissionTicksLeft();
        int secs = (ticksLeft + 19) / 20;
        String time = round ? String.format("%d:%02d", secs / 60, secs % 60) : (engine.phase() == RoundEngine.Phase.FINISHED ? "--" : String.valueOf(secs));
        glowText(g, time, cx, 48, font(34, true), round && engine.isShrinking() ? new Color(0xFF3B3B) : Color.WHITE, true);
        g.setFont(font(12, true)); g.setColor(new Color(0x8A8F98));
        center(g, round ? "ROUND " + engine.round() : engine.phase() == RoundEngine.Phase.INTERMISSION ? "ROUND " + (engine.round() + 1) + " IN" : "MATCH OVER", cx, 64);
        glowText(g, String.valueOf(engine.score(Team.RED)), cx - 130, 50, font(30, true), RED, true);
        glowText(g, String.valueOf(engine.score(Team.BLUE)), cx + 130, 50, font(30, true), BLUE, true);
        for (int i = 0; i < ROUND.winsToWin(); i++) {
            g.setColor(i < engine.score(Team.RED) ? RED : new Color(60, 60, 70)); g.fillRect(cx - 175 + i * 12, 20, 9, 4);
            g.setColor(i < engine.score(Team.BLUE) ? BLUE : new Color(60, 60, 70)); g.fillRect(cx + 166 - i * 12, 20, 9, 4);
        }
        // ビート（クロスヘアの脈動に相当）
        double now = tick * 50;
        double since = now - BEAT.beatTimeMs(BEAT.beatIndexAt(now));
        double pulse = Math.max(0, 1 - since / 200.0); pulse *= pulse;
        int pr = (int) (9 * (1 + 0.6 * pulse));
        int bx = cx + 230, by = 38;
        g.setColor(withAlpha(Color.WHITE, (float) (0.4 + 0.6 * pulse)));
        g.fillRect(bx - pr - 4, by, 4, 2); g.fillRect(bx + pr + 1, by, 4, 2); g.fillRect(bx, by - pr - 4, 2, 4); g.fillRect(bx, by + pr + 1, 2, 4);
        g.setFont(font(10, true)); g.setColor(new Color(0x8A8F98)); g.drawString("BEAT 128", bx - 22, by + 26);

        // キルフィード
        int y = 100;
        for (String[] f : feed) {
            float a = Math.max(0.25f, 1 - (tick - Long.parseLong(f[2])) / 200f);
            g.setFont(font(14, true)); g.setColor(withAlpha(new Color(Integer.parseInt(f[1]), true), a));
            g.drawString(f[0], W - 20 - g.getFontMetrics().stringWidth(f[0]), y);
            y += 20;
        }
        // 左：スタイル一覧
        g.setFont(font(12, true));
        int ly = 110;
        g.setColor(new Color(0x8A8F98)); g.drawString("STYLE", 24, ly - 16);
        for (Bot b : bots) {
            g.setColor(b.color()); g.drawString(b.name, 24, ly);
            g.setColor(rankColor(b.style.rank())); g.setFont(font(16, true)); g.drawString(b.style.rank().name(), 90, ly + 1);
            g.setFont(font(10, false)); g.setColor(new Color(0x8A8F98));
            g.drawString(b.up.owned().stream().map(d -> UP_NAME.getOrDefault(d.id(), d.id())).reduce((p, q) -> p + ", " + q).orElse(""), 112, ly);
            g.setFont(font(12, true));
            ly += 22;
        }
        // バナー
        if (bannerTicks > 0) {
            float a = Math.min(1f, bannerTicks / 10f);
            glowText(g, banner, cx, H / 2 - 120, font(40, true), withAlpha(bannerColor, a), true);
        }
        // 強化 3 択
        if (engine.phase() == RoundEngine.Phase.INTERMISSION) drawUpgrades(g);
        g.setFont(jp(13)); g.setColor(new Color(110, 115, 125));
        g.drawString("簡易プレイテスト（core ロジックのシミュレーション・実機映像ではありません）", 20, H - 14);
    }

    static void drawUpgrades(Graphics2D g) {
        Bot b = bots.get(0); // RED-1 視点
        if (b.offer.isEmpty()) return;
        int cw = 170, ch = 150, gap = 16, n = b.offer.size();
        int x0 = W / 2 - (n * cw + (n - 1) * gap) / 2, y0 = H / 2 - 40;
        g.setColor(new Color(5, 6, 8, 200)); g.fillRect(x0 - 24, y0 - 56, n * cw + (n - 1) * gap + 48, ch + 90);
        g.setFont(font(18, true)); g.setColor(Color.WHITE); center(g, "CHOOSE AN UPGRADE  (" + b.name + ")", W / 2, y0 - 30);
        g.setFont(font(13, true)); g.setColor(rankColor(b.style.rank())); center(g, "STYLE RANK " + b.style.rank(), W / 2, y0 - 12);
        for (int i = 0; i < n; i++) {
            UpgradeDef d = b.offer.get(i);
            int x = x0 + i * (cw + gap), y = y0 - (b.chosen == i ? 6 : 0);
            Color rc = rarityColor(d.rarity());
            g.setColor(new Color(16, 18, 22, b.chosen >= 0 && b.chosen != i ? 120 : 240)); g.fillRect(x, y, cw, ch);
            g.setColor(rc); g.fillRect(x, y, cw, 4);
            if (b.chosen == i) { g.setColor(Color.WHITE); g.drawRect(x - 1, y - 1, cw + 1, ch + 1); }
            g.setFont(font(11, true)); g.setColor(new Color(0x8A8F98)); center(g, d.category().name(), x + cw / 2, y + 24);
            g.setColor(rc); center(g, d.rarity().name(), x + cw / 2, y + 42);
            g.setFont(font(16, true)); g.setColor(Color.WHITE); center(g, UP_NAME.getOrDefault(d.id(), d.id()), x + cw / 2, y + 78);
            g.setFont(font(11, false)); g.setColor(new Color(200, 205, 213)); center(g, d.stat().name() + " +" + d.value(), x + cw / 2, y + 104);
            g.setFont(font(12, true)); g.setColor(rc); center(g, "[" + (i + 1) + "]", x + cw / 2, y + ch - 12);
        }
    }

    static Color rankColor(StyleRank r) {
        return switch (r) { case D -> new Color(0x9AA0A6); case C -> new Color(0x39FF88); case B -> new Color(0x00E5FF); case A -> new Color(0xFF3CF2); case S -> new Color(0xFFD23F); };
    }
    static Color rarityColor(Rarity r) {
        return switch (r) { case COMMON -> new Color(0xB8C0CC); case RARE -> new Color(0x3FA9FF); case EPIC -> new Color(0xC04CFF); case LEGENDARY -> new Color(0xFFB000); };
    }
    static Color withAlpha(Color c, float a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, (int) (a * 255)))); }
    static void center(Graphics2D g, String s, int cx, int y) { g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y); }
    static void glowText(Graphics2D g, String s, int cx, int y, Font f, Color c, boolean glow) {
        g.setFont(f);
        if (glow) {
            Color gc = withAlpha(c, c.getAlpha() / 255f * 0.25f);
            g.setColor(gc);
            for (int dx = -2; dx <= 2; dx += 2) for (int dy = -2; dy <= 2; dy += 2) if (dx != 0 || dy != 0) center(g, s, cx + dx, y + dy);
        }
        g.setColor(c); center(g, s, cx, y);
    }
}
