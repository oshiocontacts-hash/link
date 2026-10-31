package com.neonclash.client;

import com.neonclash.config.NCConfig;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.core.fx.Fx;
import com.neonclash.core.round.Team;
import com.neonclash.core.style.StyleRank;
import com.neonclash.network.S2CFxPacket;
import com.neonclash.network.S2CHudPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * HUD：ラウンド数・残り時間・スコア・ブリンク残数・スタイルランク・ビート（クロスヘアが拍で脈打つ）。
 * レイアウトの px 値は見た目の配置で、手触りの調整値ではないためここに置いている（大きさは hudScale）。
 */
@OnlyIn(Dist.CLIENT)
public final class HudOverlay {
    private HudOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        S2CHudPacket hud = ClientState.hud;
        int teamRgb = Neon.team(hud == null ? -1 : hud.team());

        drawSlowMoBars(g, w, h, pt);
        drawVignettes(g, w, h, hud);
        drawImpactOverlays(g, w, h, teamRgb, pt);
        if (ClientState.inMatch() && mc.options.getCameraType().isFirstPerson() && !mc.player.isSpectator()) {
            drawCrosshair(g, w / 2, h / 2, teamRgb, pt);
        }

        if (hud == null) return;
        float s = NCConfig.CLIENT.hudScale.get().floatValue();
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        int sw = (int) (w / s), sh = (int) (h / s);
        drawTopBar(g, font, sw, hud);
        drawSkills(g, font, sw, sh, hud, teamRgb);
        drawRank(g, font, sw, sh, pt);
        drawKillBanner(g, font, sw, sh, teamRgb, pt);
        g.pose().popPose();
    }

    // ------------------------------------------------------------ crosshair / beat

    /** 拍の瞬間に 1 で、すぐ減衰する。 */
    private static float beatPulse() {
        NCConfig.Client cfg = NCConfig.CLIENT;
        if (!ClientState.BEAT.enabled() || !cfg.showBeat.get() || !ClientState.inMatch()) return 0f;
        double since = ClientState.serverNowMs() - ClientState.BEAT.beatTimeMs(ClientState.BEAT.beatIndexAt(ClientState.serverNowMs()));
        float p = (float) Math.max(0, 1 - since / (cfg.crosshairPulseTicks.get() * 50.0));
        return p * p;
    }

    private static void drawCrosshair(GuiGraphics g, int cx, int cy, int rgb, float pt) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        float base = cfg.crosshairBaseSize.get().floatValue();
        float pulse = beatPulse();
        float r = base * (1f + cfg.crosshairPulseScale.get().floatValue() * pulse);
        float flash = ClientState.beatFlashTicks > 0 ? 1f : 0f;
        int c = Neon.argb(flash > 0 ? Neon.WHITE : rgb, 0.55f + 0.45f * Math.max(pulse, flash));
        int len = 3;
        int ri = Math.round(r);
        g.fill(cx - ri - len, cy, cx - ri, cy + 1, c);
        g.fill(cx + ri + 1, cy, cx + ri + 1 + len, cy + 1, c);
        g.fill(cx, cy - ri - len, cx + 1, cy - ri, c);
        g.fill(cx, cy + ri + 1, cx + 1, cy + ri + 1 + len, c);

        // 剣の溜め：クロスヘアを囲む円弧
        float windup = 0f;
        if (ClientState.windupTicks > 0 && ClientState.windupTotal > 0) {
            windup = 1f - (ClientState.windupTicks - pt) / ClientState.windupTotal;
        }
        if (windup > 0) {
            int dots = 24;
            float wr = base + 6;
            for (int i = 0; i < dots * Math.min(1f, windup); i++) {
                double a = -Math.PI / 2 + Math.PI * 2 * i / dots;
                int x = cx + (int) Math.round(Math.cos(a) * wr), y = cy + (int) Math.round(Math.sin(a) * wr);
                g.fill(x, y, x + 1, y + 1, Neon.argb(rgb, 1f));
            }
        }
        if (ClientState.windupCancelTicks > 0) {
            int cc = Neon.argb(0xFF3B3B, ClientState.windupCancelTicks / (float) Math.max(1, NCConfig.CLIENT.flashTicks.get()));
            Neon.line(g, cx - 6, cy - 6, cx + 6, cy + 6, cc);
            Neon.line(g, cx - 6, cy + 6, cx + 6, cy - 6, cc);
        }

        // ヒットマーカー（命中は白、キルはチームカラーで大きく）
        if (ClientState.killMarkerTicks > 0) {
            float a = ClientState.killMarkerTicks / (float) Math.max(1, cfg.killMarkerTicks.get());
            marker(g, cx, cy, 4, 10, Neon.argb(rgb, a));
        } else if (ClientState.hitMarkerTicks > 0) {
            float a = ClientState.hitMarkerTicks / (float) Math.max(1, cfg.hitMarkerTicks.get());
            boolean head = (ClientState.hitMarkerFlags & S2CFxPacket.HEAD) != 0, beat = (ClientState.hitMarkerFlags & S2CFxPacket.BEAT) != 0;
            int color = head ? 0xFFD23F : beat ? 0xFFE14D : Neon.WHITE;
            int grow = Math.round(a * a * 4) + (head ? 2 : 0); // 当たった瞬間に大きく開いて戻る
            marker(g, cx, cy, 3 + grow / 2, 7 + grow, Neon.argb(color, a));
        }
    }

    private static void marker(GuiGraphics g, int cx, int cy, int r0, int r1, int c) {
        Neon.line(g, cx - r1, cy - r1, cx - r0, cy - r0, c);
        Neon.line(g, cx + r0, cy - r0, cx + r1, cy - r1, c);
        Neon.line(g, cx - r1, cy + r1, cx - r0, cy + r0, c);
        Neon.line(g, cx + r0, cy + r0, cx + r1, cy + r1, c);
    }

    // ------------------------------------------------------------ top bar

    private static void drawTopBar(GuiGraphics g, Font font, int w, S2CHudPacket hud) {
        int cx = w / 2;
        int red = Neon.team(Team.RED.ordinal()), blue = Neon.team(Team.BLUE.ordinal());
        Neon.panel(g, cx - 92, 4, cx + 92, 40, hud.shrinking() ? 0xFF3B3B : Neon.team(hud.team()));

        boolean round = hud.phase() == RoundEngine.Phase.ROUND.ordinal();
        int secs = (hud.timeTicks() + 19) / 20;
        String time = round ? String.format("%d:%02d", secs / 60, secs % 60) : String.valueOf(secs);
        int timeColor = round && hud.shrinking() ? 0xFF3B3B : Neon.WHITE;
        g.pose().pushPose();
        g.pose().translate(cx, 13, 0);
        g.pose().scale(2f, 2f, 1f);
        Neon.glowText(g, font, Component.literal(time).withStyle(ChatFormatting.BOLD), 0, 0, timeColor, 1f);
        g.pose().popPose();

        Component label = round ? Component.translatable("neonclash.hud.round", hud.round())
                : hud.phase() == RoundEngine.Phase.INTERMISSION.ordinal() ? Component.translatable("neonclash.hud.next", hud.round() + 1)
                : Component.translatable("neonclash.hud.finished");
        Neon.glowText(g, font, label.copy().withStyle(ChatFormatting.BOLD), cx, 30, Neon.DIM, 0f);

        // スコア（数字＋先取ピップ）
        Neon.glowText(g, font, Component.literal(String.valueOf(hud.redScore())).withStyle(ChatFormatting.BOLD), cx - 62, 10, red, 1f);
        Neon.glowText(g, font, Component.literal(String.valueOf(hud.blueScore())).withStyle(ChatFormatting.BOLD), cx + 62, 10, blue, 1f);
        for (int i = 0; i < hud.winsToWin(); i++) {
            int x = cx - 80 + i * 7;
            g.fill(x, 22, x + 5, 25, i < hud.redScore() ? Neon.argb(red, 1f) : 0x40FFFFFF);
            int bx = cx + 75 - i * 7;
            g.fill(bx, 22, bx + 5, 25, i < hud.blueScore() ? Neon.argb(blue, 1f) : 0x40FFFFFF);
        }
        // 生存人数
        for (int i = 0; i < hud.redAlive(); i++) g.fill(cx - 80 + i * 5, 29, cx - 77 + i * 5, 32, Neon.argb(red, 0.8f));
        for (int i = 0; i < hud.blueAlive(); i++) g.fill(cx + 77 - i * 5, 29, cx + 80 - i * 5, 32, Neon.argb(blue, 0.8f));
    }

    // ------------------------------------------------------------ skills

    private static void drawSkills(GuiGraphics g, Font font, int w, int h, S2CHudPacket hud, int teamRgb) {
        int y = h - 58;
        int cx = w / 2;
        // ブリンク
        int pip = 12, gap = 4;
        int total = hud.blinkMax() * pip + (hud.blinkMax() - 1) * gap;
        int x0 = cx - 91 - total - 8;
        Neon.glowText(g, font, Component.translatable("neonclash.hud.blink").withStyle(ChatFormatting.BOLD), x0 + total / 2, y - 10, Neon.DIM, 0f);
        for (int i = 0; i < hud.blinkMax(); i++) {
            int x = x0 + i * (pip + gap);
            g.fill(x, y, x + pip, y + 6, 0x60FFFFFF);
            if (i < hud.blinkCharges()) {
                g.fill(x, y, x + pip, y + 6, Neon.argb(ClientState.blinkFlashTicks > 0 ? Neon.WHITE : teamRgb, 1f));
            } else if (i == hud.blinkCharges()) {
                g.fill(x, y, x + Math.round(pip * hud.blinkProgress()), y + 6, Neon.argb(teamRgb, 0.5f));
            }
        }
        // パリィ
        int px = cx + 99;
        Neon.glowText(g, font, Component.translatable("neonclash.hud.parry").withStyle(ChatFormatting.BOLD), px + 14, y - 10, Neon.DIM, 0f);
        g.fill(px, y, px + 28, y + 6, 0x60FFFFFF);
        boolean parryReady = hud.parryProgress() >= 1f;
        int parryColor = ClientState.parryFlashTicks > 0 || ClientState.parryTicks > 0 ? Neon.WHITE : teamRgb;
        g.fill(px, y, px + Math.round(28 * hud.parryProgress()), y + 6, Neon.argb(parryColor, parryReady ? 1f : 0.5f));
        // 切り札
        Component ls = Component.translatable("neonclash.hud.last_stand").withStyle(ChatFormatting.BOLD);
        // TaCZ の弾数表示（右下）と重ならないよう、パリィの上に置く
        Neon.glowText(g, font, ls, px + 14, y - 24, hud.lastStand() ? 0xFFD23F : 0x50FFFFFF, hud.lastStand() ? 1f : 0f);
    }

    // ------------------------------------------------------------ style rank

    private static void drawRank(GuiGraphics g, Font font, int w, int h, float pt) {
        StyleRank rank = ClientState.rank;
        int rgb = Neon.rank(rank);
        int x = w - 40, y = h / 2 - 30;
        int popTotal = Math.max(1, NCConfig.CLIENT.rankPopTicks.get());
        float t = ClientState.rankPopTicks > 0 ? (ClientState.rankPopTicks - pt) / popTotal : 0f; // 1 → 0
        float pop = 1f;
        if (t > 0 && ClientState.rankWentUp) {
            float maxScale = NCConfig.CLIENT.rankPopScale.get().floatValue();
            // 一瞬大きく弾けてから戻る（ease-out-back っぽく）
            pop = 1f + (maxScale - 1f) * t * t;
            int rays = 10;
            float dist = 14 + (1 - t) * 26;
            for (int i = 0; i < rays; i++) {
                double a = Math.PI * 2 * i / rays + 0.3;
                float x1 = x + (float) Math.cos(a) * dist, y1 = y + 8 + (float) Math.sin(a) * dist;
                float x2 = x + (float) Math.cos(a) * (dist + 6 * t), y2 = y + 8 + (float) Math.sin(a) * (dist + 6 * t);
                Neon.line(g, x1, y1, x2, y2, Neon.argb(rgb, t));
            }
        } else if (t > 0) {
            pop = 1f - 0.25f * t; // ランクダウンは縮む
        }
        float scale = 3f * pop;
        g.pose().pushPose();
        g.pose().translate(x, y + 8 - 4 * scale, 0);
        g.pose().scale(scale, scale, 1f);
        Neon.glowText(g, font, Component.literal(rank.name()).withStyle(ChatFormatting.BOLD), 0, 0, rgb, 1f);
        g.pose().popPose();
        Neon.glowText(g, font, Component.translatable("neonclash.hud.style").withStyle(ChatFormatting.BOLD), x, y + 26, Neon.DIM, 0f);
        float prog = ClientState.hud != null ? ClientState.hud.rankProgress() : 0f;
        g.fill(x - 16, y + 36, x + 16, y + 38, 0x50FFFFFF);
        g.fill(x - 16, y + 36, x - 16 + Math.round(32 * prog), y + 38, Neon.argb(rgb, 1f));
    }

    // ------------------------------------------------------------ effects

    private static void drawSlowMoBars(GuiGraphics g, int w, int h, float pt) {
        float a = ClientEvents.slowMoAmount(pt);
        if (a <= 0) return;
        int bar = Math.round(h * 0.08f * a);
        g.fill(0, 0, w, bar, 0xE0000000);
        g.fill(0, h - bar, w, h, 0xE0000000);
    }

    /** 被弾の向き、キルの閃光、集中線。 */
    private static void drawImpactOverlays(GuiGraphics g, int w, int h, int teamRgb, float pt) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        Minecraft mc = Minecraft.getInstance();
        if (ClientState.killFlashTicks > 0) {
            float t = ClientState.killFlashTicks / (float) Math.max(1, cfg.flashTicks.get());
            g.fill(0, 0, w, h, Neon.argb(teamRgb, cfg.killFlashAlpha.get().floatValue() * t * t));
        }
        if (ClientState.speedLineTicks > 0) {
            float t = (ClientState.speedLineTicks - pt) / Math.max(1, cfg.speedLineTicks.get());
            float outer = (float) Math.hypot(w, h) * 0.6f;
            Neon.rays(g, w / 2f, h / 2f, h * (0.5f - 0.15f * t), outer, 46, ClientState.speedLineTicks * 31L, teamRgb, t * 0.55f);
        }
        if (mc.player != null) {
            int total = Math.max(1, cfg.hurtIndicatorTicks.get());
            for (ClientState.Hurt hurt : ClientState.HURTS) {
                float t = Math.min(1f, (hurt.ticks - pt) / total);
                double angle = Fx.screenAngle(hurt.angle, mc.player.getYRot());
                Neon.arc(g, w / 2f, h / 2f, h * 0.24f, 12f, angle, 1.0, 0xFF2E63, t * 0.95f);
            }
        }
    }

    // ------------------------------------------------------------ kill banner

    private static void drawKillBanner(GuiGraphics g, Font font, int w, int h, int teamRgb, float pt) {
        NCConfig.Client cfg = NCConfig.CLIENT;
        if (ClientState.killBannerTicks <= 0) return;
        int total = Math.max(1, cfg.killBannerTicks.get());
        float age = total - ClientState.killBannerTicks + pt;
        float pop = 1f + 1.4f * (float) Math.pow(Math.max(0f, 1f - age / 5f), 2);
        int streak = Math.max(1, Math.min(5, ClientState.killStreak));
        int rgb = streak >= 3 ? 0xFFD23F : streak == 2 ? 0xFF3CF2 : teamRgb;
        int cx = w / 2, cy = (int) (h * 0.3f);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(2f * pop, 2f * pop, 1f);
        Neon.glowText(g, font, Component.translatable("neonclash.hud.kill." + streak).withStyle(ChatFormatting.BOLD), 0, -4, rgb, 1f);
        g.pose().popPose();
    }

    private static void drawVignettes(GuiGraphics g, int w, int h, S2CHudPacket hud) {
        int edge = 0;
        float alpha = 0f;
        Minecraft mc = Minecraft.getInstance();
        NCConfig.Client cfg = NCConfig.CLIENT;
        float thick = h * 0.18f;
        // 上のランクでは拍に合わせて画面の縁が光る
        if (hud != null && ClientState.rank.ordinal() >= StyleRank.A.ordinal()) {
            Neon.edgeGlow(g, w, h, thick * 0.7f, Neon.rank(ClientState.rank), beatPulse() * cfg.beatVignetteAlpha.get().floatValue());
        }
        if (ClientState.inMatch() && mc.player != null && !mc.player.isSpectator() && mc.player.getHealth() <= cfg.lowHealthThreshold.get()) {
            float beat = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            Neon.edgeGlow(g, w, h, thick * 1.4f, 0xFF2E63, 0.22f + 0.2f * beat);
        }
        if (hud != null && hud.phase() == RoundEngine.Phase.ROUND.ordinal() && mc.player != null && !mc.player.isSpectator()) {
            double dx = mc.player.getX() - hud.zoneX(), dz = mc.player.getZ() - hud.zoneZ();
            if (dx * dx + dz * dz > hud.zoneRadius() * hud.zoneRadius()) {
                edge = 0xFF3B3B;
                alpha = 0.35f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 120.0);
                Neon.glowText(g, mc.font, Component.translatable("neonclash.hud.outside").withStyle(ChatFormatting.BOLD), w / 2, h / 2 + 24, 0xFF3B3B, 1f);
            }
        }
        if (ClientState.lastStandTicks > 0) {
            edge = 0xFFD23F;
            alpha = Math.max(alpha, 0.45f);
        } else if (ClientState.parryFlashTicks > 0) {
            edge = Neon.WHITE;
            alpha = Math.max(alpha, 0.4f * ClientState.parryFlashTicks / Math.max(1, NCConfig.CLIENT.flashTicks.get()));
        } else if (ClientState.hitStopTicks > 0) {
            edge = 0xFF2E63;
            alpha = Math.max(alpha, 0.45f);
        }
        Neon.edgeGlow(g, w, h, thick, edge, alpha);
    }
}
