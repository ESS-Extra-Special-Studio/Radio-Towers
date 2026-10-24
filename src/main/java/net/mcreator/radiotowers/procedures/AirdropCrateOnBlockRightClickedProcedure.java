package net.mcreator.radiotowers.procedures;

import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

public class AirdropCrateOnBlockRightClickedProcedure {
	public static void execute(IWorld world, double x, double y, double z) {
	World _level = (world instanceof World) ? (World) world : null;
		if (_level != null) {
			if (!_level.isClientSide()) {
				_level.playSound(null, new BlockPos(x, y, z), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.barrel.open")), SoundCategory.NEUTRAL, 1, 1);
			} else {
				_level.playLocalSound(x, y, z, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.barrel.open")), SoundCategory.NEUTRAL, 1, 1, false);
			}
		}
	}
}