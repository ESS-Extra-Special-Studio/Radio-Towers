package net.mcreator.radiotowers.block;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.util.ForgeSoundType;

import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockReader;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

public class TrashblockBlock extends Block {
	public TrashblockBlock() {
		super(AbstractBlock.Properties.of(Material.METAL)
				.sound(new ForgeSoundType(1.0f, 1.0f, () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.metal.break")), () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:trash_walking")),
						() -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.metal.place")), () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.metal.hit")),
						() -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.metal.fall"))))
				.strength(1f, 10f).noCollission().friction(0.5f).speedFactor(0.4f).jumpFactor(1.1f).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, IBlockReader reader, BlockPos pos) {
		return true;
	}

	@Override
	public int getLightBlock(BlockState state, IBlockReader worldIn, BlockPos pos) {
		return 0;
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
		return box(0, 0, 0, 16, 13, 16);
	}
}