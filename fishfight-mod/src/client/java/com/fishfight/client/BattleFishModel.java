package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * 汎用の魚モデル。種類ごとに体の比率と色を変え、腕と脚は生えている時だけ出す。
 * 体だけ種類の色で染め、目・腕・脚は元の色のまま描く。
 */
public class BattleFishModel extends EntityModel<BattleFishEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(FishFight.id("battle_fish"), "main");
	private static final int GOLD_TINT = 0xFFFFD040;

	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart finLeft;
	private final ModelPart finRight;
	private final ModelPart eyes;
	private final ModelPart armLeft;
	private final ModelPart armRight;
	private final ModelPart legLeft;
	private final ModelPart legRight;
	private int tint = -1;

	public BattleFishModel(ModelPart root) {
		this.body = root.getChild("body");
		this.tail = body.getChild("tail");
		this.finLeft = body.getChild("fin_left");
		this.finRight = body.getChild("fin_right");
		this.eyes = body.getChild("eyes");
		this.armLeft = root.getChild("arm_left");
		this.armRight = root.getChild("arm_right");
		this.legLeft = root.getChild("leg_left");
		this.legRight = root.getChild("leg_right");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -4f, -6f, 4f, 8f, 12f),
				PartPose.offset(0f, 16f, 0f));
		body.addOrReplaceChild("tail",
				CubeListBuilder.create().texOffs(32, 0).addBox(0f, -4f, 0f, 0f, 8f, 6f),
				PartPose.offset(0f, 0f, 6f));
		body.addOrReplaceChild("dorsal",
				CubeListBuilder.create().texOffs(44, 0).addBox(0f, -3f, -2f, 0f, 3f, 6f),
				PartPose.offset(0f, -4f, 0f));
		body.addOrReplaceChild("fin_left",
				CubeListBuilder.create().texOffs(32, 14).addBox(0f, 0f, 0f, 3f, 0f, 3f),
				PartPose.offsetAndRotation(2f, 2f, -2f, 0f, 0f, 0.6f));
		body.addOrReplaceChild("fin_right",
				CubeListBuilder.create().texOffs(44, 14).addBox(-3f, 0f, 0f, 3f, 0f, 3f),
				PartPose.offsetAndRotation(-2f, 2f, -2f, 0f, 0f, -0.6f));
		// 目は色を付けずに描くので別パーツ（体よりほんの少し大きい箱）
		body.addOrReplaceChild("eyes",
				CubeListBuilder.create().texOffs(40, 20).addBox(-2f, -2.5f, -5.5f, 4f, 3f, 3f, new CubeDeformation(0.05f)),
				PartPose.ZERO);

		// 腕：上腕＋こぶし
		root.addOrReplaceChild("arm_left",
				CubeListBuilder.create()
						.texOffs(0, 20).addBox(-1f, 0f, -1f, 2f, 6f, 2f)
						.texOffs(8, 20).addBox(-1.5f, 5.5f, -1.5f, 3f, 3f, 3f),
				PartPose.offset(2f, 17f, -2f));
		root.addOrReplaceChild("arm_right",
				CubeListBuilder.create()
						.texOffs(20, 20).addBox(-1f, 0f, -1f, 2f, 6f, 2f)
						.texOffs(28, 20).addBox(-1.5f, 5.5f, -1.5f, 3f, 3f, 3f),
				PartPose.offset(-2f, 17f, -2f));

		// 脚：すね＋足
		root.addOrReplaceChild("leg_left",
				CubeListBuilder.create()
						.texOffs(0, 28).addBox(-1f, 0f, -1f, 2f, 6f, 2f)
						.texOffs(8, 28).addBox(-1f, 6f, -2f, 2f, 1f, 3f),
				PartPose.offset(1f, 20f, 1f));
		root.addOrReplaceChild("leg_right",
				CubeListBuilder.create()
						.texOffs(20, 28).addBox(-1f, 0f, -1f, 2f, 6f, 2f)
						.texOffs(28, 28).addBox(-1f, 6f, -2f, 2f, 1f, 3f),
				PartPose.offset(-1f, 20f, 1f));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(BattleFishEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		float partialTick = ageInTicks - entity.tickCount;
		FishSpecies species = entity.getSpecies();
		tint = entity.isGolden() ? GOLD_TINT : 0xFF000000 | species.color;

		float ws = species.widthScale;
		float hs = species.heightScale;
		float ls = species.lengthScale;
		float puff = entity.isPuffed() ? 1.4f : 1f;
		boolean flopping = entity.isFlopping();
		boolean dead = entity.isDeadOrDying();

		body.xScale = ws * puff;
		body.yScale = hs * puff;
		body.zScale = ls;
		// 陸で跳ねている時は尾びれを激しくバタバタさせる
		float wiggle = dead ? 0f : flopping ? 0.9f : 0.5f;
		float wiggleSpeed = flopping ? 1.4f : 0.6f;
		body.yRot = Mth.sin(ageInTicks * wiggleSpeed * 0.5f) * (flopping ? 0.25f : 0.1f);
		tail.yRot = Mth.sin(ageInTicks * wiggleSpeed) * wiggle;
		finLeft.zRot = 0.6f + Mth.sin(ageInTicks * 0.4f) * 0.25f;
		finRight.zRot = -finLeft.zRot;

		// 腕
		armLeft.visible = entity.hasArms();
		armRight.visible = entity.hasArms();
		float armScale = entity.armsSproutAnim > 0 ? easeOutBack(1f - (entity.armsSproutAnim - partialTick) / 20f) : 1f;
		setScale(armLeft, armScale);
		setScale(armRight, armScale);
		armLeft.x = 2f * ws * puff + 0.3f;
		armRight.x = -armLeft.x;
		armLeft.y = armRight.y = 16f + hs;
		armLeft.z = armRight.z = -2f * ls;
		float flex = Mth.sin(ageInTicks * 0.15f) * 0.1f;
		armLeft.xRot = armRight.xRot = -0.3f;
		armLeft.yRot = armRight.yRot = 0f;
		armLeft.zRot = -0.7f - flex;
		armRight.zRot = 0.7f + flex;
		if (entity.attackAnim > 0) {
			float swing = Mth.sin((1f - (entity.attackAnim - partialTick) / 10f) * Mth.PI);
			ModelPart punching = entity.punchLeft ? armLeft : armRight;
			punching.xRot = -0.3f - 1.4f * swing;
			punching.zRot *= 1f - swing * 0.8f;
		}

		// 脚：歩いた分だけ振る
		legLeft.visible = entity.hasLegs();
		legRight.visible = entity.hasLegs();
		float legScale = entity.legsSproutAnim > 0 ? easeOutBack(1f - (entity.legsSproutAnim - partialTick) / 20f) : 1f;
		setScale(legLeft, legScale);
		setScale(legRight, legScale);
		legLeft.x = ws;
		legRight.x = -ws;
		legLeft.y = legRight.y = 16f + 4f * hs - 0.5f;
		legLeft.z = legRight.z = ls;
		float walk = Mth.cos(limbSwing * 0.9f) * 1.0f * Math.min(1f, limbSwingAmount * 1.5f);
		legLeft.xRot = walk;
		legRight.xRot = -walk;
	}

	private static void setScale(ModelPart part, float scale) {
		part.xScale = scale;
		part.yScale = scale;
		part.zScale = scale;
	}

	private static float easeOutBack(float t) {
		t = Mth.clamp(t, 0f, 1f);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		// 体：種類の色で染める
		eyes.visible = false;
		body.render(poseStack, buffer, light, overlay, FastColor.ARGB32.multiply(tint, color));
		eyes.visible = true;
		// 目・腕・脚：そのままの色（肌色の腕が生えているのがいい）
		poseStack.pushPose();
		body.translateAndRotate(poseStack);
		eyes.render(poseStack, buffer, light, overlay, color);
		poseStack.popPose();
		armLeft.render(poseStack, buffer, light, overlay, color);
		armRight.render(poseStack, buffer, light, overlay, color);
		legLeft.render(poseStack, buffer, light, overlay, color);
		legRight.render(poseStack, buffer, light, overlay, color);
	}
}
