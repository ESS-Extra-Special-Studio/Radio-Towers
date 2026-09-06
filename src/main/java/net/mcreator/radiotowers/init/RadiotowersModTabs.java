/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.mcreator.radiotowers.RadiotowersMod;
import net.mcreator.radiotowers.airdrop.TaczAirdropIntegration;

@EventBusSubscriber(modid = net.mcreator.radiotowers.RadiotowersMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class RadiotowersModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RadiotowersMod.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADIOTOWERS = REGISTRY.register("radiotowers",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.radiotowers.radiotowers")).icon(() -> new ItemStack(RadiotowersModBlocks.AIRDROP_CRATE.get())).displayItems((parameters, tabData) -> {
				tabData.accept(RadiotowersModBlocks.METAL_BARS.get().asItem());
				tabData.accept(RadiotowersModBlocks.RADIO_PANEL.get().asItem());
				tabData.accept(RadiotowersModBlocks.AIRDROP_CRATE.get().asItem());
				tabData.accept(RadiotowersModBlocks.TRASHBLOCK.get().asItem());
			}).build());

	@SubscribeEvent
	public static void buildTabContentsVanilla(BuildCreativeModeTabContentsEvent tabData) {
		TaczAirdropIntegration.captureCreativeTabStacks(tabData);
		if (tabData.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
			tabData.accept(RadiotowersModItems.AIRDROPENTITY_SPAWN_EGG.get());
			tabData.accept(RadiotowersModItems.PLANEENTITY_SPAWN_EGG.get());
		}
	}
}