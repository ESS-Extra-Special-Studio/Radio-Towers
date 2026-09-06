/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import net.mcreator.radiotowers.client.gui.AirdropCrateguiScreen;
import net.mcreator.radiotowers.RadiotowersMod;

@EventBusSubscriber(modid = RadiotowersMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RadiotowersModScreens {
	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(RadiotowersModMenus.AIRDROP_CRATEGUI.get(), AirdropCrateguiScreen::new);
	}

	public interface ScreenAccessor {
		void updateMenuState(int elementType, String name, Object elementState);
	}
}
