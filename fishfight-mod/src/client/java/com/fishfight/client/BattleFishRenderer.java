package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BattleFishRenderer extends EntityRenderer<BattleFishEntity> {
	private static final ResourceLocation TEXTURE = FishFight.id("textures/entity/battle_fish.png");
	private static final int GOLD_TINT = 0xFFFFD040;
	private static final int NO_TINT = 0xFFFFFFFF;

	private final BattleFishModel model;

	public BattleFishRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new BattleFishModel(context.bakeLayer(BattleFishModel.LAYER));
		this.shadowRadius = 0.5f;
	}

	@Override
	public void render(BattleFishEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		float age = entity.tickCount + partialTick;
		float scale = entity.getFishScale();

		poseStack.pushPose();
		// ふわふわ浮く。脚がある時は立つ分だけ高くする
		float hover = 0.35f + Mth.sin(age * 0.12f) * 0.08f + (entity.hasLegs() ? 0.3f * scale : 0f);
		float defeatProgress = entity.isDefeated() ? Math.min(1f, (entity.defeatedTicks + partialTick) / 12f) : 0f;
		poseStack.translate(0f, hover * (1f - defeatProgress * 0.7f), 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));

		// 攻撃時は相手に向かって突進
		if (entity.attackAnim > 0) {
			float p = 1f - (entity.attackAnim - partialTick) / 10f;
			poseStack.translate(0f, 0f, -Mth.sin(p * Mth.PI) * 1.2f);
		}
		// 被弾時は小刻みに揺れる
		if (entity.hurtAnim > 0) {
			poseStack.translate(Mth.sin(age * 2.5f) * 0.06f, 0f, 0f);
		}
		// 負けたら腹を上にしてひっくり返る
		if (defeatProgress > 0f) {
			float pivot = 0.5f * scale;
			poseStack.translate(0f, pivot, 0f);
			poseStack.mulPose(Axis.ZP.rotation(defeatProgress * Mth.PI));
			poseStack.translate(0f, -pivot, 0f);
		}

		poseStack.scale(scale, scale, scale);
		poseStack.scale(-1f, -1f, 1f);
		poseStack.translate(0f, -1.501f, 0f);

		model.setupAnim(entity, 0f, 0f, age, 0f, 0f);
		VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(entity.hurtAnim > 0));
		int tint = entity.isGolden() ? GOLD_TINT : 0xFF000000 | entity.getSpecies().color;
		model.renderBody(poseStack, vertices, packedLight, overlay, tint);
		// 腕と脚は肌色のまま（それがいい）
		model.renderLimbs(poseStack, vertices, packedLight, overlay, NO_TINT);
		poseStack.popPose();

		// 名前とHPは魚の大きさに合わせて上にずらす
		poseStack.pushPose();
		poseStack.translate(0f, Math.max(0f, scale - 0.8f) * 0.7f + 0.2f, 0f);
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(BattleFishEntity entity) {
		return TEXTURE;
	}
}
