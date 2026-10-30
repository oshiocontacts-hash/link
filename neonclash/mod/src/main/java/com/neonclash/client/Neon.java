package com.neonclash.client;

import com.neonclash.config.NCConfig;
import com.neonclash.core.round.Team;
import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.Rarity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
