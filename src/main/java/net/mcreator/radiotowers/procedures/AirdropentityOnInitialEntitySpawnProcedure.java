package net.mcreator.radiotowers.procedures;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;

import net.mcreator.radiotowers.entity.AirdropentityEntity;

public class AirdropentityOnInitialEntitySpawnProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		entity.setNoGravity(false);
		if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
			_entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 3, false, true));
		return entity instanceof AirdropentityEntity _datEntL2 && _datEntL2.getEntityData().get(AirdropentityEntity.DATA_IsOpening);
	}
}