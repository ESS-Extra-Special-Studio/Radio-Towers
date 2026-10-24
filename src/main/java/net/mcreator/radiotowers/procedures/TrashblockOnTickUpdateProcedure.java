package net.mcreator.radiotowers.procedures;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Effects;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import net.mcreator.radiotowers.init.RadiotowersModBlocks;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class TrashblockOnTickUpdateProcedure {
	@SubscribeEvent
	public static void onEntityFall(LivingFallEvent event) {
		if (event != null && event.getEntity() != null) {
			execute(event, event.getEntity().level, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), event.getEntity(), event.getDistance());
		}
	}

	public static void execute(IWorld world, double x, double y, double z, Entity entity, double distance) {
		execute(null, world, x, y, z, entity, distance);
	}

	private static void execute(@Nullable Event event, IWorld world, double x, double y, double z, Entity entity, double distance) {
		if (entity == null)
			return;
		if ((world.getBlockState(new BlockPos(entity.getX(), entity.getY(), entity.getZ()))).getBlock() == RadiotowersModBlocks.TRASHBLOCK.get()) {
		ServerWorld _level = (world instanceof ServerWorld) ? (ServerWorld) world : null;
			if (event != null && event.isCancelable()) {
				event.setCanceled(true);
			} else if (event != null && event.hasResult()) {
				event.setResult(Event.Result.DENY);
			}
			if (_level != null)
				_level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, (entity.getX()), (entity.getY()), (entity.getZ()), 60, 1, 1, 1, 0.01);
			if (distance <= 3) {
			World walkLevel = (world instanceof World) ? (World) world : null;
				if (walkLevel != null) {
					if (!walkLevel.isClientSide()) {
						walkLevel.playSound(null, new BlockPos(x, y, z), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:trash_walking")), SoundCategory.NEUTRAL, 1, 1);
					} else {
						walkLevel.playLocalSound(x, y, z, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:trash_walking")), SoundCategory.NEUTRAL, 1, 1, false);
					}
				}
			} else if (distance > 3) {
			LivingEntity living = (entity instanceof LivingEntity) ? (LivingEntity) entity : null;
			World fallLevel = (world instanceof World) ? (World) world : null;
				if (fallLevel != null) {
					if (!fallLevel.isClientSide()) {
						fallLevel.playSound(null, new BlockPos(x, y, z), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:fall_in_trash")), SoundCategory.NEUTRAL, 1, 1);
					} else {
						fallLevel.playLocalSound(x, y, z, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("radiotowers:fall_in_trash")), SoundCategory.NEUTRAL, 1, 1, false);
					}
				}
				if (living != null && !living.level.isClientSide())
					living.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 40, 50, false, false));
				if (living != null && !living.level.isClientSide())
					living.addEffect(new EffectInstance(Effects.JUMP, 40, 128, false, false));
			}
		}
	}
}