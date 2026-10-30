package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BattleFishRenderer extends MobRenderer<BattleFishEntity, BattleFishModel> {
	private static final ResourceLocation TEXTURE = FishFight.id("textures/entity/battle_fish.png");

	public BattleFishRenderer(EntityRendererProvider.Context context) {
		super(context, new BattleFishModel(context.bakeLayer(BattleFishModel.LAYER)), 0.4f);
	}

	@Override
	protected void setupRotations(BattleFishEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
		super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
		float width = entity.getSpecies().widthScale;

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
