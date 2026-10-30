package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
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

	public BattleFishRenderer(EntityRendererProvider.Context context) {
		super(context, new BattleFishModel(context.bakeLayer(BattleFishModel.LAYER)), 0.4f);
		this.sharkMan = new SharkManRenderer(context);
	}

	@Override
	public void render(BattleFishEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (entity.isHumanoid()) {
			sharkMan.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
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

	@Override
	protected void setupRotations(BattleFishEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
		super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
		float width = entity.getSpecies().widthScale;

		if (entity.transformAnim > 0) {
			// 覚醒：立ち上がって浮き、だんだん速く回転する
			float p = 1f - (entity.transformAnim - partialTick) / BattleFishEntity.TRANSFORM_TICKS;
			poseStack.translate(0f, 0.3f + p * 0.6f, 0f);
			poseStack.mulPose(Axis.YP.rotationDegrees(p * p * 1440f));
			poseStack.mulPose(Axis.XP.rotationDegrees(-60f));
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
