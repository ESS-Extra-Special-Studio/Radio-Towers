/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.radiotowers.client.model.Modelplane;
import net.mcreator.radiotowers.client.model.Modelairdropcrate_Converted;

@EventBusSubscriber(modid = net.mcreator.radiotowers.RadiotowersMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RadiotowersModModels {
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(Modelairdropcrate_Converted.LAYER_LOCATION, Modelairdropcrate_Converted::createBodyLayer);
		event.registerLayerDefinition(Modelplane.LAYER_LOCATION, Modelplane::createBodyLayer);
	}
}