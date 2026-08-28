/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.Block;

import net.mcreator.radiotowers.block.TrashblockBlock;
import net.mcreator.radiotowers.block.RadioPanelBlock;
import net.mcreator.radiotowers.block.MetalBarsBlock;
import net.mcreator.radiotowers.block.AirdropCrateBlock;
import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModBlocks {
	public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, RadiotowersMod.MODID);
	public static final RegistryObject<Block> METAL_BARS;
	public static final RegistryObject<Block> RADIO_PANEL;
	public static final RegistryObject<Block> AIRDROP_CRATE;
	public static final RegistryObject<Block> TRASHBLOCK;
	static {
		METAL_BARS = REGISTRY.register("metal_bars", MetalBarsBlock::new);
		RADIO_PANEL = REGISTRY.register("radio_panel", RadioPanelBlock::new);
		AIRDROP_CRATE = REGISTRY.register("airdrop_crate", AirdropCrateBlock::new);
		TRASHBLOCK = REGISTRY.register("trashblock", TrashblockBlock::new);
	}
	// Start of user code block custom blocks
	// End of user code block custom blocks
}