package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BattleFishRenderer extends MobRenderer<BattleFishEntity, BattleFishModel> {
	private static final ResourceLocation TEXTURE = FishFight.id("textures/entity/battle_fish.png");

	/** サメが覚醒して人型になったらこちらで描く */
	private final SharkManRenderer sharkMan;
	/** シーホース（見た目は馬） */
	private final SeahorseRenderer seahorse;
	/** イワシの群れの2匹目以降を描く間は名前を出さない */
	private boolean hideName;

	public BattleFishRenderer(EntityRendererProvider.Context context) {
		super(context, new BattleFishModel(context.bakeLayer(BattleFishModel.LAYER)), 0.4f);
		this.sharkMan = new SharkManRenderer(context);
		this.seahorse = new SeahorseRenderer(context);
	}

	@Override
	public void render(BattleFishEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (entity.isHumanoid()) {
			sharkMan.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}
		if (entity.getSpecies() == FishSpecies.SEAHORSE) {
			seahorse.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		if (entity.getSpecies() == FishSpecies.SARDINE) {
			renderSchool(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		}
	}

	/** イワシは1匹のモブだが、群れに見えるようにまわりをぐるぐる回る13匹を足して描く */
	private void renderSchool(BattleFishEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		float age = entity.tickCount + partialTick;
		hideName = true;
		for (int i = 0; i < 13; i++) {
			float angle = age * 0.12f + i * 2.4f;
			float radius = 0.35f + (i % 3) * 0.22f;
			poseStack.pushPose();
			poseStack.translate(Mth.cos(angle) * radius, 0.1f + (i % 4) * 0.13f + Mth.sin(age * 0.2f + i) * 0.1f, Mth.sin(angle) * radius);
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			poseStack.popPose();
		}
		hideName = false;
	}

	@Override
	protected boolean shouldShowName(BattleFishEntity entity) {
		return !hideName && super.shouldShowName(entity);
	}

	@Override
	protected float getWhiteOverlayProgress(BattleFishEntity entity, float partialTick) {
		// 覚醒中は白く点滅する
		if (entity.transformAnim > 0) {
			float age = entity.tickCount + partialTick;
			return 0.4f + 0.4f * Mth.sin(age * (1.5f + (BattleFishEntity.TRANSFORM_TICKS - entity.transformAnim) * 0.05f));
		}
		return 0f;
	}

	/** ホームランで吹っ飛ぶ時、体の真ん中を軸にぐるぐる回す（魚・馬・シャークマン共通） */
	static void homerunSpin(BattleFishEntity entity, PoseStack poseStack, float partialTick, float centerHeight) {
		if (entity.deathTime <= 0) {
			return;
		}
		float t = entity.deathTime + partialTick;
		poseStack.translate(0f, centerHeight, 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(t * 47f));
		poseStack.mulPose(Axis.XP.rotationDegrees(t * 31f));
		poseStack.translate(0f, -centerHeight, 0f);
	}

	@Override
	protected void setupRotations(BattleFishEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
		super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
		homerunSpin(entity, poseStack, partialTick, 0.5f);
		float width = entity.getSpecies().widthScale;

		if (entity.transformAnim > 0) {
			// 覚醒：立ち上がって浮き、だんだん速く回転する
			float p = 1f - (entity.transformAnim - partialTick) / BattleFishEntity.TRANSFORM_TICKS;
			poseStack.translate(0f, 0.3f + p * 0.6f, 0f);
			poseStack.mulPose(Axis.YP.rotationDegrees(p * p * 1440f));
			poseStack.mulPose(Axis.XP.rotationDegrees(-60f));
			return;
		}

		if (entity.getSpecies() == FishSpecies.GARDEN_EEL && entity.deathTime <= 0) {
			// チンアナゴ：地面から縦に生えてゆらゆら。引っ込むと砂に沈む
			float sway = Mth.sin((entity.tickCount + partialTick) * 0.15f) * 8f;
			poseStack.translate(0f, entity.isHiddenEel() ? -0.8f : 0.5f, 0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(90f + sway));
			poseStack.translate(0f, -0.5f, 0f);
			return;
		}

		if (entity.hasLegs()) {
			// 脚の分だけ持ち上げて、足を地面につける
			poseStack.translate(0f, 0.17f, 0f);
		} else if (entity.isFlopping()) {
			// 陸の魚は横倒しになって跳ねる
			float flip = Mth.sin((entity.tickCount + partialTick) * 0.9f) * (entity.onGround() ? 0.15f : 0.5f);
			poseStack.translate(0f, 0.125f * width, 0f);
			poseStack.mulPose(Axis.ZP.rotation(Mth.HALF_PI + flip));
			poseStack.translate(0f, -0.5f, 0f);
		}

		// 攻撃した瞬間に少し前へ突き出す
		if (entity.attackAnim > 0) {
			float p = 1f - (entity.attackAnim - partialTick) / 10f;
			poseStack.translate(0f, 0f, -Mth.sin(p * Mth.PI) * 0.35f);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(BattleFishEntity entity) {
		return TEXTURE;
	}
}
