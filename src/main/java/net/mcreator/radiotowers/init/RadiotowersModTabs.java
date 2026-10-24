/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;
import net.minecraft.util.text.TranslationTextComponent;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

public class RadiotowersModTabs {
	public static final ItemGroup RADIOTOWERS = new ItemGroup("radiotowers.radiotowers") {
		@Override
		public ItemStack makeIcon() {
			return new ItemStack(RadiotowersModBlocks.AIRDROP_CRATE.get());
		}

		@Override
		public ITextComponent getDisplayName() {
			return new TranslationTextComponent("item_group.radiotowers.radiotowers");
		}
	};
}
