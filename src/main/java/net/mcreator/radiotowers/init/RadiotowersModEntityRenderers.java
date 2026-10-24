package net.mcreator.radiotowers.init;

import net.minecraft.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import net.mcreator.radiotowers.client.renderer.AirdropentityRenderer;
import net.mcreator.radiotowers.client.renderer.PlaneentityRenderer;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RadiotowersModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(FMLClientSetupEvent event) {
		RenderingRegistry.registerEntityRenderingHandler(RadiotowersModEntities.AIRDROPENTITY.get(), AirdropentityRenderer::new);
		RenderingRegistry.registerEntityRenderingHandler(RadiotowersModEntities.PLANEENTITY.get(), PlaneentityRenderer::new);
	}
}
