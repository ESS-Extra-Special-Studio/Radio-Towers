/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.world.level.block.Block;

import net.mcreator.radiotowers.block.TrashblockBlock;
import net.mcreator.radiotowers.block.RadioPanelBlock;
import net.mcreator.radiotowers.block.MetalBarsBlock;
import net.mcreator.radiotowers.block.AirdropCrateBlock;
import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModBlocks {
	public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(BuiltInRegistries.BLOCK, RadiotowersMod.MODID);
	public static final DeferredHolder<Block, Block> METAL_BARS;
	public static final DeferredHolder<Block, Block> RADIO_PANEL;
	public static final DeferredHolder<Block, Block> AIRDROP_CRATE;
	public static final DeferredHolder<Block, Block> TRASHBLOCK;
	static {
		METAL_BARS = REGISTRY.register("metal_bars", MetalBarsBlock::new);
		RADIO_PANEL = REGISTRY.register("radio_panel", RadioPanelBlock::new);
		AIRDROP_CRATE = REGISTRY.register("airdrop_crate", AirdropCrateBlock::new);
		TRASHBLOCK = REGISTRY.register("trashblock", TrashblockBlock::new);
	}
	// Start of user code block custom blocks
	// End of user code block custom blocks
}