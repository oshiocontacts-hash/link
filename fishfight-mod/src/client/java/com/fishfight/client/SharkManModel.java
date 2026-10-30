package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * サメの固有能力で覚醒した姿。頭はサメ、体はムキムキの人型。
 */
public class SharkManModel extends EntityModel<BattleFishEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(FishFight.id("shark_man"), "main");
	private static final int GOLD_TINT = 0xFFFFD040;

	private final ModelPart head;
	private final ModelPart torso;
	private final ModelPart armLeft;
	private final ModelPart armRight;
	private final ModelPart legLeft;
	private final ModelPart legRight;
	private final ModelPart tail;
	private int tint = -1;

	public SharkManModel(ModelPart root) {
		this.head = root.getChild("head");
		this.torso = root.getChild("torso");
		this.armLeft = root.getChild("arm_left");
		this.armRight = root.getChild("arm_right");
		this.legLeft = root.getChild("leg_left");
		this.legRight = root.getChild("leg_right");
		this.tail = root.getChild("tail");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// 頭：前に長いサメの頭と、てっぺんの背びれ
		PartDefinition head = root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -7f, -6f, 7f, 7f, 10f),
				PartPose.offset(0f, 0f, 0f));
		head.addOrReplaceChild("fin",
				CubeListBuilder.create().texOffs(34, 0).addBox(0f, -6f, -1f, 0f, 6f, 5f),
				PartPose.offsetAndRotation(0f, -7f, 0f, -0.25f, 0f, 0f));

		// 胴体：分厚い胸板
		root.addOrReplaceChild("torso",
				CubeListBuilder.create().texOffs(0, 17).addBox(-5f, 0f, -3f, 10f, 12f, 6f),
				PartPose.offset(0f, 0f, 0f));

		// 腕：太い腕＋大きなこぶし
		root.addOrReplaceChild("arm_left",
				CubeListBuilder.create()
						.texOffs(32, 17).addBox(-2.5f, -1.5f, -2.5f, 5f, 13f, 5f)
						.texOffs(0, 35).addBox(-3f, 11f, -3f, 6f, 5f, 6f),
				PartPose.offset(7.5f, 1.5f, 0f));
		root.addOrReplaceChild("arm_right",
				CubeListBuilder.create()
						.texOffs(32, 17).mirror().addBox(-2.5f, -1.5f, -2.5f, 5f, 13f, 5f)
						.texOffs(0, 35).mirror().addBox(-3f, 11f, -3f, 6f, 5f, 6f),
				PartPose.offset(-7.5f, 1.5f, 0f));

		// 脚
		root.addOrReplaceChild("leg_left",
				CubeListBuilder.create().texOffs(24, 35).addBox(-2.5f, 0f, -2.5f, 5f, 12f, 5f),
				PartPose.offset(2.6f, 12f, 0f));
		root.addOrReplaceChild("leg_right",
				CubeListBuilder.create().texOffs(24, 35).mirror().addBox(-2.5f, 0f, -2.5f, 5f, 12f, 5f),
				PartPose.offset(-2.6f, 12f, 0f));

		// しっぽ：腰の後ろから生えている
		PartDefinition tail = root.addOrReplaceChild("tail",
				CubeListBuilder.create().texOffs(0, 46).addBox(-1.5f, -1.5f, 0f, 3f, 3f, 8f),
				PartPose.offsetAndRotation(0f, 10f, 3f, -0.5f, 0f, 0f));
		tail.addOrReplaceChild("tail_fin",
				CubeListBuilder.create().texOffs(44, 46).addBox(0f, -3f, 0f, 0f, 6f, 5f),
				PartPose.offset(0f, 0f, 7f));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(BattleFishEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		float partialTick = ageInTicks - entity.tickCount;
		tint = entity.isGolden() ? GOLD_TINT : -1;

		head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5f;
		head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5f;

		// 歩き
		float walk = Mth.cos(limbSwing * 0.6662f) * 1.2f * limbSwingAmount;
		legLeft.xRot = walk;
		legRight.xRot = -walk;

		// 腕：普段はムキッと広げ、歩くと振り、攻撃でパンチ
		float breathe = Mth.sin(ageInTicks * 0.12f) * 0.05f;
		armLeft.xRot = -walk * 0.8f;
		armRight.xRot = walk * 0.8f;
		armLeft.yRot = 0f;
		armRight.yRot = 0f;
		armLeft.zRot = -0.35f - breathe;
		armRight.zRot = 0.35f + breathe;
		if (entity.attackAnim > 0) {
			float swing = Mth.sin((1f - (entity.attackAnim - partialTick) / 10f) * Mth.PI);
			ModelPart punching = entity.punchLeft ? armLeft : armRight;
			punching.xRot = -1.7f * swing;
			punching.zRot *= 1f - swing;
		}

		tail.yRot = Mth.sin(ageInTicks * 0.2f) * 0.35f;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		int c = FastColor.ARGB32.multiply(tint, color);
		head.render(poseStack, buffer, light, overlay, c);
		torso.render(poseStack, buffer, light, overlay, c);
		armLeft.render(poseStack, buffer, light, overlay, c);
		armRight.render(poseStack, buffer, light, overlay, c);
		legLeft.render(poseStack, buffer, light, overlay, c);
		legRight.render(poseStack, buffer, light, overlay, c);
		tail.render(poseStack, buffer, light, overlay, c);
	}
}
