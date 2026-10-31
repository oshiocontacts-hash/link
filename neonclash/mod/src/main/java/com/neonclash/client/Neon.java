package com.neonclash.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.neonclash.config.NCConfig;
import com.neonclash.core.round.Team;
import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.Rarity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Random;

/** ネオン風の描画ヘルパー。色はサーバー config のチームカラー（同期済み）を使う。 */
@OnlyIn(Dist.CLIENT)
final class Neon {
    static final int PANEL = 0xB00A0C10;
    static final int PANEL_EDGE = 0x40FFFFFF;
    static final int WHITE = 0xFFFFFFFF;
    static final int DIM = 0xFF8A8F98;

    private Neon() {
    }

    static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static int team(int teamOrdinal) {
        NCConfig.Server c = NCConfig.SERVER;
        try {
            if (teamOrdinal == Team.RED.ordinal()) return NCConfig.parseColor(c.redColor.get(), 0xFF2E63);
            if (teamOrdinal == Team.BLUE.ordinal()) return NCConfig.parseColor(c.blueColor.get(), 0x00E5FF);
            return NCConfig.parseColor(c.neutralColor.get(), 0xF8F32B);
        } catch (IllegalStateException notLoadedYet) {
            // サーバー config の同期前（ログイン直後の数 tick）
            return teamOrdinal == Team.RED.ordinal() ? 0xFF2E63 : teamOrdinal == Team.BLUE.ordinal() ? 0x00E5FF : 0xF8F32B;
        }
    }

    static int rank(StyleRank r) {
        return switch (r) {
            case D -> 0x9AA0A6;
            case C -> 0x39FF88;
            case B -> 0x00E5FF;
            case A -> 0xFF3CF2;
            case S -> 0xFFD23F;
        };
    }

    static int rarity(Rarity r) {
        return switch (r) {
            case COMMON -> 0xB8C0CC;
            case RARE -> 0x3FA9FF;
            case EPIC -> 0xC04CFF;
            case LEGENDARY -> 0xFFB000;
        };
    }

    /** グロー付きテキスト（中央揃え）。 */
    static void glowText(GuiGraphics g, Font font, Component text, int cx, int y, int rgb, float glow) {
        int w = font.width(text);
        int x = cx - w / 2;
        int gc = argb(rgb, 0.35f * glow);
        if (glow > 0) {
            g.drawString(font, text, x - 1, y, gc, false);
            g.drawString(font, text, x + 1, y, gc, false);
            g.drawString(font, text, x, y - 1, gc, false);
            g.drawString(font, text, x, y + 1, gc, false);
        }
        g.drawString(font, text, x, y, argb(rgb, 1f), false);
    }

    static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, int accentRgb) {
        g.fill(x1, y1, x2, y2, PANEL);
        g.fill(x1, y2 - 1, x2, y2, argb(accentRgb, 0.9f));
        g.fill(x1, y1, x2, y1 + 1, PANEL_EDGE);
    }

    /** 画面の縁で濃く、内側へ thickness で消えるグラデーション。 */
    static void edgeGlow(GuiGraphics g, int w, int h, float thickness, int rgb, float alpha) {
        if (alpha <= 0) return;
        BufferBuilder buf = beginQuads(g);
        Matrix4f m = g.pose().last().pose();
        float t = thickness;
        side(buf, m, 0, 0, w, 0, w - t, t, t, t, rgb, alpha);
        side(buf, m, w, 0, w, h, w - t, h - t, w - t, t, rgb, alpha);
        side(buf, m, w, h, 0, h, t, h - t, w - t, h - t, rgb, alpha);
        side(buf, m, 0, h, 0, 0, t, t, t, h - t, rgb, alpha);
        endQuads(buf);
    }

    private static void side(BufferBuilder buf, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int rgb, float alpha) {
        vertex(buf, m, x1, y1, rgb, alpha);
        vertex(buf, m, x4, y4, rgb, 0f);
        vertex(buf, m, x3, y3, rgb, 0f);
        vertex(buf, m, x2, y2, rgb, alpha);
    }

    /** 中心から外へ伸びる集中線。外側が濃く、中心側で消える。 */
    static void rays(GuiGraphics g, float cx, float cy, float inner, float outer, int count, long seed, int rgb, float alpha) {
        if (alpha <= 0) return;
        Random r = new Random(seed);
        BufferBuilder buf = beginQuads(g);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float half = 0.006f + r.nextFloat() * 0.016f;
            float from = inner * (0.8f + r.nextFloat() * 0.6f);
            float cos1 = (float) Math.cos(a - half), sin1 = (float) Math.sin(a - half);
            float cos2 = (float) Math.cos(a + half), sin2 = (float) Math.sin(a + half);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            vertex(buf, m, cx + cos * from, cy + sin * from, rgb, 0f);
            vertex(buf, m, cx + cos * from, cy + sin * from, rgb, 0f);
            vertex(buf, m, cx + cos2 * outer, cy + sin2 * outer, rgb, alpha);
            vertex(buf, m, cx + cos1 * outer, cy + sin1 * outer, rgb, alpha);
        }
        endQuads(buf);
    }

    /** 中心を囲む円弧の一部。center はラジアンで 0 が真上、時計回り。 */
    static void arc(GuiGraphics g, float cx, float cy, float radius, float width, double center, double span, int rgb, float alpha) {
        if (alpha <= 0) return;
        BufferBuilder buf = beginQuads(g);
        Matrix4f m = g.pose().last().pose();
        int steps = 18;
        for (int i = 0; i < steps; i++) {
            float u0 = (float) i / steps, u1 = (float) (i + 1) / steps;
            double a0 = center + span * (u0 - 0.5) - Math.PI / 2, a1 = center + span * (u1 - 0.5) - Math.PI / 2;
            // 弧の両端で消える
            float e0 = alpha * (float) Math.sin(u0 * Math.PI), e1 = alpha * (float) Math.sin(u1 * Math.PI);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vertex(buf, m, cx + c0 * radius, cy + s0 * radius, rgb, 0f);
            vertex(buf, m, cx + c0 * (radius + width), cy + s0 * (radius + width), rgb, e0);
            vertex(buf, m, cx + c1 * (radius + width), cy + s1 * (radius + width), rgb, e1);
            vertex(buf, m, cx + c1 * radius, cy + s1 * radius, rgb, 0f);
        }
        endQuads(buf);
    }

    private static BufferBuilder beginQuads(GuiGraphics g) {
        g.flush();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    private static void endQuads(BufferBuilder buf) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
    }

    private static void vertex(BufferBuilder buf, Matrix4f m, float x, float y, int rgb, float alpha) {
        buf.vertex(m, x, y, 0f).color((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha).endVertex();
    }

    /** 1px の点で線を描く（斜め線用）。 */
    static void line(GuiGraphics g, float x1, float y1, float x2, float y2, int argb) {
        int n = (int) Math.max(1, Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x1 + (x2 - x1) * i / n);
            int y = Math.round(y1 + (y2 - y1) * i / n);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }
}
