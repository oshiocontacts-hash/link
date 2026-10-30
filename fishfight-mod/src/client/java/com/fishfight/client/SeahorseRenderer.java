package com.fishfight.client;

import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** シーホースの描画。バニラの茶色い馬を、魚のように横倒しで跳ねさせる */
public class SeahorseRenderer extends MobRenderer<BattleFishEntity, SeahorseModel> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_brown.png");

	public SeahorseRenderer(EntityRendererProvider.Context context) {
		super(context, new SeahorseModel(context.bakeLayer(ModelLayers.HORSE)), 0.75f);
	}

	@Override
	protected void setupRotations(BattleFishEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
		super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
		if (entity.isFlopping()) {
			// 馬が横倒しのまま、ピチピチ跳ねる
			float flip = Mth.sin((entity.tickCount + partialTick) * 0.9f) * (entity.onGround() ? 0.12f : 0.45f);
			poseStack.translate(0f, 0.35f, 0f);
			poseStack.mulPose(Axis.ZP.rotation(Mth.HALF_PI + flip));
			poseStack.translate(0f, -0.9f, 0f);
		}
		if (entity.attackAnim > 0) {
			float p = 1f - (entity.attackAnim - partialTick) / 10f;
			poseStack.translate(0f, 0f, -Mth.sin(p * Mth.PI) * 0.4f);
		}
	}

	@Override
	protected void scale(BattleFishEntity entity, PoseStack poseStack, float partialTick) {
		// バニラの馬と同じ大きさ
		poseStack.scale(1.1f, 1.1f, 1.1f);
	}

	@Override
	public ResourceLocation getTextureLocation(BattleFishEntity entity) {
		return TEXTURE;
	}
}
