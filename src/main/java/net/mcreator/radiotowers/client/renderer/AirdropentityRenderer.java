package net.mcreator.radiotowers.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HierarchicalModel;

import net.mcreator.radiotowers.entity.AirdropentityEntity;
import net.mcreator.radiotowers.client.model.animations.airdropcrate_ConvertedAnimation;
import net.mcreator.radiotowers.client.model.Modelairdropcrate_Converted;

public class AirdropentityRenderer extends MobRenderer<AirdropentityEntity, Modelairdropcrate_Converted<AirdropentityEntity>> {
	public AirdropentityRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(Modelairdropcrate_Converted.LAYER_LOCATION)), 1f);
	}

	@Override
	public ResourceLocation getTextureLocation(AirdropentityEntity entity) {
		return ResourceLocation.parse("radiotowers:textures/entities/airdrop_crate2.png");
	}

	private static final class AnimatedModel extends Modelairdropcrate_Converted<AirdropentityEntity> {
		private final ModelPart root;
		private final HierarchicalModel animator = new HierarchicalModel<AirdropentityEntity>() {
			@Override
			public ModelPart root() {
				return root;
			}

			@Override
			public void setupAnim(AirdropentityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
				this.root().getAllParts().forEach(ModelPart::resetPose);
				this.animate(entity.animationState0, airdropcrate_ConvertedAnimation.openglider, ageInTicks, 1f);
				this.animate(entity.animationState1, airdropcrate_ConvertedAnimation.falling, ageInTicks, 1f);
			}
		};

		public AnimatedModel(ModelPart root) {
			super(root);
			this.root = root;
		}

		@Override
		public void setupAnim(AirdropentityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
			animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		}
	}
}