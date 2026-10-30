package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.network.MinigameResultPayload;
import com.fishfight.network.StartMinigamePayload;
import com.fishfight.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * 釣りミニゲーム。スペース（またはクリック）を押している間、緑のゾーンが上がる。
 * 魚がゾーンの中にいる間ゲージが溜まり、満タンで釣り上げ、空になると逃げられる。
 */
public class FishingMinigameScreen extends Screen {
	private static final int BAR_H = 150;
	private static final int BAR_W = 24;
	private static final int SKIN = 0xFFE0AC69;
	private static final int SKIN_DARK = 0xFFB07840;

	private final int session;
	private final FishRarity rarity;
	private final float difficulty;
	private final float zoneSize;
	private final ItemStack icon;
	private final RandomSource random = RandomSource.create();

	private float zonePos;
	private float prevZonePos;
	private float zoneVel;
	private float fishPos = 0.5f;
	private float prevFishPos = 0.5f;
	private float fishVel;
	private float fishTarget = 0.5f;
	private float progress = 0.3f;
	private float prevProgress = 0.3f;
	private boolean inZone;

	private boolean keyHeld;
	private boolean mouseHeld;
	private int ticks;

	/** 途中で魚から腕が生えるタイミング（-1なら生えない） */
	private final int armsAt;
	private boolean armsOut;
	private int shake;

	private boolean resolved;
	private boolean success;
	private int resolvedTicks;

	public FishingMinigameScreen(StartMinigamePayload payload) {
		super(FishFight.tr("minigame.title"));
		this.session = payload.session();
		this.rarity = FishRarity.byOrdinal(payload.rarity());
		this.difficulty = payload.difficulty();
		this.zoneSize = rarity.zoneSize;
		this.icon = new ItemStack(ModItems.fish(FishSpecies.byId(payload.species())));
		this.armsAt = payload.armsGag() ? 30 + random.nextInt(40) : -1;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		prevZonePos = zonePos;
		prevFishPos = fishPos;
		prevProgress = progress;
		if (resolved) {
			if (++resolvedTicks > 14) {
				onClose();
			}
			return;
		}
		ticks++;

		if (FishFight.DEMO) {
			// 録画用の自動操作：魚の少し先を狙ってゾーンを動かす
			keyHeld = fishPos + fishVel * 4f > zonePos + zoneSize / 2f + zoneVel * 6f;
		}

		// キャッチゾーン：押すと上昇、離すと落下。底では少し跳ねる
		boolean holding = keyHeld || mouseHeld;
		zoneVel = Mth.clamp(zoneVel + (holding ? 0.011f : -0.009f), -0.045f, 0.045f);
		zonePos += zoneVel;
		if (zonePos < 0f) {
			zonePos = 0f;
			zoneVel = Math.abs(zoneVel) < 0.01f ? 0f : -zoneVel * 0.35f;
		}
		if (zonePos > 1f - zoneSize) {
			zonePos = 1f - zoneSize;
			zoneVel = 0f;
		}

		// 魚：ときどき目標地点を変えて泳ぐ。難しいほど素早く、急に暴れる
		if (ticks == armsAt) {
			armsOut = true;
			shake = 16;
			play(SoundEvents.ANVIL_LAND, 1.6f, 0.6f);
		}
		if (shake > 0) {
			shake--;
		}
		float d = difficulty * (armsOut ? 1.35f : 1f);
		if (random.nextFloat() < 0.025f * d) {
			fishTarget = random.nextFloat();
		}
		if (d > 1.5f && random.nextFloat() < 0.012f * d) {
			fishVel += (random.nextFloat() - 0.5f) * 0.09f;
		}
		float maxVel = 0.012f + 0.006f * d;
		fishVel = Mth.clamp((fishVel + (fishTarget - fishPos) * 0.004f * d) * 0.93f, -maxVel, maxVel);
		fishPos += fishVel;
		if (fishPos < 0f || fishPos > 1f) {
			fishPos = Mth.clamp(fishPos, 0f, 1f);
			fishVel = -fishVel * 0.5f;
		}

		inZone = fishPos >= zonePos && fishPos <= zonePos + zoneSize;
		if (inZone) {
			progress += 0.0105f / (0.7f + d * 0.3f);
			if (ticks % 4 == 0) {
				play(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.4f, 0.25f);
			}
		} else {
			if (ticks > 20) { // 最初の1秒は猶予
				progress -= 0.0075f * (0.8f + d * 0.15f);
			}
		}

		if (progress >= 1f) {
			progress = 1f;
			resolve(true);
		} else if (progress <= 0f) {
			progress = 0f;
			resolve(false);
		}
	}

