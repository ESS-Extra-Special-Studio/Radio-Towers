package net.mcreator.radiotowers.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.world.phys.AABB;

import net.mcreator.radiotowers.entity.PlaneentityEntity;
import net.mcreator.radiotowers.client.model.animations.planeAnimation;
import net.mcreator.radiotowers.client.model.Modelplane;

public class PlaneentityRenderer extends MobRenderer<PlaneentityEntity, Modelplane<PlaneentityEntity>> {
	public PlaneentityRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(Modelplane.LAYER_LOCATION)), 1f);
	}

	@Override
	public ResourceLocation getTextureLocation(PlaneentityEntity entity) {
		return new ResourceLocation("radiotowers:textures/entities/airplane.png");
	}

	/**
	 * The visual mesh is much larger than the entity AABB. Inflate the cull box, but never
	 * treat camera-to-world-origin as distance (that forced the giant mesh on every frame near spawn).
	 */
	@Override
	public boolean shouldRender(PlaneentityEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		if (entity.distanceToSqr(camX, camY, camZ) > 256.0 * 256.0) {
			return false;
		}
		AABB box = entity.getBoundingBox().inflate(96.0, 32.0, 96.0);
		return frustum.isVisible(box);
	}

	private static final class AnimatedModel extends Modelplane<PlaneentityEntity> {
		private final ModelPart root;
		private final HierarchicalModel animator = new HierarchicalModel<PlaneentityEntity>() {
			@Override
			public ModelPart root() {
				return root;
			}

			@Override
			public void setupAnim(PlaneentityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
				this.root().getAllParts().forEach(ModelPart::resetPose);
				this.animate(entity.animationState0, planeAnimation.flyIn, ageInTicks, 1.2f);
			}
		};

		public AnimatedModel(ModelPart root) {
			super(root);
			this.root = root;
		}

		@Override
		public void setupAnim(PlaneentityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
			animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		}
	}
}