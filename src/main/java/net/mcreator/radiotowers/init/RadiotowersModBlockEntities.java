/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.tileentity.TileEntityType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.Block;

import net.mcreator.radiotowers.block.entity.AirdropCrateBlockEntity;
import net.mcreator.radiotowers.RadiotowersMod;

public class RadiotowersModBlockEntities {
	public static final DeferredRegister<TileEntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, RadiotowersMod.MODID);
	public static final RegistryObject<TileEntityType<AirdropCrateBlockEntity>> AIRDROP_CRATE = register("airdrop_crate", RadiotowersModBlocks.AIRDROP_CRATE, AirdropCrateBlockEntity::new);

	// Start of user code block custom block entities
	// End of user code block custom block entities
	private static <T extends TileEntity> RegistryObject<TileEntityType<T>> register(String registryname, RegistryObject<Block> block, java.util.function.Supplier<T> supplier) {
		return REGISTRY.register(registryname, () -> TileEntityType.Builder.of(supplier, block.get()).build(null));
	}
}