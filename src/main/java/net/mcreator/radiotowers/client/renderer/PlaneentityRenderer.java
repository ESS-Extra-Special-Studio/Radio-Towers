package net.mcreator.radiotowers.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import net.mcreator.radiotowers.client.model.Modelplane;
import net.mcreator.radiotowers.entity.PlaneentityEntity;

/**
 * Flying airdrops and grounded EB wrecks both use {@link Modelplane} (same mesh as the fly-in).
 * Crashed wrecks skip the fly-in animation and apply a nose-down / bank pose.
 */
public class PlaneentityRenderer extends MobRenderer<PlaneentityEntity, PlaneentityRenderState, Modelplane> {

	private static final Identifier TEXTURE = Identifier.parse("radiotowers:textures/entities/airplane.png");

	public PlaneentityRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelplane(context.bakeLayer(Modelplane.LAYER_LOCATION)), 1f);
	}

	@Override
	public Identifier getTextureLocation(PlaneentityRenderState state) {
		return TEXTURE;
	}

	@Override
	public PlaneentityRenderState createRenderState() {
		return new PlaneentityRenderState();
	}

	@Override
	public void extractRenderState(PlaneentityEntity entity, PlaneentityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.crashedWreck = entity.isCrashedWreck();
		state.flyIn.copyFrom(entity.animationState0);
	}

	@Override
	public boolean shouldRender(PlaneentityEntity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick) {
		if (entity.distanceToSqr(camX, camY, camZ) > 256.0 * 256.0) {
			return false;
		}
		AABB box = entity.getBoundingBox().inflate(96.0, 32.0, 96.0);
		return frustum.isVisible(box);
	}

	@Override
	protected void setupRotations(PlaneentityRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.crashedWreck) {
			poseStack.mulPose(new org.joml.Matrix4f().rotation(Axis.XP.rotationDegrees(22f)));
			poseStack.mulPose(new org.joml.Matrix4f().rotation(Axis.ZP.rotationDegrees(28f)));
			poseStack.translate(0.0, -1.25, 0.0);
		}
	}
}
