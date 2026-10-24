package net.mcreator.radiotowers.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AnimationState;

import net.mcreator.radiotowers.client.model.Modelairdropcrate_Converted;
import net.mcreator.radiotowers.entity.AirdropentityEntity;

public class AirdropentityRenderer extends MobRenderer<AirdropentityEntity, AirdropentityRenderer.AirdropRenderState, Modelairdropcrate_Converted> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("radiotowers", "textures/entities/airdrop_crate2.png");

	public AirdropentityRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelairdropcrate_Converted(context.bakeLayer(Modelairdropcrate_Converted.LAYER_LOCATION)), 1f);
	}

	@Override
	public AirdropRenderState createRenderState() {
		return new AirdropRenderState();
	}

	@Override
	public void extractRenderState(AirdropentityEntity entity, AirdropRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.glideAnimationState.copyFrom(entity.animationState0);
		state.fallAnimationState.copyFrom(entity.animationState1);
	}

	@Override
	public ResourceLocation getTextureLocation(AirdropRenderState state) {
		return TEXTURE;
	}

	public static final class AirdropRenderState extends LivingEntityRenderState {
		public final AnimationState glideAnimationState = new AnimationState();
		public final AnimationState fallAnimationState = new AnimationState();
	}
}
