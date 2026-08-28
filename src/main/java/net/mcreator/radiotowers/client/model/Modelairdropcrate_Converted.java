package net.mcreator.radiotowers.client.model;

import org.apache.http.impl.conn.Wire;

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
public class Modelairdropcrate_Converted<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("radiotowers", "modelairdropcrate_converted"), "main");
	public final ModelPart airdrop;
	public final ModelPart parachute;
	public final ModelPart Glider;
	public final ModelPart Wire;
	public final ModelPart Crate;
	public final ModelPart LeftWire;
	public final ModelPart LeftHook;
	public final ModelPart RightWire;
	public final ModelPart RightHook;

	public Modelairdropcrate_Converted(ModelPart root) {
		this.airdrop = root.getChild("airdrop");
		this.parachute = this.airdrop.getChild("parachute");
		this.Glider = this.parachute.getChild("Glider");
		this.Wire = this.parachute.getChild("Wire");
		this.Crate = this.airdrop.getChild("Crate");
		this.LeftWire = this.Crate.getChild("LeftWire");
		this.LeftHook = this.LeftWire.getChild("LeftHook");
		this.RightWire = this.Crate.getChild("RightWire");
		this.RightHook = this.RightWire.getChild("RightHook");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition airdrop = partdefinition.addOrReplaceChild("airdrop", CubeListBuilder.create(), PartPose.offset(0.0F, -46.25F, 0.0F));
		PartDefinition parachute = airdrop.addOrReplaceChild("parachute", CubeListBuilder.create(), PartPose.offset(0.0F, 55.25F, 1.0F));
		PartDefinition Glider = parachute.addOrReplaceChild("Glider",
				CubeListBuilder.create().texOffs(210, 0).mirror().addBox(-9.9456F, -49.0F, -50.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(210, 0).mirror()
						.addBox(-9.9456F, -49.0F, 47.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(194, 78).mirror().addBox(-50.0F, -49.0F, -9.9456F, 3.0F, 15.0F, 19.8912F, new CubeDeformation(0.0F)).mirror(false)
						.texOffs(194, 78).mirror().addBox(47.0F, -49.0F, -9.9456F, 3.0F, 15.0F, 19.8912F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition hexadecagon_r1 = Glider.addOrReplaceChild("hexadecagon_r1", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-5.1124F, -39.0F, -2.2502F, -2.6117F, 0.7119F, -1.2053F));
		PartDefinition hexadecagon_r2 = Glider.addOrReplaceChild("hexadecagon_r2", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-5.1124F, -39.0F, -2.2502F, -2.7201F, 0.3614F, -1.4136F));
		PartDefinition hexadecagon_r3 = Glider.addOrReplaceChild("hexadecagon_r3", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-5.1124F, -39.0F, -2.2502F, -2.7201F, -0.3614F, -1.728F));
		PartDefinition hexadecagon_r4 = Glider.addOrReplaceChild("hexadecagon_r4", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-5.1124F, -39.0F, -2.2502F, -2.6117F, -0.7119F, -1.9363F));
		PartDefinition hexadecagon_r5 = Glider.addOrReplaceChild("hexadecagon_r5", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-5.1124F, -39.0F, -2.2502F, -2.7489F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r6 = Glider.addOrReplaceChild("hexadecagon_r6", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-1.9302F, -39.0F, -5.4324F, -1.8557F, 0.274F, -0.8249F));
		PartDefinition hexadecagon_r7 = Glider.addOrReplaceChild("hexadecagon_r7", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.9302F, -39.0F, -5.4324F, -1.9363F, 0.147F, -1.2053F));
		PartDefinition hexadecagon_r8 = Glider.addOrReplaceChild("hexadecagon_r8", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.9302F, -39.0F, -5.4324F, -1.9363F, -0.147F, -1.9363F));
		PartDefinition hexadecagon_r9 = Glider.addOrReplaceChild("hexadecagon_r9", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-1.9302F, -39.0F, -5.4324F, -1.8557F, -0.274F, -2.3166F));
		PartDefinition hexadecagon_r10 = Glider.addOrReplaceChild("hexadecagon_r10", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-1.9302F, -39.0F, -5.4324F, -1.9635F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r11 = Glider.addOrReplaceChild("hexadecagon_r11", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(2.5702F, -39.0F, -5.4324F, -1.2859F, -0.274F, -0.8249F));
		PartDefinition hexadecagon_r12 = Glider.addOrReplaceChild("hexadecagon_r12", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(2.5702F, -39.0F, -5.4324F, -1.2053F, -0.147F, -1.2053F));
		PartDefinition hexadecagon_r13 = Glider.addOrReplaceChild("hexadecagon_r13", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(2.5702F, -39.0F, -5.4324F, -1.2053F, 0.147F, -1.9363F));
		PartDefinition hexadecagon_r14 = Glider.addOrReplaceChild("hexadecagon_r14", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(2.5702F, -39.0F, -5.4324F, -1.2859F, 0.274F, -2.3166F));
		PartDefinition hexadecagon_r15 = Glider.addOrReplaceChild("hexadecagon_r15", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(2.5702F, -39.0F, -5.4324F, -1.1781F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r16 = Glider.addOrReplaceChild("hexadecagon_r16", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(5.7524F, -39.0F, -2.2502F, -0.5299F, -0.7119F, -1.2053F));
		PartDefinition hexadecagon_r17 = Glider.addOrReplaceChild("hexadecagon_r17", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.7524F, -39.0F, -2.2502F, -0.4215F, -0.3614F, -1.4136F));
		PartDefinition hexadecagon_r18 = Glider.addOrReplaceChild("hexadecagon_r18", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.7524F, -39.0F, -2.2502F, -0.4215F, 0.3614F, -1.728F));
		PartDefinition hexadecagon_r19 = Glider.addOrReplaceChild("hexadecagon_r19", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(5.7524F, -39.0F, -2.2502F, -0.5299F, 0.7119F, -1.9363F));
		PartDefinition hexadecagon_r20 = Glider.addOrReplaceChild("hexadecagon_r20", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(5.7524F, -39.0F, -2.2502F, -0.3927F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r21 = Glider.addOrReplaceChild("hexadecagon_r21", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-3.8378F, -39.0F, -4.1578F, -2.1863F, 0.5236F, -0.9553F));
		PartDefinition hexadecagon_r22 = Glider.addOrReplaceChild("hexadecagon_r22", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.8378F, -39.0F, -4.1578F, -2.3166F, 0.274F, -1.2859F));
		PartDefinition hexadecagon_r23 = Glider.addOrReplaceChild("hexadecagon_r23", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.8378F, -39.0F, -4.1578F, -2.3166F, -0.274F, -1.8557F));
		PartDefinition hexadecagon_r24 = Glider.addOrReplaceChild("hexadecagon_r24", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-3.8378F, -39.0F, -4.1578F, -2.1863F, -0.5236F, -2.1863F));
		PartDefinition hexadecagon_r25 = Glider.addOrReplaceChild("hexadecagon_r25", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-3.8378F, -39.0F, -4.1578F, -2.3562F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r26 = Glider.addOrReplaceChild("hexadecagon_r26", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.32F, -39.0F, -5.88F, -1.5708F, 0.0F, -0.7854F));
		PartDefinition hexadecagon_r27 = Glider.addOrReplaceChild("hexadecagon_r27", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.32F, -39.0F, -5.88F, -1.5708F, 0.0F, -1.1781F));
		PartDefinition hexadecagon_r28 = Glider.addOrReplaceChild("hexadecagon_r28", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.32F, -39.0F, -5.88F, -1.5708F, 0.0F, -1.9635F));
		PartDefinition hexadecagon_r29 = Glider.addOrReplaceChild("hexadecagon_r29", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.32F, -39.0F, -5.88F, -1.5708F, 0.0F, -2.3562F));
		PartDefinition hexadecagon_r30 = Glider.addOrReplaceChild("hexadecagon_r30", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.32F, -39.0F, -5.88F, -1.5708F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r31 = Glider.addOrReplaceChild("hexadecagon_r31", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(4.4778F, -39.0F, -4.1578F, -0.9553F, -0.5236F, -0.9553F));
		PartDefinition hexadecagon_r32 = Glider.addOrReplaceChild("hexadecagon_r32", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.4778F, -39.0F, -4.1578F, -0.8249F, -0.274F, -1.2859F));
		PartDefinition hexadecagon_r33 = Glider.addOrReplaceChild("hexadecagon_r33", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(4.4778F, -39.0F, -4.1578F, -0.8249F, 0.274F, -1.8557F));
		PartDefinition hexadecagon_r34 = Glider.addOrReplaceChild("hexadecagon_r34", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(4.4778F, -39.0F, -4.1578F, -0.9553F, 0.5236F, -2.1863F));
		PartDefinition hexadecagon_r35 = Glider.addOrReplaceChild("hexadecagon_r35", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(4.4778F, -39.0F, -4.1578F, -0.7854F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r36 = Glider.addOrReplaceChild("hexadecagon_r36", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(6.2F, -39.0F, 0.0F, 0.0F, -0.7854F, -1.5708F));
		PartDefinition hexadecagon_r37 = Glider.addOrReplaceChild("hexadecagon_r37", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, -49.0F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.2F, -39.0F, 0.0F, 0.0F, -0.3927F, -1.5708F));
		PartDefinition hexadecagon_r38 = Glider.addOrReplaceChild("hexadecagon_r38", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(209, 38)
				.mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.2F, -39.0F, 0.0F, 0.0F, 0.3927F, -1.5708F));
		PartDefinition hexadecagon_r39 = Glider.addOrReplaceChild("hexadecagon_r39", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(-9.2533F, -15.06F, 46.06F, 19.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(6.2F, -39.0F, 0.0F, 0.0F, 0.7854F, -1.5708F));
		PartDefinition hexadecagon_r40 = Glider.addOrReplaceChild("hexadecagon_r40", CubeListBuilder.create().texOffs(209, 38).mirror().addBox(46.0F, -15.06F, -9.7467F, 3.0F, 18.0F, 19.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(6.2F, -39.0F, 0.0F, 0.0F, 0.0F, -1.5708F));
		PartDefinition hexadecagon_r41 = Glider.addOrReplaceChild("hexadecagon_r41",
				CubeListBuilder.create().texOffs(194, 78).mirror().addBox(47.0F, -16.0F, -9.9456F, 3.0F, 15.0F, 19.8912F, new CubeDeformation(0.0F)).mirror(false).texOffs(194, 78).mirror()
						.addBox(-50.0F, -16.0F, -9.9456F, 3.0F, 15.0F, 19.8912F, new CubeDeformation(0.0F)).mirror(false).texOffs(210, 0).mirror().addBox(-9.9456F, -16.0F, 47.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
						.texOffs(210, 0).mirror().addBox(-9.9456F, -16.0F, -50.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.0F, -33.0F, 0.0F, 0.0F, 0.3927F, 0.0F));
		PartDefinition hexadecagon_r42 = Glider.addOrReplaceChild("hexadecagon_r42",
				CubeListBuilder.create().texOffs(194, 78).mirror().addBox(47.0F, -16.0F, -9.9456F, 3.0F, 15.0F, 19.8912F, new CubeDeformation(0.0F)).mirror(false).texOffs(194, 78).mirror()
						.addBox(-50.0F, -16.0F, -9.9456F, 3.0F, 15.0F, 19.8912F, new CubeDeformation(0.0F)).mirror(false).texOffs(210, 0).mirror().addBox(-9.9456F, -16.0F, 47.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
						.texOffs(210, 0).mirror().addBox(-9.9456F, -16.0F, -50.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.0F, -33.0F, 0.0F, 0.0F, -0.3927F, 0.0F));
		PartDefinition hexadecagon_r43 = Glider.addOrReplaceChild("hexadecagon_r43", CubeListBuilder.create().texOffs(210, 0).mirror().addBox(-9.9456F, -16.0F, 47.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(210, 0)
				.mirror().addBox(-9.9456F, -16.0F, -50.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -33.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
		PartDefinition hexadecagon_r44 = Glider.addOrReplaceChild("hexadecagon_r44", CubeListBuilder.create().texOffs(210, 0).mirror().addBox(-9.9456F, -16.0F, 47.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(210, 0)
				.mirror().addBox(-9.9456F, -16.0F, -50.0F, 19.8912F, 15.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -33.0F, 0.0F, 0.0F, -0.7854F, 0.0F));
		PartDefinition Wire = parachute.addOrReplaceChild("Wire", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -0.9803F));
		PartDefinition cube_r1 = Wire.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(252, 109).addBox(-1.0F, -78.0F, -1.0F, 1.0F, 78.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-10.0F, 0.0F, -4.0197F, 0.1745F, 0.0F, -0.1745F));
		PartDefinition cube_r2 = Wire.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(252, 107).addBox(-1.0F, -79.0F, 0.0F, 1.0F, 79.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-10.0F, 0.0F, 3.9803F, -0.1719F, -0.0302F, -0.1719F));
		PartDefinition cube_r3 = Wire.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(252, 109).addBox(0.0F, -78.0F, -1.0F, 1.0F, 78.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(10.0F, 0.0F, -4.0197F, 0.1745F, 0.0F, 0.1745F));
		PartDefinition cube_r4 = Wire.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(252, 110).addBox(0.0F, -78.0F, 0.0F, 1.0F, 78.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(10.0F, 0.0F, 3.9803F, -0.1771F, -0.0302F, 0.1719F));
		PartDefinition Crate = airdrop.addOrReplaceChild("Crate",
				CubeListBuilder.create().texOffs(0, 54).addBox(-8.0F, 12.25F, -8.5F, 16.0F, 2.0F, 17.0F, new CubeDeformation(0.0F)).texOffs(40, 101).addBox(11.0F, 0.25F, -7.0F, 2.5F, 2.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(72, 111)
						.addBox(-13.25F, 0.25F, -7.0F, 2.3F, 2.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(0, 0).addBox(-13.5F, 4.25F, -7.0F, 27.0F, 8.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(116, 88)
						.addBox(8.05F, 0.25F, -5.5F, 2.9F, 2.0F, 11.0F, new CubeDeformation(0.0F)).texOffs(40, 117).addBox(-10.95F, 0.25F, -5.5F, 2.9F, 2.0F, 11.0F, new CubeDeformation(0.0F)).texOffs(0, 22)
						.addBox(-11.0F, 12.25F, -7.0F, 22.0F, 2.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(76, 73).addBox(-14.0F, 12.25F, -8.5F, 3.0F, 2.0F, 17.0F, new CubeDeformation(0.0F)).texOffs(82, 0)
						.addBox(11.0F, 12.25F, -8.5F, 3.0F, 2.0F, 17.0F, new CubeDeformation(0.0F)).texOffs(76, 92).addBox(11.0F, 2.25F, -8.5F, 3.0F, 2.0F, 17.0F, new CubeDeformation(0.0F)).texOffs(104, 111)
						.addBox(-13.25F, 14.25F, -7.0F, 2.3F, 1.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(72, 38).addBox(-8.0F, 14.25F, -7.0F, 16.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(116, 73)
						.addBox(11.0F, 14.25F, -7.0F, 2.5F, 1.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(72, 22).addBox(-8.0F, 0.25F, -7.0F, 16.0F, 2.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(66, 54)
						.addBox(-8.0F, 2.25F, -8.5F, 16.0F, 2.0F, 17.0F, new CubeDeformation(0.0F)).texOffs(0, 38).addBox(-11.0F, 2.25F, -7.0F, 22.0F, 2.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(0, 101)
						.addBox(-14.0F, 2.25F, -8.5F, 3.0F, 2.0F, 17.0F, new CubeDeformation(0.0F)).texOffs(0, 120).addBox(8.05F, 13.35F, -5.5F, 2.9F, 2.0F, 11.0F, new CubeDeformation(0.0F)).texOffs(122, 0)
						.addBox(-10.95F, 13.35F, -5.5F, 2.9F, 2.0F, 11.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 55.0F, 0.0F));
		PartDefinition LeftWire = Crate.addOrReplaceChild("LeftWire", CubeListBuilder.create().texOffs(104, 126).addBox(-34.75F, -25.8F, 2.25F, 2.5F, 1.0F, 11.5F, new CubeDeformation(0.0F)).texOffs(66, 127)
				.addBox(-34.75F, -10.1F, 2.25F, 2.5F, 1.0F, 11.5F, new CubeDeformation(0.0F)).texOffs(0, 73).addBox(-34.75F, -23.0F, -0.5F, 2.5F, 11.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offset(24.0F, 25.25F, -8.0F));
		PartDefinition cube_r5 = LeftWire.addOrReplaceChild("cube_r5",
				CubeListBuilder.create().texOffs(26, 120).addBox(-10.75F, 3.4901F, -6.4901F, 2.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(52, 130).addBox(-10.75F, -6.4901F, 3.4901F, 2.5F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-24.0F, -11.2221F, 7.9925F, -0.7854F, 0.0F, 0.0F));
		PartDefinition cube_r6 = LeftWire.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(116, 101).addBox(-1.25F, -1.0F, -2.0F, 2.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-33.5F, -23.7221F, 14.3425F, -0.7854F, 0.0F, 0.0F));
		PartDefinition cube_r7 = LeftWire.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(128, 101).addBox(-1.25F, -2.0F, -1.0F, 2.5F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-33.5F, -23.7221F, 1.6425F, -0.7854F, 0.0F, 0.0F));
		PartDefinition LeftHook = LeftWire.addOrReplaceChild("LeftHook",
				CubeListBuilder.create().texOffs(175, 251).addBox(-35.75F, -18.0F, -0.75F, 4.5F, 1.0F, 0.5F, new CubeDeformation(0.0F)).texOffs(239, 251).addBox(-35.75F, -19.0F, -0.25F, 4.5F, 5.0F, 0.75F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, -1.0F, 0.0F));
		PartDefinition RightWire = Crate.addOrReplaceChild("RightWire", CubeListBuilder.create().texOffs(26, 130).addBox(-1.25F, -2.0779F, -12.0925F, 2.5F, 1.0F, 11.5F, new CubeDeformation(0.0F)).texOffs(130, 126)
				.addBox(-1.25F, 13.6221F, -12.0925F, 2.5F, 1.0F, 11.5F, new CubeDeformation(0.0F)).texOffs(38, 73).addBox(-1.25F, 0.7221F, -14.8425F, 2.5F, 11.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offset(9.5F, 1.5279F, 6.3425F));
		PartDefinition cube_r8 = RightWire.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(92, 127).addBox(-1.25F, -1.0F, -2.0F, 2.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
		PartDefinition cube_r9 = RightWire.addOrReplaceChild("cube_r9",
				CubeListBuilder.create().texOffs(132, 25).addBox(8.25F, -6.4901F, 3.4901F, 2.5F, 4.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(122, 13).addBox(8.25F, 3.4901F, -6.4901F, 2.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-9.5F, 12.5F, -6.35F, -0.7854F, 0.0F, 0.0F));
		PartDefinition cube_r10 = RightWire.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(132, 19).addBox(8.25F, -2.0F, -1.0F, 2.5F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-9.5F, 0.0F, -12.7F, -0.7854F, 0.0F, 0.0F));
		PartDefinition RightHook = RightWire.addOrReplaceChild("RightHook",
				CubeListBuilder.create().texOffs(170, 196).addBox(-35.75F, -18.0F, -0.75F, 4.5F, 1.0F, 0.5F, new CubeDeformation(0.0F)).texOffs(248, 251).addBox(-35.75F, -19.0F, -0.25F, 4.5F, 5.0F, 0.75F, new CubeDeformation(0.0F)),
				PartPose.offset(33.5F, 22.7221F, -14.3425F));
		return LayerDefinition.create(meshdefinition, 256, 256);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		airdrop.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}