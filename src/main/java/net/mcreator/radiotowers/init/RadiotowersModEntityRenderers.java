/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.radiotowers.client.renderer.PlaneentityRenderer;
import net.mcreator.radiotowers.client.renderer.AirdropentityRenderer;

@EventBusSubscriber(modid = net.mcreator.radiotowers.RadiotowersMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RadiotowersModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(RadiotowersModEntities.AIRDROPENTITY.get(), AirdropentityRenderer::new);
		event.registerEntityRenderer(RadiotowersModEntities.PLANEENTITY.get(), PlaneentityRenderer::new);
	}
}