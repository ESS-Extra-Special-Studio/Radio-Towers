package net.mcreator.radiotowers.client.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.math.vector.Vector3f;

import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;

import net.mcreator.radiotowers.client.model.Modelplane;
import net.mcreator.radiotowers.entity.PlaneentityEntity;

/**
 * Flying airdrops and grounded EB wrecks both use {@link Modelplane} (same mesh as the fly-in).
 * Crashed wrecks apply a nose-down / bank pose. 1.18.2 has no entity AnimationState API.
 */
public class PlaneentityRenderer extends MobRenderer<PlaneentityEntity, EntityModel<PlaneentityEntity>> {

	public PlaneentityRenderer(EntityRendererManager context) {
		super(context, new Modelplane<>(), 1f);
	}

	@Override
	public ResourceLocation getTextureLocation(PlaneentityEntity entity) {
		return new ResourceLocation("radiotowers:textures/entities/airplane.png");
	}

	@Override
	public boolean shouldRender(PlaneentityEntity entity, ClippingHelper frustum, double camX, double camY, double camZ) {
		if (entity.distanceToSqr(camX, camY, camZ) > 256.0 * 256.0) {
			return false;
		}
		AxisAlignedBB box = entity.getBoundingBox().inflate(96.0, 32.0, 96.0);
		return frustum.isVisible(box);
	}

	@Override
	protected void setupRotations(PlaneentityEntity entity, MatrixStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks) {
		super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
		if (entity.isCrashedWreck()) {
			poseStack.mulPose(Vector3f.XP.rotationDegrees(22f));
			poseStack.mulPose(Vector3f.ZP.rotationDegrees(28f));
			poseStack.translate(0.0, -1.25, 0.0);
		}
	}
}
