/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;

import net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity;
import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RadiotowersMod.MODID);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirdropCrateBlockEntity>> AIRDROP_CRATE = register("airdrop_crate", RadiotowersModBlocks.AIRDROP_CRATE, AirdropCrateBlockEntity::new);

	// Start of user code block custom block entities
	// End of user code block custom block entities
	private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(String registryname, DeferredHolder<Block, Block> block, BlockEntityType.BlockEntitySupplier<T> supplier) {
		return REGISTRY.register(registryname, () -> BlockEntityType.Builder.of(supplier, block.get()).build(null));
	}
}