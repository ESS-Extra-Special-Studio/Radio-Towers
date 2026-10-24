package net.mcreator.radiotowers.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.util.DeferredSoundType;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

public class TrashblockBlock extends Block {
	public TrashblockBlock() {
		super(BlockBehaviour.Properties.of()
				.sound(new DeferredSoundType(1.0f, 1.0f, () -> BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("block.metal.break")).orElse(null), () -> BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("radiotowers:trash_walking")).orElse(null),
						() -> BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("block.metal.place")).orElse(null), () -> BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("block.metal.hit")).orElse(null),
						() -> BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("block.metal.fall")).orElse(null)))
				.strength(1f, 10f).noCollision().friction(0.5f).speedFactor(0.4f).jumpFactor(1.1f).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return box(0, 0, 0, 16, 13, 16);
	}
}