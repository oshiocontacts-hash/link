package com.fishfight.client;

import com.fishfight.entity.BattleFishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * シーホース。中身はバニラの馬モデルそのもの（魚ではない）。
 * 陸では横倒しのまま4本脚をジタバタさせ、脚生えが発動すると馬として立って歩く。
 */
public class SeahorseModel extends EntityModel<BattleFishEntity> {
	/** 鞍・手綱・子馬用の脚など、出さない部品 */
	private static final List<String> HIDDEN = List.of(
			"saddle", "left_saddle_mouth", "right_saddle_mouth", "left_saddle_line", "right_saddle_line",
			"head_saddle", "mouth_saddle_wrap",
			"left_hind_baby_leg", "right_hind_baby_leg", "left_front_baby_leg", "right_front_baby_leg");

	private final ModelPart root;
	private final ModelPart headParts;
	private final ModelPart tail;
	private final ModelPart leftHindLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightFrontLeg;

	public SeahorseModel(ModelPart root) {
		this.root = root;
		ModelPart body = child(root, "body");
		this.headParts = child(root, "head_parts");
		this.tail = child(body, "tail");
		this.leftHindLeg = child(root, "left_hind_leg");
		this.rightHindLeg = child(root, "right_hind_leg");
		this.leftFrontLeg = child(root, "left_front_leg");
		this.rightFrontLeg = child(root, "right_front_leg");
	}

	/** 部品が見つからなくても落ちないように、空の部品で代用する */
	private static ModelPart child(ModelPart parent, String name) {
		return parent.hasChild(name) ? parent.getChild(name) : new ModelPart(List.of(), Map.of());
	}

	private void hideExtras() {
		for (ModelPart part : List.of(root, child(root, "body"), headParts)) {
			for (String name : HIDDEN) {
				if (part.hasChild(name)) {
					part.getChild(name).visible = false;
				}
			}
		}
	}

	@Override
	public void setupAnim(BattleFishEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		root.getAllParts().forEach(ModelPart::resetPose);
		hideExtras();
		float partialTick = ageInTicks - entity.tickCount;

		headParts.xRot = 0.52f;
		if (entity.isFlopping()) {
			// 横倒しで4本脚をバラバラにジタバタ
			float t = ageInTicks * 1.3f;
			leftFrontLeg.xRot = Mth.sin(t) * 0.9f;
			rightFrontLeg.xRot = Mth.sin(t + 1.7f) * 0.9f;
			leftHindLeg.xRot = Mth.sin(t + 3.1f) * 0.9f;
			rightHindLeg.xRot = Mth.sin(t + 4.4f) * 0.9f;
			headParts.xRot = 0.52f + Mth.sin(ageInTicks * 0.9f) * 0.4f;
			tail.xRot = 0.5f + Mth.sin(ageInTicks * 1.1f) * 0.6f;
		} else {
			// 立っている時は普通の馬の歩き方
			float walk = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
			leftFrontLeg.xRot = walk;
			rightFrontLeg.xRot = -walk;
			leftHindLeg.xRot = -walk;
			rightHindLeg.xRot = walk;
			tail.xRot = 0.5f + limbSwingAmount * 0.5f;
		}
		if (entity.attackAnim > 0) {
			// 攻撃：前脚で蹴る
			float swing = Mth.sin((1f - (entity.attackAnim - partialTick) / 10f) * Mth.PI);
			leftFrontLeg.xRot -= swing * 1.2f;
			rightFrontLeg.xRot -= swing * 1.2f;
			headParts.xRot -= swing * 0.4f;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		root.render(poseStack, buffer, light, overlay, color);
	}
}
