package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * シーホースの描画。バニラの茶色い馬を、魚のように横倒しで跳ねさせる。
 * 脚生えが発動すると、横倒しのまま下側から人間の脚が2本生えて歩く。
 */
public class SeahorseRenderer extends MobRenderer<BattleFishEntity, SeahorseModel> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_brown.png");

	private static final ResourceLocation LEG_TEXTURE = FishFight.id("textures/entity/battle_fish.png");
	/** 生えてくる人間の脚の太さ倍率 */
	private static final float LEG_SCALE = 1.6f;
	/** 脚の長さ（ブロック単位）＝馬を持ち上げる高さ */
	private static final float LEG_LENGTH = 7f * LEG_SCALE / 16f;

	private final ModelPart legLeft;
	private final ModelPart legRight;

	public SeahorseRenderer(EntityRendererProvider.Context context) {
		super(context, new SeahorseModel(context.bakeLayer(ModelLayers.HORSE)), 0.75f);
		// 魚モデルの肌色の脚を借りる
		ModelPart fish = context.bakeLayer(BattleFishModel.LAYER);
		this.legLeft = fish.getChild("leg_left");
		this.legRight = fish.getChild("leg_right");
	}

	/** 脚の生え具合（0〜1）。生えた瞬間はにょきっと伸びる */
	private static float legGrowth(BattleFishEntity entity, float partialTick) {
		if (!entity.hasLegs() || entity.isInWater()) {
			return 0f;
		}
		if (entity.legsSproutAnim <= 0) {
			return 1f;
		}
		float t = Mth.clamp(1f - (entity.legsSproutAnim - partialTick) / 20f, 0f, 1f);
		float u = t - 1f;
		return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
	}

	@Override
	public void render(BattleFishEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		float growth = legGrowth(entity, partialTick);
		if (growth <= 0f) {
			return;
		}
		// 横倒しの馬の下側から生えた、人間の脚2本
		float k = LEG_SCALE * entity.getScale();
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
		poseStack.scale(-k, -k, k);
		float walkPos = entity.walkAnimation.position(partialTick);
		float walkSpeed = Math.min(1f, entity.walkAnimation.speed(partialTick));
		float swing = Mth.cos(walkPos * 0.6662f) * 1.2f * walkSpeed;
		setupLeg(legLeft, 3f, swing, growth);
		setupLeg(legRight, -3f, -swing, growth);
		VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(LEG_TEXTURE));
		int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
		legLeft.render(poseStack, vertices, packedLight, overlay);
		legRight.render(poseStack, vertices, packedLight, overlay);
		poseStack.popPose();
	}

	private static void setupLeg(ModelPart leg, float x, float swing, float growth) {
		leg.resetPose();
		leg.x = x;
		leg.y = -7f;
		leg.z = 0f;
		leg.xRot = swing;
		leg.xScale = growth;
		leg.yScale = growth;
		leg.zScale = growth;
	}

	@Override
	protected void setupRotations(BattleFishEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
		super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
		BattleFishRenderer.homerunSpin(entity, poseStack, partialTick, 0.8f);
		float growth = legGrowth(entity, partialTick);
		if (growth > 0f) {
			// 横倒しのまま、生えた脚の長さだけ持ち上がる
			float step = Math.abs(Mth.sin(entity.walkAnimation.position(partialTick) * 0.6662f)) * 0.05f;
			poseStack.translate(0f, LEG_LENGTH * growth + step, 0f);
			poseStack.translate(0f, 0.35f, 0f);
			poseStack.mulPose(Axis.ZP.rotation(Mth.HALF_PI));
			poseStack.translate(0f, -0.9f, 0f);
		} else if (entity.isFlopping()) {
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
