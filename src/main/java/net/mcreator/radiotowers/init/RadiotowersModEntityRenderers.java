/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.mcreator.radiotowers.client.renderer.PlaneentityRenderer;
import net.mcreator.radiotowers.client.renderer.AirdropentityRenderer;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RadiotowersModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(RadiotowersModEntities.AIRDROPENTITY.get(), AirdropentityRenderer::new);
		event.registerEntityRenderer(RadiotowersModEntities.PLANEENTITY.get(), PlaneentityRenderer::new);
	}
}