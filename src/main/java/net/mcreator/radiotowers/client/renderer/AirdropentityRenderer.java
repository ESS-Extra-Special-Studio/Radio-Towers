package net.mcreator.radiotowers.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import net.mcreator.radiotowers.client.model.Modelairdropcrate_Converted;
import net.mcreator.radiotowers.entity.AirdropentityEntity;

public class AirdropentityRenderer extends MobRenderer<AirdropentityEntity, AirdropentityRenderState, Modelairdropcrate_Converted> {

	private static final Identifier TEXTURE = Identifier.parse("radiotowers:textures/entities/airdrop_crate2.png");

	public AirdropentityRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelairdropcrate_Converted(context.bakeLayer(Modelairdropcrate_Converted.LAYER_LOCATION)), 1f);
	}

	@Override
	public Identifier getTextureLocation(AirdropentityRenderState state) {
		return TEXTURE;
	}

	@Override
	public AirdropentityRenderState createRenderState() {
		return new AirdropentityRenderState();
	}

	@Override
	public void extractRenderState(AirdropentityEntity entity, AirdropentityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.animationState0.copyFrom(entity.animationState0);
		state.animationState1.copyFrom(entity.animationState1);
	}
}
