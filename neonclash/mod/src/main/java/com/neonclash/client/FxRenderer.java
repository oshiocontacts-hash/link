package com.neonclash.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.beam.BeamTrail;
import com.neonclash.core.fx.Fx;
import com.neonclash.core.fx.Spark;
import com.neonclash.core.fx.SparkField;
import com.neonclash.core.math.Vec3d;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.network.S2CFxPacket;
import com.neonclash.network.S2CHudPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 世界の中の演出。テクスチャを使わず、加算合成の板だけで描く（暗い場所ほどネオンらしく光る）。
 * 形の寸法は見た目の造形で、手触りの調整値ではないためここに置いている（数や大きさの全体は config）。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID, value = Dist.CLIENT)
public final class FxRenderer {
    private static final int GOLD = 0xFFD23F;
    private static final int BEAT_COLOR = 0xFFE14D;
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static final float TAU = (float) (Math.PI * 2);

    /** t は 0..1 の進み具合。 */
    private interface Shape {
        void draw(Quads q, float t);
    }

    private static final class Effect {
        final int life;
        final Shape shape;
        int age;

        Effect(int life, Shape shape) {
            this.life = life;
            this.shape = shape;
        }
    }

    private static final class Number {
        final Vec3d pos;
        final String text;
        final int color;
        final float size;
        int age;

        Number(Vec3d pos, String text, int color, float size) {
            this.pos = pos;
            this.text = text;
            this.color = color;
            this.size = size;
        }
    }

    private static final class Charge {
        final int entityId;
        final int color;
        int ticks;

        Charge(int entityId, int color, int ticks) {
            this.entityId = entityId;
            this.color = color;
            this.ticks = ticks;
        }
    }

    private static final SparkField SPARKS = new SparkField();
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final List<Number> NUMBERS = new ArrayList<>();
    private static final List<Charge> CHARGES = new ArrayList<>();
    private static final Random RNG = new Random();
    private static int slashSide = 1;

    private FxRenderer() {
    }

    // ------------------------------------------------------------ 受信

