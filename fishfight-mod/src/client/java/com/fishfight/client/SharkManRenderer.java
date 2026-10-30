package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 覚醒したサメ（人型）の描画。{@link BattleFishRenderer} から呼ばれる */
public class SharkManRenderer extends MobRenderer<BattleFishEntity, SharkManModel> {
	private static final ResourceLocation TEXTURE = FishFight.id("textures/entity/shark_man.png");

	public SharkManRenderer(EntityRendererProvider.Context context) {
		super(context, new SharkManModel(context.bakeLayer(SharkManModel.LAYER)), 0.6f);
	}

	@Override
	protected void scale(BattleFishEntity entity, PoseStack poseStack, float partialTick) {
		// 変身直後はボンッと膨らむように出てくる
		if (entity.humanoidAnim > 0) {
			float t = Mth.clamp(1f - (entity.humanoidAnim - partialTick) / 20f, 0f, 1f);
			float u = t - 1f;
			float s = 0.4f + 0.6f * (1f + 2.70158f * u * u * u + 1.70158f * u * u);
			poseStack.scale(s, s, s);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(BattleFishEntity entity) {
		return TEXTURE;
	}
}
