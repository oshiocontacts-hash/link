package com.neonclash.client;

import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.UpgradeDef;
import com.neonclash.network.C2SActionPacket;
import com.neonclash.network.NCNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** ラウンド間の強化 3 択。クリックか数字キーで選ぶ。選ばなければラウンド開始時にランダム。 */
@OnlyIn(Dist.CLIENT)
public final class UpgradeScreen extends Screen {
    private static final int CARD_W = 110, CARD_H = 130, GAP = 12;

    public UpgradeScreen() {
        super(Component.translatable("neonclash.upgrade.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cardX(int i, int n) {
        int total = n * CARD_W + (n - 1) * GAP;
        return width / 2 - total / 2 + i * (CARD_W + GAP);
    }

    private int cardY() {
        return height / 2 - CARD_H / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, 0x90050608);
        List<UpgradeDef> offers = ClientState.offers;
        StyleRank rank = StyleRank.values()[Math.max(0, Math.min(StyleRank.values().length - 1, ClientState.offerRank))];
        Neon.glowText(g, font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, cardY() - 34, Neon.WHITE, 1f);
        Neon.glowText(g, font, Component.translatable("neonclash.upgrade.rank", rank.name()).withStyle(ChatFormatting.BOLD),
                width / 2, cardY() - 20, Neon.rank(rank), 1f);
        for (int i = 0; i < offers.size(); i++) {
            drawCard(g, i, offers.size(), offers.get(i), mouseX, mouseY);
        }
        int secs = (ClientState.offerTicks + 19) / 20;
        Neon.glowText(g, font, Component.translatable("neonclash.upgrade.timer", secs), width / 2, cardY() + CARD_H + 14, Neon.DIM, 0f);
        super.render(g, mouseX, mouseY, pt);
    }

    private void drawCard(GuiGraphics g, int i, int n, UpgradeDef d, int mx, int my) {
        int x = cardX(i, n), y = cardY();
        boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
        boolean chosen = ClientState.offerChosen == i;
        boolean locked = ClientState.offerChosen >= 0 && !chosen;
        int rc = Neon.rarity(d.rarity());
        int lift = (hover && !locked) || chosen ? -4 : 0;
        y += lift;
        g.fill(x, y, x + CARD_W, y + CARD_H, locked ? 0x80101216 : 0xE0101216);
        g.fill(x, y, x + CARD_W, y + 3, Neon.argb(rc, locked ? 0.3f : 1f));
        if (chosen || hover) {
            int c = Neon.argb(chosen ? Neon.WHITE : rc, 0.9f);
            g.fill(x - 1, y - 1, x + CARD_W + 1, y, c);
            g.fill(x - 1, y + CARD_H, x + CARD_W + 1, y + CARD_H + 1, c);
            g.fill(x - 1, y, x, y + CARD_H, c);
            g.fill(x + CARD_W, y, x + CARD_W + 1, y + CARD_H, c);
        }
        int cx = x + CARD_W / 2;
        String cat = d.category().name().toLowerCase(Locale.ROOT);
        Neon.glowText(g, font, Component.translatable("neonclash.category." + cat).withStyle(ChatFormatting.BOLD), cx, y + 10, Neon.DIM, 0f);
        Neon.glowText(g, font, Component.translatable("neonclash.rarity." + d.rarity().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD),
                cx, y + 22, rc, 1f);
        Component name = Component.translatableWithFallback("upgrade.neonclash." + d.id(), d.id()).withStyle(ChatFormatting.BOLD);
        Neon.glowText(g, font, name, cx, y + 50, Neon.WHITE, 0.5f);
        Component desc = Component.translatable("neonclash.stat." + d.stat().name().toLowerCase(Locale.ROOT), format(d));
        g.drawWordWrap(font, desc, x + 8, y + 70, CARD_W - 16, 0xFFC8CDD5);
        Neon.glowText(g, font, Component.literal("[" + (i + 1) + "]").withStyle(ChatFormatting.BOLD), cx, y + CARD_H - 16, rc, 0.5f);
    }

    /** 割合系は %、tick 系は秒、それ以外はそのまま。単位は言語ファイル側。 */
    private static String format(UpgradeDef d) {
        double v = d.value();
        return switch (d.stat()) {
            case GUN_DAMAGE, BEAT_DAMAGE, SWORD_WINDUP, MOVE_SPEED, JUMP, BLINK_COOLDOWN -> String.format(Locale.ROOT, "%.0f", v * 100);
            case PARRY_WINDOW, LAST_STAND_DURATION -> String.format(Locale.ROOT, "%.2f", v / 20.0);
            default -> v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
        };
    }

    private void choose(int i) {
        if (ClientState.offerChosen < 0 && i >= 0 && i < ClientState.offers.size()) {
            NCNetwork.sendToServer(new C2SActionPacket(C2SActionPacket.Action.CHOOSE_UPGRADE, i));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int n = ClientState.offers.size();
        for (int i = 0; i < n; i++) {
            int x = cardX(i, n), y = cardY();
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
                choose(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choose(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