    public static void add(S2CFxPacket p) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        Vec3d at = new Vec3d(p.x(), p.y(), p.z());
        Vec3d dir = new Vec3d(p.dx(), p.dy(), p.dz());
        if (p.kind() == S2CFxPacket.Kind.DAMAGE) {
            if (cfg.damageNumbers.get()) number(at, p);
            return;
        }
        if (!cfg.fxEnabled.get()) return;
        switch (p.kind()) {
            case MUZZLE -> muzzle(at, dir, p.color(), p.value() > 0.5f, p.extra());
            case HIT -> hit(at, dir, p.color(), p.extra());
            case IMPACT -> impact(at, dir, p.color());
            case KILL -> kill(at, dir, p.color(), p.extra());
            case SLASH -> slash(at, dir, p.value(), p.color());
            case BLINK -> blink(at, dir, p.value(), p.color());
            case PARRY -> parry(at, p.value(), p.color());
            case LAST_STAND -> lastStand(at, p.value(), p.color());
            case WINDUP -> CHARGES.add(new Charge(p.extra(), p.color(), Math.round(p.value())));
            default -> {
            }
        }
    }

    private static void effect(int life, Shape shape) {
        EFFECTS.add(new Effect(life, shape));
    }

    private static boolean isOwnFirstPerson(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == entityId && mc.options.getCameraType().isFirstPerson();
    }

    private static double between(double lo, double hi) {
        return lo + RNG.nextDouble() * (hi - lo);
    }

    private static void spark(Vec3d pos, Vec3d velocity, int life, float size, int color, double drag, double gravity) {
        SPARKS.add(new Spark(pos, velocity, new Spark.Look(life, size, color, drag, gravity, false), 0f), NCConfig.CLIENT.fxMaxSparks.get());
    }

    private static void shard(Vec3d pos, Vec3d velocity, int life, float size, int color) {
        float spin = (float) between(0.2, 0.7) * (RNG.nextBoolean() ? 1 : -1);
        SPARKS.add(new Spark(pos, velocity, new Spark.Look(life, size, color, 0.94, 0.025, true), spin), NCConfig.CLIENT.fxMaxSparks.get());
    }

    private static void muzzle(Vec3d eye, Vec3d dir, int color, boolean beat, int shooter) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        Vec3d right = Fx.cross(dir, UP).normalize();
        Vec3d down = Fx.cross(dir, right).normalize();
        boolean own = isOwnFirstPerson(shooter);
        Vec3d pos = own
                ? eye.add(dir.scale(0.9)).add(right.scale(cfg.beamMuzzleRight.get())).add(down.scale(cfg.beamMuzzleDown.get()))
                : eye.add(dir.scale(0.7)).add(right.scale(0.3)).add(down.scale(0.25));
        // 一人称では銃口がカメラのすぐ前にあるので、小さくしないと画面を塞ぐ
        float near = own ? 0.4f : 1f;
        float size = cfg.fxMuzzleSize.get().floatValue() * (beat ? 1.7f : 1f) * near;
        effect(3, (q, t) -> {
            q.glow(pos, size * (1f + t), Fx.whiten(color, 0.5f), 1f - t);
            q.glow(pos, size * 0.4f, 0xFFFFFF, 1f - t);
        });
        for (int i = beat ? 10 : 4; i > 0; i--) {
            spark(pos, Fx.cone(RNG, dir, 22).scale(between(0.2, 0.55)), 3 + RNG.nextInt(4), 0.035f, Fx.whiten(color, 0.4f), 0.75, 0);
        }
        if (beat) {
            effect(6, (q, t) -> q.ring(pos, dir, Fx.easeOut(t) * 0.6f * near, 0.05f * near, BEAT_COLOR, 1f - t));
        }
    }

    private static void hit(Vec3d at, Vec3d dir, int color, int flags) {
        boolean beat = (flags & S2CFxPacket.BEAT) != 0, head = (flags & S2CFxPacket.HEAD) != 0;
        int tint = head ? GOLD : beat ? BEAT_COLOR : color;
        int count = NCConfig.CLIENT.fxHitSparks.get() * (beat || head ? 2 : 1);
        effect(4, (q, t) -> q.glow(at, 0.6f + 0.3f * t, Fx.whiten(tint, 0.3f), 1f - t));
        effect(7, (q, t) -> q.ring(at, dir, 0.1f + Fx.easeOut(t) * 0.8f, 0.07f, tint, (1f - t) * 0.9f));
        for (int i = 0; i < count; i++) {
            // 大半は弾の抜ける向きへ、残りは手前へ跳ね返る
            Vec3d v = i % 3 == 0 ? Fx.cone(RNG, dir.scale(-1), 60) : Fx.cone(RNG, dir, 38);
            spark(at, v.scale(between(0.2, 0.65)), 6 + RNG.nextInt(7), 0.05f, Fx.whiten(tint, 0.35f), 0.82, 0.012);
        }
    }

    private static void impact(Vec3d at, Vec3d normal, int color) {
        Vec3d pos = at.add(normal.scale(0.03));
        effect(3, (q, t) -> q.glow(pos, 0.3f, Fx.whiten(color, 0.4f), 1f - t));
        effect(8, (q, t) -> q.ring(pos, normal, Fx.easeOut(t) * 0.45f, 0.05f, color, 1f - t));
        effect(34, (q, t) -> q.ring(pos, normal, 0.16f, 0.09f, color, (1f - t) * 0.5f)); // 焼け跡
        for (int i = NCConfig.CLIENT.fxImpactSparks.get(); i > 0; i--) {
            spark(pos, Fx.cone(RNG, normal, 58).scale(between(0.12, 0.42)), 5 + RNG.nextInt(6), 0.04f, Fx.whiten(color, 0.3f), 0.84, 0.018);
        }
    }

    private static void kill(Vec3d at, Vec3d dir, int color, int victimColor) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        Vec3d feet = at.add(0, -1.05, 0);
        effect(5, (q, t) -> q.glow(at, 1.6f * (1f + t * 0.5f), Fx.whiten(color, 0.45f), 1f - t));
        effect(12, (q, t) -> q.ring(feet.add(0, 0.1, 0), UP, 0.2f + Fx.easeOut(t) * 5f, 0.5f * (1f - t) + 0.05f, color, 1f - t));
        effect(9, (q, t) -> q.ring(at, dir, 0.2f + Fx.easeOut(t) * 2.6f, 0.2f, Fx.whiten(color, 0.3f), 1f - t));
        effect(14, (q, t) -> q.pillar(feet, 11f, 1.3f * (1f - t * t), victimColor, 1f - t));
        for (int i = cfg.fxKillShards.get(); i > 0; i--) {
            // 倒した弾の向きへ体が砕けて飛ぶ
            Vec3d pos = at.add(between(-0.3, 0.3), between(-0.9, 0.5), between(-0.3, 0.3));
            Vec3d v = dir.scale(between(0.1, 0.5)).add(Fx.sphere(RNG).scale(between(0.05, 0.3))).add(0, 0.12, 0);
            shard(pos, v, 16 + RNG.nextInt(16), (float) between(0.08, 0.2), RNG.nextInt(4) == 0 ? 0xFFFFFF : victimColor);
        }
        for (int i = cfg.fxKillSparks.get(); i > 0; i--) {
            spark(at, Fx.sphere(RNG).scale(between(0.25, 0.95)), 8 + RNG.nextInt(11), 0.06f, Fx.whiten(color, 0.35f), 0.86, 0.01);
        }
    }

    private static void slash(Vec3d eye, Vec3d look, float range, int color) {
        Vec3d right = Fx.cross(look, UP).normalize();
        Vec3d down = Fx.cross(look, right).normalize();
        Vec3d center = eye.add(look.scale(-0.2)).add(down.scale(0.25));
        // 振るたびに逆向きへ傾ける
        int sign = slashSide = -slashSide;
        Vec3d side = right.scale(0.82 * sign).add(down.scale(-0.57));
        // 目の前の相手に隠れないよう、弧は射程より手前に描く
        float radius = Math.min(range * 0.75f, 1.6f);
        effect(8, (q, t) -> q.arc(center, look, side, radius, 2.7f, t, color));
        for (int i = 0; i < 14; i++) {
            double a = between(-1.3, 1.3);
            Vec3d out = look.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            spark(center.add(out.scale(range * 0.9)), out.scale(between(0.05, 0.3)), 6 + RNG.nextInt(6), 0.045f, Fx.whiten(color, 0.4f), 0.85, 0.01);
        }
    }

    private static void blink(Vec3d from, Vec3d delta, float height, int color) {
        Vec3d to = from.add(delta);
        Vec3d mid = new Vec3d(0, height * 0.5, 0);
        Vec3d a = from.add(mid), b = to.add(mid);
        // 自分のブリンクは一人称だと線の中に立つことになるので描かない
        Minecraft mc = Minecraft.getInstance();
        boolean mine = mc.player != null && isOwnFirstPerson(mc.player.getId()) && mc.player.position().distanceToSqr(to.x(), to.y(), to.z()) < 9;
        effect(10, (q, t) -> q.pillar(from, height * 1.05f, 0.9f, color, (1f - t) * 0.8f)); // 残像
        if (!mine) {
            effect(9, (q, t) -> q.streak(a, b, 0.55f * (1f - t), color, 1f - t));
        }
        effect(8, (q, t) -> q.ring(to.add(0, 0.1, 0), UP, 0.2f + Fx.easeOut(t) * 1.6f, 0.14f, Fx.whiten(color, 0.3f), 1f - t));
        effect(8, (q, t) -> q.ring(from.add(0, 0.1, 0), UP, 1.4f * (1f - t), 0.12f, color, 1f - t));
        for (int i = NCConfig.CLIENT.fxBlinkSparks.get(); i > 0; i--) {
            Vec3d p = a.add(b.subtract(a).scale(RNG.nextDouble()));
            spark(p, Fx.sphere(RNG).scale(between(0.03, 0.2)), 8 + RNG.nextInt(9), 0.05f, Fx.whiten(color, 0.3f), 0.88, 0);
        }
    }

    private static void parry(Vec3d at, float radius, int color) {
        int white = Fx.whiten(color, 0.7f);
        effect(5, (q, t) -> q.glow(at, 1.6f, white, 1f - t));
        Vec3d[] axes = {UP, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1)};
        for (Vec3d axis : axes) {
            effect(12, (q, t) -> q.ring(at, axis, 0.2f + Fx.easeOut(t) * radius, 0.14f, white, 1f - t));
        }
        for (int i = 0; i < 36; i++) {
            spark(at, Fx.sphere(RNG).scale(between(0.3, 0.8)), 7 + RNG.nextInt(8), 0.05f, white, 0.84, 0);
        }
    }

    private static void lastStand(Vec3d feet, float height, int color) {
        Vec3d chest = feet.add(0, height * 0.5, 0);
        effect(6, (q, t) -> q.glow(chest, 2.4f, GOLD, 1f - t));
        effect(26, (q, t) -> q.pillar(feet, 14f, 1.7f * (1f - t), GOLD, (1f - t) * 0.9f));
        effect(14, (q, t) -> q.ring(feet.add(0, 0.1, 0), UP, 0.3f + Fx.easeOut(t) * 6f, 0.3f, GOLD, 1f - t));
        effect(14, (q, t) -> q.ring(feet.add(0, 0.1, 0), UP, 0.3f + Fx.easeOut(t) * 3.5f, 0.2f, color, 1f - t));
        for (int i = 0; i < 70; i++) {
            spark(chest, Fx.cone(RNG, UP, 50).scale(between(0.2, 0.85)), 10 + RNG.nextInt(14), 0.06f, i % 3 == 0 ? color : GOLD, 0.88, 0.006);
        }
    }

    private static void number(Vec3d at, S2CFxPacket p) {
        boolean beat = (p.extra() & S2CFxPacket.BEAT) != 0, head = (p.extra() & S2CFxPacket.HEAD) != 0;
        int color = head ? GOLD : beat ? BEAT_COLOR : 0xFFFFFF;
        Vec3d pos = at.add(between(-0.35, 0.35), between(0.1, 0.5), between(-0.35, 0.35));
        NUMBERS.add(new Number(pos, Fx.damageText(p.value()), color, head ? 1.5f : beat ? 1.25f : 1f));
    }

    // ------------------------------------------------------------ 更新

    static void tick() {
        SPARKS.tick();
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            if (++e.age >= e.life) it.remove();
        }
        int numberLife = NCConfig.CLIENT.damageNumberTicks.get();
        NUMBERS.removeIf(n -> ++n.age >= numberLife);
        tickCharges();
    }

    /** 剣の溜め：光が体へ集まる。撃たれたら潰せると相手にも見えるように。 */
    private static void tickCharges() {
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Charge> it = CHARGES.iterator(); it.hasNext(); ) {
            Charge c = it.next();
            Entity e = mc.level != null ? mc.level.getEntity(c.entityId) : null;
            if (--c.ticks < 0 || e == null) {
                it.remove();
                continue;
            }
            if (isOwnFirstPerson(c.entityId)) continue;
            Vec3d chest = new Vec3d(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ());
            for (int i = 0; i < 4; i++) {
                Vec3d out = Fx.sphere(RNG).scale(1.3);
                spark(chest.add(out), out.scale(-0.2), 5, 0.05f, Fx.whiten(c.color, 0.4f), 1.0, 0);
            }
        }
    }

    static void clear() {
        SPARKS.clear();
        EFFECTS.clear();
        NUMBERS.clear();
        CHARGES.clear();
    }

    // ------------------------------------------------------------ 描画

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3d camera3 = new Vec3d(cam.x, cam.y, cam.z);

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Quads q = new Quads(buf, event.getPoseStack().last().pose(), camera3);
        drawZoneWall(q);
        for (Effect e : EFFECTS) {
            e.shape.draw(q, Math.min(1f, (e.age + partial) / e.life));
        }
        for (Spark s : SPARKS.sparks()) {
            q.spark(s, partial);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        drawNumbers(event.getPoseStack(), camera, camera3, partial);
    }

    private static void drawZoneWall(Quads q) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        S2CHudPacket hud = ClientState.hud;
        if (!cfg.zoneWall.get() || hud == null || hud.phase() != RoundEngine.Phase.ROUND.ordinal() || hud.zoneRadius() <= 0) return;
        double r = hud.zoneRadius();
        double dx = q.camera.x() - hud.zoneX(), dz = q.camera.z() - hud.zoneZ();
        double gap = Math.abs(r - Math.sqrt(dx * dx + dz * dz));
        float near = (float) Math.max(0, 1 - gap / cfg.zoneWallNearDistance.get());
        float alpha = cfg.zoneWallAlpha.get().floatValue() + (cfg.zoneWallNearAlpha.get().floatValue() - cfg.zoneWallAlpha.get().floatValue()) * near;
        int rgb = hud.shrinking() ? 0xFF3B3B : Neon.team(-1);
        double h = cfg.zoneWallHeight.get();
        double y = q.camera.y();
        double time = System.currentTimeMillis() / 1000.0;
        double scanY = y - h + (time * 3.0) % (h * 2);
        int n = (int) Math.max(24, Math.min(240, Math.ceil(r * TAU / 1.5)));
        for (int i = 0; i < n; i++) {
            double a0 = TAU * i / n, a1 = TAU * (i + 1) / n;
            double x0 = hud.zoneX() + Math.cos(a0) * r, z0 = hud.zoneZ() + Math.sin(a0) * r;
            double x1 = hud.zoneX() + Math.cos(a1) * r, z1 = hud.zoneZ() + Math.sin(a1) * r;
            // 縦の縞が横へ流れる
            float stripe = alpha * (0.55f + 0.45f * (float) Math.sin(i * 0.9 + time * 2.5));
            q.vertical(x0, z0, x1, z1, y - h, 0f, y, stripe, rgb);
            q.vertical(x0, z0, x1, z1, y, stripe, y + h, 0f, rgb);
            q.vertical(x0, z0, x1, z1, scanY, alpha * 1.5f, scanY + 0.12, alpha * 1.5f, rgb);
        }
    }

    private static void drawNumbers(PoseStack pose, Camera camera, Vec3d cam, float partial) {
        if (NUMBERS.isEmpty()) return;
        NCConfig.Client cfg = NCConfig.CLIENT;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();
        int life = cfg.damageNumberTicks.get();
        for (Number n : NUMBERS) {
            float t = (n.age + partial) / life;
            Vec3d p = n.pos.add(0, Fx.easeOut(t) * 0.7, 0);
            // 遠くでも読めるように距離で大きくし、出た瞬間に弾ませる
            float pop = 1f + 0.8f * Math.max(0f, 1f - t * 6f);
            float s = cfg.damageNumberScale.get().floatValue() * n.size * pop * (1f + (float) p.distanceTo(cam) * 0.08f);
            float alpha = Math.min(1f, (1f - t) * 3f);
            pose.pushPose();
            pose.translate(p.x() - cam.x(), p.y() - cam.y(), p.z() - cam.z());
            pose.mulPose(camera.rotation());
            pose.scale(-s, -s, s);
            font.drawInBatch(n.text, -font.width(n.text) / 2f, 0f, Neon.argb(n.color, alpha), true, pose.last().pose(), src,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        src.endBatch();
    }

    /** 帯の板を積む道具。座標はカメラからの相対で書き込む。 */
    private static final class Quads {
        final BufferBuilder buf;
        final Matrix4f pose;
        final Vec3d camera;

        Quads(BufferBuilder buf, Matrix4f pose, Vec3d camera) {
            this.buf = buf;
            this.pose = pose;
            this.camera = camera;
        }

        void v(Vec3d p, int rgb, float a) {
            buf.vertex(pose, (float) (p.x() - camera.x()), (float) (p.y() - camera.y()), (float) (p.z() - camera.z()))
                    .color((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, a).endVertex();
        }

        void vertical(double x0, double z0, double x1, double z1, double yLow, float aLow, double yHigh, float aHigh, int rgb) {
            v(new Vec3d(x0, yLow, z0), rgb, aLow);
            v(new Vec3d(x1, yLow, z1), rgb, aLow);
            v(new Vec3d(x1, yHigh, z1), rgb, aHigh);
            v(new Vec3d(x0, yHigh, z0), rgb, aHigh);
        }

        /** カメラの方を向く丸い光。中心が濃く縁で消える。カメラが光の中に入るほど薄くする（画面全体が白くならないように）。 */
        void glow(Vec3d c, float radius, int rgb, float alphaAtDistance) {
            float alpha = alphaAtDistance * BeamTrail.ramp(c.distanceTo(camera), radius * 1.6);
            Vec3d toCam = camera.subtract(c).normalize();
            Vec3d u = Fx.perpendicular(toCam);
            Vec3d w = Fx.cross(toCam, u);
            int n = 14;
            for (int i = 0; i < n; i++) {
                double a0 = TAU * i / n, a1 = TAU * (i + 1) / n;
                v(c, rgb, alpha);
                v(c, rgb, alpha);
                v(c.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius)), rgb, 0f);
                v(c.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius)), rgb, 0f);
            }
        }

        /** normal を軸にした輪。太さの中心で濃く、内外へ消える。 */
        void ring(Vec3d c, Vec3d normal, float radius, float width, int rgb, float alpha) {
            Vec3d n = normal.normalize();
            if (n.length() < 0.5) return;
            Vec3d u = Fx.perpendicular(n);
            Vec3d w = Fx.cross(n, u);
            int segments = 44;
            for (int i = 0; i < segments; i++) {
                double a0 = TAU * i / segments, a1 = TAU * (i + 1) / segments;
                Vec3d d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0)));
                Vec3d d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
                band(c, d0, d1, Math.max(0, radius - width), radius, 0f, alpha, rgb);
                band(c, d0, d1, radius, radius + width, alpha, 0f, rgb);
            }
        }

        private void band(Vec3d c, Vec3d d0, Vec3d d1, float r0, float r1, float a0, float a1, int rgb) {
            v(c.add(d0.scale(r0)), rgb, a0);
            v(c.add(d1.scale(r0)), rgb, a0);
            v(c.add(d1.scale(r1)), rgb, a1);
            v(c.add(d0.scale(r1)), rgb, a1);
        }

        /** 縦の光の柱。カメラの方を向き、上へ行くほど消える。カメラのすぐ近くでは薄くする。 */
        void pillar(Vec3d base, float height, float width, int rgb, float alphaAtDistance) {
            double flat = Math.hypot(base.x() - camera.x(), base.z() - camera.z());
            float alpha = alphaAtDistance * BeamTrail.ramp(flat, width * 2.5);
            Vec3d side = Fx.cross(UP, camera.subtract(base)).normalize().scale(width * 0.5);
            Vec3d top = base.add(0, height, 0);
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3d off = side.scale(sign);
                v(base, rgb, alpha);
                v(base.add(off), rgb, 0f);
                v(top.add(off), rgb, 0f);
                v(top, rgb, 0f);
            }
        }

        /** 2 点を結ぶ太い線。カメラの近くは消す（自分の体を貫いて見えないように）。 */
        void streak(Vec3d a, Vec3d b, float width, int rgb, float alpha) {
            Vec3d dir = b.subtract(a).normalize();
            Vec3d side = Fx.cross(dir, camera.subtract(a)).normalize().scale(width * 0.5);
            float fa = alpha * BeamTrail.ramp(a.distanceTo(camera), 2.5), fb = alpha * BeamTrail.ramp(b.distanceTo(camera), 2.5);
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3d off = side.scale(sign);
                v(a, rgb, fa);
                v(a.add(off), rgb, 0f);
                v(b.add(off), rgb, 0f);
                v(b, rgb, fb);
            }
        }

        /**
         * 斬撃の弧。先頭が走り、尾が追いかけて消える。振る面の中に寝かせると本人の視線とほぼ平行になって線にしか見えないので、
         * 帯の幅は面に垂直な向きに取る（本人には画面を横切る帯に見える）。
         */
        void arc(Vec3d c, Vec3d forward, Vec3d side, float radius, float sweep, float t, int color) {
            float head = Fx.easeOut(t * 1.7f), tail = Fx.easeOut((t - 0.2f) * 1.6f);
            Vec3d up = Fx.cross(side, forward).normalize();
            float half = radius * 0.3f;
            int white = Fx.whiten(color, 0.6f);
            int steps = 28;
            for (int i = 0; i < steps; i++) {
                float u0 = (float) i / steps, u1 = (float) (i + 1) / steps;
                Vec3d p0 = arcPoint(c, forward, side, radius, sweep * (tail + (head - tail) * u0 - 0.5f));
                Vec3d p1 = arcPoint(c, forward, side, radius, sweep * (tail + (head - tail) * u1 - 0.5f));
                // 先頭ほど濃く太い
                float a0 = u0 * (1f - t), a1 = u1 * (1f - t);
                Vec3d w0 = up.scale(half * u0), w1 = up.scale(half * u1);
                v(p0, color, a0);
                v(p1, color, a1);
                v(p1.add(w1), color, 0f);
                v(p0.add(w0), color, 0f);
                v(p0, color, a0);
                v(p1, color, a1);
                v(p1.subtract(w1), color, 0f);
                v(p0.subtract(w0), color, 0f);
                // 白く細い芯
                Vec3d c0 = w0.scale(0.16), c1 = w1.scale(0.16);
                v(p0.subtract(c0), white, a0);
                v(p1.subtract(c1), white, a1);
                v(p1.add(c1), white, a1);
                v(p0.add(c0), white, a0);
            }
        }

        private Vec3d arcPoint(Vec3d c, Vec3d forward, Vec3d side, float radius, double angle) {
            return c.add(forward.scale(Math.cos(angle) * radius)).add(side.scale(Math.sin(angle) * radius));
        }

        void spark(Spark s, float partial) {
            Vec3d p = s.position(partial);
            // カメラのすぐ前の粒は薄くする（目の前で弾けると大きく写って画面が白くなる）
            float b = s.brightness(partial) * BeamTrail.ramp(p.distanceTo(camera), 1.6);
            float size = s.look().size();
            int rgb = s.look().color();
            Vec3d toCam = camera.subtract(p).normalize();
            if (s.look().shard()) {
                // 破片：カメラの方を向いたまま回る菱形
                Vec3d u = Fx.perpendicular(toCam), w = Fx.cross(toCam, u);
                double a = (s.age() + partial) * s.spin();
                Vec3d e0 = u.scale(Math.cos(a) * size).add(w.scale(Math.sin(a) * size));
                Vec3d e1 = u.scale(-Math.sin(a) * size * 0.55).add(w.scale(Math.cos(a) * size * 0.55));
                v(p.add(e0), rgb, b);
                v(p.add(e1), Fx.whiten(rgb, 0.5f), b);
                v(p.subtract(e0), rgb, b);
                v(p.subtract(e1), rgb, b * 0.6f);
                return;
            }
            // 粒：進む向きに伸びた光。尾は透ける
            Vec3d vel = s.velocity();
            double speed = vel.length();
            Vec3d dir = speed > 1.0e-3 ? vel.scale(1 / speed) : Fx.perpendicular(toCam);
            double len = size * 2 + speed * 1.1;
            Vec3d side = Fx.cross(dir, toCam).normalize().scale(size * 0.6);
            v(p.subtract(dir.scale(len)), rgb, 0f);
            v(p.add(side), rgb, b);
            v(p.add(dir.scale(size * 1.5)), rgb, b);
            v(p.subtract(side), rgb, b);
        }
    }
}
