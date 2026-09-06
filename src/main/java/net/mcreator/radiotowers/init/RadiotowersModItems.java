/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(BuiltInRegistries.ITEM, RadiotowersMod.MODID);
	public static final DeferredHolder<Item, Item> METAL_BARS;
	public static final DeferredHolder<Item, Item> RADIO_PANEL;
	public static final DeferredHolder<Item, Item> AIRDROP_CRATE;
	public static final DeferredHolder<Item, Item> TRASHBLOCK;
	public static final DeferredHolder<Item, Item> AIRDROPENTITY_SPAWN_EGG;
	public static final DeferredHolder<Item, Item> PLANEENTITY_SPAWN_EGG;
	static {
		METAL_BARS = block(RadiotowersModBlocks.METAL_BARS);
		RADIO_PANEL = block(RadiotowersModBlocks.RADIO_PANEL);
		AIRDROP_CRATE = block(RadiotowersModBlocks.AIRDROP_CRATE);
		TRASHBLOCK = block(RadiotowersModBlocks.TRASHBLOCK);
		AIRDROPENTITY_SPAWN_EGG = REGISTRY.register("airdropentity_spawn_egg", () -> new DeferredSpawnEggItem(RadiotowersModEntities.AIRDROPENTITY, -1, -1, new Item.Properties()));
		PLANEENTITY_SPAWN_EGG = REGISTRY.register("planeentity_spawn_egg", () -> new DeferredSpawnEggItem(RadiotowersModEntities.PLANEENTITY, -1, -1, new Item.Properties()));
	}

	// Start of user code block custom items
	// End of user code block custom items
	private static DeferredHolder<Item, Item> block(DeferredHolder<Block, Block> block) {
		return block(block, new Item.Properties());
	}

	private static DeferredHolder<Item, Item> block(DeferredHolder<Block, Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
	}
}