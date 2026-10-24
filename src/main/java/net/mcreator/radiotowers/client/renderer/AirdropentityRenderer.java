package net.mcreator.radiotowers.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import net.mcreator.radiotowers.entity.AirdropentityEntity;
import net.mcreator.radiotowers.client.model.Modelairdropcrate_Converted;

public class AirdropentityRenderer extends MobRenderer<AirdropentityEntity, Modelairdropcrate_Converted<AirdropentityEntity>> {
	public AirdropentityRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelairdropcrate_Converted<>(context.bakeLayer(Modelairdropcrate_Converted.LAYER_LOCATION)), 1f);
	}

	@Override
	public ResourceLocation getTextureLocation(AirdropentityEntity entity) {
		return new ResourceLocation("radiotowers:textures/entities/airdrop_crate2.png");
	}
}
