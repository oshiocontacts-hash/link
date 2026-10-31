package com.neonclash.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.beam.BeamTrail;
import com.neonclash.core.math.Vec3d;
import com.neonclash.network.S2CBeamPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 弾の軌道に残るネオンのビーム。常にカメラの方を向く帯で、中心が濃く縁が透ける帯を
 * 太いもの（チーム色のにじみ）と細いもの（白に寄せた芯）の 2 本重ねて描く。テクスチャは使わない。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID, value = Dist.CLIENT)
public final class BeamRenderer {
    private static final BeamTrail TRAIL = new BeamTrail();
    private static final int MAX_PIECES = 32;

    private BeamRenderer() {
    }

    private static BeamTrail.Settings settings() {
        return new BeamTrail.Settings(NCConfig.CLIENT.beamFadeTicks.get(), NCConfig.CLIENT.beamMaxSegments.get());
    }

    public static void add(S2CBeamPacket p) {
        if (!NCConfig.CLIENT.beamEnabled.get()) return;
        Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
        Vec3d to = new Vec3d(p.toX(), p.toY(), p.toZ());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p.ownerId() == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) {
            // 弾は目の高さから出るので、一人称では銃口の方へずらす
            Vec3 look = mc.player.getLookAngle();
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 down = look.cross(right).normalize();
            Vec3 off = right.scale(NCConfig.CLIENT.beamMuzzleRight.get()).add(down.scale(NCConfig.CLIENT.beamMuzzleDown.get()));
            Vec3d eye = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
            from = towardMuzzle(from, off, eye);
            to = towardMuzzle(to, off, eye);
        }
        TRAIL.add(from, to, p.color(), settings());
    }

    private static Vec3d towardMuzzle(Vec3d p, Vec3 off, Vec3d eye) {
        double k = BeamTrail.muzzleOffsetScale(p.distanceTo(eye), NCConfig.CLIENT.beamMuzzleDecay.get());
        return p.add(off.x * k, off.y * k, off.z * k);
    }

    static void tick() {
        TRAIL.tick(settings());
    }

    static void clear() {
        TRAIL.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAIL.segments().isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        Vec3d camera = new Vec3d(cam.x, cam.y, cam.z);
        Matrix4f pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick();

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (BeamTrail.Segment s : TRAIL.segments()) {
            // 1 tick に数ブロック進むので、近距離の細さ・薄さが効くよう細かく分けて描く
            double step = NCConfig.CLIENT.beamSubdivide.get();
            int pieces = (int) Math.max(1, Math.min(MAX_PIECES, Math.ceil(s.from().distanceTo(s.to()) / step)));
            Vec3d dir = s.to().subtract(s.from()).normalize();
            Station a = station(s, dir, 0, camera, partial);
            for (int i = 1; i <= pieces; i++) {
                Station b = station(s, dir, (float) i / pieces, camera, partial);
                piece(buf, pose, s.color(), a, b, camera);
                a = b;
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** 区間上の 1 点：位置、帯を広げる方向、太さの倍率、濃さ。 */
    private record Station(Vec3d pos, Vec3d side, float width, float alpha) {
    }

    private static Station station(BeamTrail.Segment s, Vec3d dir, float t, Vec3d camera, float partial) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        Vec3d pos = s.from().add(s.to().subtract(s.from()).scale(t));
        double dist = pos.distanceTo(camera);
        // 根元側は 1 tick 先の濃さにして、隣の区間と濃さがつながるようにする
        float age = s.age() + partial + (1 - t);
        float alpha = BeamTrail.fade(age, cfg.beamFadeTicks.get()) * BeamTrail.ramp(dist, cfg.beamNearFade.get());
        return new Station(pos, side(pos, dir, camera), BeamTrail.ramp(dist, cfg.beamTaperDistance.get()), alpha);
    }

    /** ビームの向きと視線の両方に直交する方向。点ごとに求めるので、隣の区間と継ぎ目ができない。 */
    private static Vec3d side(Vec3d p, Vec3d dir, Vec3d camera) {
        Vec3d v = p.subtract(camera);
        return new Vec3d(dir.y() * v.z() - dir.z() * v.y(), dir.z() * v.x() - dir.x() * v.z(), dir.x() * v.y() - dir.y() * v.x()).normalize();
    }

    /** チーム色のにじみと、白に寄せた芯を重ねる。 */
    private static void piece(BufferBuilder buf, Matrix4f pose, int color, Station a, Station b, Vec3d camera) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, bl = (color & 0xFF) / 255f;
        float glow = cfg.beamGlowAlpha.get().floatValue(), core = cfg.beamCoreAlpha.get().floatValue();
        float w = cfg.beamCoreWhiten.get().floatValue();
        ribbon(buf, pose, a, b, camera, cfg.beamGlowWidth.get().floatValue() * 0.5f, r, g, bl, glow);
        ribbon(buf, pose, a, b, camera, cfg.beamCoreWidth.get().floatValue() * 0.5f, r + (1 - r) * w, g + (1 - g) * w, bl + (1 - bl) * w, core);
    }

    /** 中心線で濃く、両端で透明になる帯（左右 2 枚）。 */
    private static void ribbon(BufferBuilder buf, Matrix4f pose, Station a, Station b, Vec3d camera,
                               float halfWidth, float r, float g, float bl, float alpha) {
        for (int sign = -1; sign <= 1; sign += 2) {
            Vec3d aEdge = a.pos().add(a.side().scale(sign * halfWidth * a.width()));
            Vec3d bEdge = b.pos().add(b.side().scale(sign * halfWidth * b.width()));
            vertex(buf, pose, a.pos(), camera, r, g, bl, a.alpha() * alpha);
            vertex(buf, pose, aEdge, camera, r, g, bl, 0f);
            vertex(buf, pose, bEdge, camera, r, g, bl, 0f);
            vertex(buf, pose, b.pos(), camera, r, g, bl, b.alpha() * alpha);
        }
    }

    private static void vertex(BufferBuilder buf, Matrix4f pose, Vec3d p, Vec3d camera, float r, float g, float b, float a) {
        buf.vertex(pose, (float) (p.x() - camera.x()), (float) (p.y() - camera.y()), (float) (p.z() - camera.z())).color(r, g, b, a).endVertex();
    }
}
