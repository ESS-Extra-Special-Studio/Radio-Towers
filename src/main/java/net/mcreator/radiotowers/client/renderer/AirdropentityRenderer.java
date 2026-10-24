package net.mcreator.radiotowers.client.renderer;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import net.mcreator.radiotowers.client.model.Modelairdropcrate_Converted;
import net.mcreator.radiotowers.client.model.animations.airdropcrate_ConvertedAnimation;
import net.mcreator.radiotowers.entity.AirdropentityEntity;

public class AirdropentityRenderer extends MobRenderer<AirdropentityEntity, AirdropRenderState, Modelairdropcrate_Converted> {
	private static final Identifier TEXTURE = Identifier.parse("radiotowers:textures/entities/airdrop_crate2.png");

	public AirdropentityRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(Modelairdropcrate_Converted.LAYER_LOCATION)), 1f);
	}

	@Override
	public AirdropRenderState createRenderState() {
		return new AirdropRenderState();
	}

	@Override
	public void extractRenderState(AirdropentityEntity entity, AirdropRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.glider.copyFrom(entity.animationState0);
		state.falling.copyFrom(entity.animationState1);
	}

	@Override
	public Identifier getTextureLocation(AirdropRenderState state) {
		return TEXTURE;
	}

	private static final class AnimatedModel extends Modelairdropcrate_Converted {
		private final KeyframeAnimation glider;
		private final KeyframeAnimation falling;

		private AnimatedModel(ModelPart root) {
			super(root);
			this.glider = airdropcrate_ConvertedAnimation.openglider.bake(root);
			this.falling = airdropcrate_ConvertedAnimation.falling.bake(root);
		}

		@Override
		public void setupAnim(AirdropRenderState state) {
			this.root().getAllParts().forEach(ModelPart::resetPose);
			this.glider.apply(state.glider, state.ageInTicks, 1f);
			this.falling.apply(state.falling, state.ageInTicks, 1f);
		}
	}
}
