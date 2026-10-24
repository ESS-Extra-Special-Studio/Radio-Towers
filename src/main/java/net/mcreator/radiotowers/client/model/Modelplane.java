package net.mcreator.radiotowers.client.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.Entity;

/** 1.16.5 has no ModelPart layer API. Mesh is not baked; the entity still ticks and collides. */
public class Modelplane<T extends Entity> extends EntityModel<T> {
	public Modelplane() {
		super(rl -> RenderType.entityCutoutNoCull(rl));
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	}

	@Override
	public void renderToBuffer(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
	}
}