	private void resolve(boolean ok) {
		resolved = true;
		success = ok;
		ClientPlayNetworking.send(new MinigameResultPayload(session, ok));
		if (ok) {
			play(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
		} else {
			play(SoundEvents.FISH_SWIM, 1.0f, 0.8f);
		}
	}

	private void play(SoundEvent sound, float pitch, float volume) {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
		}
	}

	@Override
	public void removed() {
		// ESCで閉じた等は「逃げられた」扱い
		if (!resolved) {
			resolved = true;
			ClientPlayNetworking.send(new MinigameResultPayload(session, false));
		}
		super.removed();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_SPACE) {
			keyHeld = true;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_SPACE) {
			keyHeld = false;
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			mouseHeld = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			mouseHeld = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 釣り場が見えるように背景はぼかさない
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		int shakeX = shake > 0 ? random.nextInt(7) - 3 : 0;
		int shakeY = shake > 0 ? random.nextInt(5) - 2 : 0;
		int panelW = BAR_W + 34;
		int barX = width / 2 - panelW / 2 + 6 + shakeX;
		int top = height / 2 - BAR_H / 2 + shakeY;

		// 枠と水
		graphics.fill(barX - 6, top - 24, barX + panelW - 6, top + BAR_H + 8, 0xC0101820);
		graphics.fill(barX - 1, top - 1, barX + BAR_W + 1, top + BAR_H + 1, 0xFF000000 | rarity.color.getColor());
		graphics.fillGradient(barX, top, barX + BAR_W, top + BAR_H, 0xFF3A8FD0, 0xFF0B2545);

		// キャッチゾーン
		float zone = Mth.lerp(partialTick, prevZonePos, zonePos);
		int zoneTop = top + Math.round((1f - zone - zoneSize) * BAR_H);
		int zoneBottom = top + Math.round((1f - zone) * BAR_H);
		graphics.fill(barX, zoneTop, barX + BAR_W, zoneBottom, inZone ? 0xB070FF70 : 0x8050B050);
		graphics.fill(barX, zoneTop, barX + BAR_W, zoneTop + 1, 0xFFB0FFB0);
		graphics.fill(barX, zoneBottom - 1, barX + BAR_W, zoneBottom, 0xFFB0FFB0);

		// 魚
		float fish = Mth.lerp(partialTick, prevFishPos, fishPos);
		int fishX = barX + BAR_W / 2 - 8;
		int fishY = top + Math.round((1f - fish) * BAR_H) - 8;
		if (armsOut) {
			drawArms(graphics, fishX, fishY, ticks + partialTick);
		}
		graphics.renderItem(icon, fishX, fishY);

		// 進捗ゲージ
		int gx = barX + BAR_W + 8;
		graphics.fill(gx, top, gx + 10, top + BAR_H, 0xFF202020);
		float p = Mth.lerp(partialTick, prevProgress, progress);
		int gaugeColor = p < 0.3f ? 0xFFE04040 : p < 0.7f ? 0xFFE0C040 : 0xFF50E050;
		graphics.fill(gx + 1, top + BAR_H - Math.round(p * BAR_H), gx + 9, top + BAR_H, gaugeColor);

		// 文字
		int cx = width / 2;
		graphics.drawCenteredString(font, Component.literal(rarity.stars).withStyle(rarity.color), cx, top - 18, 0xFFFFFF);
		graphics.drawCenteredString(font, FishFight.tr("minigame.hint"), cx, top + BAR_H + 14, 0xFFFFFF);
		if (armsOut && ticks - armsAt < 50) {
			graphics.drawCenteredString(font, FishFight.tr("minigame.arms").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
					cx, top - 36, 0xFFFFFF);
		}
		if (resolved) {
			Component result = success
					? FishFight.tr("minigame.success").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
					: FishFight.tr("minigame.fail").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
			graphics.drawCenteredString(font, result, cx, top + BAR_H / 2, 0xFFFFFF);
		}
	}

	/** アイコンの両側にジタバタする腕を描く */
	private void drawArms(GuiGraphics graphics, int x, int y, float time) {
		int flap = Math.round(Mth.sin(time * 1.2f) * 3f);
		// 左腕
		graphics.fill(x - 6, y + 7 + flap, x + 2, y + 10 + flap, SKIN);
		graphics.fill(x - 10, y + 5 + flap, x - 5, y + 11 + flap, SKIN_DARK);
		graphics.fill(x - 9, y + 6 + flap, x - 6, y + 10 + flap, SKIN);
		// 右腕
		graphics.fill(x + 14, y + 7 - flap, x + 22, y + 10 - flap, SKIN);
		graphics.fill(x + 21, y + 5 - flap, x + 26, y + 11 - flap, SKIN_DARK);
		graphics.fill(x + 22, y + 6 - flap, x + 25, y + 10 - flap, SKIN);
	}
}
