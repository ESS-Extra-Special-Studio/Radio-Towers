package net.mcreator.radiotowers.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import net.mcreator.radiotowers.client.model.Modelplane;
import net.mcreator.radiotowers.client.model.animations.planeAnimation;
import net.mcreator.radiotowers.entity.PlaneentityEntity;

/**
 * Flying airdrops and grounded EB wrecks both use {@link Modelplane} (same mesh as the fly-in).
 * Crashed wrecks skip the fly-in animation and apply a nose-down / bank pose.
 */
public class PlaneentityRenderer extends MobRenderer<PlaneentityEntity, PlaneRenderState, Modelplane> {

	private static final ResourceLocation TEXTURE = ResourceLocation.parse("radiotowers:textures/entities/airplane.png");

	public PlaneentityRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(Modelplane.LAYER_LOCATION)), 1f);
	}

	@Override
	public PlaneRenderState createRenderState() {
		return new PlaneRenderState();
	}

	@Override
	public void extractRenderState(PlaneentityEntity entity, PlaneRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.crashedWreck = entity.isCrashedWreck();
		state.flyIn.copyFrom(entity.animationState0);
	}

	@Override
	public ResourceLocation getTextureLocation(PlaneRenderState state) {
		return TEXTURE;
	}

	@Override
	public boolean shouldRender(PlaneentityEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		if (entity.distanceToSqr(camX, camY, camZ) > 256.0 * 256.0) {
			return false;
		}
		AABB box = entity.getBoundingBox().inflate(96.0, 32.0, 96.0);
		return frustum.isVisible(box);
	}

	@Override
	protected void setupRotations(PlaneRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);
		if (state.crashedWreck) {
			poseStack.mulPose(Axis.XP.rotationDegrees(22f));
			poseStack.mulPose(Axis.ZP.rotationDegrees(28f));
			poseStack.translate(0.0, -1.25, 0.0);
		}
	}

	private static final class AnimatedModel extends Modelplane {
		private final KeyframeAnimation flyIn;

		private AnimatedModel(ModelPart root) {
			super(root);
			this.flyIn = planeAnimation.flyIn.bake(root);
		}

		@Override
		public void setupAnim(PlaneRenderState state) {
			this.root().getAllParts().forEach(ModelPart::resetPose);
			if (!state.crashedWreck) {
				this.flyIn.apply(state.flyIn, state.ageInTicks, 1.2f);
			}
		}
	}
}
