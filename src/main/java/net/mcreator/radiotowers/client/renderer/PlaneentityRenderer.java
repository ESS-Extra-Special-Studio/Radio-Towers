package net.mcreator.radiotowers.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
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
public class PlaneentityRenderer extends MobRenderer<PlaneentityEntity, EntityModel<PlaneentityEntity>> {

	public PlaneentityRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(Modelplane.LAYER_LOCATION)), 1f);
	}

	@Override
	public ResourceLocation getTextureLocation(PlaneentityEntity entity) {
		return new ResourceLocation("radiotowers:textures/entities/airplane.png");
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
	protected void setupRotations(PlaneentityEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTicks) {
		super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
		if (entity.isCrashedWreck()) {
			// Nose into dirt + slight bank — same Modelplane mesh as the flyover.
			poseStack.mulPose(Axis.XP.rotationDegrees(22f));
			poseStack.mulPose(Axis.ZP.rotationDegrees(28f));
			poseStack.translate(0.0, -1.25, 0.0);
		}
	}

	private static final class AnimatedModel extends Modelplane<PlaneentityEntity> {
		private final ModelPart root;
		private final HierarchicalModel animator = new HierarchicalModel<PlaneentityEntity>() {
			@Override
			public ModelPart root() {
				return root;
			}

			@Override
			public void setupAnim(PlaneentityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
					float headPitch) {
				this.root().getAllParts().forEach(ModelPart::resetPose);
				if (!entity.isCrashedWreck()) {
					this.animate(entity.animationState0, planeAnimation.flyIn, ageInTicks, 1.2f);
				}
			}
		};

		public AnimatedModel(ModelPart root) {
			super(root);
			this.root = root;
		}

		@Override
		public void setupAnim(PlaneentityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
				float headPitch) {
			animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		}
	}
}
