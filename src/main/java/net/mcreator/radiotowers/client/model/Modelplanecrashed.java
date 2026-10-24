package net.mcreator.radiotowers.client.model;

import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.EntityModel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

// Made with Blockbench 5.2.2 — crashed wreck variant of Modelplane (same airplane.png atlas)
public class Modelplanecrashed<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("radiotowers", "modelplanecrashed"), "main");
	private final ModelPart bone;

	public Modelplanecrashed(ModelPart root) {
		this.bone = root.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone = partdefinition.addOrReplaceChild("bone",
				CubeListBuilder.create().texOffs(1618, 1255).addBox(-116.0F, 24.0F, -34.0F, 275.0F, 69.0F, 73.0F, new CubeDeformation(0.0F))
						.texOffs(1618, 1397).addBox(-312.0F, 54.0F, -34.0F, 197.0F, 39.0F, 73.0F, new CubeDeformation(0.0F))
						.texOffs(0, 925).addBox(-56.0F, 85.0F, -331.0F, 116.0F, 15.0F, 693.0F, new CubeDeformation(0.0F))
						.texOffs(0, 0).addBox(-45.0F, 83.0F, -424.0F, 106.0F, 18.0F, 907.0F, new CubeDeformation(0.0F))
						.texOffs(0, 1633).addBox(-31.0F, 44.0F, -131.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F))
						.texOffs(290, 1633).addBox(-31.0F, 44.0F, -278.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F))
						.texOffs(870, 1633).addBox(-31.0F, 44.0F, 133.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F))
						.texOffs(1618, 925).addBox(-233.0F, 83.0F, -156.0F, 77.0F, 18.0F, 312.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.4538F, -0.2443F, 0.8378F));

		bone.addOrReplaceChild("nose_cone_crushed_r1",
				CubeListBuilder.create().texOffs(1386, 1633).addBox(-96.0F, 16.0F, -34.0F, 36.0F, 13.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(93.0F, 64.0F, 0.0F, -0.384F, 0.2618F, -1.7889F));

		bone.addOrReplaceChild("intake_twisted_b_r1",
				CubeListBuilder.create().texOffs(168, 1712).addBox(-214.2651F, -29.383F, -28.0F, 41.0F, 17.0F, 43.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(191.2651F, 42.883F, 21.5F, 1.7453F, 0.3491F, -1.309F));

		bone.addOrReplaceChild("intake_twisted_a_r1",
				CubeListBuilder.create().texOffs(336, 1712).addBox(-213.2651F, -29.383F, 5.0F, 41.0F, 17.0F, 43.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(190.2651F, 42.883F, -11.5F, 1.2654F, -0.4363F, -1.5708F));

		bone.addOrReplaceChild("wing_fairing_r1",
				CubeListBuilder.create().texOffs(1618, 1509).addBox(108.0F, -54.0F, -39.0F, 199.0F, 32.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-209.5F, 58.5F, 2.5F, 0.0F, 0.0F, -0.2705F));

		bone.addOrReplaceChild("tail_strut_r1",
				CubeListBuilder.create().texOffs(1604, 1693).addBox(178.8016F, -186.1301F, -22.0F, 42.0F, 93.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-212.3016F, 141.1301F, 2.0F, -0.0873F, 0.0F, 0.8726F));

		bone.addOrReplaceChild("tail_fin_r1",
				CubeListBuilder.create().texOffs(1160, 1633).addBox(163.8016F, -107.1301F, -22.0F, 73.0F, 52.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-212.3016F, 141.1301F, 2.0F, -0.2094F, 0.1396F, 1.885F));

		bone.addOrReplaceChild("nose_crumpled_r1",
				CubeListBuilder.create().texOffs(0, 1712).addBox(-22.0F, -18.0F, -20.0F, 44.0F, 36.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(176.0F, 34.0F, 2.0F, -0.6109F, -0.1396F, -0.733F));

		bone.addOrReplaceChild("engine_smashed_r1",
				CubeListBuilder.create().texOffs(580, 1633).addBox(-52.5F, -19.5F, -20.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(21.5F, 44.5F, 300.0F, -0.3142F, 0.2094F, 0.9599F));

		PartDefinition wing_broken = bone.addOrReplaceChild("wing_broken", CubeListBuilder.create(),
				PartPose.offsetAndRotation(-113.5F, 25.5F, 2.5F, -0.384F, 0.2443F, 1.1519F));

		wing_broken.addOrReplaceChild("wing_folded_r1",
				CubeListBuilder.create().texOffs(1618, 1614).addBox(-5.5F, -29.5F, -36.5F, 102.0F, 6.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(17.5F, 8.5F, -2.5F, -0.1396F, -0.0873F, -1.3614F));

		wing_broken.addOrReplaceChild("wing_stump_r1",
				CubeListBuilder.create().texOffs(1618, 1614).addBox(96.0F, -48.5F, -36.5F, 98.0F, 6.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(17.5F, 27.5F, -2.5F, 0.0F, 0.0F, -0.1309F));

		PartDefinition debris = bone.addOrReplaceChild("debris", CubeListBuilder.create(),
				PartPose.offsetAndRotation(-40.0F, 32.0F, 90.0F, -1.2217F, -0.5236F, -0.3491F));

		debris.addOrReplaceChild("wing_scrap_r1",
				CubeListBuilder.create().texOffs(1618, 1614).addBox(-40.0F, -4.0F, -20.0F, 80.0F, 4.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(40.0F, -8.0F, -90.0F, -0.2094F, 0.6981F, 0.0873F));

		return LayerDefinition.create(meshdefinition, 2400, 2400);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green,
			float blue, float alpha) {
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
