/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;
import net.minecraft.network.chat.TranslatableComponent;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class RadiotowersModTabs {
	public static final CreativeModeTab RADIOTOWERS = new CreativeModeTab("radiotowers.radiotowers") {
		@Override
		public ItemStack makeIcon() {
			return new ItemStack(RadiotowersModBlocks.AIRDROP_CRATE.get());
		}

		@Override
		public Component getDisplayName() {
			return new TranslatableComponent("item_group.radiotowers.radiotowers");
		}
	};
}
