/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.ForgeSpawnEggItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, RadiotowersMod.MODID);
	public static final RegistryObject<Item> METAL_BARS;
	public static final RegistryObject<Item> RADIO_PANEL;
	public static final RegistryObject<Item> AIRDROP_CRATE;
	public static final RegistryObject<Item> TRASHBLOCK;
	public static final RegistryObject<Item> AIRDROPENTITY_SPAWN_EGG;
	public static final RegistryObject<Item> PLANEENTITY_SPAWN_EGG;
	static {
		METAL_BARS = block(RadiotowersModBlocks.METAL_BARS);
		RADIO_PANEL = block(RadiotowersModBlocks.RADIO_PANEL);
		AIRDROP_CRATE = block(RadiotowersModBlocks.AIRDROP_CRATE);
		TRASHBLOCK = block(RadiotowersModBlocks.TRASHBLOCK);
		AIRDROPENTITY_SPAWN_EGG = REGISTRY.register("airdropentity_spawn_egg", () -> new ForgeSpawnEggItem(RadiotowersModEntities.AIRDROPENTITY, -1, -1, new Item.Properties()));
		PLANEENTITY_SPAWN_EGG = REGISTRY.register("planeentity_spawn_egg", () -> new ForgeSpawnEggItem(RadiotowersModEntities.PLANEENTITY, -1, -1, new Item.Properties()));
	}

	// Start of user code block custom items
	// End of user code block custom items
	private static RegistryObject<Item> block(RegistryObject<Block> block) {
		return block(block, new Item.Properties());
	}

	private static RegistryObject<Item> block(RegistryObject<Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
	}
}