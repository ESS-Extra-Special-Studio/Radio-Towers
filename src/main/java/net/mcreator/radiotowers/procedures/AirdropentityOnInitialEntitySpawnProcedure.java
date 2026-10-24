package net.mcreator.radiotowers.procedures;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Effects;
import net.minecraft.potion.EffectInstance;

import net.mcreator.radiotowers.entity.AirdropentityEntity;

public class AirdropentityOnInitialEntitySpawnProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		entity.setNoGravity(false);
		LivingEntity _entity = (entity instanceof LivingEntity) ? (LivingEntity) entity : null;
		if (_entity != null && !_entity.level.isClientSide())
			_entity.addEffect(new EffectInstance(Effects.SLOW_FALLING, 120, 3, false, true));
			AirdropentityEntity _datEntL2 = (entity instanceof AirdropentityEntity) ? (AirdropentityEntity) entity : null;
		return _datEntL2 != null && _datEntL2.getEntityData().get(AirdropentityEntity.DATA_IsOpening);
	}
}