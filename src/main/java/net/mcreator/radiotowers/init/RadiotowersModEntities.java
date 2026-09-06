/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.radiotowers.init;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

import net.mcreator.radiotowers.entity.PlaneentityEntity;
import net.mcreator.radiotowers.entity.AirdropentityEntity;
import net.mcreator.radiotowers.RadiotowersMod;

@EventBusSubscriber(modid = RadiotowersMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class RadiotowersModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, RadiotowersMod.MODID);
	public static final DeferredHolder<EntityType<?>, EntityType<AirdropentityEntity>> AIRDROPENTITY = register("airdropentity",
			EntityType.Builder.<AirdropentityEntity>of(AirdropentityEntity::new, MobCategory.MONSTER).fireImmune().sized(0.6f, 1.8f).clientTrackingRange(64).updateInterval(3));
	public static final DeferredHolder<EntityType<?>, EntityType<PlaneentityEntity>> PLANEENTITY = register("planeentity",
			EntityType.Builder.<PlaneentityEntity>of(PlaneentityEntity::new, MobCategory.MONSTER).fireImmune().sized(0.6f, 1.8f).clientTrackingRange(64).updateInterval(3));

	private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return REGISTRY.register(registryname, () -> entityTypeBuilder.build(ResourceLocation.fromNamespaceAndPath(RadiotowersMod.MODID, registryname).toString()));
	}

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			AirdropentityEntity.init();
			PlaneentityEntity.init();
		});
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(AIRDROPENTITY.get(), AirdropentityEntity.createAttributes().build());
		event.put(PLANEENTITY.get(), PlaneentityEntity.createAttributes().build());
	}
}
