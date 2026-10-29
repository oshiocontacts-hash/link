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
import net.minecraft.util.Mth;

/**
 * 汎用の魚モデル。種類ごとに体の比率を変え、腕と脚は必要な時だけ出す。
 */
public class BattleFishModel extends EntityModel<BattleFishEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(FishFight.id("battle_fish"), "main");

	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart dorsal;
	private final ModelPart finLeft;
	private final ModelPart finRight;
	private final ModelPart eyes;
	private final ModelPart armLeft;
	private final ModelPart armRight;
	private final ModelPart legLeft;
	private final ModelPart legRight;

	public BattleFishModel(ModelPart root) {
		this.body = root.getChild("body");
		this.tail = body.getChild("tail");
		this.dorsal = body.getChild("dorsal");
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
		float ws = species.widthScale;
		float hs = species.heightScale;
		float ls = species.lengthScale;
		boolean defeated = entity.isDefeated();
		float swimSpeed = defeated ? 0.05f : 0.3f;

		body.xScale = ws;
		body.yScale = hs;
		body.zScale = ls;
		body.yRot = Mth.sin(ageInTicks * swimSpeed) * 0.12f;
		tail.yRot = Mth.sin(ageInTicks * swimSpeed * 2f) * 0.5f;
		dorsal.yRot = 0f;
		finLeft.zRot = 0.6f + Mth.sin(ageInTicks * 0.4f) * 0.25f;
		finRight.zRot = -finLeft.zRot;

		// 腕
		armLeft.visible = entity.hasArms();
		armRight.visible = entity.hasArms();
		float armScale = 1f;
		if (entity.armsSproutAnim > 0) {
			armScale = easeOutBack(1f - (entity.armsSproutAnim - partialTick) / 20f);
		}
		setScale(armLeft, armScale);
		setScale(armRight, armScale);
		armLeft.x = 2f * ws + 0.3f;
		armRight.x = -(2f * ws + 0.3f);
		armLeft.y = armRight.y = 16f + hs;
		armLeft.z = armRight.z = -2f * ls;

		float flex = Mth.sin(ageInTicks * 0.15f) * 0.1f;
		armLeft.xRot = -0.3f;
		armRight.xRot = -0.3f;
		armLeft.yRot = 0f;
		armRight.yRot = 0f;
		armLeft.zRot = -0.7f - flex;
		armRight.zRot = 0.7f + flex;
		if (entity.attackAnim > 0) {
			float swing = Mth.sin((1f - (entity.attackAnim - partialTick) / 10f) * Mth.PI);
			ModelPart punching = entity.punchLeft ? armLeft : armRight;
			punching.xRot = -0.3f - 1.4f * swing;
			punching.zRot *= 1f - swing * 0.8f;
		}
		if (defeated) {
			armLeft.zRot = -1.4f;
			armRight.zRot = 1.4f;
		}

		// 脚
		legLeft.visible = entity.hasLegs();
		legRight.visible = entity.hasLegs();
		float legScale = 1f;
		if (entity.legsSproutAnim > 0) {
			legScale = easeOutBack(1f - (entity.legsSproutAnim - partialTick) / 20f);
		}
		setScale(legLeft, legScale);
		setScale(legRight, legScale);
		legLeft.x = ws;
		legRight.x = -ws;
		legLeft.y = legRight.y = 16f + 4f * hs - 0.5f;
		legLeft.z = legRight.z = ls;
		float walk = defeated ? 0f : Mth.sin(ageInTicks * 0.5f) * 0.5f;
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

	public void renderBody(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		eyes.visible = false;
		body.render(poseStack, buffer, light, overlay, color);
		eyes.visible = true;
	}

	/** 目・腕・脚（色を付けない部分） */
	public void renderLimbs(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		poseStack.pushPose();
		body.translateAndRotate(poseStack);
		eyes.render(poseStack, buffer, light, overlay, color);
		poseStack.popPose();
		armLeft.render(poseStack, buffer, light, overlay, color);
		armRight.render(poseStack, buffer, light, overlay, color);
		legLeft.render(poseStack, buffer, light, overlay, color);
		legRight.render(poseStack, buffer, light, overlay, color);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		renderBody(poseStack, buffer, light, overlay, color);
		renderLimbs(poseStack, buffer, light, overlay, color);
	}
}
