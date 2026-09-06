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

// Made with Blockbench 5.0.7
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports
public class Modelplane<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("radiotowers", "modelplane"), "main");
	public final ModelPart bone;
	public final ModelPart bone2;

	public Modelplane(ModelPart root) {
		this.bone = root.getChild("bone");
		this.bone2 = this.bone.getChild("bone2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition bone = partdefinition.addOrReplaceChild("bone",
				CubeListBuilder.create().texOffs(1618, 1255).addBox(-159.0F, -69.0F, -34.0F, 275.0F, 69.0F, 73.0F, new CubeDeformation(0.0F)).texOffs(1618, 1397).addBox(115.0F, -69.0F, -34.0F, 197.0F, 39.0F, 73.0F, new CubeDeformation(0.0F))
						.texOffs(0, 925).addBox(-60.0F, -76.0F, -331.0F, 116.0F, 15.0F, 693.0F, new CubeDeformation(0.0F)).texOffs(0, 0).addBox(-61.0F, -77.0F, -424.0F, 106.0F, 18.0F, 907.0F, new CubeDeformation(0.0F)).texOffs(0, 1633)
						.addBox(-74.0F, -59.0F, -131.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F)).texOffs(290, 1633).addBox(-74.0F, -59.0F, -278.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F)).texOffs(870, 1633)
						.addBox(-74.0F, -59.0F, 133.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F)).texOffs(580, 1633).addBox(-74.0F, -59.0F, 280.0F, 105.0F, 39.0F, 40.0F, new CubeDeformation(0.0F)).texOffs(1618, 925)
						.addBox(156.0F, -77.0F, -156.0F, 77.0F, 18.0F, 312.0F, new CubeDeformation(0.0F)).texOffs(0, 1712).addBox(-198.0F, -42.0F, -18.0F, 44.0F, 42.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(1160, 1633).addBox(-24.5F, -62.0F, -20.0F, 73.0F, 52.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(212.3016F, -117.1301F, 2.0F, 0.0F, 0.0F, 1.5708F));
		PartDefinition cube_r2 = bone.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(1604, 1693).addBox(-8.5F, -24.0F, -20.0F, 42.0F, 93.0F, 40.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(212.3016F, -117.1301F, 2.0F, 0.0F, 0.0F, 0.6981F));
		PartDefinition cube_r3 = bone.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(1618, 1509).addBox(-97.5F, -12.5F, -36.5F, 199.0F, 32.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(209.5F, -34.5F, 2.5F, 0.0F, 0.0F, -0.1309F));
		PartDefinition cube_r4 = bone.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(336, 1712).addBox(-18.0F, -6.5F, -6.5F, 41.0F, 17.0F, 43.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-190.2651F, -18.883F, -11.5F, -1.9635F, 0.0F, -1.5708F));
		PartDefinition cube_r5 = bone.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(168, 1712).addBox(-18.0F, -6.5F, -6.5F, 41.0F, 17.0F, 43.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-191.2651F, -18.883F, 21.5F, -1.2217F, 0.0F, -1.5708F));
		PartDefinition cube_r6 = bone.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(1386, 1633).addBox(-33.0F, -69.0F, -34.0F, 36.0F, 13.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-93.0F, -52.0F, 0.0F, 0.0F, 0.0F, -1.3526F));
		PartDefinition bone2 = bone.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offset(113.5F, -1.5F, 2.5F));
		PartDefinition cube_r7 = bone2.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(1618, 1614).addBox(-97.5F, 13.5F, -36.5F, 199.0F, 6.0F, 73.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(96.0F, -29.0F, 0.0F, 0.0F, 0.0F, -0.1309F));
		return LayerDefinition.create(meshdefinition, 2400, 2400);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}